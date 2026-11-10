package com.harmonia.wavestation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A Wavestation performance: up to 8 parts, each playing a patch, plus the effects.
 * Thin typed layer over the raw {@code performance} structure ({@link Layouts#PERFORMANCE}).
 */
public final class Performance {

    private final Struct struct;

    /** Performance decoded from the 181 raw bytes. */
    public Performance(byte[] raw) {
        this(new Struct(Layouts.PERFORMANCE, raw, 0));
    }

    public Performance(Struct struct) {
        if (struct.layout() != Layouts.PERFORMANCE) {
            throw new IllegalArgumentException("not a performance structure");
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
        return struct.getString("Perf_Name");
    }

    public void setName(String name) {
        struct.setString("Perf_Name", name);
    }

    /** Part 1-8. */
    public Part part(int number) {
        if (number < 1 || number > Layouts.PARTS) {
            throw new IllegalArgumentException("part must be 1-8: " + number);
        }
        return new Part(struct.view("P" + number), number);
    }

    /** All 8 parts, used or not. */
    public List<Part> getParts() {
        List<Part> parts = new ArrayList<>(Layouts.PARTS);
        for (int i = 1; i <= Layouts.PARTS; i++) {
            parts.add(part(i));
        }
        return parts;
    }

    /** Parts that play a patch. */
    public List<Part> getUsedParts() {
        return getParts().stream().filter(Part::isUsed).toList();
    }

    /** Effects: the two effects, their placement and mix settings. */
    public PerformanceEffects getEffects() {
        return new PerformanceEffects(getEffectsBlock());
    }

    /** Raw effects block (21 bytes). */
    public byte[] getEffectsBlock() {
        byte[] fx = new byte[21];
        for (int i = 0; i < fx.length; i++) {
            fx[i] = (byte) struct.get("Fx_Perf_Block[" + i + "]");
        }
        return fx;
    }

    @Override
    public String toString() {
        return getName() + " (" + getUsedParts().size() + " part" + (getUsedParts().size() > 1 ? "s" : "") + ")";
    }
}
