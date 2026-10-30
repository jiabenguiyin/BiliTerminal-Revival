package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.event.ReplyStateEvent;
import com.RobinNotBad.BiliClient.model.Reply;
import org.greenrobot.eventbus.EventBus;
import java.util.Iterator;
import java.util.List;
import org.json.JSONObject;

public final class ReplyStateSync {
    private ReplyStateSync() { }
    public static final ReplyStateStore STORE = new ReplyStateStore(512);
    public static long account() { return SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0); }

    public static void copy(Reply reply, ReplyStateStore.State state) {
        reply.likeCount = state.likes;
        reply.liked = state.liked;
        reply.hated = state.hated;
        // Reply previews/counts are maintained by the existing ReplyEvent flow;
        // reaction snapshots must not overwrite a newly inserted child reply.
    }

    public static boolean observe(Reply reply, int type, long oid, long account, long read) {
        if (reply == null || account != account()) return false;
        ReplyStateStore.State old = STORE.get(account, type, oid, reply.rpid);
        ReplyStateStore.State state = STORE.observe(account, type, oid, reply.rpid, reply.root,
                reply.likeCount, reply.liked, reply.hated, reply.childCount, read);
        copy(reply, state);
        if (state.deleted) return false;
        if (reply.childMsgList != null) observe(reply.childMsgList, type, oid, account, read);
        if (state.revision == read && (old == null || old.likes != state.likes
                || old.liked != state.liked || old.hated != state.hated)) {
            publish(state);
        }
        return true;
    }

    public static void observe(List<Reply> replies, int type, long oid, long account, long read) {
        for (Iterator<Reply> it = replies.iterator(); it.hasNext();) {
            if (!observe(it.next(), type, oid, account, read)) it.remove();
        }
    }

    public static void publish(ReplyStateStore.State state) {
        if (state.account != account()) return;
        try {
            JSONObject details = new JSONObject().put("type", state.type).put("oid", state.oid)
                    .put("rpid", state.rpid).put("revision", state.revision)
                    .put("like_count", state.likes).put("liked", state.liked)
                    .put("hated", state.hated).put("deleted", state.deleted);
            DiagnosticLogManager.record("reply_state_sync", details);
        } catch (org.json.JSONException ignored) { }
        EventBus.getDefault().post(new ReplyStateEvent(state));
    }

    public static void applyLatest(List<Reply> replies, int type, long oid) {
        if (replies == null) return;
        for (Iterator<Reply> it = replies.iterator(); it.hasNext();) {
            Reply reply = it.next();
            if (reply == null) continue;
            ReplyStateStore.State state = STORE.get(account(), type, oid, reply.rpid);
            if (state != null) {
                if (state.deleted) { it.remove(); continue; }
                copy(reply, state);
            }
            if (reply.childMsgList != null) applyLatest(reply.childMsgList, type, oid);
        }
    }
}
