/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.transport;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;


/**
 * Windows named pipe transport.
 */
public final class WindowsPipeTransport implements Transport {
    private final RandomAccessFile pipe;
    private final InputStream input;

    private WindowsPipeTransport(RandomAccessFile pipe) throws IOException {
        this.pipe = pipe;
        FileDescriptor fd = pipe.getFD();
        this.input = new FileInputStream(fd) {
            @Override
            public void close() {}
        };
    }

    public static WindowsPipeTransport connect(String path) throws IOException {
        RandomAccessFile pipe = new RandomAccessFile(path, "rw");
        try {
            return new WindowsPipeTransport(pipe);
        } catch (IOException | RuntimeException e) {
            pipe.close();
            throw e;
        }
    }

    @Override
    public InputStream input() {
        return input;
    }

    @Override
    public synchronized void write(byte[] frame) throws IOException {
        pipe.write(frame);
    }

    @Override
    public void close() throws IOException {
        pipe.close();
    }

}
