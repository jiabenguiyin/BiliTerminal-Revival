package com.RobinNotBad.BiliClient.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SwipeSeekUtilTest {

    @Test
    public void sameGestureHasSameDeltaForShortAndLongVideos() {
        long shortVideo = SwipeSeekUtil.calculateTarget(600_000L, 100f, 1000f, 1_800_000L);
        long longVideo = SwipeSeekUtil.calculateTarget(600_000L, 100f, 1000f, 360_000_000L);

        assertEquals(606_000L, shortVideo);
        assertEquals(shortVideo, longVideo);
    }

    @Test
    public void clampsToVideoBounds() {
        assertEquals(0L, SwipeSeekUtil.calculateTarget(2_000L, -1_000f, 1_000f, 60_000L));
        assertEquals(60_000L, SwipeSeekUtil.calculateTarget(59_000L, 1_000f, 1_000f, 60_000L));
    }

    @Test
    public void hintShowsSignedSecondsWithoutLosingSmallAdjustments() {
        assertEquals("+0", SwipeSeekUtil.formatDelta(0L));
        assertEquals("+0.6", SwipeSeekUtil.formatDelta(600L));
        assertEquals("-0.6", SwipeSeekUtil.formatDelta(-600L));
        assertEquals("+7.9", SwipeSeekUtil.formatDelta(7900L));
        assertEquals("-8", SwipeSeekUtil.formatDelta(-8000L));
        assertEquals("+90.5", SwipeSeekUtil.formatDelta(90500L));
    }
}
