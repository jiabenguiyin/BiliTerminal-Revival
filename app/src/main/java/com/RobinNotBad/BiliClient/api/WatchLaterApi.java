package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import okhttp3.Response;

//稍后再看API
//2023-08-17

public class WatchLaterApi {
    public static ArrayList<VideoCard> getWatchLaterList() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/history/toview/web";

        return parseWatchLaterList(NetWorkUtil.getJson(url));
    }

    static ArrayList<VideoCard> parseWatchLaterList(JSONObject result) throws JSONException {
        ArrayList<VideoCard> videoCardList = new ArrayList<>();
        JSONObject data = result.optJSONObject("data");
        if (data == null) return videoCardList;

        JSONArray list = data.optJSONArray("list");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject videoCard = list.optJSONObject(i);
                if (videoCard == null) continue;
                long aid = videoCard.optLong("aid", 0);
                String bvid = videoCard.optString("bvid", "");
                String title = videoCard.optString("title", "");
                String cover = videoCard.optString("pic", "");
                JSONObject owner = videoCard.optJSONObject("owner");
                JSONObject stat = videoCard.optJSONObject("stat");
                String upName = owner == null ? "" : owner.optString("name", "");
                long view = stat == null ? 0 : stat.optLong("view", 0);
                String viewStr = StringUtil.toWan(view) + "观看";
                videoCardList.add(new VideoCard(title, upName, viewStr, cover, aid, bvid));
            }
        }
        return videoCardList;
    }

    public static int delete(long aid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/history/toview/del";
        String per = "aid=" + aid + "&csrf=" + SharedPreferencesUtil.getString("csrf", "");

        Response response = NetWorkUtil.post(url, per, NetWorkUtil.webHeaders);

        JSONObject result = new JSONObject(Objects.requireNonNull(response.body()).string());

        return result.getInt("code");
    }

    public static int add(long aid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/history/toview/add";
        String per = "aid=" + aid + "&csrf=" + SharedPreferencesUtil.getString("csrf", "");

        Response response = NetWorkUtil.post(url, per, NetWorkUtil.webHeaders);

        JSONObject result = new JSONObject(Objects.requireNonNull(response.body()).string());

        return result.getInt("code");
    }
}
