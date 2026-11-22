package com.RobinNotBad.BiliClient.api;

import android.graphics.Bitmap;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.QRCodeUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Response;

/**
 * Created by liupe on 2018/10/6.
 * 各位大佬好
 * #以下代码修改自腕上哔哩的开源项目，感谢开源者做出的贡献！
 */

public class LoginApi {
    private static String oauthKey;

    /** Body plus authentication cookies from a password or SMS login response. */
    public static final class LoginResponse {
        public final JSONObject body;
        public final List<String> setCookieHeaders;

        public LoginResponse(JSONObject body, List<String> setCookieHeaders) {
            this.body = body;
            this.setCookieHeaders = setCookieHeaders == null
                    ? new ArrayList<>() : new ArrayList<>(setCookieHeaders);
        }
    }

    public static Bitmap getLoginQR() throws JSONException, IOException {
        oauthKey = null;
        String url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header&go_url=https:%2F%2Fwww.bilibili.com%2F";
        QrLoginCode loginCode = QrLoginCode.parse(
                NetWorkUtil.getJsonDirect(url, CookiesApi.genWebHeaders()).toString());
        oauthKey = loginCode.key;
        return QRCodeUtil.createQRCodeBitmap(loginCode.url, 320, 320);
    }

    public static Response getLoginState() throws IOException {
        String key = oauthKey;
        if (key == null || key.isEmpty()) throw new IOException("二维码尚未准备好");
        String encodedKey = URLEncoder.encode(key, "UTF-8");
        return NetWorkUtil.getDirect("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?source=main-fe-header&qrcode_key=" + encodedKey, CookiesApi.genWebHeaders());
    }

    public static long verifyMainWebSession(String cookies) throws IOException, JSONException {
        ArrayList<String> headers = CookiesApi.genWebHeaders();
        headers.set(1, cookies == null ? "" : cookies);
        JSONObject response = NetWorkUtil.getJsonDirect(
                "https://api.bilibili.com/x/web-interface/nav", headers);
        return MainLoginSession.verifiedMid(response.toString(), cookies);
    }

    public static void activateLoginRedirect(String target) throws IOException {
        if (!isTrustedMainUrl(target)) return;
        try (Response ignored = NetWorkUtil.getDirect(target, CookiesApi.genWebHeaders())) {
            // Consume Set-Cookie through the shared client, then close the response.
        }
    }

    public static void requestSSOs() throws JSONException, IOException {
        String listUrl = "https://passport.bilibili.com/x/passport-login/web/sso/list";
        String body;
        try (Response response = NetWorkUtil.postDirect(listUrl,
                new NetWorkUtil.FormData().put("csrf", SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "")).toString(),
                CookiesApi.genWebHeaders())) {
            if (response.body() == null) return;
            body = response.body().string();
        }
        JSONObject listResult = new JSONObject(body);
        JSONObject data = listResult.optJSONObject("data");
        JSONArray sso = data == null ? null : data.optJSONArray("sso");
        if (sso != null) {
            for (int i = 0; i < sso.length(); i++) {
                String target = sso.optString(i, "");
                if (!isTrustedMainUrl(target)) continue;
                try (Response ignored = NetWorkUtil.postDirect(target, "", CookiesApi.genWebHeaders())) {
                    // SSO endpoints set cookies; the response body is not needed.
                }
            }
        }
    }

    public static JSONObject getCaptcha() throws IOException, JSONException {
        return NetWorkUtil.getJson("https://passport.bilibili.com/x/passport-login/captcha?source=main_web",
                NetWorkUtil.webHeaders);
    }

    public static JSONObject getWebKey() throws IOException, JSONException {
        return NetWorkUtil.getJson("https://passport.bilibili.com/x/passport-login/web/key",
                NetWorkUtil.webHeaders);
    }

    public static LoginResponse passwordLoginWithCookies(String username, String encryptedPassword,
                                                         String captchaToken, String challenge,
                                                         String validate, String seccode)
            throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/web/login";
        String body = new NetWorkUtil.FormData()
                .put("source", "main_web")
                .put("username", username)
                .put("password", encryptedPassword)
                .put("keep", "0")
                .put("token", captchaToken)
                .put("challenge", challenge)
                .put("validate", validate)
                .put("seccode", seccode)
                .toString();
        return executeLogin(url, body);
    }

    public static LoginResponse smsSendWithCookies(String phone, String captchaToken,
                                                    String challenge, String validate,
                                                    String seccode)
            throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/web/sms/send";
        String body = new NetWorkUtil.FormData()
                .put("cid", "86")
                .put("tel", phone)
                .put("source", "main_web")
                .put("token", captchaToken)
                .put("challenge", challenge)
                .put("validate", validate)
                .put("seccode", seccode)
                .toString();
        return executeLogin(url, body);
    }

    public static LoginResponse smsLoginWithCookies(String phone, String code, String captchaKey)
            throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/web/login/sms";
        String body = new NetWorkUtil.FormData()
                .put("cid", "86")
                .put("tel", phone)
                .put("source", "main_web")
                .put("code", code)
                .put("captcha_key", captchaKey == null ? "" : captchaKey)
                .put("keep", "1")
                .toString();
        return executeLogin(url, body);
    }

    private static LoginResponse executeLogin(String url, String body) throws IOException, JSONException {
        try (Response response = NetWorkUtil.post(url, body, NetWorkUtil.webHeaders)) {
            if (response.body() == null) throw new IOException("登录响应为空");
            JSONObject json = new JSONObject(response.body().string());
            return new LoginResponse(json, response.headers("Set-Cookie"));
        }
    }

    private static boolean isTrustedMainUrl(String target) {
        try {
            URI uri = new URI(target);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null
                    && host != null
                    && (host.equalsIgnoreCase("bilibili.com")
                    || host.toLowerCase().endsWith(".bilibili.com"));
        } catch (Exception ignored) {
            return false;
        }
    }

}
