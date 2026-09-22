package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UiConfigurationUtilTest {
    @Test
    public void scaleIsBounded() {
        assertEquals(1.0f, UiConfigurationUtil.normalizeScale(10.0f), 0.001f);
        assertEquals(0.5f, UiConfigurationUtil.normalizeScale(0.5f), 0.001f);
        assertEquals(2.0f, UiConfigurationUtil.normalizeScale(2.0f), 0.001f);
    }

    @Test
    public void paddingIsBounded() {
        assertEquals(0, UiConfigurationUtil.normalizePadding(-3));
        assertEquals(20, UiConfigurationUtil.normalizePadding(30));
    }

    @Test
    public void densityIsBoundedByDeviceDensity() {
        assertEquals(72, UiConfigurationUtil.normalizeDensity(72, 160));
        assertEquals(240, UiConfigurationUtil.normalizeDensity(9999, 160));
        assertEquals(-1, UiConfigurationUtil.normalizeDensity(0, 160));
    }

    @Test
    public void scaledDensityNeverBecomesInvalid() {
        assertEquals(72, UiConfigurationUtil.densityForScale(0.25f, 160));
        assertEquals(320, UiConfigurationUtil.densityForScale(2.0f, 160));
        assertEquals(160, UiConfigurationUtil.densityForScale(10.0f, 160));
    }

    @Test
    public void narrowScreenKeepsMinimumUsableDpWidth() {
        assertEquals(420, UiConfigurationUtil.densityForScale(1.0f, 420, 408));
        assertEquals(420, UiConfigurationUtil.normalizeDensity(420, 420, 408));
        assertEquals(294, UiConfigurationUtil.densityForScale(0.7f, 420, 408));
        assertEquals(504, UiConfigurationUtil.densityForScale(1.2f, 420, 408));
    }
}
