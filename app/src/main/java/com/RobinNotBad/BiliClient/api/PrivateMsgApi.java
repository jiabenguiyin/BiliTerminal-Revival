package com.RobinNotBad.BiliClient.api;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.util.Log;

import com.RobinNotBad.BiliClient.model.PrivateMessage;
import com.RobinNotBad.BiliClient.model.PrivateMsgSession;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.EmoteUtil;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import okhttp3.Response;

public class PrivateMsgApi {

    public static final int MSG_TYPE_TEXT = 1;
    public static final int MSG_TYPE_PIC = 2;
    public static final int MSG_TYPE_RETRACT = 5;
    private static final String ACK_URL = "https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack";
    private static final String SESSION_LIST_URL =
            "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions";
    private static final int MAX_UNREAD_SESSION_PAGES = 5;

    public static final class SessionApiException extends IOException {
        public final int code;

        SessionApiException(int code) {
            super("私信列表接口返回错误 (" + code + ")");
            this.code = code;
        }
    }

    static void requireSuccessfulSessionResponse(JSONObject response) throws SessionApiException {
        int code = response.optInt("code", -1);
        if (code != 0) throw new SessionApiException(code);
    }

    // 返回的是倒序的消息列表，使用时记得列表倒置
    //seqno传0时只看size，不进行seqno的筛选
    public static JSONObject getPrivateMsg(long talkerId, int size, long beginSeqno, long endSeqno)
            throws IOException, JSONException {
        String url =
                "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs?session_type=1&talker_id=" + talkerId
                        + "&size=" + size
                        + "&begin_seqno=" + beginSeqno
                        + "&end_seqno=" + endSeqno;
        JSONObject allMsgJson = NetWorkUtil.getJson(url);
        if (allMsgJson.has("data") && !allMsgJson.isNull("data"))
            return allMsgJson.getJSONObject("data");
        else return new JSONObject();
    }

    public static ArrayList<PrivateMessage> getPrivateMsgList(JSONObject allMsgJson)
            throws IOException, JSONException {
        ArrayList<PrivateMessage> list = new ArrayList<>();
        if (!allMsgJson.isNull("messages")) {
            JSONArray messages = allMsgJson.getJSONArray("messages");
            UserInfo myInfo = UserInfoApi.getCurrentUserInfo();
            UserInfo targetInfo = new UserInfo();

            // list.add(new PrivateMessage(114514,1,new JSONObject("{\"content\":\"
            // 。\"}"),11111111,"aaaaa",111));

            boolean isReqTargetInfo = false;
            for (int i = 0; i < messages.length(); i++) {
                PrivateMessage msgObject = new PrivateMessage();
                JSONObject msgJson = messages.getJSONObject(i);
                msgObject.uid = msgJson.getLong("sender_uid");
                msgObject.type = msgJson.getInt("msg_type");
                if (msgObject.uid == myInfo.mid) {
                    msgObject.name = myInfo.name;
                } else {
                    if (!isReqTargetInfo) {
                        targetInfo = UserInfoApi.getUserInfo(msgObject.uid);
                        isReqTargetInfo = true;
                    }
                    if (targetInfo != null) msgObject.name = targetInfo.name;
                }
                msgObject.content = new JSONObject("{\"content\":\" .\"}"); // 防止内容不为json时解析错误
                if (msgJson.getString("content").endsWith("}")
                        && msgJson.getString("content").startsWith("{")) {
                    try{
                        msgObject.content =
                                new JSONObject(msgJson.getString("content") /*.replace("\\","")*/);
                    }catch (Exception ignore){}
                    try{
                    msgObject.content_array =
                            new JSONArray(msgJson.getString("content") /*.replace("\\","")*/);
                    }catch (Exception ignore){}
                }
                msgObject.timestamp = msgJson.getLong("timestamp");
                msgObject.msg_source = msgJson.getInt("msg_source");
                msgObject.msgId = msgJson.getLong("msg_key");
                msgObject.msgSeqno = msgJson.getLong("msg_seqno");
                list.add(msgObject);

            }
        }
        return list;
    }

