package com.RobinNotBad.BiliClient.api;

import android.net.Uri;
import android.text.SpannableString;
import android.util.Pair;

import com.RobinNotBad.BiliClient.model.MessageCard;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import okhttp3.Response;

public class MessageApi {
    private static final String NOTICE_FEED = "https://api.bilibili.com/x/msgfeed/";
    private static final String SYSTEM_FEED = "https://message.bilibili.com/x/sys-msg/";
    private static final String PRIVATE_SESSIONS = "https://api.vc.bilibili.com/session_svr/v1/session_svr/";
    private static final String WEB_PARAMS = "&build=0&mobi_app=web";

    /** Keeps every acknowledgement bound to the account shown before confirmation. */
    public static final class ReadSession {
        private final long account;
        private final String sessionToken;
        private final String csrf;
        private final ArrayList<String> headers;

        private ReadSession(long account, String cookie) {
            this.account = account;
            sessionToken = NetWorkUtil.getInfoFromCookie("SESSDATA", cookie);
            csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookie);
            headers = new ArrayList<>(Arrays.asList("Cookie", cookie, "User-Agent", NetWorkUtil.USER_AGENT_WEB,
                    "Origin", "https://message.bilibili.com", "Referer", "https://message.bilibili.com/"));
        }

        public static ReadSession capture(long account) throws IOException {
            String cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            ReadSession session = new ReadSession(account, cookie);
            String cookieMid = NetWorkUtil.getInfoFromCookie("DedeUserID", cookie);
            if (account <= 0 || session.sessionToken.isEmpty() || session.csrf.isEmpty()
                    || !session.isCurrent() || (!cookieMid.isEmpty() && !cookieMid.equals(Long.toString(account)))) {
                throw new IOException("登录信息已变化，请返回消息页重试");
            }
            return session;
        }

        public boolean isCurrent() {
            String cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            return account == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                    && sessionToken.equals(NetWorkUtil.getInfoFromCookie("SESSDATA", cookie))
                    && csrf.equals(NetWorkUtil.getInfoFromCookie("bili_jct", cookie));
        }

        private void requireCurrent() throws IOException {
            if (!isCurrent()) throw new IOException("账号已切换");
        }

        private JSONObject get(String url) throws IOException, JSONException {
            requireCurrent();
            JSONObject response = NetWorkUtil.getJson(url, headers);
            requireCurrent();
            return response;
        }

        public void markAllRead() {
            try {
                JSONObject counts = requireUnreadData(get(NOTICE_FEED + "unread"));
                for (String type : new String[]{"reply", "at", "like", "sys_msg"}) {
                    if (!isCurrent()) return;
                    if (unread(counts, type) == 0) continue;
                    try {
                        if ("sys_msg".equals(type)) {
                            acknowledgeSystem(getSystemNotices());
                        } else {
                            requireSuccess(get(NOTICE_FEED + type + "?platform=web" + WEB_PARAMS));
                        }
                    } catch (IOException | JSONException error) {
                        logReadFailure(type, error);
                    }
                }
            } catch (IOException | JSONException error) {
                logReadFailure("interactions", error);
            }
            if (!isCurrent()) return;
            try {
                markPrivateRead();
            } catch (IOException | JSONException error) {
                logReadFailure("private", error);
            }
        }

