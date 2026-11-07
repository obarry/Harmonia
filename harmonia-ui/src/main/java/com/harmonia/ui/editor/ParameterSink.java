package com.harmonia.ui.editor;

import com.harmonia.ui.mock.MockParameter;

/**
 * Receives parameter changes made with graphical editors that are not knobs (the vector pad,
 * for instance): the window shows them on the LCD and in the MIDI monitor, and later sends them.
 */
@FunctionalInterface
public interface ParameterSink {

    /**
     * @param parameter the changed parameter
     * @param value     new value as sent to the device (ASCII text of the parameter change message)
     * @param lcdText   text for line 2 of the LCD, e.g. "AC:+100 BD:-56"
     */
    void parameterChanged(MockParameter parameter, String value, String lcdText);
}
