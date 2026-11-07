package com.harmonia.ui;

import javax.swing.SwingUtilities;

import com.harmonia.ui.shell.MainWindow;
import com.harmonia.ui.theme.HarmoniaLaf;

/** Entry point of Harmonia. */
public final class HarmoniaApp {

    private HarmoniaApp() {
    }

    public static void main(String[] args) {
        HarmoniaLaf.setup();
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
