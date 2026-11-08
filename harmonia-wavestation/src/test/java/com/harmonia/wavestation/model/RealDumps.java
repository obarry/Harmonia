package com.harmonia.wavestation.model;

import java.util.ArrayList;
import java.util.List;

/** Splits a .syx file (several messages back to back) into messages. */
final class RealDumps {

    private RealDumps() {
    }

    static List<byte[]> split(byte[] file) {
        List<byte[]> messages = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < file.length; i++) {
            int b = file[i] & 0xFF;
            if (b == 0xF0) {
                start = i;
            } else if (b == 0xF7 && start >= 0) {
                byte[] m = new byte[i - start + 1];
                System.arraycopy(file, start, m, 0, m.length);
                messages.add(m);
                start = -1;
            }
        }
        return messages;
    }
}
