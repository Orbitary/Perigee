/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.support;

import xyz.orbitary.perigee.protocol.Frame;
import xyz.orbitary.perigee.protocol.FrameCodec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;


public final class UnixTestServer implements AutoCloseable {
    private final Path socket;
    private final ServerSocketChannel server;

    public UnixTestServer(Path socket) throws IOException {
        this.socket = socket;
        this.server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socket));
    }

    public Path socket() {
        return socket;
    }

    public SocketChannel accept() throws IOException {
        return server.accept();
    }

    public CompletableFuture<SocketChannel> acceptAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return accept();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public CompletableFuture<Void> echoOnce() {
        return CompletableFuture.runAsync(() -> {
            try (SocketChannel client = accept()) {
                Frame frame = FrameCodec.decode(Channels.newInputStream(client));
                Channels.newOutputStream(client).write(FrameCodec.encode(frame.opcode(), frame.payload()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void close() throws IOException {
        server.close();
        Files.deleteIfExists(socket);
    }

}
