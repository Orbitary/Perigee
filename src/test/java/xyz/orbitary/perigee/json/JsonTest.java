/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.json;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {
    @Test
    void writesNestedStructures() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cmd", "SET_ACTIVITY");
        map.put("pid", 42L);
        map.put("size", List.of(1, 4));
        map.put("none", null);
        map.put("ok", true);
        assertEquals("{\"cmd\":\"SET_ACTIVITY\",\"pid\":42,\"size\":[1,4],\"none\":null,\"ok\":true}", Json.write(map));
    }

    @Test
    void escapesStrings() {
        assertEquals("\"a\\\"b\\\\c\\n\\u0001\"", Json.write("a\"b\\c\n\u0001"));
    }

    @Test
    void roundTrips() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "h\u00e9llo \ud83d\ude00 \"q\"\n");
        map.put("list", List.of(1L, 2.5, false));
        assertEquals(map, Json.parse(Json.write(map)));
    }

    @Test
    void parsesNumbersAndLiterals() {
        assertEquals(-12L, Json.parse(" -12 "));
        assertEquals(1.5e2, Json.parse("1.5e2"));
        assertEquals(true, Json.parse("true"));
        assertNull(Json.parse("null"));
        assertEquals("\u00e9", Json.parse("\"\\u00e9\""));
    }

    @Test
    void rejectsMalformedInput() {
        for (String bad : List.of("", "{", "{\"a\":}", "[1,]x", "\"abc", "tru", "{\"a\":1} extra", "\"\\q\"", "-", "[".repeat(100))) {
            assertThrows(JsonException.class, () -> Json.parse(bad), bad);
        }
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of(1, "x")));
    }
}
