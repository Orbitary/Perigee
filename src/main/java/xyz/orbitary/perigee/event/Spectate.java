/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.event;

/**
 * The local user asked to spectate.
 *
 * @param secret The value set in the activity's spectate secret
 */
public record Spectate(
  String secret
) implements PerigeeEvent {}
