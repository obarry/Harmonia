package com.harmonia.midi;

import java.io.ByteArrayOutputStream;

/**
 * Proof of concept (2026-01), kept for the former {@code WaveStationEditor} window only.
 *
 * @deprecated replaced by SysexAssembler and SysexRequests (phase 2); removed once the new UI is connected to the device.
 */
@Deprecated(forRemoval = true)
public class SysexDumpCollector {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean collecting = false;

    public synchronized void start() {
        buffer.reset();
        collecting = true;
    }

    public synchronized void stop() {
        collecting = false;
    }

    public synchronized boolean isCollecting() {
        return collecting;
    }

    public synchronized void onSysexReceived(byte[] fullSysex) {
        if (!collecting) return;
        buffer.writeBytes(fullSysex);
    }

    public synchronized byte[] getFullDump() {
        return buffer.toByteArray();
    }

    public synchronized boolean isComplete() {
        byte[] data = buffer.toByteArray();
        return data.length > 0 &&
               data[0] == (byte)0xF0 &&
               data[data.length - 1] == (byte)0xF7;
    }
}
