/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.transport;

import java.io.IOException;
import java.io.InputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;


/**
 * Unix domain socket transport for Linux, macOS and Windows hosts that expose sockets.
 */
public final class UnixSocketTransport implements Transport {
    private final SocketChannel channel;
    private final InputStream input;

    private UnixSocketTransport(SocketChannel channel) {
        this.channel = channel;
        this.input = Channels.newInputStream(channel);
    }

    public static UnixSocketTransport connect(String path) throws IOException {
        SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            channel.connect(UnixDomainSocketAddress.of(path));
            return new UnixSocketTransport(channel);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    @Override
    public InputStream input() {
        return input;
    }

    @Override
    public synchronized void write(byte[] frame) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

}
