/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.orbitary.perigee.event.Disconnected;
import xyz.orbitary.perigee.event.Join;
import xyz.orbitary.perigee.json.Json;
import xyz.orbitary.perigee.protocol.Frame;
import xyz.orbitary.perigee.protocol.FrameCodec;
import xyz.orbitary.perigee.protocol.Opcode;
import xyz.orbitary.perigee.transport.UnixSocketTransport;
import xyz.orbitary.perigee.user.DiscordUser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


class PerigeeClientTest {
    private static final String READY = "{\"cmd\":\"DISPATCH\",\"data\":{\"v\":1,\"config\":{},\"user\":{\"id\":\"42\",\"username\":\"tester\",\"global_name\":\"Tester\",\"avatar\":null}},\"evt\":\"READY\",\"nonce\":null}";

    @TempDir
    Path dir;

    private final List<PerigeeClient> clients = new ArrayList<>();
    private FakeDiscord discord;

    @BeforeEach
    void startServer() throws IOException {
        discord = new FakeDiscord(dir.resolve("discord-ipc-0"));
    }

    @AfterEach
    void stop() throws IOException {
        clients.forEach(PerigeeClient::close);
        discord.close();
    }

    private PerigeeClient.Builder builder() {
        return PerigeeClient.builder("1234567890").transport(() -> UnixSocketTransport.connect(discord.socket.toString()));
    }

    private PerigeeClient start(PerigeeClient.Builder builder) {
        PerigeeClient client = builder.build();
        clients.add(client);
        return client;
    }

    @Test
    void handshakesSetsActivityAndDeliversEvents() throws Exception {
        discord.joinSecret = "abc";
        PerigeeClient client = start(builder());
        BlockingQueue<Join> joins = new LinkedBlockingQueue<>();
        client.on(Join.class, joins::add);

        DiscordUser user = client.connect().get(5, TimeUnit.SECONDS);
        assertEquals(new DiscordUser("42", "tester", "Tester", null), user);
        assertEquals(new Join("abc"), joins.poll(5, TimeUnit.SECONDS));

        client.setActivity(a -> a.details("hi").largeImage("big")).get(5, TimeUnit.SECONDS);
        Map<?, ?> args = child(discord.awaitCommand("SET_ACTIVITY"), "args");
        assertEquals(ProcessHandle.current().pid(), number(args, "pid"));
        Map<?, ?> activity = child(args, "activity");
        assertEquals("hi", text(activity, "details"));
        assertEquals(0L, number(activity, "type"));

        client.clearActivity().get(5, TimeUnit.SECONDS);
        assertFalse(child(discord.awaitCommand("SET_ACTIVITY"), "args").containsKey("activity"));
    }

    @Test
    void resendsPresenceAfterDiscordRestarts() throws Exception {
        discord.dropFirstSessionAfterActivity = true;
        PerigeeClient client = start(builder().reconnectDelay(Duration.ofMillis(20), Duration.ofMillis(100)));
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);

        client.connect().get(5, TimeUnit.SECONDS);
        client.setActivity(a -> a.details("again")).get(10, TimeUnit.SECONDS);

