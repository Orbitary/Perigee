/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

import org.jetbrains.annotations.Nullable;

/**
 * Image fields take an uploaded asset key or a public image URL, which Discord proxies.
 */
public record Assets(
        @Nullable String largeImage,
        @Nullable String largeText,
        @Nullable String largeUrl,
        @Nullable String smallImage,
        @Nullable String smallText,
        @Nullable String smallUrl,
        @Nullable String inviteCoverImage
) {
    public static final Assets EMPTY = new Assets(null, null, null, null, null, null, null);

    public Assets withLarge(String image, @Nullable String text) {
        return new Assets(image, text, largeUrl, smallImage, smallText, smallUrl, inviteCoverImage);
    }

    public Assets withLargeUrl(String url) {
        return new Assets(largeImage, largeText, url, smallImage, smallText, smallUrl, inviteCoverImage);
    }

    public Assets withSmall(String image, @Nullable String text) {
        return new Assets(largeImage, largeText, largeUrl, image, text, smallUrl, inviteCoverImage);
    }

    public Assets withSmallUrl(String url) {
        return new Assets(largeImage, largeText, largeUrl, smallImage, smallText, url, inviteCoverImage);
    }

    public Assets withInviteCoverImage(String image) {
        return new Assets(largeImage, largeText, largeUrl, smallImage, smallText, smallUrl, image);
    }
}
