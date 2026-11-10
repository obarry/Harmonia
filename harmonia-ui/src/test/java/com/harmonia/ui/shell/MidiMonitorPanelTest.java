package com.harmonia.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.harmonia.ui.shell.MidiMonitorPanel.Direction;

class MidiMonitorPanelTest {

    @Test
    void streamsWithTheSameKeyUseASingleLine() {
        MidiMonitorPanel monitor = new MidiMonitorPanel();
        monitor.post(Direction.OUT, "90 3C 64", "Note on C4", null);
        for (int i = 0; i < 100; i++) {
            monitor.post(Direction.OUT, "D0 " + i, "Aftertouch", "OUT:at:208");
        }
        monitor.flush();
        assertEquals(2, monitor.lineCount());
    }

    @Test
    void theListIsCapped() {
        MidiMonitorPanel monitor = new MidiMonitorPanel();
        for (int i = 0; i < 1000; i++) {
            monitor.post(Direction.OUT, "90 3C 64", "Note on C4", null);
        }
        monitor.flush();
        assertEquals(300, monitor.lineCount());
    }

    @Test
    void activityIsReportedOncePerBatch() {
        MidiMonitorPanel monitor = new MidiMonitorPanel();
        AtomicInteger flashes = new AtomicInteger();
        monitor.setOnActivity(flashes::incrementAndGet);
        for (int i = 0; i < 50; i++) {
            monitor.post(Direction.IN, "F8", "", null);
        }
        monitor.flush();
        monitor.flush(); // nothing pending: nothing done
        assertEquals(1, flashes.get());
    }

    @Test
    void manyMessagesAreHandledQuickly() {
        MidiMonitorPanel monitor = new MidiMonitorPanel();
        long start = System.nanoTime();
        for (int batch = 0; batch < 100; batch++) {
            for (int i = 0; i < 100; i++) {
                monitor.post(Direction.OUT, "90 3C 64", "Note on C4", null);
                monitor.post(Direction.OUT, "D0 40", "Aftertouch", "OUT:at:208");
            }
            monitor.flush();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 2000, "20 000 messages took " + ms + " ms");
    }
}