    public static long getRefreshBeginSeqno(List<PrivateMessage> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        PrivateMessage latest = messages.get(messages.size() - 1);
        return latest == null ? 0 : latest.msgSeqno;
    }

    public static JSONArray getEmoteJsonArray(JSONObject allMsgJson) throws JSONException {
        if (allMsgJson.has("e_infos") && !allMsgJson.isNull("e_infos")) {
            return allMsgJson.getJSONArray("e_infos");
        } else {
            return new JSONArray();
        }
    }

    public static HashMap<Long, UserInfo> getUsersInfo(ArrayList<Long> uidList)
            throws IOException, JSONException {
        HashMap<Long, UserInfo> userMap = new HashMap<>();
        if (uidList == null || uidList.isEmpty()) {
            return userMap;
        }

        StringBuilder userString = new StringBuilder();
        for (Long uid : uidList) {
            if (uid == null || uid <= 0) {
                continue;
            }
            if (userString.length() > 0) {
                userString.append(",");
            }
            userString.append(uid);
        }
        if (userString.length() == 0) {
            return userMap;
        }
        String url = "https://api.vc.bilibili.com/account/v1/user/cards?uids="
                + userString;
        JSONObject root = NetWorkUtil.getJson(url);
        if (root.has("data") && !root.isNull("data")) {
            JSONArray data = root.getJSONArray("data");
            for (int i = 0; i < data.length(); ++i) {
                JSONObject userJson = data.getJSONObject(i);
                UserInfo user = new UserInfo();
                user.mid = userJson.getLong("mid");
                user.name = userJson.getString("name");
                user.avatar = userJson.getString("face");
                Log.e("", user.mid + user.name + user.avatar);
                userMap.put(user.mid, user);
            }
        }
        return userMap;
    }

    public static ArrayList<PrivateMsgSession> getSessionsList(int size)
            throws IOException, JSONException {
        return getSessionsList(size, 0);
    }

    public static ArrayList<PrivateMsgSession> getSessionsList(int size, int expectedUnread)
            throws IOException, JSONException {
        return getSessionsList(size, expectedUnread, null, 0, null);
    }

    static ArrayList<PrivateMsgSession> getSessionsListForAccount(int size, int expectedUnread,
                                                                long mid, String cookie)
            throws IOException, JSONException {
        ArrayList<String> headers = new ArrayList<>();
        java.util.Collections.addAll(headers, "Cookie", cookie, "User-Agent", NetWorkUtil.USER_AGENT_WEB,
                "Referer", "https://message.bilibili.com/", "Origin", "https://message.bilibili.com");
        return getSessionsList(size, expectedUnread, headers, mid, cookie);
    }

