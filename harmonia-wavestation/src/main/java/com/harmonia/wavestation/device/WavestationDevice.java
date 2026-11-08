package com.harmonia.wavestation.device;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import com.harmonia.midi.DeviceIdentity;
import com.harmonia.midi.MidiBytes;
import com.harmonia.midi.MidiSendQueue;
import com.harmonia.midi.SysexRequests;
import com.harmonia.wavestation.sysex.MessageType;
import com.harmonia.wavestation.sysex.WavestationBank;
import com.harmonia.wavestation.sysex.WavestationSysex;

/**
 * Conversation with a Wavestation SR: requests with their expected answers, dumps with their
 * acknowledgements, real-time parameter changes.
 * <p>
 * It only needs a {@link MidiSendQueue} to send and a {@link SysexRequests} fed with the
 * received SysEx ({@link #onSysex}), so it can be tested without hardware. Every operation that
 * waits for an answer returns a {@link CompletableFuture} that fails with a
 * {@link java.util.concurrent.TimeoutException} if the device does not answer, or with a
 * {@link DeviceException} if it reports an error.
 */
public class WavestationDevice {

    /** Margin added to the transfer time when waiting for an answer. */
    static final long ANSWER_MARGIN_MS = 2000;
    /** Wait for answers of unknown length (system setup, multisets...). */
    static final long DEFAULT_TIMEOUT_MS = 5000;
    /** Wait for the identity: 17 inquiries are sent first, 40 ms apart. */
    static final long IDENTIFY_TIMEOUT_MS = 3000;
    /** Pause after a dump sent to the device, so that it can store it. */
    static final long PAUSE_AFTER_DUMP_MS = 300;

    private final MidiSendQueue queue;
    private final SysexRequests requests;
    private volatile int channel;

    /**
     * @param queue    output queue towards the device
     * @param requests matcher of the received SysEx (fed with {@link #onSysex})
     * @param channel  MIDI basic channel of the device, 0-15
     */
    public WavestationDevice(MidiSendQueue queue, SysexRequests requests, int channel) {
        this.queue = queue;
        this.requests = requests;
        setChannel(channel);
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        if (channel < 0 || channel > 15) {
            throw new IllegalArgumentException("channel must be 0-15: " + channel);
        }
        this.channel = channel;
    }

    /** To be called with every complete SysEx received from the device. */
    public boolean onSysex(byte[] sysex) {
        return requests.offer(sysex);
    }

    // ------------------------------------------------------------------ identification

    /**
     * Sends a Device Inquiry and waits for a Wavestation SR answer, which gives the device
     * channel: useful to configure the connection automatically. The manual documents the inquiry
     * with a channel number, so it is sent to "all" (7F) and to each of the 16 channels.
     */
    public CompletableFuture<DeviceIdentity> identify() {
        CompletableFuture<byte[]> answer = requests.expect(m -> DeviceIdentity.parse(m)
                .filter(id -> id.manufacturer() == WavestationSysex.KORG_ID && id.family() == WavestationSysex.FAMILY)
                .isPresent(), IDENTIFY_TIMEOUT_MS);
        queue.send(DeviceIdentity.request());
        for (int ch = 0; ch < 16; ch++) {
            queue.send(DeviceIdentity.request(ch));
        }
        return answer.thenApply(m -> DeviceIdentity.parse(m).orElseThrow());
    }

    // ------------------------------------------------------------------ requests

    public CompletableFuture<byte[]> requestPatch(WavestationBank bank, int number) {
        return request(WavestationSysex.patchRequest(channel, bank, number),
                dump(MessageType.PATCH_DUMP, bank, number));
    }

    public CompletableFuture<byte[]> requestPerformance(WavestationBank bank, int number) {
        return request(WavestationSysex.performanceRequest(channel, bank, number),
                dump(MessageType.PERFORMANCE_DUMP, bank, number));
    }

    public CompletableFuture<byte[]> requestPatchBank(WavestationBank bank) {
        return request(WavestationSysex.patchBankRequest(channel, bank), dump(MessageType.PATCH_BANK_DUMP, bank, -1));
    }

    public CompletableFuture<byte[]> requestPerformanceBank(WavestationBank bank) {
        return request(WavestationSysex.performanceBankRequest(channel, bank),
                dump(MessageType.PERFORMANCE_BANK_DUMP, bank, -1));
    }

    public CompletableFuture<byte[]> requestWaveSequenceBank(WavestationBank bank) {
        return request(WavestationSysex.waveSequenceBankRequest(channel, bank),
                dump(MessageType.WAVE_SEQUENCE_BANK_DUMP, bank, -1));
    }

    public CompletableFuture<byte[]> requestSystemSetup() {
        return request(WavestationSysex.systemSetupRequest(channel), dump(MessageType.SYSTEM_SETUP_DUMP, null, -1));
    }

