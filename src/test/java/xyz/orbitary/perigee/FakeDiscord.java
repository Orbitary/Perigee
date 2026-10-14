/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.jetbrains.annotations.Nullable;
import xyz.orbitary.perigee.json.Json;
import xyz.orbitary.perigee.protocol.Frame;
import xyz.orbitary.perigee.protocol.FrameCodec;
import xyz.orbitary.perigee.protocol.Opcode;
import xyz.orbitary.perigee.support.Payloads;
import xyz.orbitary.perigee.support.UnixTestServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A scriptable stand-in for the Discord desktop client, listening on a Unix socket.
 */
final class FakeDiscord implements AutoCloseable {
    @FunctionalInterface
    interface Responder {
        @Nullable String respond(int session, Map<String, Object> command);

    }

    final Path socket;
    final BlockingQueue<String> handshakes = new LinkedBlockingQueue<>();
    final BlockingQueue<Map<String, Object>> commands = new LinkedBlockingQueue<>();
    final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
    final AtomicInteger sessions = new AtomicInteger();
    volatile boolean rejectHandshake;
    volatile boolean fragmentWrites;
    volatile int silentSessions;
    volatile Responder responder = (session, command) -> Payloads.ok(command);

    private final UnixTestServer server;
    private final Object writeLock = new Object();
    private volatile @Nullable SocketChannel current;

    FakeDiscord(Path socket) throws IOException {
        this.socket = socket;
        this.server = new UnixTestServer(socket);
        Thread.ofPlatform().daemon().name("fake-discord").start(this::acceptLoop);
    }

    Map<String, Object> awaitCommand(String cmd) throws InterruptedException {
        while (true) {
            Map<String, Object> command = Objects.requireNonNull(commands.poll(5, TimeUnit.SECONDS), "timed out waiting for command " + cmd);
            if (cmd.equals(command.get("cmd"))) return command;
        }
    }

    Frame awaitFrame(Opcode opcode) throws InterruptedException {
        while (true) {
            Frame frame = Objects.requireNonNull(frames.poll(5, TimeUnit.SECONDS), "timed out waiting for frame " + opcode);
            if (frame.opcode() == opcode) return frame;
        }
    }

    void push(String payload) {
        pushFrame(Opcode.FRAME, payload);
    }

    void pushFrame(Opcode opcode, String payload) {
        pushBytes(FrameCodec.encode(opcode, payload));
    }

    void pushBytes(byte[] bytes) {
        SocketChannel channel = Objects.requireNonNull(current, "no client connected");
        try {
            send(channel, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void dropCurrent() {
        SocketChannel channel = current;
        if (channel == null) return;
        try {
            Thread.sleep(50);
            channel.shutdownOutput();
        } catch (IOException ignored) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void acceptLoop() {
        try {
            while (true) {
                SocketChannel channel = server.accept();
                int session = sessions.incrementAndGet();
                Thread.ofPlatform().daemon().name("fake-discord-session-" + session).start(() -> serve(channel, session));
            }
        } catch (IOException ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void serve(SocketChannel channel, int session) {
        try (channel) {
            InputStream in = Channels.newInputStream(channel);
            current = channel;
            Frame handshake = FrameCodec.decode(in);
            handshakes.add(handshake.payload());
            if (rejectHandshake) {
                send(channel, FrameCodec.encode(Opcode.CLOSE, Payloads.close(4000, "Invalid Client ID")));
                return;
            }
            if (session > silentSessions) send(channel, FrameCodec.encode(Opcode.FRAME, Payloads.READY));

            while (true) {
                Frame frame = FrameCodec.decode(in);
                frames.add(frame);
                if (frame.opcode() == Opcode.CLOSE) return;
                if (frame.opcode() != Opcode.FRAME) continue;
                Map<String, Object> command = (Map<String, Object>)Json.parse(frame.payload()).orElseThrow();
                commands.add(command);
                String reply = responder.respond(session, command);
                if (reply != null) send(channel, FrameCodec.encode(Opcode.FRAME, reply));
            }
        } catch (IOException ignored) {}
    }

    private void send(SocketChannel channel, byte[] bytes) throws IOException {
        synchronized (writeLock) {
            if (fragmentWrites) {
                for (byte b : bytes) {
                    channel.write(ByteBuffer.wrap(new byte[]{b}));
                }
                return;
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
        SocketChannel channel = current;
        if (channel != null) channel.close();
    }

}
