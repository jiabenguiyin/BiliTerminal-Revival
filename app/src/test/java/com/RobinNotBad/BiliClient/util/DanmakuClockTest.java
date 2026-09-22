package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DanmakuClockTest {
    @Test
    public void advancesAtPlaybackSpeedWithoutPlayerQueries() {
        DanmakuClock clock = new DanmakuClock();
        clock.reset(10_000L, 1_000L, true);
        clock.setSpeed(2.0F, 1_000L);

        assertEquals(12_000L, clock.positionAt(2_000L));
        assertEquals(16_000L, clock.positionAt(4_000L));
    }

    @Test
    public void gentlyCorrectsSmallDriftAndHardCorrectsSeek() {
        DanmakuClock clock = new DanmakuClock();
        clock.reset(10_000L, 1_000L, true);

        long eased = clock.reconcile(10_300L, 1_000L, 800L);
        assertTrue(eased > 10_000L && eased < 10_300L);

        assertEquals(80_000L, clock.reconcile(80_000L, 2_000L, 800L));
        assertEquals(81_000L, clock.positionAt(3_000L));
    }

    @Test
    public void pauseAndResumeKeepPositionStable() {
        DanmakuClock clock = new DanmakuClock();
        clock.reset(5_000L, 1_000L, true);
        clock.setPlaying(false, 2_000L);
        assertEquals(6_000L, clock.positionAt(5_000L));
        clock.setPlaying(true, 5_000L);
        assertEquals(7_000L, clock.positionAt(6_000L));
    }
}
