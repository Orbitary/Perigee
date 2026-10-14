/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.orbitary.perigee.support.Payloads;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


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
    void writesPrimitivesAndEmptyContainers() {
        assertEquals("null", Json.write(null));
        assertEquals("true", Json.write(true));
        assertEquals("{}", Json.write(Map.of()));
        assertEquals("[]", Json.write(List.of()));
        assertEquals("\"\"", Json.write(""));
        assertEquals(Long.toString(Long.MIN_VALUE), Json.write(Long.MIN_VALUE));
        assertEquals("1.5", Json.write(1.5));
        assertEquals("[[],{}]", Json.write(List.of(List.of(), Map.of())));
    }

    @Test
    void escapesStrings() {
        assertEquals("\"a\\\"b\\\\c\\n\\u0001\"", Json.write("a\"b\\c\n\u0001"));
    }

    @Test
    void escapesEveryControlCharacter() {
        StringBuilder all = new StringBuilder();
        for (char c = 0; c < 0x20; c++) {
            all.append(c);
        }

        String json = Json.write(all.toString());

        assertEquals(Optional.of(all.toString()), Json.parse(json));
        for (char c : json.toCharArray()) {
            assertTrue(c >= 0x20, "raw control character leaked into output");
        }
    }

    @Test
    void refusesToWriteWhatJsonCannotRepresent() {
        Map<Object, Object> nullKey = new LinkedHashMap<>();
        nullKey.put(null, 1);

        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of(1, "x")));
        assertThrows(IllegalArgumentException.class, () -> Json.write(nullKey));
    }

    @Test
    void roundTripsAwkwardText() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "mixed \ud83d\ude00 \u00e9 \u4e2d \"quote\" \\ slash \n newline \t tab");
        map.put("list", List.of(1L, 2.5, false));

        assertEquals(Optional.of(map), Json.parse(Json.write(map)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "-01", "00", "1.", "1.e5", ".5", "+1", "1e", "1e+", "--1", "0x10", "-", "1.5.5", "1e5e5", "NaN", "Infinity", "- 1"})
    void rejectsNumbersThatStrictJsonRejects(String text) {
        assertThrows(JsonException.class, () -> Json.parse(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0", "7", "-12", "0.5", "-0.5", "1E5", "1e-2", "-12.5e+3", "123456789012", "0e0"})
    void acceptsValidNumbers(String text) {
        assertInstanceOf(Number.class, Json.parse(text).orElseThrow(), text);
    }

    @Test
    void integersAreLongsAndFractionsAreDoubles() {
        assertEquals(Optional.of(42L), Json.parse("42"));
        assertEquals(Optional.of(-12L), Json.parse(" -12 "));
        assertEquals(Optional.of(0.5), Json.parse("0.5"));
        assertEquals(Optional.of(1000.0), Json.parse("1e3"));
    }

    @Test
    void integersBeyondLongFallBackToDouble() {
        assertEquals(Optional.of(1.2345678901234567E19), Json.parse("12345678901234567890"));
        assertEquals(Optional.of(Long.MAX_VALUE), Json.parse(Long.toString(Long.MAX_VALUE)));
        assertEquals(Optional.of(Long.MIN_VALUE), Json.parse(Long.toString(Long.MIN_VALUE)));
    }

    @Test
    void numbersEndAtTheirBoundary() {
        assertEquals(List.of(1L, 2L), Json.parse("[1,2]").orElseThrow());
        assertThrows(JsonException.class, () -> Json.parse("[1 2]"));
        assertThrows(JsonException.class, () -> Json.parse("1 2"));
    }

    @Test
    void parsesLiterals() {
        assertEquals(Optional.of(true), Json.parse("true"));
        assertEquals(Optional.of(false), Json.parse("false"));
        assertEquals(Optional.empty(), Json.parse("null"));
    }

    @Test
    void decodesSurrogatePairsAndUnicodeEscapes() {
        assertEquals(Optional.of("\ud83d\ude00"), Json.parse("\"\\ud83d\\ude00\""));
        assertEquals(Optional.of("\ud83d\ude00"), Json.parse("\"\ud83d\ude00\""));
        assertEquals(Optional.of("A\u00e9\u4e2d"), Json.parse("\"\\u0041\\u00e9\\u4e2d\""));
        assertEquals(Optional.of("/"), Json.parse("\"\\/\""));
    }

    @Test
    void lastDuplicateKeyWins() {
        assertEquals(Map.of("a", 2L), Json.parse("{\"a\":1,\"a\":2}").orElseThrow());
    }

    @Test
    void toleratesWhitespaceEverywhere() {
        assertEquals(Map.of("a", List.of(1L, 2L)), Json.parse(" \n\t\r{ \"a\" \n: [ 1 ,\t2 ] } \r\n").orElseThrow());
    }

    @Test
    void preservesObjectKeyOrder() {
        Map<?, ?> parsed = (Map<?, ?>)Json.parse("{\"z\":1,\"a\":2,\"m\":3}").orElseThrow();

        assertEquals(List.of("z", "a", "m"), List.copyOf(parsed.keySet()));
    }

    @Test
    void enforcesTheNestingLimitExactly() {
        assertDoesNotThrow(() -> Json.parse("[".repeat(65) + "]".repeat(65)));
        assertThrows(JsonException.class, () -> Json.parse("[".repeat(66) + "]".repeat(66)));
        assertThrows(JsonException.class, () -> Json.parse("[".repeat(100_000)));
    }

    @Test
    void rejectsRawControlCharactersAndBadEscapes() {
        assertThrows(JsonException.class, () -> Json.parse("\"line\nbreak\""));
        assertThrows(JsonException.class, () -> Json.parse("\"tab\there\""));
        assertThrows(JsonException.class, () -> Json.parse("\"\\x41\""));
        assertThrows(JsonException.class, () -> Json.parse("\"\\u12\""));
        assertThrows(JsonException.class, () -> Json.parse("\"\\u12G4\""));
        assertThrows(JsonException.class, () -> Json.parse("\"dangling\\"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "{", "}", "[", "]", "{\"a\"}", "{\"a\":}", "{a:1}", "{'a':1}", "[1,]", "[,1]", "{\"a\":1,}", "tru", "nul", "True", "\"open", "[1}", "{\"a\":1]", "1 1", "{} {}", "{\"a\":1} extra"})
    void rejectsMalformedDocuments(String text) {
        assertThrows(JsonException.class, () -> Json.parse(text), text);
    }

    @Test
    void parsesARealReadyPayload() {
        Map<?, ?> parsed = (Map<?, ?>)Json.parse(Payloads.READY).orElseThrow();

        assertEquals("READY", parsed.get("evt"));
        Map<?, ?> data = (Map<?, ?>)Objects.requireNonNull(parsed.get("data"));
        Map<?, ?> user = (Map<?, ?>)Objects.requireNonNull(data.get("user"));
        assertEquals("tester", user.get("username"));
        assertTrue(parsed.containsKey("nonce"));
        assertNull(parsed.get("nonce"));
        assertEquals(false, user.get("bot"));
    }

}
