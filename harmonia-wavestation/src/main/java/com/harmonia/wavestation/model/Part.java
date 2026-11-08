package com.harmonia.wavestation.model;

import com.harmonia.wavestation.sysex.WavestationBank;

/**
 * One of the 8 parts of a performance: a patch with its level, output, transposition, key and
 * velocity zones, and playing mode. The {@code part} structure of appendix 4, 5.1.
 */
public final class Part {

    /** Who plays the part (Part_Mode bits 5-4). */
    public enum PlayMode {
        RESERVED, LOCAL, MIDI, BOTH
    }

    /** Voice assignment (Part_Mode bits 3-2). */
    public enum VoiceMode {
        RESERVED, POLY, UNISON_RETRIGGER, UNISON_LEGATO
    }

    /** Note priority in unison modes (Part_Mode bits 1-0). */
    public enum NotePriority {
        LOW, HIGH, LAST, RESERVED
    }

    private final Struct.View fields;
    private final int number;

    Part(Struct.View fields, int number) {
        this.fields = fields;
        this.number = number;
    }

    /** Part number 1-8. */
    public int getNumber() {
        return number;
    }

    /** Raw access to any field of the part structure, e.g. {@code get("Lo_Key")}. */
    public int get(String field) {
        return fields.get(field);
    }

    public void set(String field, int value) {
        fields.set(field, value);
    }

    /** An unused part has no valid bank (negative bank number) or no patch (number -1). */
    public boolean isUsed() {
        return getBank() != null && getPatchNumber() >= 0;
    }

    /** Bank of the patch: Bank_Num expanded by Part_Mode bits 7-6. */
    public WavestationBank getBank() {
        int mode = fields.get("Part_Mode");
        return BankExpansion.decode(fields.get("Bank_Num"), (mode >> 6) & 0x03);
    }

    public int getPatchNumber() {
        return fields.get("Patch_Num");
    }

    /** Sets the patch played by the part (bank number and expansion bits). */
    public void setPatch(WavestationBank bank, int patchNumber) {
        fields.set("Bank_Num", BankExpansion.bankNumber(bank));
        int mode = fields.get("Part_Mode") & 0x3F;
        fields.set("Part_Mode", mode | BankExpansion.expansion(bank) << 6);
        fields.set("Patch_Num", patchNumber);
    }

    public int getLevel() {
        return fields.get("Level");
    }

    public PlayMode getPlayMode() {
        return PlayMode.values()[(fields.get("Part_Mode") >> 4) & 0x03];
    }

    public VoiceMode getVoiceMode() {
        return VoiceMode.values()[(fields.get("Part_Mode") >> 2) & 0x03];
    }

    public NotePriority getNotePriority() {
        return NotePriority.values()[fields.get("Part_Mode") & 0x03];
    }

    public int getLowKey() {
        return fields.get("Lo_Key");
    }

    public int getHighKey() {
        return fields.get("Hi_Key");
    }

    public int getLowVelocity() {
        return fields.get("Lo_Vel");
    }

    public int getHighVelocity() {
        return fields.get("Hi_Vel");
    }

    /** Transposition in semitones. */
    public int getTranspose() {
        return fields.get("Trans");
    }

    /** Detune in cents. */
    public int getDetune() {
        return fields.get("Detune");
    }

    /** Note-on delay in milliseconds. */
    public int getDelay() {
        return fields.get("Delay");
    }

    /** "RAM1:12", or "-" for an unused part. */
    public String patchReference() {
        return isUsed() ? getBank().label() + ":" + String.format("%02d", getPatchNumber()) : "-";
    }
}
