package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Response;

public final class ShareApi {
    private static final String SHARE_URL = "https://api.bilibili.com/x/web-interface/share/add";

    private ShareApi() { }

    public static int shareVideo(long aid, String cookies) throws IOException, JSONException {
        if (aid <= 0 || cookies == null || cookies.trim().isEmpty())
            throw new IOException("登录信息无效");
        ArrayList<String> headers = new ArrayList<>(NetWorkUtil.webHeaders);
        for (int i = 0; i + 1 < headers.size(); i += 2) {
            if ("Cookie".equalsIgnoreCase(headers.get(i))) {
                headers.set(i + 1, cookies);
                break;
            }
        }
        String csrf = NetWorkUtil.getInfoFromCookie("bili_jct", cookies);
        String form = new NetWorkUtil.FormData().put("aid", aid).put("csrf", csrf).toString();
        try (Response response = NetWorkUtil.post(SHARE_URL, form, headers)) {
            if (response.body() == null) throw new JSONException("分享接口返回为空");
            return parseResponseCode(new JSONObject(response.body().string()));
        }
    }

    static int parseResponseCode(JSONObject response) throws JSONException {
        return response.getInt("code");
    }
}
