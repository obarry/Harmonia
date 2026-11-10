package com.harmonia.wavestation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PerformanceEffectsTest {

    @Test
    void decodesTheHeader() {
        byte[] b = new byte[21];
        b[0] = (byte) (0x80 | 0x40 | (1 + 2));      // series, mod 3 negative, Small Hall
        b[1] = (byte) (21 + 2);                       // Stereo Chorus
        b[2] = (byte) (0xB << 4 | 0x1);              // mix 4 = 11, mix 3 = 1
        b[3] = (byte) (13 << 4 | 1);                 // mod 4 Joystick AC, mod 3 mod wheel
        b[4] = (byte) (5 << 4 | 9);
        PerformanceEffects fx = new PerformanceEffects(b);
        assertEquals("Small Hall", fx.effectName(1));
        assertEquals("Stereo Chorus", fx.effectName(2));
        assertTrue(fx.isSeries());
        assertEquals(1, fx.mix(3));
        assertEquals(11, fx.mix(4));
        assertEquals("Mod wheel", PerformanceEffects.SOURCES.get(fx.modSource(3)));
        assertEquals("Joystick AC", PerformanceEffects.SOURCES.get(fx.modSource(4)));
        assertEquals(-9, fx.modAmount(3));
        assertEquals(5, fx.modAmount(4));
        assertEquals("Small Hall  →  Stereo Chorus", fx.summary());
    }

    @Test
    void extendedEffects() {
        byte[] b = new byte[21];
        b[0] = 2;                     // "no effect" ...
        b[1] = (byte) (0x80 | 2);     // extended effects on
        b[5] = 1;                     // ... effect 1 is extended effect 1 = 48
        b[13] = 8;                    // effect 2 = 55
        PerformanceEffects fx = new PerformanceEffects(b);
        assertFalse(fx.isSeries());
        assertEquals(48, fx.effect(1));
        assertEquals("Mod Pitch Shifter-Delay", fx.effectName(1));
        assertEquals("Stereo Vocoder 2", fx.effectName(2));
        assertEquals(PerformanceEffects.EFFECTS.size() - 1, fx.effect(2));
    }

    @Test
    void emptyBlockMeansNoEffect() {
        PerformanceEffects fx = new PerformanceEffects(new byte[21]);
        assertEquals("No effect", fx.effectName(1));
        assertEquals("No effect", fx.effectName(2));
    }
}
