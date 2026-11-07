package com.harmonia.ui.theme;

import java.awt.Font;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

/**
 * Harmonia look and feel: FlatLaf dark theme with the Harmonia palette.
 * <p>
 * FlatLaf automatically loads {@code HarmoniaLaf.properties} (same package, same name as this
 * class) on top of the FlatDarkLaf defaults: colours and shapes are tuned there, not in code.
 */
public class HarmoniaLaf extends FlatDarkLaf {

    public static final String NAME = "Harmonia";

    /** Installs the Harmonia look and feel. Call once, before creating any Swing component. */
    public static boolean setup() {
        return FlatLaf.setup(new HarmoniaLaf());
    }

    @Override
    public String getName() {
        return NAME;
    }

    /** Monospaced font used for values, SysEx dumps and the LCD. */
    public static Font monoFont(int style, float size) {
        return new Font(Font.MONOSPACED, style, 1).deriveFont(size);
    }
}
