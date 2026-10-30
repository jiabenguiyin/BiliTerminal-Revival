package com.RobinNotBad.BiliClient.api;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppInfoApiVersionTest {
    @Test public void higherBetaCodeIsDetectedFromStable() {
        assertTrue(AppInfoApi.isNewerVersion("1.2.1-BETA", 2026100502,
                "1.2.1", 2026100501, true, false));
    }

    @Test public void sameCodeStableAndBetaAreNotRepeated() {
        assertFalse(AppInfoApi.isNewerVersion("1.2.1-BETA", 2026100501,
                "1.2.1", 2026100501, true, false));
        assertFalse(AppInfoApi.isNewerVersion("1.2.1", 2026100501,
                "1.2.1-BETA", 2026100501, false, true));
    }

    @Test public void lowerCodeWithHigherVisibleNameIsNotDetected() {
        assertFalse(AppInfoApi.isNewerVersion("9.9.9", 2026100501,
                "1.2.1-BETA", 2026100502, false, true));
    }

    @Test public void higherStableCodeIsDetectedFromBeta() {
        assertTrue(AppInfoApi.isNewerVersion("1.2.1", 2026100503,
                "1.2.1-BETA", 2026100502, false, true));
    }

    @Test public void betaNameRecoversMissingBetaFlag() {
        assertTrue(AppInfoApi.isBetaChannelResponse(true, "1.2.1-BETA", false, 0));
    }

    @Test public void betaReleaseMarkerRecoversMissingBetaFlag() {
        assertTrue(AppInfoApi.isBetaChannelResponse(true, "1.2.1", false, 0));
    }

    @Test public void stableResponseIsNotRelabeledAsBeta() {
        assertFalse(AppInfoApi.isBetaChannelResponse(true, "1.2.1", false, 1));
        assertFalse(AppInfoApi.isBetaChannelResponse(false, "1.2.1-BETA", true, 0));
    }
}
