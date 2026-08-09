package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackProgressGuardTest {
    @Test
    public void rejectsDecoderTimelineGlitches() {
        PlaybackProgressGuard guard = new PlaybackProgressGuard();
        assertEquals(10_000L, guard.accept(10_000L, 120_000L, 1_000L));
        assertEquals(11_000L, guard.accept(11_000L, 120_000L, 2_000L));
        assertEquals(-1L, guard.accept(70_000L, 120_000L, 2_250L));
        assertEquals(-1L, guard.accept(2_000L, 120_000L, 2_500L));
        assertEquals(11_500L, guard.accept(11_500L, 120_000L, 2_750L));
    }

    @Test
    public void acceptsExplicitSeekAndDecoderSettling() {
        PlaybackProgressGuard guard = new PlaybackProgressGuard();
        guard.accept(10_000L, 120_000L, 1_000L);
        guard.recordSeek(80_000L, 120_000L, 2_000L);
        assertEquals(-1L, guard.accept(10_500L, 120_000L, 2_100L));
        assertEquals(79_500L, guard.accept(79_500L, 120_000L, 2_250L));
        assertEquals(80_500L, guard.accept(80_500L, 120_000L, 2_500L));
    }
}
