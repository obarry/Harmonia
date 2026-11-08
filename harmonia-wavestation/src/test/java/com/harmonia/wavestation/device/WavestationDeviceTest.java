package com.harmonia.wavestation.device;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.harmonia.midi.DeviceIdentity;
import com.harmonia.midi.MidiSendQueue;
import com.harmonia.midi.SysexRequests;
import com.harmonia.wavestation.sysex.MessageType;
import com.harmonia.wavestation.sysex.TestDumps;
import com.harmonia.wavestation.sysex.WavestationBank;
import com.harmonia.wavestation.sysex.WavestationSysex;

/** Talks to a simulated device: the test plays the role of the Wavestation SR. */
class WavestationDeviceTest {

    private final List<byte[]> sent = Collections.synchronizedList(new ArrayList<>());
    private final MidiSendQueue queue = new MidiSendQueue(sent::add, 0);
    private final SysexRequests requests = new SysexRequests();
    private final WavestationDevice device = new WavestationDevice(queue, requests, 0);

    @AfterEach
    void close() {
        queue.close();
    }

    private static byte[] status(MessageType type) {
        return WavestationSysex.message(0, type);
    }

    @Test
    void requestPatchSendsTheRequestAndReturnsTheDump() throws Exception {
        CompletableFuture<byte[]> f = device.requestPatch(WavestationBank.RAM1, 12);
        assertTrue(queue.awaitEmpty(1000));
        assertArrayEquals(WavestationSysex.patchRequest(0, WavestationBank.RAM1, 12), sent.get(0));

        device.onSysex(TestDumps.patch(0, WavestationBank.RAM1, 11)); // not the one we asked for
        byte[] answer = TestDumps.patch(0, WavestationBank.RAM1, 12);
        assertTrue(device.onSysex(answer));
        assertArrayEquals(answer, f.get(1, TimeUnit.SECONDS));
    }

    @Test
    void corruptedAnswerFailsTheRequest() {
        CompletableFuture<byte[]> f = device.requestPatch(WavestationBank.RAM2, 0);
        byte[] answer = TestDumps.patch(0, WavestationBank.RAM2, 0);
        answer[50] ^= 0x01;
        device.onSysex(answer);
        ExecutionException e = assertThrows(ExecutionException.class, () -> f.get(1, TimeUnit.SECONDS));
        assertInstanceOf(WavestationDevice.DeviceException.class, e.getCause());
    }

    @Test
    void sendDumpRewritesTheChannelAndWaitsForTheAcknowledgement() throws Exception {
        device.setChannel(4);
        byte[] dump = TestDumps.patch(0, WavestationBank.RAM1, 3);
        CompletableFuture<Void> f = device.sendDump(dump);
        assertTrue(queue.awaitEmpty(2000));
        assertEquals(0x34, sent.get(0)[2]);
        device.onSysex(WavestationSysex.message(4, MessageType.DATA_LOAD_COMPLETE));
        f.get(1, TimeUnit.SECONDS);
    }

    @Test
    void dataLoadErrorFailsTheDump() {
        CompletableFuture<Void> f = device.sendDump(TestDumps.patch(0, WavestationBank.RAM1, 3));
        device.onSysex(status(MessageType.DATA_LOAD_ERROR));
        ExecutionException e = assertThrows(ExecutionException.class, () -> f.get(1, TimeUnit.SECONDS));
        assertInstanceOf(WavestationDevice.DeviceException.class, e.getCause());
    }

    @Test
    void writeToRomIsRefusedWithoutSendingAnything() throws Exception {
        CompletableFuture<Void> f = device.writePatch(WavestationBank.ROM4, 0);
        assertTrue(f.isCompletedExceptionally());
        Thread.sleep(50);
        assertEquals(0, sent.size());
    }

    @Test
    void writeWaitsForWriteComplete() throws Exception {
        CompletableFuture<Void> f = device.writePatch(WavestationBank.RAM1, 5);
        device.onSysex(status(MessageType.WRITE_COMPLETE));
        f.get(1, TimeUnit.SECONDS);
        assertTrue(queue.awaitEmpty(2000));
        assertArrayEquals(WavestationSysex.patchWrite(0, WavestationBank.RAM1, 5), sent.get(0));
    }

    @Test
    void identifyFindsTheSr() throws Exception {
        CompletableFuture<DeviceIdentity> f = device.identify();
        byte[] reply = { (byte) 0xF0, 0x7E, 0x02, 0x06, 0x02, 0x42, 0x28, 0x00, 0x02, 0x00, 0x05, 0x00, 0x01, 0x00,
                (byte) 0xF7 };
        device.onSysex(reply);
        DeviceIdentity id = f.get(1, TimeUnit.SECONDS);
        assertEquals(2, id.channel());
        assertEquals(WavestationSysex.MEMBER_SR, id.member());
    }

    @Test
    void parameterChangesAreSentWithTheDeviceChannel() throws Exception {
        device.setChannel(1);
        device.setParameter(93, 72);
        assertTrue(queue.awaitEmpty(1000));
        assertArrayEquals(WavestationSysex.parameterChange(1, 93, "+72"), sent.get(0));
    }

    @Test
    void selectPerformanceSendsBankSelectAndProgramChange() throws Exception {
        device.setChannel(2);
        device.selectPerformance(WavestationBank.ROM4, 12);
        assertTrue(queue.awaitEmpty(2000));
        assertArrayEquals(new byte[] { (byte) 0xB2, 0x00, 0x00 }, sent.get(0));
        assertArrayEquals(new byte[] { (byte) 0xB2, 0x20, 0x02 }, sent.get(1));
        assertArrayEquals(new byte[] { (byte) 0xC2, 62 }, sent.get(2));
    }

    @Test
    void answerTimeoutsFollowTheDumpSize() {
        assertEquals(2 * 9545 + WavestationDevice.ANSWER_MARGIN_MS,
                WavestationDevice.timeoutFor(MessageType.PATCH_BANK_DUMP));
        assertEquals(WavestationDevice.DEFAULT_TIMEOUT_MS, WavestationDevice.timeoutFor(MessageType.SYSTEM_SETUP_DUMP));
    }
}
