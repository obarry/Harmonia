package com.harmonia.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

@SuppressWarnings("removal")
class SysexDumpCollectorTest {

    private static final byte[] SYSEX = { (byte) 0xF0, 0x42, 0x30, 0x28, 0x23, (byte) 0xF7 };

    @Test
    void ignoresDataWhenNotCollecting() {
        SysexDumpCollector collector = new SysexDumpCollector();
        collector.onSysexReceived(SYSEX);
        assertEquals(0, collector.getFullDump().length);
        assertFalse(collector.isComplete());
    }

    @Test
    void collectsCompleteSysexMessage() {
        SysexDumpCollector collector = new SysexDumpCollector();
        collector.start();
        collector.onSysexReceived(SYSEX);
        assertTrue(collector.isCollecting());
        assertTrue(collector.isComplete());
        assertArrayEquals(SYSEX, collector.getFullDump());
    }

    @Test
    void startResetsPreviousData() {
        SysexDumpCollector collector = new SysexDumpCollector();
        collector.start();
        collector.onSysexReceived(SYSEX);
        collector.start();
        assertEquals(0, collector.getFullDump().length);
    }

    @Test
    void stopEndsCollection() {
        SysexDumpCollector collector = new SysexDumpCollector();
        collector.start();
        collector.stop();
        collector.onSysexReceived(SYSEX);
        assertFalse(collector.isCollecting());
        assertEquals(0, collector.getFullDump().length);
    }
}
