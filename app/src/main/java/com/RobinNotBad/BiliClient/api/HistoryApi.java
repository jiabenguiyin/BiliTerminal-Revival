package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.SearchQueryUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class HistoryApi {

    /**
     * 上传历史记录
     *
     * @param aid      视频aid
     * @param cid      分集cid
     * @param progress 观看进度，单位为s
     * @throws IOException
     */
    public static void reportHistory(long aid, long cid, long progress) throws IOException {
        String url = "https://api.bilibili.com/x/v2/history/report";
        String per = "aid=" + aid + "&cid=" + cid
                + "&progress=" + (progress >= 0 ? progress : "")
                + "&platform=pc"
                + "&csrf=" + SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
        NetWorkUtil.post(url, per, NetWorkUtil.webHeaders);
    }

    /**
     * 获取视频历史记录
     *
     * @param lastResult 上一次获取返回的ApiResult，如果是第一次就传入新对象
     * @param videoList  已有的视频列表
     * @return 新的ApiResult，包含了返回码、文本信息以及翻页所需的offset
     * @throws IOException
     * @throws JSONException
     */
    public static ApiResult getHistory(ApiResult lastResult, List<VideoCard> videoList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/history/cursor?type=archive&view_at=" + lastResult.timestamp + "&business=" + lastResult.business + "&max=" + lastResult.offset;
        JSONObject result = NetWorkUtil.getJson(url);
        return parseHistoryPage(result, videoList);
    }

    static String buildHistorySearchUrl(String keyword, int page) {
        return "https://api.bilibili.com/x/web-interface/history/search" + new NetWorkUtil.FormData()
                .setUrlParam(true).put("keyword", keyword == null ? "" : keyword.trim())
                .put("pn", page).put("business", "all");
    }

    public static ApiResult searchHistory(String keyword, int page, List<VideoCard> output)
            throws IOException, JSONException {
        return parseHistoryPage(NetWorkUtil.getJson(buildHistorySearchUrl(keyword, page)), output);
    }

    static ApiResult parseHistoryPage(JSONObject result, List<VideoCard> videoList) throws JSONException {
        ApiResult apiResult = new ApiResult(result);
        if (apiResult.code != 0) return apiResult;
        JSONObject data = result.optJSONObject("data");
        if (data != null) {
            JSONArray list = data.optJSONArray("list");
            if (list == null) return apiResult.setBottom(true);
            for (int i = 0; i < list.length(); i++) {
                JSONObject videoCard = list.optJSONObject(i);
                if (videoCard == null) continue;
                String title = SearchQueryUtil.plainResultTitle(videoCard.optString("title"));
                String cover = videoCard.optString("cover");
                String upName = videoCard.optString("author_name");
                int progress = videoCard.optInt("progress");

                JSONObject history = videoCard.optJSONObject("history");
                if (history == null || !"archive".equals(history.optString("business", "archive"))) continue;
                long aid = history.optLong("oid");
                if (aid <= 0) continue;
                String bvid = history.optString("bvid");

                String viewStr;
                if (progress < 0) viewStr = "已看完";
                else if (progress == 0) viewStr = "还没看过";
                else viewStr = "看到" + StringUtil.toTime(progress);

                videoList.add(new VideoCard(title, upName, viewStr, cover, aid, bvid));
            }
            if (list.length() == 0) apiResult.isBottom = true;

            JSONObject cursor = data.optJSONObject("cursor");
            if (cursor != null) {
                apiResult.business = cursor.optString("business");
                apiResult.offset = cursor.optLong("max");
                apiResult.timestamp = cursor.optLong("view_at");
                if (cursor.optBoolean("is_end", false)) apiResult.isBottom = true;
            }
            if (data.has("has_more") && !data.optBoolean("has_more")) apiResult.isBottom = true;
        } else apiResult.isBottom = true;
        return apiResult;
    }

}
