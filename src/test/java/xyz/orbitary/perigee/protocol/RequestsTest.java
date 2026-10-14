/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.protocol;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


class RequestsTest {
    @Test
    void handshakeIsVersionOneWithTheClientId() {
        assertEquals("{\"v\":1,\"client_id\":\"1234567890\"}", Requests.handshake("1234567890"));
    }

    @Test
    void commandCarriesCmdArgsAndNonce() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("pid", 42L);

        assertEquals("{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":42},\"nonce\":\"n\"}", Requests.command(Requests.SET_ACTIVITY, args, "n", null));
    }

    @Test
    void subscriptionsNameTheirEvent() {
        assertEquals("{\"cmd\":\"SUBSCRIBE\",\"args\":{},\"nonce\":\"n\",\"evt\":\"ACTIVITY_JOIN\"}", Requests.command(Requests.SUBSCRIBE, Map.of(), "n", Requests.EVENT_JOIN));
    }

    @Test
    void commandNamesMatchDiscordsDocumentation() {
        assertEquals("SET_ACTIVITY", Requests.SET_ACTIVITY);
        assertEquals("SUBSCRIBE", Requests.SUBSCRIBE);
        assertEquals("SEND_ACTIVITY_JOIN_INVITE", Requests.ACCEPT_JOIN);
        assertEquals("CLOSE_ACTIVITY_REQUEST", Requests.REJECT_JOIN);
        assertEquals("ACTIVITY_JOIN", Requests.EVENT_JOIN);
        assertEquals("ACTIVITY_SPECTATE", Requests.EVENT_SPECTATE);
        assertEquals("ACTIVITY_JOIN_REQUEST", Requests.EVENT_JOIN_REQUEST);
    }

}
