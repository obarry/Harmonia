package com.harmonia.ui;

import java.util.List;

/**
 * The main sections (tabs) of Harmonia, each with the Wavestation SR pages it covers.
 * <p>
 * Page names are those shown on the device screen (Player's Guide, figure 1-4 "Wavestation SR
 * levels"), so that a musician who knows the SR finds the same vocabulary in Harmonia.
 */
public enum EditorSection {

    LIBRARIAN("Librarian", "LIBRARIAN",
            List.of()),
    PERFORMANCE("Performance", "PERFORM",
            List.of("PERFORM", "EDIT PERF", "ZONES", "FX SELECT", "PART COPY")),
    PATCH("Patch", "PATCH",
            List.of("PATCH", "WAVES", "MACROS", "AMP ENV", "AMP MOD", "FILTER", "LFO1", "LFO2", "A-B PAN", "PITCH",
                    "ENV1", "ENV1MOD", "MIX ENV", "MIX MOD", "BUS ASN")),
    WAVE_SEQ("Wave Seq", "WSEQ",
            List.of("WSEQ", "WAVESEQ LOOP", "WAVESEQ MOD", "WS UTILITIES", "WAVESEQ COPY")),
    EFFECTS("Effects", "FX SELECT",
            List.of("FX SELECT", "FX1", "FX2", "FX MIX")),
    MULTISET("Multiset", "MULTISET",
            List.of("MULTISET", "EDTMULTI", "FX SELECT")),
    GLOBAL("MIDI / Global", "MIDI",
            List.of("MIDI", "GLOBAL", "MIDI STATUS", "MIDI RECEIVE", "MIDI REMAP", "PERFMAP", "SCALE", "BANK COPY",
                    "SYSEX DATA XMIT"));

    private final String title;
    private final String lcdName;
    private final List<String> pages;

    EditorSection(String title, String lcdName, List<String> pages) {
        this.title = title;
        this.lcdName = lcdName;
        this.pages = pages;
    }

    /** Tab title. */
    public String title() {
        return title;
    }

    /** Name shown on line 1 of the LCD when no specific page is selected. */
    public String lcdName() {
        return lcdName;
    }

    /** Wavestation SR pages of this section, in the device PAGE+ order. */
    public List<String> pages() {
        return pages;
    }
}
