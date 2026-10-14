/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

import org.jetbrains.annotations.Nullable;


/**
 * Omit the join secret when the player cannot invite anyone.
 */
public record Secrets(
  @Nullable String join,
  @Nullable String spectate,
  @Nullable String match
) {
    public static final Secrets EMPTY = new Secrets(null, null, null);

    public Secrets withJoin(String join) {
        return new Secrets(join, spectate, match);
    }

    public Secrets withSpectate(String spectate) {
        return new Secrets(join, spectate, match);
    }

    public Secrets withMatch(String match) {
        return new Secrets(join, spectate, match);
    }

}
