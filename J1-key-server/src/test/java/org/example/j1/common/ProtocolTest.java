package org.example.j1.common;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolTest {

    @Test
    void requestIsNameBytesPlusNullTerminator() {
        byte[] encoded = Protocol.encodeRequest("alice");

        assertEquals(6, encoded.length);
        assertEquals("alice", new String(encoded, 0, 5, StandardCharsets.US_ASCII));
        assertEquals(0, encoded[5]);
    }

    @Test
    void successResponseRoundTrips() throws Exception {
        byte[] wire = Protocol.encodeSuccess("CERT-PEM-TEXT", "KEY-PEM-TEXT");

        Protocol.Response response = Protocol.readResponse(new ByteArrayInputStream(wire));

        assertTrue(response.isOk());
        assertEquals("CERT-PEM-TEXT", response.getCertPem());
        assertEquals("KEY-PEM-TEXT", response.getKeyPem());
        assertNull(response.getErrorMessage());
    }

    @Test
    void errorResponseRoundTrips() throws Exception {
        byte[] wire = Protocol.encodeError("name too long");

        Protocol.Response response = Protocol.readResponse(new ByteArrayInputStream(wire));

        assertFalse(response.isOk());
        assertEquals("name too long", response.getErrorMessage());
        assertNull(response.getCertPem());
        assertNull(response.getKeyPem());
    }
}
