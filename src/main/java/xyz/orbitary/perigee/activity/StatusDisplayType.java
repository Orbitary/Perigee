/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

/**
 * Which field Discord shows in the member list status text.
 */
public enum StatusDisplayType {
    NAME(0),
    STATE(1),
    DETAILS(2);

    private final int id;

    StatusDisplayType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }
}
