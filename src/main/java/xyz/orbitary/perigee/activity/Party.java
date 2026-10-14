/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

import org.jetbrains.annotations.Nullable;


/**
 * A max of zero means no size is shown.
 */
public record Party(
  @Nullable String id,
  int size,
  int max
) {
    public Party {
        if (size < 0 || max < 0) throw new IllegalArgumentException("party sizes must not be negative");
        if (max > 0 && size > max) throw new IllegalArgumentException("party size " + size + " exceeds max " + max);
        if (max == 0 && size != 0) throw new IllegalArgumentException("party size requires a max");
    }

    public static Party of(String id, int size, int max) {
        return new Party(id, size, max);
    }

    public static Party sized(int size, int max) {
        return new Party(null, size, max);
    }

    public boolean hasSize() {
        return max > 0;
    }

}
