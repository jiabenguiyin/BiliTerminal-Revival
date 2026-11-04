package com.RobinNotBad.BiliClient.api;

import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.model.ContentType;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.ReplyStateSync;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.Result;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

import okhttp3.Response;

//腕上哔哩那边注释里写了一连串的麻烦麻烦麻烦，顿时预感不妙
//其实还好
//用Log直接替代注释了，应该都能看懂吧
//2023-07-22

public class ReplyApi {

    public static final int REPLY_TYPE_VIDEO_CHILD = 0;
    public static final int REPLY_TYPE_VIDEO = 1;
    public static final int REPLY_TYPE_PHOTO = 11;
    public static final int REPLY_TYPE_ARTICLE = 12;
    public static final int REPLY_TYPE_AUDIO = 14;
    public static final int REPLY_TYPE_DYNAMIC_CHILD = 11;
    public static final int REPLY_TYPE_DYNAMIC = 17;
    public static final String TOP_TIP = "[置顶]";

    public static int resolveDynamicCommentType(String dynamicType, int resourceType) {
        if (dynamicType != null) {
            switch (dynamicType) {
                case "DYNAMIC_TYPE_AV":
                case "DYNAMIC_TYPE_PGC":
                case "DYNAMIC_TYPE_UGC_SEASON":
                    return REPLY_TYPE_VIDEO;
                case "DYNAMIC_TYPE_DRAW":
                    return REPLY_TYPE_PHOTO;
                case "DYNAMIC_TYPE_ARTICLE":
                    return REPLY_TYPE_ARTICLE;
                case "DYNAMIC_TYPE_MUSIC":
                    return REPLY_TYPE_AUDIO;
                case "DYNAMIC_TYPE_FORWARD":
                case "DYNAMIC_TYPE_WORD":
                case "DYNAMIC_TYPE_LIVE":
                case "DYNAMIC_TYPE_LIVE_RCMD":
                case "DYNAMIC_TYPE_COMMON_SQUARE":
                    return REPLY_TYPE_DYNAMIC;
            }
        }

        switch (resourceType) {
            case 2:
                return REPLY_TYPE_PHOTO;
            case 8:
                return REPLY_TYPE_VIDEO;
            case 64:
                return REPLY_TYPE_ARTICLE;
            case 256:
                return REPLY_TYPE_AUDIO;
            default:
                return REPLY_TYPE_DYNAMIC;
        }
    }

    /**
     * @param originId       评论区id，为评论所属内容的id，例如视频aid
     * @param rpid           父评论的id，无父评论则为0
     * @param pageNumber     分页，需要拉取的评论的页号
     * @param type           评论所属内容类型
     * @param sort           评论区排序方式，0=时间；1=点赞数量；2=回复数量
     * @param replyArrayList 填充数组
     * @return -1错误,0正常，1到底了
     * @throws JSONException json解析异常
     * @throws IOException   网络异常
     */
    public static int getReplies(long originId, long rpid, int pageNumber, ContentType type, int sort, List<Reply> replyArrayList) throws JSONException, IOException {
        long account = ReplyStateSync.account(), read = ReplyStateSync.STORE.beginRead();
        String url = "https://api.bilibili.com/x/v2/reply" + (rpid == 0 ? "" : "/reply") + "?pn=" + pageNumber
                + "&type=" + type.getTypeCode() + "&oid=" + originId + "&sort=" + sort + (rpid == 0 ? "" : ("&root=" + rpid));
        JSONObject all = NetWorkUtil.getJson(url);

        //Log.e("debug-评论区",all.toString());

        int size = replyArrayList.size();
        if (all.getInt("code") == 0 && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONObject page = data.getJSONObject("page");
            if (data.optJSONObject("root") != null)
                ReplyStateSync.observe(new Reply(true, data.getJSONObject("root")), type.getTypeCode(), originId, account, read);
            if (rpid == 0 && pageNumber == 1) analyzeTopReplies(data, replyArrayList);
            ReplyStateSync.observe(replyArrayList, type.getTypeCode(), originId, account, read);
            if (!data.isNull("replies") && page.getInt("size") > 0) {
                JSONArray replies = data.getJSONArray("replies");
                analyzeReplyArray(rpid == 0, replies, replyArrayList);
                ReplyStateSync.observe(replyArrayList, type.getTypeCode(), originId, account, read);
                if (replyArrayList.size() == size) return 1;
                else return 0;
            } else return 1;
        } else return -1;
    }

