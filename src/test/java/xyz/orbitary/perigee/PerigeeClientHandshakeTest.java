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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.await;
import static xyz.orbitary.perigee.support.Tests.failureOf;


/**
 * What the client does when Discord answers the handshake with a CLOSE frame.
 */
class PerigeeClientHandshakeTest extends ClientTestBase {
    @ParameterizedTest
    @ValueSource(ints = {4000, 4001, 4004, 4005})
    void configurationErrorsAreFatalAndNeverRetried(int code) throws Exception {
        discord.rejectHandshake = true;
        discord.handshakeCloseCode = code;
        PerigeeClient client = start(builder());

        RpcException failure = assertInstanceOf(RpcException.class, failureOf(client.connect()));
        Thread.sleep(300);

        assertEquals(code, failure.code());
        assertNull(failure.command());
        assertEquals(1, discord.sessions.get(), "a fatal close must not be retried");
        assertFalse(client.isConnected());
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 1001, 4003})
    void otherCloseCodesDuringTheHandshakeAreRetried(int code) throws Exception {
        discord.rejectedSessions = 1;
        discord.handshakeCloseCode = code;
        PerigeeClient client = start(builder());

        assertEquals("tester", await(client.connect()).username());

        assertEquals(2, discord.sessions.get());
    }

    @Test
    void aRateLimitedCloseWaitsForTheLongestDelayBeforeRetrying() throws Exception {
        discord.rejectedSessions = 1;
        discord.handshakeCloseCode = 4002;
        PerigeeClient client = start(builder());
        long started = System.nanoTime();

        await(client.connect());

        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(elapsed >= 90, "retried after only " + elapsed + "ms; the maximum delay is 100ms");
        assertEquals(2, discord.sessions.get());
    }

}
