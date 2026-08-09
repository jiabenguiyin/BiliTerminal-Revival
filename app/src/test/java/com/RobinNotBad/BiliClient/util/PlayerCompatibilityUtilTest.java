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
}
