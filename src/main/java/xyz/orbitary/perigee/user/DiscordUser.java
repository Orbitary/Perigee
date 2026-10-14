/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.user;

import org.jetbrains.annotations.Nullable;


public record DiscordUser(
  String id,
  String username,
  @Nullable String globalName,
  @Nullable String avatar
) {}