    public static Result<Reply> getRootReply(ContentType contentType, long originId, long rpid) {
        long account = ReplyStateSync.account(), read = ReplyStateSync.STORE.beginRead();
        String url = "https://api.bilibili.com/x/v2/reply/reply" + "?type=" + contentType.getTypeCode() + "&oid=" + originId + "&root=" + rpid;
        try {
            JSONObject json = NetWorkUtil.getJson(url);
            if (json.getInt("code") != 0 || json.isNull("data")) {
                return Result.failure(new Exception(json.getString("message")));
            }
            JSONObject data = json.getJSONObject("data");
            if (data.isNull("root")) {
                return Result.failure(new Exception("未找到根评论"));
            }
            Reply reply = new Reply(true, data.getJSONObject("root"));
            if (!ReplyStateSync.observe(reply, contentType.getTypeCode(), originId, account, read))
                return Result.failure(new Exception("评论已删除或账号已切换，请重新打开评论区"));
            return Result.success(reply);
        } catch (Exception e) {
            return Result.failure(e);
        }
    }


    /**
     * 获取评论列表（/x/v2/reply/wbi/main）
     *
     * @param oid        评论区oid
     * @param rpid       指定要寻找的评论的rpid
     * @param pagination 页
     * @param type       评论区类型
     * @param sort       排序方式
     * @return 返回码（0=继续加载，1=到底了，-1=错误）与下一页的pagination
     */
    @NonNull
    public static Pair<Integer, String> getRepliesLazy(long oid, long rpid, String pagination, int type, int sort, List<Reply> replyArrayList) throws JSONException, IOException {
        long account = ReplyStateSync.account(), read = ReplyStateSync.STORE.beginRead();
        NetWorkUtil.FormData reqData = new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("type", type)
                .put("oid", oid)
                .put("plat", 1)
                .put("web_location", "1315875")
                .put("mode", sort);
        reqData.put("pagination_str", new JSONObject().put("offset", TextUtils.isEmpty(pagination) ? "" : pagination));
        if (rpid > 0) reqData.put("seek_rpid", rpid);
        String url = "https://api.bilibili.com/x/v2/reply/wbi/main" + reqData;

        //Log.e("debug-评论区链接", url);

        JSONObject all = NetWorkUtil.getJson(ConfInfoApi.signWBI(url));

        //Log.e("debug-评论区",all.toString());

        if (all.getInt("code") == 0 && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONObject cursor = data.getJSONObject("cursor");
            if (rpid <= 0 && cursor.optBoolean("is_begin", true)) analyzeTopReplies(data, replyArrayList);
            ReplyStateSync.observe(replyArrayList, type, oid, account, read);
            if (!data.isNull("replies") && data.getJSONArray("replies").length() > 0) {
                JSONArray replies = data.getJSONArray("replies");
                analyzeReplyArray(true, replies, replyArrayList);
                ReplyStateSync.observe(replyArrayList, type, oid, account, read);
                JSONObject paginationReply = cursor.optJSONObject("pagination_reply");
                String nextOffset = paginationReply == null ? null : paginationReply.optString("next_offset");
                if (cursor.optBoolean("is_end", false) || TextUtils.isEmpty(nextOffset)) {
                    return new Pair<>(1, "");
                } else {
                    return new Pair<>(0, nextOffset);
                }
            } else return new Pair<>(1, "");
        } else return new Pair<>(-1, "");
    }  //-1错误,0正常，1到底了

