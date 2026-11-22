package com.RobinNotBad.BiliClient.api;

import android.os.SystemClock;
import com.RobinNotBad.BiliClient.model.PrivateMsgSession;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;

/** Process-local conversation snapshots. Cached rows never acknowledge messages as read. */
public final class PrivateSessionLoader {
    private static final long FRESH_MS = 5000, SNAPSHOT_MS = 10 * 60 * 1000, LIMITED_MS = 15000;
    private static final PrivateSessionLoader SHARED = new PrivateSessionLoader(
            SystemClock::elapsedRealtime, (key, size, unread) ->
            PrivateMsgApi.getSessionsListForAccount(size, unread, key.mid, key.cookie));
    interface Clock { long now(); }
    interface Fetcher { ArrayList<PrivateMsgSession> fetch(Key key, int size, int unread) throws Exception; }
    static final class Key {
        final long mid; final String cookie;
        Key(long mid, String cookie) { this.mid = mid; this.cookie = cookie; }
        boolean same(Key other) { return other != null && mid == other.mid && cookie.equals(other.cookie); }
        boolean isCurrent() {
            return mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                    && cookie.equals(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        }
    }
    public static final class Result {
        public final ArrayList<PrivateMsgSession> sessions;
        public final Exception failure;
        public final long retryAfterMs;
        public final boolean cached;
        private Result(Entry entry, long now, boolean cached) {
            sessions = copy(entry.sessions); failure = entry.failure;
            retryAfterMs = Math.max(0, entry.retryAt - now); this.cached = cached;
        }
    }
    private static final class Entry {
        final Key key; boolean loading; int expected, size;
        long updatedAt, retryAt; Exception failure;
        ArrayList<PrivateMsgSession> sessions;
        Entry(Key key) { this.key = key; }
    }
    private final Clock clock;
    private final Fetcher fetcher;
    private Entry current;
    PrivateSessionLoader(Clock clock, Fetcher fetcher) { this.clock = clock; this.fetcher = fetcher; }

    public static Result loadCurrent(int size, int unread, boolean force) throws IOException {
        Key key = new Key(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0),
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        if (key.mid <= 0) throw new IOException("请先登录后查看私信");
        Result result = SHARED.load(key, size, unread, force);
        if (!key.isCurrent()) throw new IOException("账号已变化，请重新加载私信");
        try {
            DiagnosticLogManager.record("private_sessions_refresh", new JSONObject()
                    .put("cached", result.cached)
                    .put("snapshot", result.sessions != null)
                    .put("sessions", result.sessions == null ? 0 : result.sessions.size())
                    .put("retry_after_ms", result.retryAfterMs)
                    .put("code", result.failure instanceof PrivateMsgApi.SessionApiException
                            ? ((PrivateMsgApi.SessionApiException) result.failure).code : result.failure == null ? 0 : -1));
        } catch (JSONException ignored) { }
        return result;
    }

    Result load(Key key, int size, int expected, boolean force) throws IOException {
        final Entry entry;
        synchronized (this) {
            if (current == null || !key.same(current.key)) current = new Entry(key);
            entry = current;
            boolean joined = false;
            while (entry.loading) {
                try { wait(); } catch (InterruptedException error) {
                    Thread.currentThread().interrupt(); throw new IOException("私信加载已中断", error);
                }
                joined = true;
            }
            if (entry != current) throw new IOException("账号已变化，请重新加载私信");
            long now = clock.now();
            if (entry.sessions != null && now - entry.updatedAt > SNAPSHOT_MS) entry.sessions = null;
            if (now < entry.retryAt) return new Result(entry, now, true);
            if (!force && entry.sessions != null && entry.expected == expected && entry.size == size
                    && (joined || now - entry.updatedAt < FRESH_MS)) return new Result(entry, now, true);
            entry.loading = true;
        }
        ArrayList<PrivateMsgSession> sessions = null;
        Exception failure = null;
        try { sessions = fetcher.fetch(key, size, expected); }
        catch (Exception error) { failure = error; }
        synchronized (this) {
            entry.loading = false;
            long now = clock.now();
            entry.failure = failure;
            entry.expected = expected; entry.size = size;
            if (failure == null) {
                entry.sessions = copy(sessions); entry.updatedAt = now; entry.retryAt = 0;
            } else {
                entry.retryAt = now + (failure instanceof PrivateMsgApi.SessionApiException
                        && ((PrivateMsgApi.SessionApiException) failure).code == -509 ? LIMITED_MS : 3000);
            }
            notifyAll();
            if (entry != current) throw new IOException("账号已变化，请重新加载私信");
            return new Result(entry, now, false);
        }
    }

    private static ArrayList<PrivateMsgSession> copy(ArrayList<PrivateMsgSession> rows) {
        if (rows == null) return null;
        ArrayList<PrivateMsgSession> result = new ArrayList<>();
        for (PrivateMsgSession row : rows) {
            PrivateMsgSession item = new PrivateMsgSession();
            item.talkerUid = row.talkerUid; item.sessionType = row.sessionType; item.systemMsgType = row.systemMsgType;
            item.unread = row.unread; item.sessionTs = row.sessionTs; item.contentType = row.contentType;
            item.accountName = row.accountName; item.accountAvatar = row.accountAvatar;
            try { item.content = row.content == null ? null : new JSONObject(row.content.toString()); }
            catch (JSONException impossible) { throw new IllegalStateException(impossible); }
            result.add(item);
        }
        return result;
    }
}
