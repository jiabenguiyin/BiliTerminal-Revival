package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.api.LoginApi;

import org.json.JSONObject;

/** Common credential handoff for all native main-site login modes. */
public final class MainLoginUtil {
    private MainLoginUtil() {
    }

    public static boolean save(LoginApi.LoginResponse response) {
        if (response == null || response.body == null) return false;
        JSONObject data = response.body.optJSONObject("data");
        JSONObject tokenInfo = data == null ? null : data.optJSONObject("token_info");
        String refreshToken = tokenInfo == null ? "" : tokenInfo.optString("refresh_token", "");
        if (refreshToken.isEmpty() && data != null) refreshToken = data.optString("refresh_token", "");

        String existing = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        String merged = CookieMergeUtil.merge(existing, response.setCookieHeaders, true);
        if (!AccountManager.saveLogin(merged, refreshToken)) return false;
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.setup, true);

        try {
            LoginApi.requestSSOs();
        } catch (Exception error) {
            Logu.e("MainLogin", "SSO 通知失败，不影响登录: " + error.getMessage());
        }
        return true;
    }
}
