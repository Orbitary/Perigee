/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.jetbrains.annotations.Nullable;

import java.io.Serial;


/**
 * Base type for failures reported by the client.
 */
public class PerigeeException extends RuntimeException {
    private static final @Serial long serialVersionUID = 1L;

    public PerigeeException(String message) {
        super(message);
    }

    public PerigeeException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

}
