package com.harmonia.wavestation.model;

import com.harmonia.wavestation.sysex.WavestationBank;

/**
 * One wave (oscillator) of a patch: the {@code indiv} structure A, B, C or D. Each wave is a
 * complete voice with its own filter, amp envelope, envelope 1 and two LFOs.
 */
public final class PatchWave {

    /** Waves 0-31 of a bank are its wave sequences; above, PCM waves (Edisyn convention). */
    public static final int WAVE_SEQUENCE_COUNT = 32;

    private final Patch patch;
    private final int index;
    private final Struct.View fields;

    PatchWave(Patch patch, int index) {
        this.patch = patch;
        this.index = index;
        this.fields = patch.struct().view(Patch.WAVES.get(index));
    }

    /** Letter A-D. */
    public String getLetter() {
        return Patch.WAVES.get(index);
    }

    /** Raw access to any field of the indiv structure, e.g. {@code get("Lfo1_Rate")}. */
    public int get(String field) {
        return fields.get(field);
    }

    public void set(String field, int value) {
        fields.set(field, value);
    }

    /** Wave number: Wave_Num plus Wave_Num_Exp (expansion PCM waves, 397 and above). */
    public int getWaveNumber() {
        return fields.get("Wave_Num") + fields.get("Wave_Num_Exp");
    }

    /** True if the wave plays a wave sequence rather than a single PCM wave. */
    public boolean isWaveSequence() {
        return fields.get("Wave_Num") < WAVE_SEQUENCE_COUNT;
    }

    /**
     * Bank of the wave sequence: Wave_Bank (0-3) expanded by the bits of Bank_Exp for this wave
     * (bit 0-3: first expansion for A-D, bit 4-7: second expansion).
     */
    public WavestationBank getBank() {
        int exp = patch.struct().get("Bank_Exp") & 0xFF;
        int expansion = (exp >> (4 + index) & 1) != 0 ? 2 : (exp >> index & 1) != 0 ? 1 : 0;
        return BankExpansion.decode(fields.get("Wave_Bank") & 0x03, expansion);
    }

    public int getCoarse() {
        return fields.get("Wave_Coarse");
    }

    public int getFine() {
        return fields.get("Wave_Fine");
    }

    /** Level of the wave (Indiv_Level, parameter WAVEx_LEVEL). */
    public int getLevel() {
        return fields.get("Indiv_Level");
    }

    public int getCutoff() {
        return fields.get("Cutoff");
    }

    public int getExciter() {
        return fields.get("Filter_Exciter");
    }

    /** Amp envelope level 0-3. */
    public int getAmpLevel(int i) {
        return fields.get("AEG_Level" + i);
    }

    /** Amp envelope time 1-4. */
    public int getAmpTime(int i) {
        return fields.get("AEG_Rate" + i);
    }

    /** Envelope 1 level 0-4. */
    public int getEnv1Level(int i) {
        return fields.get("EG_Level" + i);
    }

    /** Envelope 1 time 1-4. */
    public int getEnv1Time(int i) {
        return fields.get("EG_Rate" + i);
    }

    /** Short description, e.g. "WS 07 (RAM1)" or "PCM 245". */
    public String describeSource() {
        return isWaveSequence() ? String.format("WS %02d (%s)", fields.get("Wave_Num"),
                getBank() != null ? getBank().label() : "?") : "PCM " + (getWaveNumber() - WAVE_SEQUENCE_COUNT);
    }
}
