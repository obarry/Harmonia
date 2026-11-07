package com.harmonia.ui.editor;

import javax.swing.JComponent;

/** A view shown in the central area for one section (Patch, Performance...). */
public interface EditorView {

    /** The Swing component to display. */
    JComponent component();

    /**
     * Called when a Wavestation page is selected (page chips or PAGE+/PAGE-): the view highlights
     * the matching area. Default: nothing to highlight.
     */
    default void showPage(String page) {
    }
}
