package com.RobinNotBad.BiliClient.util;

/** Calculates fixed-sensitivity horizontal swipe seeking. */
public final class SwipeSeekUtil {

    /** A full player-width swipe changes playback by about one minute. */
    public static final long FULL_WIDTH_DELTA_MS = 60_000L;

    private SwipeSeekUtil() {
    }

    public static long calculateTarget(long startPositionMs, float deltaX,
                                       float viewportWidth, long durationMs) {
        long safeStart = Math.max(0L, startPositionMs);
        if (durationMs <= 0L) return safeStart;

        float safeWidth = Math.max(1f, viewportWidth);
        long deltaMs = Math.round(deltaX / safeWidth * FULL_WIDTH_DELTA_MS);
        return Math.max(0L, Math.min(durationMs, safeStart + deltaMs));
    }

    /** Signed seconds, including tenths for small adjustments. */
    public static String formatDelta(long deltaMs) {
        long tenths = Math.round(Math.abs(deltaMs) / 100.0);
        String sign = deltaMs < 0L ? "-" : "+";
        return sign + (tenths / 10) + (tenths % 10 == 0 ? "" : "." + tenths % 10);
    }
}
