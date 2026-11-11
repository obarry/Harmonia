package com.harmonia.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.harmonia.midi.MidiBytes;
import com.harmonia.wavestation.device.WavestationDevice;
import com.harmonia.wavestation.model.Dumps;
import com.harmonia.wavestation.sysex.MessageType;
import com.harmonia.wavestation.sysex.WavestationBank;

/**
 * Reads banks from the device in the background, one dump after the other, into the
 * {@link SoundLibrary}, and saves each bank in the {@link LibraryStore} as soon as it is read.
 * <p>
 * A bank that cannot be read (no answer, bad checksum) does not stop the others: it is reported
 * as failed. A CARD bank that does not answer is recorded as absent (no memory card), so that it
 * is not asked again at each start. The job can be cancelled; the dump in progress ends first.
 */
public class LibrarySync {

    /** Order in which the banks are read: the user banks first, then the factory banks. */
    public static final List<WavestationBank> ORDER = List.of(WavestationBank.RAM1, WavestationBank.RAM2,
            WavestationBank.RAM3, WavestationBank.CARD, WavestationBank.ROM11, WavestationBank.ROM4,
            WavestationBank.ROM5, WavestationBank.ROM6, WavestationBank.ROM7, WavestationBank.ROM8,
            WavestationBank.ROM9, WavestationBank.ROM10);

    /** Kinds of dumps read for each bank, in order. */
    public enum Step {
        PERFORMANCES("performances", MessageType.PERFORMANCE_BANK_DUMP),
        PATCHES("patchs", MessageType.PATCH_BANK_DUMP),
        WAVE_SEQUENCES("wave sequences", MessageType.WAVE_SEQUENCE_BANK_DUMP);

        private final String label;
        private final MessageType dump;

        Step(String label, MessageType dump) {
            this.label = label;
            this.dump = dump;
        }

        public String label() {
            return label;
        }

        /** Transfer time of the dump at MIDI speed, in milliseconds. */
        public long transferMillis() {
            return MidiBytes.transferMillis(dump.expectedLength());
        }
    }

    /** Progress, reported on a MIDI thread. */
    public interface Listener {
        /** A dump is being read: {@code done} dumps of {@code total} are already read. */
        void reading(WavestationBank bank, Step step, int done, int total);

        /** A bank has been read and saved. */
        default void bankRead(WavestationBank bank) {
        }
    }

    /** Outcome of a synchronisation. */
    public record Result(List<WavestationBank> read, List<WavestationBank> failed, List<WavestationBank> absent,
            boolean cancelled) {
    }

    private final WavestationDevice device;
    private final SoundLibrary library;
    private final LibraryStore store;
    private volatile boolean cancelled;

    public LibrarySync(WavestationDevice device, SoundLibrary library, LibraryStore store) {
        this.device = device;
        this.library = library;
        this.store = store;
    }

    /**
     * Banks to read at connection: those never read entirely, except a card already found absent.
     */
    public static List<WavestationBank> missing(SoundLibrary library, LibraryStore store) {
        return ORDER.stream()
                .filter(b -> !(library.isComplete(b) && store.syncedAt(b).isPresent()) && !store.isAbsent(b))
                .toList();
    }

    /** Estimated time to read the given banks, in seconds. */
    public static long estimatedSeconds(int banks) {
        long perBank = 0;
        for (Step s : Step.values()) {
            perBank += s.transferMillis() + 300;
        }
        return banks * perBank / 1000;
    }

    public void cancel() {
        cancelled = true;
    }

    /** Reads the banks one after the other. The future never fails: errors are in the result. */
    public CompletableFuture<Result> run(List<WavestationBank> banks, Listener listener) {
        List<WavestationBank> read = Collections.synchronizedList(new ArrayList<>());
        List<WavestationBank> failed = Collections.synchronizedList(new ArrayList<>());
        List<WavestationBank> absent = Collections.synchronizedList(new ArrayList<>());
        int total = banks.size() * Step.values().length;
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < banks.size(); i++) {
            WavestationBank bank = banks.get(i);
            int first = i * Step.values().length;
            chain = chain.thenCompose(v -> readBank(bank, first, total, listener)
                    .handle((ok, error) -> {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause() : error;
                        if (cause == null) {
                            store.save(library, bank);
                            store.markSynced(bank);
                            read.add(bank);
                            listener.bankRead(bank);
                        } else if (cause instanceof CancellationException) {
                            throw new CompletionException(cause);
                        } else if (bank == WavestationBank.CARD && cause instanceof TimeoutException) {
                            store.markAbsent(bank);
                            absent.add(bank);
                        } else {
                            failed.add(bank);
                        }
                        return null;
                    }));
        }
        return chain.handle((v, error) -> new Result(List.copyOf(read), List.copyOf(failed), List.copyOf(absent),
                cancelled));
    }

    private CompletableFuture<Void> readBank(WavestationBank bank, int done, int total, Listener listener) {
        return step(bank, Step.PERFORMANCES, done, total, listener,
                b -> device.requestPerformanceBank(b).thenAccept(d -> library.putPerformances(b,
                        Dumps.decodePerformanceBank(d))))
                .thenCompose(v -> step(bank, Step.PATCHES, done + 1, total, listener,
                        b -> device.requestPatchBank(b).thenAccept(d -> library.putPatches(b,
                                Dumps.decodePatchBank(d)))))
                .thenCompose(v -> step(bank, Step.WAVE_SEQUENCES, done + 2, total, listener,
                        b -> device.requestWaveSequenceBank(b).thenAccept(d -> library.putWaveSequences(b,
                                Dumps.decodeWaveSequenceBank(d)))));
    }

    private CompletableFuture<Void> step(WavestationBank bank, Step step, int done, int total, Listener listener,
            Function<WavestationBank, CompletableFuture<Void>> read) {
        if (cancelled) {
            return CompletableFuture.failedFuture(new CancellationException());
        }
        listener.reading(bank, step, done, total);
        return read.apply(bank);
    }
}
