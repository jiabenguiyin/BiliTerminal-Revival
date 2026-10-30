package com.RobinNotBad.BiliClient.util;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Bounded, account-scoped comment state. Read revisions prevent late responses undoing actions. */
public final class ReplyStateStore {
    public static final class State {
        public final long account, oid, rpid, root, revision;
        public final int type, likes, children;
        public final boolean liked, hated, deleted;

        private State(long account, int type, long oid, long rpid, long root, int likes,
                      boolean liked, boolean hated, int children, boolean deleted, long revision) {
            this.account = account; this.type = type; this.oid = oid; this.rpid = rpid;
            this.root = root; this.likes = Math.max(0, likes); this.liked = liked;
            this.hated = hated; this.children = Math.max(0, children);
            this.deleted = deleted; this.revision = revision;
        }
    }

    private long revision;
    private final Map<String, State> states;
    private final Set<String> pending = new HashSet<>();

    public ReplyStateStore(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        states = new LinkedHashMap<String, State>(capacity, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, State> entry) {
                return size() > capacity;
            }
        };
    }

    private String key(long account, int type, long oid, long rpid) {
        return account + ":" + type + ":" + oid + ":" + rpid;
    }

    public synchronized long beginRead() { return ++revision; }

    public synchronized State get(long account, int type, long oid, long rpid) {
        return states.get(key(account, type, oid, rpid));
    }

    public synchronized State observe(long account, int type, long oid, long rpid, long root,
                                      int likes, boolean liked, boolean hated, int children, long read) {
        State old = get(account, type, oid, rpid);
        if (old != null && (old.deleted || old.revision > read)) return old;
        State state = new State(account, type, oid, rpid, root, likes, liked, hated, children, false, read);
        states.put(key(account, type, oid, rpid), state);
        return state;
    }

    public synchronized boolean beginMutation(long account, int type, long oid, long rpid) {
        return pending.add(key(account, type, oid, rpid));
    }

    public synchronized void endMutation(long account, int type, long oid, long rpid) {
        pending.remove(key(account, type, oid, rpid));
    }

    public synchronized State react(long account, int type, long oid, long rpid, long root,
                                    int likes, boolean liked, boolean hated, int children,
                                    boolean likeAction, boolean target) {
        State old = get(account, type, oid, rpid);
        if (old != null) {
            if (old.deleted) return old;
            likes = old.likes; liked = old.liked; hated = old.hated; children = old.children;
        }
        boolean nextLiked = likeAction ? target : (target ? false : liked);
        boolean nextHated = likeAction ? (target ? false : hated) : target;
        if (liked != nextLiked) likes = nextLiked
                ? (likes == Integer.MAX_VALUE ? likes : likes + 1) : Math.max(0, likes - 1);
        State state = new State(account, type, oid, rpid, root, likes, nextLiked, nextHated,
                children, false, ++revision);
        states.put(key(account, type, oid, rpid), state);
        return state;
    }

    public synchronized State delete(long account, int type, long oid, long rpid, long root) {
        State state = new State(account, type, oid, rpid, root, 0, false, false, 0, true, ++revision);
        states.put(key(account, type, oid, rpid), state);
        return state;
    }
}
