package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    public void androidSevenAndOlderStayOnCompatibilityPlaybackPath() {
        assertEquals(DeviceProfile.Tier.COMPAT,
                DeviceProfile.classify(Build.VERSION_CODES.M, 512, 8));
        assertEquals(DeviceProfile.Tier.COMPAT,
                DeviceProfile.classify(Build.VERSION_CODES.N, 512, 8));
        assertEquals(DeviceProfile.Tier.COMPAT,
                DeviceProfile.classify(Build.VERSION_CODES.N_MR1, 512, 8));
        assertEquals(DeviceProfile.Tier.BALANCED,
                DeviceProfile.classify(Build.VERSION_CODES.O, 0, 4));
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

    @Test
    public void spreadtrumA15KitkatUsesSoftwarePlayerRotation() {
        org.junit.Assert.assertTrue(DeviceProfile.requiresSoftwarePlayerRotation(
                "A15", "Spreadtrum", Build.VERSION_CODES.KITKAT));
        org.junit.Assert.assertFalse(DeviceProfile.requiresSoftwarePlayerRotation(
                "A15", "Spreadtrum", Build.VERSION_CODES.LOLLIPOP));
        org.junit.Assert.assertFalse(DeviceProfile.requiresSoftwarePlayerRotation(
                "A15", "Qualcomm", Build.VERSION_CODES.KITKAT));
    }

    @Test
    public void identifiesWatchRomEvenWhenWatchFeatureIsMissing() {
        assertTrue(DeviceProfile.isWatchLike(
                "aqsh_watch", "", "", "dove_evb4", 368, 448));
        assertTrue(DeviceProfile.isWatchLike(
                "A15", "", "Spreadtrum", "", 240, 320));
        assertFalse(DeviceProfile.isWatchLike(
                "Pixel", "Google", "Google", "raven", 1080, 2400));
        assertEquals(DeviceProfile.Tier.COMPAT,
                DeviceProfile.classify(Build.VERSION_CODES.O, 512, 8, true));
    }
}
