package com.harmonia.wavestation.sysex;

import java.util.Random;

/** Builds synthetic but well-formed Wavestation dumps for the tests. */
public final class TestDumps {

    private TestDumps() {
    }

    /** Patch dump (861 bytes) with random data and a correct checksum. */
    public static byte[] patch(int channel, WavestationBank bank, int number) {
        return dump(channel, MessageType.PATCH_DUMP, new int[] { bank.sysexNumber(), number });
    }

    /** Dump of the given type (fixed length) with random data and a correct checksum. */
    public static byte[] dump(int channel, MessageType type, int[] headerBytes) {
        byte[] m = new byte[type.expectedLength()];
        m[0] = (byte) 0xF0;
        m[1] = 0x42;
        m[2] = (byte) (0x30 | channel);
        m[3] = 0x28;
        m[4] = (byte) type.code();
        for (int i = 0; i < headerBytes.length; i++) {
            m[5 + i] = (byte) headerBytes[i];
        }
        Random random = new Random(42);
        for (int i = type.dataOffset(); i < m.length - 2; i++) {
            m[i] = (byte) random.nextInt(16);
        }
        m[m.length - 2] = (byte) WavestationSysex.checksum(m, type.dataOffset());
        m[m.length - 1] = (byte) 0xF7;
        return m;
    }
}
