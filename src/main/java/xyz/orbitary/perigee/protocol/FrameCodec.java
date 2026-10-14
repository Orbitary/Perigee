/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;


/**
 * Encodes and decodes IPC frames:
 * - Two unsigned 32-bit integers (opcode, length)
 * - A UTF-8 JSON payload
 */
public final class FrameCodec {
    static final int HEADER_SIZE = 8;
    static final int MAX_PAYLOAD = 1 << 20;

    private FrameCodec() {}

    /**
     * @throws IllegalArgumentException if the payload exceeds the size Discord accepts
     */
    public static byte[] encode(Opcode opcode, String payload) {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_PAYLOAD) throw new IllegalArgumentException("Payload of " + body.length + " bytes exceeds the " + MAX_PAYLOAD + " byte limit");
        return ByteBuffer.allocate(HEADER_SIZE + body.length)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putInt(opcode.code())
          .putInt(body.length)
          .put(body)
          .array();
    }

    /**
     * Blocks until a full frame has been read.
     *
     * @throws EOFException if the stream ends mid-frame or before a frame
     * @throws IOException  on a read failure or a corrupt header
     */
    public static Frame decode(InputStream in) throws IOException {
        ByteBuffer header = ByteBuffer.wrap(readFully(in, HEADER_SIZE)).order(ByteOrder.LITTLE_ENDIAN);
        int code = header.getInt();
        int length = header.getInt();
        Opcode opcode = Opcode.fromCode(code).orElseThrow(() -> new IOException("Unknown opcode " + code));
        if (length < 0 || length > MAX_PAYLOAD) throw new IOException("Invalid payload length " + length);
        return new Frame(opcode, new String(readFully(in, length), StandardCharsets.UTF_8));
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(buffer, read, length - read);
            if (n < 0) throw new EOFException("Stream closed after " + read + " of " + length + " bytes");
            read += n;
        }
        return buffer;
    }

}
