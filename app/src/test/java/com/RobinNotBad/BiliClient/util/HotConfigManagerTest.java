package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HotConfigManagerTest {
    private static final long HOUR = 60L * 60L * 1000L;

    @Test
    public void missingCacheTriggersRefreshEvenWhenCheckTimestampIsRecent() {
        assertTrue(HotConfigRefreshPolicy.shouldRefresh(false, 10 * HOUR, 9 * HOUR, 6 * HOUR));
    }

    @Test
    public void recentVerifiedCacheDoesNotTriggerRefresh() {
        assertFalse(HotConfigRefreshPolicy.shouldRefresh(true, 10 * HOUR, 9 * HOUR, 6 * HOUR));
    }

    @Test
    public void staleVerifiedCacheTriggersRefresh() {
        assertTrue(HotConfigRefreshPolicy.shouldRefresh(true, 10 * HOUR, 3 * HOUR, 6 * HOUR));
    }
}
