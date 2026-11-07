package com.harmonia.wavestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchFileManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void saveThenLoadReturnsSameBytes() throws IOException {
        byte[] sysex = { (byte) 0xF0, 0x42, 0x30, 0x28, 0x10, 0x00, 0x0C, (byte) 0xF7 };
        File file = tempDir.resolve("patch.syx").toFile();

        PatchFileManager.save(file, sysex);

        assertArrayEquals(sysex, PatchFileManager.load(file));
    }
}
