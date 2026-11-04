package com.RobinNotBad.BiliClient.player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Random;

/** A bounded-to-one-collection queue. Video order is independent of its part list. */
public final class CollectionPlaybackQueue {
    public static final int SEQUENTIAL = 0;
    public static final int SHUFFLE = 1;
    public static final int REPEAT_ONE = 2;
    public static final int REVERSE = 3;
    private final ArrayList<Long> aids;
    private final ArrayList<Long> history = new ArrayList<>();
    private int historyIndex = -1;
    public final long collectionId;

    public CollectionPlaybackQueue(long collectionId, long[] source) {
        this.collectionId = collectionId;
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        if (source != null) for (long aid : source) if (aid > 0) unique.add(aid);
        aids = new ArrayList<>(unique);
    }

    public boolean contains(long aid) { return aids.contains(aid); }
    public int size() { return aids.size(); }
    public void append(long[] values) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>(aids);
        if (values != null) for (long value : values) if (value > 0) unique.add(value);
        aids.clear(); aids.addAll(unique);
    }
    public long[] toArray() {
        long[] result = new long[aids.size()];
        for (int i = 0; i < result.length; i++) result[i] = aids.get(i);
        return result;
    }

    /** -1 means the collection is complete or the current video is not a member. */
    public long next(long currentAid, int mode, Random random) {
        int index = aids.indexOf(currentAid);
        if (index < 0) return -1;
        if (mode == REPEAT_ONE) return currentAid;
        if (mode == REVERSE) return index > 0 ? aids.get(index - 1) : -1;
        if (mode == SHUFFLE) {
            if (aids.size() == 1) return currentAid;
            int next = random.nextInt(aids.size() - 1);
            if (next >= index) next++;
            return aids.get(next);
        }
        return index + 1 < aids.size() ? aids.get(index + 1) : -1;
    }

    public static int nextMode(int mode) { return mode >= 0 && mode < 3 ? mode + 1 : SEQUENTIAL; }

    public long nextForUser(long current, int mode, Random random) {
        if (!contains(current)) return -1;
        if (historyIndex >= 0 && historyIndex + 1 < history.size()) return history.get(historyIndex + 1);
        return next(current, mode == REPEAT_ONE ? SEQUENTIAL : mode, random);
    }

    public long previousForUser(long current) {
        return previousForUser(current, SEQUENTIAL);
    }

    public long previousForUser(long current, int mode) {
        if (!contains(current)) return -1;
        if (historyIndex > 0) return history.get(historyIndex - 1);
        int index = aids.indexOf(current);
        if (mode == REVERSE) return index + 1 < aids.size() ? aids.get(index + 1) : -1;
        return index > 0 ? aids.get(index - 1) : -1;
    }

    public boolean canSkipNext(long current, int mode) {
        if (!contains(current)) return false;
        return historyIndex >= 0 && historyIndex + 1 < history.size()
                || mode == SHUFFLE && aids.size() > 1
                || (mode == REVERSE ? aids.indexOf(current) > 0 : aids.indexOf(current) + 1 < aids.size());
    }

    public void recordPlayed(long current, boolean backwards) {
        if (!contains(current)) return;
        if (backwards && historyIndex > 0 && history.get(historyIndex - 1) == current) {
            historyIndex--;
            return;
        }
        if (backwards) {
            history.add(0, current);
            if (history.size() > 256) history.remove(history.size() - 1);
            historyIndex = 0;
            return;
        }
        if (historyIndex >= 0 && history.get(historyIndex) == current) return;
        if (!backwards && historyIndex + 1 < history.size() && history.get(historyIndex + 1) == current) {
            historyIndex++;
            return;
        }
        clearForwardHistory();
        history.add(current);
        if (history.size() > 256) history.remove(0);
        historyIndex = history.size() - 1;
    }

    public void clearForwardHistory() {
        while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
    }
}
