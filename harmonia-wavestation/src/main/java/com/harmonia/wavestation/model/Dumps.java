package com.harmonia.wavestation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.harmonia.wavestation.sysex.MessageType;
import com.harmonia.wavestation.sysex.WavestationBank;
import com.harmonia.wavestation.sysex.WavestationSysex;

/**
 * Conversion between SysEx dumps and the model: patches, performances, banks, wave sequences.
 * <p>
 * Decoding checks the message type, length and checksum, then converts the nibbles to bytes.
 * Encoding does the reverse. Decoding then encoding an unmodified dump gives the same bytes.
 */
public final class Dumps {

    private Dumps() {
    }

    // ------------------------------------------------------------------ decoding

    public static Patch decodePatch(byte[] dump) {
        return new Patch(data(dump, MessageType.PATCH_DUMP));
    }

    public static Performance decodePerformance(byte[] dump) {
        return new Performance(data(dump, MessageType.PERFORMANCE_DUMP));
    }

    /** The 35 patches of a patch bank dump. */
    public static List<Patch> decodePatchBank(byte[] dump) {
        return split(data(dump, MessageType.PATCH_BANK_DUMP), WavestationBank.PATCHES, Layouts.PATCH,
                Patch::new);
    }

    /** The 50 performances of a performance bank dump. */
    public static List<Performance> decodePerformanceBank(byte[] dump) {
        return split(data(dump, MessageType.PERFORMANCE_BANK_DUMP), WavestationBank.PERFORMANCES,
                Layouts.PERFORMANCE, Performance::new);
    }

    public static WaveSequenceBank decodeWaveSequenceBank(byte[] dump) {
        return new WaveSequenceBank(data(dump, MessageType.WAVE_SEQUENCE_BANK_DUMP));
    }

    /** Checks the dump and returns its decoded data bytes. */
    static byte[] data(byte[] dump, MessageType expected) {
        if (!WavestationSysex.isType(dump, expected)) {
            throw new IllegalArgumentException("expected " + expected.description() + ", got "
                    + WavestationSysex.type(dump).map(MessageType::description).orElse("an unknown message"));
        }
        if (!WavestationSysex.isValidDump(dump)) {
            throw new IllegalArgumentException("invalid " + expected.description() + " dump (length or checksum)");
        }
        return WavestationSysex.decodeNibbles(dump, expected.dataOffset());
    }

    private static <T> List<T> split(byte[] data, int count, Layout layout, Function<Struct, T> factory) {
        List<T> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(factory.apply(new Struct(layout, data, i * layout.size())));
        }
        return items;
    }

    // ------------------------------------------------------------------ encoding

    public static byte[] encodePatch(Patch patch, int channel, WavestationBank bank, int number) {
        return encode(MessageType.PATCH_DUMP, channel, new int[] { bank.sysexNumber(), number }, patch.toBytes());
    }

    public static byte[] encodePerformance(Performance performance, int channel, WavestationBank bank, int number) {
        return encode(MessageType.PERFORMANCE_DUMP, channel, new int[] { bank.sysexNumber(), number },
                performance.toBytes());
    }

    public static byte[] encodePatchBank(List<Patch> patches, int channel, WavestationBank bank) {
        return encode(MessageType.PATCH_BANK_DUMP, channel, new int[] { bank.sysexNumber() },
                concat(patches.stream().map(Patch::toBytes).toList(), WavestationBank.PATCHES));
    }

    public static byte[] encodePerformanceBank(List<Performance> performances, int channel, WavestationBank bank) {
        return encode(MessageType.PERFORMANCE_BANK_DUMP, channel, new int[] { bank.sysexNumber() },
                concat(performances.stream().map(Performance::toBytes).toList(), WavestationBank.PERFORMANCES));
    }

    public static byte[] encodeWaveSequenceBank(WaveSequenceBank sequences, int channel, WavestationBank bank) {
        return encode(MessageType.WAVE_SEQUENCE_BANK_DUMP, channel, new int[] { bank.sysexNumber() },
                sequences.toBytes());
    }

    private static byte[] concat(List<byte[]> items, int expectedCount) {
        if (items.size() != expectedCount) {
            throw new IllegalArgumentException("a bank holds " + expectedCount + " items, got " + items.size());
        }
        int size = items.stream().mapToInt(b -> b.length).sum();
        byte[] all = new byte[size];
        int pos = 0;
        for (byte[] b : items) {
            System.arraycopy(b, 0, all, pos, b.length);
            pos += b.length;
        }
        return all;
    }

    /** Header, bank (and number), nibbles, checksum, F7. */
    static byte[] encode(MessageType type, int channel, int[] headerBytes, byte[] data) {
        byte[] nibbles = WavestationSysex.encodeNibbles(data);
        byte[] header = WavestationSysex.message(channel, type, headerBytes);   // F0 42 3n 28 type ... F7
        byte[] dump = new byte[type.dataOffset() + nibbles.length + 2];
        System.arraycopy(header, 0, dump, 0, type.dataOffset());
        System.arraycopy(nibbles, 0, dump, type.dataOffset(), nibbles.length);
        dump[dump.length - 2] = (byte) WavestationSysex.checksum(dump, type.dataOffset());
        dump[dump.length - 1] = (byte) 0xF7;
        return dump;
    }
}
