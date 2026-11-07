package com.harmonia.ui.mock;

import java.util.ArrayList;
import java.util.List;

/**
 * Temporary data used by the UI shell until the real Wavestation model exists (phase 3).
 * <p>
 * Performance names of RAM 1 are the factory ones (Performance Notes); everything else is
 * invented. This class disappears when the UI is connected to the model (phase 6).
 */
public final class MockData {

    private MockData() {
    }

    /** Banks in display order (RAM 1-3, ROM 4-11, CARD). */
    public static final List<String> BANKS = List.of("RAM1", "RAM2", "RAM3", "ROM4", "ROM5", "ROM6", "ROM7", "ROM8",
            "ROM9", "ROM10", "ROM11", "CARD");

    private static final List<String> RAM1_PERFORMANCES = List.of("Ski Jam", "Entropy", "Pinger", "Reswacker",
            "Lead Rock Guitar", "Softwaves", "Cascade Falls", "Blow the Bottle", "Magic Guitar", "Will I Dream?",
            "Fire Dance", "Analog Love Thang", "Panned Waves", "Super Res", "Batlerina Bells", "Soft Analog",
            "Mod Wheel Air", "Bowed Strings", "Pluckrimba", "Vector Guitar", "Midnight Run", "African Sunset",
            "Harmonic Motion", "Air Chorus & Bell", "SunGlasses Kid", "Stabby Horns", "Soft EP w/Tine",
            "Artificial Strg", "The Pied Piper", "Vox Concrete", "Snake Charmer", "Rock Tine Piano", "Pressure Glass",
            "Vox Arpeggios", "Struck Bell", "Upright & Oboe", "Refinery", "Kick up da Bass", "Syn Vox",
            "Kingdom Come", "Cat's Eye", "Jazz Mutes", "VS Bell Pad", "Spectra", "New Sparkle", "Vektor Organ",
            "Alien Dreams", "End of Voltaire", "Kilimanjaro", "Debussy On Wheels");

    private static final List<String> PATCHES = List.of("Init Patch", "Spectral Bell", "Glass Choir",
            "Digital Sweep", "Ghostly Pad", "Hollow Wind", "Metal Pluck", "Vox Vector", "Night Strings",
            "Spinning Bells", "Deep Space", "Pulse Tines", "Airy Keys", "Wavelead", "Organ Swirl", "Chimes 2",
            "Breath Pad", "Soft Brass", "Wire Harp", "Moon Bells", "Clav Wave", "Rez Sweep", "Choir Vector",
            "Analog Bass", "Kalimba", "Sync Lead", "Air Vox", "Bell Tree", "Mallet Pad", "String Ens",
            "Blown Glass", "Thick Pick", "Sample Hold", "Flute Air", "Tine Piano");

    public static List<String> performances(String bank) {
        if ("RAM1".equals(bank)) {
            return RAM1_PERFORMANCES;
        }
        return numbered(bank + " Performance", 50);
    }

    public static List<String> patches(String bank) {
        if ("RAM1".equals(bank)) {
            return PATCHES;
        }
        return numbered(bank + " Patch", 35);
    }

    public static List<String> waveSequences(String bank) {
        return numbered(bank + " WaveSeq", 32);
    }

    private static List<String> numbered(String prefix, int count) {
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(prefix + " " + i);
        }
        return names;
    }
}
