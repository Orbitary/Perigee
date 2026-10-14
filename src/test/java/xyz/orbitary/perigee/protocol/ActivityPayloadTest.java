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
import xyz.orbitary.perigee.activity.Party;
import xyz.orbitary.perigee.activity.StatusDisplayType;
import xyz.orbitary.perigee.activity.Timestamps;
import xyz.orbitary.perigee.json.Json;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


class ActivityPayloadTest {
    private static String json(Activity activity) {
        return Json.write(ActivityPayload.toMap(activity));
    }

    @Test
    void anEmptyActivityOnlyHasItsType() {
        assertEquals("{\"type\":0}", json(Activity.of(a -> {})));
    }

    @Test
    void mapsEveryField() {
        Activity activity = Activity.of(a -> a
          .name("Perigee")
          .type(ActivityType.LISTENING)
          .details("Song")
          .state("Artist")
          .statusDisplay(StatusDisplayType.DETAILS)
          .timestamps(Timestamps.between(Instant.ofEpochSecond(100), Instant.ofEpochSecond(200)))
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
          json(activity)
        );
    }

    @Test
    void everyActivityTypeWritesItsId() {
        for (ActivityType type : ActivityType.values()) {
            assertEquals("{\"type\":" + type.id() + "}", json(Activity.of(a -> a.type(type))), type.name());
        }
    }

    @Test
    void partyWritesIdAndSizeIndependently() {
        assertEquals("{\"type\":0,\"party\":{\"id\":\"x\"}}", json(Activity.of(a -> a.party(Party.of("x", 0, 0)))));
        assertEquals("{\"type\":0,\"party\":{\"size\":[1,2]}}", json(Activity.of(a -> a.party(Party.sized(1, 2)))));
        assertEquals("{\"type\":0,\"party\":{\"id\":\"x\",\"size\":[0,5]}}", json(Activity.of(a -> a.party("x", 0, 5))));
    }

    @Test
    void timestampsWriteOnlyTheEndsThatAreSet() {
        assertEquals("{\"type\":0,\"timestamps\":{\"start\":100}}", json(Activity.of(a -> a.timestamps(Timestamps.since(Instant.ofEpochSecond(100))))));
        assertEquals("{\"type\":0,\"timestamps\":{\"end\":200}}", json(Activity.of(a -> a.timestamps(Timestamps.until(Instant.ofEpochSecond(200))))));
        assertEquals("{\"type\":0}", json(Activity.of(a -> a.timestamps(new Timestamps(null, null)))));
    }

    @Test
    void timestampsAreWholeSecondsSinceTheEpoch() {
        Instant withMillis = Instant.ofEpochMilli(1_700_000_000_999L);

        assertEquals("{\"type\":0,\"timestamps\":{\"start\":1700000000}}", json(Activity.of(a -> a.startedAt(withMillis))));
    }

    @Test
    void everyAssetKeyUsesDiscordsFieldName() {
        Activity activity = Activity.of(a -> a
          .largeImage("li", "lt").largeUrl("lu")
          .smallImage("si", "st").smallUrl("su")
          .inviteCoverImage("ic"));

        assertEquals(
          "{\"type\":0,\"assets\":{\"large_image\":\"li\",\"large_text\":\"lt\",\"large_url\":\"lu\",\"small_image\":\"si\",\"small_text\":\"st\",\"small_url\":\"su\",\"invite_cover_image\":\"ic\"}}",
          json(activity)
        );
    }

    @Test
    void secretsWriteOnlyWhatIsSet() {
        assertEquals("{\"type\":0,\"secrets\":{\"spectate\":\"s\"}}", json(Activity.of(a -> a.spectateSecret("s"))));
        assertEquals("{\"type\":0,\"secrets\":{\"join\":\"j\",\"spectate\":\"s\",\"match\":\"m\"}}", json(Activity.of(a -> a.joinSecret("j").spectateSecret("s").matchSecret("m"))));
    }

    @Test
    void linkFieldsUseDiscordsNames() {
        Activity activity = Activity.of(a -> a.type(ActivityType.STREAMING).url("https://twitch.tv/x").details("d").detailsUrl("https://d").state("s").stateUrl("https://s"));

        assertEquals(
          "{\"type\":1,\"url\":\"https://twitch.tv/x\",\"details\":\"d\",\"details_url\":\"https://d\",\"state\":\"s\",\"state_url\":\"https://s\"}",
          json(activity)
        );
    }

    @Test
    void buttonsKeepTheirOrder() {
        Activity activity = Activity.of(a -> a.button("first", "https://1").button("second", "https://2"));

        assertEquals(
          "{\"type\":0,\"buttons\":[{\"label\":\"first\",\"url\":\"https://1\"},{\"label\":\"second\",\"url\":\"https://2\"}]}",
          json(activity)
        );
    }

    @Test
    void instanceAndFlagsAreOnlyWrittenWhenSet() {
        assertEquals("{\"type\":0}", json(Activity.of(a -> a.instance(false))));
        assertEquals("{\"type\":0,\"instance\":true}", json(Activity.of(a -> a.instance(true))));
        assertEquals("{\"type\":0,\"flags\":511}", json(Activity.of(a -> a.flag(EnumSet.allOf(ActivityFlag.class).toArray(ActivityFlag[]::new)))));
    }

    @Test
    void emptyStringsAreKeptBecauseTheyAreSetValues() {
        assertEquals("{\"type\":0,\"details\":\"\"}", json(Activity.of(a -> a.details(""))));
    }

    @Test
    void awkwardTextSurvivesSerialisation() {
        String awkward = "quote\" back\\slash \n line \t tab é 😀 </script>";

        Object parsed = Json.parse(json(Activity.of(a -> a.details(awkward)))).orElseThrow();

        assertEquals(awkward, ((Map<?, ?>)parsed).get("details"));
    }

}
