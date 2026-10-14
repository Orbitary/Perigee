/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

/**
 * The library's logger. Output is routed by whichever {@link System.Logger} backend the application configures.
 */
public final class Logger {
    private static final System.Logger LOGGER = System.getLogger("xyz.orbitary.perigee");

    private Logger() {}

    public static void warn(String message, Object... args) {
        LOGGER.log(System.Logger.Level.WARNING, message, args);
    }

    public static void warn(String message, Throwable cause) {
        LOGGER.log(System.Logger.Level.WARNING, message, cause);
    }

    public static void debug(String message, Object... args) {
        LOGGER.log(System.Logger.Level.DEBUG, message, args);
    }

}
