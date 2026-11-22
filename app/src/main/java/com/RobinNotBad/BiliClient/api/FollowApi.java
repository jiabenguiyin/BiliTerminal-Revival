package com.RobinNotBad.BiliClient.api;

//关注api
//2023-08-27

import com.RobinNotBad.BiliClient.model.FollowTag;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;


public class FollowApi {
    static String buildFollowingSearchUrl(long mid, int page, String keyword) {
        try {
            return "https://api.bilibili.com/x/relation/followings/search?vmid=" + mid
                    + "&pn=" + page + "&ps=50&name=" + URLEncoder.encode(keyword, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            throw new IllegalStateException("UTF-8 unavailable");
        }
    }

    public static int searchFollowingList(long mid, int page, String keyword, List<UserInfo> userList)
            throws IOException, JSONException {
        if (page < 1 || page > 5) return 1;
        JSONObject callback = NetWorkUtil.getJson(buildFollowingSearchUrl(mid, page, keyword));
        int code = callback.optInt("code", -1);
        if (code != 0)
            throw new JSONException(code + "：" + callback.optString("message", "未知API错误"));
        JSONObject data = callback.getJSONObject("data");
        JSONArray list = data.getJSONArray("list");
        if (list.length() == 0) return 1;
        for (int i = 0; i < list.length(); i++) {
            JSONObject userInfo = list.getJSONObject(i);
            userList.add(new UserInfo(userInfo.getLong("mid"), userInfo.getString("uname"),
                    userInfo.getString("face"), userInfo.optString("sign", ""),
                    0, 0, 0, true, "", 0, "", userInfo.optLong("mtime", 0), 0));
        }
        return 0;
    }

    public static int getFollowingList(long mid, int page, List<UserInfo> userList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/followings?vmid=" + mid + "&pn=" + page + "&ps=20&order=desc&order_type=attention";
        JSONObject callback = NetWorkUtil.getJson(url);
        if (callback.optInt("code", -1) != 0)
            throw new JSONException(callback.optInt("code", -1) + "：" + callback.optString("message", "未知API错误"));
        JSONObject data = callback.getJSONObject("data");
        JSONArray list = data.getJSONArray("list");
        if (list.length() == 0) return 1;
        else {
            for (int i = 0; i < list.length(); i++) {
                JSONObject userInfo = list.getJSONObject(i);
                String name = userInfo.getString("uname");
                long uid = userInfo.getLong("mid");
                String avatar = userInfo.getString("face");
                String sign = userInfo.getString("sign");
                userList.add(new UserInfo(uid, name, avatar, sign, 0, 0, 0, true, "", 0, "", userInfo.optLong("mtime", 0), 0));
            }
            return 0;
        }
    }

    public static int getFollowerList(long mid, int page, List<UserInfo> userList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/followers?vmid=" + mid + "&pn=" + page + "&ps=20&order=desc&order_type=attention";
        JSONObject callback = NetWorkUtil.getJson(url);
        if (callback.optInt("code", -1) != 0)
            throw new JSONException(callback.optInt("code", -1) + "：" + callback.optString("message", "未知API错误"));
        JSONObject data = callback.getJSONObject("data");
        JSONArray list = data.getJSONArray("list");
        if (list.length() == 0) return 1;
        else {
            for (int i = 0; i < list.length(); i++) {
                JSONObject userInfo = list.getJSONObject(i);
                String name = userInfo.getString("uname");
                long uid = userInfo.getLong("mid");
                String avatar = userInfo.getString("face");
                String sign = userInfo.getString("sign");
                userList.add(new UserInfo(uid, name, avatar, sign, 0, 0, 0, true, "", 0, "", userInfo.optLong("mtime", 0), 0));
            }
            return 0;
        }
    }

    public static List<FollowTag> getFollowTags() throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/tags";
        JSONObject callback = NetWorkUtil.getJson(url);
        if (callback.optInt("code", -1) != 0)
            throw new JSONException(callback.optInt("code", -1) + "：" + callback.optString("message", "未知API错误"));
        JSONArray data = callback.getJSONArray("data");
        List<FollowTag> tagList = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject tag = data.getJSONObject(i);
            int tagid = tag.getInt("tagid");
            String name = tag.getString("name");
            int count = tag.getInt("count");
            tagList.add(new FollowTag(tagid, name, count));
        }
        return tagList;
    }

    public static int getFollowTagUsers(int tagid, int page, List<UserInfo> userList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/relation/tag?tagid=" + tagid + "&pn=" + page + "&ps=20";
        JSONObject callback = NetWorkUtil.getJson(url);
        if (callback.optInt("code", -1) != 0)
            throw new JSONException(callback.optInt("code", -1) + "：" + callback.optString("message", "未知API错误"));
        JSONArray data = callback.getJSONArray("data");
        if (data.length() == 0) return 1;
        else {
            for (int i = 0; i < data.length(); i++) {
                JSONObject userInfo = data.getJSONObject(i);
                String name = userInfo.getString("uname");
                long uid = userInfo.getLong("mid");
                String avatar = userInfo.getString("face");
                String sign = userInfo.getString("sign");
                userList.add(new UserInfo(uid, name, avatar, sign, 0, 0, 0, true, "", 0, "", 0));
            }
            return 0;
        }
    }
}
