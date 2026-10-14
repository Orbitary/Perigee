/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;


/**
 * Field rules Discord enforces on activities. Lengths count code points, not UTF-16 units.
 */
final class Validation {
    static final int MIN_TEXT = 2;
    static final int MAX_TEXT = 128;
    static final int MAX_URL = 512;

    private static final String MEDIA_PROXY_PREFIX = "mp:";

    private Validation() {}

    /**
     * Trims the value; blank becomes {@code null}.
     */
    static @Nullable String text(String field, @Nullable String value) {
        return bounded(field, value, MIN_TEXT, MAX_TEXT);
    }

    /**
     * Trims the value; blank becomes {@code null}.
     */
    static @Nullable String url(String field, @Nullable String value) {
        String url = blankToNull(value);
        if (url == null) return null;
        requireHttpUrl(field, url);
        return url;
    }

    /**
     * An asset key, an {@code http(s)} URL or a {@code mp:} media proxy path. The value is never rewritten.
     */
    static @Nullable String image(String field, @Nullable String value) {
        String image = blankToNull(value);
        if (image == null) return null;
        if (isHttp(image)) {
            requireHttpUrl(field, image);
        } else if (image.startsWith(MEDIA_PROXY_PREFIX)) {
            bounded(field, image, 1, MAX_URL);
        } else {
            return text(field, image);
        }
        return image;
    }

    static @Nullable String maxLength(String field, @Nullable String value, int max) {
        return bounded(field, value, 1, max);
    }

    static String requireText(String field, @Nullable String value, int max) {
        String text = bounded(field, value, 1, max);
        if (text == null) throw new IllegalArgumentException(field + " must not be blank");
        return text;
    }

    static String requireUrl(String field, @Nullable String value) {
        String url = blankToNull(value);
        if (url == null) throw new IllegalArgumentException(field + " must not be blank");
        requireHttpUrl(field, url);
        return url;
    }

    private static @Nullable String bounded(String field, @Nullable String value, int min, int max) {
        String text = blankToNull(value);
        if (text == null) return null;
        int length = length(text);
        if (length < min || length > max) {
            throw new IllegalArgumentException(field + " must be " + min + "-" + max + " characters, was " + length);
        }
        return text;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    private static boolean isHttp(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7) || value.regionMatches(true, 0, "https://", 0, 8);
    }

    private static void requireHttpUrl(String field, String value) {
        bounded(field, value, 1, MAX_URL);
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(field + " is not a valid URL", e);
        }
        String scheme = uri.getScheme();
        boolean http = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        if (!http || uri.getHost() == null) throw new IllegalArgumentException(field + " must be an http or https URL");
    }

}
