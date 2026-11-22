package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.Cookies;

import org.json.JSONException;
import org.json.JSONObject;

public final class MainLoginSession {
    private MainLoginSession() {
    }

    public static long verifiedMid(String body, String cookieHeader) throws JSONException {
        JSONObject response = new JSONObject(body);
        if (response.optInt("code", Integer.MIN_VALUE) != 0) return 0;
        JSONObject data = response.optJSONObject("data");
        if (data == null || !data.optBoolean("isLogin", false)) return 0;

        long responseMid = data.optLong("mid", 0);
        Cookies cookies = new Cookies(cookieHeader == null ? "" : cookieHeader);
        long cookieMid;
        try {
            cookieMid = Long.parseLong(cookies.getOrDefault("DedeUserID", "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
        if (responseMid <= 0 || responseMid != cookieMid
                || cookies.getOrDefault("SESSDATA", "").isEmpty()
                || cookies.getOrDefault("bili_jct", "").isEmpty()) return 0;
        return responseMid;
    }
}
