package com.harmonia.wavestation.sysex;

import java.util.Optional;

/**
 * Memory banks of the Wavestation SR.
 * <p>
 * The display order (RAM 1-3, ROM 4-11, CARD) is not the SysEx numbering: bank numbers 0-11 in
 * SysEx messages follow the history of the Wavestation family (RAM1, RAM2, ROM11 = original
 * keyboard sounds, CARD, then the SR banks). The same pairs appear in the Bank Select values
 * (Reference Guide, appendices 2 and 3). Order deduced from Edisyn; to be confirmed on the
 * device.
 */
public enum WavestationBank {

    RAM1("RAM1", 0, true),
    RAM2("RAM2", 1, true),
    RAM3("RAM3", 4, true),
    ROM4("ROM4", 5, false),
    ROM5("ROM5", 6, false),
    ROM6("ROM6", 7, false),
    ROM7("ROM7", 8, false),
    ROM8("ROM8", 9, false),
    ROM9("ROM9", 10, false),
    ROM10("ROM10", 11, false),
    ROM11("ROM11", 2, false),
    CARD("CARD", 3, true);

    /** Performances per bank. */
    public static final int PERFORMANCES = 50;
    /** Patches per bank. */
    public static final int PATCHES = 35;
    /** Wave sequences per bank. */
    public static final int WAVE_SEQUENCES = 32;

    private final String label;
    private final int sysexNumber;
    private final boolean writable;

    WavestationBank(String label, int sysexNumber, boolean writable) {
        this.label = label;
        this.sysexNumber = sysexNumber;
        this.writable = writable;
    }

    /** Name shown to the user, e.g. "RAM1". */
    public String label() {
        return label;
    }

    /** Number used in SysEx messages (0-11). */
    public int sysexNumber() {
        return sysexNumber;
    }

    /** RAM banks and the card can be written; ROM banks are factory sounds. */
    public boolean isWritable() {
        return writable;
    }

    /** Short form used on the device LCD: "1" for RAM1, "11" for ROM11, "C" for the card. */
    public String shortLabel() {
        return this == CARD ? "C" : label.replaceAll("\\D", "");
    }

    public static Optional<WavestationBank> fromSysex(int number) {
        for (WavestationBank b : values()) {
            if (b.sysexNumber == number) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public static Optional<WavestationBank> fromLabel(String label) {
        for (WavestationBank b : values()) {
            if (b.label.equalsIgnoreCase(label)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }
}
