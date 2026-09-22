package com.RobinNotBad.BiliClient.util;

/**
 * A lightweight monotonic clock for danmaku rendering.
 *
 * Player position queries cross into the media layer and are expensive on old
 * watches. The clock advances from elapsed realtime and is corrected by the
 * player position periodically instead of once per danmaku frame.
 */
public final class DanmakuClock {
    private long anchorPosition = 0L;
    private long anchorTime = -1L;
    private float speed = 1.0F;
    private boolean running;

    public synchronized void reset(long position, long now, boolean running) {
        anchorPosition = Math.max(0L, position);
        anchorTime = now;
        this.running = running;
    }

    public synchronized void setPlaying(boolean playing, long now) {
        anchorPosition = positionAtLocked(now);
        anchorTime = now;
        running = playing;
    }

    public synchronized void setSpeed(float newSpeed, long now) {
        anchorPosition = positionAtLocked(now);
        anchorTime = now;
        speed = newSpeed > 0.0F ? newSpeed : 1.0F;
    }

    public synchronized long positionAt(long now) {
        return positionAtLocked(now);
    }

    /**
     * Reconcile with a real player position. Small differences are eased in;
     * large differences are corrected immediately so a seek cannot leave the
     * danmaku timeline behind the video.
     */
    public synchronized long reconcile(long playerPosition, long now, long hardThresholdMs) {
        long actual = Math.max(0L, playerPosition);
        long estimated = positionAtLocked(now);
        long difference = actual - estimated;
        if (Math.abs(difference) >= Math.max(1L, hardThresholdMs)) {
            reset(actual, now, running);
            return actual;
        }

        if (difference != 0L) {
            anchorPosition = estimated + difference / 3L;
            anchorTime = now;
        }
        return positionAtLocked(now);
    }

    private long positionAtLocked(long now) {
        if (anchorTime < 0L || !running) return Math.max(0L, anchorPosition);
        long elapsed = Math.max(0L, now - anchorTime);
        return Math.max(0L, anchorPosition + (long) (elapsed * speed));
    }
}
