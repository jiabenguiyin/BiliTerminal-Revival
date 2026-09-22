package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayerCompatibilityUtilTest {
    @Test
    public void huaweiAndHarmonyDevicesPreferSoftwareCodec() {
        assertTrue(PlayerCompatibilityUtil.prefersSoftwareCodec("HUAWEI", "HUAWEI", "HarmonyOS 3"));
        assertTrue(PlayerCompatibilityUtil.prefersSoftwareCodec("unknown", "unknown", "HarmonyOS"));
    }

    @Test
    public void otherDevicesKeepHardwareCodecDefault() {
        assertFalse(PlayerCompatibilityUtil.prefersSoftwareCodec("Xiaomi", "Redmi", "Android"));
    }

    @Test
    public void unsupportedNativeAbisUsePlatformPlayer() {
        assertTrue(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer("mips", ""));
        assertTrue(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer("armeabi", ""));
        assertFalse(PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer("armeabi-v7a", "x86"));
    }

    @Test
    public void oldAndLowMemoryDevicesUseGentlerLongPressSpeed() {
        assertTrue(PlayerCompatibilityUtil.shouldReduceLongPressLoad(19, 256));
        assertTrue(PlayerCompatibilityUtil.shouldReduceLongPressLoad(30, 128));
        assertFalse(PlayerCompatibilityUtil.shouldReduceLongPressLoad(30, 256));
        assertTrue(PlayerCompatibilityUtil.longPressSpeed(19, 256) == 2.0f);
        assertTrue(PlayerCompatibilityUtil.longPressSpeed(30, 256) == 3.0f);
    }

    @Test
    public void highFrameRateRequiresAtLeastFiftyNineHertz() {
        assertFalse(PlayerCompatibilityUtil.supportsHighFrameRate(58.9f));
        assertTrue(PlayerCompatibilityUtil.supportsHighFrameRate(59.0f));
        assertTrue(PlayerCompatibilityUtil.supportsHighFrameRate(60.0f));
    }
}
