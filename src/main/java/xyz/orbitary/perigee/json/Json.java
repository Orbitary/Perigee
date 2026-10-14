/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.json;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer for the Discord protocol.
 */
public final class Json {
    private static final int MAX_DEPTH = 64;

    private Json() {}

    /**
     * Serialises a {@link Map}, {@link Collection}, {@link CharSequence}, {@link Number}, {@link Boolean} or {@code null}.
     *
     * @throws IllegalArgumentException for unsupported types or non-finite numbers
     */
    public static String write(@Nullable Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    /**
     * Parses a complete JSON document.
     *
     * @throws JsonException if the input is malformed
     */
    public static @Nullable Object parse(CharSequence text) {
        Parser parser = new Parser(text);
        Object value = parser.value(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) throw parser.error("Trailing content");
        return value;
    }

    private static void write(@Nullable Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case Boolean b -> out.append(b.booleanValue());
            case Double d -> out.append(finite(d));
            case Float f -> out.append(finite(f));
            case Number n -> out.append(n);
            case CharSequence s -> writeString(s, out);
            case Map<?, ?> map -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("JSON object keys must be strings: " + entry.getKey());
                    if (!first) out.append(',');
                    first = false;
                    writeString(key, out);
                    out.append(':');
                    write(entry.getValue(), out);
                }
                out.append('}');
            }
            case Collection<?> list -> {
                out.append('[');
                boolean first = true;
                for (Object item : list) {
                    if (!first) out.append(',');
                    first = false;
                    write(item, out);
                }
                out.append(']');
            }
            default -> throw new IllegalArgumentException("Unsupported JSON type: " + value.getClass().getName());
        }
    }

    private static String finite(double d) {
        if (!Double.isFinite(d)) throw new IllegalArgumentException("JSON numbers must be finite: " + d);
        return Double.toString(d);
    }

    private static void writeString(CharSequence s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final CharSequence text;
        private int pos;

        Parser(CharSequence text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        JsonException error(String message) {
            return new JsonException(message + " at index " + pos);
        }

        void skipWhitespace() {
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        @Nullable Object value(int depth) {
            if (depth > MAX_DEPTH) throw error("Nesting too deep");
            skipWhitespace();
            if (atEnd()) throw error("Unexpected end of input");
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object(depth);
                case '[' -> array(depth);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) yield number();
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        private @Nullable Object literal(String word, @Nullable Object result) {
            if (!text.subSequence(pos, Math.min(text.length(), pos + word.length())).toString().equals(word)) throw error("Invalid literal");
            pos += word.length();
            return result;
        }

        private Map<String, Object> object(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) throw error("Expected object key");
                String key = string();
                skipWhitespace();
                expect(':');
                map.put(key, value(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                expect('}');
                return map;
            }
        }

        private List<Object> array(int depth) {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(value(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                    continue;
                }
                expect(']');
                return list;
            }
        }

        private String string() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw error("Unterminated string");
                char c = text.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c < 0x20) throw error("Control character in string");
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) throw error("Unterminated escape");
                char e = text.charAt(pos++);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw error("Truncated unicode escape");
                        try {
                            sb.append((char) Integer.parseInt(text.subSequence(pos, pos + 4).toString(), 16));
                        } catch (NumberFormatException ex) {
                            throw error("Invalid unicode escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("Invalid escape '\\" + e + "'");
                }
            }
        }

        private Number number() {
            int start = pos;
            boolean integral = true;
            if (peek('-')) pos++;
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c >= '0' && c <= '9') pos++;
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    integral = false;
                    pos++;
                } else break;
            }
            String token = text.subSequence(start, pos).toString();
            try {
                return integral ? (Number) Long.parseLong(token) : (Number) Double.parseDouble(token);
            } catch (NumberFormatException e) {
                pos = start;
                throw error("Invalid number");
            }
        }

        private boolean peek(char c) {
            return !atEnd() && text.charAt(pos) == c;
        }

        private void expect(char c) {
            if (!peek(c)) throw error("Expected '" + c + "'");
            pos++;
        }
    }
}
