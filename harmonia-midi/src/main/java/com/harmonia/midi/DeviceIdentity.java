package com.harmonia.midi;

import java.util.Optional;

/**
 * Standard MIDI "Device Inquiry" (universal non-real-time SysEx, sub-ID 06).
 * <p>
 * Request: {@code F0 7E <channel> 06 01 F7} (channel 7F = all devices).<br>
 * Reply: {@code F0 7E <channel> 06 02 <manufacturer> <family LSB MSB> <member LSB MSB>
 * <version: 4 bytes> F7}. The manufacturer ID is one byte, or three bytes starting with 00.
 * <p>
 * The Wavestation SR answers with manufacturer 42 (Korg), family 28, member 02
 * (Reference Guide, appendix 4, 3.2).
 *
 * @param channel      channel of the answering device
 * @param manufacturer manufacturer ID (one byte, or 0x00xxyy for three-byte IDs)
 * @param family       device family code
 * @param member       family member code
 * @param version      the four software version bytes
 */
public record DeviceIdentity(int channel, int manufacturer, int family, int member, byte[] version) {

    public static final int ALL_CHANNELS = 0x7F;

    /** Inquiry request sent to every device. */
    public static byte[] request() {
        return request(ALL_CHANNELS);
    }

    /** Inquiry request for a given channel (0-15) or {@link #ALL_CHANNELS}. */
    public static byte[] request(int channel) {
        return new byte[] { (byte) 0xF0, 0x7E, (byte) (channel & 0x7F), 0x06, 0x01, (byte) 0xF7 };
    }

    /** True if the message is a Device Inquiry reply. */
    public static boolean isReply(byte[] m) {
        return m != null && m.length >= 15 && (m[0] & 0xFF) == 0xF0 && m[1] == 0x7E && m[3] == 0x06 && m[4] == 0x02;
    }

    /** Parses a Device Inquiry reply. */
    public static Optional<DeviceIdentity> parse(byte[] m) {
        if (!isReply(m)) {
            return Optional.empty();
        }
        int i = 5;
        int manufacturer = m[i] & 0x7F;
        if (manufacturer == 0) {                           // three-byte manufacturer ID
            manufacturer = ((m[i + 1] & 0x7F) << 8) | (m[i + 2] & 0x7F);
            i += 3;
        } else {
            i += 1;
        }
        if (m.length < i + 9) {
            return Optional.empty();
        }
        int family = (m[i] & 0x7F) | ((m[i + 1] & 0x7F) << 7);
        int member = (m[i + 2] & 0x7F) | ((m[i + 3] & 0x7F) << 7);
        byte[] version = { m[i + 4], m[i + 5], m[i + 6], m[i + 7] };
        return Optional.of(new DeviceIdentity(m[2] & 0x7F, manufacturer, family, member, version));
    }

    /** Version as "major.minor", assuming the Korg layout (minor LSB, minor MSB, major LSB, major MSB). */
    public String versionText() {
        int minor = (version[0] & 0x7F) | ((version[1] & 0x7F) << 7);
        int major = (version[2] & 0x7F) | ((version[3] & 0x7F) << 7);
        return major + "." + String.format("%02d", minor);
    }
}
