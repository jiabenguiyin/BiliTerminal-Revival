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
}
