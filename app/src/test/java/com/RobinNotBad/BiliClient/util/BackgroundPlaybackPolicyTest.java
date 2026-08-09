package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackgroundPlaybackPolicyTest {
    @Test
    public void ordinaryPlaybackNeedsSetting() {
        assertFalse(BackgroundPlaybackPolicy.shouldContinue(false, false, true, true, false));
        assertFalse(BackgroundPlaybackPolicy.shouldContinue(false, false, true, false, true));
    }

    @Test
    public void pausedPlaybackNeverContinues() {
        assertFalse(BackgroundPlaybackPolicy.shouldContinue(true, false, false, true, true));
        assertFalse(BackgroundPlaybackPolicy.shouldContinue(false, true, false, true, true));
    }

    @Test
    public void ordinaryPlaybackContinuesWhenEnabled() {
        assertTrue(BackgroundPlaybackPolicy.shouldContinue(true, false, true, true, false));
        assertTrue(BackgroundPlaybackPolicy.shouldContinue(true, false, true, false, true));
    }

    @Test
    public void audioOnlyModeContinuesWithoutSetting() {
        assertTrue(BackgroundPlaybackPolicy.shouldContinue(false, true, true, true, false));
        assertTrue(BackgroundPlaybackPolicy.shouldContinue(false, true, true, false, true));
    }

    @Test
    public void unrelatedPauseDoesNotContinue() {
        assertFalse(BackgroundPlaybackPolicy.shouldContinue(true, false, true, false, false));
        assertFalse(BackgroundPlaybackPolicy.shouldContinue(false, true, true, false, false));
    }
}
