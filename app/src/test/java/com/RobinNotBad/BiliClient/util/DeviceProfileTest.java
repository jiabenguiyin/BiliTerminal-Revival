package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import android.os.Build;

import org.junit.Test;

public class DeviceProfileTest {
    @Test
    public void unknownMemoryDoesNotEnableHighEndOnWatchSdk() {
        assertEquals(DeviceProfile.Tier.BALANCED,
                DeviceProfile.classify(Build.VERSION_CODES.P, 0, 2));
        assertEquals(DeviceProfile.Tier.BALANCED,
                DeviceProfile.classify(Build.VERSION_CODES.Q, 0, 4));
    }

    @Test
    public void explicitMemoryStillEnablesHighEnd() {
        assertEquals(DeviceProfile.Tier.HIGH_END,
                DeviceProfile.classify(Build.VERSION_CODES.Q, 512, 4));
    }

    @Test
    public void watchesStayOnCompatibilityPathUnlessOverridden() {
        assertEquals(DeviceProfile.Tier.COMPAT,
                DeviceProfile.classify(Build.VERSION_CODES.P, 512, 4, true));
    }

    @Test
    public void stableReleaseAlwaysUsesSinglePlayerPath() {
        assertFalse(DeviceProfile.useDashDualPlayer());
    }

    @Test
    public void compatibilityProfileKeeps720pAutomaticButAllowsManual1080p() {
        assertEquals(64, DeviceProfile.autoQuality());
        assertEquals(80, DeviceProfile.maxQuality());
    }

    @Test
    public void compatibilityOnlineBufferHasNetworkJitterHeadroom() {
        assertEquals(6 * 1024 * 1024, DeviceProfile.maxBufferBytes());
        assertEquals(6000, DeviceProfile.maxCachedDurationMs());
        assertEquals(5, DeviceProfile.minBufferedFrames());
    }
}
