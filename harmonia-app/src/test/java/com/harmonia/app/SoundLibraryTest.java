package com.harmonia.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.harmonia.midi.MidiSendQueue;
import com.harmonia.midi.SysexRequests;
import com.harmonia.wavestation.device.WavestationDevice;
import com.harmonia.wavestation.model.Dumps;
import com.harmonia.wavestation.model.Layouts;
import com.harmonia.wavestation.model.Patch;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.model.Struct;
import com.harmonia.wavestation.model.WaveSequenceBank;
import com.harmonia.wavestation.sysex.MessageType;
import com.harmonia.wavestation.sysex.WavestationBank;
import com.harmonia.wavestation.sysex.WavestationSysex;

/** Reads a whole bank from a simulated device that answers each request. */
class SoundLibraryTest {

    private final List<byte[]> sent = Collections.synchronizedList(new ArrayList<>());
    private final MidiSendQueue queue = new MidiSendQueue(sent::add, 0);
    private final WavestationDevice device = new WavestationDevice(queue, new SysexRequests(), 0);
    private final SoundLibrary library = new SoundLibrary();

    @AfterEach
    void close() {
        queue.close();
    }

    /** Plays the device: answers the requests found in the output queue. */
    private void answer(WavestationBank bank) {
        List<byte[]> requests = new ArrayList<>(sent);
        sent.removeAll(requests); // answering may queue the next request
        for (byte[] request : requests) {
            MessageType type = WavestationSysex.type(request).orElseThrow();
            byte[] dump = switch (type) {
                case PERFORMANCE_BANK_REQUEST -> Dumps.encodePerformanceBank(performances(), 0, bank);
                case PATCH_BANK_REQUEST -> Dumps.encodePatchBank(patches(), 0, bank);
                case WAVE_SEQUENCE_BANK_REQUEST -> Dumps.encodeWaveSequenceBank(
                        new WaveSequenceBank(new byte[WaveSequenceBank.SIZE]), 0, bank);
                default -> null;
            };
            if (dump != null) {
                device.onSysex(dump);
            }
        }
    }

    private static List<Performance> performances() {
        List<Performance> list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Performance p = new Performance(new Struct(Layouts.PERFORMANCE));
            p.setName("Perf " + i);
            list.add(p);
        }
        return list;
    }

    private static List<Patch> patches() {
        List<Patch> list = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            Patch p = new Patch(new Struct(Layouts.PATCH));
            p.setName("Patch " + i);
            list.add(p);
        }
        return list;
    }

    @Test
    void readBankFillsTheLibrary() throws Exception {
        List<WavestationBank> changed = Collections.synchronizedList(new ArrayList<>());
        library.addListener(changed::add);
        CompletableFuture<Void> f = library.readBank(device, WavestationBank.RAM2);
        for (int step = 0; step < 3 && !f.isDone(); step++) {
            assertTrue(queue.awaitEmpty(2000));
            Thread.sleep(50);
            answer(WavestationBank.RAM2);
        }
        f.get(2, TimeUnit.SECONDS);
        assertEquals("Perf 49", library.performanceNames(WavestationBank.RAM2).orElseThrow().get(49));
        assertEquals("Patch 3", library.patch(WavestationBank.RAM2, 3).orElseThrow().getName());
        assertEquals(32, library.waveSequenceNames(WavestationBank.RAM2).orElseThrow().size());
        assertEquals(3, changed.size());
        assertTrue(library.patchNames(WavestationBank.RAM1).isEmpty());
    }

    @Test
    void knownPatchIsNotRequestedAgain() throws Exception {
        Patch p = new Patch(new Struct(Layouts.PATCH));
        p.setName("Known");
        library.putPatch(WavestationBank.RAM1, 4, p);
        assertEquals("Known", library.loadPatch(device, WavestationBank.RAM1, 4).get(1, TimeUnit.SECONDS).getName());
        Thread.sleep(50);
        assertEquals(0, sent.size());
    }
}
