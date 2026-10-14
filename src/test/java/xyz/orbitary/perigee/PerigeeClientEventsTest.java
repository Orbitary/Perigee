/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.event.Join;
import xyz.orbitary.perigee.event.JoinRequest;
import xyz.orbitary.perigee.event.PerigeeEvent;
import xyz.orbitary.perigee.event.Ready;
import xyz.orbitary.perigee.event.Spectate;
import xyz.orbitary.perigee.event.Subscription;
import xyz.orbitary.perigee.support.Payloads;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.*;
import static xyz.orbitary.perigee.support.Wire.*;


class PerigeeClientEventsTest extends ClientTestBase {
    @Test
    void subscribesToAllJoinEventsAfterReady() throws Exception {
        connected();

        Set<String> events = new HashSet<>();
        while (events.size() < 3) {
            events.add(text(discord.awaitCommand("SUBSCRIBE"), "evt"));
        }

        assertEquals(Set.of("ACTIVITY_JOIN", "ACTIVITY_SPECTATE", "ACTIVITY_JOIN_REQUEST"), events);
    }

    @Test
    void mapsJoinSpectateAndJoinRequestEvents() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<PerigeeEvent> events = new LinkedBlockingQueue<>();
        client.on(PerigeeEvent.class, events::add);
        await(client.connect());
        assertInstanceOf(Ready.class, take(events));

        discord.push(Payloads.join("join-secret"));
        discord.push(Payloads.spectate("spectate-secret"));
        discord.push(Payloads.joinRequest(Payloads.JOINER));

        assertEquals(new Join("join-secret"), take(events));
        assertEquals(new Spectate("spectate-secret"), take(events));
        assertEquals(new JoinRequest(Payloads.JOINER_USER), take(events));
    }

    @Test
    void deliversEventsInOrder() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Join> joins = new LinkedBlockingQueue<>();
        client.on(Join.class, joins::add);
        await(client.connect());

        for (int i = 0; i < 20; i++) {
            discord.push(Payloads.join(Integer.toString(i)));
        }

        for (int i = 0; i < 20; i++) {
            assertEquals(Integer.toString(i), take(joins).secret());
        }
    }

    @Test
    void handlersOnlyReceiveTheirOwnEventType() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Spectate> spectates = new LinkedBlockingQueue<>();
        BlockingQueue<Join> joins = new LinkedBlockingQueue<>();
        client.on(Spectate.class, spectates::add);
        client.on(Join.class, joins::add);
        await(client.connect());

        discord.push(Payloads.join("j"));

        assertEquals("j", take(joins).secret());
        assertTrue(spectates.isEmpty());
    }

    @Test
    void cancelledSubscriptionStopsDelivery() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Join> cancelled = new LinkedBlockingQueue<>();
        BlockingQueue<Join> kept = new LinkedBlockingQueue<>();
        Subscription subscription = client.on(Join.class, cancelled::add);
        client.on(Join.class, kept::add);
        await(client.connect());
        subscription.cancel();

        discord.push(Payloads.join("x"));

        assertEquals("x", take(kept).secret());
        assertTrue(cancelled.isEmpty());
    }

    @Test
    void throwingHandlerDoesNotAffectOtherHandlersOrLaterEvents() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Join> joins = new LinkedBlockingQueue<>();
        client.on(Join.class, join -> {
            throw new IllegalStateException("handler failure");
        });
        client.on(Join.class, joins::add);
        await(client.connect());

        discord.push(Payloads.join("1"));
        discord.push(Payloads.join("2"));

        assertEquals("1", take(joins).secret());
        assertEquals("2", take(joins).secret());
        assertTrue(client.isConnected());
    }

    @Test
    void unknownEventsAreIgnored() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Join> joins = new LinkedBlockingQueue<>();
        client.on(Join.class, joins::add);
        await(client.connect());

        discord.push(Payloads.dispatch("SOMETHING_NEW", Map.of("a", 1)));
        discord.push(Payloads.join("after"));

        assertEquals("after", take(joins).secret());
        assertEquals(1, discord.sessions.get());
    }

    @Test
    void handlerMayUseTheClient() throws Exception {
        PerigeeClient client = start(builder());
        client.on(Ready.class, ready -> client.setActivity(a -> a.details("from handler")));

        await(client.connect());

        assertEquals("from handler", text(activity(discord.awaitCommand("SET_ACTIVITY")), "details"));
    }

    @Test
    void acceptJoinRequestSendsTheInviteCommand() throws Exception {
        PerigeeClient client = connected();

        await(client.acceptJoinRequest("77"));

        assertEquals("77", text(child(discord.awaitCommand("SEND_ACTIVITY_JOIN_INVITE"), "args"), "user_id"));
    }

    @Test
    void rejectJoinRequestSendsTheCloseCommand() throws Exception {
        PerigeeClient client = connected();

        await(client.rejectJoinRequest("77"));

        assertEquals("77", text(child(discord.awaitCommand("CLOSE_ACTIVITY_REQUEST"), "args"), "user_id"));
    }

    @Test
    void joinRequestAnswersFailWhenNotConnected() {
        PerigeeClient client = start(builder());

        for (var answer : List.of(client.acceptJoinRequest("1"), client.rejectJoinRequest("1"))) {
            assertInstanceOf(PerigeeException.class, failureOf(answer));
        }
    }

    @Test
    void joinRequestAnswerSurfacesDiscordErrors() throws Exception {
        PerigeeClient client = connected();
        discord.responder = (session, command) -> Payloads.error(command, 4005, "no such request");

        assertEquals(4005, assertInstanceOf(RpcException.class, failureOf(client.acceptJoinRequest("1"))).code());
    }

}
