package com.RobinNotBad.BiliClient.api;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Experimental Bilibili App recommendation feed. */
public final class AppRecommendApi {
    private static final String URL = "https://app.bilibili.com/x/v2/feed/index";
    static final String APP_KEY = "4409e2ce8ffd12b8";
    static final String APP_SECRET = "59b43e04ad6965f34319062b478f83dd";
    private static final String APP_USER_AGENT = "Mozilla/5.0 BiliDroid/9161100 "
            + "(Linux; U; Android 9; zh_CN; BiliTerminal Build/PQ3B.190801.002)";
    private static final String IPAD_BUILD = "90300100";
    private static long requestIndex = initialRequestIndex();
    private static long ipadIndexBase;
    private static long ipadRequestSequence;
    private static boolean coldOpenEventPending = true;

    private AppRecommendApi() {
    }

    public static synchronized void resetPaging() {
        resetPaging(true);
    }

    public static synchronized void resetPaging(boolean coldStart) {
        requestIndex = initialRequestIndex();
        ipadIndexBase = 0L;
        ipadRequestSequence = 0L;
        coldOpenEventPending = coldStart;
    }

    public static boolean hasAccessKey() {
        return !getAccessKey().isEmpty();
    }

    public static boolean hasRecommendationCredentials() {
        return hasAccessKey();
    }

    public static void getRecommend(java.util.List<VideoCard> videoCardList)
            throws IOException, JSONException {
        // The App feed uses its App access key for personalized results. A web SESSDATA
        // cookie is not an equivalent credential and must not be sent as one.
        requestRecommend(videoCardList, hasAccessKey());
    }

    /** Fetches personalized App recommendations for a caller-provided page index. */
    public static void getRecommend(java.util.List<VideoCard> videoCardList, long pageIndex)
            throws IOException, JSONException {
        requestRecommend(videoCardList, hasAccessKey(), pageIndex);
    }

    /** Fetches public App recommendations without forwarding any account credentials. */
    public static void getPublicRecommend(java.util.List<VideoCard> videoCardList)
            throws IOException, JSONException {
        requestRecommend(videoCardList, false);
    }

    /** Fetches public App recommendations for a caller-provided page index. */
    public static void getPublicRecommend(java.util.List<VideoCard> videoCardList, long pageIndex)
            throws IOException, JSONException {
        requestRecommend(videoCardList, false, pageIndex);
    }

    /** Returns the next App feed index; failed requests leave it available for retry. */
    public static synchronized long reservePageIndex() {
        return requestIndex;
    }

    static long nextPageIndex(long currentIndex, int responseItemCount) {
        return currentIndex + Math.max(1, responseItemCount);
    }

    static synchronized void completePageIndex(long currentIndex, int responseItemCount) {
        requestIndex = Math.max(requestIndex, nextPageIndex(currentIndex, responseItemCount));
    }

    private static void requestRecommend(java.util.List<VideoCard> videoCardList,
                                         boolean includeCredentials)
            throws IOException, JSONException {
        requestRecommend(videoCardList, includeCredentials, reservePageIndex());
    }

