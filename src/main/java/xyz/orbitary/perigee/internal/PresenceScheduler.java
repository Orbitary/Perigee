/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.internal;

import org.jetbrains.annotations.Nullable;
import xyz.orbitary.perigee.Logger;
import xyz.orbitary.perigee.PerigeeException;
import xyz.orbitary.perigee.PerigeeTimeoutException;
import xyz.orbitary.perigee.RpcException;
import xyz.orbitary.perigee.activity.Activity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;


/**
 * Respect Discord's rate limit (5 updates per 20 seconds): only the newest requested activity is sent.
 */
public final class PresenceScheduler {
    public static final int DEFAULT_LIMIT = 5;
    public static final long DEFAULT_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(20);

    @FunctionalInterface
    public interface Sender {
        CompletableFuture<Void> send(@Nullable Activity activity);

    }

    private final ScheduledExecutorService executor;
    private final Sender sender;
    private final int limit;
    private final long windowNanos;
    private final LongSupplier clock;

    private final ArrayDeque<Long> sent = new ArrayDeque<>();
    private final List<CompletableFuture<@Nullable Void>> waiters = new ArrayList<>();
    private @Nullable Activity latest;
    private @Nullable Activity accepted;
    private boolean dirty;
    private boolean online;
    private boolean closed;
    private @Nullable ScheduledFuture<?> timer;

    public PresenceScheduler(ScheduledExecutorService executor, Sender sender) {
        this(executor, sender, DEFAULT_LIMIT, DEFAULT_WINDOW_NANOS, System::nanoTime);
    }

    public PresenceScheduler(ScheduledExecutorService executor, Sender sender, int limit, long windowNanos, LongSupplier clock) {
        this.executor = executor;
        this.sender = sender;
        this.limit = limit;
        this.windowNanos = windowNanos;
        this.clock = clock;
    }

    /**
     * Requests this activity, or clears presence when {@code null}.
     */
    public synchronized CompletableFuture<Void> submit(@Nullable Activity activity) {
        if (closed) return CompletableFuture.failedFuture(new PerigeeException("Client is closed"));
        CompletableFuture<Void> done = new CompletableFuture<>();
        latest = activity;
        dirty = true;
        waiters.add(done);
        flush();
        return done;
    }

    public synchronized void setOnline(boolean online) {
        this.online = online;
        if (online && latest != null) dirty = true;
        flush();
    }

    public synchronized void close() {
        closed = true;
        if (timer != null) timer.cancel(false);
        PerigeeException failure = new PerigeeException("Client is closed");
        waiters.forEach(f -> f.completeExceptionally(failure));
        waiters.clear();
    }

    private void flush() {
        if (closed || !online || !dirty || timer != null) return;
        long now = clock.getAsLong();
        while (!sent.isEmpty() && now - sent.peekFirst() >= windowNanos) {
            sent.pollFirst();
        }
        if (sent.size() >= limit) {
            long delay = windowNanos - (now - sent.peekFirst());
            timer = executor.schedule(this::onTimer, delay, TimeUnit.NANOSECONDS);
            return;
        }
        sent.addLast(now);
        Activity activity = latest;
        List<CompletableFuture<@Nullable Void>> batch = new ArrayList<>(waiters);
        waiters.clear();
        dirty = false;
        executor.execute(() -> send(activity, batch));
    }

    private synchronized void onTimer() {
        timer = null;
        flush();
    }

    private void send(@Nullable Activity activity, List<? extends CompletableFuture<@Nullable Void>> batch) {
        CompletableFuture<@Nullable Void> result;
        try {
            result = sender.send(activity);
        } catch (RuntimeException e) {
            result = CompletableFuture.failedFuture(e);
        }
        result.whenComplete((ignored, error) -> {
            if (error == null) {
                onSuccess(activity, batch);
            } else {
                onFailure(activity, batch, error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            }
        });
    }

    private synchronized void onSuccess(@Nullable Activity activity, List<? extends CompletableFuture<@Nullable Void>> batch) {
        accepted = activity;
        batch.forEach(f -> f.complete(null));
    }

    private synchronized void onFailure(@Nullable Activity activity, List<? extends CompletableFuture<@Nullable Void>> batch, Throwable error) {
        if (error instanceof RpcException) {
            Logger.warn("Discord rejected a presence update: {0}", String.valueOf(error.getMessage()));
            if (latest == activity) latest = accepted;
            batch.forEach(f -> f.completeExceptionally(error));
            return;
        }
        if (error instanceof PerigeeTimeoutException || closed) {
            if (error instanceof PerigeeTimeoutException) Logger.warn("Discord did not confirm a presence update: {0}", String.valueOf(error.getMessage()));
            batch.forEach(f -> f.completeExceptionally(error));
            return;
        }
        waiters.addAll(0, batch);
        dirty = true;
    }

}
