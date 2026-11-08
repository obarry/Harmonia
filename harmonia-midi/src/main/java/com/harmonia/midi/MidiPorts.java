package com.harmonia.midi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;

/**
 * Lists the MIDI ports of the computer.
 * <p>
 * Java Sound reports every device twice (once as input, once as output) and also its own
 * software synthesizer and sequencer. Only real ports are kept: inputs are devices that can
 * transmit to us, outputs devices that can receive.
 */
public final class MidiPorts {

    /** A MIDI port as shown to the user. */
    public record Port(String name, String description, MidiDevice.Info info) {
        @Override
        public String toString() {
            return name;
        }
    }

    private MidiPorts() {
    }

    /** Ports we can receive from (MIDI IN of the computer). */
    public static List<Port> inputs() {
        return list(true);
    }

    /** Ports we can send to (MIDI OUT of the computer). */
    public static List<Port> outputs() {
        return list(false);
    }

    public static Optional<Port> findInput(String name) {
        return inputs().stream().filter(p -> p.name().equals(name)).findFirst();
    }

    public static Optional<Port> findOutput(String name) {
        return outputs().stream().filter(p -> p.name().equals(name)).findFirst();
    }

    private static List<Port> list(boolean input) {
        List<Port> ports = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                if (device instanceof Synthesizer || device instanceof Sequencer) {
                    continue;
                }
                int max = input ? device.getMaxTransmitters() : device.getMaxReceivers();
                if (max != 0) {
                    ports.add(new Port(info.getName(), info.getDescription(), info));
                }
            } catch (MidiUnavailableException e) {
                // device disappeared meanwhile: ignore it
            }
        }
        return ports;
    }
}
