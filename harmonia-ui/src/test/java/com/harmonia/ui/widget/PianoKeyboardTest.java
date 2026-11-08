package com.harmonia.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PianoKeyboardTest {

    private static byte[] msg(int status, int d1, int d2) {
        return new byte[] { (byte) status, (byte) d1, (byte) d2 };
    }

    @Test
    void notesFromTheMasterKeyboardLightTheKeys() {
        PianoKeyboard kb = new PianoKeyboard();
        assertTrue(kb.showMidi(msg(0x92, 60, 100)));   // note on, any channel
        assertTrue(kb.isNoteActive(60));
        kb.showMidi(msg(0x82, 60, 0));                 // note off
        assertFalse(kb.isNoteActive(60));
    }

    @Test
    void noteOnWithZeroVelocityIsANoteOff() {
        PianoKeyboard kb = new PianoKeyboard();
        kb.showMidi(msg(0x90, 64, 90));
        kb.showMidi(msg(0x90, 64, 0));
        assertFalse(kb.isNoteActive(64));
    }

    @Test
    void allNotesOffReleasesEverything() {
        PianoKeyboard kb = new PianoKeyboard();
        kb.showMidi(msg(0x90, 60, 90));
        kb.showMidi(msg(0x90, 67, 90));
        kb.showMidi(msg(0xB0, 123, 0));
        assertFalse(kb.isNoteActive(60));
        assertFalse(kb.isNoteActive(67));
    }

    @Test
    void otherMessagesAreIgnored() {
        PianoKeyboard kb = new PianoKeyboard();
        assertFalse(kb.showMidi(msg(0xB0, 1, 64)));    // mod wheel
        assertFalse(kb.showMidi(msg(0xE0, 0, 64)));    // pitch bend
    }

    @Test
    void noteNames() {
        assertEquals("C4", PianoKeyboard.noteName(60));
        assertEquals("A#2", PianoKeyboard.noteName(46));
    }
}
