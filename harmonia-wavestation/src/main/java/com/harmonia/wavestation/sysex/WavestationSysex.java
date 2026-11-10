package com.harmonia.wavestation.sysex;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.harmonia.wavestation.param.Parameter;

/**
 * Builds and decodes Wavestation SysEx messages (Reference Guide, appendix 4).
 * <p>
 * Every message starts with the header {@code F0 42 3n 28}: Korg ID, format ID with the MIDI
 * basic channel {@code n}, Wavestation device ID; then the message type. Data is sent as nibbles
 * (low 4 bits first, then high 4 bits) followed by a checksum: the sum of the nibbles, 7 bits.
 */
public final class WavestationSysex {

    public static final int KORG_ID = 0x42;
    public static final int WAVESTATION_ID = 0x28;
    /** Device Inquiry reply of the SR: family 28, member 02. */
    public static final int FAMILY = 0x28;
    public static final int MEMBER_SR = 0x02;
    /** Maximum length of a parameter value text. */
    public static final int MAX_VALUE_CHARS = 16;

    private static final int TYPE_INDEX = 4;

    /** Messages whose byte 5 is a bank number. */
    private static final Set<MessageType> WITH_BANK = EnumSet.of(MessageType.PATCH_REQUEST,
            MessageType.PERFORMANCE_REQUEST, MessageType.PATCH_BANK_REQUEST, MessageType.PERFORMANCE_BANK_REQUEST,
            MessageType.WAVE_SEQUENCE_BANK_REQUEST, MessageType.PATCH_WRITE, MessageType.PERFORMANCE_WRITE,
            MessageType.PATCH_DUMP, MessageType.PERFORMANCE_DUMP, MessageType.PATCH_BANK_DUMP,
            MessageType.PERFORMANCE_BANK_DUMP, MessageType.WAVE_SEQUENCE_BANK_DUMP);
    /** Messages whose byte 6 is a patch or performance number. */
    private static final Set<MessageType> WITH_NUMBER = EnumSet.of(MessageType.PATCH_REQUEST,
            MessageType.PERFORMANCE_REQUEST, MessageType.PATCH_WRITE, MessageType.PERFORMANCE_WRITE,
            MessageType.PATCH_DUMP, MessageType.PERFORMANCE_DUMP);

    private WavestationSysex() {
    }

    // ------------------------------------------------------------------ building

    /** Message with the header, the type and the given bytes, ended by F7. */
    public static byte[] message(int channel, MessageType type, int... body) {
        byte[] m = new byte[body.length + 6];
        m[0] = (byte) 0xF0;
        m[1] = KORG_ID;
        m[2] = (byte) (0x30 | (channel & 0x0F));
        m[3] = WAVESTATION_ID;
        m[4] = (byte) type.code();
        for (int i = 0; i < body.length; i++) {
            m[5 + i] = (byte) (body[i] & 0x7F);
        }
        m[m.length - 1] = (byte) 0xF7;
        return m;
    }

    public static byte[] patchRequest(int channel, WavestationBank bank, int number) {
        checkNumber(number, WavestationBank.PATCHES);
        return message(channel, MessageType.PATCH_REQUEST, bank.sysexNumber(), number);
    }

    public static byte[] performanceRequest(int channel, WavestationBank bank, int number) {
        checkNumber(number, WavestationBank.PERFORMANCES);
        return message(channel, MessageType.PERFORMANCE_REQUEST, bank.sysexNumber(), number);
    }

    public static byte[] patchBankRequest(int channel, WavestationBank bank) {
        return message(channel, MessageType.PATCH_BANK_REQUEST, bank.sysexNumber());
    }

    public static byte[] performanceBankRequest(int channel, WavestationBank bank) {
        return message(channel, MessageType.PERFORMANCE_BANK_REQUEST, bank.sysexNumber());
    }

    public static byte[] waveSequenceBankRequest(int channel, WavestationBank bank) {
        return message(channel, MessageType.WAVE_SEQUENCE_BANK_REQUEST, bank.sysexNumber());
    }

