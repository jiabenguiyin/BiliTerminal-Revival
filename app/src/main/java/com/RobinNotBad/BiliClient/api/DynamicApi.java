package com.RobinNotBad.BiliClient.api;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.model.ArticleCard;
import com.RobinNotBad.BiliClient.model.At;
import com.RobinNotBad.BiliClient.model.Dynamic;
import com.RobinNotBad.BiliClient.model.Emote;
import com.RobinNotBad.BiliClient.model.LiveRoom;
import com.RobinNotBad.BiliClient.model.Stats;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.DmImgParamUtil;
import com.RobinNotBad.BiliClient.util.EmoteUtil;
import com.RobinNotBad.BiliClient.util.HotConfigManager;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;
import okhttp3.ResponseBody;

//新的动态api，旧的那个实在太蛋疼而且说不定随时会被弃用（

public class DynamicApi {
    private static final int DYNAMIC_DETAIL_SYNC_RETRY_COUNT = 4;
    private static final long DYNAMIC_DETAIL_SYNC_RETRY_DELAY_MS = 400L;

    public static class DynamicSyncPendingException extends IOException {
        public DynamicSyncPendingException() {
            super("动态详情仍在同步，请稍后刷新");
        }
    }

    /**
     * 发送纯文本动态
     *
     * @param content 文字内容
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishTextContent(String content) throws IOException {
        String url = "https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/create";
        String data = new NetWorkUtil.FormData()
                .put("dynamic_id", 0)
                .put("type", 4)
                .put("rid", 0)
                .put("content", content)
                .put("csrf", SharedPreferencesUtil.getString("csrf", ""))
                .toString();
        try {
            JSONObject result = postDynamicWithCompat(
                    "dynamic_create_text_legacy",
                    url,
                    data,
                    "application/x-www-form-urlencoded"
            );
            if (result.getString("code").equals("0") && result.has("data"))
                return result.getJSONObject("data").getLong("dynamic_id");
        } catch (JSONException ignored) {
            return -1;
        }
        return -1;
    }

    /**
     * 发送复杂动态
     *
     * @param contents 动态内容
     * @param pics     携带图片
     * @param option   选项
     * @param topic    话题
     * @param scene    动态类型
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishComplex(@NonNull JSONArray contents, JSONArray pics, JSONObject option, JSONObject topic, int scene, Map<String, Object> otherArgs) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/dynamic/feed/create/dyn?csrf=" + SharedPreferencesUtil.getString("csrf", "");
        JSONObject reqBody = new JSONObject()
                .put("content", new JSONObject().put("contents", contents))
                .put("scene", scene)
                .put("meta", new JSONObject().put("app_meta", new JSONObject()
                        .put("from", "create.dynamic.web")
                        .put("mobi_app", "web")));
        if (pics != null) reqBody.put("pics", pics);
        if (option != null) reqBody.put("option", option);
        if (topic != null) reqBody.put("topic", topic);
        reqBody = new JSONObject().put("dyn_req", reqBody);
        if (otherArgs != null) {
            for (Map.Entry<String, Object> entry : otherArgs.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                reqBody.put(key, val);
            }
        }
        Logu.v("publishComplex reqBody=" + reqBody);
        try {
            JSONObject result = postDynamicWithCompat(
                    "dynamic_create",
                    url,
                    reqBody.toString(),
                    "application/json"
            );
            if (result.getString("code").equals("0") && result.has("data"))
                return result.getJSONObject("data").getLong("dyn_id");
        } catch (JSONException e) {
            MsgUtil.err("发送动态", e);
            return -1;
        }
        return -1;
    }

    /**
     * 发布可包含艾特信息的文本动态
     *
     * @param content   文本内容
     * @param atUserUid 文本内at到的人的用户名uid map
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long publishTextContent(String content, Map<String, Long> atUserUid) throws JSONException, IOException {
        return publishComplex(parseAtContent(content, atUserUid), null, null, null,
                1, null);
    }

    /**
     * 转发视频到动态，瞎扒的api
     *
     * @param text 附加文字
     * @param aid  aid
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long relayVideo(String text, Map<String, Long> atUserUid, long aid) throws JSONException, IOException {
        return publishComplex(text == null ? new JSONArray().put(Content.create("", 1, null)) : atUserUid != null ? parseAtContent(text, atUserUid) : new JSONArray().put(Content.create(text, 1, null)),
                null, null, null,
                5, Collections.singletonMap("web_repost_src",
                        new JSONObject().put("revs_id", new JSONObject()
                                .put("dyn_type", 8)
                                .put("rid", aid))));
    }

    /**
     * 转发动态
     *
     * @param text 文字内容
     * @param dyid 动态id
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long relayDynamic(String text, long dyid) throws IOException {
        String url = "https://api.vc.bilibili.com/dynamic_repost/v1/dynamic_repost/repost";
        String data = new NetWorkUtil.FormData()
                .put("dynamic_id", dyid)
                .put("content", text)
                .put("csrf_token", SharedPreferencesUtil.getString("csrf", ""))
                .toString();
        try {
            JSONObject result = postDynamicWithCompat(
                    "dynamic_repost_legacy",
                    url,
                    data,
                    "application/x-www-form-urlencoded"
            );
            if (result.getString("code").equals("0") && result.has("data"))
                return result.getJSONObject("data").getLong("dynamic_id");
        } catch (JSONException ignored) {
            return -1;
        }
        return -1;
    }

    /**
     * 转发动态（复杂动态api），还是自己瞎扒的api
     *
     * @param text      文字内容
     * @param atUserUid 文本内at到的人的用户名uid map
     * @param dyid      动态id
     * @return 发送成功返回的动态id，失败返回-1
     */
    public static long relayDynamic(String text, Map<String, Long> atUserUid, long dyid) throws JSONException, IOException {
        return publishComplex(text == null ? new JSONArray().put(Content.create("", 1, null)) : atUserUid != null ? parseAtContent(text, atUserUid) : new JSONArray().put(Content.create(text, 1, null)),
                null, null, null,
                4, Collections.singletonMap("web_repost_src", new JSONObject().put("dyn_id_str", String.valueOf(dyid))));
    }

