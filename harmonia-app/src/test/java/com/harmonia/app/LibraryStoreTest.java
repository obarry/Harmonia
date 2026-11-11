package com.harmonia.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.harmonia.wavestation.model.Layouts;
import com.harmonia.wavestation.model.Patch;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.model.Struct;
import com.harmonia.wavestation.model.WaveSequenceBank;
import com.harmonia.wavestation.sysex.WavestationBank;

class LibraryStoreTest {

    @TempDir
    Path dir;

    static List<Performance> performances(String prefix) {
        List<Performance> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Performance p = new Performance(new Struct(Layouts.PERFORMANCE));
            p.setName(prefix + i);
            list.add(p);
        }
        return list;
    }

    static List<Patch> patches(String prefix) {
        List<Patch> list = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            Patch p = new Patch(new Struct(Layouts.PATCH));
            p.setName(prefix + i);
            list.add(p);
        }
        return list;
    }

    @Test
    void completeBankIsSavedAsBankDumpsAndReloaded() throws Exception {
        SoundLibrary library = new SoundLibrary();
        library.putPerformances(WavestationBank.RAM1, performances("Perf "));
        library.putPatches(WavestationBank.RAM1, patches("Patch "));
        library.putWaveSequences(WavestationBank.RAM1, new WaveSequenceBank(new byte[WaveSequenceBank.SIZE]));
        assertTrue(library.isComplete(WavestationBank.RAM1));

        LibraryStore store = new LibraryStore(dir);
        store.save(library, WavestationBank.RAM1);
        store.markSynced(WavestationBank.RAM1);
        // three bank dumps: 18108 + 29828 + 17576 bytes
        assertEquals(18108 + 29828 + 17576, Files.size(dir.resolve("RAM1.syx")));

        SoundLibrary copy = new SoundLibrary();
        LibraryStore reopened = new LibraryStore(dir);
        assertEquals(Set.of(WavestationBank.RAM1), reopened.load(copy));
        assertTrue(copy.isComplete(WavestationBank.RAM1));
        assertEquals("Perf 49", copy.performance(WavestationBank.RAM1, 49).orElseThrow().getName());
        assertEquals("Patch 7", copy.patch(WavestationBank.RAM1, 7).orElseThrow().getName());
        assertTrue(reopened.syncedAt(WavestationBank.RAM1).isPresent());
        assertFalse(reopened.syncedAt(WavestationBank.RAM2).isPresent());
    }

    @Test
    void soundsReadOneByOneAreSavedAsSingleDumps() {
        SoundLibrary library = new SoundLibrary();
        library.putPerformance(WavestationBank.ROM5, 12, performances("Solo ").get(3));
        library.putPatch(WavestationBank.ROM5, 34, patches("Single ").get(2));
        LibraryStore store = new LibraryStore(dir);
        store.save(library, WavestationBank.ROM5);
        store.save(library, WavestationBank.RAM2);   // nothing known: no file
        assertFalse(Files.exists(dir.resolve("RAM2.syx")));

        SoundLibrary copy = new SoundLibrary();
        new LibraryStore(dir).load(copy);
        assertEquals("Solo 3", copy.performance(WavestationBank.ROM5, 12).orElseThrow().getName());
        assertEquals("Single 2", copy.patch(WavestationBank.ROM5, 34).orElseThrow().getName());
        assertFalse(copy.isComplete(WavestationBank.ROM5));
    }

    @Test
    void damagedFileIsIgnored() throws Exception {
        Files.write(dir.resolve("RAM3.syx"), new byte[] { (byte) 0xF0, 0x42, 0x30, 0x28, 0x4D, 1, 2, (byte) 0xF7 });
        SoundLibrary copy = new SoundLibrary();
        assertTrue(new LibraryStore(dir).load(copy).isEmpty());
        assertFalse(copy.hasAny(WavestationBank.RAM3));
    }

    @Test
    void absentCardAndInvalidation() {
        LibraryStore store = new LibraryStore(dir);
        store.markAbsent(WavestationBank.CARD);
        store.markSynced(WavestationBank.RAM1);
        store.setDeviceVersion("1.30");
        LibraryStore reopened = new LibraryStore(dir);
        assertTrue(reopened.isAbsent(WavestationBank.CARD));
        assertEquals("1.30", reopened.deviceVersion());
        reopened.invalidate();
        assertFalse(reopened.isAbsent(WavestationBank.CARD));
        assertFalse(reopened.syncedAt(WavestationBank.RAM1).isPresent());
        assertEquals("1.30", new LibraryStore(dir).deviceVersion());
    }
}
