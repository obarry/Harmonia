package com.harmonia.wavestation.model;

import java.util.List;

/**
 * Header of the effects block of a performance (the first bytes of Fx_Perf_Block): the two
 * effects, their placement and the mix 3 / mix 4 settings. The parameters of each effect, packed
 * in the rest of the block, are decoded later.
 * <p>
 * Layout (Reference Guide appendix 4, completed with Edisyn's findings, to be confirmed on the
 * device):
 * <ul>
 * <li>byte 0: bit 7 series (1) / parallel (0), bit 6 sign of mod 3 amount, bits 5-0 effect 1 + 2</li>
 * <li>byte 1: bit 7 extended effects, bit 6 sign of mod 4 amount, bits 5-0 effect 2 + 2</li>
 * <li>byte 2: mix 3 (low nibble), mix 4 (high nibble)</li>
 * <li>byte 3: mod 3 source (low nibble), mod 4 source (high nibble)</li>
 * <li>byte 4: mod 3 amount (low nibble), mod 4 amount (high nibble), magnitudes</li>
 * <li>bytes 5 and 13 (low nibble): extended effect of effect 1 / 2 (47 + n) when its type is 0</li>
 * </ul>
 */
public final class PerformanceEffects {

    /** Effect names, in the order of the effect numbers (0 = no effect). */
    public static final List<String> EFFECTS = List.of("No effect", "Small Hall", "Medium Hall", "Large Hall",
            "Small Room", "Large Room", "Live Stage", "Wet Plate", "Dry Plate", "Spring Reverb",
            "Early Reflections 1", "Early Reflections 2", "Early Reflections 3", "Gated Reverb", "Reverse Gate",
            "Stereo Delay", "Ping-Pong Delay", "Dual Mono Delay", "Multi-Tap Delay 1", "Multi-Tap Delay 2",
            "Multi-Tap Delay 3", "Stereo Chorus", "Quadrature Chorus", "Crossover Chorus", "Harmonic Chorus",
            "Stereo Flanger 1", "Stereo Flanger 2", "Crossover Flanger", "Enhancer/Exciter", "Distortion-Filter",
            "Overdrive-Filter", "Stereo Phaser 1", "Stereo Phaser 2", "Rotary Speaker", "Stereo Mod-Pan",
            "Quadrature Mod-Pan", "Stereo Parametric EQ", "Chorus-Stereo Delay", "Flanger-Stereo Delay",
            "Dual Mono Delay/Hall", "Dual Mono Delay/Room", "Dual Mono Delay/Chorus", "Dual Mono Delay/Flanger",
            "Dual Mono Delay/Distortion", "Dual Mono Delay/Overdrive", "Dual Mono Delay/Phaser",
            "Dual Mono Delay/Rotary", "Stereo Pitch Shifter", "Mod Pitch Shifter-Delay",
            "Stereo Compressor-Limiter/Gate", "Small Vocoder 1", "Small Vocoder 2", "Small Vocoder 3",
            "Small Vocoder 4", "Stereo Vocoder 1", "Stereo Vocoder 2");

    /** Modulation sources of mix 3 and mix 4. */
    public static final List<String> SOURCES = List.of("None", "Mod wheel", "Aftertouch", "Last velocity",
            "High velocity", "Amp envelopes", "Key down gate", "FX switch", "FX toggle", "MIDI mod pedal", "MIDI 1",
            "MIDI 2", "AT + mod wheel", "Joystick AC", "Joystick BD");

    private final byte[] block;

    public PerformanceEffects(byte[] block) {
        if (block.length < 14) {
            throw new IllegalArgumentException("effects block too short: " + block.length);
        }
        this.block = block.clone();
    }

    private int u(int i) {
        return block[i] & 0xFF;
    }

    /** Effect number (index in {@link #EFFECTS}) of effect 1 or 2. */
    public int effect(int fx) {
        if (fx != 1 && fx != 2) {
            throw new IllegalArgumentException("effect must be 1 or 2: " + fx);
        }
        int type = Math.max(0, (u(fx - 1) & 0x3F) - 2);
        int extended = u(fx == 1 ? 5 : 13) & 0x0F;
        if (isExtended() && type == 0 && extended > 0) {
            type = 47 + extended;
        }
        return type < EFFECTS.size() ? type : 0;
    }

    public String effectName(int fx) {
        return EFFECTS.get(effect(fx));
    }

    /** True for series (effect 1 into effect 2), false for parallel. */
    public boolean isSeries() {
        return (u(0) & 0x80) != 0;
    }

    /** True if the extended effects (47 and above) are used. */
    public boolean isExtended() {
        return (u(1) & 0x80) != 0;
    }

    /** Mix 3 or 4 level, 0-11 (0 off, 1 dry or left, ..., 11 wet or right). */
    public int mix(int mix) {
        return mix == 3 ? u(2) & 0x0F : u(2) >> 4;
    }

    /** Modulation source of mix 3 or 4 (index in {@link #SOURCES}). */
    public int modSource(int mix) {
        return mix == 3 ? u(3) & 0x0F : u(3) >> 4;
    }

    /** Modulation amount of mix 3 or 4, signed (-15..15). */
    public int modAmount(int mix) {
        int magnitude = mix == 3 ? u(4) & 0x0F : u(4) >> 4;
        boolean negative = (u(mix == 3 ? 0 : 1) & 0x40) != 0;
        return negative ? -magnitude : magnitude;
    }

    /** "Small Hall → Stereo Chorus" (series) or "Small Hall ‖ Stereo Chorus" (parallel). */
    public String summary() {
        return effectName(1) + (isSeries() ? "  →  " : "  ‖  ") + effectName(2);
    }
}
