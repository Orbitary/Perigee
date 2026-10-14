/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

/**
 * A single IPC message: an opcode and its JSON payload.
 */
public record Frame(
  Opcode opcode,
  String payload
) {}
