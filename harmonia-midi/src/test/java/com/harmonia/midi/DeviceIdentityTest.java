package com.harmonia.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeviceIdentityTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    @Test
    void requestToAllDevices() {
        assertArrayEquals(bytes(0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7), DeviceIdentity.request());
    }

    @Test
    void parsesAWavestationSrReply() {
        // Reference Guide, appendix 4, 3.2: Korg, family 28 00, member 02 00, version
        byte[] reply = bytes(0xF0, 0x7E, 0x03, 0x06, 0x02, 0x42, 0x28, 0x00, 0x02, 0x00, 0x05, 0x00, 0x01, 0x00, 0xF7);
        DeviceIdentity id = DeviceIdentity.parse(reply).orElseThrow();
        assertEquals(3, id.channel());
        assertEquals(0x42, id.manufacturer());
        assertEquals(0x28, id.family());
        assertEquals(0x02, id.member());
        assertEquals("1.05", id.versionText());
    }

    @Test
    void parsesAThreeByteManufacturerId() {
        byte[] reply = bytes(0xF0, 0x7E, 0x00, 0x06, 0x02, 0x00, 0x20, 0x33, 0x01, 0x00, 0x02, 0x00, 0, 0, 0, 0, 0xF7);
        DeviceIdentity id = DeviceIdentity.parse(reply).orElseThrow();
        assertEquals(0x2033, id.manufacturer());
        assertEquals(1, id.family());
    }

    @Test
    void otherMessagesAreNotReplies() {
        assertTrue(DeviceIdentity.parse(bytes(0xF0, 0x42, 0x30, 0x28, 0x23, 0xF7)).isEmpty());
    }
}