    /** Recognises a dump of the given type, bank and number (null / -1 = any) on our channel. */
    private Predicate<byte[]> dump(MessageType type, WavestationBank bank, int number) {
        return m -> WavestationSysex.isType(m, type) && WavestationSysex.channel(m) == channel
                && (bank == null || WavestationSysex.bank(m).map(b -> b == bank).orElse(false))
                && (number < 0 || WavestationSysex.number(m) == number);
    }

    private CompletableFuture<byte[]> request(byte[] request, Predicate<byte[]> answer) {
        MessageType type = expectedAnswer(request);
        CompletableFuture<byte[]> future = requests.expect(answer, timeoutFor(type));
        queue.send(request);
        return future.thenApply(dump -> {
            if (!WavestationSysex.isValidDump(dump)) {
                throw new DeviceException("invalid dump received: " + WavestationSysex.describe(dump));
            }
            return dump;
        });
    }

    private static MessageType expectedAnswer(byte[] request) {
        return switch (WavestationSysex.type(request).orElseThrow()) {
            case PATCH_REQUEST -> MessageType.PATCH_DUMP;
            case PERFORMANCE_REQUEST -> MessageType.PERFORMANCE_DUMP;
            case PATCH_BANK_REQUEST -> MessageType.PATCH_BANK_DUMP;
            case PERFORMANCE_BANK_REQUEST -> MessageType.PERFORMANCE_BANK_DUMP;
            case WAVE_SEQUENCE_BANK_REQUEST -> MessageType.WAVE_SEQUENCE_BANK_DUMP;
            default -> MessageType.SYSTEM_SETUP_DUMP;
        };
    }

    /**
     * Time to wait for an answer: twice the transfer time of the expected dump plus a margin
     * (a patch bank needs about 9.5 s at MIDI speed).
     */
    static long timeoutFor(MessageType answer) {
        int length = answer.expectedLength();
        return length > 0 ? 2 * MidiBytes.transferMillis(length) + ANSWER_MARGIN_MS : DEFAULT_TIMEOUT_MS;
    }

    // ------------------------------------------------------------------ sending data

    /**
     * Sends a dump (single patch or performance into the edit buffer, or a whole bank) and waits
     * for the device acknowledgement: Data Load Complete, or Data Load Error (bad checksum). The
     * channel byte is rewritten with the device channel, since the channel is stored in the
     * dumps and the device ignores other channels.
     */
    public CompletableFuture<Void> sendDump(byte[] dump) {
        if (!WavestationSysex.isValidDump(dump)) {
            return CompletableFuture.failedFuture(new DeviceException("not a valid Wavestation dump"));
        }
        byte[] message = WavestationSysex.withChannel(dump, channel);
        CompletableFuture<byte[]> ack = requests.expect(status(MessageType.DATA_LOAD_COMPLETE, MessageType.DATA_LOAD_ERROR),
                2 * MidiBytes.transferMillis(message.length) + ANSWER_MARGIN_MS);
        queue.send(message, PAUSE_AFTER_DUMP_MS);
        return ack.thenAccept(m -> {
            if (WavestationSysex.isType(m, MessageType.DATA_LOAD_ERROR)) {
                throw new DeviceException("the device reported a checksum error (Data Load Error)");
            }
        });
    }

    /** Writes the patch edit buffer to memory and waits for Write Complete / Write Error. */
    public CompletableFuture<Void> writePatch(WavestationBank bank, int number) {
        return write(WavestationSysex.patchWrite(channel, bank, number), bank);
    }

    /** Writes the performance edit buffer to memory and waits for Write Complete / Write Error. */
    public CompletableFuture<Void> writePerformance(WavestationBank bank, int number) {
        return write(WavestationSysex.performanceWrite(channel, bank, number), bank);
    }

    private CompletableFuture<Void> write(byte[] command, WavestationBank bank) {
        if (!bank.isWritable()) {
            return CompletableFuture.failedFuture(new DeviceException(bank.label() + " is a ROM bank"));
        }
        CompletableFuture<byte[]> ack = requests.expect(status(MessageType.WRITE_COMPLETE, MessageType.WRITE_ERROR),
                DEFAULT_TIMEOUT_MS);
        queue.send(command, PAUSE_AFTER_DUMP_MS);
        return ack.thenAccept(m -> {
            if (WavestationSysex.isType(m, MessageType.WRITE_ERROR)) {
                throw new DeviceException("the device refused to write (memory protect on?)");
            }
        });
    }

    private Predicate<byte[]> status(MessageType ok, MessageType error) {
        return m -> WavestationSysex.channel(m) == channel
                && (WavestationSysex.isType(m, ok) || WavestationSysex.isType(m, error));
    }

    // ------------------------------------------------------------------ real time

    /**
     * Changes a parameter of the edit buffer. Successive values of the same parameter waiting in
     * the queue are merged: only the last one is sent.
     */
    public void setParameter(int number, String value) {
        queue.sendCoalesced("param:" + number, WavestationSysex.parameterChange(channel, number, value));
    }

    public void setParameter(int number, int value) {
        setParameter(number, WavestationSysex.signed(value));
    }

    /** Error reported by the device or invalid data received. */
    public static class DeviceException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DeviceException(String message) {
            super(message);
        }
    }
}
