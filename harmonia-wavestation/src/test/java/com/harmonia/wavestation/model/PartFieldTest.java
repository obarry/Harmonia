package com.harmonia.wavestation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.harmonia.wavestation.sysex.WavestationBank;

class PartFieldTest {

    private final Performance perf = new Performance(new Struct(Layouts.PERFORMANCE));

    @Test
    void plainFieldsAreStoredAsIs() {
        Part p = perf.part(3);
        p.set(PartField.LEVEL, 87);
        p.set(PartField.TRANSPOSE, -12);
        p.set(PartField.DETUNE, 7);
        p.set(PartField.DELAY, 1500);
        p.set(PartField.LOW_KEY, 36);
        assertEquals(87, p.getLevel());
        assertEquals(-12, p.getTranspose());
        assertEquals(7, p.getDetune());
        assertEquals(1500, p.getDelay());
        assertEquals(36, p.get(PartField.LOW_KEY));
        Performance copy = new Performance(perf.toBytes());
        assertEquals(-12, copy.part(3).get(PartField.TRANSPOSE));
        assertEquals(1500, copy.part(3).get(PartField.DELAY));
    }

    @Test
    void valuesAreClampedToTheirRange() {
        Part p = perf.part(1);
        p.set(PartField.LEVEL, 150);
        p.set(PartField.TRANSPOSE, -40);
        assertEquals(99, p.get(PartField.LEVEL));
        assertEquals(-24, p.get(PartField.TRANSPOSE));
    }

    @Test
    void modesArePackedInPartModeWithoutTouchingTheBankExpansion() {
        Part p = perf.part(2);
        p.setPatch(WavestationBank.ROM7, 10);      // expansion 2 in bits 7-6
        p.set(PartField.VOICE_MODE, 3);
        p.set(PartField.PLAY_MODE, 2);
        p.set(PartField.NOTE_PRIORITY, 1);
        assertEquals(Part.VoiceMode.UNISON_LEGATO, p.getVoiceMode());
        assertEquals(Part.PlayMode.MIDI, p.getPlayMode());
        assertEquals(Part.NotePriority.HIGH, p.getNotePriority());
        assertEquals(WavestationBank.ROM7, p.getBank());
        assertEquals(0b10_10_11_01, p.get("Part_Mode"));
        p.set(PartField.VOICE_MODE, 1);
        assertEquals(Part.VoiceMode.POLY, p.getVoiceMode());
        assertEquals(Part.PlayMode.MIDI, p.getPlayMode());
    }

    @Test
    void formatting() {
        assertEquals("C4", PartField.LOW_KEY.format(60));
        assertEquals("C-1", PartField.LOW_KEY.format(0));
        assertEquals("G9", PartField.HIGH_KEY.format(127));
        assertEquals("+7", PartField.TRANSPOSE.format(7));
        assertEquals("A", PartField.OUTPUT.format(0));
        assertEquals("70/30", PartField.OUTPUT.format(30));
        assertEquals("C+D", PartField.OUTPUT.format(102));
        assertEquals("Uni legato", PartField.VOICE_MODE.format(3));
    }
}
