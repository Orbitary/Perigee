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
import xyz.orbitary.perigee.protocol.Opcode;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.*;
import static xyz.orbitary.perigee.support.Wire.*;


class PerigeeClientLifecycleTest extends ClientTestBase {
    @Test
    void isNotConnectedBeforeConnect() {
        assertFalse(start(builder()).isConnected());
        assertEquals(0, discord.sessions.get());
    }

    @Test
    void connectIsIdempotent() throws Exception {
        PerigeeClient client = start(builder());

        CompletableFuture<?> first = client.connect();
        CompletableFuture<?> second = client.connect();
        await(first);

        assertSame(first, second);
        assertEquals(1, discord.sessions.get());
    }

    @Test
    void activitySetBeforeConnectIsSentOnceReady() throws Exception {
        PerigeeClient client = start(builder());
        CompletableFuture<Void> pending = client.setActivity(a -> a.details("early"));
        assertFalse(pending.isDone());

        await(client.connect());
        await(pending);

        assertEquals("early", text(activity(discord.awaitCommand("SET_ACTIVITY")), "details"));
    }

    @Test
    void readyIsFiredAgainAfterEveryReconnect() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Ready> ready = new LinkedBlockingQueue<>();
        client.on(Ready.class, ready::add);
        await(client.connect());
        take(ready);

        discord.dropCurrent();

        assertEquals("tester", take(ready).user().username());
        assertEquals(2, discord.sessions.get());
    }

    @Test
    void closeSendsACloseFrameAndIsIdempotent() throws Exception {
        PerigeeClient client = connected();

        client.close();
        client.close();

        discord.awaitFrame(Opcode.CLOSE);
        assertFalse(client.isConnected());
    }

    @Test
    void callsAfterCloseFail() throws Exception {
        PerigeeClient client = connected();
        client.close();

        assertInstanceOf(PerigeeException.class, failureOf(client.setActivity(a -> a.details("late"))));
        assertInstanceOf(PerigeeException.class, failureOf(client.clearActivity()));
        assertInstanceOf(PerigeeException.class, failureOf(client.connect()));
    }

    @Test
    void closeWhileStillConnectingFailsTheConnectFuture() throws Exception {
        discord.close();
        PerigeeClient client = start(builder());
        CompletableFuture<?> connecting = client.connect();
        Thread.sleep(100);

        client.close();

        assertInstanceOf(PerigeeException.class, failureOf(connecting));
    }

    @Test
    void unreachableDiscordFailsConnectWhenAutoReconnectIsOff() throws Exception {
        discord.close();
        PerigeeClient client = start(builder().autoReconnect(false));

        assertInstanceOf(PerigeeException.class, failureOf(client.connect()));
    }

    @Test
    void droppedConnectionEndsTheClientWhenAutoReconnectIsOff() throws Exception {
        PerigeeClient client = start(builder().autoReconnect(false));
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);
        await(client.connect());

        discord.dropCurrent();

        assertFalse(take(drops).reconnecting());
        Thread.sleep(150);
        assertEquals(1, discord.sessions.get(), "must not reconnect");
        assertFalse(client.isConnected());
        assertInstanceOf(PerigeeException.class, failureOf(client.setActivity(a -> a.details("x"))));
    }

}