    private static void requestRecommend(java.util.List<VideoCard> videoCardList,
                                         boolean includeCredentials, long idx)
            throws IOException, JSONException {
        Exception primaryError = null;
        String openEvent = takeOpenEvent();
        // Use the Android profile first because it is the native profile for
        // this client and has the most predictable response shape. Keep the
        // iPad profile as a compatibility fallback for older devices/accounts.
        try {
            int parsedCount = requestRecommendWithProfile(
                    videoCardList, includeCredentials, idx, false, openEvent);
            if (parsedCount > 0) return;
        } catch (Exception error) {
            primaryError = error;
        }

        try {
            int parsedCount = requestRecommendWithProfile(
                    videoCardList, includeCredentials, idx, true, openEvent);
            if (parsedCount > 0) return;
        } catch (Exception compatibilityError) {
            if (primaryError == null) primaryError = compatibilityError;
        }

        if (primaryError != null) {
            if (primaryError instanceof IOException) throw (IOException) primaryError;
            if (primaryError instanceof JSONException) throw (JSONException) primaryError;
            throw new IOException("App 推荐请求失败", primaryError);
        }
        JSONObject emptyDetails = new JSONObject();
        try {
            emptyDetails.put("credential_mode", includeCredentials ? "access_key" : "anonymous");
            emptyDetails.put("reason", "both_profiles_empty");
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("app_recommend_empty", emptyDetails);
    }

    private static int requestRecommendWithProfile(java.util.List<VideoCard> videoCardList,
                                                   boolean includeCredentials, long idx,
                                                   boolean ipadProfile, String openEvent)
            throws IOException, JSONException {
        String accessKey = includeCredentials ? getAccessKey() : "";
        Map<String, String> params;
        if (ipadProfile) {
            params = buildIpadFeedParams(nextIpadRequestIndex(), accessKey);
        } else {
            boolean pull = idx == 0L;
            int flush = pull ? 0 : 8; // official PegasusFlush.NORMAL / PULL_UP
            params = buildFeedParams(idx, accessKey, pull, flush, openEvent);
        }
        String url = URL + "?" + signedQuery(params);
        String credentialMode = !includeCredentials ? "anonymous"
                : "access_key";
        JSONObject requestDetails = new JSONObject();
        try {
            requestDetails.put("profile", ipadProfile ? "ipad" : "android");
            requestDetails.put("idx", params.get("idx"));
            requestDetails.put("credential_mode", credentialMode);
            requestDetails.put("has_access_key", !accessKey.isEmpty());
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("app_recommend_request", requestDetails);
        // The App feed depends on the same direct app.bilibili.com request context
        // as the official client. Do not route it through the web relay: a relay
        // can change headers or response handling enough to produce an empty feed.
        JSONObject result;
        try {
            result = NetWorkUtil.getJsonDirect(url, appHeaders());
        } catch (IOException directError) {
            // A public feed can safely use the configured relay as a transport
            // fallback. Never send a personalized access_key through it.
            if (includeCredentials || !NetWorkUtil.isRelayActive()) throw directError;
            JSONObject relayDetails = new JSONObject();
            try {
                relayDetails.put("reason", "direct_network_error");
                relayDetails.put("profile", ipadProfile ? "ipad" : "android");
                relayDetails.put("credential_mode", "anonymous");
            } catch (JSONException ignored) {
            }
            try {
                result = NetWorkUtil.getJson(url, appHeaders());
                relayDetails.put("result", "recovered");
            } catch (IOException relayError) {
                relayDetails.put("result", "failed");
                relayDetails.put("error_type", relayError.getClass().getSimpleName());
                DiagnosticLogManager.record("app_recommend_transport_fallback", relayDetails);
                throw relayError;
            }
            DiagnosticLogManager.record("app_recommend_transport_fallback", relayDetails);
        }
        if (result.optInt("code", -1) != 0) {
            JSONObject details = new JSONObject();
            try {
                details.put("code", result.optInt("code", -1));
                details.put("credential_mode", credentialMode);
                details.put("profile", ipadProfile ? "ipad" : "android");
            } catch (JSONException ignored) {
            }
            DiagnosticLogManager.record("app_recommend_error", details);
            throw new IOException("App 推荐接口失败: " + result.optInt("code") + " "
                    + result.optString("message"));
        }

        JSONObject data = result.optJSONObject("data");
        JSONArray items = findItems(data);
        if (items == null) {
            DiagnosticLogManager.record("app_recommend_empty",
                    buildEmptyDetails(result, data, null, credentialMode,
                            ipadProfile ? "ipad" : "android"));
            return 0;
        }
        // The iPad profile has its own time-based cursor. Only the Android
        // profile participates in the shared fallback page index.
        if (!ipadProfile) completePageIndex(idx, items.length());
        int parsedCount = parseItems(items, videoCardList);
        JSONObject details = new JSONObject();
        try {
            details.put("item_count", items.length());
            details.put("parsed_count", parsedCount);
            details.put("credential_mode", credentialMode);
            details.put("profile", ipadProfile ? "ipad" : "android");
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("app_recommend_parsed", details);
        if (parsedCount == 0) {
            DiagnosticLogManager.record("app_recommend_empty",
                    buildEmptyDetails(result, data, items, credentialMode,
                            ipadProfile ? "ipad" : "android"));
        }
        return parsedCount;
    }

    /** Parameters used by Bilibili-Gate's verified iPad/iOS feed profile. */
    static Map<String, String> buildIpadFeedParams(long idx, String accessKey) {
        Map<String, String> params = new TreeMap<>();
        params.put("appkey", APP_KEY);
        if (accessKey != null && !accessKey.isEmpty()) {
            params.put("access_key", accessKey);
        }
        params.put("platform", "ios");
        params.put("mobi_app", "iphone");
        params.put("device", "pad");
        params.put("build", IPAD_BUILD);
        params.put("c_locale", "zh-Hans_CN");
        params.put("s_locale", "zh-Hans_CN");
        params.put("idx", String.valueOf(idx));
        return params;
    }

    static synchronized long nextIpadRequestIndex() {
        if (ipadIndexBase == 0L) {
            ipadIndexBase = System.currentTimeMillis() / 1000L;
        }
        // The verified iPad client uses a time-based page cursor: the first
        // request is now+100, then now+200, and so on. It is not the number of
        // cards returned by the previous page.
        ipadRequestSequence++;
        return ipadIndexBase + (ipadRequestSequence * 100L);
    }

    static Map<String, String> buildFeedParams(long idx, String accessKey) {
        return buildFeedParams(idx, accessKey, idx == 0L, idx == 0L ? 0 : 8,
                idx == 0L ? "cold" : "");
    }

    static Map<String, String> buildFeedParams(long idx, String accessKey,
                                               boolean pull, int flush) {
        return buildFeedParams(idx, accessKey, pull, flush, idx == 0L ? "cold" : "");
    }

    static Map<String, String> buildFeedParams(long idx, String accessKey,
                                               boolean pull, int flush, String openEvent) {
        Map<String, String> params = new TreeMap<>();
        params.put("actionKey", "appkey");
        params.put("appkey", APP_KEY);
        // The TV App API uses the snake-case access_key query parameter.
        params.put("access_key", accessKey == null ? "" : accessKey);
        params.put("platform", "android");
        params.put("mobi_app", "android");
        params.put("device", "phone");
        params.put("build", "9161100");
        params.put("channel", "master");
        params.put("pull", String.valueOf(pull));
        params.put("login_event", "0");
        params.put("open_event", openEvent == null ? "" : openEvent);
        params.put("flush", String.valueOf(flush));
        params.put("interest_id", "0");
        params.put("column", "2");
        params.put("window_size_type", "1");
        params.put("raw_window_size_type", "1");
        params.put("network", currentNetworkType());
        params.put("device_name", Build.MODEL == null ? "" : Build.MODEL);
        params.put("c_locale", "zh_CN");
        params.put("s_locale", "zh_CN");
        params.put("idx", String.valueOf(idx));
        return params;
    }

    static synchronized String takeOpenEvent() {
        if (coldOpenEventPending) {
            coldOpenEventPending = false;
            return "cold";
        }
        return ""; // Official OpenEvent.NORMAL is represented by an empty value.
    }

    public static int parseResponse(JSONObject response, List<VideoCard> videoCardList) throws IOException {
        if (response == null) return 0;
        int code = response.optInt("code", -1);
        if (code != 0) throw new IOException("App 推荐接口失败: " + code);
        return parseItems(findItems(response.optJSONObject("data")), videoCardList);
    }

    private static JSONArray findItems(JSONObject data) {
        if (data == null) return null;
        JSONArray items = data.optJSONArray("items");
        if (items == null) items = data.optJSONArray("item");
        if (items == null) items = data.optJSONArray("list");
        if (items == null) items = data.optJSONArray("recommend");
        if (items == null) items = nestedItems(data.optJSONObject("items"));
        if (items == null) items = nestedItems(data.optJSONObject("item"));
        if (items == null) items = nestedItems(data.optJSONObject("feed"));
        return items;
    }

    private static JSONArray nestedItems(JSONObject object) {
        if (object == null) return null;
        JSONArray items = object.optJSONArray("items");
        if (items == null) items = object.optJSONArray("item");
        if (items == null) items = object.optJSONArray("list");
        if (items == null) items = object.optJSONArray("recommend");
        return items;
    }

    /** Parses the App feed cards without retaining any credentials or raw response data. */
    public static int parseItems(JSONArray items, List<VideoCard> videoCardList) {
        if (items == null || videoCardList == null) return 0;
        Set<String> seen = new HashSet<>();
        for (VideoCard card : videoCardList) {
            addVideoKeys(seen, card.aid, card.bvid);
        }

        int parsedCount = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || item.optJSONObject("ad_info") != null || item.optBoolean("is_ad", false)) {
                continue;
            }

            String cardGoto = firstString(item, "card_goto").toLowerCase();
            String rawGoto = firstString(item, "goto").toLowerCase();
            String gotoType = rawGoto.isEmpty() ? cardGoto : rawGoto;
            boolean knownVideoCard = "av".equals(rawGoto) || "video".equals(rawGoto)
                    || "archive".equals(rawGoto) || "av".equals(cardGoto)
                    || cardGoto.startsWith("large_cover") || cardGoto.startsWith("small_cover")
                    || cardGoto.startsWith("three_two_cover") || cardGoto.startsWith("middle_cover");
            if (gotoType.contains("ad") || cardGoto.contains("ad")
                    || !knownVideoCard) {
                continue;
            }

            JSONObject args = item.optJSONObject("args");
            JSONObject playerArgs = item.optJSONObject("player_args");
            String bvid = firstString(item, "bvid", "bv_id");
            if (bvid.isEmpty()) bvid = firstString(playerArgs, "bvid", "bv_id");
            if (bvid.isEmpty()) bvid = firstString(args, "bvid", "bv_id");

            long aid = firstLong(playerArgs, "aid", 0L);
            aid = firstLong(args, "aid", aid);
            aid = firstLong(item, "aid", aid);
            aid = firstLong(item, "id", aid);
            String param = firstString(item, "param");
            if (param.isEmpty()) param = firstString(args, "param");
            if (bvid.isEmpty() && param.regionMatches(true, 0, "BV", 0, 2)) bvid = param;
            if (aid <= 0) aid = parseLong(param);

            if (bvid.isEmpty() && aid <= 0) continue;
            if (!addVideoKeys(seen, aid, bvid)) continue;

            String title = firstString(item, "title", "desc");
            String cover = firstString(item, "cover", "pic");
            String upName = firstString(args, "up_name");
            if (upName.isEmpty()) upName = firstString(item.optJSONObject("owner"), "name", "uname");
            if (upName.isEmpty()) upName = firstString(item, "author", "up_name");
            if (upName.isEmpty()) upName = firstString(item.optJSONObject("desc_button"), "text");
            String view = firstString(item, "cover_left_text_1", "cover_left_text_2",
                    "cover_right_text_1", "cover_right_text_2", "cover_right_text", "desc");
            if (view.isEmpty()) {
                JSONObject stat = item.optJSONObject("stat");
                long viewCount = stat == null ? 0L : stat.optLong("view", 0L);
                if (viewCount > 0) view = StringUtil.toWan(viewCount) + "观看";
            }

            long cid = firstLong(playerArgs, "cid", 0L);
            cid = firstLong(args, "cid", cid);
            cid = firstLong(item, "cid", cid);
            videoCardList.add(new VideoCard(title, upName, view, normalizeCover(cover), aid, bvid, cid));
            parsedCount++;
        }
        return parsedCount;
    }

    static ArrayList<String> appHeaders() {
        ArrayList<String> headers = new ArrayList<>();
        headers.add("User-Agent");
        headers.add(APP_USER_AGENT);
        headers.add("Accept");
        headers.add("application/json");
        String buvid = cookieValue("buvid3");
        if (!buvid.isEmpty()) {
            headers.add("Buvid");
            headers.add(buvid);
        }
        String deviceId = androidId();
        if (!deviceId.isEmpty()) {
            headers.add("Device-ID");
            headers.add(deviceId);
        }
        return headers;
    }

    static String currentNetworkType() {
        try {
            Context context = BiliTerminal.context;
            if (context != null) {
                ConnectivityManager manager =
                        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo active = manager == null ? null : manager.getActiveNetworkInfo();
                if (active != null && active.isConnected()) {
                    if (active.getType() == ConnectivityManager.TYPE_WIFI) return "wifi";
                    if (active.getType() == ConnectivityManager.TYPE_MOBILE) {
                        return mobileNetworkType(active.getSubtype());
                    }
                }
            }
        } catch (Throwable ignored) {
            // The feed must remain usable on old ROMs with incomplete network APIs.
        }
        return "";
    }

    static String mobileNetworkType(int subtype) {
        return "mobile";
    }

    private static String androidId() {
        try {
            Context context = BiliTerminal.context;
            if (context == null) return "";
            String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return id == null ? "" : id;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String cookieValue(String key) {
        try {
            if (SharedPreferencesUtil.sharedPreferences == null) return "";
            String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            String value = NetWorkUtil.getInfoFromCookie(key, cookies);
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static JSONObject buildEmptyDetails(JSONObject response, JSONObject data,
                                                 JSONArray items, String credentialMode,
                                                 String profile) {
        JSONObject details = new JSONObject();
        try {
            details.put("code", response == null ? -1 : response.optInt("code", -1));
            details.put("has_data", data != null);
            details.put("item_count", items == null ? 0 : items.length());
            details.put("credential_mode", credentialMode);
            details.put("profile", profile);
            if (data != null) details.put("data_keys", jsonKeys(data));
            if (items != null && items.length() > 0) {
                JSONObject firstItem = items.optJSONObject(0);
                if (firstItem != null) {
                    details.put("first_item_keys", jsonKeys(firstItem));
                    details.put("first_goto", firstString(firstItem, "goto"));
                    details.put("first_card_goto", firstString(firstItem, "card_goto"));
                }
            }
        } catch (JSONException ignored) {
        }
        return details;
    }

    private static JSONArray jsonKeys(JSONObject object) {
        JSONArray keys = new JSONArray();
        java.util.Iterator<String> iterator = object.keys();
        int count = 0;
        while (iterator.hasNext() && count++ < 24) keys.put(iterator.next());
        return keys;
    }

    private static String normalizeCover(String cover) {
        if (cover == null || cover.isEmpty()) return "";
        if (cover.startsWith("//")) return "https:" + cover;
        if (cover.startsWith("http://")) return "https://" + cover.substring("http://".length());
        return cover;
    }

    private static String firstString(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private static long firstLong(JSONObject object, String key, long fallback) {
        if (object == null) return fallback;
        return object.optLong(key, fallback);
    }

    private static long parseLong(String value) {
        try {
            if (value == null) return 0L;
            String normalized = value.trim().toLowerCase();
            if (normalized.startsWith("av")) normalized = normalized.substring(2);
            if (normalized.isEmpty()) return 0L;
            for (int i = 0; i < normalized.length(); i++) {
                if (!Character.isDigit(normalized.charAt(i))) return 0L;
            }
            return Long.parseLong(normalized);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static boolean addVideoKeys(Set<String> seen, long aid, String bvid) {
        boolean duplicate = false;
        if (bvid != null && !bvid.isEmpty()) duplicate |= !seen.add("b:" + bvid);
        if (aid > 0) duplicate |= !seen.add("a:" + aid);
        return !duplicate;
    }

    static String signedQuery(Map<String, String> params) throws IOException {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        String query = encode(sorted);
        return query + "&sign=" + ToolsUtil.md5(query + APP_SECRET);
    }

    private static long initialRequestIndex() {
        // The official Pegasus client starts the first feed request at idx=0.
        return 0L;
    }

    private static String getAccessKey() {
        String key = SharedPreferencesUtil.getString(
                SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY, "").trim();
        long expiresAt = SharedPreferencesUtil.getLong(
                SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY_EXPIRES_AT, 0L);
        if (!key.isEmpty() && expiresAt > 0L && System.currentTimeMillis() >= expiresAt) {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY, "");
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY_EXPIRES_AT, 0L);
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_BOUND_MID, 0L);
            return "";
        }
        if (!key.isEmpty()) return key;
        // The normal login access_key may belong to another client/app. Do not
        // send it to the App feed as if it were the separately bound TV key.
        return "";
    }

    private static String encode(Map<String, String> params) throws IOException {
        StringBuilder result = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (result.length() > 0) result.append('&');
                result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                result.append('=');
                result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            throw new IOException(e);
        }
        return result.toString();
    }
}
