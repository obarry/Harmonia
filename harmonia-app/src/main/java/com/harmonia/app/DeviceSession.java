package com.harmonia.app;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.midi.MidiUnavailableException;

import com.harmonia.midi.MidiConnection;
import com.harmonia.midi.MidiPorts;
import com.harmonia.midi.SysexRequests;
import com.harmonia.wavestation.device.WavestationDevice;

/**
 * The link between Harmonia and the Wavestation SR: opens the MIDI connection with the saved
 * settings, creates the {@link WavestationDevice} and forwards what happens to the listeners
 * (the UI).
 * <p>
 * Listeners are called on MIDI threads; the UI must switch to the Swing thread.
 */
public class DeviceSession implements AutoCloseable {

    /** Receives the session events. */
    public interface Listener {
        /** Connection opened or closed; {@code description} is a short text for the status bar. */
        void connectionChanged(boolean connected, String description);

        /** A message was sent to or received from the device. */
        void message(MidiConnection.Direction direction, byte[] message);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private MidiConnection connection;
    private WavestationDevice device;
    private ConnectionSettings settings = ConnectionSettings.load();

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public ConnectionSettings getSettings() {
        return settings;
    }

    public synchronized boolean isConnected() {
        return connection != null;
    }

    /** The device, when connected. */
    public synchronized Optional<WavestationDevice> device() {
        return Optional.ofNullable(device);
    }

    /** The connection, when connected (to send notes from the on-screen keyboard...). */
    public synchronized Optional<MidiConnection> connection() {
        return Optional.ofNullable(connection);
    }

    /**
     * Opens the connection with the given settings (closing the current one) and saves them.
     *
     * @throws MidiUnavailableException if a port does not exist or is busy
     */
    public synchronized void connect(ConnectionSettings newSettings) throws MidiUnavailableException {
        disconnect();
        MidiPorts.Port in = MidiPorts.findInput(newSettings.input())
                .orElseThrow(() -> new MidiUnavailableException("MIDI IN port not found: " + newSettings.input()));
        MidiPorts.Port out = MidiPorts.findOutput(newSettings.output())
                .orElseThrow(() -> new MidiUnavailableException("MIDI OUT port not found: " + newSettings.output()));
        MidiConnection c = new MidiConnection(in, out);
        try {
            if (newSettings.hasKeyboard()) {
                MidiPorts.Port kbd = MidiPorts.findInput(newSettings.keyboard()).orElseThrow(
                        () -> new MidiUnavailableException("keyboard port not found: " + newSettings.keyboard()));
                c.setKeyboard(kbd, newSettings.channel());
            }
        } catch (MidiUnavailableException | RuntimeException e) {
            c.close();
            throw e;
        }
        SysexRequests requests = new SysexRequests();
        WavestationDevice d = new WavestationDevice(c.getQueue(), requests, newSettings.channel());
        c.addSysexListener(d::onSysex);
        c.addMessageListener((direction, message) -> listeners.forEach(l -> l.message(direction, message)));
        connection = c;
        device = d;
        settings = newSettings;
        settings.save();
        fireConnection(true, describe(newSettings));
    }

    /** Reconnects with the saved settings if they are complete and the ports exist. */
    public boolean connectWithSavedSettings() {
        if (!settings.isComplete()) {
            return false;
        }
        try {
            connect(settings);
            return true;
        } catch (MidiUnavailableException | RuntimeException e) {
            return false;
        }
    }

    public synchronized void disconnect() {
        if (connection != null) {
            connection.close();
            connection = null;
            device = null;
            fireConnection(false, "Not connected");
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    private void fireConnection(boolean connected, String description) {
        listeners.forEach(l -> l.connectionChanged(connected, description));
    }

    private static String describe(ConnectionSettings s) {
        return "Wavestation SR (channel " + (s.channel() + 1) + ") · IN: " + s.input() + "  OUT: " + s.output()
                + (s.hasKeyboard() ? " · keyboard: " + s.keyboard() : "");
    }
}
