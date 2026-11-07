package com.harmonia.ui.theme;

import java.awt.Color;

/**
 * Harmonia colour palette (see docs/DESIGN_ROADMAP.md, "Charte").
 * <p>
 * The FlatLaf theme ({@code HarmoniaLaf.properties}) uses the same values for standard Swing
 * components; this class is for custom-painted widgets (knobs, LEDs, LCD, front panel).
 */
public final class HarmoniaColors {

    private HarmoniaColors() {
    }

    // Surfaces
    public static final Color BACKGROUND = new Color(0x0E0F11);
    public static final Color PANEL = new Color(0x18191C);
    public static final Color PANEL_RAISED = new Color(0x202227);
    public static final Color PANEL_SUNKEN = new Color(0x0B0C0E);
    public static final Color BORDER = new Color(0x2C2F35);
    public static final Color CONTROL = new Color(0x2A2C31);

    // Text
    public static final Color TEXT = new Color(0xD8DADE);
    public static final Color TEXT_DIM = new Color(0x8B8F97);
    public static final Color TEXT_FAINT = new Color(0x5D616A);

    // Accents
    /** Turquoise accent, reminiscent of the SR backlit LCD. */
    public static final Color ACCENT = new Color(0x2EC4B6);
    public static final Color ACCENT_DARK = new Color(0x1F3B38);
    /** Orange: write actions and "hot" values. */
    public static final Color WRITE = new Color(0xFF8A3D);

    // LEDs
    public static final Color LED_GREEN = new Color(0x38E07B);
    public static final Color LED_AMBER = new Color(0xFFB020);
    public static final Color LED_RED = new Color(0xFF5A3D);
    public static final Color LED_BLUE = new Color(0x5D9CFF);

    // LCD
    public static final Color LCD_TOP = new Color(0x0F3B3A);
    public static final Color LCD_BOTTOM = new Color(0x0A2726);
    public static final Color LCD_TEXT = new Color(0x7FF5E6);
    public static final Color LCD_TEXT_DIM = new Color(0x5FD6C8);

    // Rack front panel
    public static final Color RACK_TOP = new Color(0x26272B);
    public static final Color RACK_BOTTOM = new Color(0x141518);
    public static final Color RACK_EAR = new Color(0x1D1E22);
}
