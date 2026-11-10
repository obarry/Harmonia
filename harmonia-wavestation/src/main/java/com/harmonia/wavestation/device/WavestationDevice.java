package com.harmonia.wavestation.device;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import com.harmonia.midi.DeviceIdentity;
import com.harmonia.midi.MidiBytes;
import com.harmonia.midi.MidiSendQueue;
import com.harmonia.midi.SysexRequests;
import com.harmonia.wavestation.param.Parameter;
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
    /** Part selected on the device with CURRENT_PART (1-8), 0 if unknown. */
    private int currentPart;
    /** Wave selected on the device with CURRENT_WAVE (0-3), -1 if unknown. */
    private int currentWave = -1;
    /**
     * Incremented each time the context changes on the device (part, wave, performance, dump):
     * waiting values of a parameter are only merged within the same context, never across a
     * change, which would send them in the wrong order.
     */
    private int epoch;

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
    public synchronized CompletableFuture<Void> sendDump(byte[] dump) {
        if (!WavestationSysex.isValidDump(dump)) {
            return CompletableFuture.failedFuture(new DeviceException("not a valid Wavestation dump"));
        }
        byte[] message = WavestationSysex.withChannel(dump, channel);
        forgetEditContext();
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

    /** Pause after a program change: the device needs time to load the performance. */
    static final long PAUSE_AFTER_PROGRAM_CHANGE_MS = 300;

    /**
     * Makes the device play a performance: Bank Select (controller 0 = 0, controller 32 = MIDI
     * bank) then Program Change, on the device channel. The SR must receive program changes and
     * bank selects (MIDI/GLOBAL › MIDI RECEIVE).
     */
    public synchronized void selectPerformance(WavestationBank bank, int number) {
        if (number < 0 || number >= WavestationBank.PERFORMANCES) {
            throw new IllegalArgumentException("performance must be 0-49: " + number);
        }
        int ch = channel;
        forgetEditContext();
        queue.send(new byte[] { (byte) (0xB0 | ch), 0x00, 0x00 });
        queue.send(new byte[] { (byte) (0xB0 | ch), 0x20, (byte) bank.midiBank() });
        queue.send(new byte[] { (byte) (0xC0 | ch), (byte) (bank.programOffset() + number) },
                PAUSE_AFTER_PROGRAM_CHANGE_MS);
    }

    /**
     * Changes a parameter of the edit buffer, by number, without selecting any context. Successive
     * values of the same parameter waiting in the queue are merged: only the last one is sent.
     */
    public synchronized void setParameter(int number, String value) {
        queue.sendCoalesced("param:" + number + "#" + epoch, WavestationSysex.parameterChange(channel, number, value));
    }

    public void setParameter(int number, int value) {
        setParameter(number, WavestationSysex.signed(value));
    }

    /** Changes a global or performance parameter ({@link Parameter.Scope#NONE}). */
    public void setParameter(Parameter parameter, int value) {
        requireScope(parameter, Parameter.Scope.NONE);
        setParameter(parameter.number(), value);
    }

    /** Changes the name of the performance in the edit buffer (PROG_NAME). */
    public void setPerformanceName(String name) {
        setParameter(Parameter.PROG_NAME.number(), name);
    }

    /**
     * Changes a parameter of a part, or of the patch it plays ({@link Parameter.Scope#PART}):
     * CURRENT_PART is sent first when another part is selected on the device.
     *
     * @param part part 1-8
     */
    public void setPartParameter(int part, Parameter parameter, int value) {
        setPartParameter(part, parameter, WavestationSysex.signed(value));
    }

    /** Same as {@link #setPartParameter(int, Parameter, int)}, with the value as text. */
    public synchronized void setPartParameter(int part, Parameter parameter, String value) {
        requireScope(parameter, Parameter.Scope.PART);
        selectPart(part);
        queue.sendCoalesced("param:" + parameter.number() + "#" + epoch,
                WavestationSysex.parameterChange(channel, parameter.number(), value));
    }

    /**
     * Changes a parameter of one wave of the patch played by a part ({@link Parameter.Scope#WAVE}):
     * CURRENT_PART and CURRENT_WAVE are sent first when needed.
     *
     * @param part part 1-8
     * @param wave wave 0-3 (A-D)
     */
    public synchronized void setWaveParameter(int part, int wave, Parameter parameter, int value) {
        requireScope(parameter, Parameter.Scope.WAVE);
        selectPart(part);
        selectWave(wave);
        queue.sendCoalesced("param:" + parameter.number() + "#" + epoch,
                WavestationSysex.parameterChange(channel, parameter.number(), WavestationSysex.signed(value)));
    }

    /**
     * Selects the part edited on the device (CURRENT_PART), if it is not already selected.
     * Values 1-8, as shown on the device (to be confirmed with test T11).
     */
    public synchronized void selectPart(int part) {
        if (part < 1 || part > 8) {
            throw new IllegalArgumentException("part must be 1-8: " + part);
        }
        if (part != currentPart) {
            queue.send(WavestationSysex.parameterChange(channel, Parameter.CURRENT_PART.number(),
                    WavestationSysex.signed(part)));
            currentPart = part;
            currentWave = -1;
            epoch++;
        }
    }

    /** Selects the wave edited on the device (CURRENT_WAVE, 0-3 for A-D), if needed. */
    public synchronized void selectWave(int wave) {
        if (wave < 0 || wave > 3) {
            throw new IllegalArgumentException("wave must be 0-3: " + wave);
        }
        if (wave != currentWave) {
            queue.send(WavestationSysex.parameterChange(channel, Parameter.CURRENT_WAVE.number(),
                    WavestationSysex.signed(wave)));
            currentWave = wave;
            epoch++;
        }
    }

    /**
     * Forgets the part and wave selected on the device: they are sent again before the next
     * parameter change. Called when the edit buffer is replaced (performance change, dump), and to
     * be called when the device itself changes them (front panel).
     */
    public synchronized void forgetEditContext() {
        currentPart = 0;
        currentWave = -1;
        epoch++;
    }

    private static void requireScope(Parameter parameter, Parameter.Scope scope) {
        if (parameter.scope() != scope) {
            throw new IllegalArgumentException(parameter + " has scope " + parameter.scope() + ", not " + scope);
        }
        if (!parameter.isEditable()) {
            throw new IllegalArgumentException(parameter + " cannot be changed (" + parameter.kind() + ")");
        }
    }

    /** Error reported by the device or invalid data received. */
    public static class DeviceException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DeviceException(String message) {
            super(message);
        }
    }
}
