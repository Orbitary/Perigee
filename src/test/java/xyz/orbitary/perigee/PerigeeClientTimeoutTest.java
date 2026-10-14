/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.internal.Connection;
import xyz.orbitary.perigee.support.Payloads;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.await;
import static xyz.orbitary.perigee.support.Tests.failureOf;


class PerigeeClientTimeoutTest extends ClientTestBase {
    private static final Connection.Timeouts SHORT = new Connection.Timeouts(Duration.ofMillis(150), Duration.ofMillis(150));

    @Test
    void aSessionThatNeverSendsReadyIsDroppedAndRetried() throws Exception {
        discord.silentSessions = 1;
        PerigeeClient client = start(builder().timeouts(SHORT));

        assertEquals("tester", await(client.connect()).username());

        assertEquals(2, discord.sessions.get());
    }

    @Test
    void aLostReplyFailsTheUpdateAndTheNextOneStillGoesThrough() throws Exception {
        PerigeeClient client = start(builder().timeouts(SHORT));
        await(client.connect());
        discord.responder = (session, command) -> "SET_ACTIVITY".equals(command.get("cmd")) ? null : Payloads.ok(command);

        assertInstanceOf(PerigeeTimeoutException.class, failureOf(client.setActivity(a -> a.details("lost"))));
        discord.responder = (session, command) -> Payloads.ok(command);

        await(client.setActivity(a -> a.details("later")));
        assertTrue(client.isConnected());
    }

    @Test
    void connectWithATimeoutCompletesOnceReady() throws Exception {
        PerigeeClient client = start(builder());

        assertEquals("tester", await(client.connect(Duration.ofSeconds(5))).username());
    }

    @Test
    void connectWithATimeoutFailsWhenDiscordIsNotReadyInTime() throws Exception {
        discord.close();
        PerigeeClient client = start(builder());

        assertInstanceOf(PerigeeTimeoutException.class, failureOf(client.connect(Duration.ofMillis(200))));
        assertFalse(client.isConnected());
    }

    @Test
    void connectWithATimeoutRejectsNonPositiveDurations() {
        PerigeeClient client = start(builder());

        assertThrows(IllegalArgumentException.class, () -> client.connect(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> client.connect(Duration.ofSeconds(-1)));
    }

}
