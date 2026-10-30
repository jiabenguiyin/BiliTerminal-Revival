package com.RobinNotBad.BiliClient.api;

import android.graphics.Bitmap;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.QRCodeUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import okhttp3.Response;

/** QR authorization for the optional Bilibili App recommendation credential. */
public final class AppRecommendAuthApi {
    private static final String AUTH_URL =
            "https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code";
    private static final String POLL_URL =
            "https://passport.bilibili.com/x/passport-tv-login/qrcode/poll";

    private AppRecommendAuthApi() {
    }

    public static AuthSession createSession() throws IOException, JSONException {
        Map<String, String> params = new TreeMap<>();
        params.put("appkey", AppRecommendApi.APP_KEY);
        params.put("local_id", "0");
        // The TV login flow uses the fixed ts=0 form used by the compatible
        // clients; the request is still protected by the app sign.
        params.put("ts", "0");
        JSONObject result = postSigned(AUTH_URL, params);
        if (result.optInt("code", -1) != 0) {
            throw new IOException("获取 App 推荐二维码失败: " + result.optInt("code"));
        }
        JSONObject data = result.optJSONObject("data");
        if (data == null) throw new IOException("二维码响应缺少 data");
        String authCode = data.optString("auth_code", "");
        String url = data.optString("url", data.optString("qrcode_url", ""));
        if (authCode.isEmpty() || url.isEmpty()) throw new IOException("二维码响应不完整");
        return new AuthSession(authCode, QRCodeUtil.createQRCodeBitmap(url, 420, 420));
    }

    public static PollResult poll(AuthSession session) throws IOException, JSONException {
        Map<String, String> params = new TreeMap<>();
        params.put("appkey", AppRecommendApi.APP_KEY);
        params.put("auth_code", session.authCode);
        params.put("local_id", "0");
        params.put("ts", "0");
        JSONObject result = postSigned(POLL_URL, params);
        int code = result.optInt("code", -1);
        JSONObject data = result.optJSONObject("data");
        if (code != 0) return PollResult.pending(code);
        if (data == null) return PollResult.pending(-1);

        String accessKey = data.optString("access_token", data.optString("access_key", ""));
        if (accessKey.isEmpty()) return PollResult.pending(-2);
        long expiresIn = data.optLong("expires_in", 0L);
        return PollResult.success(accessKey, data.optLong("mid", 0L), expiresIn);
    }

    private static JSONObject postSigned(String url, Map<String, String> params)
            throws IOException, JSONException {
        try (Response response = NetWorkUtil.postDirect(
                url, AppRecommendApi.signedQuery(params), AppRecommendApi.appHeaders())) {
            if (response.body() == null) throw new IOException("登录接口没有响应内容");
            return new JSONObject(response.body().string());
        }
    }

    public static final class AuthSession {
        public final String authCode;
        public final Bitmap qrCode;

        AuthSession(String authCode, Bitmap qrCode) {
            this.authCode = authCode;
            this.qrCode = qrCode;
        }
    }

    public static final class PollResult {
        public final boolean success;
        public final int code;
        public final String accessKey;
        public final long mid;
        public final long expiresIn;

        private PollResult(boolean success, int code, String accessKey, long mid, long expiresIn) {
            this.success = success;
            this.code = code;
            this.accessKey = accessKey;
            this.mid = mid;
            this.expiresIn = expiresIn;
        }

        static PollResult pending(int code) {
            return new PollResult(false, code, "", 0L, 0L);
        }

        static PollResult success(String accessKey, long mid, long expiresIn) {
            return new PollResult(true, 0, accessKey, mid, expiresIn);
        }
    }
}