    private static ArrayList<PrivateMsgSession> getSessionsList(int size, int expectedUnread,
                                                               ArrayList<String> headers, long mid, String cookie)
            throws IOException, JSONException {
        int pageSize = Math.max(1, Math.min(size, 100));
        int expected = Math.max(0, expectedUnread);
        ArrayList<PrivateMsgSession> sessions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long unreadFound = 0;
        long endTs = 0;

        for (int pageIndex = 0; pageIndex < MAX_UNREAD_SESSION_PAGES; pageIndex++) {
            StringBuilder url = new StringBuilder(SESSION_LIST_URL)
                    .append("?session_type=1&size=").append(pageSize)
                    .append("&sort_rule=2&group_fold=0&unfollow_fold=0&build=0&mobi_app=web");
            if (endTs > 0) url.append("&end_ts=").append(endTs);

            requireSessionAccount(mid, cookie);
            JSONObject root = headers == null ? NetWorkUtil.getJson(url.toString())
                    : NetWorkUtil.getJson(url.toString(), headers);
            requireSessionAccount(mid, cookie);
            int code = root.optInt("code", -1);
            if (code != 0) {
                recordSessionPage(pageIndex, code, 0, 0, 0, expected, false);
                requireSuccessfulSessionResponse(root);
            }

            SessionPage page;
            try {
                page = parseSessionPage(root);
            } catch (JSONException error) {
                recordSessionParseFailure(pageIndex, code, expected);
                throw error;
            }
            int unreadSessions = 0, unreadMessages = 0;
            for (PrivateMsgSession session : page.sessions) {
                String key = session.sessionType + ":" + session.talkerUid + ":" + session.systemMsgType;
                if (!seen.add(key)) continue;
                boolean unreadPrivateSession = session.sessionType == 1
                        && session.systemMsgType == 0 && session.unread > 0;
                int sessionUnread = unreadPrivateSession ? session.unread : 0;
                unreadFound += sessionUnread;
                unreadMessages += sessionUnread;
                if (pageIndex == 0 || unreadPrivateSession) sessions.add(session);
                if (unreadPrivateSession) unreadSessions++;
            }
            recordSessionPage(pageIndex, code, page.sessions.size(), unreadSessions, unreadMessages,
                    expected, page.hasMore);

            if (expected <= 0 || unreadFound >= expected || !page.hasMore
                    || page.nextCursor <= 0 || (endTs > 0 && page.nextCursor >= endTs)) break;
            endTs = page.nextCursor;
        }
        return sessions;
    }

