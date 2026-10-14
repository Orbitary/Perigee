/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.event;

import org.jetbrains.annotations.Nullable;


/**
 * The connection to Discord dropped.
 *
 * @param cause        why it dropped, if known
 * @param reconnecting whether the client will try again
 */
public record Disconnected(
  @Nullable Throwable cause,
  boolean reconnecting
) implements PerigeeEvent {}
