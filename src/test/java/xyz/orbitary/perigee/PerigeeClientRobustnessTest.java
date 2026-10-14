/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.orbitary.perigee.event.Disconnected;
import xyz.orbitary.perigee.event.Join;
import xyz.orbitary.perigee.event.Ready;
import xyz.orbitary.perigee.protocol.Opcode;
import xyz.orbitary.perigee.support.Payloads;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.*;


class PerigeeClientRobustnessTest extends ClientTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"this is not json", "[1,2,3]", "null", "\"text\"", "", "{}", "{\"cmd\":\"SET_ACTIVITY\",\"data\":{},\"nonce\":\"never-sent\"}"})
    void unusableFramesAreIgnored(String payload) throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Join> joins = new LinkedBlockingQueue<>();
        client.on(Join.class, joins::add);
        await(client.connect());

        discord.push(payload);
        discord.push(Payloads.join("alive"));

        assertEquals("alive", take(joins).secret());
        assertTrue(client.isConnected());
        assertEquals(1, discord.sessions.get(), "the connection must have survived");
    }

    @Test
    void pingIsAnsweredWithAMatchingPong() throws Exception {
        PerigeeClient client = connected();

        discord.pushFrame(Opcode.PING, "{\"n\":7}");

        assertEquals("{\"n\":7}", discord.awaitFrame(Opcode.PONG).payload());
        assertTrue(client.isConnected());
    }

    @Test
    void closeFrameFromDiscordDisconnectsWithItsReason() throws Exception {
        PerigeeClient client = start(builder().autoReconnect(false));
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);
        await(client.connect());

        discord.pushFrame(Opcode.CLOSE, Payloads.close(4001, "shutting down"));

        assertEquals(4001, assertInstanceOf(RpcException.class, take(drops).cause()).code());
    }

    @Test
    void errorEventWithoutANonceReconnects() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        BlockingQueue<Ready> ready = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);
        client.on(Ready.class, ready::add);
        await(client.connect());
        take(ready);

        discord.push(Payloads.unsolicitedError(4002, "rate limited"));

        assertEquals(4002, assertInstanceOf(RpcException.class, take(drops).cause()).code());
        take(ready);
        assertEquals(2, discord.sessions.get());
    }

    @Test
    void unknownOpcodeDropsTheConnectionAndReconnects() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        BlockingQueue<Ready> ready = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);
        client.on(Ready.class, ready::add);
        await(client.connect());
        take(ready);

        discord.pushBytes(new byte[]{9, 0, 0, 0, 0, 0, 0, 0});

        assertNotNull(take(drops).cause());
        take(ready);
        assertEquals(2, discord.sessions.get());
    }

    @Test
    void truncatedFrameFollowedByDisconnectReconnects() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        BlockingQueue<Ready> ready = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);
        client.on(Ready.class, ready::add);
        await(client.connect());
        take(ready);

        discord.pushBytes(new byte[]{1, 0, 0});
        discord.dropCurrent();

        assertTrue(take(drops).reconnecting());
        take(ready);
        assertEquals(2, discord.sessions.get());
    }

    @Test
    void reassemblesFramesDeliveredOneByteAtATime() throws Exception {
        discord.fragmentWrites = true;
        PerigeeClient client = start(builder());
        BlockingQueue<Join> joins = new LinkedBlockingQueue<>();
        client.on(Join.class, joins::add);

        assertEquals("tester", await(client.connect()).username());
        discord.push(Payloads.join("slow"));

        assertEquals("slow", take(joins).secret());
    }

    @Test
    void survivesRepeatedDisconnects() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Ready> ready = new LinkedBlockingQueue<>();
        client.on(Ready.class, ready::add);
        await(client.connect());
        take(ready);

        for (int i = 2; i <= 6; i++) {
            discord.dropCurrent();
            take(ready);
            assertEquals(i, discord.sessions.get());
        }

        assertTrue(client.isConnected());
    }

}
