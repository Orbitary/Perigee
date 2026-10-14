/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameCodecTest {
    @Test
    void encodesLittleEndianHeader() {
        byte[] bytes = FrameCodec.encode(Opcode.FRAME, "{}");
        assertArrayEquals(new byte[]{1, 0, 0, 0, 2, 0, 0, 0, '{', '}'}, bytes);
    }

    @Test
    void roundTripsUtf8() throws IOException {
        Frame frame = FrameCodec.decode(new ByteArrayInputStream(FrameCodec.encode(Opcode.HANDSHAKE, "{\"v\":1,\"n\":\"hé\"}")));
        assertEquals(new Frame(Opcode.HANDSHAKE, "{\"v\":1,\"n\":\"hé\"}"), frame);
    }

    @Test
    void decodesBackToBackFrames() throws IOException {
        byte[] a = FrameCodec.encode(Opcode.PING, "1");
        byte[] b = FrameCodec.encode(Opcode.PONG, "2");
        byte[] both = new byte[a.length + b.length];
        System.arraycopy(a, 0, both, 0, a.length);
        System.arraycopy(b, 0, both, a.length, b.length);
        ByteArrayInputStream in = new ByteArrayInputStream(both);
        assertEquals(Opcode.PING, FrameCodec.decode(in).opcode());
        assertEquals(Opcode.PONG, FrameCodec.decode(in).opcode());
    }

    @Test
    void rejectsTruncatedAndCorruptStreams() {
        assertThrows(EOFException.class, () -> FrameCodec.decode(new ByteArrayInputStream(new byte[0])));
        byte[] full = FrameCodec.encode(Opcode.FRAME, "{\"a\":1}");
        assertThrows(EOFException.class, () -> FrameCodec.decode(new ByteArrayInputStream(java.util.Arrays.copyOf(full, full.length - 1))));
        assertThrows(IOException.class, () -> FrameCodec.decode(new ByteArrayInputStream(new byte[]{9, 0, 0, 0, 0, 0, 0, 0})));
        assertThrows(IOException.class, () -> FrameCodec.decode(new ByteArrayInputStream(new byte[]{1, 0, 0, 0, -1, -1, -1, -1})));
    }
}
