package com.RobinNotBad.BiliClient.util;

public final class VipQualityPolicy {
    public static final long STATUS_TTL_MS = 10 * 60 * 1000L;

    private VipQualityPolicy() {
    }

    public static boolean hasFreshStatus(long currentMid, long cachedMid,
                                         long checkedAt, long now) {
        return currentMid > 0
                && cachedMid == currentMid
                && checkedAt > 0
                && now >= checkedAt
                && now - checkedAt < STATUS_TTL_MS;
    }

    public static boolean shouldShowVipQualities(long currentMid, long cachedMid,
                                                  boolean cachedVip, long checkedAt,
                                                  long now) {
        if (currentMid <= 0) return false;
        return !hasFreshStatus(currentMid, cachedMid, checkedAt, now) || cachedVip;
    }

    public static boolean shouldResetRestrictedQuality(int quality, long currentMid,
                                                       long cachedMid, boolean cachedVip,
                                                       long checkedAt, long now) {
        if (!isVipRestrictedQuality(quality)) return false;
        return hasFreshStatus(currentMid, cachedMid, checkedAt, now) && !cachedVip;
    }

    private static boolean isVipRestrictedQuality(int quality) {
        return quality == 74 || quality == 112 || quality == 116 || quality == 120;
    }
}
