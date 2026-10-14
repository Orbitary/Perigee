/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.internal;

import org.jetbrains.annotations.Nullable;
import xyz.orbitary.perigee.Logger;
import xyz.orbitary.perigee.PerigeeException;
import xyz.orbitary.perigee.PerigeeTimeoutException;
import xyz.orbitary.perigee.protocol.Frame;
import xyz.orbitary.perigee.protocol.FrameCodec;
import xyz.orbitary.perigee.protocol.Opcode;
import xyz.orbitary.perigee.protocol.Requests;
import xyz.orbitary.perigee.protocol.RpcMessage;
import xyz.orbitary.perigee.transport.Transport;
import xyz.orbitary.perigee.user.DiscordUser;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * One live session with the Discord client: handshake, a reader thread, ping replies and nonce-matched responses.
 * Discord must send READY after the handshake and answer each request, or the connection is abandoned or the request fails.
 */
public final class Connection {
    private static final int CLOSE_WAIT_MILLIS = 500;

    /**
     * @param ready   how long to wait for READY after the handshake
     * @param request how long to wait for each reply
     */
    public record Timeouts(
      Duration ready,
      Duration request
    ) {
        public static final Timeouts DEFAULT = new Timeouts(Duration.ofSeconds(10), Duration.ofSeconds(10));

    }

    public interface Listener {
        void onReady(DiscordUser user);

        void onEvent(RpcMessage message);

        void onClosed(@Nullable Throwable cause);

    }

    private final Transport transport;
    private final Listener listener;
    private final ScheduledExecutorService timer;
    private final Timeouts timeouts;
    private final Map<String, CompletableFuture<RpcMessage>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile @Nullable Thread reader;
    private volatile @Nullable Throwable failure;
    private volatile @Nullable Throwable abortCause;
    private volatile @Nullable ScheduledFuture<?> readyTimeout;

    public Connection(Transport transport, Listener listener, ScheduledExecutorService timer, Timeouts timeouts) {
        this.transport = transport;
        this.listener = listener;
        this.timer = timer;
        this.timeouts = timeouts;
    }

    public void start(String clientId) throws IOException {
        transport.write(FrameCodec.encode(Opcode.HANDSHAKE, Requests.handshake(clientId)));
        Thread thread = Thread.ofPlatform().daemon().name("perigee-reader").unstarted(this::readLoop);
        reader = thread;
        readyTimeout = timer.schedule(
          () -> abort(new PerigeeTimeoutException("Discord did not send READY within " + timeouts.ready())),
          timeouts.ready().toMillis(),
          TimeUnit.MILLISECONDS
        );
        thread.start();
    }

    public CompletableFuture<RpcMessage> request(String cmd, Map<String, Object> args) {
        return request(cmd, args, null);
    }

    public CompletableFuture<RpcMessage> request(String cmd, Map<String, Object> args, @Nullable String evt) {
        if (closing.get()) return CompletableFuture.failedFuture(new PerigeeException("Connection is closed"));
        String nonce = UUID.randomUUID().toString();
        byte[] frame;
        try {
            frame = FrameCodec.encode(Opcode.FRAME, Requests.command(cmd, args, nonce, evt));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new PerigeeException("Request " + cmd + " is too large to send", e));
        }
        CompletableFuture<RpcMessage> response = new CompletableFuture<>();
        pending.put(nonce, response);
        try {
            ScheduledFuture<?> timeout = timer.schedule(() -> {
                if (pending.remove(nonce, response)) {
                    response.completeExceptionally(new PerigeeTimeoutException("Discord did not reply to " + cmd + " within " + timeouts.request()));
                }
            }, timeouts.request().toMillis(), TimeUnit.MILLISECONDS);
            response.whenComplete((message, error) -> timeout.cancel(false));
        } catch (RejectedExecutionException e) {
            pending.remove(nonce);
            return CompletableFuture.failedFuture(new PerigeeException("Connection is closed", e));
        }
        try {
            transport.write(frame);
        } catch (IOException e) {
            pending.remove(nonce);
            response.completeExceptionally(new PerigeeException("Failed to write to Discord", e));
            abandon();
        }
        return response;
    }

    /**
     * Drops a connection that Discord has stopped serving; the reader reports {@code cause} to the listener.
     */
    private void abort(Throwable cause) {
        abortCause = cause;
        close();
    }

    /**
     * A failed write means the connection is dead even if no end-of-stream was seen.
     */
    private void abandon() {
        try {
            transport.close();
        } catch (IOException ignored) {}
    }

    /**
     * Sends CLOSE so Discord ends the stream, which is the only safe way to unblock a Windows pipe read, then releases the transport.
     */
    public void close() {
        if (!closing.compareAndSet(false, true)) return;
        try {
            transport.write(FrameCodec.encode(Opcode.CLOSE, "{}"));
        } catch (IOException ignored) {
            // already gone
        }
        Thread thread = reader;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(CLOSE_WAIT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            transport.close();
        } catch (IOException ignored) {
            // nothing left to release
        }
    }

    private void readLoop() {
        Throwable cause;
        try {
            boolean open = true;
            while (open) {
                open = handle(FrameCodec.decode(transport.input()));
            }
            cause = failure;
        } catch (IOException | RuntimeException e) {
            cause = e;
        }
        if (closing.get()) cause = abortCause;
        closing.set(true);
        ScheduledFuture<?> timeout = readyTimeout;
        if (timeout != null) timeout.cancel(false);
        try {
            transport.close();
        } catch (IOException ignored) {
            // nothing left to release
        }
        PerigeeException closed = new PerigeeException("Connection closed", cause);
        pending.values().forEach(f -> f.completeExceptionally(closed));
        pending.clear();
        listener.onClosed(cause);
    }

    private boolean handle(Frame frame) throws IOException {
        switch (frame.opcode()) {
            case PING -> transport.write(FrameCodec.encode(Opcode.PONG, frame.payload()));
            case CLOSE -> {
                failure = RpcMessage.closeReason(frame.payload());
                return false;
            }
            case FRAME -> {
                RpcMessage message;
                try {
                    message = RpcMessage.parse(frame.payload());
                } catch (RuntimeException e) {
                    Logger.warn("Ignoring malformed frame from Discord", e);
                    return true;
                }
                return onMessage(message);
            }
            default -> {}
        }
        return true;
    }

    private boolean onMessage(RpcMessage message) {
        if ("READY".equals(message.evt())) {
            ScheduledFuture<?> timeout = readyTimeout;
            if (timeout != null) timeout.cancel(false);
            listener.onReady(RpcMessage.user(RpcMessage.map(message.data(), "user")));
            return true;
        }
        if (message.nonce() != null) {
            CompletableFuture<RpcMessage> response = pending.remove(message.nonce());
            if (response != null) {
                if (message.isError()) {
                    response.completeExceptionally(message.toException());
                } else {
                    response.complete(message);
                }
            }
            return true;
        }
        if (message.isError()) {
            failure = message.toException();
            return false;
        }
        if (message.evt() != null) listener.onEvent(message);
        return true;
    }

}
