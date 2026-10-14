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
import xyz.orbitary.perigee.activity.Activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.orbitary.perigee.support.Tests.await;
import static xyz.orbitary.perigee.support.Tests.failureOf;


class PresenceSchedulerTest {
    private static final String PREFIX = "d-";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final List<String> sent = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private volatile boolean discordRejects;
    private volatile boolean discordSilent;

    private PresenceScheduler scheduler(int limit, long windowMillis) {
        return new PresenceScheduler(executor, this::send, limit, TimeUnit.MILLISECONDS.toNanos(windowMillis), System::nanoTime);
    }

    private CompletableFuture<@Nullable Void> send(@Nullable Activity activity) {
        sent.add(activity == null ? "clear" : String.valueOf(activity.details()).substring(PREFIX.length()));
        if (discordSilent) return CompletableFuture.failedFuture(new PerigeeTimeoutException("no reply"));
        if (discordRejects) return CompletableFuture.failedFuture(new RpcException(4000, "rejected"));
        if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) return CompletableFuture.failedFuture(new PerigeeException("connection lost"));
        return CompletableFuture.completedFuture(null);
    }

    private static Activity activity(String label) {
        return Activity.of(a -> a.details(PREFIX + label));
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void coalescesUpdatesBeyondTheRateLimit() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 300);
        scheduler.setOnline(true);
        List<CompletableFuture<@Nullable Void>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(scheduler.submit(activity(Integer.toString(i))));
        }

        await(CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)));

        assertEquals(List.of("0", "1", "2", "3", "4", "7"), sent);
    }

    @Test
    void anUpdateBeyondTheLimitWaitsForTheWindow() throws Exception {
        PresenceScheduler scheduler = scheduler(1, 400);
        scheduler.setOnline(true);
        await(scheduler.submit(activity("first")));

        CompletableFuture<@Nullable Void> second = scheduler.submit(activity("second"));

        assertThrows(TimeoutException.class, () -> second.get(150, TimeUnit.MILLISECONDS));
        await(second);
        assertEquals(List.of("first", "second"), sent);
    }

    @Test
    void theRateLimitWindowSlides() throws Exception {
        PresenceScheduler scheduler = scheduler(2, 200);
        scheduler.setOnline(true);
        await(scheduler.submit(activity("a")));
        await(scheduler.submit(activity("b")));

        Thread.sleep(300);
        scheduler.submit(activity("c")).get(1, TimeUnit.SECONDS);

        assertEquals(List.of("a", "b", "c"), sent);
    }

    @Test
    void holdsLatestWhileOfflineAndSendsOnlyThatOnReconnect() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 300);
        CompletableFuture<@Nullable Void> first = scheduler.submit(activity("old"));
        CompletableFuture<@Nullable Void> second = scheduler.submit(activity("new"));
        Thread.sleep(50);
        assertEquals(List.of(), sent);
        assertFalse(first.isDone());

        scheduler.setOnline(true);

        await(CompletableFuture.allOf(first, second));
        assertEquals(List.of("new"), sent);
    }

    @Test
    void resendsLatestActivityAfterReconnect() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 300);
        scheduler.setOnline(true);
        await(scheduler.submit(activity("a")));

        scheduler.setOnline(false);
        scheduler.setOnline(true);
        Thread.sleep(100);

        assertEquals(List.of("a", "a"), sent);
    }

    @Test
    void clearingSendsNull() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 1_000);
        scheduler.setOnline(true);

        await(scheduler.submit(null));

        assertEquals(List.of("clear"), sent);
    }

    @Test
    void connectionTroubleKeepsTheUpdateUntilTheNextReconnect() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 1_000);
        failuresLeft.set(1);
        scheduler.setOnline(true);

        CompletableFuture<@Nullable Void> update = scheduler.submit(activity("kept"));
        Thread.sleep(100);
        assertFalse(update.isDone());
        assertEquals(List.of("kept"), sent);

        scheduler.setOnline(false);
        scheduler.setOnline(true);

        await(update);
        assertEquals(List.of("kept", "kept"), sent);
    }

    @Test
    void aNewerUpdateReplacesOneThatWasWaitingToBeRetried() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 1_000);
        failuresLeft.set(1);
        scheduler.setOnline(true);
        CompletableFuture<@Nullable Void> old = scheduler.submit(activity("old"));
        Thread.sleep(100);

        CompletableFuture<@Nullable Void> replacement = scheduler.submit(activity("new"));

        await(CompletableFuture.allOf(old, replacement));
        assertEquals("new", sent.getLast());
    }

    @Test
    void aSenderThatThrowsIsTreatedAsConnectionTrouble() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PresenceScheduler scheduler = new PresenceScheduler(executor, activity -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("boom");
            return CompletableFuture.completedFuture(null);
        }, 5, TimeUnit.SECONDS.toNanos(1), System::nanoTime);
        scheduler.setOnline(true);

        CompletableFuture<@Nullable Void> update = scheduler.submit(activity("x"));
        Thread.sleep(100);
        assertFalse(update.isDone());

        scheduler.setOnline(false);
        scheduler.setOnline(true);
        await(update);

        assertEquals(2, calls.get());
    }

    @Test
    void discordRejectingAnUpdateFailsItWithoutRetrying() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 1_000);
        discordRejects = true;
        scheduler.setOnline(true);

        Throwable failure = failureOf(scheduler.submit(activity("bad")));
        scheduler.setOnline(false);
        scheduler.setOnline(true);
        Thread.sleep(150);

        assertEquals(4000, assertInstanceOf(RpcException.class, failure).code());
        assertEquals(List.of("bad"), sent, "a rejected update must not be resent");
    }

    @Test
    void oneRejectedUpdateDoesNotBlockLaterValidOnes() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 1_000);
        scheduler.setOnline(true);
        await(scheduler.submit(activity("good")));

        discordRejects = true;
        assertInstanceOf(RpcException.class, failureOf(scheduler.submit(activity("bad"))));
        discordRejects = false;
        await(scheduler.submit(activity("later")));

        assertEquals(List.of("good", "bad", "later"), sent);
    }

    @Test
    void aRejectedUpdateIsNotReplayedButTheLastAcceptedOneIs() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 1_000);
        scheduler.setOnline(true);
        await(scheduler.submit(activity("good")));
        discordRejects = true;
        failureOf(scheduler.submit(activity("bad")));
        discordRejects = false;

        scheduler.setOnline(false);
        scheduler.setOnline(true);
        Thread.sleep(150);

        assertEquals(List.of("good", "bad", "good"), sent);
    }

    @Test
    void anUnansweredUpdateFailsInsteadOfStayingPendingAndLaterOnesStillWork() throws Exception {
        PresenceScheduler scheduler = scheduler(5, 1_000);
        scheduler.setOnline(true);
        discordSilent = true;

        assertInstanceOf(PerigeeTimeoutException.class, failureOf(scheduler.submit(activity("lost"))));
        discordSilent = false;
        await(scheduler.submit(activity("later")));

        assertEquals(List.of("lost", "later"), sent);
    }

    @Test
    void closeFailsPendingUpdatesAndRefusesNewOnes() {
        PresenceScheduler scheduler = scheduler(5, 1_000);
        CompletableFuture<@Nullable Void> pending = scheduler.submit(activity("x"));

        scheduler.close();

        assertInstanceOf(PerigeeException.class, failureOf(pending));
        assertInstanceOf(PerigeeException.class, failureOf(scheduler.submit(null)));
    }

    @Test
    void closingStopsAPendingRateLimitedSend() throws Exception {
        PresenceScheduler scheduler = scheduler(1, 300);
        scheduler.setOnline(true);
        await(scheduler.submit(activity("sent")));
        CompletableFuture<@Nullable Void> waiting = scheduler.submit(activity("never"));

        scheduler.close();
        Thread.sleep(450);

        assertInstanceOf(PerigeeException.class, failureOf(waiting));
        assertEquals(List.of("sent"), sent);
    }

}
