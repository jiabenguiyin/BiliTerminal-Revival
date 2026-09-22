package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StartupRoutePolicyTest {
    @Test
    public void offlineContinuesWithoutRelay() {
        assertEquals(StartupRoutePolicy.FailureAction.OFFLINE_CONTINUE,
                StartupRoutePolicy.decideFailure(false, false));
    }

    @Test
    public void directFailureOffersRelayOnce() {
        assertEquals(StartupRoutePolicy.FailureAction.OFFER_RELAY,
                StartupRoutePolicy.decideFailure(true, false));
    }

    @Test
    public void relayFailureDoesNotOfferRelayAgain() {
        assertEquals(StartupRoutePolicy.FailureAction.RELAY_FAILED,
                StartupRoutePolicy.decideFailure(true, true));
    }
}
