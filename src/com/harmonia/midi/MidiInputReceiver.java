package com.harmonia.midi;

import javax.sound.midi.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

public class MidiInputReceiver implements Receiver {
    private final List<RawSysexListener> sysexListeners = new ArrayList<>();
    private final MessageListener uiListener;
    

    public interface MessageListener {
        void onMidiMessage(MidiMessage msg, long timeStamp);
    }

    public interface RawSysexListener {
        void onSysexReceived(byte[] data);
    }

    public MidiInputReceiver(MessageListener uiListener) {
        this.uiListener = uiListener;
    }

    public void addSysexListener(RawSysexListener l) {
        sysexListeners.add(l);
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        // forward to UI in EDT
        SwingUtilities.invokeLater(() -> uiListener.onMidiMessage(message, timeStamp));

        // If SysEx, notify raw listeners and optionally save
        if (message instanceof SysexMessage) {
            SysexMessage sx = (SysexMessage) message;
            byte[] data = sx.getData(); // does NOT include F0/F7
            // wrap with F0/F7 if you want full
            byte[] full = new byte[data.length + 2];
            full[0] = (byte)0xF0;
            System.arraycopy(data, 0, full, 1, data.length);
            full[full.length - 1] = (byte)0xF7;

            for (RawSysexListener l : sysexListeners) {
                l.onSysexReceived(full);
            }
        }
    }

    @Override
    public void close() { }
}
