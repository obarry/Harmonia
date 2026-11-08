package com.harmonia.app;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.harmonia.wavestation.device.WavestationDevice;
import com.harmonia.wavestation.model.Dumps;
import com.harmonia.wavestation.model.Patch;
import com.harmonia.wavestation.model.Performance;
import com.harmonia.wavestation.model.WaveSequenceBank;
import com.harmonia.wavestation.sysex.WavestationBank;

/**
 * The sounds read from the device, bank by bank: performances, patches and wave sequences.
 * <p>
 * A bank can be read at once ({@link #readBank}: three dumps, about 20 seconds at MIDI speed),
 * or sound by sound when needed ({@link #loadPatch}, {@link #loadPerformance}). Listeners are told
 * which bank changed; they are called on MIDI threads.
 */
public class SoundLibrary {

    private final Map<WavestationBank, Performance[]> performances = new EnumMap<>(WavestationBank.class);
    private final Map<WavestationBank, Patch[]> patches = new EnumMap<>(WavestationBank.class);
    private final Map<WavestationBank, WaveSequenceBank> waveSequences = new EnumMap<>(WavestationBank.class);
    private final List<Consumer<WavestationBank>> listeners = new CopyOnWriteArrayList<>();

    /** Called with the bank whose content changed. */
    public void addListener(Consumer<WavestationBank> l) {
        listeners.add(l);
    }

    public synchronized Optional<Performance> performance(WavestationBank bank, int number) {
        Performance[] list = performances.get(bank);
        return list == null ? Optional.empty() : Optional.ofNullable(list[number]);
    }

    public synchronized Optional<Patch> patch(WavestationBank bank, int number) {
        Patch[] list = patches.get(bank);
        return list == null ? Optional.empty() : Optional.ofNullable(list[number]);
    }

    public synchronized Optional<WaveSequenceBank> waveSequences(WavestationBank bank) {
        return Optional.ofNullable(waveSequences.get(bank));
    }

    /** Performance names of a bank, null where unknown; empty if nothing is known. */
    public synchronized Optional<List<String>> performanceNames(WavestationBank bank) {
        Performance[] list = performances.get(bank);
        return list == null ? Optional.empty()
                : Optional.of(Arrays.stream(list).map(p -> p == null ? null : p.getName()).toList());
    }

    public synchronized Optional<List<String>> patchNames(WavestationBank bank) {
        Patch[] list = patches.get(bank);
        return list == null ? Optional.empty()
                : Optional.of(Arrays.stream(list).map(p -> p == null ? null : p.getName()).toList());
    }

    public synchronized Optional<List<String>> waveSequenceNames(WavestationBank bank) {
        return Optional.ofNullable(waveSequences.get(bank)).map(WaveSequenceBank::getNames);
    }

    // ------------------------------------------------------------------ filling

    public void putPerformances(WavestationBank bank, List<Performance> list) {
        synchronized (this) {
            performances.put(bank, list.toArray(new Performance[WavestationBank.PERFORMANCES]));
        }
        fire(bank);
    }

    public void putPatches(WavestationBank bank, List<Patch> list) {
        synchronized (this) {
            patches.put(bank, list.toArray(new Patch[WavestationBank.PATCHES]));
        }
        fire(bank);
    }

    public void putWaveSequences(WavestationBank bank, WaveSequenceBank sequences) {
        synchronized (this) {
            waveSequences.put(bank, sequences);
        }
        fire(bank);
    }

    public void putPatch(WavestationBank bank, int number, Patch patch) {
        synchronized (this) {
            patches.computeIfAbsent(bank, b -> new Patch[WavestationBank.PATCHES])[number] = patch;
        }
        fire(bank);
    }

    public void putPerformance(WavestationBank bank, int number, Performance performance) {
        synchronized (this) {
            performances.computeIfAbsent(bank, b -> new Performance[WavestationBank.PERFORMANCES])[number] = performance;
        }
        fire(bank);
    }

    private void fire(WavestationBank bank) {
        listeners.forEach(l -> l.accept(bank));
    }

    // ------------------------------------------------------------------ reading from the device

    /** Reads the performances, patches and wave sequences of a bank, one dump after the other. */
    public CompletableFuture<Void> readBank(WavestationDevice device, WavestationBank bank) {
        return device.requestPerformanceBank(bank)
                .thenAccept(dump -> putPerformances(bank, Dumps.decodePerformanceBank(dump)))
                .thenCompose(v -> device.requestPatchBank(bank))
                .thenAccept(dump -> putPatches(bank, Dumps.decodePatchBank(dump)))
                .thenCompose(v -> device.requestWaveSequenceBank(bank))
                .thenAccept(dump -> putWaveSequences(bank, Dumps.decodeWaveSequenceBank(dump)));
    }

    /** The patch from the library, or read from the device (and kept) if unknown. */
    public CompletableFuture<Patch> loadPatch(WavestationDevice device, WavestationBank bank, int number) {
        Optional<Patch> known = patch(bank, number);
        if (known.isPresent()) {
            return CompletableFuture.completedFuture(known.get());
        }
        return device.requestPatch(bank, number).thenApply(dump -> {
            Patch p = Dumps.decodePatch(dump);
            putPatch(bank, number, p);
            return p;
        });
    }

    /** The performance from the library, or read from the device (and kept) if unknown. */
    public CompletableFuture<Performance> loadPerformance(WavestationDevice device, WavestationBank bank, int number) {
        Optional<Performance> known = performance(bank, number);
        if (known.isPresent()) {
            return CompletableFuture.completedFuture(known.get());
        }
        return device.requestPerformance(bank, number).thenApply(dump -> {
            Performance p = Dumps.decodePerformance(dump);
            putPerformance(bank, number, p);
            return p;
        });
    }
}
