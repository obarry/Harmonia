package com.harmonia.wavestation.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Byte layout of a Wavestation data structure, as described in C in the Reference Guide
 * (appendix 4, section 5): an ordered list of named fields with their type.
 * <p>
 * Nested structures are flattened with a prefix: the four {@code indiv} blocks of a patch become
 * {@code "A.Wave_Coarse"}, {@code "B.Wave_Coarse"}... Field names are those of the manual, so the
 * code can be checked against it line by line. Multi-byte values are big-endian (the
 * Wavestation runs on a Motorola 68000).
 */
public final class Layout {

    /** Field types of the C structures. */
    public enum Type {
        /** {@code char[n]}: text. */
        CHARS(0, false),
        /** {@code byte}: signed 8 bits. */
        BYTE(1, true),
        /** {@code ubyte}: unsigned 8 bits. */
        UBYTE(1, false),
        /** {@code word}: signed 16 bits. */
        WORD(2, true),
        /** {@code uword}: unsigned 16 bits. */
        UWORD(2, false),
        /** {@code long}: signed 32 bits. */
        LONG(4, true);

        final int size;
        final boolean signed;

        Type(int size, boolean signed) {
            this.size = size;
            this.signed = signed;
        }
    }

    /** A field: name, type, offset in the structure and size in bytes. */
    public record Field(String name, Type type, int offset, int size) {
    }

    private final String name;
    private final List<Field> fields;
    private final Map<String, Field> byName;
    private final int size;

    private Layout(String name, List<Field> fields) {
        this.name = name;
        this.fields = Collections.unmodifiableList(fields);
        Map<String, Field> map = new LinkedHashMap<>();
        for (Field f : fields) {
            if (map.put(f.name(), f) != null) {
                throw new IllegalArgumentException("duplicate field " + f.name() + " in " + name);
            }
        }
        this.byName = map;
        Field last = fields.get(fields.size() - 1);
        this.size = last.offset() + last.size();
    }

    public String name() {
        return name;
    }

    /** Size in bytes (before nibble encoding). */
    public int size() {
        return size;
    }

    public List<Field> fields() {
        return fields;
    }

    public boolean has(String field) {
        return byName.containsKey(field);
    }

    public Field field(String field) {
        Field f = byName.get(field);
        if (f == null) {
            throw new IllegalArgumentException("no field " + field + " in " + name);
        }
        return f;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Builds a layout field by field, in the order of the C structure. */
    public static final class Builder {
        private final String name;
        private final List<Field> fields = new ArrayList<>();
        private int offset;

        private Builder(String name) {
            this.name = name;
        }

        private Builder add(String field, Type type, int size) {
            fields.add(new Field(field, type, offset, size));
            offset += size;
            return this;
        }

        public Builder chars(String field, int length) {
            return add(field, Type.CHARS, length);
        }

        public Builder bytes(String... names) {
            for (String n : names) {
                add(n, Type.BYTE, 1);
            }
            return this;
        }

        public Builder ubytes(String... names) {
            for (String n : names) {
                add(n, Type.UBYTE, 1);
            }
            return this;
        }

        public Builder words(String... names) {
            for (String n : names) {
                add(n, Type.WORD, 2);
            }
            return this;
        }

        public Builder uwords(String... names) {
            for (String n : names) {
                add(n, Type.UWORD, 2);
            }
            return this;
        }

        public Builder longs(String... names) {
            for (String n : names) {
                add(n, Type.LONG, 4);
            }
            return this;
        }

        /** An array of signed bytes, named {@code field[0]}, {@code field[1]}... */
        public Builder byteArray(String field, int count) {
            for (int i = 0; i < count; i++) {
                add(field + "[" + i + "]", Type.BYTE, 1);
            }
            return this;
        }

        /** Appends a nested structure, its fields prefixed with {@code prefix + "."}. */
        public Builder struct(String prefix, Layout nested) {
            for (Field f : nested.fields()) {
                add(prefix + "." + f.name(), f.type(), f.size());
            }
            return this;
        }

        public Layout build() {
            return new Layout(name, new ArrayList<>(fields));
        }
    }
}
