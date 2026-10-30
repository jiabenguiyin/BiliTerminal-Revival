package com.RobinNotBad.BiliClient.util;

final class HotConfigRefreshPolicy {
    private HotConfigRefreshPolicy() {
    }

    static boolean shouldRefresh(boolean cacheApplied, long now, long lastCheck, long refreshInterval) {
        return !cacheApplied || now - lastCheck >= refreshInterval;
    }
}
