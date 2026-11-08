package com.harmonia.wavestation.model;

import java.util.List;

/**
 * A Wavestation patch: 1, 2 or 4 waves (A-D), each a complete voice, mixed by the vector
 * mix envelope.
 * <p>
 * Thin typed layer over the raw {@code patch} structure ({@link Layouts#PATCH}): every value can
 * still be read with {@link #struct()} under its official field name.
 */
public final class Patch {

    /** Wave letters, in the order of the structure. */
    public static final List<String> WAVES = List.of("A", "B", "C", "D");
    /** Offset of the mix envelope coordinates: stored 0-254, shown -127..+127. */
    static final int MIX_CENTER = 127;

    private final Struct struct;

    /** Patch decoded from the 426 raw bytes. */
    public Patch(byte[] raw) {
        this(new Struct(Layouts.PATCH, raw, 0));
    }

    public Patch(Struct struct) {
        if (struct.layout() != Layouts.PATCH) {
            throw new IllegalArgumentException("not a patch structure");
        }
        this.struct = struct;
    }

    public Struct struct() {
        return struct;
    }

    public byte[] toBytes() {
        return struct.toBytes();
    }

    public String getName() {
        return struct.getString("Patch_Name");
    }

    public void setName(String name) {
        struct.setString("Patch_Name", name);
    }

    /**
     * Number of waves: 1, 2 (waves A and C) or 4. Stored as 0, 1 or 2 in Number_Of_Waves
     * (encoding used by Edisyn, to be confirmed with real dumps).
     */
    public int getWaveCount() {
        return switch (struct.get("Number_Of_Waves")) {
            case 0 -> 1;
            case 1 -> 2;
            default -> 4;
        };
    }

    /** Waves actually used: A; A and C; or A, B, C, D. */
    public List<PatchWave> getActiveWaves() {
        return switch (getWaveCount()) {
            case 1 -> List.of(wave("A"));
            case 2 -> List.of(wave("A"), wave("C"));
            default -> List.of(wave("A"), wave("B"), wave("C"), wave("D"));
        };
    }

    public boolean isHardSync() {
        return struct.get("Hard_Sync") != 0;
    }

    /** Wave A, B, C or D. */
    public PatchWave wave(String letter) {
        int index = WAVES.indexOf(letter);
        if (index < 0) {
            throw new IllegalArgumentException("wave must be A, B, C or D: " + letter);
        }
        return new PatchWave(this, index);
    }

    // ------------------------------------------------------------------ mix envelope

    /** AC coordinate of a mix envelope point (0-4), -127..+127. */
    public int getMixAc(int point) {
        return struct.get("Mix_X" + point) - MIX_CENTER;
    }

    /** BD coordinate of a mix envelope point (0-4), -127..+127. */
    public int getMixBd(int point) {
        return struct.get("Mix_Y" + point) - MIX_CENTER;
    }

    /** Mix envelope time 1-4 (0-99, greater = slower). */
    public int getMixTime(int time) {
        return struct.get("Mix_Rate" + time);
    }

    /** Loop setting of the mix envelope, as stored (Mix_Env_Loop). */
    public int getMixLoop() {
        return struct.get("Mix_Env_Loop");
    }

    public int getMixRepeats() {
        return struct.get("Mix_Repeats");
    }

    @Override
    public String toString() {
        return getName() + " (" + getWaveCount() + " wave" + (getWaveCount() > 1 ? "s" : "") + ")";
    }
}
