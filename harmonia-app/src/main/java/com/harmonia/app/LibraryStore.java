package com.harmonia.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import com.harmonia.wavestation.sysex.WavestationBank;

/**
 * Copy of the device memory kept on disk between two sessions, so that Harmonia starts with all
 * the sounds already known instead of reading them from the device at each click.
 * <p>
 * One standard .syx file per bank ({@code RAM1.syx}, {@code ROM4.syx}...), that can also be sent
 * back to the device as a backup, and {@code library.properties} with the date each bank was
 * read, the banks found absent (no card) and the version of the device they come from.
 * Default folder: {@code ~/.harmonia/library}.
 */
public class LibraryStore {

    private static final String INDEX = "library.properties";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path folder;
    private final Properties index = new Properties();

    public LibraryStore(Path folder) {
        this.folder = folder;
        Path file = folder.resolve(INDEX);
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                index.load(in);
            } catch (IOException e) {
                index.clear();   // unreadable index: the banks will be read again
            }
        }
    }

    /** The default store, in the user's home folder. */
    public static LibraryStore defaultStore() {
        return new LibraryStore(Path.of(System.getProperty("user.home"), ".harmonia", "library"));
    }

    public Path folder() {
        return folder;
    }

    /** Fills the library with the banks saved on disk; returns the banks loaded. */
    public synchronized Set<WavestationBank> load(SoundLibrary library) {
        Set<WavestationBank> loaded = EnumSet.noneOf(WavestationBank.class);
        for (WavestationBank bank : WavestationBank.values()) {
            Path file = file(bank);
            if (Files.exists(file)) {
                try {
                    if (library.importSyx(Files.readAllBytes(file)) > 0) {
                        loaded.add(bank);
                    }
                } catch (IOException e) {
                    // unreadable file: the bank will be read again from the device
                }
            }
        }
        return loaded;
    }

    /** Saves what the library knows about a bank (nothing is written if it knows nothing). */
    public synchronized void save(SoundLibrary library, WavestationBank bank) {
        if (!library.hasAny(bank)) {
            return;
        }
        try {
            Files.createDirectories(folder);
            Path tmp = folder.resolve(bank.label() + ".syx.tmp");
            Files.write(tmp, library.exportBank(bank));
            try {
                Files.move(tmp, file(bank), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file(bank), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Records that a bank has just been read entirely from the device. */
    public synchronized void markSynced(WavestationBank bank) {
        index.setProperty("synced." + bank.label(), LocalDateTime.now().withNano(0).format(DATE));
        index.remove("absent." + bank.label());
        writeIndex();
    }

    /** Records that the device has no such bank (no memory card). */
    public synchronized void markAbsent(WavestationBank bank) {
        index.setProperty("absent." + bank.label(), "true");
        writeIndex();
    }

    /** When the bank was last read entirely from the device. */
    public synchronized Optional<LocalDateTime> syncedAt(WavestationBank bank) {
        String v = index.getProperty("synced." + bank.label());
        try {
            return v == null ? Optional.empty() : Optional.of(LocalDateTime.parse(v, DATE));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public synchronized boolean isAbsent(WavestationBank bank) {
        return Boolean.parseBoolean(index.getProperty("absent." + bank.label()));
    }

    /** Version of the device the saved banks come from ("" if unknown). */
    public synchronized String deviceVersion() {
        return index.getProperty("device.version", "");
    }

    public synchronized void setDeviceVersion(String version) {
        index.setProperty("device.version", version);
        writeIndex();
    }

    /** Forgets when the banks were read: they will all be read again. */
    public synchronized void invalidate() {
        index.stringPropertyNames().stream().filter(k -> k.startsWith("synced.") || k.startsWith("absent."))
                .forEach(index::remove);
        writeIndex();
    }

    private Path file(WavestationBank bank) {
        return folder.resolve(bank.label() + ".syx");
    }

    private void writeIndex() {
        try {
            Files.createDirectories(folder);
            try (OutputStream out = Files.newOutputStream(folder.resolve(INDEX))) {
                index.store(out, "Harmonia: copy of the Wavestation SR memory");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
