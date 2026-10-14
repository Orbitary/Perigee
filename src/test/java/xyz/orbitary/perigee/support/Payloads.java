/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.support;

import xyz.orbitary.perigee.json.Json;
import xyz.orbitary.perigee.user.DiscordUser;

import java.util.Map;


/**
 * The registry of wire payloads and identities the tests share.
 */
public final class Payloads {
    public static final String APPLICATION_ID = "1234567890";

    public static final DiscordUser USER = new DiscordUser("42", "tester", "Tester", null);
    public static final Map<String, Object> JOINER = Map.of("id", "77", "username", "joiner", "global_name", "Joiner", "avatar", "abc123");
    public static final DiscordUser JOINER_USER = new DiscordUser("77", "joiner", "Joiner", "abc123");

    public static final String READY = "{\"cmd\":\"DISPATCH\",\"data\":{\"v\":1,\"config\":{\"cdn_host\":\"cdn.discordapp.com\",\"api_endpoint\":\"//discord.com/api\",\"environment\":\"production\"},\"user\":{\"id\":\"42\",\"username\":\"tester\",\"discriminator\":\"0\",\"global_name\":\"Tester\",\"avatar\":null,\"bot\":false}},\"evt\":\"READY\",\"nonce\":null}";

    private Payloads() {}

    public static String dispatch(String evt, Map<String, Object> data) {
        return Json.write(Map.of("cmd", "DISPATCH", "evt", evt, "data", data));
    }

    public static String join(String secret) {
        return dispatch("ACTIVITY_JOIN", Map.of("secret", secret));
    }

    public static String spectate(String secret) {
        return dispatch("ACTIVITY_SPECTATE", Map.of("secret", secret));
    }

    public static String joinRequest(Map<String, Object> user) {
        return dispatch("ACTIVITY_JOIN_REQUEST", Map.of("user", user));
    }

    public static String ok(Map<String, Object> command) {
        return Json.write(Map.of("cmd", command.get("cmd"), "data", Map.of(), "nonce", command.get("nonce")));
    }

    public static String error(Map<String, Object> command, int code, String message) {
        return Json.write(Map.of("cmd", command.get("cmd"), "evt", "ERROR", "data", Map.of("code", code, "message", message), "nonce", command.get("nonce")));
    }

    public static String unsolicitedError(int code, String message) {
        return dispatch("ERROR", Map.of("code", code, "message", message));
    }

    public static String close(int code, String message) {
        return Json.write(Map.of("code", code, "message", message));
    }

}
