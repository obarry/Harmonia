package com.harmonia.wavestation.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.harmonia.wavestation.sysex.MessageType;
import com.harmonia.wavestation.sysex.TestDumps;
import com.harmonia.wavestation.sysex.WavestationBank;
import com.harmonia.wavestation.sysex.WavestationSysex;

class DumpsTest {

    @Test
    void patchDumpRoundTrip() {
        byte[] dump = TestDumps.patch(0, WavestationBank.RAM2, 7);
        Patch patch = Dumps.decodePatch(dump);
        assertArrayEquals(dump, Dumps.encodePatch(patch, 0, WavestationBank.RAM2, 7));
    }

    @Test
    void bankDumpsRoundTrip() {
        byte[] patches = TestDumps.dump(3, MessageType.PATCH_BANK_DUMP, new int[] { 4 });
        assertArrayEquals(patches, Dumps.encodePatchBank(Dumps.decodePatchBank(patches), 3, WavestationBank.RAM3));

        byte[] perfs = TestDumps.dump(0, MessageType.PERFORMANCE_BANK_DUMP, new int[] { 1 });
        List<Performance> list = Dumps.decodePerformanceBank(perfs);
        assertEquals(50, list.size());
        assertArrayEquals(perfs, Dumps.encodePerformanceBank(list, 0, WavestationBank.RAM2));

        byte[] ws = TestDumps.dump(0, MessageType.WAVE_SEQUENCE_BANK_DUMP, new int[] { 0 });
        assertArrayEquals(ws, Dumps.encodeWaveSequenceBank(Dumps.decodeWaveSequenceBank(ws), 0, WavestationBank.RAM1));
    }

    @Test
    void patchValuesSurviveEncoding() {
        Patch patch = new Patch(new Struct(Layouts.PATCH));
        patch.setName("Glass Choir");
        patch.struct().set("Number_Of_Waves", 2);
        patch.struct().set("Mix_X3", 127 + 100);
        patch.struct().set("Mix_Y3", 127 - 56);
        patch.wave("C").set("Cutoff", 72);
        patch.wave("C").set("Wave_Coarse", -12);

        Patch copy = Dumps.decodePatch(Dumps.encodePatch(patch, 0, WavestationBank.RAM1, 12));
        assertEquals("Glass Choir", copy.getName());
        assertEquals(4, copy.getWaveCount());
        assertEquals(100, copy.getMixAc(3));
        assertEquals(-56, copy.getMixBd(3));
        assertEquals(72, copy.wave("C").getCutoff());
        assertEquals(-12, copy.wave("C").getCoarse());
    }

    @Test
    void partBankUsesTheExpansionBits() {
        Performance perf = new Performance(new Struct(Layouts.PERFORMANCE));
        Part part = perf.part(2);
        part.setPatch(WavestationBank.RAM3, 12);
        assertEquals(WavestationBank.RAM3, part.getBank());
        assertEquals(2, part.get("Bank_Num"));           // RAM3 = 2 with expansion 1
        assertEquals(1, (part.get("Part_Mode") >> 6) & 3);
        assertEquals("RAM3:12", part.patchReference());

        part.setPatch(WavestationBank.ROM10, 3);
        assertEquals(WavestationBank.ROM10, part.getBank());
    }

    @Test
    void unusedPartsAreDetected() {
        Performance perf = new Performance(new Struct(Layouts.PERFORMANCE));
        perf.part(1).setPatch(WavestationBank.RAM1, 0);
        for (int i = 2; i <= 8; i++) {
            perf.part(i).set("Bank_Num", -1);
            perf.part(i).set("Patch_Num", -1);
        }
        assertEquals(1, perf.getUsedParts().size());
        assertNull(perf.part(5).getBank());
        assertEquals("-", perf.part(5).patchReference());
    }

    @Test
    void waveBankUsesBankExp() {
        Patch patch = new Patch(new Struct(Layouts.PATCH));
        patch.wave("B").set("Wave_Bank", 2);
        assertEquals(WavestationBank.ROM11, patch.wave("B").getBank());
        patch.struct().set("Bank_Exp", 1 << 1);           // first expansion bit of wave B
        assertEquals(WavestationBank.RAM3, patch.wave("B").getBank());
        patch.struct().set("Bank_Exp", 1 << 5);           // second expansion bit of wave B
        assertEquals(WavestationBank.ROM9, patch.wave("B").getBank());
    }

    @Test
    void wrongOrCorruptedDumpsAreRejected() {
        byte[] patch = TestDumps.patch(0, WavestationBank.RAM1, 0);
        assertThrows(IllegalArgumentException.class, () -> Dumps.decodePerformance(patch));
        patch[200] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> Dumps.decodePatch(patch));
    }

    /**
     * Real dumps saved from a Wavestation SR (.syx files placed in src/test/resources/syx) must
     * decode and re-encode to the same bytes. Without files, nothing is checked.
     */
    @Test
    void realDumpsRoundTrip() throws IOException {
        Path folder = Path.of("src/test/resources/syx");
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.syx")) {
            for (Path file : files) {
                for (byte[] dump : RealDumps.split(Files.readAllBytes(file))) {
                    checkRoundTrip(file.getFileName().toString(), dump);
                }
            }
        }
    }

    private static void checkRoundTrip(String file, byte[] dump) {
        MessageType type = WavestationSysex.type(dump).orElse(null);
        if (type == null || !type.isDump()) {
            return;
        }
        assertTrue(WavestationSysex.isValidDump(dump), file + ": invalid " + type);
        int ch = WavestationSysex.channel(dump);
        WavestationBank bank = WavestationSysex.bank(dump).orElse(null);
        byte[] again = switch (type) {
            case PATCH_DUMP -> Dumps.encodePatch(Dumps.decodePatch(dump), ch, bank, WavestationSysex.number(dump));
            case PERFORMANCE_DUMP -> Dumps.encodePerformance(Dumps.decodePerformance(dump), ch, bank,
                    WavestationSysex.number(dump));
            case PATCH_BANK_DUMP -> Dumps.encodePatchBank(Dumps.decodePatchBank(dump), ch, bank);
            case PERFORMANCE_BANK_DUMP -> Dumps.encodePerformanceBank(Dumps.decodePerformanceBank(dump), ch, bank);
            case WAVE_SEQUENCE_BANK_DUMP -> Dumps.encodeWaveSequenceBank(Dumps.decodeWaveSequenceBank(dump), ch, bank);
            default -> dump;
        };
        assertArrayEquals(dump, again, file + ": " + type);
        assertFalse(again.length == 0);
    }
}
