package com.harmonia.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class MidiSendQueueTest {

    private static byte[] sysex(int id) {
        return new byte[] { (byte) 0xF0, 0x42, 0x30, 0x28, 0x41, (byte) id, 0x00, (byte) 0xF7 };
    }

    private static byte[] note(int n) {
        return new byte[] { (byte) 0x90, (byte) n, 100 };
    }

    @Test
    void messagesAreSentInOrderWithAPauseAfterSysex() throws Exception {
        List<Long> times = Collections.synchronizedList(new ArrayList<>());
        List<Integer> ids = Collections.synchronizedList(new ArrayList<>());
        try (MidiSendQueue queue = new MidiSendQueue(m -> {
            times.add(System.nanoTime());
            ids.add((int) m[5]);
        }, 30)) {
            queue.send(sysex(1));
            queue.send(sysex(2));
            queue.send(sysex(3));
            assertTrue(queue.awaitEmpty(2000));
        }
        assertEquals(List.of(1, 2, 3), ids);
        for (int i = 1; i < times.size(); i++) {
            long gapMs = TimeUnit.NANOSECONDS.toMillis(times.get(i) - times.get(i - 1));
            assertTrue(gapMs >= 25, "gap " + gapMs + " ms");
        }
    }

    @Test
    void pendingValuesOfTheSameParameterAreMerged() throws Exception {
        CountDownLatch firstSent = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> ids = Collections.synchronizedList(new ArrayList<>());
        try (MidiSendQueue queue = new MidiSendQueue(m -> {
            ids.add((int) m[5]);
            firstSent.countDown();
            release.await(2, TimeUnit.SECONDS); // keep the worker busy
        }, 0)) {
            queue.send(sysex(1));
            assertTrue(firstSent.await(2, TimeUnit.SECONDS));
            queue.sendCoalesced("cutoff", sysex(10));
            queue.sendCoalesced("level", sysex(20));
            queue.sendCoalesced("cutoff", sysex(11));
            queue.sendCoalesced("cutoff", sysex(12));
            assertEquals(2, queue.pendingCount());
            release.countDown();
            assertTrue(queue.awaitEmpty(2000));
        }
        assertEquals(List.of(1, 12, 20), ids); // the cutoff keeps its place in the queue
    }

    @Test
    void notesGoBeforeWaitingSysex() throws Exception {
        CountDownLatch firstSent = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        try (MidiSendQueue queue = new MidiSendQueue(m -> {
            statuses.add(m[0] & 0xFF);
            firstSent.countDown();
            release.await(2, TimeUnit.SECONDS);
        }, 0)) {
            queue.send(sysex(1));
            assertTrue(firstSent.await(2, TimeUnit.SECONDS));
            queue.send(sysex(2));
            queue.sendNow(note(60));
            release.countDown();
            assertTrue(queue.awaitEmpty(2000));
        }
        assertEquals(List.of(0xF0, 0x90, 0xF0), statuses);
    }

    @Test
    void notesAreNotDelayedByThePauseAfterASysex() throws Exception {
        List<Long> times = Collections.synchronizedList(new ArrayList<>());
        long start = System.nanoTime();
        try (MidiSendQueue queue = new MidiSendQueue(m -> times.add(System.nanoTime() - start), 500)) {
            queue.send(sysex(1));
            Thread.sleep(50);
            queue.sendNow(note(60));
            Thread.sleep(100);
            assertEquals(2, times.size());
        }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(times.get(1)) < 300);
    }

    @Test
    void sentMessagesAreReported() throws Exception {
        List<byte[]> sent = Collections.synchronizedList(new ArrayList<>());
        try (MidiSendQueue queue = new MidiSendQueue(m -> { }, 0)) {
            queue.setOnSent(sent::add);
            queue.send(note(64));
            assertTrue(queue.awaitEmpty(1000));
        }
        assertEquals(1, sent.size());
    }
}
