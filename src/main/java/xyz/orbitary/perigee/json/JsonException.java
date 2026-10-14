/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.json;

import java.io.Serial;


/**
 * Thrown when a JSON document cannot be parsed.
 */
public final class JsonException extends RuntimeException {

    private static final @Serial long serialVersionUID = 1L;

    JsonException(String message) {
        super(message);
    }

}
