package com.harmonia.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.harmonia.midi.MidiSendQueue;
import com.harmonia.midi.SysexRequests;
import com.harmonia.wavestation.device.WavestationDevice;
import com.harmonia.wavestation.model.Dumps;
import com.harmonia.wavestation.model.WaveSequenceBank;
import com.harmonia.wavestation.sysex.WavestationBank;
import com.harmonia.wavestation.sysex.WavestationSysex;

/** Synchronisation with a simulated device that answers every bank request. */
class LibrarySyncTest {

    @TempDir
    Path dir;

    private final SysexRequests requests = new SysexRequests();
    private WavestationDevice device;
    private final MidiSendQueue queue = new MidiSendQueue(this::answer, 0);
    private final SoundLibrary library = new SoundLibrary();

    @AfterEach
    void close() {
        queue.close();
    }

    /** Plays the device: answers each bank request with a dump named after the bank. */
    private void answer(byte[] request) {
        WavestationBank bank = WavestationBank.fromSysex(request[5] & 0x7F).orElseThrow();
        byte[] dump = switch (WavestationSysex.type(request).orElseThrow()) {
            case PERFORMANCE_BANK_REQUEST -> Dumps.encodePerformanceBank(
                    LibraryStoreTest.performances(bank.label() + " perf "), 0, bank);
            case PATCH_BANK_REQUEST -> Dumps.encodePatchBank(LibraryStoreTest.patches(bank.label() + " patch "), 0,
                    bank);
            case WAVE_SEQUENCE_BANK_REQUEST -> Dumps.encodeWaveSequenceBank(
                    new WaveSequenceBank(new byte[WaveSequenceBank.SIZE]), 0, bank);
            default -> null;
        };
        if (dump != null) {
            Thread.startVirtualThread(() -> device.onSysex(dump));
        }
    }

    @Test
    void readsTheBanksInOrderAndSavesThem() throws Exception {
        device = new WavestationDevice(queue, requests, 0);
        LibraryStore store = new LibraryStore(dir);
        List<String> progress = Collections.synchronizedList(new ArrayList<>());
        LibrarySync sync = new LibrarySync(device, library, store);
        LibrarySync.Result result = sync.run(List.of(WavestationBank.RAM1, WavestationBank.ROM4),
                (bank, step, done, total) -> progress.add(bank.label() + " " + step.label() + " " + done + "/" + total))
                .get(10, TimeUnit.SECONDS);

        assertEquals(List.of(WavestationBank.RAM1, WavestationBank.ROM4), result.read());
        assertTrue(result.failed().isEmpty());
        assertEquals(List.of("RAM1 performances 0/6", "RAM1 patchs 1/6", "RAM1 wave sequences 2/6",
                "ROM4 performances 3/6", "ROM4 patchs 4/6", "ROM4 wave sequences 5/6"), progress);
        assertEquals("ROM4 patch 3", library.patch(WavestationBank.ROM4, 3).orElseThrow().getName());
        assertTrue(Files.exists(dir.resolve("RAM1.syx")));
        assertTrue(Files.exists(dir.resolve("ROM4.syx")));
        assertTrue(store.syncedAt(WavestationBank.ROM4).isPresent());

        List<WavestationBank> missing = LibrarySync.missing(library, store);
        assertFalse(missing.contains(WavestationBank.RAM1));
        assertTrue(missing.contains(WavestationBank.RAM2));
        assertEquals(WavestationBank.RAM2, missing.get(0));
    }

    @Test
    void cancelStopsAfterTheCurrentBank() throws Exception {
        device = new WavestationDevice(queue, requests, 0);
        LibrarySync sync = new LibrarySync(device, library, new LibraryStore(dir));
        LibrarySync.Result result = sync.run(List.of(WavestationBank.RAM1, WavestationBank.RAM2),
                new LibrarySync.Listener() {
                    @Override
                    public void reading(WavestationBank bank, LibrarySync.Step step, int done, int total) {
                    }

                    @Override
                    public void bankRead(WavestationBank bank) {
                        sync.cancel();
                    }
                }).get(10, TimeUnit.SECONDS);
        assertTrue(result.cancelled());
        assertEquals(List.of(WavestationBank.RAM1), result.read());
        assertFalse(library.hasAny(WavestationBank.RAM2));
    }

    @Test
    void estimate() {
        long s = LibrarySync.estimatedSeconds(1);
        assertTrue(s >= 20 && s <= 23, Long.toString(s));
    }
}
