package com.RobinNotBad.BiliClient.player;

/** Per-video automatic loading decision; a manual enable always takes priority. */
public final class DanmakuLoadPolicy {
    public static final int AUTO_LOAD_LIMIT = 5000;
    private final boolean protectLargeVideos;
    private boolean requested;
    private boolean manualOverride;
    private boolean countChecked;
    private long count;

    public DanmakuLoadPolicy(boolean requested, boolean protectLargeVideos, long count) {
        this.requested = requested;
        this.protectLargeVideos = protectLargeVideos;
        this.count = count;
        countChecked = count >= 0;
    }

    public boolean shouldLoad() {
        return requested && (!protectLargeVideos || manualOverride
                || (countChecked && count >= 0 && count <= AUTO_LOAD_LIMIT));
    }

    public boolean needsCount() {
        return requested && protectLargeVideos && !manualOverride && !countChecked;
    }

    public void setCount(long count) {
        this.count = count;
        countChecked = true;
    }

    public void setEnabledByUser(boolean enabled) {
        requested = enabled;
        manualOverride = enabled;
    }

    public boolean isSuppressed() {
        return requested && protectLargeVideos && !manualOverride && countChecked && !shouldLoad();
    }
}
