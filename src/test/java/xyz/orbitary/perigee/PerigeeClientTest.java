/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.event.Disconnected;
import xyz.orbitary.perigee.support.Payloads;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.*;
import static xyz.orbitary.perigee.support.Wire.*;


class PerigeeClientTest extends ClientTestBase {
    @Test
    void handshakesWithTheApplicationId() throws Exception {
        connected();

        Map<?, ?> handshake = object(take(discord.handshakes));
        assertEquals(1L, number(handshake, "v"));
        assertEquals(Payloads.APPLICATION_ID, text(handshake, "client_id"));
    }

    @Test
    void connectCompletesWithTheLoggedInUser() throws Exception {
        PerigeeClient client = start(builder());

        assertEquals(Payloads.USER, await(client.connect()));
        assertTrue(client.isConnected());
    }

    @Test
    void setsAndClearsActivity() throws Exception {
        PerigeeClient client = connected();

        await(client.setActivity(a -> a.details("hi").largeImage("big")));
        Map<String, Object> set = discord.awaitCommand("SET_ACTIVITY");
        assertEquals(ProcessHandle.current().pid(), number(child(set, "args"), "pid"));
        assertEquals("hi", text(activity(set), "details"));
        assertEquals(0L, number(activity(set), "type"));

        await(client.clearActivity());
        assertFalse(child(discord.awaitCommand("SET_ACTIVITY"), "args").containsKey("activity"));
    }

    @Test
    void resendsPresenceWhenTheConnectionDropsBeforeDiscordAnswers() throws Exception {
        discord.responder = (session, command) -> {
            if (session == 1 && "SET_ACTIVITY".equals(command.get("cmd"))) {
                discord.dropCurrent();
                return null;
            }
            return Payloads.ok(command);
        };
        PerigeeClient client = start(builder());
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);

        await(client.connect());
        client.setActivity(a -> a.details("again")).get(10, TimeUnit.SECONDS);

        assertTrue(take(drops).reconnecting());
        assertEquals(2, discord.sessions.get());
        assertEquals("again", text(activity(discord.awaitCommand("SET_ACTIVITY")), "details"));
        assertEquals("again", text(activity(discord.awaitCommand("SET_ACTIVITY")), "details"));
    }

    @Test
    void connectFailsForRejectedApplicationId() {
        discord.rejectHandshake = true;
        PerigeeClient client = start(builder());

        assertEquals(4000, assertInstanceOf(RpcException.class, failureOf(client.connect())).code());
        assertEquals(1, discord.sessions.get(), "a rejected application ID must not be retried");
    }

    @Test
    void keepsRetryingUntilDiscordAppears() throws Exception {
        discord.close();
        PerigeeClient client = start(builder());
        var connected = client.connect();
        Thread.sleep(150);
        assertFalse(connected.isDone());

        discord = new FakeDiscord(dir.resolve("discord-ipc-0"));

        assertEquals("tester", await(connected).username());
    }

    @Test
    void rejectsNonNumericApplicationIds() {
        assertThrows(IllegalArgumentException.class, () -> PerigeeClient.builder("not-a-snowflake"));
        assertThrows(IllegalArgumentException.class, () -> PerigeeClient.builder(""));
        assertDoesNotThrow(() -> PerigeeClient.builder(42L));
    }

}
