package com.harmonia.wavestation.sysex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Expected bytes: Reference Guide, appendix 4. */
class WavestationSysexTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    @Test
    void patchRequest() {
        assertArrayEquals(bytes(0xF0, 0x42, 0x30, 0x28, 0x10, 0x00, 0x0C, 0xF7),
                WavestationSysex.patchRequest(0, WavestationBank.RAM1, 12));
    }

    @Test
    void channelGoesInTheFormatByte() {
        assertArrayEquals(bytes(0xF0, 0x42, 0x3A, 0x28, 0x1D, 0x05, 0xF7),
                WavestationSysex.performanceBankRequest(10, WavestationBank.ROM4));
    }

    @Test
    void writeCommands() {
        assertArrayEquals(bytes(0xF0, 0x42, 0x30, 0x28, 0x11, 0x04, 0x22, 0xF7),
                WavestationSysex.patchWrite(0, WavestationBank.RAM3, 34));
        assertThrows(IllegalArgumentException.class, () -> WavestationSysex.patchWrite(0, WavestationBank.RAM1, 35));
        assertArrayEquals(bytes(0xF0, 0x42, 0x30, 0x28, 0x1A, 0x01, 0x31, 0xF7),
                WavestationSysex.performanceWrite(0, WavestationBank.RAM2, 49));
    }

    @Test
    void parameterChangeWithAsciiValue() {
        // Filter cutoff (#93) = 72
        assertArrayEquals(bytes(0xF0, 0x42, 0x30, 0x28, 0x41, 0x5D, 0x00, '7', '2', 0x00, 0xF7),
                WavestationSysex.parameterChange(0, 93, "72"));
    }

    @Test
    void parameterChangeFunctionDependsOnTheNumber() {
        assertEquals(0x42, WavestationSysex.parameterChange(0, 380, "+1")[4]);
        assertEquals(0x43, WavestationSysex.parameterChange(0, 421, "5")[4]);
        byte[] m = WavestationSysex.parameterChange(0, 421, "5");
        assertEquals(0x25, m[5]); // 421 = 3 * 128 + 37
        assertEquals(0x03, m[6]);
    }

    @Test
    void spacesAreSentAs7F() {
        byte[] m = WavestationSysex.parameterChange(0, 3, "A B");
        assertEquals(0x7F, m[8]);
        assertEquals("Parameter change #3 = A B", WavestationSysex.describe(m));
    }

    @Test
    void signedValues() {
        assertEquals("+12", WavestationSysex.signed(12));
        assertEquals("-4", WavestationSysex.signed(-4));
        assertEquals("0", WavestationSysex.signed(0));
    }

    @Test
    void recognisesDumpsAndTheirHeader() {
        byte[] dump = TestDumps.patch(2, WavestationBank.RAM2, 7);
        assertEquals(861, dump.length);
        assertTrue(WavestationSysex.isWavestation(dump));
        assertEquals(MessageType.PATCH_DUMP, WavestationSysex.type(dump).orElseThrow());
        assertEquals(2, WavestationSysex.channel(dump));
        assertEquals(WavestationBank.RAM2, WavestationSysex.bank(dump).orElseThrow());
        assertEquals(7, WavestationSysex.number(dump));
        assertTrue(WavestationSysex.isValidDump(dump));
        assertEquals("Patch RAM2:07 (861 bytes, checksum OK)", WavestationSysex.describe(dump));
    }

    @Test
    void corruptedDumpIsInvalid() {
        byte[] dump = TestDumps.patch(0, WavestationBank.RAM1, 0);
        dump[100] ^= 0x01;
        assertFalse(WavestationSysex.isValidDump(dump));
    }

    @Test
    void truncatedDumpIsInvalid() {
        byte[] dump = TestDumps.patch(0, WavestationBank.RAM1, 0);
        byte[] shorter = new byte[dump.length - 2];
        System.arraycopy(dump, 0, shorter, 0, shorter.length - 2);
        shorter[shorter.length - 2] = dump[dump.length - 2];
        shorter[shorter.length - 1] = (byte) 0xF7;
        assertFalse(WavestationSysex.isValidDump(shorter));
    }

    @Test
    void nibblesRoundTrip() {
        byte[] data = { 0x00, 0x7F, (byte) 0x80, (byte) 0xA5, 0x12 };
        byte[] nibbles = WavestationSysex.encodeNibbles(data);
        assertArrayEquals(bytes(0x0, 0x0, 0xF, 0x7, 0x0, 0x8, 0x5, 0xA, 0x2, 0x1), nibbles);
        byte[] message = new byte[nibbles.length + 2];
        System.arraycopy(nibbles, 0, message, 0, nibbles.length);
        assertArrayEquals(data, WavestationSysex.decodeNibbles(message, 0));
    }

    @Test
    void channelCanBeRewritten() {
        byte[] dump = TestDumps.patch(0, WavestationBank.RAM1, 0);
        byte[] moved = WavestationSysex.withChannel(dump, 5);
        assertEquals(0x35, moved[2]);
        assertTrue(WavestationSysex.isValidDump(moved)); // the channel is not in the checksum
    }

    @Test
    void bankNumbering() {
        assertEquals(0, WavestationBank.RAM1.sysexNumber());
        assertEquals(2, WavestationBank.ROM11.sysexNumber());
        assertEquals(3, WavestationBank.CARD.sysexNumber());
        assertEquals(4, WavestationBank.RAM3.sysexNumber());
        assertEquals(11, WavestationBank.ROM10.sysexNumber());
        assertEquals(WavestationBank.ROM5, WavestationBank.fromSysex(6).orElseThrow());
        assertEquals("C", WavestationBank.CARD.shortLabel());
        assertFalse(WavestationBank.ROM4.isWritable());
    }
}
