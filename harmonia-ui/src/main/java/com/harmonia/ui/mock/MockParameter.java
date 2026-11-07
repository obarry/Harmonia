package com.harmonia.ui.mock;

/**
 * Minimal description of a Wavestation parameter, enough for the UI shell and the inspector.
 * <p>
 * Numbers and official names come from the Reference Guide (appendix 4, parameter number
 * table). Replaced by the real parameter catalogue in phase 3.
 *
 * @param label        short label shown under the knob
 * @param officialName name in the Korg parameter table
 * @param number       SysEx parameter number
 * @param page         Wavestation SR page where the parameter lives
 * @param min          minimum value
 * @param max          maximum value
 * @param defaultValue default value
 */
public record MockParameter(String label, String officialName, int number, String page, int min, int max,
        int defaultValue) {

    // Wave A (levels and tuning are per wave: B, C, D use the following numbers)
    public static final MockParameter WAVE_A_COARSE = new MockParameter("Coarse", "WAVEA_TUNE_COARSE", 151, "WAVES", -24, 24, 0);
    public static final MockParameter WAVE_A_FINE = new MockParameter("Fine", "WAVEA_TUNE_FINE", 152, "WAVES", -99, 99, 0);
    public static final MockParameter WAVE_A_LEVEL = new MockParameter("Level", "WAVEA_LEVEL", 150, "WAVES", 0, 99, 99);

    // Filter
    public static final MockParameter CUTOFF = new MockParameter("Cutoff", "FILTER_MOD_CUTOFF", 93, "FILTER", 0, 99, 99);
    public static final MockParameter TRACKING = new MockParameter("Kbd track", "FILTER_MOD_TRACKING", 94, "FILTER", -99, 99, 0);
    public static final MockParameter EXCITER = new MockParameter("Exciter", "FILTER_EXCITER_AMOUNT", 95, "FILTER", 0, 99, 0);
    public static final MockParameter FILTER_SRC1_AMT = new MockParameter("Src1 amt", "FILTER_MOD_SOURCE1_AMT", 97, "FILTER", -127, 127, 0);

    // Amp envelope
    public static final MockParameter AMP_RATE_1 = new MockParameter("Rate 1", "AMP_ENV_RATE_1", 114, "AMP ENV", 0, 99, 99);
    public static final MockParameter AMP_RATE_2 = new MockParameter("Rate 2", "AMP_ENV_RATE_2", 115, "AMP ENV", 0, 99, 60);
    public static final MockParameter AMP_RATE_3 = new MockParameter("Rate 3", "AMP_ENV_RATE_3", 116, "AMP ENV", 0, 99, 50);
    public static final MockParameter AMP_RATE_4 = new MockParameter("Rate 4", "AMP_ENV_RATE_4", 117, "AMP ENV", 0, 99, 45);
    public static final MockParameter AMP_LEVEL_2 = new MockParameter("Level 2", "AMP_ENV_LEVEL_2", 112, "AMP ENV", 0, 99, 80);
    public static final MockParameter AMP_LEVEL_3 = new MockParameter("Level 3", "AMP_ENV_LEVEL_3", 113, "AMP ENV", 0, 99, 70);

    // Mix envelope
    public static final MockParameter MIX_RATE_1 = new MockParameter("Rate 1", "MIX_ENV_RATE_1", 421, "MIX ENV", 0, 99, 24);
    public static final MockParameter MIX_RATE_2 = new MockParameter("Rate 2", "MIX_ENV_RATE_2", 422, "MIX ENV", 0, 99, 48);
    public static final MockParameter MIX_RATE_3 = new MockParameter("Rate 3", "MIX_ENV_RATE_3", 423, "MIX ENV", 0, 99, 35);
    public static final MockParameter MIX_RATE_4 = new MockParameter("Rate 4", "MIX_ENV_RATE_4", 424, "MIX ENV", 0, 99, 63);

    // LFO 1
    public static final MockParameter LFO1_RATE = new MockParameter("Rate", "LFO1_RATE", 125, "LFO1", 0, 99, 40);
    public static final MockParameter LFO1_AMOUNT = new MockParameter("Amount", "LFO1_INITIAL_AMOUNT", 126, "LFO1", 0, 99, 0);
    public static final MockParameter LFO1_DELAY = new MockParameter("Delay", "LFO1_DELAY", 129, "LFO1", 0, 99, 0);
    public static final MockParameter LFO1_FADE = new MockParameter("Fade in", "LFO1_FADE_IN", 130, "LFO1", 0, 99, 0);

    /** Range as text, e.g. "-99 … +99". */
    public String rangeText() {
        return signed(min) + " … " + signed(max);
    }

    private String signed(int v) {
        return min < 0 && v > 0 ? "+" + v : Integer.toString(v);
    }
}
