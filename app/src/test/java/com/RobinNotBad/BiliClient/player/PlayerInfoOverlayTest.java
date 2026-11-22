package com.RobinNotBad.BiliClient.player;

import com.RobinNotBad.BiliClient.model.InteractionVideoData;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PlayerInfoOverlayTest {
    private InteractionVideoData.InteractionHiddenVar variable(String name, long value, int visible) {
        InteractionVideoData.InteractionHiddenVar var = new InteractionVideoData.InteractionHiddenVar();
        var.name = name; var.value = value; var.isShow = visible; return var;
    }
    @Test public void onlyAuthorVisibleVariablesAreDisplayed() {
        InteractionVideoData data = new InteractionVideoData();
        data.hiddenVars = Arrays.asList(variable("Score", 12, 1), variable("Secret", 98, 0),
                variable("", 3, 1), null, variable("Luck", -7, 1));
        assertEquals("Score: 12    Luck: -7", PlayerInfoOverlay.visibleValues(data));
    }
    @Test public void liveVariableValuesAreFormattedAgainWithoutLosingPrecision() {
        InteractionVideoData data = new InteractionVideoData();
        InteractionVideoData.InteractionHiddenVar var = variable("Score\nlevel", Long.MAX_VALUE, 1);
        data.hiddenVars = Arrays.asList(var);
        assertEquals("Score level: 9223372036854775807", PlayerInfoOverlay.visibleValues(data));
        var.value = 0;
        assertEquals("Score level: 0", PlayerInfoOverlay.visibleValues(data));
    }
    @Test public void ordinaryOrEmptyInteractiveVideoDoesNotReserveSpace() {
        assertEquals("", PlayerInfoOverlay.visibleValues(null));
        assertEquals("", PlayerInfoOverlay.visibleValues(new InteractionVideoData()));
    }
}
