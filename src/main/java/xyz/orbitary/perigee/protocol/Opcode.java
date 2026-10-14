/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import java.util.Optional;


/**
 * IPC frame opcodes.
 */
public enum Opcode {
    HANDSHAKE(0),
    FRAME(1),
    CLOSE(2),
    PING(3),
    PONG(4);

    private static final Opcode[] BY_CODE = values();

    private final int code;

    Opcode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static Optional<Opcode> fromCode(int code) {
        return code >= 0 && code < BY_CODE.length ? Optional.of(BY_CODE[code]) : Optional.empty();
    }
}