    public static byte[] systemSetupRequest(int channel) {
        return message(channel, MessageType.SYSTEM_SETUP_REQUEST);
    }

    public static byte[] multiSetupRequest(int channel) {
        return message(channel, MessageType.MULTI_SETUP_REQUEST);
    }

    public static byte[] performanceMapRequest(int channel) {
        return message(channel, MessageType.PERFORMANCE_MAP_REQUEST);
    }

    public static byte[] scalesRequest(int channel) {
        return message(channel, MessageType.MICRO_TUNE_SCALES_REQUEST);
    }

    public static byte[] allDataRequest(int channel) {
        return message(channel, MessageType.ALL_DATA_REQUEST);
    }

    /** Writes the patch edit buffer to a memory location (0-34). */
    public static byte[] patchWrite(int channel, WavestationBank bank, int number) {
        checkNumber(number, WavestationBank.PATCHES);
        return message(channel, MessageType.PATCH_WRITE, bank.sysexNumber(), number);
    }

    /** Writes the performance edit buffer to a memory location (0-49). */
    public static byte[] performanceWrite(int channel, WavestationBank bank, int number) {
        checkNumber(number, WavestationBank.PERFORMANCES);
        return message(channel, MessageType.PERFORMANCE_WRITE, bank.sysexNumber(), number);
    }

    /**
     * Parameter change (appendix 4, 2.1.17-19): function 41 for numbers 0-379, 42 for 380-406,
     * 43 from 407; number on two 7-bit bytes (LSB first); value as ASCII text (16 characters max,
     * space sent as 7F), null-terminated.
     */
    public static byte[] parameterChange(int channel, int number, String value) {
        if (number < 0 || number > 0x3FFF) {
            throw new IllegalArgumentException("parameter number out of range: " + number);
        }
        if (value.length() > MAX_VALUE_CHARS) {
            throw new IllegalArgumentException("value longer than " + MAX_VALUE_CHARS + " characters: " + value);
        }
        MessageType type = number >= 407 ? MessageType.PARAMETER_CHANGE_SR
                : number >= 380 ? MessageType.PARAMETER_CHANGE_EXPANDED : MessageType.PARAMETER_CHANGE;
        byte[] text = value.getBytes(StandardCharsets.US_ASCII);
        int[] body = new int[text.length + 3];
        body[0] = number & 0x7F;
        body[1] = (number >> 7) & 0x7F;
        for (int i = 0; i < text.length; i++) {
            body[2 + i] = text[i] == ' ' ? 0x7F : text[i];
        }
        body[body.length - 1] = 0;
        return message(channel, type, body);
    }

    /** Signed value text for a parameter change: "+12", "-4", "0". */
    public static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    /** Returns a copy of the message with the channel byte set (the channel is stored in dumps). */
    public static byte[] withChannel(byte[] message, int channel) {
        byte[] copy = message.clone();
        copy[2] = (byte) (0x30 | (channel & 0x0F));
        return copy;
    }

    private static void checkNumber(int number, int count) {
        if (number < 0 || number >= count) {
            throw new IllegalArgumentException("number must be 0-" + (count - 1) + ": " + number);
        }
    }

    // ------------------------------------------------------------------ decoding

    /** True if the message is a complete Wavestation SysEx (any channel). */
    public static boolean isWavestation(byte[] m) {
        return m != null && m.length >= 6 && (m[0] & 0xFF) == 0xF0 && m[1] == KORG_ID && (m[2] & 0xF0) == 0x30
                && m[3] == WAVESTATION_ID && (m[m.length - 1] & 0xFF) == 0xF7;
    }

    public static Optional<MessageType> type(byte[] m) {
        return isWavestation(m) ? MessageType.fromCode(m[TYPE_INDEX] & 0x7F) : Optional.empty();
    }

