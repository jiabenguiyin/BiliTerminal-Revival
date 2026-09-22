package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.model.DanmakuElem;
import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.model.HighEnergyData;

/**
 * Builds the high-energy graph from the protobuf danmaku windows already loaded
 * by the player. This avoids the retired PBP endpoint and keeps long videos
 * bounded to the currently visible danmaku window.
 */
public final class HighEnergyDataBuilder {
    private static final int DEFAULT_STEP_SEC = 10;

    private final int stepSec;
    private float[] buckets = new float[0];
    private int durationMs;

    public HighEnergyDataBuilder() {
        this(DEFAULT_STEP_SEC);
    }

    public HighEnergyDataBuilder(int stepSec) {
        this.stepSec = Math.max(1, stepSec);
    }

    public synchronized void reset(int durationMs) {
        this.durationMs = Math.max(0, durationMs);
        int bucketCount = this.durationMs <= 0
                ? 0
                : Math.max(1, (this.durationMs + stepSec * 1000 - 1) / (stepSec * 1000));
        buckets = new float[bucketCount];
    }

    public synchronized boolean addSegment(DmSegMobileReply segment) {
        if (segment == null || segment.elems == null || buckets.length == 0) return false;
        boolean changed = false;
        for (DanmakuElem elem : segment.elems) {
            if (elem == null || elem.progress < 0 || elem.progress >= durationMs) continue;
            int bucket = elem.progress / (stepSec * 1000);
            if (bucket < 0 || bucket >= buckets.length) continue;
            buckets[bucket] += 1.0f;
            changed = true;
        }
        return changed;
    }

    public synchronized HighEnergyData snapshot() {
        float[] copy = new float[buckets.length];
        System.arraycopy(buckets, 0, copy, 0, buckets.length);
        return new HighEnergyData(stepSec, "", copy, "danmaku-density");
    }
}
