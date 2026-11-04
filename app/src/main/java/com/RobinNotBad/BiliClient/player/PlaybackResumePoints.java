package com.RobinNotBad.BiliClient.player;

import java.util.LinkedHashMap;

/** Per-playlist bookmarks; a completed item explicitly overrides stale server history with zero. */
public final class PlaybackResumePoints {
    private static final int LIMIT = 128;
    private final LinkedHashMap<String, Long> points = new LinkedHashMap<>();

    public void remember(long aid, long cid, long position, long duration, boolean completed) {
        if (aid <= 0 || cid <= 0 || position < 0) return;
        String key = aid + ":" + cid;
        points.remove(key);
        points.put(key, completed ? 0 : Math.min(Integer.MAX_VALUE,
                duration > 0 ? Math.min(position, duration) : position));
        if (points.size() > LIMIT) points.remove(points.keySet().iterator().next());
    }

    public long position(long aid, long cid, long fallbackCid, long fallbackPosition) {
        if (aid <= 0 || cid <= 0) return 0;
        Long saved = points.get(aid + ":" + cid);
        return saved != null ? saved : fallbackCid == cid ? Math.max(0, Math.min(Integer.MAX_VALUE, fallbackPosition)) : 0;
    }

    public String[] keys() { return points.keySet().toArray(new String[0]); }
    public long[] values() {
        long[] values = new long[points.size()]; int index = 0;
        for (long value : points.values()) values[index++] = value;
        return values;
    }
    public void restore(String[] keys, long[] values) {
        points.clear();
        if (keys == null || values == null || keys.length != values.length) return;
        for (int i = Math.max(0, keys.length - LIMIT); i < keys.length; i++) {
            if (keys[i] != null && keys[i].matches("[1-9][0-9]*:[1-9][0-9]*") && values[i] >= 0)
                points.put(keys[i], Math.min(Integer.MAX_VALUE, values[i]));
        }
    }
}
