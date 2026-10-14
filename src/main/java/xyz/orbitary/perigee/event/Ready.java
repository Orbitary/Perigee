/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.event;

import xyz.orbitary.perigee.user.DiscordUser;


/**
 * The handshake finished. Fired again after every reconnect.
 *
 * @param user The user that is logged in
 */
public record Ready(
  DiscordUser user
) implements PerigeeEvent {}
