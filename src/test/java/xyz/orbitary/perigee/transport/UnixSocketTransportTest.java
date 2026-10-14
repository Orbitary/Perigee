/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.orbitary.perigee.protocol.Frame;
import xyz.orbitary.perigee.protocol.FrameCodec;
import xyz.orbitary.perigee.protocol.Opcode;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnixSocketTransportTest {
    @TempDir
    Path dir;

    @Test
    void echoesFramesThroughTheCodec() throws Exception {
        Path socket = dir.resolve("discord-ipc-0");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            CompletableFuture<Void> echo = CompletableFuture.runAsync(() -> {
                try (SocketChannel client = server.accept()) {
                    Frame frame = FrameCodec.decode(Channels.newInputStream(client));
                    Channels.newOutputStream(client).write(FrameCodec.encode(frame.opcode(), frame.payload()));
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });

            try (Transport transport = TransportConnector.connect(List.of(dir.resolve("missing").toString(), socket.toString()), false)) {
                transport.write(FrameCodec.encode(Opcode.HANDSHAKE, "{\"v\":1}"));
                assertEquals(new Frame(Opcode.HANDSHAKE, "{\"v\":1}"), FrameCodec.decode(transport.input()));
            }
            echo.get();
        }
    }

    @Test
    void reportsFailureWhenNothingIsListening() {
        IOException e = assertThrows(IOException.class, () -> TransportConnector.connect(List.of(dir.resolve("a").toString(), dir.resolve("b").toString()), false));
        assertEquals(2, e.getSuppressed().length);
    }
}
