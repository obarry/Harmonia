package com.harmonia.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnvelopeEditorTest {

    @Test
    void ampEnvelopeHasFourLevelsAndEnv1Five() {
        assertEquals(4, new EnvelopeEditor(EnvelopeEditor.Type.AMP).getLevelCount());
        assertEquals(5, new EnvelopeEditor(EnvelopeEditor.Type.ENV1).getLevelCount());
    }

    @Test
    void valuesAreClampedTo0_99() {
        EnvelopeEditor editor = new EnvelopeEditor(EnvelopeEditor.Type.ENV1);
        editor.setLevel(4, 150);
        editor.setTime(0, -5);
        assertEquals(99, editor.getLevel(4));
        assertEquals(0, editor.getTime(0));
    }

    @Test
    void longerTimeGivesALongerSegment() {
        EnvelopeEditor editor = new EnvelopeEditor(EnvelopeEditor.Type.AMP);
        editor.setSize(600, 160);
        editor.setTime(0, 10);
        int shortAttack = editor.breakpoints()[1].x;
        editor.setTime(0, 90);
        int longAttack = editor.breakpoints()[1].x;
        assertEquals(true, longAttack > shortAttack);
    }
}
