package com.RobinNotBad.BiliClient.util;
import org.junit.Test;
import static org.junit.Assert.*;
public class LongPressHintSettingsTest {
    @Test public void smallNormalAndLargeHintScalesAreIndependentValidChoices(){
        for(float scale:new float[]{.5f,.75f,1f,1.25f})assertEquals(scale,PlayerSettingsUtil.normalizeFloat(PlayerSettingsUtil.LONGPRESS_HINT_SCALE,scale),0);
    }
    @Test public void invalidHintSizesCannotMakeTheOverlayHugeOrCrashLayout(){
        for(float scale:new float[]{-1,0,1.5f,Float.NaN,Float.POSITIVE_INFINITY})assertEquals(.75f,PlayerSettingsUtil.normalizeFloat(PlayerSettingsUtil.LONGPRESS_HINT_SCALE,scale),0);
    }
    @Test public void changingHintScaleDoesNotChangeDanmakuOrInteractionPolicy(){
        assertEquals(2f,PlayerSettingsUtil.normalizeFloat("player_danmaku_size",2f),0);
        assertEquals(.7f,PlayerSettingsUtil.normalizeFloat("player_interaction_choice_scale",2f),0);
    }
}
