package com.harmonia.wavestation;

import java.io.*;

public class PatchFileManager {

    public static void save(File file, byte[] sysex) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(sysex);
        }
    }

    public static byte[] load(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }
}
