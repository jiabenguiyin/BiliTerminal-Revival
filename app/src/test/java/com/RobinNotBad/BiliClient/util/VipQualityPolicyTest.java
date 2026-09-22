package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VipQualityPolicyTest {
    private static final long NOW = 1_000_000L;

    @Test
    public void unknownMemberStateKeepsVipQualitiesVisible() {
        assertTrue(VipQualityPolicy.shouldShowVipQualities(
                123L, 0L, false, 0L, NOW));
        assertTrue(VipQualityPolicy.shouldShowVipQualities(
                123L, 123L, false,
                NOW - VipQualityPolicy.STATUS_TTL_MS - 1L, NOW));
    }

    @Test
    public void freshConfirmedNonMemberHidesVipQualities() {
        assertFalse(VipQualityPolicy.shouldShowVipQualities(
                123L, 123L, false, NOW - 1L, NOW));
    }

    @Test
    public void freshConfirmedMemberShowsVipQualities() {
        assertTrue(VipQualityPolicy.shouldShowVipQualities(
                123L, 123L, true, NOW - 1L, NOW));
    }

    @Test
    public void onlyFreshConfirmedNonMemberResetsRestrictedDefault() {
        assertTrue(VipQualityPolicy.shouldResetRestrictedQuality(
                116, 123L, 123L, false, NOW - 1L, NOW));
        assertFalse(VipQualityPolicy.shouldResetRestrictedQuality(
                116, 123L, 123L, false,
                NOW - VipQualityPolicy.STATUS_TTL_MS - 1L, NOW));
        assertFalse(VipQualityPolicy.shouldResetRestrictedQuality(
                80, 123L, 123L, false, NOW - 1L, NOW));
    }
}
