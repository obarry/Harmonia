package com.harmonia.wavestation.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StructTest {

    private static final Layout LAYOUT = Layout.builder("test")
            .chars("Name", 6).bytes("S").ubytes("U").words("W").uwords("UW").longs("L").build();

    @Test
    void valuesAreBigEndianAndSignedAccordingToTheType() {
        byte[] raw = { 'P', 'a', 'd', ' ', 0, 0, (byte) 0xFE, (byte) 0xFE, (byte) 0xFF, (byte) 0x9C, (byte) 0x12,
                (byte) 0x34, 0x00, 0x00, 0x01, 0x00 };
        Struct s = new Struct(LAYOUT, raw, 0);
        assertEquals("Pad", s.getString("Name"));
        assertEquals(-2, s.get("S"));
        assertEquals(254, s.get("U"));
        assertEquals(-100, s.get("W"));
        assertEquals(0x1234, s.get("UW"));
        assertEquals(256, s.get("L"));
        assertArrayEquals(raw, s.toBytes());
    }

    @Test
    void setWritesTheBytes() {
        Struct s = new Struct(LAYOUT);
        s.set("S", -5);
        s.set("UW", 500);
        s.setString("Name", "Bells");
        assertEquals(-5, s.get("S"));
        assertEquals(500, s.get("UW"));
        assertEquals("Bells", s.getString("Name"));
        assertEquals(' ', s.toBytes()[5]); // padded with spaces
    }
}
