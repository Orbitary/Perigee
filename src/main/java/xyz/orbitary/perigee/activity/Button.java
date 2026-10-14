/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

/**
 * Visible to other users only.
 */
public record Button(
  String label,
  String url
) {
    public static final int MAX_LABEL = 32;
    public static final int MAX_URL = 512;

    public Button {
        if (label.isEmpty() || label.length() > MAX_LABEL) throw new IllegalArgumentException("button label must be 1-" + MAX_LABEL + " characters");
        if (url.isEmpty() || url.length() > MAX_URL) throw new IllegalArgumentException("button url must be 1-" + MAX_URL + " characters");
    }

}
