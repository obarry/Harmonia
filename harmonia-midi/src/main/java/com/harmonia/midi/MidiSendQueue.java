package com.harmonia.midi;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Paced output queue: sends MIDI messages one by one from a background thread, with a pause
 * after each SysEx so that the device does not drop data.
 * <ul>
 * <li><b>Pacing</b>: the Wavestation SR loses parameter changes sent too quickly; about 40 ms
 * between messages is safe (value used by Edisyn).</li>
 * <li><b>Coalescing</b>: while a knob is being turned, only the latest value of a parameter
 * matters. A message sent with a key replaces a pending (not yet sent) message with the same
 * key, keeping its place in the queue.</li>
 * <li><b>Priority lane</b>: notes and controllers from the keyboard ({@link #sendNow}) are sent
 * before any waiting SysEx, without pause, so that playing stays responsive while editing.</li>
 * </ul>
 */
public class MidiSendQueue implements AutoCloseable {

    /** Where messages finally go: a Java Sound receiver in the application, a fake in tests. */
    @FunctionalInterface
    public interface Sink {
        void send(byte[] message) throws Exception;
    }

    /** Default pause after a SysEx message. */
    public static final long DEFAULT_PAUSE_MS = 40;

    private record Entry(String key, byte[] message, long pauseAfterMs) {
    }

    private final Sink sink;
    private final long defaultPauseMs;
    private final Deque<Entry> urgent = new ArrayDeque<>();
    private final LinkedList<Entry> paced = new LinkedList<>();
    private final Thread worker;
    private volatile Consumer<byte[]> onSent = m -> { };
    private volatile Consumer<Exception> onError = e -> { };
    private boolean closed;
    private boolean sending;

    public MidiSendQueue(Sink sink) {
        this(sink, DEFAULT_PAUSE_MS);
    }

    public MidiSendQueue(Sink sink, long defaultPauseMs) {
        this.sink = Objects.requireNonNull(sink);
        this.defaultPauseMs = defaultPauseMs;
        worker = new Thread(this::run, "harmonia-midi-out");
        worker.setDaemon(true);
        worker.start();
    }

    /** Called (on the queue thread) after each message actually sent: MIDI monitor, LED... */
    public void setOnSent(Consumer<byte[]> onSent) {
        this.onSent = onSent != null ? onSent : m -> { };
    }

    /** Called (on the queue thread) when the sink fails. */
    public void setOnError(Consumer<Exception> onError) {
        this.onError = onError != null ? onError : e -> { };
    }

    /** Queues a message, followed by the default pause if it is a SysEx. */
    public void send(byte[] message) {
        send(message, MidiBytes.isCompleteSysex(message) ? defaultPauseMs : 0);
    }

    /** Queues a message followed by a given pause (e.g. longer after a program change). */
    public synchronized void send(byte[] message, long pauseAfterMs) {
        checkOpen();
        paced.addLast(new Entry(null, message.clone(), pauseAfterMs));
        notifyAll();
    }

    /**
     * Queues a message that replaces any pending message with the same key (for instance
     * {@code "param:93"} for the filter cutoff).
     */
    public synchronized void sendCoalesced(String key, byte[] message) {
        checkOpen();
        Objects.requireNonNull(key);
        long pause = MidiBytes.isCompleteSysex(message) ? defaultPauseMs : 0;
        for (ListIterator<Entry> it = paced.listIterator(); it.hasNext();) {
            if (key.equals(it.next().key())) {
                it.set(new Entry(key, message.clone(), pause)); // same place in the queue
                return;
            }
        }
        paced.addLast(new Entry(key, message.clone(), pause));
        notifyAll();
    }

    /** Sends a real-time message (note, controller...) before any waiting SysEx, without pause. */
    public synchronized void sendNow(byte[] message) {
        checkOpen();
        urgent.addLast(new Entry(null, message.clone(), 0));
        notifyAll();
    }

    /** Number of messages waiting (both lanes). */
    public synchronized int pendingCount() {
        return urgent.size() + paced.size();
    }

    /** Drops all waiting messages (e.g. when the connection is closed or a dump is cancelled). */
    public synchronized void clear() {
        urgent.clear();
        paced.clear();
    }

    /** Waits until every queued message has been sent, or the timeout expires. */
    public synchronized boolean awaitEmpty(long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (!urgent.isEmpty() || !paced.isEmpty() || sending) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) {
                return false;
            }
            wait(left);
        }
        return true;
    }

    private void run() {
        while (true) {
            Entry entry;
            synchronized (this) {
                while (!closed && urgent.isEmpty() && paced.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (closed) {
                    return;
                }
                entry = !urgent.isEmpty() ? urgent.pollFirst() : paced.pollFirst();
                sending = true;
            }
            try {
                sink.send(entry.message());
                onSent.accept(entry.message());
            } catch (Exception e) {
                onError.accept(e);
            }
            pause(entry.pauseAfterMs());
            synchronized (this) {
                sending = false;
                notifyAll();
            }
        }
    }

    /** Pause after a message; urgent messages arriving meanwhile are still sent right away. */
    private void pause(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (true) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) {
                return;
            }
            Entry entry;
            synchronized (this) {
                if (closed) {
                    return;
                }
                if (urgent.isEmpty()) {
                    try {
                        wait(left);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
                entry = urgent.pollFirst();
            }
            try {
                sink.send(entry.message());
                onSent.accept(entry.message());
            } catch (Exception e) {
                onError.accept(e);
            }
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("queue closed");
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        urgent.clear();
        paced.clear();
        notifyAll();
    }
}