    /**
     * 解析包含艾特信息的文本动态内容
     *
     * @param content   文本内容
     * @param atUserUid 文本内at到的人的用户名uid map
     * @return Content JSON数组
     */
    public static JSONArray parseAtContent(String content, Map<String, Long> atUserUid) throws JSONException {
        JSONArray contentJSONArray = new JSONArray();

        Set<Pair<Integer, Integer>> indexes = new HashSet<>();
        Map<Pair<Integer, Integer>, Long> uidIndexes = new HashMap<>();
        for (Map.Entry<String, Long> entry : atUserUid.entrySet()) {
            String key = entry.getKey();
            long val = entry.getValue();

            Pattern pattern = Pattern.compile("@" + Pattern.quote(key) + " ");
            Matcher matcher = pattern.matcher(content);
            List<Pair<Integer, Integer>> mIndex = new ArrayList<>();
            while (matcher.find()) {
                int start = matcher.start();
                // 不包含空格，我直接按照我抓的请求内容弄的
                int end = matcher.end();
                Pair<Integer, Integer> pair = new Pair<>(start, end);
                mIndex.add(pair);
                uidIndexes.put(pair, val);
            }
            indexes.addAll(mIndex);
        }

        ArrayList<Pair<Integer, Integer>> indexesList = new ArrayList<>(indexes);
        Collections.sort(indexesList, (left, right) -> {
            int startCompare = left.first < right.first ? -1 : (left.first.equals(right.first) ? 0 : 1);
            if (startCompare != 0) return startCompare;
            return left.second < right.second ? -1 : (left.second.equals(right.second) ? 0 : 1);
        });
        int pos = 0;
        for (Pair<Integer, Integer> index : indexesList) {
            int start = index.first;
            int end = index.second;
            if (start < pos || end > content.length()) continue;
            String sub = content.substring(pos, start);
            if (!sub.isEmpty()) contentJSONArray.put(Content.create(sub, 1, null));
            String subAt = content.substring(start, end);
            if (!subAt.isEmpty())
                contentJSONArray.put(Content.create(subAt, 2, String.valueOf(uidIndexes.get(index))));
            pos = end;
        }
        String sub = content.substring(pos);
        if (!sub.isEmpty()) contentJSONArray.put(Content.create(sub, 1, null));

        if (indexesList.isEmpty()) contentJSONArray.put(Content.create(content, 1, null));
        return contentJSONArray;
    }

