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

    RAM1("RAM1", 0, true, 0, 0),
    RAM2("RAM2", 1, true, 0, 50),
    RAM3("RAM3", 4, true, 2, 0),
    ROM4("ROM4", 5, false, 2, 50),
    ROM5("ROM5", 6, false, 3, 0),
    ROM6("ROM6", 7, false, 3, 50),
    ROM7("ROM7", 8, false, 4, 0),
    ROM8("ROM8", 9, false, 4, 50),
    ROM9("ROM9", 10, false, 5, 0),
    ROM10("ROM10", 11, false, 5, 50),
    ROM11("ROM11", 2, false, 1, 0),
    CARD("CARD", 3, true, 1, 50);

    /** Performances per bank. */
    public static final int PERFORMANCES = 50;
    /** Patches per bank. */
    public static final int PATCHES = 35;
    /** Wave sequences per bank. */
    public static final int WAVE_SEQUENCES = 32;

    private final String label;
    private final int sysexNumber;
    private final boolean writable;
    private final int midiBank;
    private final int programOffset;

    WavestationBank(String label, int sysexNumber, boolean writable, int midiBank, int programOffset) {
        this.label = label;
        this.sysexNumber = sysexNumber;
        this.writable = writable;
        this.midiBank = midiBank;
        this.programOffset = programOffset;
    }

    /**
     * MIDI bank (Bank Select LSB, controller 32) of the performances of this bank. The 12 banks are
     * grouped by two in 6 MIDI banks of 100 programs: program changes 0-49 select the first bank of
     * the pair, 50-99 the second (Player's Guide, 4.8; Reference Guide, appendix 2).
     */
    public int midiBank() {
        return midiBank;
    }

    /** Program change of performance 0 of this bank: 0 or 50. */
    public int programOffset() {
        return programOffset;
    }

    /** Bank of a performance selected by Bank Select (controller 32) and Program Change. */
    public static Optional<WavestationBank> fromMidi(int midiBank, int program) {
        int offset = program >= PERFORMANCES ? PERFORMANCES : 0;
        for (WavestationBank b : values()) {
            if (b.midiBank == midiBank && b.programOffset == offset) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
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
