package com.RobinNotBad.BiliClient.util;

public final class StartupRoutePolicy {
    public enum FailureAction {
        OFFLINE_CONTINUE,
        OFFER_RELAY,
        RELAY_FAILED
    }

    private StartupRoutePolicy() {
    }

    public static FailureAction decideFailure(boolean hasNetwork, boolean relayActive) {
        if (!hasNetwork) return FailureAction.OFFLINE_CONTINUE;
        return relayActive ? FailureAction.RELAY_FAILED : FailureAction.OFFER_RELAY;
    }
}
