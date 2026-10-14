/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import java.io.Serial;


/**
 * Discord did not answer in time. The command may still have been applied.
 */
public final class PerigeeTimeoutException extends PerigeeException {
    private static final @Serial long serialVersionUID = 1L;

    public PerigeeTimeoutException(String message) {
        super(message);
    }

}
