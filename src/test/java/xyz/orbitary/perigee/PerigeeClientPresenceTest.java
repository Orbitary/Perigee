/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.activity.Activity;
import xyz.orbitary.perigee.activity.ActivityFlag;
import xyz.orbitary.perigee.activity.ActivityType;
import xyz.orbitary.perigee.activity.StatusDisplayType;
import xyz.orbitary.perigee.activity.Timestamps;
import xyz.orbitary.perigee.event.Disconnected;
import xyz.orbitary.perigee.support.Payloads;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.*;
import static xyz.orbitary.perigee.support.Wire.*;


class PerigeeClientPresenceTest extends ClientTestBase {
    private int countCommands(String cmd) {
        return (int)discord.commands.stream().filter(command -> cmd.equals(command.get("cmd"))).count();
    }

    @Test
    void sendsEveryFieldOfTheActivity() throws Exception {
        PerigeeClient client = connected();
        Activity activity = Activity.of(a -> a
          .name("Perigee")
          .type(ActivityType.LISTENING)
          .details("Song")
          .detailsUrl("https://example.com/song")
          .state("Artist")
          .stateUrl("https://example.com/artist")
          .statusDisplay(StatusDisplayType.DETAILS)
          .timestamps(Timestamps.between(Instant.ofEpochSecond(1_700_000_000L), Instant.ofEpochSecond(1_700_000_200L)))
          .party("party-1", 2, 4)
          .largeImage("big", "Big")
          .largeUrl("https://example.com/big")
          .smallImage("small", "Small")
          .inviteCoverImage("cover")
          .joinSecret("join")
          .spectateSecret("spectate")
          .matchSecret("match")
          .button("Docs", "https://example.com/docs")
          .button("Source", "https://example.com/source")
          .instance(true)
          .flag(ActivityFlag.JOIN, ActivityFlag.SPECTATE));

        await(client.setActivity(activity));

        Map<?, ?> sent = activity(discord.awaitCommand("SET_ACTIVITY"));
        assertEquals("Perigee", text(sent, "name"));
        assertEquals(2L, number(sent, "type"));
        assertEquals("Song", text(sent, "details"));
        assertEquals("https://example.com/song", text(sent, "details_url"));
        assertEquals("Artist", text(sent, "state"));
        assertEquals("https://example.com/artist", text(sent, "state_url"));
        assertEquals(2L, number(sent, "status_display_type"));
        assertEquals(1_700_000_000L, number(child(sent, "timestamps"), "start"));
        assertEquals(1_700_000_200L, number(child(sent, "timestamps"), "end"));
        assertEquals("party-1", text(child(sent, "party"), "id"));
        assertEquals(List.of(2L, 4L), child(sent, "party").get("size"));
        Map<?, ?> assets = child(sent, "assets");
        assertEquals("big", text(assets, "large_image"));
        assertEquals("Big", text(assets, "large_text"));
        assertEquals("https://example.com/big", text(assets, "large_url"));
        assertEquals("small", text(assets, "small_image"));
        assertEquals("cover", text(assets, "invite_cover_image"));
        Map<?, ?> secrets = child(sent, "secrets");
        assertEquals("join", text(secrets, "join"));
        assertEquals("spectate", text(secrets, "spectate"));
        assertEquals("match", text(secrets, "match"));
        assertEquals(2, ((List<?>)Objects.requireNonNull(sent.get("buttons"))).size());
        assertEquals(true, sent.get("instance"));
        assertEquals(6L, number(sent, "flags"));
    }

    @Test
    void sendsUnsupportedActivityTypesAnyway() throws Exception {
        PerigeeClient client = connected();

        await(client.setActivity(a -> a.type(ActivityType.STREAMING).url("https://twitch.tv/example").details("live")));

        assertEquals(1L, number(activity(discord.awaitCommand("SET_ACTIVITY")), "type"));
    }

    @Test
    void preservesUnicodeAndLargePayloads() throws Exception {
        PerigeeClient client = connected();
        String details = "héllo 😀 \"quoted\" \\ back\nline " + "x".repeat(100_000);

        await(client.setActivity(a -> a.details(details)));

        assertEquals(details, text(activity(discord.awaitCommand("SET_ACTIVITY")), "details"));
    }

    @Test
    void discordErrorFailsOnlyThatUpdate() throws Exception {
        PerigeeClient client = connected();
        discord.responder = (session, command) -> "SET_ACTIVITY".equals(command.get("cmd")) ? Payloads.error(command, 4000, "bad activity") : Payloads.ok(command);

        assertEquals(4000, assertInstanceOf(RpcException.class, failureOf(client.setActivity(a -> a.details("bad")))).code());

        discord.responder = (session, command) -> Payloads.ok(command);
        await(client.setActivity(a -> a.details("good")));
        assertTrue(client.isConnected());
        assertEquals(1, discord.sessions.get(), "a rejected command must not reconnect");
    }

    @Test
    void burstOfUpdatesStaysWithinDiscordsRateLimit() throws Exception {
        PerigeeClient client = connected();
        List<CompletableFuture<@Nullable Void>> futures = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            int n = i;
            futures.add(client.setActivity(a -> a.state("update " + n)));
        }
        Thread.sleep(500);

        assertEquals(5, countCommands("SET_ACTIVITY"));
        assertFalse(futures.getLast().isDone(), "the newest update waits for the rate-limit window");
    }

    @Test
    void concurrentUpdatesNeverExceedTheRateLimit() throws Exception {
        PerigeeClient client = connected();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<@Nullable Void>> futures = new ArrayList<>();
        try {
            List<Future<?>> submissions = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int thread = t;
                submissions.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 10; i++) {
                        String state = "t" + thread + "-" + i;
                        CompletableFuture<@Nullable Void> future = client.setActivity(a -> a.state(state));
                        synchronized (futures) {
                            futures.add(future);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> submission : submissions) {
                submission.get(WAIT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        Thread.sleep(300);

        assertEquals(80, futures.size());
        assertTrue(countCommands("SET_ACTIVITY") <= 5);
        assertTrue(client.isConnected());
    }

    @Test
    void latestPresenceIsResentAfterReconnect() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);
        await(client.connect());
        await(client.setActivity(a -> a.details("persist")));
        discord.awaitCommand("SET_ACTIVITY");

        discord.dropCurrent();
        take(drops);

        assertEquals("persist", text(activity(discord.awaitCommand("SET_ACTIVITY")), "details"));
    }

    @Test
    void clearedPresenceIsNotResentAfterReconnect() throws Exception {
        PerigeeClient client = start(builder());
        BlockingQueue<Disconnected> drops = new LinkedBlockingQueue<>();
        client.on(Disconnected.class, drops::add);
        await(client.connect());
        await(client.setActivity(a -> a.details("shown")));
        await(client.clearActivity());
        int before = countCommands("SET_ACTIVITY");

        discord.dropCurrent();
        take(drops);
        while (discord.sessions.get() < 2) {
            Thread.sleep(10);
        }
        Thread.sleep(300);

        assertEquals(before, countCommands("SET_ACTIVITY"));
    }

}
