package com.harmonia.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class SysexAssemblerTest {

    private final List<byte[]> received = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong();
    private final SysexAssembler assembler = new SysexAssembler(received::add, clock::get, 1000);

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    @Test
    void completeMessageIsDeliveredAsIs() {
        byte[] m = bytes(0xF0, 0x42, 0x30, 0x28, 0x23, 0xF7);
        assembler.accept(m);
        assertEquals(1, received.size());
        assertArrayEquals(m, received.get(0));
    }

    @Test
    void piecesAreGluedTogether() {
        // Java Sound continuation messages start with status F7, which is not part of the data
        assembler.accept(bytes(0xF0, 0x42, 0x30));
        assembler.accept(bytes(0xF7, 0x28, 0x40, 0x00));
        assertTrue(assembler.isAssembling());
        assembler.accept(bytes(0xF7, 0x0C, 0x05, 0xF7));
        assertEquals(1, received.size());
        assertArrayEquals(bytes(0xF0, 0x42, 0x30, 0x28, 0x40, 0x00, 0x0C, 0x05, 0xF7), received.get(0));
        assertFalse(assembler.isAssembling());
    }

    @Test
    void lonelyEndByteEndsTheMessage() {
        assembler.accept(bytes(0xF0, 0x42, 0x30, 0x28, 0x23));
        assembler.accept(bytes(0xF7));
        assertArrayEquals(bytes(0xF0, 0x42, 0x30, 0x28, 0x23, 0xF7), received.get(0));
    }

    @Test
    void newStartDropsTheUnfinishedMessage() {
        assembler.accept(bytes(0xF0, 0x01, 0x02));
        assembler.accept(bytes(0xF0, 0x03, 0xF7));
        assertEquals(1, received.size());
        assertArrayEquals(bytes(0xF0, 0x03, 0xF7), received.get(0));
        assertEquals(1, assembler.getDroppedCount());
    }

    @Test
    void tooLongGapDropsTheUnfinishedMessage() {
        assembler.accept(bytes(0xF0, 0x01, 0x02));
        clock.addAndGet(1500);
        assembler.accept(bytes(0xF7, 0x03, 0xF7));
        assertEquals(0, received.size());
        assertEquals(1, assembler.getDroppedCount());
    }

    @Test
    void otherMessagesAreIgnored() {
        assembler.accept(bytes(0xF0, 0x01));
        assembler.accept(bytes(0x90, 60, 100));
        assembler.accept(bytes(0xFE));
        assembler.accept(bytes(0xF7, 0x02, 0xF7));
        assertArrayEquals(bytes(0xF0, 0x01, 0x02, 0xF7), received.get(0));
    }
}
