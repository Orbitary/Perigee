/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import org.jetbrains.annotations.Nullable;
import xyz.orbitary.perigee.activity.Activity;
import xyz.orbitary.perigee.activity.ActivityFlag;
import xyz.orbitary.perigee.activity.Assets;
import xyz.orbitary.perigee.activity.Button;
import xyz.orbitary.perigee.activity.Party;
import xyz.orbitary.perigee.activity.Secrets;
import xyz.orbitary.perigee.activity.Timestamps;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Maps an {@link Activity} to the JSON object Discord expects, leaving out unset fields.
 * <p>
 * IPC takes timestamps in Unix seconds, as in the Social SDK guide; the gateway activity object documents milliseconds.
 */
public final class ActivityPayload {
    private ActivityPayload() {}

    public static Map<String, Object> toMap(Activity activity) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "name", activity.name());
        map.put("type", activity.type().id());
        put(map, "url", activity.url());
        put(map, "details", activity.details());
        put(map, "details_url", activity.detailsUrl());
        put(map, "state", activity.state());
        put(map, "state_url", activity.stateUrl());
        if (activity.statusDisplayType() != null) map.put("status_display_type", activity.statusDisplayType().id());
        if (activity.timestamps() != null) putIfPresent(map, "timestamps", timestamps(activity.timestamps()));
        if (activity.party() != null) putIfPresent(map, "party", party(activity.party()));
        putIfPresent(map, "assets", assets(activity.assets()));
        putIfPresent(map, "secrets", secrets(activity.secrets()));
        if (!activity.buttons().isEmpty()) map.put("buttons", buttons(activity.buttons()));
        if (activity.instance()) map.put("instance", true);
        if (!activity.flags().isEmpty()) map.put("flags", ActivityFlag.toBits(activity.flags()));
        return map;
    }

    private static Map<String, Object> timestamps(Timestamps timestamps) {
        Map<String, Object> map = new LinkedHashMap<>();
        putSeconds(map, "start", timestamps.start());
        putSeconds(map, "end", timestamps.end());
        return map;
    }

    private static Map<String, Object> party(Party party) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "id", party.id());
        if (party.hasSize()) map.put("size", List.of(party.size(), party.max()));
        return map;
    }

    private static Map<String, Object> assets(Assets assets) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "large_image", assets.largeImage());
        put(map, "large_text", assets.largeText());
        put(map, "large_url", assets.largeUrl());
        put(map, "small_image", assets.smallImage());
        put(map, "small_text", assets.smallText());
        put(map, "small_url", assets.smallUrl());
        put(map, "invite_cover_image", assets.inviteCoverImage());
        return map;
    }

    private static Map<String, Object> secrets(Secrets secrets) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "join", secrets.join());
        put(map, "spectate", secrets.spectate());
        put(map, "match", secrets.match());
        return map;
    }

    private static List<Map<String, Object>> buttons(List<Button> buttons) {
        List<Map<String, Object>> list = new ArrayList<>(buttons.size());
        for (Button button : buttons) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("label", button.label());
            map.put("url", button.url());
            list.add(map);
        }
        return list;
    }

    private static void putSeconds(Map<String, Object> map, String key, @Nullable Instant instant) {
        if (instant != null) map.put(key, instant.getEpochSecond());
    }

    private static void put(Map<String, Object> map, String key, @Nullable String value) {
        if (value != null) map.put(key, value);
    }

    private static void putIfPresent(Map<String, Object> map, String key, Map<String, Object> value) {
        if (!value.isEmpty()) map.put(key, value);
    }

}
