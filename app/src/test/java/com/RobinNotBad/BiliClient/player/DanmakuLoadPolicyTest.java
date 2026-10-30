package com.RobinNotBad.BiliClient.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class DanmakuLoadPolicyTest {
    @Test public void protectionIsOptionalAndDoesNotOverrideTheSavedOffPreference() {
        assertTrue(new DanmakuLoadPolicy(true, false, -1).shouldLoad());
        assertTrue(new DanmakuLoadPolicy(true, false, 900000).shouldLoad());
        assertFalse(new DanmakuLoadPolicy(false, false, 10).shouldLoad());
        DanmakuLoadPolicy off = new DanmakuLoadPolicy(false, true, -1);
        assertFalse(off.shouldLoad());
        assertFalse(off.needsCount());
    }

    @Test public void thresholdIsStrictlyGreaterThanFiveThousand() {
        for (long count : new long[]{0, 4999, 5000})
            assertTrue(new DanmakuLoadPolicy(true, true, count).shouldLoad());
        for (long count : new long[]{5001, Long.MAX_VALUE}) {
            DanmakuLoadPolicy policy = new DanmakuLoadPolicy(true, true, count);
            assertFalse(policy.shouldLoad());
            assertTrue(policy.isSuppressed());
        }
    }

    @Test public void unknownCountIsCheckedBeforeLoadingAndFailureStillAllowsManualEnable() {
        DanmakuLoadPolicy policy = new DanmakuLoadPolicy(true, true, -1);
        assertTrue(policy.needsCount());
        assertFalse(policy.shouldLoad());
        policy.setCount(-1);
        assertFalse(policy.needsCount());
        assertTrue(policy.isSuppressed());
        policy.setEnabledByUser(true);
        assertTrue(policy.shouldLoad());
    }

    @Test public void lateCountCannotUndoManualEnableOrRestartAfterManualDisable() {
        DanmakuLoadPolicy policy = new DanmakuLoadPolicy(true, true, -1);
        policy.setEnabledByUser(true);
        policy.setCount(10000);
        assertTrue(policy.shouldLoad());
        policy.setEnabledByUser(false);
        policy.setCount(2);
        assertFalse(policy.shouldLoad());
        assertFalse(policy.needsCount());
    }

    @Test public void manualOverrideDoesNotCarryIntoAnotherVideo() {
        DanmakuLoadPolicy first = new DanmakuLoadPolicy(true, true, 9000);
        first.setEnabledByUser(true);
        assertTrue(first.shouldLoad());
        assertFalse(new DanmakuLoadPolicy(true, true, 9000).shouldLoad());
    }
}
