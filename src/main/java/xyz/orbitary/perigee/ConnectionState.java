/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;


/**
 * Where a {@link PerigeeClient} is in its connection life cycle.
 */
public enum ConnectionState {
    /**
     * {@link PerigeeClient#connect()} has not been called.
     */
    IDLE,
    /**
     * Opening the IPC endpoint or waiting for Discord's READY.
     */
    CONNECTING,
    /**
     * The handshake finished; presence is being sent.
     */
    READY,
    /**
     * Waiting to try again after the connection dropped or Discord was not reachable.
     */
    BACKOFF,
    /**
     * Closed for good, by {@link PerigeeClient#close()} or a fatal error.
     */
    CLOSED
}
