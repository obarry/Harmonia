package com.harmonia.midi;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Matches incoming SysEx messages with the requests waiting for them.
 * <p>
 * Typical use: before sending a dump request, call {@link #expect} with a predicate that
 * recognises the answer; the returned future completes with the answer, or fails with a
 * {@link java.util.concurrent.TimeoutException} if nothing matching arrives in time. Every
 * complete SysEx received is passed to {@link #offer}.
 */
public class SysexRequests {

    private record Pending(Predicate<byte[]> matcher, CompletableFuture<byte[]> future) {
    }

    private final List<Pending> pending = new CopyOnWriteArrayList<>();

    /**
     * Registers a request.
     *
     * @param matcher   recognises the expected answer
     * @param timeoutMs maximum wait
     * @return a future completed with the first matching message
     */
    public CompletableFuture<byte[]> expect(Predicate<byte[]> matcher, long timeoutMs) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        Pending p = new Pending(matcher, future);
        pending.add(p);
        future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> pending.remove(p));
        return future;
    }

    /**
     * Gives a received SysEx to the oldest matching request.
     *
     * @return true if a request consumed it
     */
    public boolean offer(byte[] sysex) {
        for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
            Pending p = it.next();
            if (!p.future().isDone() && p.matcher().test(sysex)) {
                pending.remove(p);
                p.future().complete(sysex);
                return true;
            }
        }
        return false;
    }

    /** Number of requests still waiting. */
    public int pendingCount() {
        return (int) pending.stream().filter(p -> !p.future().isDone()).count();
    }

    /** Fails every waiting request (connection closed). */
    public void cancelAll() {
        for (Pending p : pending) {
            p.future().cancel(false);
        }
        pending.clear();
    }
}
