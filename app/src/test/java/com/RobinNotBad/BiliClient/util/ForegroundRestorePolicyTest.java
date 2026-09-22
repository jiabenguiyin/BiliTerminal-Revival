package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ForegroundRestorePolicyTest {
    @Test
    public void doesNotSeekWhenSurfaceRecoveryKeptCurrentPosition() {
        assertFalse(ForegroundRestorePolicy.needsSeek(20_000L, 20_300L));
        assertEquals(20_000L,
                ForegroundRestorePolicy.choosePosition(20_000L, 20_300L, 120_000L));
    }

    @Test
    public void seeksForwardOnlyWhenCurrentPositionFellBehind() {
        assertTrue(ForegroundRestorePolicy.needsSeek(10_000L, 20_000L));
        assertEquals(20_000L,
                ForegroundRestorePolicy.choosePosition(10_000L, 20_000L, 120_000L));
    }

    @Test
    public void neverSeeksBackwardOrPastDuration() {
        assertEquals(30_000L,
                ForegroundRestorePolicy.choosePosition(30_000L, 20_000L, 120_000L));
        assertEquals(120_000L,
                ForegroundRestorePolicy.choosePosition(0L, 150_000L, 120_000L));
    }
}
