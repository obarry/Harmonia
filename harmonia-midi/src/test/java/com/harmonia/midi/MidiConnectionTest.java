package com.harmonia.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** The connection itself needs real ports; only the pure parts are tested here. */
class MidiConnectionTest {

    @Test
    void thruMessagesAreRechannelled() {
        byte[] noteOnChannel1 = { (byte) 0x90, 60, 100 };
        assertArrayEquals(new byte[] { (byte) 0x93, 60, 100 }, MidiConnection.rechannel(noteOnChannel1, 3));
    }

    @Test
    void channelIsKeptWhenNoThruChannel() {
        byte[] cc = { (byte) 0xB2, 1, 64 };
        assertArrayEquals(cc, MidiConnection.rechannel(cc, -1));
    }
}
