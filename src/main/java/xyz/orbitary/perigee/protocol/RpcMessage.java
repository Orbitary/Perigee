/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import org.jetbrains.annotations.Nullable;
import xyz.orbitary.perigee.RpcException;
import xyz.orbitary.perigee.json.Json;
import xyz.orbitary.perigee.user.DiscordUser;

import java.util.Map;
import java.util.Optional;


/**
 * A decoded FRAME payload, either:
 * - a command response
 * - a dispatched event
 */
public record RpcMessage(
  @Nullable String cmd,
  @Nullable String evt,
  @Nullable String nonce,
  Map<String, Object> data
) {
    public static RpcMessage parse(String json) {
        Map<?, ?> raw = object(Json.parse(json));
        return new RpcMessage(text(raw, "cmd").orElse(null), text(raw, "evt").orElse(null), text(raw, "nonce").orElse(null), map(raw, "data"));
    }

    public boolean isError() {
        return "ERROR".equals(evt);
    }

    public RpcException toException() {
        return exception(data);
    }

    public static RpcException closeReason(String json) {
        return exception(object(Json.parse(json)));
    }

    public static DiscordUser user(Map<?, ?> map) {
        return new DiscordUser(
          String.valueOf(map.get("id")),
          String.valueOf(map.get("username")),
          text(map, "global_name").orElse(null),
          text(map, "avatar").orElse(null)
        );
    }

    public static Optional<String> text(Map<?, ?> map, String key) {
        return map.get(key) instanceof String s ? Optional.of(s) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<?, ?> map, String key) {
        return map.get(key) instanceof Map<?, ?> nested ? (Map<String, Object>)nested : Map.of();
    }

    private static Map<?, ?> object(Optional<Object> parsed) {
        if (parsed.orElse(null) instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("RPC payload is not a JSON object");
    }

    private static RpcException exception(Map<?, ?> map) {
        int code = map.get("code") instanceof Number n ? n.intValue() : 0;
        String message = map.get("message") instanceof String s ? s : "Unknown error";
        return new RpcException(code, message);
    }

}
