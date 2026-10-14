/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.support;

import xyz.orbitary.perigee.json.Json;

import java.util.Map;
import java.util.Objects;


public final class Wire {
    private Wire() {}

    public static Map<?, ?> object(String json) {
        return (Map<?, ?>)Json.parse(json).orElseThrow();
    }

    public static Map<?, ?> child(Map<?, ?> map, String key) {
        return (Map<?, ?>)Objects.requireNonNull(map.get(key), key);
    }

    public static long number(Map<?, ?> map, String key) {
        return ((Number)Objects.requireNonNull(map.get(key), key)).longValue();
    }

    public static String text(Map<?, ?> map, String key) {
        return (String)Objects.requireNonNull(map.get(key), key);
    }

    public static Map<?, ?> activity(Map<?, ?> command) {
        return child(child(command, "args"), "activity");
    }

}
