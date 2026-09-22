package com.RobinNotBad.BiliClient.api;

import android.util.Log;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

public class LikeCoinFavApi {

    public static int triple(long aid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/archive/like/triple";
        String per = "aid=" + aid + "&csrf=" + SharedPreferencesUtil.getString("csrf", "");

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-三联", result.toString());
        return result.getInt("code");
    }

    public static int like(long aid, int likeState) throws IOException, JSONException {  //likeState 1点赞0取消
        String url = "https://api.bilibili.com/x/web-interface/archive/like";
        String per = "aid=" + aid + "&like=" + likeState + "&csrf=" + SharedPreferencesUtil.getString("csrf", "");

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-点赞", result.toString());
        return result.getInt("code");
    }

    public static int coin(long aid, int multiply) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/coin/add";
        String per = "aid=" + aid + "&multiply=" + multiply + "&csrf=" + SharedPreferencesUtil.getString("csrf", "");

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-投币", result.toString());
        return result.getInt("code");
    }

    public static int favorite(long aid, long fid) throws IOException, JSONException {
        String strMid = String.valueOf(SharedPreferencesUtil.getLong("mid", 0));
        String addFid = fid + strMid.substring(strMid.length() - 2);
        String url = "https://api.bilibili.com/medialist/gateway/coll/resource/deal";
        String per = "rid=" + aid + "&type=2&add_media_ids=" + addFid + "&del_media_ids=&csrf=" + SharedPreferencesUtil.getString("csrf", "");

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-添加收藏", result.toString());
        return result.getInt("code");
    }

    public static ApiResult getVideoStats(VideoInfo videoInfo) {
        ApiResult apiResult = new ApiResult();
        if (SharedPreferencesUtil.getLong("mid", 0) == 0) return apiResult;
        // The relation endpoint is optional metadata. A stale local mid can remain
        // after an expired or manually removed Cookie, so avoid a doomed request
        // and never let it break the public video detail page.
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        if (NetWorkUtil.getInfoFromCookie("SESSDATA", cookies).isEmpty()) return apiResult;
        try {
            String url = "https://api.bilibili.com/x/web-interface/archive/relation?aid=" + videoInfo.aid;
            JSONObject result = NetWorkUtil.getJson(url);
            apiResult.fromJson(result);
            if (apiResult.code != 0) {
                recordRelationFailure(apiResult);
                return apiResult;
            }
            JSONObject data = result.optJSONObject("data");
            if (data == null) {
                recordRelationFailure(apiResult);
                return apiResult;
            }
            videoInfo.stats.followed = data.optBoolean("attention");
            videoInfo.stats.liked = data.optBoolean("like");
            videoInfo.stats.disliked = data.optBoolean("dislik");
            videoInfo.stats.favoured = data.optBoolean("favorite");
            videoInfo.stats.coined = data.optInt("coin");
        } catch (Exception e) {
            // This request only fills button state. Keep the page usable when the
            // account session is expired, blocked, or temporarily unavailable.
            JSONObject details = new JSONObject();
            try {
                details.put("aid", videoInfo == null ? 0 : videoInfo.aid);
                details.put("error", e.getClass().getSimpleName());
                details.put("message", apiResult.message == null ? "" : apiResult.message);
            } catch (JSONException ignored) {
            }
            DiagnosticLogManager.record("video_relation_optional_failed", details);
        }
        return apiResult;
    }

    private static void recordRelationFailure(ApiResult result) {
        JSONObject details = new JSONObject();
        try {
            details.put("code", result.code);
            details.put("message", result.message == null ? "" : result.message);
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("video_relation_optional_rejected", details);
    }

}
