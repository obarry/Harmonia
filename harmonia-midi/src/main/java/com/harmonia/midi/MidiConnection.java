package com.harmonia.midi;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

/**
 * Connection to a MIDI device: one input and one output port, plus an optional keyboard input.
 * <ul>
 * <li>Everything received from the device goes to the message listeners; complete SysEx
 * messages (reassembled by a {@link SysexAssembler}) also go to the SysEx listeners.</li>
 * <li>Everything sent goes through a {@link MidiSendQueue}.</li>
 * <li>Notes and controllers played on the keyboard are forwarded to the device ("MIDI thru"),
 * optionally re-channelled to the device channel.</li>
 * </ul>
 * Listeners are called on the Java Sound threads: the UI must switch to the Swing thread.
 */
public class MidiConnection implements AutoCloseable {

    /** Direction of a message, for the MIDI monitor. */
    public enum Direction {
        /** Received from the device. */
        IN,
        /** Sent to the device (including the notes forwarded from the keyboard). */
        OUT
    }

    /** Receives every message, in both directions. */
    @FunctionalInterface
    public interface MessageListener {
        void message(Direction direction, byte[] message);
    }

    private final MidiDevice inputDevice;
    private final MidiDevice outputDevice;
    private final Transmitter transmitter;
    private final Receiver outputReceiver;
    private final MidiSendQueue queue;
    private final SysexAssembler assembler;
    private final List<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<byte[]>> sysexListeners = new CopyOnWriteArrayList<>();

    private MidiDevice keyboardDevice;
    private Transmitter keyboardTransmitter;
    private volatile int thruChannel = -1;   // -1: keep the keyboard channel

    /**
     * Opens the ports.
     *
     * @throws MidiUnavailableException if a port is missing or already used by another program
     */
    public MidiConnection(MidiPorts.Port input, MidiPorts.Port output) throws MidiUnavailableException {
        outputDevice = MidiSystem.getMidiDevice(output.info());
        outputDevice.open();
        try {
            outputReceiver = outputDevice.getReceiver();
            inputDevice = MidiSystem.getMidiDevice(input.info());
            inputDevice.open();
            transmitter = inputDevice.getTransmitter();
        } catch (MidiUnavailableException | RuntimeException e) {
            outputDevice.close();
            throw e;
        }
        assembler = new SysexAssembler(this::sysexReceived);
        transmitter.setReceiver(new ListeningReceiver(this::received));
        queue = new MidiSendQueue(bytes -> outputReceiver.send(MidiBytes.toMidiMessage(bytes), -1));
        queue.setOnSent(bytes -> fire(Direction.OUT, bytes));
    }

    public MidiSendQueue getQueue() {
        return queue;
    }

    public void addMessageListener(MessageListener l) {
        messageListeners.add(l);
    }

    public void removeMessageListener(MessageListener l) {
        messageListeners.remove(l);
    }

    /** Receives every complete SysEx coming from the device. */
    public void addSysexListener(Consumer<byte[]> l) {
        sysexListeners.add(l);
    }

    public void removeSysexListener(Consumer<byte[]> l) {
        sysexListeners.remove(l);
    }

    /**
     * Forwards a MIDI keyboard to the device (MIDI thru). Only channel messages are forwarded.
     *
     * @param keyboard   keyboard input port, or null to stop
     * @param channel    channel (0-15) to send on, or -1 to keep the keyboard channel
     */
    public synchronized void setKeyboard(MidiPorts.Port keyboard, int channel) throws MidiUnavailableException {
        closeKeyboard();
        thruChannel = channel;
        if (keyboard == null) {
            return;
        }
        keyboardDevice = MidiSystem.getMidiDevice(keyboard.info());
        keyboardDevice.open();
        keyboardTransmitter = keyboardDevice.getTransmitter();
        keyboardTransmitter.setReceiver(new ListeningReceiver(this::keyboardReceived));
    }

    /** Re-channels a channel message (status low nibble) if a thru channel is set. */
    static byte[] rechannel(byte[] message, int channel) {
        if (channel < 0 || !MidiBytes.isChannelMessage(message)) {
            return message;
        }
        byte[] copy = message.clone();
        copy[0] = (byte) ((copy[0] & 0xF0) | (channel & 0x0F));
        return copy;
    }

    private void keyboardReceived(byte[] message) {
        if (MidiBytes.isChannelMessage(message)) {
            queue.sendNow(rechannel(message, thruChannel));
        }
    }

    private void received(byte[] message) {
        int status = message[0] & 0xFF;
        if (status == 0xFE || status == 0xF8) {
            return; // active sensing and clock: too frequent to be useful in the monitor
        }
        if (status == MidiBytes.SYSEX_START || status == MidiBytes.SYSEX_END) {
            assembler.accept(message);
        } else {
            fire(Direction.IN, message);
        }
    }

    private void sysexReceived(byte[] sysex) {
        fire(Direction.IN, sysex);
        for (Consumer<byte[]> l : sysexListeners) {
            l.accept(sysex);
        }
    }

    private void fire(Direction direction, byte[] message) {
        for (MessageListener l : messageListeners) {
            l.message(direction, message);
        }
    }

    private void closeKeyboard() {
        if (keyboardTransmitter != null) {
            keyboardTransmitter.close();
            keyboardTransmitter = null;
        }
        if (keyboardDevice != null) {
            keyboardDevice.close();
            keyboardDevice = null;
        }
    }

    @Override
    public synchronized void close() {
        queue.close();
        closeKeyboard();
        transmitter.close();
        inputDevice.close();
        outputReceiver.close();
        outputDevice.close();
    }

    /** Adapts a Java Sound receiver to a byte consumer. */
    private record ListeningReceiver(Consumer<byte[]> target) implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            target.accept(message.getMessage());
        }

        @Override
        public void close() {
        }
    }
}
