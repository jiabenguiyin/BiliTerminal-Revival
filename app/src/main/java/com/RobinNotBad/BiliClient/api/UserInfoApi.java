package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ArticleCard;
import com.RobinNotBad.BiliClient.model.LiveRoom;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.DmImgParamUtil;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.FollowRelationUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

//用户信息API

public class UserInfoApi {
    private static final Map<String, String> USER_OPUS_OFFSETS = new ConcurrentHashMap<>();

    public static UserInfo getUserInfo(long mid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/card?mid=" + mid;
        JSONObject all = NetWorkUtil.getJson(url);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject notice_all = NetWorkUtil.getJson("https://api.bilibili.com/x/space/notice?mid=" + mid);
            String notice;
            if (notice_all.has("data") && !notice_all.isNull("data"))
                notice = notice_all.getString("data");
            else notice = "";
            JSONObject data = all.getJSONObject("data");
            boolean followed = data.getBoolean("following");
            boolean followsMe = false;
            int relationAttribute = followed ? 2 : 0;
            int beRelationAttribute = 0;
            int fans = data.getInt("follower");

            long currentMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            if (currentMid != 0 && currentMid != mid) {
                try {
                    JSONObject relationData = getUserRelation(mid);
                    if (relationData != null) {
                        JSONObject relation = relationData.optJSONObject("relation");
                        JSONObject beRelation = relationData.optJSONObject("be_relation");
                        if (relation != null) {
                            relationAttribute = relation.optInt("attribute", relationAttribute);
                            followed = FollowRelationUtil.isFollowingAttribute(relationAttribute);
                        }
                        if (beRelation != null) {
                            beRelationAttribute = beRelation.optInt("attribute", 0);
                            followsMe = FollowRelationUtil.isFollowingAttribute(beRelationAttribute);
                        }
                    }
                } catch (Exception ignore) {
                }
            }

            JSONObject card = data.getJSONObject("card");
            String name = card.getString("name");
            String avatar = card.getString("face");
            String sign = card.getString("sign");
            JSONObject levelInfo = card.getJSONObject("level_info");
            int level = levelInfo.getInt("current_level");
            int attention = card.getInt("attention");

            JSONObject official_data = card.getJSONObject("Official");
            int official = official_data.getInt("role");
            String officialDesc = official_data.getString("title");

            String sys_notice = "";
            LiveRoom liveroom = null;
            boolean is_follow_display = false;
            try {
                JSONObject spaceInfo = getUserSpaceInfo(mid);
                if (spaceInfo != null) {
                    if (!spaceInfo.isNull("sys_notice")) {
                        sys_notice = spaceInfo.getJSONObject("sys_notice").optString("content");
                        if (sys_notice == null) sys_notice = "";
                        else sys_notice = sys_notice.replace("请点此查看纪念账号相关说明", "");
                    }
                    if (!spaceInfo.isNull("live_room")) {
                        JSONObject live_room = spaceInfo.getJSONObject("live_room");
                        if (live_room.getInt("roomStatus") == 1 && live_room.getInt("liveStatus") == 1) {
                            liveroom = new LiveRoom();
                            liveroom.title = "直播中：" + live_room.getString("title");
                            liveroom.user_cover = live_room.getString("cover");
                            liveroom.roomid = live_room.getLong("roomid");
                        }
                    }
                    if (!spaceInfo.isNull("contract")) {
                        JSONObject contract = spaceInfo.getJSONObject("contract");
                        is_follow_display = contract.optBoolean("is_follow_display", false);
                    }
                }
            } catch (Exception ignore) {
            }

            JSONObject vip = card.getJSONObject("vip");
            if (vip.getInt("status") == 1) {
                UserInfo result = new UserInfo(mid, name, avatar, sign, fans, attention, level, followed, notice, official, officialDesc, vip.getInt("role"), sys_notice, liveroom, card.getInt("is_senior_member"));
                result.vip_nickname_color = vip.optString("nickname_color", "");
                result.is_follow_display = is_follow_display;
                result.followsMe = followsMe;
                result.relationAttribute = relationAttribute;
                result.beRelationAttribute = beRelationAttribute;
                return result;
            } else {
                UserInfo result = new UserInfo(mid, name, avatar, sign, fans, attention, level, followed, notice, official, officialDesc, sys_notice, liveroom, card.getInt("is_senior_member"));
                result.is_follow_display = is_follow_display;
                result.followsMe = followsMe;
                result.relationAttribute = relationAttribute;
                result.beRelationAttribute = beRelationAttribute;
                return result;
            }
        } else return null;
    }

    public static JSONObject getUserSpaceInfo(long mid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/space/wbi/acc/info?";
        url += "mid=" + mid;
        JSONObject all = NetWorkUtil.getJson(ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url)));
        if (all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return null;
    }

    public static JSONObject getUserRelation(long mid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/space/wbi/acc/relation?"
                + "mid=" + mid
                + "&gaia_source=m_station"
                + "&platform=h5"
                + "&web_location=1280305";
        JSONObject all = NetWorkUtil.getJson(
                ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url)),
                NetWorkUtil.webHeaders);
        if (all.optInt("code", -1) == 0 && all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return null;
    }

    public static UserInfo getCurrentUserInfo() throws IOException, JSONException {
        JSONObject myInfo = null;
        Exception primaryError = null;
        try {
            myInfo = NetWorkUtil.getJson("https://api.bilibili.com/x/space/myinfo");
            if (myInfo.optInt("code", -1) == 0 && !myInfo.isNull("data")) {
                return parseCurrentUserMyInfo(myInfo.getJSONObject("data"));
            }
        } catch (IOException | JSONException | RuntimeException error) {
            primaryError = error;
        }

        JSONObject nav = null;
        Exception fallbackError = null;
        try {
            nav = NetWorkUtil.getJson("https://api.bilibili.com/x/web-interface/nav", NetWorkUtil.webHeaders);
            JSONObject data = nav.optJSONObject("data");
            if (nav.optInt("code", -1) == 0 && data != null && data.optBoolean("isLogin", false)) {
                recordCurrentUserFallback(myInfo);
                return parseCurrentUserNav(data);
            }
        } catch (IOException | JSONException | RuntimeException error) {
            fallbackError = error;
        }

        int myInfoCode = myInfo == null ? Integer.MIN_VALUE : myInfo.optInt("code", -1);
        int navCode = nav == null ? Integer.MIN_VALUE : nav.optInt("code", -1);
        JSONObject navData = nav == null ? null : nav.optJSONObject("data");
        boolean navLoggedOut = navCode == -101 || (navCode == 0 && navData != null
                && !navData.optBoolean("isLogin", false));
        if (myInfoCode == -101 && navLoggedOut) {
            throw new LoginExpiredException();
        }

        JSONObject details = new JSONObject();
        try {
            details.put("myinfo_code", myInfoCode);
            details.put("nav_code", navCode);
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("current_user_load_failed", details);
        Throwable cause = fallbackError != null ? fallbackError : primaryError;
        throw new IOException("个人资料加载失败（" + printableCode(myInfoCode)
                + "/" + printableCode(navCode) + "）", cause);
    }

    private static UserInfo parseCurrentUserMyInfo(JSONObject data) {
        long mid = data.optLong("mid", 0);
        JSONObject officialData = data.optJSONObject("official");
        JSONObject levelExp = data.optJSONObject("level_exp");
        int official = officialData == null ? 0 : officialData.optInt("role", 0);
        String officialDesc = officialData == null ? "" : officialData.optString("desc", "");
        long currentExp = levelExp == null ? 0 : levelExp.optLong("current_exp", 0);
        long nextExp = levelExp == null ? 0 : levelExp.optLong("next_exp", 0);
        return new UserInfo(mid, data.optString("name", ""), data.optString("face", ""),
                data.optString("sign", ""), data.optInt("follower", 0), 0,
                data.optInt("level", 0), false, "", official, officialDesc,
                currentExp, nextExp, data.optInt("is_senior_member", 0));
    }

    private static UserInfo parseCurrentUserNav(JSONObject data) {
        long mid = data.optLong("mid", 0);
        JSONObject officialData = data.optJSONObject("official");
        JSONObject levelInfo = data.optJSONObject("level_info");
        int official = officialData == null ? 0 : officialData.optInt("role", 0);
        String officialDesc = officialData == null ? "" : officialData.optString("desc",
                officialData.optString("title", ""));
        int level = levelInfo == null ? 0 : levelInfo.optInt("current_level", 0);
        long currentExp = levelInfo == null ? 0 : levelInfo.optLong("current_exp", 0);
        long nextExp = levelInfo == null ? 0 : levelInfo.optLong("next_exp", 0);
        UserInfo result = new UserInfo(mid, data.optString("uname", ""),
                data.optString("face", ""), "", getCurrentUserFans(mid), 0, level,
                false, "", official, officialDesc, currentExp, nextExp,
                data.optInt("is_senior_member", 0));
        JSONObject vip = data.optJSONObject("vip");
        if (vip != null) {
            result.vip_role = vip.optInt("vipStatus", data.optInt("vipStatus", 0));
            result.vip_nickname_color = vip.optString("nickname_color", "");
        }
        return result;
    }

    private static int getCurrentUserFans(long mid) {
        if (mid <= 0) return 0;
        try {
            JSONObject result = NetWorkUtil.getJson(
                    "https://api.bilibili.com/x/relation/stat?vmid=" + mid,
                    NetWorkUtil.webHeaders);
            JSONObject data = result.optJSONObject("data");
            return data == null ? 0 : data.optInt("follower", 0);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void recordCurrentUserFallback(JSONObject myInfo) {
        JSONObject details = new JSONObject();
        try {
            details.put("myinfo_code", myInfo == null ? "network" : myInfo.optInt("code", -1));
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("current_user_nav_fallback", details);
    }

    private static String printableCode(int code) {
        return code == Integer.MIN_VALUE ? "network" : String.valueOf(code);
    }

    public static int getCurrentUserCoin() {
        try {
            JSONObject nav = NetWorkUtil.getJson("https://api.bilibili.com/x/web-interface/nav",
                    NetWorkUtil.webHeaders);
            JSONObject data = nav.optJSONObject("data");
            if (nav.optInt("code", -1) == 0 && data != null
                    && data.optBoolean("isLogin", false)) {
                return (int) data.optDouble("money", 0);
            }
        } catch (Exception ignored) {
        }
        try {
            JSONObject all = NetWorkUtil.getJson("https://account.bilibili.com/site/getCoin");
            JSONObject data = all.optJSONObject("data");
            return data == null ? 0 : (int) data.optDouble("money", 0);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static class LoginExpiredException extends IOException {
        public LoginExpiredException() {
            super("登录状态已失效");
        }
    }


    public static int getUserVideos(long mid, int page, String searchKeyword, List<VideoCard> videoList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/space/wbi/arc/search?";
        url += "keyword=" + searchKeyword + "&mid=" + mid + "&order_avoided=true&order=pubdate&pn=" + page
                + "&ps=40&tid=0&platform=web&index=1&web_location=1550101";
        JSONObject all = NetWorkUtil.getJson(ConfInfoApi.signWBI(DmImgParamUtil.getDmImgParamsUrl(url)));
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            JSONObject list = data.optJSONObject("list");
            if (list == null) return -1;
            if (list.has("vlist") && !list.isNull("vlist")) {
                JSONArray vlist = list.getJSONArray("vlist");
                if (vlist.length() == 0) return 1;
                for (int i = 0; i < vlist.length(); i++) {
                    JSONObject card = vlist.getJSONObject(i);
                    String cover = card.getString("pic");
                    long play = card.getLong("play");
                    String playStr = StringUtil.toWan(play) + "观看";
                    long aid = card.getLong("aid");
                    String bvid = card.getString("bvid");
                    String upName = card.getString("author");
                    String title = card.getString("title");

                    videoList.add(new VideoCard(title, upName, playStr, cover, aid, bvid));
                }
                return 0;
            } else return -1;
        } else return -1;
    }


    public static int getUserArticles(long mid, int page, List<ArticleCard> articleList) throws IOException, JSONException {
        String offset = page <= 1 ? "" : USER_OPUS_OFFSETS.get(mid + ":" + page);
        if (page > 1 && offset == null) return 1;

        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/feed/space"
                + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("host_mid", mid)
                .put("page_size", 30);
        if (!offset.isEmpty()) url += "&offset=" + offset;

        JSONObject all = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (all.optInt("code", -1) != 0) return -1;
        JSONObject data = all.optJSONObject("data");
        if (data == null) return -1;
        JSONArray items = data.optJSONArray("items");
        if (items == null || items.length() == 0) return 1;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            long opusId;
            try {
                opusId = Long.parseLong(item.optString("opus_id", "0"));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (opusId <= 0) continue;
            ArticleCard articleCard = new ArticleCard();
            articleCard.id = opusId;
            articleCard.title = item.optString("content", "无标题内容");
            articleCard.cover = item.optString("cover", "");
            articleCard.upName = "";
            articleCard.view = "";
            articleList.add(articleCard);
        }

        boolean hasMore = data.optBoolean("has_more", false);
        String nextOffset = data.optString("offset", "");
        if (hasMore && !nextOffset.isEmpty()) {
            USER_OPUS_OFFSETS.put(mid + ":" + (page + 1), nextOffset);
            return 0;
        }
        return 1;
    }

    @Deprecated
    private static int getUserArticlesLegacy(long mid, int page, List<ArticleCard> articleList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/space/wbi/article?";
        url += "mid=" + mid + "&order_avoided=true&order=pubdate&pn=" + page
                + "&ps=30&tid=0";
        JSONObject all = NetWorkUtil.getJson(ConfInfoApi.signWBI(url), NetWorkUtil.webHeaders);
        if (all.has("data") && !all.isNull("data")) {
            JSONObject data = all.getJSONObject("data");
            if (data.has("articles")) {
                JSONArray list = data.getJSONArray("articles");
                if (list.length() == 0) return 1;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject card = list.getJSONObject(i);

                    ArticleCard articleCard = new ArticleCard();
                    articleCard.id = card.getLong("id");
                    articleCard.title = card.getString("title");
                    JSONObject stats = card.getJSONObject("stats");
                    articleCard.view = StringUtil.toWan(stats.getInt("view")) + "阅读";
                    articleCard.cover = card.getString("banner_url");
                    JSONObject author = card.getJSONObject("author");
                    articleCard.upName = author.getString("name");
                    articleList.add(articleCard);
                }
                return 0;
            } else return 1;
        } else return -1;
    }

    public static int followUser(long mid, boolean isFollow, int relationAttribute) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/modify"
                + "?statistics=%7B%22appId%22%3A100%2C%22platform%22%3A5%7D";
        String arg = "fid=" + mid + "&csrf=" + NetWorkUtil.getInfoFromCookie("bili_jct", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        arg += "&re_src=114";
        if (isFollow) arg += "&act=1"; //关注
        else arg += "&act=" + FollowRelationUtil.getUnfollowAction(relationAttribute); //取消关注
        JSONObject all = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        return all.getInt("code");
    }

    public static void exitLogin() {
        try {
            String url = "https://passport.bilibili.com/login/exit/v2";
            NetWorkUtil.get(url, NetWorkUtil.webHeaders);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int addContract(long upMid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v1/contract/add_contract";
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        String arg = "aid=&up_mid=" + upMid + "&source=4&scene=105&platform=web&mobi_app=pc&csrf=" + csrf;
        JSONObject all = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        return all.getInt("code");
    }

    public static JSONObject getMedalWall(long targetId) throws IOException, JSONException {
        String url = "https://api.live.bilibili.com/xlive/web-ucenter/user/MedalWall?target_id=" + targetId;
        JSONObject all = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);
        if (all.has("data") && !all.isNull("data")) {
            return all.getJSONObject("data");
        }
        return null;
    }
}
