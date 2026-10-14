/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;


/**
 * Start only counts elapsed time; end counts down remaining time.
 */
public record Timestamps(
  @Nullable Instant start,
  @Nullable Instant end
) {
    public Timestamps {
        if (start != null && end != null && end.isBefore(start)) throw new IllegalArgumentException("end must not be before start");
    }

    public static Timestamps since(Instant start) {
        return new Timestamps(start, null);
    }

    public static Timestamps until(Instant end) {
        return new Timestamps(null, end);
    }

    public static Timestamps between(Instant start, Instant end) {
        return new Timestamps(start, end);
    }

}
