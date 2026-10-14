/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;


class ActivityValidationTest {
    private static final String EMOJI = "😀";

    private static void rejects(Consumer<Activity.Builder> configure) {
        assertThrows(IllegalArgumentException.class, () -> Activity.of(configure));
    }

    @Test
    void textIsTrimmedAndBlankBecomesNull() {
        Activity activity = Activity.of(a -> a.name("  Game  ").details("   ").state(""));

        assertEquals("Game", activity.name());
        assertNull(activity.details());
        assertNull(activity.state());
    }

    @Test
    void textMustBeTwoToOneHundredTwentyEightCodePoints() {
        assertDoesNotThrow(() -> Activity.of(a -> a.details("ab")));
        assertDoesNotThrow(() -> Activity.of(a -> a.details("a".repeat(128))));
        rejects(a -> a.details("a"));
        rejects(a -> a.details("a".repeat(129)));
        rejects(a -> a.name("a"));
        rejects(a -> a.state("a"));
    }

    @Test
    void lengthCountsCodePointsNotUtf16Units() {
        assertEquals(2, EMOJI.length());
        rejects(a -> a.details(EMOJI));
        assertDoesNotThrow(() -> Activity.of(a -> a.details(EMOJI.repeat(128))));
        rejects(a -> a.details(EMOJI.repeat(129)));
        assertEquals(1, EMOJI.codePointCount(0, EMOJI.length()));
    }

    @Test
    void assetTextFollowsTheTextRules() {
        rejects(a -> a.largeImage("big", "x"));
        rejects(a -> a.smallImage("small", "x".repeat(129)));
        assertNull(Activity.of(a -> a.largeImage("big", "  ")).assets().largeText());
    }

    @Test
    void urlFieldsMustBeHttpOrHttps() {
        assertDoesNotThrow(() -> Activity.of(a -> a.detailsUrl("https://example.com").stateUrl("http://example.com/x")));
        rejects(a -> a.detailsUrl("ftp://example.com"));
        rejects(a -> a.stateUrl("example.com"));
        rejects(a -> a.largeUrl("javascript:alert(1)"));
        rejects(a -> a.smallUrl("https://"));
        rejects(a -> a.detailsUrl("https://example.com/" + "u".repeat(512)));
        assertNull(Activity.of(a -> a.detailsUrl("  ")).detailsUrl());
    }

    @Test
    void imagesAreAcceptedAsKeysUrlsAndMediaProxyPathsAndAreNotRewritten() {
        String proxy = "mp:external/abc/https/cdn.example.com/" + "p".repeat(100) + ".png";
        String url = "https://cdn.example.com/" + "p".repeat(200) + ".png";

        Assets assets = Activity.of(a -> a.largeImage("logo").smallImage(url).assets(Assets.EMPTY.withInviteCoverImage(proxy).withLarge("logo", null).withSmall(url, null))).assets();

        assertEquals("logo", assets.largeImage());
        assertEquals(url, assets.smallImage());
        assertEquals(proxy, assets.inviteCoverImage());
        rejects(a -> a.largeImage("k"));
        rejects(a -> a.largeImage("https://"));
        rejects(a -> a.largeImage("key".repeat(50)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "ftp://x.example", "not a url", "https://"})
    void buttonUrlMustBeAValidHttpUrl(String url) {
        assertThrows(IllegalArgumentException.class, () -> new Button("label", url));
    }

    @Test
    void buttonLabelCountsCodePoints() {
        assertDoesNotThrow(() -> new Button(EMOJI.repeat(32), "https://example.com"));
        assertThrows(IllegalArgumentException.class, () -> new Button(EMOJI.repeat(33), "https://example.com"));
        assertThrows(IllegalArgumentException.class, () -> new Button("  ", "https://example.com"));
        assertEquals("go", new Button(" go ", "https://example.com").label());
    }

    @Test
    @SuppressWarnings("NullAway")
    void buttonsAreCheckedAsTheyAreAddedAndOnConstruction() {
        Button button = new Button("b", "https://example.com");
        assertThrows(IllegalArgumentException.class, () -> Activity.builder().button(button).button(button).button(button));
        assertThrows(NullPointerException.class, () -> Activity.builder().button((Button)null));
        assertThrows(IllegalArgumentException.class, () -> new Activity(null, ActivityType.PLAYING, null, null, null, null, null, null, null, null, Assets.EMPTY, Secrets.EMPTY, List.of(button, button, button), false, Set.of()));
    }

    @Test
    void partyIdIsLimitedAndSizeRulesHold() {
        assertDoesNotThrow(() -> Party.of("i".repeat(128), 1, 2));
        assertThrows(IllegalArgumentException.class, () -> Party.of("i".repeat(129), 1, 2));
        assertNull(Party.of("  ", 1, 2).id());
        assertThrows(IllegalArgumentException.class, () -> Party.sized(3, 2));
        assertThrows(IllegalArgumentException.class, () -> Party.sized(-1, 2));
    }

    @Test
    @SuppressWarnings("NullAway")
    void canonicalConstructorRejectsNullsThatTheBuilderWouldNeverProduce() {
        assertThrows(NullPointerException.class, () -> new Activity(null, null, null, null, null, null, null, null, null, null, Assets.EMPTY, Secrets.EMPTY, List.of(), false, Set.of()));
        assertThrows(NullPointerException.class, () -> new Activity(null, ActivityType.PLAYING, null, null, null, null, null, null, null, null, null, Secrets.EMPTY, List.of(), false, Set.of()));
        assertThrows(NullPointerException.class, () -> new Activity(null, ActivityType.PLAYING, null, null, null, null, null, null, null, null, Assets.EMPTY, null, List.of(), false, Set.of()));
        assertThrows(NullPointerException.class, () -> new Activity(null, ActivityType.PLAYING, null, null, null, null, null, null, null, null, Assets.EMPTY, Secrets.EMPTY, null, false, Set.of()));
        assertThrows(NullPointerException.class, () -> new Activity(null, ActivityType.PLAYING, null, null, null, null, null, null, null, null, Assets.EMPTY, Secrets.EMPTY, List.of(), false, null));
    }

    @Test
    void urlIsOnlyValidForStreaming() {
        assertDoesNotThrow(() -> Activity.of(a -> a.type(ActivityType.STREAMING).url("https://twitch.tv/example")));
        rejects(a -> a.type(ActivityType.PLAYING).url("https://twitch.tv/example"));
        rejects(a -> a.type(ActivityType.STREAMING).url("rtmp://example.com"));
    }

    @Test
    void customTypeIsRejected() {
        rejects(a -> a.type(ActivityType.CUSTOM));
    }

}
