package com.harmonia.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sound.midi.InvalidMidiDataException;

import org.junit.jupiter.api.Test;

class MidiBytesTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    @Test
    void hexDump() {
        assertEquals("F0 42 30 28 10 00 0C F7", MidiBytes.toHex(bytes(0xF0, 0x42, 0x30, 0x28, 0x10, 0x00, 0x0C, 0xF7)));
    }

    @Test
    void longHexDumpIsShortened() {
        assertEquals("F0 01 02 … 7E F7", MidiBytes.toHex(bytes(0xF0, 1, 2, 3, 4, 5, 0x7E, 0xF7), 5));
    }

    @Test
    void recognisesMessageKinds() {
        assertTrue(MidiBytes.isCompleteSysex(bytes(0xF0, 0x7E, 0xF7)));
        assertFalse(MidiBytes.isCompleteSysex(bytes(0xF0, 0x7E)));
        assertTrue(MidiBytes.isChannelMessage(bytes(0x93, 60, 100)));
        assertFalse(MidiBytes.isChannelMessage(bytes(0xF8)));
    }

    @Test
    void transferTimeAtMidiSpeed() {
        assertEquals(1000, MidiBytes.transferMillis(3125));
        assertEquals(9545, MidiBytes.transferMillis(29828)); // a Wavestation patch bank
    }

    @Test
    void roundTripThroughJavaSound() throws InvalidMidiDataException {
        byte[] note = bytes(0x90, 60, 100);
        assertArrayEquals(note, MidiBytes.toMidiMessage(note).getMessage());
        byte[] sysex = bytes(0xF0, 0x42, 0x30, 0x28, 0x23, 0xF7);
        assertArrayEquals(sysex, MidiBytes.toMidiMessage(sysex).getMessage());
    }
}
