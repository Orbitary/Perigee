/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.event;

public sealed interface PerigeeEvent permits Ready, Disconnected, Join, Spectate, JoinRequest {}
