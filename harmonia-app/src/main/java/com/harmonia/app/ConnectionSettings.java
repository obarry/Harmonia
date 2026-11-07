package com.harmonia.app;

import java.util.prefs.Preferences;

/**
 * MIDI connection settings, remembered between sessions (Java preferences: the Windows registry
 * on Windows, a file in the home folder elsewhere).
 *
 * @param input    name of the MIDI IN port connected to the device MIDI OUT
 * @param output   name of the MIDI OUT port connected to the device MIDI IN
 * @param keyboard name of the MIDI IN port of the master keyboard, or empty for none
 * @param channel  MIDI basic channel of the device, 0-15 (shown as 1-16)
 */
public record ConnectionSettings(String input, String output, String keyboard, int channel) {

    private static final String NODE = "/com/harmonia/connection";

    public ConnectionSettings {
        input = input == null ? "" : input;
        output = output == null ? "" : output;
        keyboard = keyboard == null ? "" : keyboard;
        if (channel < 0 || channel > 15) {
            throw new IllegalArgumentException("channel must be 0-15: " + channel);
        }
    }

    public boolean hasKeyboard() {
        return !keyboard.isEmpty();
    }

    /** True if both device ports are set. */
    public boolean isComplete() {
        return !input.isEmpty() && !output.isEmpty();
    }

    public static ConnectionSettings load() {
        Preferences p = Preferences.userRoot().node(NODE);
        return new ConnectionSettings(p.get("input", ""), p.get("output", ""), p.get("keyboard", ""),
                Math.max(0, Math.min(15, p.getInt("channel", 0))));
    }

    public void save() {
        Preferences p = Preferences.userRoot().node(NODE);
        p.put("input", input);
        p.put("output", output);
        p.put("keyboard", keyboard);
        p.putInt("channel", channel);
    }
}
