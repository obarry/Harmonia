package com.harmonia.midi;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import javax.sound.midi.MidiMessage;

/**
 * Rebuilds complete SysEx messages from what Java Sound delivers.
 * <p>
 * A long SysEx (a Wavestation bank is about 30 KB) may arrive in several pieces, depending on
 * the operating system and the MIDI driver: a first {@code SysexMessage} with status F0, then
 * "continuation" messages with status F7, the last one ending with the F7 end byte. Real-time
 * messages (clock, active sensing) may also be interleaved. This class glues the pieces back
 * together and delivers each complete message F0 ... F7 once.
 * <p>
 * An incomplete message is dropped if a new F0 arrives, or if the gap between two pieces
 * exceeds the timeout.
 */
public class SysexAssembler {

    /** Default maximum gap between two pieces of the same SysEx. */
    public static final long DEFAULT_TIMEOUT_MS = 2000;

    private final Consumer<byte[]> onComplete;
    private final LongSupplier clockMillis;
    private final long timeoutMs;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean assembling;
    private long lastPieceAt;
    private int droppedCount;

    /**
     * @param onComplete receives each complete SysEx (F0 ... F7)
     */
    public SysexAssembler(Consumer<byte[]> onComplete) {
        this(onComplete, System::currentTimeMillis, DEFAULT_TIMEOUT_MS);
    }

    /** Constructor for tests: injectable clock and timeout. */
    public SysexAssembler(Consumer<byte[]> onComplete, LongSupplier clockMillis, long timeoutMs) {
        this.onComplete = onComplete;
        this.clockMillis = clockMillis;
        this.timeoutMs = timeoutMs;
    }

    /** Feeds a message received from Java Sound. Non-SysEx messages are ignored. */
    public void accept(MidiMessage message) {
        accept(message.getMessage());
    }

    /**
     * Feeds raw bytes as returned by {@link MidiMessage#getMessage()}: status byte first
     * (F0 for a start, F7 for a continuation).
     */
    public synchronized void accept(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return;
        }
        int status = raw[0] & 0xFF;
        long now = clockMillis.getAsLong();
        if (assembling && now - lastPieceAt > timeoutMs) {
            drop();
        }
        if (status == MidiBytes.SYSEX_START) {
            if (assembling) {
                drop(); // the previous message never ended
            }
            buffer.reset();
            assembling = true;
            append(raw, 0, now);
        } else if (status == MidiBytes.SYSEX_END && assembling) {
            // Continuation: the F7 status byte is not part of the data (Java Sound convention),
            // unless this piece is just the end byte.
            if (raw.length == 1) {
                append(raw, 0, now);
            } else {
                append(raw, 1, now);
            }
        }
        // Anything else (channel or real-time message, stray continuation) is not our business.
    }

    private void append(byte[] raw, int from, long now) {
        lastPieceAt = now;
        for (int i = from; i < raw.length; i++) {
            int b = raw[i] & 0xFF;
            buffer.write(b);
            if (b == MidiBytes.SYSEX_END) {
                byte[] complete = buffer.toByteArray();
                buffer.reset();
                assembling = false;
                onComplete.accept(complete);
                return; // bytes after F7 in the same piece would be garbage
            }
        }
    }

    private void drop() {
        buffer.reset();
        assembling = false;
        droppedCount++;
    }

    /** True while a SysEx has started but not ended. */
    public synchronized boolean isAssembling() {
        return assembling;
    }

    /** Number of incomplete messages dropped so far (useful for diagnostics). */
    public synchronized int getDroppedCount() {
        return droppedCount;
    }
}
