package com.harmonia.wavestation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.harmonia.wavestation.sysex.MessageType;

/** The transcribed structures must give the dump sizes of the manual. */
class LayoutsTest {

    private static int dumpLength(MessageType type, int dataBytes) {
        return type.dataOffset() + 2 * dataBytes + 2;
    }

    @Test
    void structureSizes() {
        assertEquals(84, Layouts.WAVE.size());
        assertEquals(426, Layouts.PATCH.size());
        assertEquals(18, Layouts.PART.size());
        assertEquals(181, Layouts.PERFORMANCE.size());
        assertEquals(16, Layouts.WAVE_SEQUENCE.size());
        assertEquals(16, Layouts.WAVE_STEP.size());
        assertEquals(8784, WaveSequenceBank.SIZE);
    }

    @Test
    void dumpSizesMatchTheManual() {
        assertEquals(861, dumpLength(MessageType.PATCH_DUMP, Layouts.PATCH.size()));
        assertEquals(371, dumpLength(MessageType.PERFORMANCE_DUMP, Layouts.PERFORMANCE.size()));
        assertEquals(29828, dumpLength(MessageType.PATCH_BANK_DUMP, 35 * Layouts.PATCH.size()));
        assertEquals(18108, dumpLength(MessageType.PERFORMANCE_BANK_DUMP, 50 * Layouts.PERFORMANCE.size()));
        assertEquals(17576, dumpLength(MessageType.WAVE_SEQUENCE_BANK_DUMP, WaveSequenceBank.SIZE));
    }

    @Test
    void fieldOffsets() {
        assertEquals(0, Layouts.PATCH.field("Patch_Name").offset());
        assertEquals(90, Layouts.PATCH.field("A.Wave_Coarse").offset());
        assertEquals(90 + 84, Layouts.PATCH.field("B.Wave_Coarse").offset());
        assertEquals(37, Layouts.PERFORMANCE.field("P1.Bank_Num").offset());
        assertEquals(37 + 7 * 18, Layouts.PERFORMANCE.field("P8.Bank_Num").offset());
    }
}
