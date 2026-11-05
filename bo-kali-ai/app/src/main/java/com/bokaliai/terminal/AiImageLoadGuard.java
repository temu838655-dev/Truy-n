package com.bokaliai.terminal;

import java.util.concurrent.atomic.AtomicInteger;

/** Prevents stale asynchronous image reads from restoring a cleared/replaced image. */
public final class AiImageLoadGuard {
    private final AtomicInteger generation = new AtomicInteger();

    /** Starts a new image read and invalidates all earlier reads. */
    public int begin() {
        return generation.incrementAndGet();
    }

    /** Invalidates every read currently in flight. */
    public void cancel() {
        generation.incrementAndGet();
    }

    public boolean isCurrent(int token) {
        return token != 0 && generation.get() == token;
    }
}
