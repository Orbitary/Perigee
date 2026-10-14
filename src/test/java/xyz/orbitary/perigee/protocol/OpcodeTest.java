/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;


class OpcodeTest {
    @Test
    void codesMatchDiscordsDocumentedValues() {
        assertEquals(0, Opcode.HANDSHAKE.code());
        assertEquals(1, Opcode.FRAME.code());
        assertEquals(2, Opcode.CLOSE.code());
        assertEquals(3, Opcode.PING.code());
        assertEquals(4, Opcode.PONG.code());
    }

    @Test
    void everyOpcodeRoundTripsThroughItsCode() {
        for (Opcode opcode : Opcode.values()) {
            assertEquals(Optional.of(opcode), Opcode.fromCode(opcode.code()));
        }
    }

    @Test
    void unknownCodesAreEmpty() {
        for (int code : new int[]{-1, 5, 6, 255, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertEquals(Optional.empty(), Opcode.fromCode(code), "code " + code);
        }
    }

}
