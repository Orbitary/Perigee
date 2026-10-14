/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.PerigeeException;
import xyz.orbitary.perigee.activity.Activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PresenceSchedulerTest {
    private static final long WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(300);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final List<String> sent = Collections.synchronizedList(new ArrayList<>());
    private final PresenceScheduler scheduler = new PresenceScheduler(executor, activity -> {
        sent.add(activity == null ? "clear" : activity.details());
        return CompletableFuture.completedFuture(null);
    }, 5, WINDOW_NANOS, System::nanoTime);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static Activity activity(String details) {
        return Activity.of(a -> a.details(details));
    }

    @Test
    void coalescesUpdatesBeyondTheRateLimit() throws Exception {
        scheduler.setOnline(true);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) futures.add(scheduler.submit(activity(Integer.toString(i))));

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);

        assertEquals(List.of("0", "1", "2", "3", "4", "7"), sent);
    }

    @Test
    void holdsLatestWhileOfflineAndSendsOnlyThatOnReconnect() throws Exception {
        CompletableFuture<Void> first = scheduler.submit(activity("old"));
        CompletableFuture<Void> second = scheduler.submit(activity("new"));
        Thread.sleep(50);
        assertEquals(List.of(), sent);
        assertFalse(first.isDone());

        scheduler.setOnline(true);
        CompletableFuture.allOf(first, second).get(5, TimeUnit.SECONDS);
        assertEquals(List.of("new"), sent);
    }

    @Test
    void resendsLatestActivityAfterReconnect() throws Exception {
        scheduler.setOnline(true);
        scheduler.submit(activity("a")).get(5, TimeUnit.SECONDS);
        scheduler.setOnline(false);
        scheduler.setOnline(true);
        Thread.sleep(100);
        assertEquals(List.of("a", "a"), sent);
    }

    @Test
    void closeFailsPendingUpdates() {
        CompletableFuture<Void> pending = scheduler.submit(activity("x"));
        scheduler.close();
        ExecutionException e = assertThrows(ExecutionException.class, () -> pending.get(1, TimeUnit.SECONDS));
        assertInstanceOf(PerigeeException.class, e.getCause());
        assertInstanceOf(PerigeeException.class, assertThrows(ExecutionException.class, () -> scheduler.submit(null).get()).getCause());
    }
}
