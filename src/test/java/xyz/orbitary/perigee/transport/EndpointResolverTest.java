/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.transport;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndpointResolverTest {
    @Test
    void windowsUsesNamedPipes() {
        List<String> endpoints = EndpointResolver.resolve(true, Map.of(), List.of("ignored"));
        assertEquals(10, endpoints.size());
        assertEquals("\\\\?\\pipe\\discord-ipc-0", endpoints.getFirst());
        assertEquals("\\\\?\\pipe\\discord-ipc-9", endpoints.getLast());
    }

    @Test
    void unixFollowsDocumentedOrderWithSandboxesAndFallback() {
        Map<String, String> env = Map.of("XDG_RUNTIME_DIR", "/run/user/1000", "TMPDIR", "/var/tmp/", "TEMP", "/run/user/1000");
        List<String> endpoints = EndpointResolver.resolve(false, env, List.of("/custom"));

        List<String> firstOfEach = List.of(0, 10, 20, 30, 40, 50).stream().map(endpoints::get).toList();
        assertEquals(List.of(
                "/custom/discord-ipc-0",
                "/run/user/1000/discord-ipc-0",
                "/run/user/1000/app/com.discordapp.Discord/discord-ipc-0",
                "/run/user/1000/snap.discord/discord-ipc-0",
                "/var/tmp/discord-ipc-0",
                "/tmp/discord-ipc-0"
        ), firstOfEach);
        assertEquals(60, endpoints.size());
        assertEquals("/tmp/discord-ipc-9", endpoints.getLast());
    }

    @Test
    void unixSkipsUnsetAndBlankVariables() {
        List<String> endpoints = EndpointResolver.resolve(false, Map.of("TMPDIR", " "), List.of());
        assertEquals(10, endpoints.size());
        assertEquals("/tmp/discord-ipc-0", endpoints.getFirst());
    }
}
