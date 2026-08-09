package com.RobinNotBad.BiliClient.util;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Inflater;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 被 luern0313 创建于 2019/10/13.
 * #以下代码来源于腕上哔哩的开源项目，感谢开源者做出的贡献！
 */

public class NetWorkUtil {
    private static final AtomicReference<OkHttpClient> INSTANCE = new AtomicReference<>();
    public static volatile boolean BILI_RELAY_ENABLE = true;
    public static volatile boolean BILI_RELAY_AUTO_FALLBACK = true;
    public static volatile String BILI_RELAY_BASE = "https://jp.031030.xyz/bili-relay";
    public static volatile String BILI_RELAY_TOKEN = "PUBLIC_RELAY_TOKEN_PLACEHOLDER_";
    private static volatile int BILI_RELAY_DIRECT_RETRY_COUNT = 2;
    private static volatile int BILI_RELAY_DIRECT_ATTEMPT_TIMEOUT_SEC = 5;
    private static volatile boolean BILI_RELAY_FORCED = false;
    private static final List<String> BILI_RELAY_HOSTS = Arrays.asList(
            "bilibili.com",
            "*.bilibili.com",
            "*.bilivideo.com",
            "*.bilivideo.cn",
            "upos-*.akamaized.net",
            "*.hdslb.com",
            "*.biliapi.net",
            "api.bilibili.com",
            "api.vc.bilibili.com",
            "api.live.bilibili.com",
            "passport.bilibili.com",
            "account.bilibili.com",
            "member.bilibili.com",
            "www.bilibili.com",
            "live.bilibili.com",
            "space.bilibili.com",
            "search.bilibili.com",
            "s.search.bilibili.com",
            "comment.bilibili.com",
            "i0.hdslb.com",
            "i1.hdslb.com",
            "i2.hdslb.com",
            "b23.tv"
    );

    private static URI parseUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static void configureRelay(boolean enabled, boolean autoFallback, String baseUrl,
                                      String token, int directRetryCount, int directTimeoutSeconds) {
        BILI_RELAY_ENABLE = enabled;
        BILI_RELAY_AUTO_FALLBACK = autoFallback;
        BILI_RELAY_BASE = baseUrl;
        BILI_RELAY_TOKEN = token;
        BILI_RELAY_DIRECT_RETRY_COUNT = directRetryCount;
        BILI_RELAY_DIRECT_ATTEMPT_TIMEOUT_SEC = directTimeoutSeconds;
    }

