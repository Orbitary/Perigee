/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.activity.Activity;
import xyz.orbitary.perigee.activity.ActivityFlag;
import xyz.orbitary.perigee.activity.ActivityType;
import xyz.orbitary.perigee.activity.Button;
import xyz.orbitary.perigee.activity.Party;
import xyz.orbitary.perigee.activity.StatusDisplayType;
import xyz.orbitary.perigee.json.Json;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivityPayloadTest {
    @Test
    void minimalActivityOnlyHasType() {
        assertEquals("{\"type\":0}", Json.write(ActivityPayload.toMap(Activity.of(a -> {}))));
    }

    @Test
    void mapsEveryField() {
        Activity activity = Activity.of(a -> a
                .name("Perigee")
                .type(ActivityType.LISTENING)
                .details("Song")
                .state("Artist")
                .statusDisplay(StatusDisplayType.DETAILS)
                .timestamps(xyz.orbitary.perigee.activity.Timestamps.between(Instant.ofEpochSecond(100), Instant.ofEpochSecond(200)))
                .party("p1", 2, 4)
                .largeImage("big", "Big")
                .smallImage("small")
                .joinSecret("j")
                .button("Docs", "https://example.com")
                .instance(true)
                .flag(ActivityFlag.JOIN, ActivityFlag.SPECTATE));

        assertEquals(
                "{\"name\":\"Perigee\",\"type\":2,\"details\":\"Song\",\"state\":\"Artist\",\"status_display_type\":2,"
                        + "\"timestamps\":{\"start\":100,\"end\":200},\"party\":{\"id\":\"p1\",\"size\":[2,4]},"
                        + "\"assets\":{\"large_image\":\"big\",\"large_text\":\"Big\",\"small_image\":\"small\"},"
                        + "\"secrets\":{\"join\":\"j\"},\"buttons\":[{\"label\":\"Docs\",\"url\":\"https://example.com\"}],"
                        + "\"instance\":true,\"flags\":6}",
                Json.write(ActivityPayload.toMap(activity)));
    }

    @Test
    void validatesLimits() {
        assertThrows(IllegalArgumentException.class, () -> new Button("", "https://a"));
        assertThrows(IllegalArgumentException.class, () -> new Button("x".repeat(33), "https://a"));
        assertThrows(IllegalArgumentException.class, () -> new Button("ok", "x".repeat(513)));
        assertThrows(IllegalArgumentException.class, () -> Activity.of(a -> a.button("a", "u").button("b", "u").button("c", "u")));
        assertThrows(IllegalArgumentException.class, () -> Party.sized(5, 4));
        assertThrows(IllegalArgumentException.class, () -> Party.sized(1, 0));
        assertThrows(IllegalArgumentException.class, () -> xyz.orbitary.perigee.activity.Timestamps.between(Instant.ofEpochSecond(2), Instant.ofEpochSecond(1)));
    }
}
