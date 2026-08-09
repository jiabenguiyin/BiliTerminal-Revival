package com.RobinNotBad.BiliClient.util;

/** Filters decoder position glitches before they can move the UI or become a later seek target. */
public final class PlaybackProgressGuard {
    private static final long MAX_BACKWARD_JUMP_MS = 2_000L;
    private static final long MIN_FORWARD_ALLOWANCE_MS = 6_000L;
    private static final long SEEK_SETTLE_TIME_MS = 5_000L;
    private static final long SEEK_KEYFRAME_TOLERANCE_MS = 15_000L;

    private long acceptedPosition = -1L;
    private long acceptedAt = -1L;
    private long seekTarget = -1L;
    private long seekSettlesUntil = -1L;

    public synchronized long accept(long position, long duration, long now) {
        if (position < 0L || (duration > 0L && position > duration + 2_000L)) return -1L;

        long boundedPosition = duration > 0L ? Math.min(position, duration) : position;
        if (acceptedPosition < 0L) {
            update(boundedPosition, now);
            return boundedPosition;
        }

        if (now <= seekSettlesUntil) {
            if (Math.abs(boundedPosition - seekTarget) > SEEK_KEYFRAME_TOLERANCE_MS) return -1L;
            update(boundedPosition, now);
            return boundedPosition;
        }

        long elapsed = Math.max(0L, now - acceptedAt);
        long maxForwardJump = Math.max(MIN_FORWARD_ALLOWANCE_MS, elapsed * 4L + 2_000L);
        long delta = boundedPosition - acceptedPosition;
        if (delta < -MAX_BACKWARD_JUMP_MS || delta > maxForwardJump) return -1L;

        update(boundedPosition, now);
        return boundedPosition;
    }

    public synchronized void recordSeek(long position, long duration, long now) {
        long boundedPosition = Math.max(0L, duration > 0L ? Math.min(position, duration) : position);
        update(boundedPosition, now);
        seekTarget = boundedPosition;
        seekSettlesUntil = now + SEEK_SETTLE_TIME_MS;
    }

    public synchronized long getAcceptedPosition() {
        return Math.max(0L, acceptedPosition);
    }

    public synchronized void reset() {
        acceptedPosition = -1L;
        acceptedAt = -1L;
        seekTarget = -1L;
        seekSettlesUntil = -1L;
    }

    private void update(long position, long now) {
        acceptedPosition = position;
        acceptedAt = now;
    }
}