    private static boolean isRelayHostAllowed(String host) {
        String lowerHost = host.toLowerCase();
        for (String rule : BILI_RELAY_HOSTS) {
            String lowerRule = rule.toLowerCase();
            if (lowerRule.startsWith("upos-*.")) {
                String suffix = lowerRule.substring("upos-*".length());
                if (lowerHost.startsWith("upos-")
                        && lowerHost.endsWith(suffix)
                        && lowerHost.length() > suffix.length() + "upos-".length()) return true;
            } else if (lowerRule.startsWith("*.")) {
                String suffix = lowerRule.substring(1);
                if (lowerHost.endsWith(suffix) && lowerHost.length() > suffix.length()) return true;
            } else if (lowerHost.equals(lowerRule)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBilibiliCookieHost(String host) {
        String lowerHost = host.toLowerCase();
        return lowerHost.equals("bilibili.com") || lowerHost.endsWith(".bilibili.com");
    }

    private static boolean isBiliRelayTarget(String url) {
        if (!BILI_RELAY_ENABLE) return false;
        URI uri = parseUri(url);
        if (uri == null || uri.getHost() == null || uri.getScheme() == null) return false;
        String scheme = uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        return isRelayHostAllowed(uri.getHost());
    }

    public static boolean shouldAttachBiliCredentials(String url) {
        return NetworkCredentialPolicy.shouldAttach(
                url, BILI_RELAY_ENABLE, BILI_RELAY_BASE, BILI_RELAY_HOSTS
        );
    }

    private static boolean shouldRouteUrlForRelay(String url) {
        return BILI_RELAY_ENABLE && BILI_RELAY_FORCED && isBiliRelayTarget(url);
    }

    public static boolean isRelayForced() {
        return BILI_RELAY_FORCED;
    }

    public static boolean forceRelayFallback(String reason) {
        boolean changed = !BILI_RELAY_FORCED;
        BILI_RELAY_FORCED = true;
        if (changed) {
            Logu.e("bili-relay", "switch to relay: " + reason);
            JSONObject details = new JSONObject();
            try {
                details.put("reason", relayFailureCategory(reason));
            } catch (JSONException ignored) {
            }
            DiagnosticLogManager.record("relay_fallback", details);
        }
        return changed;
    }

    private static String relayFailureCategory(String reason) {
        String text = reason == null ? "" : reason.toLowerCase();
        if (text.contains("ssl") || text.contains("handshake")) return "tls";
        if (text.contains("timeout")) return "timeout";
        if (text.contains("reset")) return "connection_reset";
        if (text.contains("unknownhost") || text.contains("dns")) return "dns";
        return "network";
    }

    public static String routeUrlForRelay(String url) {
        if (url.startsWith(BILI_RELAY_BASE + "/")) return url;
        if (!shouldRouteUrlForRelay(url)) return url;
        return relayUrl(url);
    }

    public static String relayUrl(String url) {
        if (!isBiliRelayTarget(url)) return url;
        URI uri = parseUri(url);
        if (uri == null) return url;
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        String query = uri.getRawQuery();
        return BILI_RELAY_BASE + "/" + uri.getHost().toLowerCase() + path + (query == null ? "" : "?" + query);
    }

    public static void addRelayHeaders(Request.Builder requestBuilder, String originalUrl) {
        addRelayHeaders(requestBuilder, originalUrl, originalUrl.startsWith(BILI_RELAY_BASE + "/"));
    }

    private static void addRelayHeaders(Request.Builder requestBuilder, String originalUrl, boolean useRelay) {
        if (BILI_RELAY_ENABLE && originalUrl.startsWith(BILI_RELAY_BASE + "/")) {
            requestBuilder.header("X-Relay-Token", BILI_RELAY_TOKEN);
            return;
        }
        if (!useRelay || !isBiliRelayTarget(originalUrl)) {
            requestBuilder.removeHeader("X-Relay-Token");
            requestBuilder.removeHeader("X-Relay-Target-Scheme");
            requestBuilder.removeHeader("X-Relay-Target-Host");
            return;
        }
        URI uri = parseUri(originalUrl);
        if (uri == null || uri.getHost() == null || uri.getScheme() == null) return;
        requestBuilder.header("X-Relay-Token", BILI_RELAY_TOKEN);
        requestBuilder.header("X-Relay-Target-Scheme", uri.getScheme().toLowerCase());
        requestBuilder.header("X-Relay-Target-Host", uri.getHost().toLowerCase());
    }

    public static class Inet4Selector implements Dns {
        @NonNull
        @Override
        public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
            List<InetAddress> hosts = Dns.SYSTEM.lookup(hostname);
            List<InetAddress> inet4Hosts = new ArrayList<>();
            for (InetAddress host : hosts) {
                if (host.getAddress().length == 4) inet4Hosts.add(host);
            }
            return inet4Hosts;    //筛选IPV4地址，IPV6请求有异常
        }
    }

    public static OkHttpClient getOkHttpInstance() {
        while (INSTANCE.get() == null) {
            INSTANCE.compareAndSet(null, setOkHttpSsl(new OkHttpClient.Builder())
                    .followRedirects(false)
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        Response response = chain.proceed(request);
                        RedirectHandler handler;
                        String location = response.header("Location");
                        boolean isSslRedirect = false;
                        try {
                            URI redirectUri = location == null ? null : new URI(location);
                            isSslRedirect = redirectUri != null && !request.isHttps()
                                    && "https".equalsIgnoreCase(redirectUri.getScheme())
                                    && request.url().host().equalsIgnoreCase(redirectUri.getHost());
                        } catch (Exception ignored) {
                        }

                        if (response.isRedirect() && location != null) {
                            HttpUrl resolvedRedirect = request.url().resolve(location);
                            if (resolvedRedirect == null) return response;
                            String redirectUrl = resolvedRedirect.toString();
                            String logicalHost = request.header("X-Relay-Target-Host");
                            if (logicalHost == null) logicalHost = request.url().host();
                            if (logicalHost.equals("b23.tv") && !isSslRedirect && (handler = request.tag(RedirectHandler.class)) != null) {
                                handler.handleRedirect(redirectUrl);
                            } else {
                                Request.Builder newRequestBuilder = request.newBuilder()
                                        .url(routeUrlForRelay(redirectUrl));
                                if (!shouldAttachBiliCredentials(redirectUrl)) {
                                    newRequestBuilder.removeHeader("Cookie");
                                    newRequestBuilder.removeHeader("Authorization");
                                }
                                addRelayHeaders(newRequestBuilder, redirectUrl, shouldRouteUrlForRelay(redirectUrl));
                                Request newRequest = newRequestBuilder.build();
                                response.close();
                                return chain.proceed(newRequest);
                            }
                        }
                        return response;
                    })
                    .addInterceptor(new CookieSaveInterceptor())
                    .dns(new Inet4Selector())
                    .pingInterval(8, TimeUnit.SECONDS)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(16, TimeUnit.SECONDS).build());
        }
        return INSTANCE.get();
    }

