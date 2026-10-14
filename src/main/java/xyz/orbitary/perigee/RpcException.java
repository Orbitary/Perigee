/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.jetbrains.annotations.Nullable;

import java.io.Serial;


/**
 * Discord rejected a command or the handshake.
 */
public final class RpcException extends PerigeeException {
    private static final @Serial long serialVersionUID = 1L;

    static final int INVALID_CLIENT_ID = 4000;
    static final int INVALID_ORIGIN = 4001;
    static final int RATE_LIMITED = 4002;
    static final int INVALID_VERSION = 4004;
    static final int INVALID_ENCODING = 4005;

    private final int code;
    private final String reason;
    private final @Nullable String command;

    public RpcException(int code, String reason) {
        this(code, reason, null);
    }

    public RpcException(int code, String reason, @Nullable String command) {
        super(reason + " (code " + code + (command == null ? "" : ", command " + command) + ")");
        this.code = code;
        this.reason = reason;
        this.command = command;
    }

    public int code() {
        return code;
    }

    /**
     * Discord's own explanation, without the code or command.
     */
    public String reason() {
        return reason;
    }

    /**
     * The command Discord rejected, or {@code null} when it rejected the connection itself.
     */
    public @Nullable String command() {
        return command;
    }

    /**
     * Retrying cannot help: the application ID or the protocol settings are wrong.
     */
    boolean isFatal() {
        return code == INVALID_CLIENT_ID || code == INVALID_ORIGIN || code == INVALID_VERSION || code == INVALID_ENCODING;
    }

    boolean isRateLimited() {
        return code == RATE_LIMITED;
    }

}