        private void markPrivateRead() throws IOException, JSONException {
            long remaining = parsePrivateUnread(get(PRIVATE_SESSIONS + "single_unread"));
            long endTs = 0;
            HashSet<Long> acknowledged = new HashSet<>();
            while (remaining > 0) {
                String url = PRIVATE_SESSIONS + "get_sessions?session_type=1&size=20&sort_rule=2"
                        + "&group_fold=0&unfollow_fold=0" + WEB_PARAMS;
                if (endTs > 0) url += "&end_ts=" + endTs;
                JSONObject data = requireUnreadData(get(ConfInfoApi.signWBI(url)));
                JSONArray rows = data.optJSONArray("session_list");
                if (rows == null || rows.length() == 0) return;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null || row.optInt("session_type", 1) != 1
                            || row.optInt("system_msg_type") != 0) continue;
                    long uid = number(row, "talker_id");
                    long seqno = number(row, "max_seqno");
                    int count = unread(row, "unread_count");
                    if (uid <= 0 || seqno <= 0 || count == 0 || !acknowledged.add(uid)) continue;
                    requireCurrent();
                    try {
                        requireSuccess(PrivateMsgApi.updateAck(uid, 1, seqno, csrf, headers));
                        remaining -= count;
                    } catch (IOException | JSONException error) {
                        logReadFailure("private_ack", error);
                    }
                }
                if (!data.optBoolean("has_more", false) && data.optInt("has_more", 0) != 1) return;
                long next = number(rows.getJSONObject(rows.length() - 1), "session_ts");
                // A repeated cursor must not turn a partial result into an endless sync.
                if (next <= 0 || (endTs > 0 && next >= endTs)) return;
                endTs = next;
            }
        }

        private ArrayList<JSONObject> getSystemNotices() throws IOException, JSONException {
            ArrayList<JSONObject> notices = new ArrayList<>();
            // The web system feed rejects page_size=35 with business code -6.
            for (String path : new String[]{"query_user_notify?page_size=20", "query_unified_notify?page_size=10"}) {
                JSONObject response = get(SYSTEM_FEED + path + WEB_PARAMS);
                int code = response.optInt("code", -1);
                if (code != 0) {
                    Logu.w("MessageApi", "系统通知请求失败: " + path + " code=" + code);
                    requireSuccess(response);
                }
                JSONObject data = response.optJSONObject("data");
                if (data == null) throw new JSONException("系统通知接口缺少数据");
                JSONArray items = data.optJSONArray("system_notify_list");
                if (items == null) continue;
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null) notices.add(item);
                }
            }
            Collections.sort(notices, (first, second) ->
                    Long.valueOf(number(second, "cursor")).compareTo(number(first, "cursor")));
            return notices;
        }

        private void acknowledgeSystem(ArrayList<JSONObject> notices) throws IOException, JSONException {
            if (notices.isEmpty()) return;
            long cursor = number(notices.get(0), "cursor");
            if (cursor > 0) requireSuccess(get(SYSTEM_FEED + "update_cursor?cursor=" + cursor + "&has_up=0" + WEB_PARAMS));
        }
    }

    private static long number(JSONObject data, String key) {
        try {
            return Long.parseLong(data.optString(key));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void requireSuccess(JSONObject response) throws JSONException {
        int code = response.optInt("code", -1);
        if (code != 0) throw new JSONException("消息接口错误（" + code + "）");
    }

    private static void logReadFailure(String type, Exception error) {
        Logu.w("MessageApi", "已读同步失败：" + type + " (" + error.getClass().getSimpleName() + ")");
    }

    private static JSONObject getUnreadData() throws IOException, JSONException {
        String url = NOTICE_FEED + "unread";
        JSONObject all = NetWorkUtil.getJson(url);
        return requireUnreadData(all);
    }

    static JSONObject requireUnreadData(JSONObject all) throws JSONException {
        int code=all.optInt("code",-1);
        if(code!=0)throw new JSONException("消息未读接口错误（"+code+"）");
        JSONObject data=all.optJSONObject("data");
        if(data==null)throw new JSONException("消息未读接口缺少数据");
        return data;
    }
    private static int unread(JSONObject data,String key) {
        return (int)Math.max(0,Math.min(Integer.MAX_VALUE,data.optLong(key,0)));
    }
    public static int countInteractionUnread(JSONObject data) {
        return (int)Math.min(Integer.MAX_VALUE,(long)unread(data,"at")+unread(data,"reply")
                +unread(data,"like")+unread(data,data.has("system")?"system":"sys_msg"));
    }
    static int parsePrivateUnread(JSONObject all) throws JSONException {
        JSONObject data=requireUnreadData(all);
        // Match ordinary one-to-one conversations. Push, filtered and customer-service
        // buckets do not have corresponding unread conversation rows in this page.
        return (int)Math.min(Integer.MAX_VALUE,(long)unread(data,"follow_unread")+unread(data,"unfollow_unread"));
    }

    public static JSONObject getUnread() throws IOException, JSONException {
        JSONObject data = getUnreadData();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("at", unread(data,"at"));
        jsonObject.put("like", unread(data,"like"));
        jsonObject.put("reply", unread(data,"reply"));
        jsonObject.put("system", unread(data,"sys_msg"));
        return jsonObject;
    }

    public static int checkMessageUnread() throws IOException, JSONException {
        JSONObject data = getUnreadData();
        return countInteractionUnread(data);
    }

    public static int checkPrivateMsgUnread() throws IOException, JSONException {
        String url = PRIVATE_SESSIONS + "single_unread";
        JSONObject all = NetWorkUtil.getJson(url);
        int total=parsePrivateUnread(all);
        JSONObject counts=new JSONObject().put("total",total);
        JSONObject data=all.getJSONObject("data");
        for(String key:new String[]{"follow_unread","unfollow_unread","unfollow_push_msg","dustbin_push_msg",
                "dustbin_unread","biz_msg_unfollow_unread","biz_msg_follow_unread","custom_unread"})
            counts.put(key,unread(data,key));
        com.RobinNotBad.BiliClient.util.DiagnosticLogManager.record("private_unread_counts",counts);
        return total;
    }

    public static int checkGroupMsgUnread() throws IOException, JSONException {
        String url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/my_group_unread";
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            return data.optInt("unread_count", 0);
        }
        return 0;
    }

    private static void requireNoticeData(JSONObject root)throws JSONException {
        int code=root.optInt("code",-1);
        if(code!=0||root.optJSONObject("data")==null)throw new JSONException("消息接口返回错误（"+code+"），请检查登录后重试");
    }

    public static Pair<MessageCard.Cursor, List<MessageCard>> getLikeMsg(long id, long time)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/msgfeed/like?platform=web&build=0&mobi_app=web";
        if (id > 0 && time > 0)
            url += String.format("&id=%s&like_time=%s", id, time);
        JSONObject all = NetWorkUtil.getJson(url);
        requireNoticeData(all);
        if (all.has("data") && !all.isNull("data")) {
            // 所有消息
            ArrayList<MessageCard> totalArray = new ArrayList<>();
            com.RobinNotBad.BiliClient.util.MessageSections.Likes sections=
                    com.RobinNotBad.BiliClient.util.MessageSections.likes(all.getJSONObject("data"),id<=0);
            for (int i = 0; i < sections.items.length(); i++) {
                JSONObject object = sections.items.getJSONObject(i);
                MessageCard likeInfo = new MessageCard();

                ArrayList<UserInfo> userList = new ArrayList<>();
                for (int j = 0; j < object.getJSONArray("users").length(); j++) {
                    JSONObject userArrayInfo = ((JSONObject) object.getJSONArray("users").get(j));
                    userList.add(new UserInfo(userArrayInfo.getLong("mid"), userArrayInfo.getString("nickname"),
                            userArrayInfo.getString("avatar"), "", userArrayInfo.getInt("fans"), 0, 0,
                            userArrayInfo.getBoolean("follow"), "", 0, "", 0));
                }

                likeInfo.id = object.getLong("id");
                likeInfo.readStateKnown=sections.known;
                likeInfo.isNew=sections.latestIds.contains(likeInfo.id);
                likeInfo.user = userList;
                likeInfo.timeStamp = object.getLong("like_time");

                JSONObject item = object.getJSONObject("item");
                com.RobinNotBad.BiliClient.util.MessageTarget target=com.RobinNotBad.BiliClient.util.MessageTarget.like(item);
                likeInfo.businessId = target.type;
                likeInfo.subjectId = target.oid;
                likeInfo.contentId = target.contentId;
                likeInfo.itemUri = item.optString("native_uri",item.optString("uri",""));
                likeInfo.sourceId = item.optLong("source_id", -1);
                likeInfo.rootId = target.root;
                likeInfo.targetId = target.reply;
                likeInfo.itemType = target.kind;

                switch (likeInfo.itemType) {
                    case "video":
                        likeInfo.content = "等总共 " + object.getLong("counts") + " 人点赞了你的视频";
                        VideoCard videoCard = new VideoCard();
                        videoCard.aid = com.RobinNotBad.BiliClient.util.MessageTarget
                                .aidForVideoNavigation(target.oid, target.bvid);
                        videoCard.bvid = target.bvid;
                        videoCard.upName = "";
                        videoCard.title = item.optString("title","");
                        videoCard.cover = item.optString("image","");
                        videoCard.view = "";
                        likeInfo.videoCard = videoCard;
                        break;
                    case "reply":
                        likeInfo.content = "等总共 " + object.getLong("counts") + " 人点赞了你的评论";
                        Reply replyInfo = new Reply();
                        replyInfo.rpid = target.reply;
                        replyInfo.sender = null;
                        replyInfo.message = new SpannableString(item.optString("title",""));
                        replyInfo.pictureList = new ArrayList<>();
                        replyInfo.likeCount = 0;
                        replyInfo.upLiked = false;
                        replyInfo.upReplied = false;
                        replyInfo.liked = false;
                        replyInfo.childCount = 0;
                        replyInfo.ofBvid = target.bvid;
                        replyInfo.childMsgList = new ArrayList<>();
                        likeInfo.replyInfo = replyInfo;
                        break;
                    case "dynamic":
                    case "album":
                    case "opus":
                        likeInfo.content = "等总共 " + object.getLong("counts") + " 人点赞了你的动态";
                        Reply replyInfo_dynamic = new Reply();
                        replyInfo_dynamic.rpid = target.oid;
                        replyInfo_dynamic.sender = null;
                        replyInfo_dynamic.message = new SpannableString(item.optString("title",""));
                        replyInfo_dynamic.pictureList = new ArrayList<>();
                        replyInfo_dynamic.likeCount = 0;
                        replyInfo_dynamic.upLiked = false;
                        replyInfo_dynamic.upReplied = false;
                        replyInfo_dynamic.liked = false;
                        replyInfo_dynamic.isDynamic = true;
                        replyInfo_dynamic.childCount = 0;
                        replyInfo_dynamic.childMsgList = new ArrayList<>();
                        likeInfo.dynamicInfo = replyInfo_dynamic;
                        break;
                    case "article":
                        likeInfo.content = "等总共 " + object.getLong("counts") + " 人点赞了你的专栏";
                        // 实在是抽象 但是我没时间改那么多
                        Reply replyChildInfo = new Reply();
                        replyChildInfo.rpid = item.optLong("source_id", item.optLong("target_id", -1));
                        replyChildInfo.message = new SpannableString(item.getString("title"));
                        replyChildInfo.childCount = 0;
                        likeInfo.replyInfo = replyChildInfo;
                        break;
                    default:
                        likeInfo.content = "无法识别这个类别：" + likeInfo.itemType;
                }

                likeInfo.getType = MessageCard.GET_TYPE_LIKE;
                totalArray.add(likeInfo);
            }

            JSONObject cursor = sections.cursor;
            return new Pair<>(cursor == null ? null
                    : new MessageCard.Cursor(cursor.optBoolean("is_end", true), cursor.optLong("id", -1),
                    cursor.optLong("time", -1)),
                    totalArray);
        } else {
            return new Pair<>(null, new ArrayList<>());
        }
    }

    public static Pair<MessageCard.Cursor, List<MessageCard>> getReplyMsg(long id, long time)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/msgfeed/reply?platform=web&build=0&mobi_app=web";
        if (id > 0 && time > 0)
            url += String.format("&id=%s&reply_time=%s", id, time);
        JSONObject all = NetWorkUtil.getJson(url);
        requireNoticeData(all);
        if (all.has("data") && !all.isNull("data")) {
            ArrayList<MessageCard> totalArray = new ArrayList<>();
            for (int i = 0; i < all.getJSONObject("data").getJSONArray("items").length(); i++) {
                JSONObject object = ((JSONObject) all.getJSONObject("data").getJSONArray("items").get(i));
                MessageCard replyInfo = new MessageCard();

                List<UserInfo> userList = new ArrayList<>();
                userList.add(new UserInfo(object.getJSONObject("user").getLong("mid"),
                        object.getJSONObject("user").getString("nickname"),
                        object.getJSONObject("user").getString("avatar"), "",
                        object.getJSONObject("user").getInt("fans"), 0, 0,
                        object.getJSONObject("user").getBoolean("follow"), "", 0, "", 0));
                replyInfo.user = userList;

                replyInfo.id = object.getLong("id");
                replyInfo.timeStamp = object.getLong("reply_time");

                JSONObject item = object.getJSONObject("item");
                replyInfo.businessId = item.getInt("business_id");
                replyInfo.subjectId = item.getLong("subject_id");
                replyInfo.itemUri = item.optString("uri", "");
                replyInfo.contentId = parseContentId(item);
                replyInfo.sourceId = item.optLong("source_id", -1);
                replyInfo.rootId = item.optLong("root_id", -1);
                replyInfo.targetId = item.optLong("target_id", -1);
                replyInfo.itemType = item.getString("type");
                replyInfo.getType = MessageCard.GET_TYPE_REPLY;
                replyInfo.content = item.getString("source_content");

                switch (replyInfo.itemType) {
                    case "video":
                        VideoCard videoCard = new VideoCard();
                        videoCard.aid = 0;
                        videoCard.bvid = item.getString("uri").replace("https://www.bilibili.com/video/BV", "");
                        videoCard.upName = "";
                        videoCard.title = item.getString("title");
                        videoCard.cover = item.getString("image");
                        videoCard.view = "";
                        replyInfo.videoCard = videoCard;
                        break;
                    case "reply":
                        Reply replyChildInfo = new Reply();
                        replyChildInfo.rpid = item.getLong("target_id");
                        replyChildInfo.sender = null;
                        replyChildInfo.message = new SpannableString("[评论] " + item.getString("title"));
                        replyChildInfo.pictureList = new ArrayList<>();
                        replyChildInfo.likeCount = 0;
                        replyChildInfo.upLiked = false;
                        replyChildInfo.upReplied = false;
                        replyChildInfo.liked = false;
                        replyChildInfo.childCount = 0;
                        replyChildInfo.ofBvid = item.getString("uri").replace("https://www.bilibili.com/video/", "");
                        replyChildInfo.childMsgList = new ArrayList<>();
                        replyInfo.replyInfo = replyChildInfo;
                        break;
                    case "dynamic":
                    case "album":
                        Reply replyChildInfo_dynamic = new Reply();
                        replyChildInfo_dynamic.rpid = item.optLong("source_id", item.optLong("target_id", -1));
                        replyChildInfo_dynamic.sender = null;
                        replyChildInfo_dynamic.message = new SpannableString("[动态] " + item.getString("title"));
                        replyChildInfo_dynamic.pictureList = new ArrayList<>();
                        replyChildInfo_dynamic.likeCount = 0;
                        replyChildInfo_dynamic.upLiked = false;
                        replyChildInfo_dynamic.upReplied = false;
                        replyChildInfo_dynamic.liked = false;
                        replyChildInfo_dynamic.childCount = 0;
                        replyChildInfo_dynamic.isDynamic = true;
                        replyChildInfo_dynamic.childMsgList = new ArrayList<>();
                        replyInfo.dynamicInfo = replyChildInfo_dynamic;
                        break;
                    case "article":
                        Reply replyChildInfo_article = new Reply();
                        replyChildInfo_article.rpid = item.optLong("source_id", item.optLong("target_id", -1));
                        replyChildInfo_article.message = new SpannableString("[专栏] " + item.getString("title"));
                        replyChildInfo_article.childCount = 0;
                        replyInfo.replyInfo = replyChildInfo_article;
                        break;
                    default:
                        replyInfo.content = "无法识别这个类别：" + replyInfo.itemType;
                }

                com.RobinNotBad.BiliClient.util.MessageSections.applyBoundary(replyInfo,all.getJSONObject("data"));
                totalArray.add(replyInfo);
            }
            JSONObject cursor = all.getJSONObject("data").optJSONObject("cursor");
            return new Pair<>(cursor == null ? null
                    : new MessageCard.Cursor(cursor.optBoolean("is_end", true), cursor.optLong("id", -1),
                    cursor.optLong("time", -1)),
                    totalArray);
        } else {
            return new Pair<>(null, new ArrayList<>());
        }
    }

    public static Pair<MessageCard.Cursor, List<MessageCard>> getAtMsg(long id, long time)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/msgfeed/at?platform=web&build=0&mobi_app=web";
        if (id > 0 && time > 0)
            url += String.format("&id=%s&at_time=%s", id, time);
        JSONObject all = NetWorkUtil.getJson(url);
        requireNoticeData(all);
        if (all.has("data") && !all.isNull("data")) {
            ArrayList<MessageCard> totalArray = new ArrayList<>();
            for (int i = 0; i < all.getJSONObject("data").getJSONArray("items").length(); i++) {
                JSONObject object = ((JSONObject) all.getJSONObject("data").getJSONArray("items").get(i));
                MessageCard replyInfo = new MessageCard();

                List<UserInfo> userList = new ArrayList<>();
                userList.add(new UserInfo(object.getJSONObject("user").getLong("mid"),
                        object.getJSONObject("user").getString("nickname"),
                        object.getJSONObject("user").getString("avatar"), "",
                        object.getJSONObject("user").getInt("fans"), 0, 0,
                        object.getJSONObject("user").getBoolean("follow"), "", 0, "", 0));
                replyInfo.user = userList;

                replyInfo.id = object.getLong("id");
                replyInfo.timeStamp = object.getLong("at_time");
                replyInfo.content = "提到了我";

                JSONObject item = object.getJSONObject("item");
                String itemType = item.getString("type");
                if (itemType.equals("video")) {
                    VideoCard videoCard = new VideoCard();
                    videoCard.aid = 0;
                    videoCard.bvid = item.getString("uri")
                            .replace("https://www.bilibili.com/video/BV", "");
                    videoCard.upName = "";
                    videoCard.title = item.getString("title");
                    videoCard.cover = item.getString("image");
                    videoCard.view = "";
                    replyInfo.videoCard = videoCard;
                } else if (itemType.equals("reply")) {
                    Reply replyChildInfo = new Reply();
                    replyChildInfo.rpid = item.optLong("source_id", item.optLong("target_id", -1));
                    replyChildInfo.sender = null;
                    replyChildInfo.message = new SpannableString(
                            "[评论] " + item.getString("title"));
                    replyChildInfo.pictureList = new ArrayList<>();
                    replyChildInfo.likeCount = 0;
                    replyChildInfo.upLiked = false;
                    replyChildInfo.upReplied = false;
                    replyChildInfo.liked = false;
                    replyChildInfo.childCount = 0;
                    replyChildInfo.ofBvid = item.getString("uri")
                            .replace("https://www.bilibili.com/video/", "");
                    replyChildInfo.childMsgList = new ArrayList<>();
                    replyInfo.replyInfo = replyChildInfo;
                } else if (itemType.equals("dynamic") || itemType.equals("album") || itemType.equals("opus")) {
                    Reply replyChildInfo = new Reply();
                    replyChildInfo.rpid = item.optLong("source_id", item.optLong("target_id", -1));
                    replyChildInfo.sender = null;
                    replyChildInfo.message = new SpannableString(
                            "[动态] " + item.optString("title", item.optString("source_content", "")));
                    replyChildInfo.pictureList = new ArrayList<>();
                    replyChildInfo.likeCount = 0;
                    replyChildInfo.upLiked = false;
                    replyChildInfo.upReplied = false;
                    replyChildInfo.liked = false;
                    replyChildInfo.isDynamic = true;
                    replyChildInfo.childCount = 0;
                    replyChildInfo.childMsgList = new ArrayList<>();
                    replyInfo.dynamicInfo = replyChildInfo;
                } else if (itemType.equals("article")) {
                    Reply replyChildInfo = new Reply();
                    replyChildInfo.rpid = item.optLong("source_id", item.optLong("target_id", -1));
                    replyChildInfo.message = new SpannableString(
                            "[专栏] " + item.getString("title"));
                    replyChildInfo.childCount = 0;
                    replyInfo.replyInfo = replyChildInfo;
                }
                replyInfo.businessId = item.getInt("business_id");
                replyInfo.subjectId = item.getLong("subject_id");
                replyInfo.itemUri = item.optString("uri", "");
                replyInfo.contentId = parseContentId(item);
                replyInfo.sourceId = item.optLong("source_id", -1);
                replyInfo.rootId = item.optLong("root_id", -1);
                replyInfo.targetId = item.optLong("target_id", -1);
                replyInfo.itemType = item.getString("type");
                replyInfo.getType = MessageCard.GET_TYPE_AT;

                com.RobinNotBad.BiliClient.util.MessageSections.applyBoundary(replyInfo,all.getJSONObject("data"));
                totalArray.add(replyInfo);
            }

            JSONObject cursor = all.getJSONObject("data").optJSONObject("cursor");
            return new Pair<>(cursor == null ? null
                    : new MessageCard.Cursor(cursor.optBoolean("is_end", true), cursor.optLong("id", -1),
                    cursor.optLong("time", -1)),
                    totalArray);
        } else {
            return new Pair<>(null, new ArrayList<>());
        }
    }

    private static long parseContentId(JSONObject item) {
        long itemId = item.optLong("item_id", -1);
        if (itemId > 0) return itemId;

        String uri = item.optString("uri", "").trim();
        if (uri.isEmpty()) return -1;
        try {
            for (String segment : Uri.parse(uri).getPathSegments()) {
                String value = segment;
                if (value.startsWith("cv")) value = value.substring(2);
                try {
                    long id = Long.parseLong(value);
                    if (id > 0) return id;
                } catch (NumberFormatException ignored) {
                    // Video BV identifiers and other non-numeric path segments are skipped.
                }
            }
        } catch (RuntimeException ignored) {
            // Malformed notification links should not make the entire @ feed fail.
        }
        return -1;
    }

    public static ArrayList<MessageCard> getSystemMsg(long account) throws IOException, JSONException {
        ReadSession session = ReadSession.capture(account);
        ArrayList<JSONObject> notices = session.getSystemNotices();
        ArrayList<MessageCard> cards = new ArrayList<>();
        for (JSONObject object : notices) {
            MessageCard card = new MessageCard();
            card.user = new ArrayList<>();
            card.id = number(object, "id");
            card.timeDesc = object.optString("time_at");
            card.content = object.optString("title") + "\n" + object.optString("content");
            cards.add(card);
        }
        try {
            session.acknowledgeSystem(notices);
        } catch (IOException | JSONException error) {
            logReadFailure("sys_msg", error);
        }
        return cards;
    }

    public static JSONObject getMsgSettings() throws IOException, JSONException {
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct",
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        String url = "https://api.vc.bilibili.com/link_setting/v1/link_setting/get";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("msg_notify", 1)
                .put("show_unfollowed_msg", 1)
                .put("build", 0)
                .put("mobi_app", "web")
                .put("csrf_token", csrf)
                .put("csrf", csrf);

        Response response = NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders);
        String result = response.body().string();
        return new JSONObject(result);
    }

    public static JSONObject setMsgSettings(JSONObject settings) throws IOException, JSONException {
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct",
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        String url = "https://api.vc.bilibili.com/link_setting/v1/link_setting/set";
        NetWorkUtil.FormData formData = new NetWorkUtil.FormData()
                .put("csrf_token", csrf)
                .put("csrf", csrf)
                .put("build", 0)
                .put("mobi_app", "web");

        if (settings.has("msg_notify"))
            formData.put("msg_notify", settings.getInt("msg_notify"));
        if (settings.has("show_unfollowed_msg"))
            formData.put("show_unfollowed_msg", settings.getInt("show_unfollowed_msg"));
        if (settings.has("is_group_fold"))
            formData.put("is_group_fold", settings.getInt("is_group_fold"));
        if (settings.has("should_receive_group"))
            formData.put("should_receive_group", settings.getInt("should_receive_group"));
        if (settings.has("receive_unfollow_msg"))
            formData.put("receive_unfollow_msg", settings.getInt("receive_unfollow_msg"));
        if (settings.has("ai_intercept"))
            formData.put("ai_intercept", settings.getInt("ai_intercept"));

        Response response = NetWorkUtil.post(url, formData.toString(), NetWorkUtil.webHeaders);
        String result = response.body().string();
        return new JSONObject(result);
    }
}
