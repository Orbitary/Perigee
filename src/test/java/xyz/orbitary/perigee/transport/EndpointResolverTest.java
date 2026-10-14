/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.transport;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


class EndpointResolverTest {
    @Test
    void windowsUsesNamedPipesAndIgnoresTheEnvironment() {
        List<String> endpoints = EndpointResolver.resolve(true, Map.of(), List.of());

        assertEquals(10, endpoints.size());
        assertEquals("\\\\?\\pipe\\discord-ipc-0", endpoints.getFirst());
        assertEquals("\\\\?\\pipe\\discord-ipc-9", endpoints.getLast());
        assertEquals(endpoints, EndpointResolver.resolve(true, Map.of("XDG_RUNTIME_DIR", "/run/user/1", "TMPDIR", "/var/tmp"), List.of("/custom")));
    }

    @Test
    void unixFollowsDocumentedOrderWithSandboxesAndFallback() {
        Map<String, String> env = Map.of("XDG_RUNTIME_DIR", "/run/user/1000", "TMPDIR", "/var/tmp/", "TEMP", "/run/user/1000");

        List<String> endpoints = EndpointResolver.resolve(false, env, List.of("/custom"));

        List<String> firstOfEach = List.of(0, 10, 20, 30, 40, 50).stream().map(endpoints::get).toList();
        assertEquals(
          List.of(
            "/custom/discord-ipc-0",
            "/run/user/1000/discord-ipc-0",
            "/run/user/1000/app/com.discordapp.Discord/discord-ipc-0",
            "/run/user/1000/snap.discord/discord-ipc-0",
            "/var/tmp/discord-ipc-0",
            "/tmp/discord-ipc-0"
          ), firstOfEach
        );
        assertEquals(60, endpoints.size());
        assertEquals("/tmp/discord-ipc-9", endpoints.getLast());
    }

    @Test
    void eachDirectoryIsSearchedFromIndexZeroToNine() {
        List<String> endpoints = EndpointResolver.resolve(false, Map.of(), List.of("/custom"));

        for (int i = 0; i < 10; i++) {
            assertEquals("/custom/discord-ipc-" + i, endpoints.get(i));
        }
    }

    @Test
    void skipsUnsetAndBlankVariables() {
        List<String> endpoints = EndpointResolver.resolve(false, Map.of("TMPDIR", " "), List.of());

        assertEquals(10, endpoints.size());
        assertEquals("/tmp/discord-ipc-0", endpoints.getFirst());
    }

    @Test
    void neverListsAnEndpointTwice() {
        Map<String, String> env = Map.of("XDG_RUNTIME_DIR", "/run/user/1", "TMPDIR", "/run/user/1/", "TMP", "/tmp", "TEMP", "/tmp/");

        List<String> endpoints = EndpointResolver.resolve(false, env, List.of("/tmp", "/run/user/1"));

        assertEquals(endpoints.size(), new HashSet<>(endpoints).size());
    }

    @Test
    void trailingSlashesAreEquivalent() {
        assertEquals(
          EndpointResolver.resolve(false, Map.of("XDG_RUNTIME_DIR", "/run/user/1"), List.of()),
          EndpointResolver.resolve(false, Map.of("XDG_RUNTIME_DIR", "/run/user/1/"), List.of())
        );
    }

    @Test
    void sandboxDirectoriesOnlyDeriveFromTheRuntimeDirectory() {
        List<String> fromTmp = EndpointResolver.resolve(false, Map.of("TMPDIR", "/var/tmp"), List.of());
        List<String> fromRuntime = EndpointResolver.resolve(false, Map.of("XDG_RUNTIME_DIR", "/run/user/1"), List.of());

        assertTrue(fromTmp.stream().noneMatch(path -> path.contains("snap.discord") || path.contains("com.discordapp.Discord")));
        assertTrue(fromRuntime.contains("/run/user/1/snap.discord/discord-ipc-0"));
        assertTrue(fromRuntime.contains("/run/user/1/app/com.discordapp.Discord/discord-ipc-0"));
    }

    @Test
    void theDefaultResolutionMatchesTheCurrentPlatform() {
        List<String> endpoints = EndpointResolver.resolve();

        assertFalse(endpoints.isEmpty());
        if (EndpointResolver.isWindows()) {
            assertTrue(endpoints.stream().allMatch(path -> path.startsWith("\\\\?\\pipe\\discord-ipc-")));
        } else {
            assertTrue(endpoints.stream().allMatch(path -> path.startsWith("/")));
            assertTrue(endpoints.contains("/tmp/discord-ipc-0"));
        }
    }

}
