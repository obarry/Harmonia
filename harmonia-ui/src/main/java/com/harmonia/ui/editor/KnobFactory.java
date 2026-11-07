package com.harmonia.ui.editor;

import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.function.Consumer;

import com.harmonia.ui.mock.MockParameter;
import com.harmonia.ui.widget.Knob;

/**
 * Creates knobs bound to a parameter and wired to the window (inspector, LCD, MIDI monitor).
 * <p>
 * In phase 6 the parameter will come from the real catalogue and the change will be sent to the
 * device; the wiring stays the same.
 */
public class KnobFactory {

    private final Consumer<Knob> onFocus;
    private final Consumer<Knob> onChange;

    /**
     * @param onFocus  called when a knob gets the focus (clicked or tabbed into)
     * @param onChange called when a knob value changes
     */
    public KnobFactory(Consumer<Knob> onFocus, Consumer<Knob> onChange) {
        this.onFocus = onFocus;
        this.onChange = onChange;
    }

    public Knob create(MockParameter p) {
        return create(p, null);
    }

    public Knob create(MockParameter p, Color accent) {
        Knob k = new Knob(p.label(), p.min(), p.max(), p.defaultValue());
        k.putClientProperty(MockParameter.class, p);
        if (accent != null) {
            k.setAccent(accent);
        }
        k.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                onFocus.accept(k);
            }
        });
        k.addChangeListener(e -> onChange.accept(k));
        return k;
    }
}
