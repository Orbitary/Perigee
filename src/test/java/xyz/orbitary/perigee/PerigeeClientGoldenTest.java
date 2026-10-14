/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.activity.Activity;
import xyz.orbitary.perigee.activity.ActivityFlag;
import xyz.orbitary.perigee.activity.ActivityType;
import xyz.orbitary.perigee.activity.StatusDisplayType;
import xyz.orbitary.perigee.activity.Timestamps;
import xyz.orbitary.perigee.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xyz.orbitary.perigee.support.Tests.await;


/**
 * Pins the exact SET_ACTIVITY command Discord receives, including the timestamp unit (whole Unix seconds).
 */
class PerigeeClientGoldenTest extends ClientTestBase {
    private static Map<?, ?> golden(String name) throws IOException {
        try (var in = Objects.requireNonNull(PerigeeClientGoldenTest.class.getResourceAsStream("/golden/" + name))) {
            return (Map<?, ?>)Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).orElseThrow();
        }
    }

    /**
     * The nonce and process ID differ on every run.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalised(Map<String, Object> command) {
        Map<String, Object> copy = new LinkedHashMap<>(command);
        copy.put("nonce", "<nonce>");
        Map<String, Object> args = new LinkedHashMap<>((Map<String, Object>)command.get("args"));
        args.put("pid", 0L);
        copy.put("args", args);
        return copy;
    }

    @Test
    void aFullyPopulatedActivityMatchesTheGoldenCommand() throws Exception {
        PerigeeClient client = connected();

        await(client.setActivity(Activity.of(a -> a
          .name("Perigee Demo")
          .type(ActivityType.LISTENING)
          .details("Ranked").detailsUrl("https://example.com/details")
          .state("In a party").stateUrl("https://example.com/state")
          .statusDisplay(StatusDisplayType.DETAILS)
          .timestamps(Timestamps.between(Instant.ofEpochSecond(1_700_000_000), Instant.ofEpochSecond(1_700_003_600)))
          .party("lobby-42", 2, 4)
          .largeImage("logo", "Logo text").largeUrl("https://example.com/large")
          .smallImage("rank", "Gold II").smallUrl("https://example.com/small")
          .inviteCoverImage("cover")
          .joinSecret("join-secret").spectateSecret("spectate-secret").matchSecret("match-secret")
          .button("Docs", "https://example.com/docs").button("Repo", "https://example.com/repo")
          .instance(true)
          .flag(ActivityFlag.JOIN, ActivityFlag.SPECTATE))));

        assertEquals(golden("set-activity-full.json"), normalised(discord.awaitCommand("SET_ACTIVITY")));
    }

    @Test
    void clearingPresenceSendsNoActivityKey() throws Exception {
        PerigeeClient client = connected();

        await(client.clearActivity());

        assertEquals(golden("set-activity-cleared.json"), normalised(discord.awaitCommand("SET_ACTIVITY")));
    }

}
