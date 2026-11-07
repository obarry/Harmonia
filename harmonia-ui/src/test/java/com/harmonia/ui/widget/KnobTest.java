package com.harmonia.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class KnobTest {

    @Test
    void valueIsClampedToRange() {
        Knob knob = new Knob("Cutoff", 0, 99, 50);
        knob.setValue(150);
        assertEquals(99, knob.getValue());
        knob.setValue(-3);
        assertEquals(0, knob.getValue());
    }

    @Test
    void defaultValueIsClampedAndUsedAsInitialValue() {
        Knob knob = new Knob("Level", 0, 99, 120);
        assertEquals(99, knob.getDefaultValue());
        assertEquals(99, knob.getValue());
    }

    @Test
    void bipolarKnobShowsSignedValues() {
        Knob knob = new Knob("Fine", -99, 99, 0);
        assertTrue(knob.isBipolar());
        knob.setValue(12);
        assertEquals("+12", knob.getValueText());
        knob.setValue(-4);
        assertEquals("-4", knob.getValueText());
    }

    @Test
    void unipolarKnobShowsPlainValues() {
        Knob knob = new Knob("Rate", 0, 99, 40);
        assertFalse(knob.isBipolar());
        assertEquals("40", knob.getValueText());
    }

    @Test
    void listenersAreNotifiedOnlyWhenValueChanges() {
        Knob knob = new Knob("Rate", 0, 99, 40);
        AtomicInteger calls = new AtomicInteger();
        knob.addChangeListener(e -> calls.incrementAndGet());
        knob.setValue(41);
        knob.setValue(41);
        knob.setValue(500);
        assertEquals(2, calls.get());
    }

    @Test
    void normalizedValueCoversRange() {
        Knob knob = new Knob("Pan", -50, 50, 0);
        assertEquals(0.5, knob.getNormalizedValue(), 1e-9);
        knob.setValue(50);
        assertEquals(1.0, knob.getNormalizedValue(), 1e-9);
    }

    @Test
    void invalidRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Knob("Bad", 10, 10, 10));
    }
}