    /**
     * 动态点赞/取消赞
     *
     * @param dyid 动态id
     * @param up   是否为点赞
     * @return resultCode
     */
    public static int likeDynamic(long dyid, boolean up) throws IOException {
        String url = "https://api.vc.bilibili.com/dynamic_like/v1/dynamic_like/thumb";
        String data = new NetWorkUtil.FormData()
                .put("dynamic_id", dyid)
                .put("up", up ? 1 : 2)
                .put("csrf_token", SharedPreferencesUtil.getString("csrf", ""))
                .toString();
        try {
            JSONObject result = postDynamicWithCompat(
                    "dynamic_like",
                    url,
                    data,
                    "application/x-www-form-urlencoded"
            );
            return result.getInt("code");
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static int deleteDynamic(long dyid) throws IOException {
        String url = "https://api.vc.bilibili.com/dynamic_svr/v1/dynamic_svr/rm_dynamic";
        String data = new NetWorkUtil.FormData()
                .put("dynamic_id", dyid)
                .put("csrf_token", SharedPreferencesUtil.getString("csrf", ""))
                .toString();
        try {
            JSONObject result = postDynamicWithCompat(
                    "dynamic_delete",
                    url,
                    data,
                    "application/x-www-form-urlencoded"
            );
            return result.getInt("code");
        } catch (JSONException ignored) {
            return -1;
        }
    }

    /**
     * 寻找用户（完全匹配），仍然自己瞎扒的，不清楚是否有更好方案
     *
     * @param name 名称
     * @return 用户UID，未找到返回-1
     */
    public static long mentionAtFindUser(String name) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/mention/search?keyword=" + name;

        JSONObject resp;
        try {
            resp = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        } catch (IOException | JSONException officialError) {
            resp = getCompatDynamicJson("dynamic_mention", url, officialError);
        }
        if (resp.has("data") && !resp.isNull("data")) {
            JSONObject data = resp.getJSONObject("data");
            if (data.has("groups") && !data.isNull("groups")) {
                JSONArray groups = data.getJSONArray("groups");
                for (int i = 0; i < groups.length(); i++) {
                    JSONArray items = groups.getJSONObject(i).getJSONArray("items");
                    for (int j = 0; j < items.length(); j++) {
                        if (items.getJSONObject(j).getString("name").equals(name))
                            return Long.parseLong(items.getJSONObject(j).getString("uid"));
                    }
                }
            }
        }

        return -1;
    }

    public static long getDynamicList(List<Dynamic> dynamicList, long offset, long mid, String type) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/desktop/v1/feed/"
                + (mid == 0 ? "all?type=" + type : "space?platform=web&web_location=333.1387&timezone_offset=-480&host_mid=" + mid)
                + (offset == 0 ? "" : "&offset=" + offset)
                + "&features=itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,forwardListHidden,decorationCard,commentsNewVersion,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,avatarAutoTheme,sunflowerStyle,eva3CardOpus,eva3CardVideo,eva3CardComment";


        String signedUrl = ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url));
        String routeId = mid == 0 ? "dynamic_feed_all" : "dynamic_feed_space";
        List<Dynamic> parsed = new ArrayList<>();
        long offsetNew;
        try {
            offsetNew = parseDynamicListResponse(
                    NetWorkUtil.getJson(signedUrl, NetWorkUtil.webHeaders),
                    parsed,
                    mid
            );
        } catch (IOException | JSONException officialError) {
            parsed.clear();
            offsetNew = parseDynamicListResponse(
                    getCompatDynamicJson(routeId, signedUrl, officialError),
                    parsed,
                    mid
            );
        }
        dynamicList.addAll(parsed);
        return offsetNew;
    }

    private static long parseDynamicListResponse(JSONObject all, List<Dynamic> output, long mid) throws JSONException {
        if (all.getInt("code") != 0) {
            throw new JSONException(all.optString("message", "动态接口错误"));
        }
        JSONObject data = all.getJSONObject("data");
        boolean hasMore = data.getBoolean("has_more");
        long offsetNew = hasMore ? Long.parseLong(data.getString("offset")) : -1;

        if (mid == 0) {
            long updateBaseline = data.optLong("update_baseline", -1);
            if (updateBaseline > -1) {
                SharedPreferencesUtil.putLong("dynamic_update_baseline", updateBaseline);
            } else if (offsetNew != -1) {
                SharedPreferencesUtil.putLong("dynamic_update_baseline", offsetNew);
            }
        }

        JSONArray items = data.getJSONArray("items");
        int outputSizeBefore = output.size();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            try {
                output.add(analyzeDynamic(item));
            } catch (JSONException | RuntimeException error) {
                Logu.e("dynamic", "skip malformed item " + i + ": " + error);
            }
        }
        if (items.length() > 0 && output.size() == outputSizeBefore) {
            throw new JSONException("no valid dynamic items");
        }
        return offsetNew;
    }

    public static Dynamic getDynamic(long id) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/desktop/v1/detail?"
                + "timezone_offset=-480&platform=web&gaia_source=main_web&id=" + id
                + "&features=itemOpusStyle,opusBigCover,onlyfansVote,endFooterHidden,decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,editable,opusPrivateVisible,avatarAutoTheme"
                + "&web_location=333.1368";

        String signedUrl = ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url));
        for (int attempt = 0; attempt < DYNAMIC_DETAIL_SYNC_RETRY_COUNT; attempt++) {
            try {
                Dynamic dynamic = parseDynamicDetailResponse(
                        NetWorkUtil.getJson(signedUrl, NetWorkUtil.webHeaders)
                );
                if (dynamic != null) return dynamic;
                dynamic = parseDynamicDetailResponse(
                        getCompatDynamicJson("dynamic_detail", signedUrl, null)
                );
                if (dynamic != null) return dynamic;
            } catch (IOException | JSONException officialError) {
                Dynamic dynamic = parseDynamicDetailResponse(
                        getCompatDynamicJson("dynamic_detail", signedUrl, officialError)
                );
                if (dynamic != null) return dynamic;
            }

            if (attempt + 1 < DYNAMIC_DETAIL_SYNC_RETRY_COUNT) {
                try {
                    Thread.sleep(DYNAMIC_DETAIL_SYNC_RETRY_DELAY_MS * (attempt + 1));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("动态详情读取被中断", error);
                }
            }
        }

        throw new DynamicSyncPendingException();
    }

    public static int checkDynamicUpdate(String type, long updateBaseline) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/desktop/v1/feed/all/update?type=" + type + "&update_baseline=" + updateBaseline + "&web_location=333.1365";
        try {
            return parseDynamicUpdateResponse(NetWorkUtil.getJson(url, NetWorkUtil.webHeaders));
        } catch (IOException | JSONException officialError) {
            return parseDynamicUpdateResponse(
                    getCompatDynamicJson("dynamic_update", url, officialError)
            );
        }
    }

    private static Dynamic parseDynamicDetailResponse(JSONObject result) throws JSONException {
        if (result.getInt("code") != 0) {
            throw new JSONException(result.optString("message", "动态详情接口错误"));
        }
        JSONObject data = result.optJSONObject("data");
        JSONObject item = data == null ? null : data.optJSONObject("item");
        return item == null ? null : analyzeDynamic(item);
    }

    private static int parseDynamicUpdateResponse(JSONObject result) throws JSONException {
        if (result.getInt("code") != 0) {
            throw new JSONException(result.optString("message", "动态更新接口错误"));
        }
        JSONObject data = result.optJSONObject("data");
        return data == null ? 0 : data.optInt("update_num", 0);
    }

    private static JSONObject getCompatDynamicJson(String routeId, String officialUrl, Throwable officialError)
            throws IOException, JSONException {
        String compatUrl = HotConfigManager.getCompatUrl(routeId, officialUrl);
        if (compatUrl.isEmpty()) {
            if (officialError instanceof IOException) throw (IOException) officialError;
            if (officialError instanceof JSONException) throw (JSONException) officialError;
            throw new IOException("动态兼容接口未启用");
        }
        if (officialError != null) {
            Logu.e("dynamic-compat", routeId + " fallback: " + officialError);
        }
        return NetWorkUtil.getJson(
                compatUrl,
                HotConfigManager.withCompatHeader(NetWorkUtil.webHeaders)
        );
    }

    private static JSONObject postDynamicWithCompat(String routeId, String officialUrl,
                                                    String data, String contentType)
            throws IOException, JSONException {
        Throwable officialError;
        try {
            JSONObject result = postJsonResult(officialUrl, data, NetWorkUtil.webHeaders, contentType);
            if (result.optInt("code", 0) != -404) return result;
            officialError = new JSONException("dynamic endpoint not found");
        } catch (IOException | JSONException error) {
            officialError = error;
        }

        String compatUrl = HotConfigManager.getCompatUrl(routeId, officialUrl);
        if (compatUrl.isEmpty()) {
            if (officialError instanceof IOException) throw (IOException) officialError;
            throw (JSONException) officialError;
        }
        Logu.e("dynamic-compat", routeId + " POST fallback: " + officialError);
        return postJsonResult(
                compatUrl,
                data,
                HotConfigManager.withCompatHeader(NetWorkUtil.webHeaders),
                contentType
        );
    }

    private static JSONObject postJsonResult(String url, String data, List<String> headers,
                                             String contentType)
            throws IOException, JSONException {
        try (Response response = NetWorkUtil.post(url, data, headers, contentType)) {
            ResponseBody body = response.body();
            if (body == null) throw new IOException("动态接口返回为空");
            return new JSONObject(body.string());
        }
    }

    public static Dynamic analyzeDynamic(JSONObject dynamic_json) throws JSONException {
        Logu.v("--------------");
        Dynamic dynamic = new Dynamic();

        if (!dynamic_json.isNull("id_str"))
            try {
                dynamic.dynamicId = Long.parseLong(dynamic_json.optString("id_str", "0"));
            } catch (Exception ignored) {
            }
        else {
            dynamic.dynamicId = 0;
        }
        dynamic.type = dynamic_json.optString("type");

        JSONObject basic = dynamic_json.getJSONObject("basic");
        dynamic.comment_id = getFirstPositiveLong(basic,
                "comment_id_str", "comment_id", "rid_str", "rid");
        int explicitCommentType = getFirstPositiveInt(basic, "comment_type");
        dynamic.comment_type = explicitCommentType > 0
                ? explicitCommentType
                : ReplyApi.resolveDynamicCommentType(dynamic.type, basic.optInt("rtype", 0));

        Logu.v("id", String.valueOf(dynamic.dynamicId));
        Logu.v("oid", String.valueOf(dynamic.comment_id));
        Logu.v("type", dynamic.type);
        Logu.v("otype", String.valueOf(dynamic.comment_type));

        JSONObject modules = getModulesObject(dynamic_json);

        //发布者
        UserInfo userInfo = new UserInfo();
        if (!modules.isNull("module_author")) {
            JSONObject module_author = modules.getJSONObject("module_author");
            JSONObject author_user = module_author.optJSONObject("user");
            JSONObject author_source = author_user != null ? author_user : module_author;
            userInfo.mid = author_source.optLong("mid");
            userInfo.name = author_source.optString("name");
            if (!module_author.isNull("following"))
                userInfo.followed = module_author.getBoolean("following");
            userInfo.avatar = author_source.optString("face");
            JSONObject vipJson = author_source.optJSONObject("vip");
            if (vipJson != null) {
                userInfo.vip_nickname_color = vipJson.optString("nickname_color", "");
            }
            Logu.v("sender", userInfo.name);
            dynamic.pubTime = module_author.optString("pub_time", module_author.optString("pub_text"));
        }
        dynamic.userInfo = userInfo;

        if (dynamic.type.equals("DYNAMIC_TYPE_NONE")) {
            dynamic.content = "[动态不存在]";
            return dynamic;
        }

        dynamic.content = "";

        if (!modules.isNull("module_desc")) {
            JSONObject module_desc = modules.getJSONObject("module_desc");
            dynamic.content = analyzeTextContent(module_desc.optJSONArray("rich_text_nodes"));
        }

        //动态主体
        if (!modules.isNull("module_dynamic")) {
            JSONObject module_dynamic = modules.getJSONObject("module_dynamic");

            //内容
            if (!module_dynamic.isNull("desc")) {
                JSONObject desc = module_dynamic.getJSONObject("desc");
                JSONArray rich_text_nodes = desc.optJSONArray("rich_text_nodes");
                dynamic.content = analyzeTextContent(rich_text_nodes);
            }

            //这里面什么都有，直译为主要的
            if (!module_dynamic.isNull("major")) {
                JSONObject major = module_dynamic.getJSONObject("major");
                String major_type = major.getString("type");
                dynamic.major_type = major_type;
                Logu.d(major_type);
                switch (major_type) {
                    case "MAJOR_TYPE_ARCHIVE":
                        dynamic.major_object = analyzeVideoCard(major.getJSONObject("archive"));
                        break;
                    case "MAJOR_TYPE_UGC_SEASON":
                        dynamic.major_object = analyzeVideoCard(major.getJSONObject("ugc_season"));
                        break;
                    case "MAJOR_TYPE_PGC":
                        JSONObject bangumi = major.getJSONObject("pgc");
                        VideoCard card = new VideoCard();
                        card.type = "media_bangumi";
                        card.aid = BangumiApi.getMdidFromEpid(bangumi.getLong("epid"));
                        card.title = bangumi.getString("title");
                        card.cover = bangumi.getString("cover");
                        card.view = bangumi.getJSONObject("stat").getString("play");
                        dynamic.major_object = card;
                        break;
                    case "MAJOR_TYPE_ARTICLE":
                        JSONObject article = major.getJSONObject("article");
                        dynamic.major_object = new ArticleCard(
                                article.optString("title"),
                                parseLong(article.optString("id", "0")),
                                (article.has("covers") && !article.isNull("covers") ? article.getJSONArray("covers").optString(0) : ""),
                                "投稿文章",
                                article.optString("label")
                        );
                        break;

                    case "MAJOR_TYPE_DRAW":
                        dynamic.major_object = analyzeDrawPictureList(major.getJSONObject("draw"));
                        break;

                    case "MAJOR_TYPE_COMMON":
                        dynamic.content = dynamic.content + "\n[无法显示活动类动态的附加内容]";
                        break;

                    case "MAJOR_TYPE_LIVE_RCMD":
                        dynamic.major_object = analyzeLiveRcmd(major.getJSONObject("live_rcmd"));
                        dynamic.content = (TextUtils.isEmpty(dynamic.content) ? "" : dynamic.content + "\n");
                        break;

                    case "MAJOR_TYPE_LIVE":
                        dynamic.major_object = analyzeLiveInfo(major.getJSONObject("live"));
                        dynamic.content = (TextUtils.isEmpty(dynamic.content) ? "" : dynamic.content + "\n");
                        break;

                    case "MAJOR_TYPE_OPUS":
                        analyzeOpusCard(dynamic, major.getJSONObject("opus"));
                        break;

                    default:
                        dynamic.content = dynamic.content + "\n[*哔哩终端暂时无法查看此动态的附加内容QwQ|类型：" + major_type + "]";
                        break;
                }
            }
            if (dynamic.major_object == null) {
                analyzeDesktopModuleDynamic(dynamic, module_dynamic);
            }
            if (modules.has("module_additional") && !modules.isNull("module_additional")) {
                JSONObject module_additional = modules.getJSONObject("module_additional");
                if (module_additional.getString("type").equals("ADDITIONAL_TYPE_UGC")) {
                    dynamic.major_type = "MAJOR_TYPE_ARCHIVE";
                    dynamic.major_object = analyzeVideoCard(module_additional.getJSONObject("ugc"));
                } else Logu.v("addi", module_additional.getString("type"));
            }

            if (!module_dynamic.isNull("dyn_forward")) {
                JSONObject dyn_forward = module_dynamic.getJSONObject("dyn_forward");
                if (!dyn_forward.isNull("item"))
                    dynamic.dynamic_forward = analyzeDynamic(dyn_forward.getJSONObject("item"));
            }
        }

        // 动态Stats
        if (modules.has("module_stat") && !modules.isNull("module_stat")) {
            JSONObject module_stat = modules.getJSONObject("module_stat");
            JSONObject like = module_stat.optJSONObject("like");
            Stats stats = new Stats();
            JSONObject comment = module_stat.optJSONObject("comment");
            if (comment != null) {
                stats.reply = comment.optInt("count");
                stats.reply_disabled = comment.optBoolean("forbidden");
            }
            JSONObject forward = module_stat.optJSONObject("forward");
            if (forward != null) {
                stats.share = forward.optInt("count");
                stats.share_disabled = forward.optBoolean("forbidden");
            }
            if (like != null) {
                stats.like = like.optInt("count");
                stats.liked = like.optBoolean("status", like.optBoolean("like_state"));
                stats.like_disabled = like.optBoolean("forbidden");
            }
            // TODO 转发&回复

            dynamic.stats = stats;
        }

        JSONArray three_point_items = null;
        if (modules.has("module_more") && !modules.isNull("module_more")) {
            three_point_items = modules.getJSONObject("module_more").optJSONArray("three_point_items");
        } else if (modules.has("module_author") && !modules.isNull("module_author")) {
            JSONObject more = modules.getJSONObject("module_author").optJSONObject("more");
            if (more != null) three_point_items = more.optJSONArray("three_point_items");
        }

        if (three_point_items != null) {
            List<String> supportItemTypes = new ArrayList<>();
            for (int i = 0; i < three_point_items.length(); i++) {
                supportItemTypes.add(three_point_items.getJSONObject(i).getString("type"));
            }
            dynamic.canDelete = supportItemTypes.contains("THREE_POINT_DELETE");
        }

        if (dynamic_json.has("orig") && !dynamic_json.isNull("orig")) {
            dynamic.dynamic_forward = analyzeDynamic(dynamic_json.getJSONObject("orig"));
        }

        if (dynamic.stats == null) dynamic.stats = new Stats();
        return dynamic;
    }

    private static JSONObject getModulesObject(JSONObject dynamicJson) throws JSONException {
        Object modulesValue = dynamicJson.opt("modules");
        if (modulesValue instanceof JSONObject) return (JSONObject) modulesValue;
        JSONObject modulesObject = new JSONObject();
        if (modulesValue instanceof JSONArray) {
            JSONArray modulesArray = (JSONArray) modulesValue;
            for (int i = 0; i < modulesArray.length(); i++) {
                JSONObject module = modulesArray.optJSONObject(i);
                if (module == null) continue;
                java.util.Iterator<String> keys = module.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!"module_type".equals(key)) modulesObject.put(key, module.opt(key));
                }
            }
        }
        return modulesObject;
    }

    private static void analyzeDesktopModuleDynamic(Dynamic dynamic, JSONObject moduleDynamic) throws JSONException {
        if (!moduleDynamic.isNull("dyn_archive")) {
            dynamic.major_type = "MAJOR_TYPE_ARCHIVE";
            dynamic.major_object = analyzeVideoCard(moduleDynamic.getJSONObject("dyn_archive"));
            return;
        }

        if (!moduleDynamic.isNull("dyn_draw")) {
            dynamic.major_type = "MAJOR_TYPE_DRAW";
            dynamic.major_object = analyzeDrawPictureList(moduleDynamic.getJSONObject("dyn_draw"));
            return;
        }

        if (!moduleDynamic.isNull("dyn_ugc_season")) {
            dynamic.major_type = "MAJOR_TYPE_UGC_SEASON";
            dynamic.major_object = analyzeVideoCard(moduleDynamic.getJSONObject("dyn_ugc_season"));
            return;
        }

        if (!moduleDynamic.isNull("dyn_live_rcmd")) {
            dynamic.major_type = "MAJOR_TYPE_LIVE_RCMD";
            dynamic.major_object = analyzeLiveRcmd(moduleDynamic.getJSONObject("dyn_live_rcmd"));
            return;
        }

        if (!moduleDynamic.isNull("dyn_live")) {
            dynamic.major_type = "MAJOR_TYPE_LIVE";
            dynamic.major_object = analyzeLiveInfo(moduleDynamic.getJSONObject("dyn_live"));
            return;
        }

        if (!moduleDynamic.isNull("dyn_opus")) {
            dynamic.major_type = "MAJOR_TYPE_OPUS";
            analyzeOpusCard(dynamic, moduleDynamic.getJSONObject("dyn_opus"));
        }
    }

    private static ArrayList<String> analyzeDrawPictureList(JSONObject draw) {
        JSONArray items = draw.optJSONArray("items");
        ArrayList<String> pictureList = new ArrayList<>();
        if (items == null) return pictureList;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String src = item.optString("src", item.optString("url"));
            if (!TextUtils.isEmpty(src)) pictureList.add(src);
        }
        return pictureList;
    }

    private static VideoCard analyzeVideoCard(JSONObject jsonObject) throws JSONException {
        JSONObject stat = jsonObject.optJSONObject("stat");
        String aidText = jsonObject.optString("aid", "0");
        long aid = parseLong(aidText);
        return new VideoCard(
                jsonObject.optString("title"),
                "投稿视频",
                stat != null ? stat.optString("play", stat.optString("view")) : "",
                jsonObject.optString("cover", jsonObject.optString("pic")),
                aid,
                jsonObject.optString("bvid")
        );
    }

    private static void analyzeOpusCard(Dynamic dynamic, JSONObject opusJson) {
        String title = opusJson.optString("title");
        if (!TextUtils.isEmpty(title) && !"null".equals(title))
            dynamic.title = title;

        JSONArray pics = opusJson.optJSONArray("pics");
        if (pics == null) pics = opusJson.optJSONArray("items");
        if (pics != null) {
            ArrayList<String> opusPicList = new ArrayList<>();
            for (int i = 0; i < pics.length(); i++) {
                JSONObject pic = pics.optJSONObject(i);
                if (pic == null) continue;
                String url = pic.optString("url", pic.optString("src"));
                if (!TextUtils.isEmpty(url)) opusPicList.add(url);
            }
            dynamic.major_object = opusPicList;
        }

        JSONObject summary = opusJson.optJSONObject("summary");
        if (summary == null) summary = opusJson.optJSONObject("desc");
        if (summary != null)
            dynamic.content = analyzeTextContent(summary.optJSONArray("rich_text_nodes"));
    }

    private static LiveRoom analyzeLiveRcmd(JSONObject liveRcmd) {
        JSONObject contentJson = parseJsonObjectValue(liveRcmd.opt("content"));
        JSONObject liveInfo = contentJson != null ? contentJson.optJSONObject("live_play_info") : null;
        if (liveInfo == null && contentJson != null) liveInfo = contentJson;
        if (liveInfo == null) liveInfo = liveRcmd.optJSONObject("live_play_info");
        if (liveInfo == null) liveInfo = liveRcmd;
        return analyzeLiveInfo(liveInfo);
    }

    private static LiveRoom analyzeLiveInfo(JSONObject liveInfo) {
        LiveRoom room = new LiveRoom();
        room.roomid = liveInfo.optLong("room_id", liveInfo.optLong("id"));
        room.uid = liveInfo.optLong("uid");
        room.title = liveInfo.optString("title");
        room.uname = liveInfo.optString("uname", liveInfo.optString("name"));
        room.cover = liveInfo.optString("cover", liveInfo.optString("user_cover", liveInfo.optString("keyframe")));
        room.user_cover = liveInfo.optString("user_cover", room.cover);
        room.keyframe = liveInfo.optString("keyframe", room.cover);
        room.online = liveInfo.optInt("online");
        room.live_status = liveInfo.optInt("live_status", liveInfo.optInt("live_state"));
        room.area_id = liveInfo.optInt("area_id");
        room.area_name = liveInfo.optString("area_name");
        room.area_parent_id = liveInfo.optInt("parent_area_id");
        room.area_parent_name = liveInfo.optString("parent_area_name");
        return room;
    }

    private static JSONObject parseJsonObjectValue(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String && !TextUtils.isEmpty((String) value)) {
            try {
                return new JSONObject((String) value);
            } catch (JSONException ignored) {
            }
        }
        return null;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long getFirstPositiveLong(JSONObject object, String... keys) {
        for (String key : keys) {
            long value = parseLong(object.optString(key, "0"));
            if (value > 0) return value;
        }
        return 0;
    }

    private static int getFirstPositiveInt(JSONObject object, String... keys) {
        for (String key : keys) {
            int value = object.optInt(key, 0);
            if (value > 0) return value;
        }
        return 0;
    }

    private static SpannableStringBuilder analyzeTextContent(JSONArray rich_text_nodes) {
        if (rich_text_nodes == null) return new SpannableStringBuilder("[动态内容解析异常]");

        ArrayList<Emote> emoteList = new ArrayList<>();
        ArrayList<At> atList = new ArrayList<>();
        SpannableStringBuilder content = new SpannableStringBuilder();
        for (int i = 0; i < rich_text_nodes.length(); i++) {
            JSONObject rich_text_node = rich_text_nodes.optJSONObject(i);
            if (rich_text_node == null) continue;
            String type = rich_text_node.optString("type");
            switch (type) {
                case "RICH_TEXT_NODE_TYPE_EMOJI":
                    content.append(rich_text_node.optString("text"));
                    JSONObject emoji = rich_text_node.optJSONObject("emoji");
                    if (emoji == null) continue;
                    emoteList.add(new Emote(emoji.optString("text"), emoji.optString("icon_url"), emoji.optInt("size")));
                    break;
                case "RICH_TEXT_NODE_TYPE_AT":
                    Pair<Integer, Integer> indexs = StringUtil.appendString(content, rich_text_node.optString("text"));
                    atList.add(new At(rich_text_node.optLong("rid"), indexs.first, indexs.second));
                    break;
                case "RICH_TEXT_NODE_TYPE_WEB":
                    content.append(rich_text_node.optString("orig_text"));
                    break;
                case "RICH_TEXT_NODE_TYPE_TEXT":
                default:
                    content.append(rich_text_node.optString("text"));
                    break;
            }
        }

        EmoteUtil.textReplaceEmote(content.toString(), emoteList, 1.0f, BiliTerminal.context, content);
        for (At at : atList) {
            StringUtil.setSingleAt(content, at);
        }

        return content;
    }

    public static class Content {
        public static JSONObject create(@NonNull String raw_text, int type, String biz_id) throws JSONException {
            return new JSONObject()
                    .put("raw_text", raw_text)
                    .put("type", type)
                    .put("biz_id", biz_id == null ? "" : biz_id);
        }
    }
}
