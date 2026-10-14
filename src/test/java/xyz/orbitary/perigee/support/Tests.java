/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.support;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;


public final class Tests {
    public static final long WAIT_SECONDS = 5;

    private Tests() {}

    public static <T> T take(BlockingQueue<T> queue) throws InterruptedException {
        return Objects.requireNonNull(queue.poll(WAIT_SECONDS, TimeUnit.SECONDS), "timed out waiting for an item");
    }

    public static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    public static Throwable failureOf(CompletableFuture<?> future) {
        return Objects.requireNonNull(assertThrows(ExecutionException.class, () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS)).getCause());
    }

}