    public synchronized static OkHttpClient.Builder setOkHttpSsl(OkHttpClient.Builder okhttpBuilder) {
        if (Build.VERSION.SDK_INT > 22) return okhttpBuilder;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
            );
            trustManagerFactory.init((KeyStore) null);
            X509TrustManager systemTrustManager = null;
            for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager) {
                    systemTrustManager = (X509TrustManager) trustManager;
                    break;
                }
            }
            if (systemTrustManager == null) {
                throw new IllegalStateException("No system X509TrustManager");
            }
            final SSLSocketFactory sslSocketFactory = new SSLSocketFactoryCompat(systemTrustManager);
            okhttpBuilder.sslSocketFactory(sslSocketFactory, systemTrustManager);
        } catch (Exception e) {
            Logu.e("network", "TLS compatibility setup failed: " + e);
        }
        return okhttpBuilder;
    }

    public static JSONObject getJson(String url) throws IOException, JSONException {
        return getJson(url, webHeaders);
    }

    public static JSONObject getJson(String url, ArrayList<String> headers) throws IOException, JSONException {
        try (Response response = get(url, headers)) {
            String bodyText = readJsonBodyText(url, response);
            if (shouldRetryJsonByRelay(url, response, bodyText)) {
                forceRelayFallback("direct GET returned html: " + compactUrl(url));
                try (Response relayResponse = executeGet(url, headers, null, true, getOkHttpInstance())) {
                    return parseJsonText(url, relayResponse, readJsonBodyText(url, relayResponse));
                }
            }
            return parseJsonText(url, response, bodyText);
        }
    }

    private static String readJsonBodyText(String url, Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) throw new IOException("接口返回为空：" + compactUrl(url));
        return body.string();
    }

    private static JSONObject parseJsonText(String url, Response response, String bodyText) throws IOException, JSONException {
        String trimmed = bodyText == null ? "" : bodyText.trim();
        if (trimmed.isEmpty()) throw new IOException("接口返回为空：" + compactUrl(url));
        if (isHtmlResponse(response, trimmed)) {
            throw new IOException("接口返回网页而不是 JSON，可能被登录态、风控或运营商网络拦截：" + compactUrl(url));
        }
        return new JSONObject(trimmed);
    }

    private static boolean shouldRetryJsonByRelay(String url, Response response, String bodyText) {
        return BILI_RELAY_ENABLE
                && BILI_RELAY_AUTO_FALLBACK
                && !BILI_RELAY_FORCED
                && isBiliRelayTarget(url)
                && isHtmlResponse(response, bodyText == null ? "" : bodyText.trim());
    }

    private static boolean isHtmlResponse(Response response, String trimmedBody) {
        String contentType = response.header("Content-Type");
        if (contentType != null && contentType.toLowerCase().contains("text/html")) return true;
        String lowerBody = trimmedBody.toLowerCase();
        return lowerBody.startsWith("<!doctype") || lowerBody.startsWith("<html") || lowerBody.startsWith("<");
    }

    private static String compactUrl(String url) {
        URI uri = parseUri(url);
        if (uri == null || uri.getHost() == null) return url;
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        return uri.getHost().toLowerCase() + path;
    }

    public static Response get(String url) throws IOException {
        return get(url, webHeaders);
    }

    public static Response get(String url, ArrayList<String> headers) throws IOException {
        return get(url, headers, null);
    }

    public static Response get(String url, ArrayList<String> headers, RedirectHandler redirectHandler) throws IOException {
        Logu.d("get-url", url);
        if (shouldTryDirectBeforeRelay(url)) {
            return getWithAutoRelay(url, headers, redirectHandler);
        }
        return executeGet(url, headers, redirectHandler, shouldRouteUrlForRelay(url), getOkHttpInstance());
    }

    public static Response getDownload(String url) throws IOException {
        return getDownload(url, webHeaders);
    }

    public static Response getDownload(String url, ArrayList<String> headers) throws IOException {
        Logu.d("download-url", url);
        if (shouldTryDirectBeforeRelay(url)) {
            try {
                return executeGet(url, headers, null, false, getOkHttpInstance());
            } catch (IOException e) {
                forceRelayFallback("download direct failed: " + e);
                return executeGet(url, headers, null, true, getOkHttpInstance());
            }
        }
        return executeGet(url, headers, null, shouldRouteUrlForRelay(url), getOkHttpInstance());
    }

    private static boolean shouldTryDirectBeforeRelay(String url) {
        return BILI_RELAY_ENABLE && BILI_RELAY_AUTO_FALLBACK && !BILI_RELAY_FORCED && isBiliRelayTarget(url);
    }

    private static OkHttpClient getDirectProbeClient() {
        return getOkHttpInstance().newBuilder()
                .connectTimeout(BILI_RELAY_DIRECT_ATTEMPT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(BILI_RELAY_DIRECT_ATTEMPT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(BILI_RELAY_DIRECT_ATTEMPT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .callTimeout(BILI_RELAY_DIRECT_ATTEMPT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
    }

    public static boolean prepareBiliRoute() throws IOException, JSONException {
        if (!BILI_RELAY_ENABLE || BILI_RELAY_FORCED) return BILI_RELAY_FORCED;
        getJson("https://api.bilibili.com/x/web-interface/nav", webHeaders);
        return BILI_RELAY_FORCED;
    }

    private static Response getWithAutoRelay(String url, ArrayList<String> headers, RedirectHandler redirectHandler) throws IOException {
        IOException lastException = null;
        OkHttpClient directProbeClient = getDirectProbeClient();
        for (int i = 1; i <= BILI_RELAY_DIRECT_RETRY_COUNT; i++) {
            try {
                return executeGet(url, headers, redirectHandler, false, directProbeClient);
            } catch (IOException e) {
                lastException = e;
                Logu.e("bili-relay", "direct GET failed " + i + "/" + BILI_RELAY_DIRECT_RETRY_COUNT + ": " + e);
            }
        }
        forceRelayFallback(lastException == null ? "direct GET failed" : lastException.toString());
        return executeGet(url, headers, redirectHandler, true, getOkHttpInstance());
    }

    private static Response executeGet(String url, List<String> headers, RedirectHandler redirectHandler, boolean useRelay, OkHttpClient client) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(useRelay ? relayUrl(url) : url).get();
        addRelayHeaders(requestBuilder, url, useRelay);
        addSafeHeaders(requestBuilder, headers, url);
        if (redirectHandler != null) requestBuilder.tag(RedirectHandler.class, redirectHandler);
        Request request = requestBuilder.build();
        return client.newCall(request).execute();
    }

    public static Response post(String url, String data, List<String> headers, String contentType) throws IOException {
        Logu.d("post-url", url);
        Logu.d("post-data", data);
        if (shouldTryDirectBeforeRelay(url)) {
            return postWithAutoRelay(url, data, headers, contentType);
        }
        return executePost(url, data, headers, contentType, shouldRouteUrlForRelay(url), getOkHttpInstance());
    }

    private static Response postWithAutoRelay(String url, String data, List<String> headers, String contentType) throws IOException {
        IOException lastException = null;
        OkHttpClient directProbeClient = getDirectProbeClient();
        for (int i = 1; i <= BILI_RELAY_DIRECT_RETRY_COUNT; i++) {
            try {
                return executePost(url, data, headers, contentType, false, directProbeClient);
            } catch (IOException e) {
                lastException = e;
                Logu.e("bili-relay", "direct POST failed " + i + "/" + BILI_RELAY_DIRECT_RETRY_COUNT + ": " + e);
            }
        }
        forceRelayFallback(lastException == null ? "direct POST failed" : lastException.toString());
        return executePost(url, data, headers, contentType, true, getOkHttpInstance());
    }

    private static Response executePost(String url, String data, List<String> headers, String contentType, boolean useRelay, OkHttpClient client) throws IOException {
        RequestBody body = RequestBody.create(MediaType.parse(contentType + "; charset=utf-8"), data);
        Request.Builder requestBuilder = new Request.Builder().url(useRelay ? relayUrl(url) : url).post(body);
        addRelayHeaders(requestBuilder, url, useRelay);
        for (int i = 0; i < headers.size(); i += 2) {
            String key = headers.get(i);
            String val = headers.get(i + 1);
            if ((key.equalsIgnoreCase("Cookie") || key.equalsIgnoreCase("Authorization"))
                    && !shouldAttachBiliCredentials(url)) continue;
            if (key.equalsIgnoreCase("Content-Type")) val = contentType;
            requestBuilder.addHeader(key, val);
        }
        Request request = requestBuilder.build();
        return client.newCall(request).execute();
    }

    public static Response post(String url, String data, List<String> headers) throws IOException {
        return post(url, data, headers, "application/x-www-form-urlencoded");
    }

    public static Response postJson(String url, String data, List<String> headers) throws IOException {
        return post(url, data, headers, "application/json");
    }

    public static Response postJson(String url, String data) throws IOException {
        return post(url, data, webHeaders, "application/json");
    }

    public static Response post(String url, String data) throws IOException {
        return post(url, data, webHeaders);
    }

    private static void addSafeHeaders(Request.Builder requestBuilder, List<String> headers, String targetUrl) {
        boolean allowCredentials = shouldAttachBiliCredentials(targetUrl);
        for (int i = 0; i + 1 < headers.size(); i += 2) {
            String key = headers.get(i);
            if ((key.equalsIgnoreCase("Cookie") || key.equalsIgnoreCase("Authorization"))
                    && !allowCredentials) continue;
            requestBuilder.addHeader(key, headers.get(i + 1));
        }
    }


    public static byte[] readStream(InputStream inStream) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        outStream.close();
        inStream.close();
        return outStream.toByteArray();
    }

    public static byte[] uncompress(byte[] inputByte) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(inputByte.length);
        try {
            Inflater inflater = new Inflater(true);
            inflater.setInput(inputByte);
            byte[] buffer = new byte[4 * 1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] output = outputStream.toByteArray();
        outputStream.close();
        return output;
    }

    public static String getInfoFromCookie(String name, String cookie) {
        return new Cookies(cookie).getOrDefault(name, "");
    }

    private static synchronized void saveCookiesFromResponse(Response response) {
        String logicalHost = response.request().header("X-Relay-Target-Host");
        if (logicalHost == null || logicalHost.trim().isEmpty()) {
            logicalHost = response.request().url().host();
        }
        logicalHost = logicalHost.toLowerCase();
        if (!isBilibiliCookieHost(logicalHost)) return;

        List<String> newCookies = response.headers("Set-Cookie");
        if (newCookies.isEmpty()) return;

        String oldCookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        String mergedCookies = CookieMergeUtil.merge(oldCookies, newCookies,
                "passport.bilibili.com".equals(logicalHost));
        if (mergedCookies.equals(oldCookies)) return;

        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, mergedCookies);
        refreshHeaders();
    }

    /**
     * 存储单个Cookie
     *
     * @param key 键
     * @param val 值
     */
    public static void putCookie(String key, String val) {
        synchronized (NetWorkUtil.class) {
            Cookies cookies = new Cookies(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
            cookies.set(key, val);
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies.toString());
            refreshHeaders();
        }
    }

    /**
     * 存储Cookies（覆盖写入）
     *
     * @param cookies cookies
     */
    public static void setCookies(Cookies cookies) {
        synchronized (NetWorkUtil.class) {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies.toString());
            refreshHeaders();
        }
    }

    /**
     * 获取存储的Cookies
     *
     * @return 存储的Cookies
     */
    public static Cookies getCookies() {
        synchronized (NetWorkUtil.class) {
            return new Cookies(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        }
    }

    public static final String USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36";
    public static final ArrayList<String> webHeaders = new ArrayList<>() {{
        add("Cookie");
        add(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));

        add("Origin");
        add("https://www.bilibili.com");

        add("Referer");
        add("https://www.bilibili.com/");

        add("User-Agent");
        add(USER_AGENT_WEB);

        add("Sec-Ch-Ua");
        add("\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");

        add("Sec-Ch-Ua-Platform");
        add("\"Windows\"");

        add("Sec-Ch-Ua-Mobile");
        add("?0");
    }};

    public static void refreshHeaders() {
        webHeaders.set(1, SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
    }

    public static class FormData {
        private final Map<String, String> data;
        private boolean isUrlParam;

        public FormData() {
            data = new HashMap<>();
        }

        public FormData remove(String key) {
            data.remove(key);
            return this;
        }

        public FormData put(String key, Object value) {
            data.put(key, String.valueOf(value));
            return this;
        }

        public FormData setUrlParam(boolean isUrlParam) {
            this.isUrlParam = isUrlParam;
            return this;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            if (isUrlParam) sb.append("?");

            try {
                for (String key : data.keySet()) {
                    if (sb.length() > (isUrlParam ? 1 : 0)) {
                        sb.append("&");
                    }
                    sb.append(URLEncoder.encode(key, "UTF-8"));
                    sb.append("=");
                    sb.append(URLEncoder.encode(data.get(key), "UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            return sb.toString();
        }
    }

    public interface RedirectHandler {
        void handleRedirect(String location);
    }

    private static class CookieSaveInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());
            saveCookiesFromResponse(response);
            return response;
        }
    }

}
