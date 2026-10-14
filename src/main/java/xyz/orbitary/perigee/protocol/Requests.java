/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import org.jetbrains.annotations.Nullable;
import xyz.orbitary.perigee.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Builds outgoing JSON payloads.
 */
public final class Requests {
    public static final String SET_ACTIVITY = "SET_ACTIVITY";
    public static final String SUBSCRIBE = "SUBSCRIBE";
    public static final String ACCEPT_JOIN = "SEND_ACTIVITY_JOIN_INVITE";
    public static final String REJECT_JOIN = "CLOSE_ACTIVITY_REQUEST";

    public static final String EVENT_JOIN = "ACTIVITY_JOIN";
    public static final String EVENT_SPECTATE = "ACTIVITY_SPECTATE";
    public static final String EVENT_JOIN_REQUEST = "ACTIVITY_JOIN_REQUEST";

    private static final int RPC_VERSION = 1;

    private Requests() {}

    public static String handshake(String clientId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("v", RPC_VERSION);
        map.put("client_id", clientId);
        return Json.write(map);
    }

    public static String command(String cmd, Map<String, Object> args, String nonce, @Nullable String evt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cmd", cmd);
        map.put("args", args);
        map.put("nonce", nonce);
        if (evt != null) map.put("evt", evt);
        return Json.write(map);
    }

}