    private static void requireSessionAccount(long mid, String cookie) throws IOException {
        if (cookie != null && (mid != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                || !cookie.equals(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))))
            throw new IOException("账号已变化，请重新加载私信");
    }

    private static void recordSessionPage(int pageIndex, int code, int sessionCount,
                                          int unreadSessions, int unreadMessages,
                                          int expectedUnread, boolean hasMore) {
        try {
            DiagnosticLogManager.record("private_sessions_page", new JSONObject()
                    .put("page", pageIndex + 1)
                    .put("code", code)
                    .put("sessions", sessionCount)
                    .put("unread_sessions", unreadSessions)
                    .put("unread_messages", unreadMessages)
                    .put("expected_unread", expectedUnread)
                    .put("has_more", hasMore));
        } catch (JSONException ignored) {
        }
    }

    private static void recordSessionParseFailure(int pageIndex, int code, int expectedUnread) {
        try {
            DiagnosticLogManager.record("private_sessions_parse_error", new JSONObject()
                    .put("page", pageIndex + 1)
                    .put("code", code)
                    .put("expected_unread", expectedUnread));
        } catch (JSONException ignored) {
        }
    }

    static ArrayList<PrivateMsgSession> parseSessions(JSONObject root) throws JSONException {
        ArrayList<PrivateMsgSession> sessionList = new ArrayList<>();
        JSONObject data = root.optJSONObject("data");
        if (data != null) {
            JSONArray sessions = data.optJSONArray("session_list");
            if (sessions == null) return sessionList;
            for (int i = 0; i < sessions.length(); ++i) {
                PrivateMsgSession session = new PrivateMsgSession();
                JSONObject sessionJson = sessions.getJSONObject(i);
                session.talkerUid = sessionJson.getLong("talker_id");
                session.sessionType = sessionJson.optInt("session_type", 1);
                session.systemMsgType = sessionJson.optInt("system_msg_type", 0);
                session.sessionTs = jsonLong(sessionJson, "session_ts");

                JSONObject accountInfo = sessionJson.optJSONObject("account_info");
                if (accountInfo != null) {
                    session.accountName = accountInfo.optString("name", "");
                    session.accountAvatar = accountInfo.optString("pic_url",
                            accountInfo.optString("face", ""));
                }

                if (!sessionJson.isNull("last_msg")) {
                    session.contentType = sessionJson.getJSONObject("last_msg").getInt("msg_type");
                    String content = sessionJson.getJSONObject("last_msg").getString("content");

                    if (content.endsWith("}") && content.startsWith("{"))
                        session.content = new JSONObject(content);
                }

                session.unread = sessionJson.getInt("unread_count");
                sessionList.add(session);
            }
        }
        return sessionList;
    }

    static final class SessionPage {
        final ArrayList<PrivateMsgSession> sessions;
        final boolean hasMore;
        final long nextCursor;

        SessionPage(ArrayList<PrivateMsgSession> sessions, boolean hasMore, long nextCursor) {
            this.sessions = sessions;
            this.hasMore = hasMore;
            this.nextCursor = nextCursor;
        }
    }

    static SessionPage parseSessionPage(JSONObject root) throws JSONException {
        ArrayList<PrivateMsgSession> sessions = parseSessions(root);
        JSONObject data = root.optJSONObject("data");
        boolean hasMore = false;
        if (data != null) {
            Object value = data.opt("has_more");
            hasMore = Boolean.TRUE.equals(value) || (value instanceof Number && ((Number) value).intValue() == 1)
                    || "1".equals(String.valueOf(value));
        }
        long cursor = sessions.isEmpty() ? 0 : sessions.get(sessions.size() - 1).sessionTs;
        return new SessionPage(sessions, hasMore, cursor);
    }

    private static long jsonLong(JSONObject object, String key) {
        try {
            return Long.parseLong(object.optString(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static ArrayList<PrivateMsgSession> getNewSessionsList(long beginTs, int size, int build, String mobiApp)
            throws IOException, JSONException {
        StringBuilder url = new StringBuilder("https://api.vc.bilibili.com/session_svr/v1/session_svr/new_sessions?");
        if (beginTs > 0) {
            url.append("begin_ts=").append(beginTs).append("&");
        }
        if (size > 0) {
            url.append("size=").append(size).append("&");
        }
        if (build > 0) {
            url.append("build=").append(build).append("&");
        }
        if (mobiApp != null && !mobiApp.isEmpty()) {
            url.append("mobi_app=").append(mobiApp).append("&");
        }
        if (url.charAt(url.length() - 1) == '&') {
            url.setLength(url.length() - 1);
        }
        JSONObject root = NetWorkUtil.getJson(url.toString());
        ArrayList<PrivateMsgSession> sessionList = new ArrayList<>();
        if (root.has("data") && !root.isNull("data")) {
            JSONObject data = root.getJSONObject("data");
            if (data.has("session_list") && !data.isNull("session_list")) {
                JSONArray sessions = data.getJSONArray("session_list");
                for (int i = 0; i < sessions.length(); ++i) {
                    PrivateMsgSession session = new PrivateMsgSession();
                    JSONObject sessionJson = sessions.getJSONObject(i);
                    session.talkerUid = sessionJson.getLong("talker_id");
                    session.sessionType = sessionJson.optInt("session_type", 1);
                    session.systemMsgType = sessionJson.optInt("system_msg_type", 0);

                    JSONObject accountInfo = sessionJson.optJSONObject("account_info");
                    if (accountInfo != null) {
                        session.accountName = accountInfo.optString("name", "");
                        session.accountAvatar = accountInfo.optString("pic_url",
                                accountInfo.optString("face", ""));
                    }

                    if (!sessionJson.isNull("last_msg")) {
                        session.contentType = sessionJson.getJSONObject("last_msg").getInt("msg_type");
                        String content = sessionJson.getJSONObject("last_msg").getString("content");

                        if (content.endsWith("}") && content.startsWith("{"))
                            session.content = new JSONObject(content);
                    }

                    session.unread = sessionJson.getInt("unread_count");
                    sessionList.add(session);
                }
            }
        }
        return sessionList;
    }

    public static JSONObject sendMsg(
            long senderUid, long receiverUid, int msgType, long timestamp, String content)
            throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg?";
        String per = new NetWorkUtil.FormData()
                .put("msg[dev_id]", getDevId())
                .put("msg[msg_type]", msgType)
                .put("msg[content]", content)
                .put("msg[receiver_type]", 1)
                .put("csrf", SharedPreferencesUtil.getString("csrf", ""))
                .put("msg[sender_uid]", senderUid)
                .put("msg[receiver_id]", receiverUid)
                .put("msg[timestamp]", timestamp)
                .toString();

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body()).string());

        return result;
    }

    /** Builds the JSON payload used by Bilibili private image messages. */
    public static String toPictureContent(JSONObject data) throws JSONException {
        if (data == null) throw new JSONException("图片上传返回为空");
        String url = data.optString("image_url",
                data.optString("url", data.optString("img_src", "")));
        if (url.isEmpty()) throw new JSONException("图片上传没有返回地址");
        return new JSONObject()
                .put("url", url)
                .put("width", data.optInt("image_width", data.optInt("width", 0)))
                .put("height", data.optInt("image_height", data.optInt("height", 0)))
                .toString();
    }

    public static JSONObject updateAck(long talkerId, int sessionType, long ackSeqno)
            throws IOException, JSONException {
        String body = buildAckBody(talkerId, sessionType, ackSeqno, SharedPreferencesUtil.getString("csrf", ""));
        try (Response response = NetWorkUtil.post(ACK_URL, body, NetWorkUtil.webHeaders)) {
            return response.body() == null ? new JSONObject() : new JSONObject(response.body().string());
        }
    }

    static JSONObject updateAck(long talkerId, int sessionType, long ackSeqno, String csrf, List<String> headers)
            throws IOException, JSONException {
        String body = buildAckBody(talkerId, sessionType, ackSeqno, csrf);
        try (Response response = NetWorkUtil.postOnce(ACK_URL, body, headers)) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("私信已读结果未确认");
            return new JSONObject(response.body().string());
        }
    }

    private static String buildAckBody(long talkerId, int sessionType, long ackSeqno, String csrf) {
        StringBuilder per = new StringBuilder();
        per.append("talker_id=").append(talkerId);
        per.append("&session_type=").append(sessionType);
        if (ackSeqno > 0) {
            per.append("&ack_seqno=").append(ackSeqno);
        }
        per.append("&csrf_token=").append(csrf);
        per.append("&csrf=").append(csrf);
        per.append("&build=0");
        per.append("&mobi_app=web");

        return per.toString();
    }

    /** Removes a conversation from the session list without deleting its chat history. */
    public static JSONObject removeSession(long talkerId) throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/remove_session";
        String csrf = SharedPreferencesUtil.getString("csrf", "");
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("talker_id", talkerId)
                .put("session_type", 1)
                .put("csrf_token", csrf)
                .put("csrf", csrf)
                .put("build", 0)
                .put("mobi_app", "web");

        try (Response response = NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders)) {
            JSONObject result = new JSONObject(Objects.requireNonNull(response.body()).string());
            if (result.optInt("code", -1) != 0) {
                throw new JSONException(result.optString("message", result.optString("msg", "删除私信失败")));
            }
            return result;
        }
    }

    private static String getDevId() {
        char[] b = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] s = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".toCharArray();
        for (int i = 0; i < s.length; i++) {
            if ('-' == s[i] || '4' == s[i]) {
                continue;
            }
            int randomInt = (int) (16 * Math.random());
            if ('x' == s[i]) {
                s[i] = b[randomInt];
            } else {
                s[i] = b[3 & randomInt | 8];
            }
        }
        return new String(s);
    }

    // 由于接口特殊性定制的textReplaceEmote
    public static SpannableStringBuilder textReplaceEmote(String text, JSONArray emote, float scale, Context context) throws JSONException, ExecutionException, InterruptedException {
        SpannableStringBuilder result = new SpannableStringBuilder(text);
        if (emote != null && emote.length() > 0) {
            for (int i = 0; i < emote.length(); i++) {    //遍历每一个表情包
                JSONObject key = emote.getJSONObject(i);

                String name = key.getString("text");
                String emoteUrl = key.getString("url");
                int size = key.getInt("size");  //B站十分贴心的帮你把表情包大小都写好了，快说谢谢蜀黍

                EmoteUtil.replaceSingle(result, name, emoteUrl, size, scale, context);
            }
        }
        return result;
    }
}
