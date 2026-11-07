package com.harmonia.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

class SysexRequestsTest {

    private final SysexRequests requests = new SysexRequests();

    @Test
    void matchingMessageCompletesTheRequest() throws Exception {
        CompletableFuture<byte[]> f = requests.expect(m -> m[1] == 2, 1000);
        assertFalse(requests.offer(new byte[] { (byte) 0xF0, 1, (byte) 0xF7 }));
        byte[] answer = { (byte) 0xF0, 2, (byte) 0xF7 };
        assertTrue(requests.offer(answer));
        assertArrayEquals(answer, f.get(1, TimeUnit.SECONDS));
        assertEquals(0, requests.pendingCount());
    }

    @Test
    void oldestRequestIsServedFirst() throws Exception {
        CompletableFuture<byte[]> first = requests.expect(m -> true, 1000);
        CompletableFuture<byte[]> second = requests.expect(m -> true, 1000);
        requests.offer(new byte[] { 1 });
        assertTrue(first.isDone());
        assertFalse(second.isDone());
    }

    @Test
    void requestTimesOut() {
        CompletableFuture<byte[]> f = requests.expect(m -> true, 50);
        ExecutionException e = assertThrows(ExecutionException.class, () -> f.get(2, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, e.getCause());
        assertEquals(0, requests.pendingCount());
    }
}
