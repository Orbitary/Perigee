/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;


class FrameCodecTest {
    /**
     * Hands out a single byte per read, the worst case for a socket.
     */
    private static InputStream trickle(byte[] bytes) {
        ByteArrayInputStream source = new ByteArrayInputStream(bytes);
        return new InputStream() {
            @Override
            public int read() {
                return source.read();
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                return length == 0 ? 0 : source.read(buffer, offset, 1);
            }
        };
    }

    private static byte[] header(int opcode, int length) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(opcode).putInt(length).array();
    }

    @Test
    void headerIsTwoLittleEndianIntegersFollowedByThePayload() {
        assertArrayEquals(new byte[]{1, 0, 0, 0, 2, 0, 0, 0, '{', '}'}, FrameCodec.encode(Opcode.FRAME, "{}"));
        assertArrayEquals(new byte[]{4, 0, 0, 0, 2, 0, 0, 0, 'a', 'b'}, FrameCodec.encode(Opcode.PONG, "ab"));
    }

    @Test
    void lengthCountsUtf8BytesNotCharacters() throws IOException {
        String text = "é😀";

        byte[] bytes = FrameCodec.encode(Opcode.FRAME, text);

        assertEquals(2 + 4, ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertEquals(new Frame(Opcode.FRAME, text), FrameCodec.decode(new ByteArrayInputStream(bytes)));
    }

    @Test
    void emptyPayloadIsValid() throws IOException {
        byte[] bytes = FrameCodec.encode(Opcode.PING, "");

        assertEquals(8, bytes.length);
        assertEquals(new Frame(Opcode.PING, ""), FrameCodec.decode(new ByteArrayInputStream(bytes)));
    }

    @Test
    void decodesConsecutiveFramesEvenWhenBytesArriveOneAtATime() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Opcode opcode : Opcode.values()) {
            bytes.write(FrameCodec.encode(opcode, "payload-" + opcode));
        }
        InputStream in = trickle(bytes.toByteArray());

        for (Opcode opcode : Opcode.values()) {
            assertEquals(new Frame(opcode, "payload-" + opcode), FrameCodec.decode(in));
        }
        assertThrows(EOFException.class, () -> FrameCodec.decode(in));
    }

    @Test
    void acceptsAPayloadAtTheLimitAndRejectsOneByteMore() throws IOException {
        String atLimit = "a".repeat(FrameCodec.MAX_PAYLOAD);

        assertEquals(atLimit, FrameCodec.decode(new ByteArrayInputStream(FrameCodec.encode(Opcode.FRAME, atLimit))).payload());
        assertThrows(IOException.class, () -> FrameCodec.decode(new ByteArrayInputStream(header(1, FrameCodec.MAX_PAYLOAD + 1))));
    }

    @Test
    void rejectsCorruptHeadersBeforeReadingAnyBody() {
        assertThrows(IOException.class, () -> FrameCodec.decode(new ByteArrayInputStream(header(5, 0))));
        assertThrows(IOException.class, () -> FrameCodec.decode(new ByteArrayInputStream(header(-1, 0))));
        assertThrows(IOException.class, () -> FrameCodec.decode(new ByteArrayInputStream(header(1, -1))));
        assertThrows(IOException.class, () -> FrameCodec.decode(new ByteArrayInputStream(header(1, Integer.MIN_VALUE))));
    }

    @Test
    void reportsEofForEveryTruncationPoint() {
        byte[] full = FrameCodec.encode(Opcode.FRAME, "{\"a\":1}");

        for (int cut = 0; cut < full.length; cut++) {
            byte[] truncated = Arrays.copyOf(full, cut);
            assertThrows(EOFException.class, () -> FrameCodec.decode(new ByteArrayInputStream(truncated)), "cut at " + cut);
        }
    }

    @Test
    void invalidUtf8IsReplacedNotThrown() throws IOException {
        byte[] bytes = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(2).put((byte)0xC3).put((byte)0x28).array();

        assertTrue(FrameCodec.decode(new ByteArrayInputStream(bytes)).payload().contains("�"));
    }

}
