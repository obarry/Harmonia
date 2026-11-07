package com.harmonia.midi;

import javax.sound.midi.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Proof of concept (2026-01), kept for the former {@code WaveStationEditor} window only.
 *
 * @deprecated replaced by MidiPorts and MidiConnection (phase 2); removed once the new UI is connected to the device.
 */
@Deprecated(forRemoval = true)
public class MidiManager {
    private MidiDevice inputDevice;
    private MidiDevice outputDevice;
    private Transmitter transmitter;
    /**
     * Reference to the last Receiver passed to openInput.
     *
     * NOTE: currently this field is not used elsewhere in the class. It is
     * kept to preserve the original intent (holding a reference to the
     * receiver) and for potential future use; leaving it prevents changing
     * external behavior. If you want to remove it, also remove the
     * assignment in openInput().
     */
    private Receiver receiver;

    public static String[] listDevices() {
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        List<String> names = new ArrayList<>();
        for (MidiDevice.Info info : infos) {
            names.add(info.getName() + " - " + info.getDescription());
        }
        return names.toArray(new String[0]);
    }

    public static MidiDevice.Info[] getDeviceInfos() {
        return MidiSystem.getMidiDeviceInfo();
    }

    public void openInput(MidiDevice.Info info, Receiver rx) throws MidiUnavailableException {
        closeInput();
        inputDevice = MidiSystem.getMidiDevice(info);
        if (!inputDevice.isOpen()) inputDevice.open();
        transmitter = inputDevice.getTransmitter();
        transmitter.setReceiver(rx);
        receiver = rx;
    }

    public void openOutput(MidiDevice.Info info, Receiver dummy) throws MidiUnavailableException {
        closeOutput();
        outputDevice = MidiSystem.getMidiDevice(info);
        if (!outputDevice.isOpen()) outputDevice.open();
        // We'll obtain Receiver from device when needed
    }

    public Receiver getOutputReceiver(MidiDevice.Info info) throws MidiUnavailableException {
        if (outputDevice == null || !outputDevice.isOpen() || outputDevice.getDeviceInfo() != info) {
            // open it
            if (outputDevice != null && outputDevice.isOpen()) outputDevice.close();
            outputDevice = MidiSystem.getMidiDevice(info);
            outputDevice.open();
        }
        return outputDevice.getReceiver();
    }

    public void closeInput() {
        try {
            if (transmitter != null) transmitter.setReceiver(null);
        } catch (Exception ignored) {}
        if (inputDevice != null && inputDevice.isOpen()) inputDevice.close();
        transmitter = null;
        inputDevice = null;
    }

    public void closeOutput() {
        if (outputDevice != null && outputDevice.isOpen()) outputDevice.close();
        outputDevice = null;
    }

    public void closeAll() {
        closeInput();
        closeOutput();
    }
}
