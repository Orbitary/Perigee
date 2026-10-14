/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import java.io.Serial;


/**
 * Discord rejected a command or the handshake.
 */
public final class RpcException extends PerigeeException {
    private static final @Serial long serialVersionUID = 1L;

    private final int code;

    public RpcException(int code, String message) {
        super(message + " (code " + code + ")");
        this.code = code;
    }

    public int code() {
        return code;
    }

}
