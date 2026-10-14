/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.internal;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.PerigeeException;
import xyz.orbitary.perigee.PerigeeTimeoutException;
import xyz.orbitary.perigee.RpcException;
import xyz.orbitary.perigee.json.Json;
import xyz.orbitary.perigee.protocol.Frame;
import xyz.orbitary.perigee.protocol.FrameCodec;
import xyz.orbitary.perigee.protocol.Opcode;
import xyz.orbitary.perigee.protocol.RpcMessage;
import xyz.orbitary.perigee.support.Payloads;
import xyz.orbitary.perigee.transport.Transport;
import xyz.orbitary.perigee.user.DiscordUser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.await;
import static xyz.orbitary.perigee.support.Tests.failureOf;
import static xyz.orbitary.perigee.support.Tests.take;


class ConnectionTest {
    private static final byte[] END = new byte[0];

    /**
     * An in-memory transport: tests feed what Discord would send and inspect what the client wrote.
     */
    private static final class MemoryTransport implements Transport {
        final BlockingQueue<byte[]> incoming = new LinkedBlockingQueue<>();
        final BlockingQueue<Frame> written = new LinkedBlockingQueue<>();
        final AtomicBoolean closed = new AtomicBoolean();
        volatile boolean failWrites;

        private final InputStream input = new InputStream() {
            private byte[] current = new byte[0];
            private int position;

            @Override
            public int read() throws IOException {
                while (position >= current.length) {
                    try {
                        current = incoming.take();
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                    position = 0;
                    if (current == END) {
                        incoming.add(END);
                        return -1;
                    }
                }
                return current[position++] & 0xFF;
            }
        };

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public void write(byte[] frame) throws IOException {
            if (failWrites || closed.get()) throw new IOException("write failed");
            written.add(FrameCodec.decode(new ByteArrayInputStream(frame)));
        }

        @Override
        public void close() {
            closed.set(true);
            incoming.add(END);
        }

        void feed(Opcode opcode, String payload) {
            incoming.add(FrameCodec.encode(opcode, payload));
        }

        void endOfStream() {
            incoming.add(END);
        }

    }

    private static final class RecordingListener implements Connection.Listener {
        final BlockingQueue<DiscordUser> ready = new LinkedBlockingQueue<>();
        final BlockingQueue<RpcMessage> events = new LinkedBlockingQueue<>();
        final BlockingQueue<Optional<Throwable>> closed = new LinkedBlockingQueue<>();

        @Override
        public void onReady(DiscordUser user) {
            ready.add(user);
        }

        @Override
        public void onEvent(RpcMessage message) {
            events.add(message);
        }

        @Override
        public void onClosed(@Nullable Throwable cause) {
            closed.add(Optional.ofNullable(cause));
        }

    }

    private static final Connection.Timeouts SHORT = new Connection.Timeouts(Duration.ofMillis(100), Duration.ofMillis(100));

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final MemoryTransport transport = new MemoryTransport();
    private final RecordingListener listener = new RecordingListener();
    private final Connection connection = new Connection(transport, listener, timer, Connection.Timeouts.DEFAULT);

    @AfterEach
    void cleanUp() {
        connection.close();
        timer.shutdownNow();
    }

    @Test
    void startSendsTheHandshake() throws Exception {
        connection.start("123");

        Frame frame = take(transport.written);
        assertEquals(Opcode.HANDSHAKE, frame.opcode());
        assertEquals("{\"v\":1,\"client_id\":\"123\"}", frame.payload());
    }

    @Test
    void readyReportsTheUser() throws Exception {
        connection.start("123");

        transport.feed(Opcode.FRAME, Payloads.READY);

        assertEquals(Payloads.USER, take(listener.ready));
    }

    @Test
    void aResponseCompletesTheRequestWithTheMatchingNonce() throws Exception {
        connection.start("123");
        take(transport.written);

        CompletableFuture<RpcMessage> first = connection.request("SET_ACTIVITY", Map.of());
        CompletableFuture<RpcMessage> second = connection.request("SET_ACTIVITY", Map.of());
        String firstNonce = nonceOf(take(transport.written));
        String secondNonce = nonceOf(take(transport.written));
        assertNotEquals(firstNonce, secondNonce);

        transport.feed(Opcode.FRAME, "{\"cmd\":\"SET_ACTIVITY\",\"data\":{\"which\":2},\"nonce\":\"" + secondNonce + "\"}");
        assertEquals(2L, await(second).data().get("which"));
        assertFalse(first.isDone());

        transport.feed(Opcode.FRAME, "{\"cmd\":\"SET_ACTIVITY\",\"data\":{\"which\":1},\"nonce\":\"" + firstNonce + "\"}");
        assertEquals(1L, await(first).data().get("which"));
    }

    @Test
    void anErrorResponseFailsOnlyItsRequest() throws Exception {
        connection.start("123");
        take(transport.written);
        CompletableFuture<RpcMessage> request = connection.request("SET_ACTIVITY", Map.of());
        String nonce = nonceOf(take(transport.written));

        transport.feed(Opcode.FRAME, "{\"cmd\":\"SET_ACTIVITY\",\"evt\":\"ERROR\",\"data\":{\"code\":4000,\"message\":\"nope\"},\"nonce\":\"" + nonce + "\"}");

        assertEquals(4000, assertInstanceOf(RpcException.class, failureOf(request)).code());
        assertTrue(listener.closed.isEmpty(), "the connection must survive a rejected command");
    }

