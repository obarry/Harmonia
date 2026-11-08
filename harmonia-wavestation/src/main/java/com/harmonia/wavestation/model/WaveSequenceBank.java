package com.harmonia.wavestation.model;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * The 32 wave sequences of a bank ({@code ws_block}, appendix 4, 5.6).
 * <p>
 * The steps of all sequences share a pool of 501 steps; each sequence points to its first step
 * and the steps form a doubly linked list (WS_Flink / WS_Blink). The editing of sequences comes
 * with phase 8; this class reads the names and follows the links.
 */
public final class WaveSequenceBank {

    /** Size of the raw block: 32 headers, 501 steps, 32 names. */
    public static final int SIZE = Layouts.WAVE_SEQUENCES * Layouts.WAVE_SEQUENCE.size()
            + Layouts.WAVE_STEPS * Layouts.WAVE_STEP.size()
            + Layouts.WAVE_SEQUENCES * Layouts.WAVE_SEQUENCE_NAME_SIZE;

    /** Value of a link that points nowhere. */
    public static final int NO_LINK = 0xFFFF;
    /** A sequence has at most 255 steps. */
    public static final int MAX_STEPS = 255;

    private final List<Struct> sequences = new ArrayList<>();
    private final List<Struct> steps = new ArrayList<>();
    private final List<Struct> names = new ArrayList<>();
    private static final Layout NAME = Layout.builder("ws_name").chars("Wave_Seq_Name",
            Layouts.WAVE_SEQUENCE_NAME_SIZE).build();

    /** Bank decoded from the raw block (8784 bytes). */
    public WaveSequenceBank(byte[] raw) {
        if (raw.length != SIZE) {
            throw new IllegalArgumentException("wave sequence block must be " + SIZE + " bytes: " + raw.length);
        }
        int pos = 0;
        for (int i = 0; i < Layouts.WAVE_SEQUENCES; i++, pos += Layouts.WAVE_SEQUENCE.size()) {
            sequences.add(new Struct(Layouts.WAVE_SEQUENCE, raw, pos));
        }
        for (int i = 0; i < Layouts.WAVE_STEPS; i++, pos += Layouts.WAVE_STEP.size()) {
            steps.add(new Struct(Layouts.WAVE_STEP, raw, pos));
        }
        for (int i = 0; i < Layouts.WAVE_SEQUENCES; i++, pos += NAME.size()) {
            names.add(new Struct(NAME, raw, pos));
        }
    }

    public byte[] toBytes() {
        byte[] raw = new byte[SIZE];
        int pos = 0;
        for (List<Struct> list : List.of(sequences, steps, names)) {
            for (Struct s : list) {
                s.writeTo(raw, pos);
                pos += s.layout().size();
            }
        }
        return raw;
    }

    public String getName(int sequence) {
        return names.get(sequence).getString("Wave_Seq_Name");
    }

    public List<String> getNames() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < Layouts.WAVE_SEQUENCES; i++) {
            list.add(getName(i));
        }
        return list;
    }

    /** Header of a sequence (loop, start step, modulation...). */
    public Struct header(int sequence) {
        return sequences.get(sequence);
    }

    /**
     * Steps of a sequence, following the forward links from WS_Link. Stops at an invalid link,
     * an already visited step or after 255 steps (link conventions to be confirmed on real data).
     */
    public List<Struct> steps(int sequence) {
        List<Struct> result = new ArrayList<>();
        BitSet visited = new BitSet(Layouts.WAVE_STEPS);
        int link = sequences.get(sequence).get("WS_Link");
        while (link >= 0 && link < Layouts.WAVE_STEPS && !visited.get(link) && result.size() < MAX_STEPS) {
            visited.set(link);
            Struct step = steps.get(link);
            result.add(step);
            if (step.get("WS_Llink") != NO_LINK) {
                break; // only the last step holds the loop pointer
            }
            link = step.get("WS_Flink");
        }
        return result;
    }
}
