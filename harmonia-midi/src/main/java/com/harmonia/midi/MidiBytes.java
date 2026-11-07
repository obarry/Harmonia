package com.harmonia.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

/**
 * Helpers between raw MIDI bytes and {@code javax.sound.midi} messages.
 * <p>
 * The rest of Harmonia works with complete messages as {@code byte[]} (status byte first, a
 * SysEx from F0 to F7 included): simple to test, log and store.
 */
public final class MidiBytes {

    public static final int SYSEX_START = 0xF0;
    public static final int SYSEX_END = 0xF7;

    private MidiBytes() {
    }

    /** Builds the Java Sound message for raw bytes (channel message, system message or SysEx). */
    public static MidiMessage toMidiMessage(byte[] bytes) throws InvalidMidiDataException {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidMidiDataException("empty message");
        }
        int status = bytes[0] & 0xFF;
        if (status == SYSEX_START || status == SYSEX_END) {
            return new SysexMessage(bytes, bytes.length);
        }
        ShortMessage m = new ShortMessage();
        int d1 = bytes.length > 1 ? bytes[1] & 0x7F : 0;
        int d2 = bytes.length > 2 ? bytes[2] & 0x7F : 0;
        m.setMessage(status, d1, d2);
        return m;
    }

    /** True for a complete SysEx message, F0 ... F7. */
    public static boolean isCompleteSysex(byte[] bytes) {
        return bytes != null && bytes.length >= 2 && (bytes[0] & 0xFF) == SYSEX_START
                && (bytes[bytes.length - 1] & 0xFF) == SYSEX_END;
    }

    /** True for a channel voice message (note, controller, program change, pitch bend...). */
    public static boolean isChannelMessage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        int status = bytes[0] & 0xFF;
        return status >= 0x80 && status < 0xF0;
    }

    /** Hex dump, e.g. "F0 42 30 28 10 00 0C F7"; long messages are shortened in the middle. */
    public static String toHex(byte[] bytes, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        int n = bytes.length;
        boolean shorten = maxBytes > 0 && n > maxBytes;
        int head = shorten ? maxBytes - 2 : n;
        for (int i = 0; i < head; i++) {
            appendHex(sb, bytes[i]);
        }
        if (shorten) {
            sb.append(" …");
            appendHex(sb, bytes[n - 2]);
            appendHex(sb, bytes[n - 1]);
        }
        return sb.toString().trim();
    }

    public static String toHex(byte[] bytes) {
        return toHex(bytes, 0);
    }

    private static void appendHex(StringBuilder sb, byte b) {
        sb.append(' ').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16)))
                .append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
    }

    /**
     * Time needed to transmit a message on a MIDI DIN cable: 31 250 bauds, 10 bits per byte,
     * i.e. 3 125 bytes per second (USB interfaces are usually not faster towards the device).
     */
    public static long transferMillis(int bytes) {
        return (bytes * 1000L + 3124) / 3125;
    }
}
