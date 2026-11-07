package com.harmonia.wavestation.sysex;

import java.util.Optional;

/**
 * Wavestation SysEx message types (Reference Guide, appendix 4, 1.1 "Message Type Codes").
 * <p>
 * For data messages, {@link #dataOffset()} is the index of the first nibble after the header
 * (and bank / number bytes), and {@link #expectedLength()} the full message size when it is
 * fixed (0 otherwise).
 */
public enum MessageType {

    // Requests (received by the device)
    MULTI_SETUP_REQUEST(0x06, "Multi mode setup request"),
    PERFORMANCE_MAP_REQUEST(0x07, "Performance map request"),
    MICRO_TUNE_SCALES_REQUEST(0x08, "Micro tune scales request"),
    WAVE_SEQUENCE_BANK_REQUEST(0x0C, "Wave sequence bank request"),
    SYSTEM_SETUP_REQUEST(0x0E, "System setup request"),
    ALL_DATA_REQUEST(0x0F, "All data request"),
    PATCH_REQUEST(0x10, "Patch request"),
    PERFORMANCE_REQUEST(0x19, "Performance request"),
    PATCH_BANK_REQUEST(0x1C, "Patch bank request"),
    PERFORMANCE_BANK_REQUEST(0x1D, "Performance bank request"),

    // Commands
    PATCH_WRITE(0x11, "Patch write"),
    PERFORMANCE_WRITE(0x1A, "Performance write"),
    MULTI_SETUP_SELECT(0x5B, "Multiset select"),
    MULTI_SETUP_SELECT_SR(0x63, "Multiset select (SR)"),

    // Status messages (sent by the device)
    WRITE_COMPLETE(0x21, "Write complete"),
    WRITE_ERROR(0x22, "Write error"),
    DATA_LOAD_COMPLETE(0x23, "Data load complete"),
    DATA_LOAD_ERROR(0x24, "Data load error (checksum)"),

    // Parameter changes
    PARAMETER_CHANGE(0x41, "Parameter change"),
    PARAMETER_CHANGE_EXPANDED(0x42, "Parameter change (expanded)"),
    PARAMETER_CHANGE_SR(0x43, "Parameter change (SR expanded)"),

    // Data dumps
    PATCH_DUMP(0x40, "Patch", 7, 861),
    PERFORMANCE_DUMP(0x49, "Performance", 7, 371),
    PATCH_BANK_DUMP(0x4C, "Patch bank", 6, 29828),
    PERFORMANCE_BANK_DUMP(0x4D, "Performance bank", 6, 18108),
    ALL_DATA_DUMP(0x50, "All data", 5, 0),
    SYSTEM_SETUP_DUMP(0x51, "System setup", 5, 0),
    WAVE_SEQUENCE_BANK_DUMP(0x54, "Wave sequence bank", 6, 17576),
    MULTI_SETUP_DUMP(0x55, "Multi mode setup", 5, 0),
    MICRO_TUNE_SCALES_DUMP(0x5A, "Micro tune scales", 5, 0),
    SYSTEM_SETUP_EXPANDED_DUMP(0x5C, "System setup (expanded)", 5, 0),
    PERFORMANCE_MAP_DUMP(0x5D, "Performance map", 5, 0),
    MULTI_SETUP_EXPANDED_DUMP(0x5E, "Multi mode setup (expanded)", 5, 0),
    PERFORMANCE_MAP_EXPANDED_DUMP(0x5F, "Performance map (expanded)", 5, 0),
    PERFORMANCE_MAP_SR_DUMP(0x60, "Performance map (SR)", 5, 0),
    SYSTEM_SETUP_SR_DUMP(0x61, "System setup (SR)", 5, 0),
    MULTI_SETUP_SR_DUMP(0x62, "Multi mode setup (SR)", 5, 0);

    private final int code;
    private final String description;
    private final int dataOffset;
    private final int expectedLength;

    MessageType(int code, String description) {
        this(code, description, -1, 0);
    }

    MessageType(int code, String description, int dataOffset, int expectedLength) {
        this.code = code;
        this.description = description;
        this.dataOffset = dataOffset;
        this.expectedLength = expectedLength;
    }

    public int code() {
        return code;
    }

    public String description() {
        return description;
    }

    /** True for messages carrying nibble data and a checksum. */
    public boolean isDump() {
        return dataOffset > 0;
    }

    /** Index of the first data nibble, or -1 if the message has no data. */
    public int dataOffset() {
        return dataOffset;
    }

    /** Full message length in bytes when fixed, else 0. */
    public int expectedLength() {
        return expectedLength;
    }

    /** True if bytes 5 (and 6) of the message hold a bank (and a number). */
    public boolean hasBank() {
        return dataOffset == 6 || dataOffset == 7;
    }

    public boolean hasNumber() {
        return dataOffset == 7;
    }

    public static Optional<MessageType> fromCode(int code) {
        for (MessageType t : values()) {
            if (t.code == code) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
