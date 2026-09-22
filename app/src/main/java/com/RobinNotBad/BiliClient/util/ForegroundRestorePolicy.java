package com.RobinNotBad.BiliClient.util;

/** Chooses whether foreground surface recovery needs a positioning seek. */
public final class ForegroundRestorePolicy {
    private static final long MIN_POSITION_DELTA_MS = 750L;

    private ForegroundRestorePolicy() {
    }

    public static long choosePosition(long currentPosition, long savedPosition, long duration) {
        long current = Math.max(0L, currentPosition);
        if (savedPosition < 0L || current + MIN_POSITION_DELTA_MS >= savedPosition) {
            return current;
        }
        long saved = Math.max(0L, savedPosition);
        return duration > 0L ? Math.min(saved, duration) : saved;
    }

    public static boolean needsSeek(long currentPosition, long savedPosition) {
        return savedPosition >= 0L
                && Math.max(0L, currentPosition) + MIN_POSITION_DELTA_MS < savedPosition;
    }
}
