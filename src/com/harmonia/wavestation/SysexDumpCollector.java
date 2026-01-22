package com.harmonia.wavestation;

import java.io.ByteArrayOutputStream;

public class SysexDumpCollector {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean collecting = false;

    public void start() {
        buffer.reset();
        collecting = true;
    }

    public void stop() {
        collecting = false;
    }

    public boolean isCollecting() {
        return collecting;
    }

    public void onSysexReceived(byte[] fullSysex) {
        if (!collecting) return;
        buffer.writeBytes(fullSysex);
    }

    public byte[] getFullDump() {
        return buffer.toByteArray();
    }

    public boolean isComplete() {
        byte[] data = buffer.toByteArray();
        return data.length > 0 &&
               data[0] == (byte)0xF0 &&
               data[data.length - 1] == (byte)0xF7;
    }
}
