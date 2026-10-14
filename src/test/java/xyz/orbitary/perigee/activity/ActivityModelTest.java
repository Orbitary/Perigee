/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


class ActivityModelTest {
    @Test
    void defaultsAreEmptyApartFromThePlayingType() {
        Activity activity = Activity.of(a -> {});

        assertEquals(ActivityType.PLAYING, activity.type());
        assertNull(activity.name());
        assertNull(activity.details());
        assertNull(activity.state());
        assertNull(activity.url());
        assertNull(activity.timestamps());
        assertNull(activity.party());
        assertNull(activity.statusDisplayType());
        assertEquals(Assets.EMPTY, activity.assets());
        assertEquals(Secrets.EMPTY, activity.secrets());
        assertTrue(activity.buttons().isEmpty());
        assertTrue(activity.flags().isEmpty());
        assertFalse(activity.instance());
    }

    @Test
    void aBuilderCanBeReusedWithoutChangingEarlierActivities() {
        Activity.Builder builder = Activity.builder().details("first");
        Activity first = builder.build();

        builder.details("second").button("b", "https://example.com").flag(ActivityFlag.JOIN);
        Activity second = builder.build();

        assertEquals("first", first.details());
        assertTrue(first.buttons().isEmpty());
        assertTrue(first.flags().isEmpty());
        assertEquals("second", second.details());
        assertEquals(1, second.buttons().size());
    }

    @Test
    void buttonsAndFlagsCannotBeModifiedAfterwards() {
        Activity activity = Activity.of(a -> a.button("b", "https://example.com").flag(ActivityFlag.JOIN));

        assertThrows(UnsupportedOperationException.class, () -> activity.buttons().add(new Button("x", "https://x")));
        assertThrows(UnsupportedOperationException.class, () -> activity.flags().add(ActivityFlag.SPECTATE));
    }

    @Test
    void constructorCopiesTheCollectionsItIsGiven() {
        Set<ActivityFlag> flags = EnumSet.of(ActivityFlag.JOIN);
        List<Button> buttons = new ArrayList<>();
        Activity activity = new Activity(null, ActivityType.PLAYING, null, null, null, null, null, null, null, null, Assets.EMPTY, Secrets.EMPTY, buttons, false, flags, Map.of());

        flags.add(ActivityFlag.SPECTATE);
        buttons.add(new Button("late", "https://late"));

        assertEquals(Set.of(ActivityFlag.JOIN), activity.flags());
        assertTrue(activity.buttons().isEmpty());
    }

    @Test
    void allowsExactlyTwoButtonsAtTheirLengthLimits() {
        String label = "l".repeat(Button.MAX_LABEL);
        String url = "https://example.com/" + "u".repeat(Button.MAX_URL - "https://example.com/".length());

        Activity activity = Activity.of(a -> a.button(label, url).button("second", "https://example.com"));

        assertEquals(2, activity.buttons().size());
        assertThrows(IllegalArgumentException.class, () -> Activity.of(a -> a.button(label, url).button("second", "https://example.com").button("third", "https://example.com")));
        assertThrows(IllegalArgumentException.class, () -> new Button(label + "x", url));
        assertThrows(IllegalArgumentException.class, () -> new Button(label, url + "x"));
        assertThrows(IllegalArgumentException.class, () -> new Button("label", ""));
    }

    @Test
    void partyRules() {
        assertFalse(Party.of("id", 0, 0).hasSize());
        assertTrue(Party.sized(1, 4).hasSize());
        assertDoesNotThrow(() -> Party.sized(4, 4));
        assertDoesNotThrow(() -> Party.sized(0, 4));
        assertThrows(IllegalArgumentException.class, () -> Party.sized(-1, 4));
        assertThrows(IllegalArgumentException.class, () -> Party.sized(1, -4));
        assertThrows(IllegalArgumentException.class, () -> Party.sized(5, 4));
        assertThrows(IllegalArgumentException.class, () -> Party.sized(1, 0));
    }

    @Test
    void timestampRules() {
        Instant start = Instant.ofEpochSecond(100);
        Instant end = Instant.ofEpochSecond(200);

        assertEquals(new Timestamps(start, null), Timestamps.since(start));
        assertEquals(new Timestamps(null, end), Timestamps.until(end));
        assertEquals(new Timestamps(start, end), Timestamps.between(start, end));
        assertDoesNotThrow(() -> Timestamps.between(start, start));
        assertDoesNotThrow(() -> new Timestamps(null, null));
        assertThrows(IllegalArgumentException.class, () -> Timestamps.between(end, start));
    }

    @Test
    void activityTypesMatchDiscordsIdsAndRpcSupport() {
        assertEquals(0, ActivityType.PLAYING.id());
        assertEquals(1, ActivityType.STREAMING.id());
        assertEquals(2, ActivityType.LISTENING.id());
        assertEquals(3, ActivityType.WATCHING.id());
        assertEquals(4, ActivityType.CUSTOM.id());
        assertEquals(5, ActivityType.COMPETING.id());

        Set<ActivityType> supported = new HashSet<>();
        for (ActivityType type : ActivityType.values()) {
            if (type.rpcSupported()) supported.add(type);
        }
        assertEquals(Set.of(ActivityType.PLAYING, ActivityType.LISTENING, ActivityType.WATCHING, ActivityType.COMPETING), supported);
    }

    @Test
    void statusDisplayTypesMatchDiscordsIds() {
        assertEquals(0, StatusDisplayType.NAME.id());
        assertEquals(1, StatusDisplayType.STATE.id());
        assertEquals(2, StatusDisplayType.DETAILS.id());
    }

    @Test
    void activityFlagsAreDistinctBits() {
        int expected = 1;
        for (ActivityFlag flag : ActivityFlag.values()) {
            assertEquals(expected, flag.mask(), flag.name());
            expected <<= 1;
        }
        assertEquals(0, ActivityFlag.toBits(Set.of()));
        assertEquals(1, ActivityFlag.toBits(Set.of(ActivityFlag.INSTANCE)));
        assertEquals(6, ActivityFlag.toBits(Set.of(ActivityFlag.JOIN, ActivityFlag.SPECTATE)));
        assertEquals(511, ActivityFlag.toBits(EnumSet.allOf(ActivityFlag.class)));
    }

    @Test
    void assetsWithersChangeOnlyTheirOwnField() {
        Assets assets = Assets.EMPTY
          .withLarge("big", "Big")
          .withLargeUrl("https://big")
          .withSmall("small", "Small")
          .withSmallUrl("https://small")
          .withInviteCoverImage("cover");

        assertEquals(new Assets("big", "Big", "https://big", "small", "Small", "https://small", "cover"), assets);
        assertEquals(new Assets("other", null, "https://big", "small", "Small", "https://small", "cover"), assets.withLarge("other", null));
        assertEquals(new Assets(null, null, null, null, null, null, null), Assets.EMPTY);
    }

    @Test
    void secretsWithersChangeOnlyTheirOwnField() {
        Secrets secrets = Secrets.EMPTY.withJoin("j").withSpectate("s").withMatch("m");

        assertEquals(new Secrets("j", "s", "m"), secrets);
        assertEquals(new Secrets("j2", "s", "m"), secrets.withJoin("j2"));
    }

}