    @Test
    void dispatchedEventsReachTheListener() throws Exception {
        connection.start("123");

        transport.feed(Opcode.FRAME, Payloads.join("s"));

        RpcMessage event = take(listener.events);
        assertEquals("ACTIVITY_JOIN", event.evt());
        assertEquals("s", event.data().get("secret"));
    }

    @Test
    void pingIsAnsweredWithPongCarryingTheSamePayload() throws Exception {
        connection.start("123");
        take(transport.written);

        transport.feed(Opcode.PING, "{\"nonce\":\"abc\"}");

        assertEquals(new Frame(Opcode.PONG, "{\"nonce\":\"abc\"}"), take(transport.written));
    }

    @Test
    void aCloseFrameEndsTheConnectionWithItsReason() throws Exception {
        connection.start("123");

        transport.feed(Opcode.CLOSE, Payloads.close(4001, "bye"));

        Throwable cause = take(listener.closed).orElseThrow();
        assertEquals(4001, assertInstanceOf(RpcException.class, cause).code());
        assertTrue(transport.closed.get());
    }

    @Test
    void endOfStreamReportsACauseAndFailsPendingRequests() throws Exception {
        connection.start("123");
        take(transport.written);
        CompletableFuture<RpcMessage> pending = connection.request("SET_ACTIVITY", Map.of());

        transport.endOfStream();

        assertTrue(take(listener.closed).isPresent());
        assertInstanceOf(PerigeeException.class, failureOf(pending));
    }

    @Test
    void closingLocallyReportsNoCauseAndSendsAClosePacket() throws Exception {
        connection.start("123");
        take(transport.written);

        connection.close();

        assertEquals(Opcode.CLOSE, take(transport.written).opcode());
        assertTrue(take(listener.closed).isEmpty());
        assertTrue(transport.closed.get());
    }

    @Test
    void requestsAfterCloseFailImmediately() throws Exception {
        connection.start("123");
        connection.close();

        assertInstanceOf(PerigeeException.class, failureOf(connection.request("SET_ACTIVITY", Map.of())));
    }

    @Test
    void aFailedWriteMarksTheConnectionDeadSoTheReaderCanReportIt() throws Exception {
        connection.start("123");
        take(transport.written);
        transport.failWrites = true;

        CompletableFuture<RpcMessage> request = connection.request("SET_ACTIVITY", Map.of());

        failureOf(request);
        assertTrue(take(listener.closed).isPresent(), "the dead connection must be reported so the client can reconnect");
        assertTrue(transport.closed.get());
    }

    @Test
    void anOversizedRequestFailsWithoutBeingWritten() throws Exception {
        connection.start("123");
        take(transport.written);

        CompletableFuture<RpcMessage> request = connection.request("SET_ACTIVITY", Map.of("blob", "x".repeat(1 << 20)));

        assertInstanceOf(PerigeeException.class, failureOf(request));
        assertTrue(transport.written.isEmpty());
        assertTrue(listener.closed.isEmpty(), "the connection must survive an oversized request");
    }

    @Test
    void silenceAfterTheHandshakeDropsTheConnectionWithATimeout() throws Exception {
        Connection quick = new Connection(transport, listener, timer, SHORT);
        quick.start("123");

        Throwable cause = take(listener.closed).orElseThrow();

        assertInstanceOf(PerigeeTimeoutException.class, cause);
        assertTrue(transport.closed.get());
    }

    @Test
    void readyInTimeCancelsTheReadyTimeout() throws Exception {
        Connection quick = new Connection(transport, listener, timer, SHORT);
        quick.start("123");

        transport.feed(Opcode.FRAME, Payloads.READY);
        take(listener.ready);
        Thread.sleep(250);

        assertTrue(listener.closed.isEmpty());
        quick.close();
    }

    @Test
    void aLostReplyFailsOnlyThatRequestAndALateReplyIsIgnored() throws Exception {
        Connection quick = new Connection(transport, listener, timer, SHORT);
        quick.start("123");
        take(transport.written);
        transport.feed(Opcode.FRAME, Payloads.READY);
        take(listener.ready);

        CompletableFuture<RpcMessage> lost = quick.request("SET_ACTIVITY", Map.of());
        String lostNonce = nonceOf(take(transport.written));

        assertInstanceOf(PerigeeTimeoutException.class, failureOf(lost));
        transport.feed(Opcode.FRAME, "{\"cmd\":\"SET_ACTIVITY\",\"data\":{},\"nonce\":\"" + lostNonce + "\"}");
        CompletableFuture<RpcMessage> next = quick.request("SET_ACTIVITY", Map.of());
        transport.feed(Opcode.FRAME, "{\"cmd\":\"SET_ACTIVITY\",\"data\":{},\"nonce\":\"" + nonceOf(take(transport.written)) + "\"}");

        await(next);
        assertTrue(listener.closed.isEmpty(), "a lost reply must not drop the connection");
        quick.close();
    }

    @Test
    void malformedFramesAreSkipped() throws Exception {
        connection.start("123");

        transport.feed(Opcode.FRAME, "garbage");
        transport.feed(Opcode.FRAME, Payloads.join("after"));

        assertEquals("after", take(listener.events).data().get("secret"));
        assertTrue(listener.closed.isEmpty());
    }

    @Test
    void unsolicitedErrorWithoutANonceEndsTheConnection() throws Exception {
        connection.start("123");

        transport.feed(Opcode.FRAME, Payloads.unsolicitedError(4002, "x"));

        assertEquals(4002, assertInstanceOf(RpcException.class, take(listener.closed).orElseThrow()).code());
    }

    private static String nonceOf(Frame frame) {
        return (String)Objects.requireNonNull(((Map<?, ?>)Json.parse(frame.payload()).orElseThrow()).get("nonce"));
    }


}
