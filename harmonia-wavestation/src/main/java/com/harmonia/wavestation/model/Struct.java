package com.harmonia.wavestation.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Values of a structure described by a {@link Layout}: read from and written to the raw bytes
 * of a dump, field by field.
 * <p>
 * The raw bytes are kept, so that writing back an unmodified structure gives exactly the bytes
 * that were read (including unused or unknown fields).
 */
public final class Struct {

    private final Layout layout;
    private final byte[] bytes;

    /** Empty structure (all zeros). */
    public Struct(Layout layout) {
        this.layout = layout;
        this.bytes = new byte[layout.size()];
    }

    /** Structure read from {@code data} starting at {@code offset}. */
    public Struct(Layout layout, byte[] data, int offset) {
        this(layout);
        if (offset + layout.size() > data.length) {
            throw new IllegalArgumentException(layout.name() + ": not enough data (" + (data.length - offset)
                    + " bytes, " + layout.size() + " needed)");
        }
        System.arraycopy(data, offset, bytes, 0, layout.size());
    }

    public Layout layout() {
        return layout;
    }

    /** Copy of the raw bytes (big-endian, as in the dump after nibble decoding). */
    public byte[] toBytes() {
        return bytes.clone();
    }

    /** Writes the raw bytes into {@code out} at {@code offset}. */
    public void writeTo(byte[] out, int offset) {
        System.arraycopy(bytes, 0, out, offset, bytes.length);
    }

    /** Value of a numeric field, signed or not according to its type. */
    public int get(String field) {
        Layout.Field f = layout.field(field);
        if (f.type() == Layout.Type.CHARS) {
            throw new IllegalArgumentException(field + " is text");
        }
        int v = 0;
        for (int i = 0; i < f.size(); i++) {
            v = (v << 8) | (bytes[f.offset() + i] & 0xFF);
        }
        if (f.type().signed && f.size() < 4) {
            int shift = 32 - 8 * f.size();
            v = (v << shift) >> shift; // sign extension
        }
        return v;
    }

    /** Sets a numeric field; the value is truncated to the field size. */
    public void set(String field, int value) {
        Layout.Field f = layout.field(field);
        if (f.type() == Layout.Type.CHARS) {
            throw new IllegalArgumentException(field + " is text");
        }
        for (int i = f.size() - 1; i >= 0; i--) {
            bytes[f.offset() + i] = (byte) value;
            value >>= 8;
        }
    }

    /** Text field, without trailing spaces and nulls. */
    public String getString(String field) {
        Layout.Field f = layout.field(field);
        String s = new String(bytes, f.offset(), f.size(), StandardCharsets.ISO_8859_1);
        int end = s.indexOf('\0');
        if (end >= 0) {
            s = s.substring(0, end);
        }
        return s.stripTrailing();
    }

    /** Sets a text field, padded with spaces (longer text is cut). */
    public void setString(String field, String value) {
        Layout.Field f = layout.field(field);
        byte[] text = value.getBytes(StandardCharsets.ISO_8859_1);
        Arrays.fill(bytes, f.offset(), f.offset() + f.size(), (byte) ' ');
        System.arraycopy(text, 0, bytes, f.offset(), Math.min(text.length, f.size()));
    }

    /** A view on a nested structure: fields read and written with the given prefix. */
    public View view(String prefix) {
        return new View(prefix + ".");
    }

    /** Fields of a nested structure (a wave of a patch, a part of a performance). */
    public final class View {
        private final String prefix;

        private View(String prefix) {
            this.prefix = prefix;
        }

        public int get(String field) {
            return Struct.this.get(prefix + field);
        }

        public void set(String field, int value) {
            Struct.this.set(prefix + field, value);
        }

        public Struct struct() {
            return Struct.this;
        }
    }
}
