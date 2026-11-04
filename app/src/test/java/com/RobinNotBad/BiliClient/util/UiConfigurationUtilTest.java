package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UiConfigurationUtilTest {
    @Test public void userInputRejectsOutOfRangeInsteadOfSilentlySavingDefaults() {
        for(String value:new String[]{"950","9","0","0.24","2.01","NaN","Infinity","abc","-1"}) {
            try { UiConfigurationUtil.parseScaleInput(value,1f); org.junit.Assert.fail(value); }
            catch(NumberFormatException error) { org.junit.Assert.assertTrue(error.getMessage().contains("0.25")); }
        }
        assertEquals(0.25f,UiConfigurationUtil.parseScaleInput("0.25",1f),0.0001f);
        assertEquals(2f,UiConfigurationUtil.parseScaleInput("2.00",1f),0.0001f);
        assertEquals(1.25f,UiConfigurationUtil.parseScaleInput(" 1.25 ",1f),0.0001f);
        assertEquals(0.8f,UiConfigurationUtil.parseScaleInput("",0.8f),0.0001f);
    }
    @Test public void paddingInputIsValidatedBeforeAnySettingIsWritten() {
        for(String value:new String[]{"-1","21","99","1.5","invalid"}) {
            try { UiConfigurationUtil.parsePaddingInput(value,0); org.junit.Assert.fail(value); }
            catch(NumberFormatException expected) { }
        }
        assertEquals(0,UiConfigurationUtil.parsePaddingInput("0",5));
        assertEquals(20,UiConfigurationUtil.parsePaddingInput("20",5));
    }
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
