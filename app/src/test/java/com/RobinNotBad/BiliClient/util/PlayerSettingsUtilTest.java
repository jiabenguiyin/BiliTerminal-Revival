package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlayerSettingsUtilTest {
    @Test
    public void interactionScaleIsKeptInSupportedRange() {
        assertEquals(0.7f,
                PlayerSettingsUtil.normalizeFloat("player_interaction_choice_scale", 9999.0f),
                0.001f);
        assertEquals(0.5f,
                PlayerSettingsUtil.normalizeFloat("player_interaction_choice_scale", 0.5f),
                0.001f);
        assertEquals(1.0f,
                PlayerSettingsUtil.normalizeFloat("player_interaction_choice_scale", 1.0f),
                0.001f);
    }

    @Test
    public void danmakuValuesUseIndependentRanges() {
        assertEquals(0.7f,
                PlayerSettingsUtil.normalizeFloat("player_danmaku_size", 9999.0f),
                0.001f);
        assertEquals(0.5f,
                PlayerSettingsUtil.normalizeFloat("player_danmaku_transparency", 2.0f),
                0.001f);
        assertEquals(1.0f,
                PlayerSettingsUtil.normalizeFloat("player_danmaku_speed", -1.0f),
                0.001f);
    }

    @Test
    public void liveDurationIsBoundedIndependentlyFromVideoSpeed() {
        String liveKey = PlayerSettingsUtil.LIVE_DANMAKU_DURATION_FACTOR;
        for (float invalid : new float[]{0, -1, 3.1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(2f, PlayerSettingsUtil.normalizeFloat(liveKey, invalid), 0.001f);
        }
        for (float valid : new float[]{1f, 1.5f, 2f, 3f}) {
            assertEquals(valid, PlayerSettingsUtil.normalizeFloat(liveKey, valid), 0.001f);
        }
        assertEquals(5f, PlayerSettingsUtil.normalizeFloat("player_danmaku_speed", 5f), 0.001f);
    }
}
