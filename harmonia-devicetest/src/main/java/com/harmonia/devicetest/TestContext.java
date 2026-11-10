package com.harmonia.devicetest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.harmonia.app.DeviceSession;
import com.harmonia.app.SoundLibrary;
import com.harmonia.wavestation.device.WavestationDevice;

/**
 * What a device test can use: the dialogue with the tester, the device, the sound library, the
 * messages received from the device, and the report entry of the test.
 */
public class TestContext {

    private static final String[] PHOTO_EXTENSIONS = { ".jpg", ".jpeg", ".png", ".heic", ".webp" };

    private final Console console;
    private final DeviceSession session;
    private final SoundLibrary library;
    private final TestReport report;
    private final BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
    private final BlockingQueue<byte[]> keyboard = new LinkedBlockingQueue<>();
    private TestReport.Entry entry;
    private final java.util.Map<String, Object> shared = new java.util.HashMap<>();

    public TestContext(Console console, DeviceSession session, SoundLibrary library, TestReport report) {
        this.console = console;
        this.session = session;
        this.library = library;
        this.report = report;
    }

    public Console console() {
        return console;
    }

    public TestReport.Entry entry() {
        return entry;
    }

    void setEntry(TestReport.Entry entry) {
        this.entry = entry;
    }

    public DeviceSession session() {
        return session;
    }

    public WavestationDevice device() {
        return session.device().orElseThrow(() -> new IllegalStateException("not connected"));
    }

    public SoundLibrary library() {
        return library;
    }

    /** Shares a value with the following tests (e.g. the patch read by T04, sent back by T07). */
    public void share(String key, Object value) {
        shared.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T shared(String key) {
        return (T) shared.get(key);
    }

    /** Every message received from the device (fed by the runner). */
    void received(byte[] message) {
        received.add(message);
    }

    /** Every message played on the master keyboard (fed by the runner). */
    void keyboard(byte[] message) {
        keyboard.add(message);
    }

    /** Forgets the messages received so far. */
    public void clearReceived() {
        received.clear();
        keyboard.clear();
    }

    /** Messages received from the device during the given time. */
    public List<byte[]> collect(long millis) throws InterruptedException {
        return drain(received, millis);
    }

    /** Messages played on the master keyboard during the given time. */
    public List<byte[]> collectKeyboard(long millis) throws InterruptedException {
        return drain(keyboard, millis);
    }

    private static List<byte[]> drain(BlockingQueue<byte[]> queue, long millis) throws InterruptedException {
        List<byte[]> list = new ArrayList<>();
        long end = System.currentTimeMillis() + millis;
        long left;
        while ((left = end - System.currentTimeMillis()) > 0) {
            byte[] m = queue.poll(left, TimeUnit.MILLISECONDS);
            if (m != null) {
                list.add(m);
            }
        }
        return list;
    }

    /**
     * Waits for a device operation; returns null and records a failed check if it fails.
     *
     * @param what description for the report, e.g. "lecture du patch RAM1:00"
     */
    public <T> T await(CompletableFuture<T> future, String what) {
        long start = System.currentTimeMillis();
        try {
            T result = future.get(120, TimeUnit.SECONDS);
            entry.check(what + " (" + (System.currentTimeMillis() - start) + " ms)", true);
            return result;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String reason = cause instanceof TimeoutException ? "pas de réponse de l'appareil" : cause.toString();
            entry.check(what + " : " + reason, false);
            console.info("  ❌ " + what + " : " + reason);
            return null;
        } catch (TimeoutException e) {
            entry.check(what + " : délai dépassé", false);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Like {@link #await}, for operations without result (dump sent, write): true if it succeeded. */
    public boolean succeeds(CompletableFuture<?> future, String what) {
        int before = entry.failures();
        await(future, what);
        return entry.failures() == before;
    }

    /** Asks a yes / no question and records the answer; true if the answer is yes. */
    public boolean ask(String question) {
        Console.Answer a = console.yesNo(question);
        entry.answer(question, a);
        if (a == Console.Answer.NO) {
            String what = console.text("Qu'observez-vous ? (Entrée pour ne rien ajouter)");
            if (!what.isEmpty()) {
                entry.note("Observation du testeur : " + what);
            }
        }
        return a == Console.Answer.YES;
    }

    /** Automatic check, recorded and shown. */
    public boolean check(String description, boolean ok) {
        entry.check(description, ok);
        console.info("  " + (ok ? "✅ " : "❌ ") + description);
        return ok;
    }

    /** Information shown to the tester and kept in the report. */
    public void show(String text) {
        console.info(text.indent(4).stripTrailing());
        entry.block(text);
    }

    /** Saves a SysEx dump in the session folder and lists it in the report. */
    public Path saveDump(String name, byte[] dump, String description) {
        Path file = report.folder().resolve(name);
        try {
            Files.createDirectories(report.folder());
            Files.write(file, dump);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        entry.file(name, description);
        return file;
    }

    /**
     * Asks the tester for a photo of the device screen, saved in the session folder under the
     * given name (any common image extension). Optional: the tester can skip it.
     */
    public void photo(String name, String whatToShoot) {
        console.step("Photo (facultative) : " + whatToShoot);
        console.step("Enregistrez-la dans " + report.folder().toAbsolutePath() + " sous le nom " + name
                + ".jpg (ou .png)");
        console.waitEnter("quand c'est fait, ou directement pour passer");
        for (String ext : PHOTO_EXTENSIONS) {
            if (Files.exists(report.folder().resolve(name + ext))) {
                entry.file(name + ext, whatToShoot);
                console.info("    Photo trouvée : " + name + ext);
                return;
            }
        }
        console.info("    Pas de photo.");
    }
}