    public static void analyzeReplyArray(boolean isRoot, JSONArray replies, List<Reply> replyArrayList) throws JSONException {
        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply = replies.getJSONObject(i);
            if (containsReply(replyArrayList, reply.optLong("rpid"))) continue;
            Reply replyReturn = new Reply(isRoot, reply);
            replyArrayList.add(replyReturn);
        }
    }

    private static boolean containsReply(List<Reply> replies, long rpid) {
        for (Reply reply : replies) if (reply.rpid == rpid) return true;
        return false;
    }

    static void analyzeTopReplies(JSONObject data, List<Reply> output) throws JSONException {
        JSONArray top = data.optJSONArray("top_replies");
        JSONObject upper = data.optJSONObject("upper");
        if ((top == null || top.length() == 0) && upper != null && upper.optJSONObject("top") != null) {
            top = new JSONArray().put(upper.getJSONObject("top"));
        }
        if (top == null) return;
        for (int i = 0; i < top.length(); i++) {
            JSONObject item = top.optJSONObject(i);
            if (item == null || containsReply(output, item.optLong("rpid"))) continue;
            Reply reply = new Reply(true, item);
            reply.setTop(true);
            output.add(reply);
        }
    }

    public static Pair<Integer, Reply> sendReply(long oid, long root, long parent, String text, int type) throws IOException, JSONException {
        return sendReply(oid, root, parent, text, type, null);
    }

    public static Pair<Integer, Reply> sendReply(long oid, long root, long parent, String text,
                                                 int type, String picturesJson) throws IOException, JSONException {
        long startedAt = android.os.SystemClock.elapsedRealtime();
        String url = "https://api.bilibili.com/x/v2/reply/add";
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct",
                SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        if (csrf.isEmpty()) csrf = SharedPreferencesUtil.getString("csrf", "");
        NetWorkUtil.FormData form = new NetWorkUtil.FormData()
                .put("oid", oid)
                .put("type", type)
                .put("message", text)
                .put("jsonp", "jsonp")
                .put("csrf", csrf);
        if (picturesJson != null && !picturesJson.isEmpty()) form.put("pictures", picturesJson);
        if (root != 0) form.put("root", root).put("parent", parent);
        String arg = form.toString();
        JSONObject result;
        int httpStatus;
        try (okhttp3.Response response = NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders)) {
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("评论接口 HTTP " + response.code());
            httpStatus = response.code();
            result = new JSONObject(response.body().string());
        }
        JSONObject reply = null;
        if (result.has("data") && !result.isNull("data") && result.getJSONObject("data").has("reply") && !result.getJSONObject("data").isNull("reply")) {
            reply = result.getJSONObject("data").getJSONObject("reply");
        }
        JSONObject sendDetails = new JSONObject();
        sendDetails.put("oid", oid);
        sendDetails.put("type", type);
        sendDetails.put("root", root);
        sendDetails.put("parent", parent);
        sendDetails.put("http_status", httpStatus);
        sendDetails.put("code", result.optInt("code", -1));
        sendDetails.put("message", result.optString("message", ""));
        sendDetails.put("has_reply", reply != null);
        sendDetails.put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - startedAt);
        if (reply != null) sendDetails.put("rpid", reply.optLong("rpid", 0));
        DiagnosticLogManager.record("reply_send_result", sendDetails);
        return new Pair<>(result.getInt("code"), reply == null ? null : new Reply(root != 0, reply));
    }

    /**
     * The add endpoint can return before a reply is visible to the public
     * listing. Do not show a local-only comment as successfully published.
     */
    public static boolean isReplyVisible(long oid, long rpid, int type)
            throws IOException, JSONException {
        if (rpid <= 0) return false;
        final long[] retryDelays = {250L, 500L, 1000L, 2000L};
        for (int attempt = 0; attempt < retryDelays.length; attempt++) {
            long startedAt = android.os.SystemClock.elapsedRealtime();
            ArrayList<Reply> replies = new ArrayList<>();
            boolean visible = false;
            String errorName = "";
            try {
                Pair<Integer, String> page = getRepliesLazy(oid, rpid, "", type, 2, replies);
                for (Reply reply : replies) {
                    if (reply != null && reply.rpid == rpid) {
                        visible = true;
                        break;
                    }
                    if (reply != null && reply.childMsgList != null) {
                        for (Reply child : reply.childMsgList) {
                            if (child != null && child.rpid == rpid) {
                                visible = true;
                                break;
                            }
                        }
                    }
                    if (visible) break;
                }
                JSONObject details = new JSONObject();
                details.put("oid", oid);
                details.put("type", type);
                details.put("rpid", rpid);
                details.put("attempt", attempt + 1);
                details.put("result", page.first);
                details.put("reply_count", replies.size());
                details.put("visible", visible);
                details.put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - startedAt);
                DiagnosticLogManager.record("reply_visibility_check", details);
            } catch (Exception error) {
                errorName = error.getClass().getSimpleName();
                JSONObject details = new JSONObject();
                details.put("oid", oid);
                details.put("type", type);
                details.put("rpid", rpid);
                details.put("attempt", attempt + 1);
                details.put("visible", false);
                details.put("error", errorName);
                details.put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - startedAt);
                DiagnosticLogManager.record("reply_visibility_check", details);
            }
            if (visible) return true;
            if (attempt < retryDelays.length - 1) {
                try {
                    Thread.sleep(retryDelays[attempt]);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    public static Pair<Integer, Reply> sendReply(long oid, long root, long parent, String text) throws IOException, JSONException {
        return sendReply(oid, root, parent, text, REPLY_TYPE_VIDEO);
    }

    public static Pair<Integer, Reply> sendDynamicReply(long oid, long root, long parent, String text) throws IOException, JSONException {
        return sendReply(oid, root, parent, text, REPLY_TYPE_DYNAMIC);
    }

    public static int likeReply(long oid, long root, boolean action) throws IOException, JSONException {
        return likeReply(oid, root, REPLY_TYPE_VIDEO, action);
    }

    public static int likeReply(long oid, long root, int type, boolean action) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/action";
        String arg = "oid=" + oid + "&type=" + type + "&rpid=" + root + "&action=" + (action ? "1" : "0") + "&jsonp=jsonp&csrf=" + SharedPreferencesUtil.getString("csrf", "");
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-点赞评论", result.toString());
        return result.getInt("code");
    }

    public static int hateReply(long oid, long rpid, int type, boolean action) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/hate";
        String reqBody = new NetWorkUtil.FormData()
                .put("oid", oid)
                .put("type", type)
                .put("rpid", rpid)
                .put("action", action ? 1 : 0)
                .put("jsonp", "jsonp")
                .put("csrf", SharedPreferencesUtil.getString("csrf", ""))
                .toString();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, reqBody, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-点踩评论", result.toString());
        return result.getInt("code");
    }

    public static final int REPORT_ALREADY_REPORTED = 12008;
    public static final int REPORT_NOTE_MAX_LENGTH = 200;
    private static final Set<String> REPORTS_IN_FLIGHT = new HashSet<>();

    /** Credentials belong to the account which opened the confirmation, never to a later login. */
    public static final class ReportAccount {
        private final long mid;
        private final String cookie, csrf;

        private ReportAccount(long mid, String cookie, String csrf) {
            this.mid = mid;
            this.cookie = cookie;
            this.csrf = csrf;
        }

        public boolean isCurrent() {
            synchronized (AccountManager.class) {
                String current = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
                return mid == ReplyStateSync.account() && AccountManager.sameSession(cookie, current)
                        && csrf.equals(reportCsrf(current));
            }
        }
    }

    private static String reportCsrf(String cookie) {
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookie);
        return csrf.isEmpty() ? SharedPreferencesUtil.getString("csrf", "") : csrf;
    }

    public static ReportAccount captureReportAccount() {
        synchronized (AccountManager.class) {
            long mid = ReplyStateSync.account();
            String cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            String csrf = reportCsrf(cookie);
            if (mid <= 0 || NetWorkUtil.getInfoFromCookie("SESSDATA", cookie).isEmpty()
                    || !String.valueOf(mid).equals(NetWorkUtil.getInfoFromCookie("DedeUserID", cookie))
                    || csrf.trim().isEmpty())
                throw new IllegalStateException("Report requires an authenticated account");
            return new ReportAccount(mid, cookie, csrf);
        }
    }

    public static boolean isValidReportTarget(long oid, long rpid, int type) {
        if (oid <= 0 || rpid <= 0) return false;
        for (ContentType value : ContentType.values()) if (value.getTypeCode() == type) return true;
        return false;
    }

    /** An optional Other note is limited locally to 2–200 Unicode code points when supplied. */
    public static boolean isValidReportNote(String content) {
        if (content == null || content.isEmpty()) return true;
        String note = content.trim();
        int length = note.codePointCount(0, note.length());
        return length >= 2 && length <= REPORT_NOTE_MAX_LENGTH;
    }

    static String buildReportReplyForm(long oid, long rpid, int type, int reason,
                                       String content, String csrf) {
        if (!isValidReportTarget(oid, rpid, type)) throw new IllegalArgumentException("Invalid report target");
        if (reason < 0 || reason > 17) throw new IllegalArgumentException("Invalid report reason");
        if (csrf == null || csrf.trim().isEmpty()) throw new IllegalArgumentException("Missing report CSRF");
        if (reason == 0 && !isValidReportNote(content)) throw new IllegalArgumentException("Invalid report note");
        NetWorkUtil.FormData form = new NetWorkUtil.FormData().put("type", type).put("oid", oid)
                .put("rpid", rpid).put("reason", reason).put("csrf", csrf);
        if (reason == 0 && content != null && !content.isEmpty()) form.put("content", content.trim());
        return form.toString();
    }

    /** Called only after explicit confirmation. No retry: a lost response may already be accepted. */
    public static int reportReply(long oid, long rpid, int type, int reason, String content,
                                   ReportAccount account) throws IOException, JSONException {
        if (account == null) throw new IllegalStateException("Missing report account");
        String body = buildReportReplyForm(oid, rpid, type, reason, content, account.csrf);
        String key = account.mid + ":" + type + ":" + oid + ":" + rpid;
        synchronized (REPORTS_IN_FLIGHT) {
            if (!REPORTS_IN_FLIGHT.add(key)) throw new IllegalStateException("Report already in flight");
        }
        try {
            ArrayList<String> headers = new ArrayList<>();
            synchronized (AccountManager.class) {
                if (!account.isCurrent()) throw new IllegalStateException("Report account changed");
                // Snapshot headers too: global webHeaders can change while the request is queued.
                headers.add("Cookie"); headers.add(account.cookie);
                headers.add("Origin"); headers.add("https://www.bilibili.com");
                headers.add("Referer"); headers.add("https://www.bilibili.com/");
                headers.add("User-Agent"); headers.add(NetWorkUtil.USER_AGENT_WEB);
            }
            try (Response response = NetWorkUtil.post("https://api.bilibili.com/x/v2/reply/report", body, headers)) {
                if (!response.isSuccessful() || response.body() == null)
                    throw new IOException("Comment report HTTP " + response.code());
                // A missing or malformed code is a failure, never an implicit success.
                return new JSONObject(response.body().string()).getInt("code");
            }
        } finally {
            synchronized (REPORTS_IN_FLIGHT) { REPORTS_IN_FLIGHT.remove(key); }
        }
    }

    public static boolean canManageReplyTop(long currentMid, long ownerMid, long root) {
        return currentMid > 0 && currentMid == ownerMid && root == 0;
    }

    static String buildTopReplyForm(long oid, long rpid, int type, boolean pinned, String csrf) {
        if (oid <= 0 || rpid <= 0 || type <= 0) throw new IllegalArgumentException("Invalid comment target");
        return new NetWorkUtil.FormData().put("oid", oid).put("rpid", rpid).put("type", type)
                .put("action", pinned ? 1 : 0).put("csrf", csrf).toString();
    }

    public static int setReplyTop(long oid, long rpid, int type, boolean pinned) throws IOException, JSONException {
        String body = buildTopReplyForm(oid, rpid, type, pinned,
                SharedPreferencesUtil.getString("csrf", ""));
        try (Response response = NetWorkUtil.post("https://api.bilibili.com/x/v2/reply/top", body,
                NetWorkUtil.webHeaders)) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Comment pin HTTP " + response.code());
            }
            int code = new JSONObject(response.body().string()).getInt("code");
            JSONObject details = new JSONObject().put("oid", oid).put("type", type)
                    .put("rpid", rpid).put("pinned", pinned).put("code", code);
            DiagnosticLogManager.record("reply_top_result", details);
            return code;
        }
    }

    /**
     * 删除评论
     *
     * @param oid  oid
     * @param rpid rpid
     * @param type 评论区类型
     * @return 返回码
     */
    public static int deleteReply(long oid, long rpid, int type) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/reply/del";
        String reqBody = new NetWorkUtil.FormData()
                .put("type", type)
                .put("oid", oid)
                .put("rpid", rpid)
                .put("csrf", SharedPreferencesUtil.getString("csrf", ""))
                .toString();
        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, reqBody, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-点赞评论", result.toString());
        return result.getInt("code");
    }

    public static long getReplyCount(long oid, int type) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/v2/reply/count?oid=" + oid + "&type=" + type;
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && (!all.isNull("data"))) {
            return all.getJSONObject("data").getLong("count");
        }
        return 0;
    }
}