    public static boolean isType(byte[] m, MessageType type) {
        return type(m).map(t -> t == type).orElse(false);
    }

    public static int channel(byte[] m) {
        return m[2] & 0x0F;
    }

    /** Bank of a dump that carries one (patch, performance, banks). */
    public static Optional<WavestationBank> bank(byte[] m) {
        return type(m).filter(MessageType::hasBank).flatMap(t -> WavestationBank.fromSysex(m[5] & 0x7F));
    }

    /** Number of a single patch or performance dump, or -1. */
    public static int number(byte[] m) {
        return type(m).filter(MessageType::hasNumber).map(t -> m[6] & 0x7F).orElse(-1);
    }

    /** Checksum of a dump: sum of the nibbles, 7 bits. */
    public static int checksum(byte[] m, int dataOffset) {
        int sum = 0;
        for (int i = dataOffset; i < m.length - 2; i++) {
            sum += m[i] & 0xFF;
        }
        return sum & 0x7F;
    }

    /** True if the dump has the expected length (when fixed) and a correct checksum. */
    public static boolean isValidDump(byte[] m) {
        Optional<MessageType> t = type(m);
        if (t.isEmpty() || !t.get().isDump()) {
            return false;
        }
        int expected = t.get().expectedLength();
        if (expected > 0 && m.length != expected) {
            return false;
        }
        return (m[m.length - 2] & 0x7F) == checksum(m, t.get().dataOffset());
    }

    /** Decodes the nibble data of a dump into bytes. */
    public static byte[] decodeNibbles(byte[] m, int dataOffset) {
        int count = (m.length - 2 - dataOffset) / 2;
        byte[] data = new byte[count];
        for (int i = 0; i < count; i++) {
            int lo = m[dataOffset + 2 * i] & 0x0F;
            int hi = m[dataOffset + 2 * i + 1] & 0x0F;
            data[i] = (byte) ((hi << 4) | lo);
        }
        return data;
    }

    /** Encodes bytes as nibbles, low half first. */
    public static byte[] encodeNibbles(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 2);
        for (byte b : data) {
            out.write(b & 0x0F);
            out.write((b >> 4) & 0x0F);
        }
        return out.toByteArray();
    }

    /**
     * Short human-readable description, for the MIDI monitor, e.g.
     * "Patch RAM1:12 (861 bytes, checksum OK)" or, for a parameter change, "FILTER_MOD_CUTOFF = +72".
     */
    public static String describe(byte[] m) {
        Optional<MessageType> t = type(m);
        if (t.isEmpty()) {
            return isWavestation(m) ? "Wavestation message type " + Integer.toHexString(m[TYPE_INDEX] & 0x7F) : "";
        }
        MessageType type = t.get();
        StringBuilder sb = new StringBuilder(type.description());
        if (type == MessageType.PARAMETER_CHANGE || type == MessageType.PARAMETER_CHANGE_EXPANDED
                || type == MessageType.PARAMETER_CHANGE_SR) {
            int number = (m[5] & 0x7F) | ((m[6] & 0x7F) << 7);
            StringBuilder value = new StringBuilder();
            for (int i = 7; i < m.length - 1 && m[i] != 0; i++) {
                value.append(m[i] == 0x7F ? ' ' : (char) m[i]);
            }
            String name = number < Parameter.COUNT ? Parameter.of(number).officialName() : "#" + number;
            return name + " = " + value;
        }
        if (WITH_BANK.contains(type) && m.length > 6) {
            WavestationBank.fromSysex(m[5] & 0x7F).ifPresent(b -> sb.append(' ').append(b.label()));
            if (WITH_NUMBER.contains(type) && m.length > 7) {
                sb.append(':').append(String.format("%02d", m[6] & 0x7F));
            }
        }
        if (type.isDump()) {
            sb.append(" (").append(m.length).append(" bytes, checksum ")
                    .append(isValidDump(m) ? "OK" : "ERROR").append(')');
        }
        return sb.toString();
    }
}
