package com.harmonia.wavestation.model;

import com.harmonia.wavestation.param.Parameter;

/**
 * The editable settings of a part (EDIT PERF pages of the device): where each one is stored in
 * the {@code part} structure, the parameter that changes it in real time, and its range.
 * <p>
 * The value is the same in the structure and in the parameter change message, except for the
 * modes packed in Part_Mode. Ranges come from the Reference Guide (EDIT PERF pages).
 */
public enum PartField {

    LEVEL("Niveau", "Level", Parameter.PART_VOLUME, 0, 99),
    /** Effect bus: 0 = A, 1-99 = A/B mix, 100-105 = B, C, C+D, D, All, Patch. */
    OUTPUT("Sortie FX", "Output", Parameter.PART_OUTPUT, 0, 105),
    LOW_KEY("Note basse", "Lo_Key", Parameter.PART_KEY_LIMIT_LOW, 0, 127),
    HIGH_KEY("Note haute", "Hi_Key", Parameter.PART_KEY_LIMIT_HIGH, 0, 127),
    LOW_VELOCITY("Vélocité basse", "Lo_Vel", Parameter.PART_VEL_LIMIT_LOW, 1, 127),
    HIGH_VELOCITY("Vélocité haute", "Hi_Vel", Parameter.PART_VEL_LIMIT_HIGH, 1, 127),
    TRANSPOSE("Transposition", "Trans", Parameter.PART_TRANSPOSE, -24, 24),
    DETUNE("Désaccord", "Detune", Parameter.PART_DETUNE, -49, 49),
    /** Note-on delay, in milliseconds. */
    DELAY("Retard", "Delay", Parameter.PART_DELAY, 0, 9999),
    SUSTAIN("Sustain", "Sus_Enable", Parameter.PART_SUS_ENABLE, 0, 1),
    SCALE("Gamme", "Tunetab", Parameter.PART_MTUNE_TAB, 0, 15),
    /** {@link Part.VoiceMode}: 1 poly, 2 unison retrigger, 3 unison legato (Part_Mode bits 3-2). */
    VOICE_MODE("Mode de voix", "Part_Mode", Parameter.PART_MODE, 1, 3, 2),
    /** {@link Part.PlayMode}: 1 local, 2 MIDI, 3 both (Part_Mode bits 5-4). */
    PLAY_MODE("Jeu", "Part_Mode", Parameter.PART_PLAY_MODE, 1, 3, 4),
    /** {@link Part.NotePriority}: 0 low, 1 high, 2 last (Part_Mode bits 1-0). */
    NOTE_PRIORITY("Priorité", "Part_Mode", Parameter.PART_UNI_NOTE_PRIORITY, 0, 2, 0);

    private final String label;
    private final String field;
    private final Parameter parameter;
    private final int min;
    private final int max;
    /** Bit offset of a 2-bit value packed in Part_Mode, -1 for a whole field. */
    private final int shift;

    PartField(String label, String field, Parameter parameter, int min, int max) {
        this(label, field, parameter, min, max, -1);
    }

    PartField(String label, String field, Parameter parameter, int min, int max, int shift) {
        this.label = label;
        this.field = field;
        this.parameter = parameter;
        this.min = min;
        this.max = max;
        this.shift = shift;
    }

    /** Label shown in the interface. */
    public String label() {
        return label;
    }

    /** Field of the part structure. */
    public String field() {
        return field;
    }

    /** Parameter that changes it in real time (scope PART: needs CURRENT_PART). */
    public Parameter parameter() {
        return parameter;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    int get(Struct.View fields) {
        int raw = fields.get(field);
        return shift < 0 ? raw : (raw >> shift) & 0x03;
    }

    void set(Struct.View fields, int value) {
        int v = clamp(value);
        if (shift < 0) {
            fields.set(field, v);
        } else {
            int raw = fields.get(field) & ~(0x03 << shift);
            fields.set(field, raw | v << shift);
        }
    }

    /** Text shown for a value: notes, effect bus, modes... */
    public String format(int value) {
        return switch (this) {
            case LOW_KEY, HIGH_KEY -> noteName(value);
            case TRANSPOSE, DETUNE -> value > 0 ? "+" + value : Integer.toString(value);
            case DELAY -> value + " ms";
            case SUSTAIN -> value != 0 ? "On" : "Off";
            case OUTPUT -> busName(value);
            case SCALE -> value == 0 ? "Equal 1" : value == 1 ? "Equal 2" : value == 2 ? "Pure major"
                    : value == 3 ? "Pure minor" : "User " + (value - 3);
            case VOICE_MODE -> switch (value) {
                case 1 -> "Poly";
                case 2 -> "Uni retrig";
                case 3 -> "Uni legato";
                default -> "?";
            };
            case PLAY_MODE -> switch (value) {
                case 1 -> "Local";
                case 2 -> "MIDI";
                case 3 -> "Local+MIDI";
                default -> "?";
            };
            case NOTE_PRIORITY -> switch (value) {
                case 0 -> "Basse";
                case 1 -> "Haute";
                case 2 -> "Dernière";
                default -> "?";
            };
            default -> Integer.toString(value);
        };
    }

    /** Effect bus of a part: "A", "70/30" (A/B), "B", "C", "C+D", "D", "All", "Patch". */
    public static String busName(int value) {
        if (value == 0) {
            return "A";
        }
        if (value >= 1 && value <= 99) {
            return (100 - value) + "/" + value;
        }
        String[] high = { "B", "C", "C+D", "D", "All", "Patch" };
        return value >= 100 && value <= 105 ? high[value - 100] : "?";
    }

    /** Note name, middle C (60) = C4. */
    public static String noteName(int note) {
        String[] names = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
        return names[Math.floorMod(note, 12)] + (Math.floorDiv(note, 12) - 1);
    }
}
