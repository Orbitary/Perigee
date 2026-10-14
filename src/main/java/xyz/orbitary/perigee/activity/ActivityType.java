/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

/**
 * How Discord words the activity, e.g. "Playing {@code name}".
 */
public enum ActivityType {
    PLAYING(0, true),
    /** Gateway-only; local IPC clients may be rejected by Discord. */
    STREAMING(1, false),
    LISTENING(2, true),
    WATCHING(3, true),
    /** Gateway-only; local IPC clients may be rejected by Discord. */
    CUSTOM(4, false),
    COMPETING(5, true);

    private final int id;
    private final boolean rpcSupported;

    ActivityType(int id, boolean rpcSupported) {
        this.id = id;
        this.rpcSupported = rpcSupported;
    }

    public int id() {
        return id;
    }

    public boolean rpcSupported() {
        return rpcSupported;
    }
}
