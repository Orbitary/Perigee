/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.event;

/**
 * The local user accepted a join invite.
 *
 * @param secret The value set in the activity's join secret
 */
public record Join(
  String secret
) implements PerigeeEvent {}
