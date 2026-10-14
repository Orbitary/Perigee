/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.transport;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;


/**
 * A connected, bidirectional byte channel to the Discord client.
 */
public interface Transport extends Closeable {
    /**
     * Blocking stream of bytes from Discord. Ends or throws when the connection drops.
     */
    InputStream input();

    void write(byte[] frame) throws IOException;

}
