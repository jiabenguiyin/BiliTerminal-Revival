package com.RobinNotBad.BiliClient.api;

import android.util.Base64;

import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.CookieMergeUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import okhttp3.Response;

/*
这里是Cookie刷新逻辑
https://socialsisteryi.github.io/bilibili-API-collect/docs/login/cookie_refresh.html#%E8%8E%B7%E5%8F%96refresh-csrf
 */
public class CookieRefreshApi {
    public static JSONObject cookieInfo() throws IOException, JSONException {
        String url = "https://passport.bilibili.com/x/passport-login/web/cookie/info";
        JSONObject result = NetWorkUtil.getJson(url);
        return result.getJSONObject("data");
    }

    public static String getCorrespondPath(long timestamp) { //时间戳要精准到毫秒！
        /*
        https://socialsisteryi.github.io/bilibili-API-collect/docs/login/cookie_refresh.html#%E7%94%9F%E6%88%90correspondpath%E7%AE%97%E6%B3%95
         */
        try {
            String publicKeyPEM = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDLgd2OAkcGVtoE3ThUREbio0EgUc/prcajMKXvkCKFCWhJYJcLkcM2DKKcSeFpD/j6Boy538YXnR6VhcuUJOhH2x71nzPjfdTcqMz7djHum0qSZA0AyCBDABUqCrfNgCiJ00Ra7GmRj+YCK1NJEuewlb40JNrRuoEUXpabUzGB8QIDAQAB";
            X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(Base64.decode(publicKeyPEM, Base64.DEFAULT));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(x509EncodedKeySpec);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            OAEPParameterSpec oaepParameterSpec = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParameterSpec);
            String data = "refresh_" + timestamp;
            return base16Encode(cipher.doFinal(data.getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String getRefreshCsrf(String CorrespondPath) throws IOException {
        if (Objects.equals(CorrespondPath, "")) return "";
        String url = "https://www.bilibili.com/correspond/1/" + CorrespondPath;
        try (Response response = NetWorkUtil.get(url)) {
            if (response.body() == null) return "";
            Document document = Jsoup.parse(response.body().string());
            if (document.select("#1-name").size() > 0)
                return document.select("#1-name").get(0).text();
            return "";
        }
    }

    public static synchronized boolean refreshCookie(String RefreshCsrf) throws JSONException, IOException {
        if (!confirmPendingRefresh()) return false;
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        String token = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
        return refreshCookie(RefreshCsrf, cookies, token);
    }

    private static boolean refreshCookie(String refreshCsrf, String oldCookies, String oldToken)
            throws JSONException, IOException {
        if (refreshCsrf.isEmpty() || oldToken.isEmpty() || !isCurrent(oldCookies, oldToken)) return false;
        String url = "https://passport.bilibili.com/x/passport-login/web/cookie/refresh";
        String args = new NetWorkUtil.FormData()
                .put("csrf", NetWorkUtil.getInfoFromCookie("bili_jct", oldCookies))
                .put("refresh_csrf", refreshCsrf).put("source", "main_web")
                .put("refresh_token", oldToken).toString();
        String newToken;
        String newCookies;
        try (Response response = NetWorkUtil.post(url, args, headersFor(oldCookies))) {
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("Refresh HTTP " + response.code());
            JSONObject result = new JSONObject(response.body().string());
            if (result.getInt("code") != 0) {
                Logu.e("Cookie刷新失败", "刷新时返回:" + result.getInt("code"));
                return false;
            }
            newToken = result.getJSONObject("data").getString("refresh_token");
            String issued = CookieMergeUtil.merge("", response.headers("Set-Cookie"), true);
            if (NetWorkUtil.getInfoFromCookie("SESSDATA", issued).isEmpty()
                    || NetWorkUtil.getInfoFromCookie("bili_jct", issued).isEmpty()
                    || !NetWorkUtil.getInfoFromCookie("DedeUserID", oldCookies).equals(
                            NetWorkUtil.getInfoFromCookie("DedeUserID", issued))) return false;
            newCookies = CookieMergeUtil.merge(oldCookies, response.headers("Set-Cookie"), true);
        }
        // The server already issued the new pair. A lost confirmation must not roll it back.
        if (!AccountManager.replaceRefreshedSession(oldCookies, oldToken, newCookies, newToken)) return false;
        confirmPendingRefresh();
        Logu.v("Cookie刷新", "新凭据已保存");
        return true;
    }

    public static synchronized boolean refreshIfNeeded() throws IOException, JSONException {
        AccountManager.restoreCurrentAccount();
        if (!confirmPendingRefresh()) return false;
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        String token = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
        if (token.isEmpty() || NetWorkUtil.getInfoFromCookie("SESSDATA", cookies).isEmpty()) return false;
        JSONObject info = NetWorkUtil.getJson(
                "https://passport.bilibili.com/x/passport-login/web/cookie/info", headersFor(cookies));
        JSONObject data = info.optJSONObject("data");
        if (info.optInt("code", -1) != 0 || data == null || !data.optBoolean("refresh")) return false;
        String path = getCorrespondPath(data.getLong("timestamp"));
        if (path.isEmpty() || !isCurrent(cookies, token)) return false;
        String refreshCsrf;
        try (Response response = NetWorkUtil.get("https://www.bilibili.com/correspond/1/" + path,
                headersFor(cookies))) {
            if (!response.isSuccessful() || response.body() == null) return false;
            Document document = Jsoup.parse(response.body().string());
            if (document.select("#1-name").isEmpty()) return false;
            refreshCsrf = document.select("#1-name").get(0).text();
        }
        return refreshCookie(refreshCsrf, cookies, token);
    }

    public static synchronized boolean confirmPendingRefresh() {
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        String oldToken = SharedPreferencesUtil.getString(AccountManager.PENDING_CONFIRM_KEY, "");
        if (oldToken.isEmpty()) return true;
        try (Response response = NetWorkUtil.post(
                "https://passport.bilibili.com/x/passport-login/web/confirm/refresh",
                new NetWorkUtil.FormData()
                        .put("csrf", NetWorkUtil.getInfoFromCookie("bili_jct", cookies))
                        .put("refresh_token", oldToken).toString(), headersFor(cookies))) {
            if (response.isSuccessful() && response.body() != null
                    && new JSONObject(response.body().string()).optInt("code", -1) == 0) {
                AccountManager.finishRefreshConfirmation(cookies, oldToken);
                return true;
            }
        } catch (Exception error) {
            Logu.e("Cookie刷新", "确认暂时失败，保留新凭据: " + error.getClass().getSimpleName());
        }
        return false;
    }

    private static boolean isCurrent(String cookies, String token) {
        return AccountManager.sameSession(cookies, SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
                && token.equals(SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""));
    }

    private static ArrayList<String> headersFor(String cookies) {
        ArrayList<String> headers = new ArrayList<>(NetWorkUtil.webHeaders);
        headers.set(1, cookies);
        return headers;
    }

    public static String base16Encode(byte[] src) {
        StringBuilder strbuf = new StringBuilder(src.length * 2);
        int i;

        for (i = 0; i < src.length; i++) {
            if (((int) src[i] & 0xff) < 0x10)
                strbuf.append("0");

            strbuf.append(Long.toString((int) src[i] & 0xff, 16));
        }

        return strbuf.toString();
    }
}
