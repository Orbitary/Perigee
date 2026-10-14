/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import org.junit.jupiter.api.Test;
import xyz.orbitary.perigee.RpcException;
import xyz.orbitary.perigee.json.Json;
import xyz.orbitary.perigee.support.Payloads;
import xyz.orbitary.perigee.user.DiscordUser;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


class RpcMessageTest {
    @Test
    void parsesACommandResponse() {
        RpcMessage message = RpcMessage.parse("{\"cmd\":\"SET_ACTIVITY\",\"data\":{\"name\":\"x\"},\"evt\":null,\"nonce\":\"n-1\"}");

        assertEquals("SET_ACTIVITY", message.cmd());
        assertNull(message.evt());
        assertEquals("n-1", message.nonce());
        assertEquals(Map.of("name", "x"), message.data());
        assertFalse(message.isError());
    }

    @Test
    void parsesADispatchedEvent() {
        RpcMessage message = RpcMessage.parse(Payloads.join("s"));

        assertEquals("DISPATCH", message.cmd());
        assertEquals("ACTIVITY_JOIN", message.evt());
        assertNull(message.nonce());
        assertEquals("s", message.data().get("secret"));
    }

    @Test
    void missingOrWrongTypedFieldsBecomeNullOrEmpty() {
        RpcMessage message = RpcMessage.parse("{\"cmd\":5,\"data\":[1],\"evt\":true}");

        assertNull(message.cmd());
        assertNull(message.evt());
        assertNull(message.nonce());
        assertTrue(message.data().isEmpty());
        assertTrue(RpcMessage.parse("{}").data().isEmpty());
    }

    @Test
    void errorMessagesCarryDiscordsCodeAndText() {
        RpcMessage message = RpcMessage.parse("{\"cmd\":\"SET_ACTIVITY\",\"evt\":\"ERROR\",\"data\":{\"code\":4000,\"message\":\"Invalid activity\"},\"nonce\":\"n\"}");

        assertTrue(message.isError());
        RpcException exception = message.toException();
        assertEquals(4000, exception.code());
        assertEquals("Invalid activity", exception.reason());
        assertEquals("SET_ACTIVITY", exception.command());
        assertEquals("Invalid activity (code 4000, command SET_ACTIVITY)", exception.getMessage());
    }

    @Test
    void errorsWithoutDetailsStillProduceAnException() {
        RpcException exception = RpcMessage.parse("{\"evt\":\"ERROR\",\"data\":{}}").toException();

        assertEquals(0, exception.code());
        assertEquals("Unknown error (code 0)", exception.getMessage());
    }

    @Test
    void parsesAClosePayload() {
        RpcException reason = RpcMessage.closeReason("{\"code\":4001,\"message\":\"Unknown client\"}");

        assertEquals(4001, reason.code());
        assertEquals("Unknown client (code 4001)", reason.getMessage());
        assertNull(reason.command());
    }

    @Test
    void anEmptyClosePayloadStillExplainsItself() {
        assertEquals(0, RpcMessage.closeReason("{}").code());
    }

    @Test
    void rejectsPayloadsThatAreNotJsonObjects() {
        for (String payload : new String[]{"[]", "1", "\"s\"", "null", "true"}) {
            assertThrows(IllegalArgumentException.class, () -> RpcMessage.parse(payload), payload);
        }
        assertThrows(RuntimeException.class, () -> RpcMessage.parse("{broken"));
        assertThrows(RuntimeException.class, () -> RpcMessage.parse(""));
    }

    @Test
    void buildsAUserFromAFullPayload() {
        Map<?, ?> user = (Map<?, ?>)Json.parse("{\"id\":\"1\",\"username\":\"name\",\"global_name\":\"Global\",\"avatar\":\"hash\",\"discriminator\":\"0\"}").orElseThrow();

        assertEquals(new DiscordUser("1", "name", "Global", "hash"), RpcMessage.user(user));
    }

    @Test
    void optionalUserFieldsMayBeNullOrAbsent() {
        Map<?, ?> user = (Map<?, ?>)Json.parse("{\"id\":\"1\",\"username\":\"name\",\"global_name\":null}").orElseThrow();

        DiscordUser built = RpcMessage.user(user);

        assertNull(built.globalName());
        assertNull(built.avatar());
    }

    @Test
    void numericUserIdsAreAcceptedAsText() {
        assertEquals("123", RpcMessage.user(Map.of("id", 123L, "username", "n")).id());
    }

    @Test
    void textAndMapHelpersAreTypeSafe() {
        Map<String, Object> source = Map.of("s", "v", "n", 1L, "m", Map.of("k", "v"));

        assertEquals(Optional.of("v"), RpcMessage.text(source, "s"));
        assertEquals(Optional.empty(), RpcMessage.text(source, "n"));
        assertEquals(Optional.empty(), RpcMessage.text(source, "missing"));
        assertEquals(Map.of("k", "v"), RpcMessage.map(source, "m"));
        assertEquals(Map.of(), RpcMessage.map(source, "s"));
        assertEquals(Map.of(), RpcMessage.map(source, "missing"));
    }

}
