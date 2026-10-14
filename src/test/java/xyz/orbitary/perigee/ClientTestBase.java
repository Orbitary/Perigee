/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import xyz.orbitary.perigee.support.Payloads;
import xyz.orbitary.perigee.transport.UnixSocketTransport;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static xyz.orbitary.perigee.support.Tests.await;


/**
 * Starts a {@link FakeDiscord} per test and closes every client the test creates.
 */
abstract class ClientTestBase {
    @TempDir
    Path dir;

    FakeDiscord discord;
    private final List<PerigeeClient> clients = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        discord = new FakeDiscord(dir.resolve("discord-ipc-0"));
    }

    @AfterEach
    void stopServer() throws IOException {
        clients.forEach(PerigeeClient::close);
        discord.close();
    }

    PerigeeClient.Builder builder() {
        return PerigeeClient.builder(Payloads.APPLICATION_ID)
          .reconnectDelay(Duration.ofMillis(20), Duration.ofMillis(100))
          .transport(() -> UnixSocketTransport.connect(discord.socket.toString()));
    }

    PerigeeClient start(PerigeeClient.Builder builder) {
        PerigeeClient client = builder.build();
        clients.add(client);
        return client;
    }

    PerigeeClient connected() throws Exception {
        PerigeeClient client = start(builder());
        await(client.connect());
        return client;
    }

}
