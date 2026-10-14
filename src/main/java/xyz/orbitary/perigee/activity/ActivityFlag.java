/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

import java.util.Set;

public enum ActivityFlag {
    INSTANCE(0),
    JOIN(1),
    SPECTATE(2),
    JOIN_REQUEST(3),
    SYNC(4),
    PLAY(5),
    PARTY_PRIVACY_FRIENDS(6),
    PARTY_PRIVACY_VOICE_CHANNEL(7),
    EMBEDDED(8);

    private final int mask;

    ActivityFlag(int bit) {
        this.mask = 1 << bit;
    }

    public int mask() {
        return mask;
    }

    public static int toBits(Set<ActivityFlag> flags) {
        int bits = 0;
        for (ActivityFlag flag : flags) bits |= flag.mask;
        return bits;
    }
}
