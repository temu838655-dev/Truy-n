package com.bokaliai.terminal;

import org.junit.Test;
import static org.junit.Assert.*;

/** Verifies that cleared or replaced image reads cannot publish stale results. */
public class AiImageLoadGuardTest {
    @Test public void newestSelectionInvalidatesOlderRead() {
        AiImageLoadGuard guard = new AiImageLoadGuard();
        int first = guard.begin();
        int second = guard.begin();
        assertFalse(guard.isCurrent(first));
        assertTrue(guard.isCurrent(second));
    }

    @Test public void clearingInvalidatesInFlightRead() {
        AiImageLoadGuard guard = new AiImageLoadGuard();
        int token = guard.begin();
        guard.cancel();
        assertFalse(guard.isCurrent(token));
    }

    @Test public void tokenFromAnotherGuardIsNeverCurrent() {
        AiImageLoadGuard firstGuard = new AiImageLoadGuard();
        AiImageLoadGuard secondGuard = new AiImageLoadGuard();
        int token = firstGuard.begin();
        assertFalse(secondGuard.isCurrent(token));
    }
}