        Disconnected drop = drops.poll(5, TimeUnit.SECONDS);
        assertNotNull(drop);
        assertTrue(drop.reconnecting());
        assertEquals(2, discord.sessions.get());
        assertEquals("again", activityDetails(discord.awaitCommand("SET_ACTIVITY")));
        assertEquals("again", activityDetails(discord.awaitCommand("SET_ACTIVITY")));
    }

    @Test
    void connectFailsForRejectedApplicationId() {
        discord.rejectHandshake = true;
        PerigeeClient client = start(builder());

        ExecutionException e = assertThrows(ExecutionException.class, () -> client.connect().get(5, TimeUnit.SECONDS));
        RpcException rpc = assertInstanceOf(RpcException.class, e.getCause());
        assertEquals(4000, rpc.code());
    }

    @Test
    void keepsRetryingUntilDiscordAppears() throws Exception {
        discord.close();
        PerigeeClient client = start(builder().reconnectDelay(Duration.ofMillis(20), Duration.ofMillis(50)));
        var connected = client.connect();
        Thread.sleep(150);
        assertFalse(connected.isDone());

        discord = new FakeDiscord(dir.resolve("discord-ipc-0"));
        assertEquals("tester", connected.get(5, TimeUnit.SECONDS).username());
    }

    @Test
    void rejectsNonNumericApplicationIds() {
        assertThrows(IllegalArgumentException.class, () -> PerigeeClient.builder("not-a-snowflake"));
    }

    private static String activityDetails(Map<String, Object> command) {
        return text(child(child(command, "args"), "activity"), "details");
    }

    private static Map<?, ?> child(Map<?, ?> map, String key) {
        return (Map<?, ?>)Objects.requireNonNull(map.get(key), key);
    }

    private static long number(Map<?, ?> map, String key) {
        return ((Number)Objects.requireNonNull(map.get(key), key)).longValue();
    }

    private static String text(Map<?, ?> map, String key) {
        return (String)Objects.requireNonNull(map.get(key), key);
    }

    private static final class FakeDiscord implements AutoCloseable {
        final Path socket;
        final ServerSocketChannel server;
        final BlockingQueue<Map<String, Object>> commands = new LinkedBlockingQueue<>();
        final AtomicInteger sessions = new AtomicInteger();
        volatile @Nullable String joinSecret;
        volatile boolean dropFirstSessionAfterActivity;
        volatile boolean rejectHandshake;

        FakeDiscord(Path socket) throws IOException {
            this.socket = socket;
            this.server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(socket));
            Thread.ofPlatform().daemon().name("fake-discord").start(this::acceptLoop);
        }

        Map<String, Object> awaitCommand(String cmd) throws InterruptedException {
            while (true) {
                Map<String, Object> command = commands.poll(5, TimeUnit.SECONDS);
                assertNotNull(command, "timed out waiting for " + cmd);
                if (cmd.equals(command.get("cmd"))) return command;
            }
        }

        private void acceptLoop() {
            try {
                while (true) {
                    SocketChannel channel = server.accept();
                    int session = sessions.incrementAndGet();
                    Thread.ofPlatform().daemon().start(() -> serve(channel, session));
                }
            } catch (IOException ignored) {
                // server closed
            }
        }

        @SuppressWarnings("unchecked")
        private void serve(SocketChannel channel, int session) {
            try (channel) {
                InputStream in = Channels.newInputStream(channel);
                OutputStream out = Channels.newOutputStream(channel);
                Frame handshake = FrameCodec.decode(in);
                assertEquals(Opcode.HANDSHAKE, handshake.opcode());
                if (rejectHandshake) {
                    out.write(FrameCodec.encode(Opcode.CLOSE, "{\"code\":4000,\"message\":\"Invalid Client ID\"}"));
                    return;
                }
                out.write(FrameCodec.encode(Opcode.FRAME, READY));

                while (true) {
                    Frame frame = FrameCodec.decode(in);
                    if (frame.opcode() == Opcode.CLOSE) return;
                    Map<String, Object> command = (Map<String, Object>)Json.parse(frame.payload()).orElseThrow();
                    commands.add(command);
                    boolean dropNow = dropFirstSessionAfterActivity && session == 1 && "SET_ACTIVITY".equals(command.get("cmd"));
                    if (dropNow) return;
                    out.write(FrameCodec.encode(Opcode.FRAME, reply(command)));
                    String secret = joinSecret;
                    if (secret != null && "ACTIVITY_JOIN_REQUEST".equals(command.get("evt"))) {
                        out.write(FrameCodec.encode(Opcode.FRAME, Json.write(Map.of("cmd", "DISPATCH", "evt", "ACTIVITY_JOIN", "data", Map.of("secret", secret)))));
                    }
                }
            } catch (IOException ignored) {
                // client went away
            }
        }

        private static String reply(Map<String, Object> command) {
            return Json.write(Map.of("cmd", command.get("cmd"), "data", Map.of(), "nonce", command.get("nonce")));
        }

        @Override
        public void close() throws IOException {
            server.close();
            java.nio.file.Files.deleteIfExists(socket);
        }

    }

}
