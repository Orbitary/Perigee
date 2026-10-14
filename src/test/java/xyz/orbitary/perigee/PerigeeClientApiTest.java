/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.event.Disconnected;
import xyz.orbitary.perigee.event.Ready;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.*;
import static xyz.orbitary.perigee.support.Wire.activity;
import static xyz.orbitary.perigee.support.Wire.number;


class PerigeeClientApiTest extends ClientTestBase {
    @Test
    void stateFollowsTheConnectionLifeCycle() throws Exception {
        PerigeeClient client = start(builder().autoReconnect(false));
        assertEquals(ConnectionState.IDLE, client.state());
        assertFalse(client.isReady());

        await(client.connect());
        assertEquals(ConnectionState.READY, client.state());
        assertTrue(client.isReady());
        assertTrue(client.isConnected());

        client.close();
        assertEquals(ConnectionState.CLOSED, client.state());
        assertFalse(client.isReady());
    }

    @Test
    void stateIsBackoffWhileWaitingForDiscordAndReadyOnceItAppears() throws Exception {
        discord.close();
        PerigeeClient client = start(builder().reconnectDelay(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(200)));
        CompletableFuture<?> connecting = client.connect();
        Thread.sleep(100);

        assertEquals(ConnectionState.BACKOFF, client.state());

        discord = new FakeDiscord(dir.resolve("discord-ipc-0"));
        await(connecting);
        assertEquals(ConnectionState.READY, client.state());
    }

    @Test
    void aDroppedConnectionWithoutAutoReconnectEndsInClosed() throws Exception {
        PerigeeClient client = start(builder().autoReconnect(false));
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);
        await(client.connect());

        discord.dropCurrent();
        take(drops);

        assertEquals(ConnectionState.CLOSED, client.state());
    }

    @Test
    void aFatalHandshakeCloseEndsInClosed() {
        discord.rejectHandshake = true;
        PerigeeClient client = start(builder());

        failureOf(client.connect());

        assertEquals(ConnectionState.CLOSED, client.state());
    }

    @Test
    void aLateHandlerCanReplayTheCurrentReadyEvent() throws Exception {
        PerigeeClient client = connected();
        BlockingQueue<Ready> replayed = new LinkedBlockingQueue<>();
        BlockingQueue<Ready> plain = new LinkedBlockingQueue<>();

        client.on(Ready.class, replayed::add, true);
        client.on(Ready.class, plain::add);

        assertEquals("tester", take(replayed).user().username());
        Thread.sleep(100);
        assertTrue(plain.isEmpty(), "without replayLast a late handler gets nothing");
    }

    @Test
    void replayedReadyIsNotDeliveredTwiceOrWhileNotReady() throws Exception {
        PerigeeClient client = start(builder().autoReconnect(false));
        BlockingQueue<Ready> early = new LinkedBlockingQueue<>();
        client.on(Ready.class, early::add, true);
        await(client.connect());
        take(early);
        Thread.sleep(100);
        assertTrue(early.isEmpty(), "a handler registered before ready must see the event once");

        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);
        discord.dropCurrent();
        take(drops);
        BlockingQueue<Ready> afterDrop = new LinkedBlockingQueue<>();
        client.on(Ready.class, afterDrop::add, true);
        Thread.sleep(100);

        assertTrue(afterDrop.isEmpty(), "a stale Ready must not be replayed after the connection dropped");
    }

    @Test
    void disconnectedIsNotFiredWhileDiscordHasNeverBeenReachable() throws Exception {
        discord.close();
        PerigeeClient client = start(builder());
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);

        client.connect();
        Thread.sleep(300);

        assertTrue(drops.isEmpty());
    }

    @Test
    void updateActivityBuildsOnTheLastRequestedActivity() throws Exception {
        PerigeeClient client = connected();
        await(client.setActivity(a -> a.details("first").state("keep me")));

        await(client.updateActivity(b -> b.details("second").extra("new_field", 5)));

        discord.awaitCommand("SET_ACTIVITY");
        var sent = activity(discord.awaitCommand("SET_ACTIVITY"));
        assertEquals("second", sent.get("details"));
        assertEquals("keep me", sent.get("state"));
        assertEquals(5, number(sent, "new_field"));
    }

    @Test
    void updateActivityStartsFromAnEmptyActivityWhenNothingWasSet() throws Exception {
        PerigeeClient client = connected();

        await(client.updateActivity(b -> b.details("only")));

        assertEquals("only", activity(discord.awaitCommand("SET_ACTIVITY")).get("details"));
    }

    @Test
    void anIdenticalActivityIsSentOnlyOnce() throws Exception {
        PerigeeClient client = connected();

        await(client.setActivity(a -> a.details("same")));
        await(client.setActivity(a -> a.details("same")));
        await(client.setActivity(a -> a.details("different")));

        assertEquals("same", activity(discord.awaitCommand("SET_ACTIVITY")).get("details"));
        assertEquals("different", activity(discord.awaitCommand("SET_ACTIVITY")).get("details"));
    }

    @Test
    void handlersRunOnTheSingleEventThreadByDefault() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<String> threads = new LinkedBlockingQueue<>();
        client.on(Ready.class, ready -> threads.add(Thread.currentThread().getName()));
        client.on(Ready.class, ready -> threads.add(Thread.currentThread().getName()));

        await(client.connect());

        assertEquals("perigee-events", take(threads));
        assertEquals("perigee-events", take(threads));
    }

    @Test
    void aSlowHandlerDelaysTheOnesAfterIt() throws Exception {
        PerigeeClient client = start(builder());
        CountDownLatch release = new CountDownLatch(1);
        BlockingQueue<String> order = new LinkedBlockingQueue<>();
        client.on(Ready.class, ready -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            order.add("slow");
        });
        client.on(Ready.class, ready -> order.add("fast"));

        await(client.connect());
        Thread.sleep(150);
        assertTrue(order.isEmpty(), "the second handler must wait for the first");
        release.countDown();

        assertEquals("slow", take(order));
        assertEquals("fast", take(order));
    }

    @Test
    void aCustomEventExecutorRunsHandlersAndIsNeverShutDownByTheClient() throws Exception {
        ExecutorService custom = Executors.newSingleThreadExecutor(task -> Thread.ofPlatform().daemon().name("custom-events").unstarted(task));
        try {
            Executor executor = custom;
            PerigeeClient client = start(builder().eventExecutor(executor));
            BlockingQueue<String> threads = new LinkedBlockingQueue<>();
            client.on(Ready.class, ready -> threads.add(Thread.currentThread().getName()));

            await(client.connect());
            assertEquals("custom-events", take(threads));

            client.close();
            assertFalse(custom.isShutdown(), "the caller owns the executor");
        } finally {
            custom.shutdownNow();
        }
    }

    @Test
    void preferredIndexMustBeAValidIpcIndex() {
        assertThrows(IllegalArgumentException.class, () -> PerigeeClient.builder("1").preferredIndex(-1));
        assertThrows(IllegalArgumentException.class, () -> PerigeeClient.builder("1").preferredIndex(10));
        assertDoesNotThrow(() -> PerigeeClient.builder("1").preferredIndex(9));
    }

}
