package com.RobinNotBad.BiliClient.api;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;

/** Official BiliBili TV web login. It never reads or writes the main-site session. */
public final class IntlLoginApi {
    private static final String PASSPORT_HOST = "passport.bilibili.tv";
    private static final String API_HOST = "api.bilibili.tv";
    private static final String USER_AGENT = "BiliTerminal-IntlLogin/1.0";
    private static final int MAX_RESPONSE_BYTES = 262144;

    private IntlLoginApi() {
    }

    public static final class LoginResult {
        public final long mid;
        public final String cookies;

        LoginResult(long mid, String cookies) {
            this.mid = mid;
            this.cookies = cookies;
        }
    }

    public static final class LoginException extends Exception {
        public final int code;
        public final boolean needsOfficialVerification;

        LoginException(String message, int code, boolean needsOfficialVerification) {
            super(message);
            this.code = code;
            this.needsOfficialVerification = needsOfficialVerification;
        }
    }

    private static final class ResponseData {
        final int status;
        final String body;
        final Map<String, List<String>> headers;

        ResponseData(int status, String body, Map<String, List<String>> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }
    }

    public static LoginResult login(String email, char[] password)
            throws LoginException {
        if (email == null || email.trim().isEmpty() || password == null || password.length == 0) {
            throw new LoginException("请输入国际站邮箱和密码", 0, false);
        }

        CookieManager jar = new CookieManager(null, new CookiePolicy() {
            @Override
            public boolean shouldAccept(URI uri, HttpCookie cookie) {
                return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                        && isAllowedHost(uri.getHost())
                        && isAllowedCookieDomain(uri.getHost(), cookie.getDomain());
            }
        });
        byte[] plain = null;
        byte[] form = null;
        try {
            URI keyUri = buildPassportUri("key");
            ResponseData keyResponse = request(keyUri, null, jar);
            JSONObject keyRoot = parseObject(keyResponse, "国际站公钥响应格式异常");
            checkApiStatus(keyRoot, "获取国际站公钥失败");
            JSONObject keyData = keyRoot.optJSONObject("data");
            if (keyData == null) throw protocol("国际站公钥响应缺少 data");

            String hash = keyData.optString("hash", "");
            String pem = keyData.optString("key", "");
            PublicKey publicKey = parsePublicKey(pem);
            plain = (hash + new String(password)).getBytes("UTF-8");
            int maxBytes = (((RSAPublicKey) publicKey).getModulus().bitLength() + 7) / 8 - 11;
            if (plain.length > maxBytes) throw protocol("密码长度超出国际站登录限制");

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            String encrypted = Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP);
            String body = "username=" + encode(email.trim())
                    + "&password=" + encode(encrypted)
                    + "&keep_me=true&s_locale=en_US&isTrusted=true";
            form = body.getBytes("UTF-8");

            URI loginUri = buildPassportUri("login/password");
            ResponseData loginResponse = request(loginUri, form, jar);
            JSONObject loginRoot = parseObject(loginResponse, "国际站登录响应格式异常");
            checkApiStatus(loginRoot, "国际站登录失败");

            URI userUri = buildApiUri("user");
            ResponseData userResponse = request(userUri, null, jar);
            JSONObject userRoot = parseObject(userResponse, "国际站账号验证响应格式异常");
            checkApiStatus(userRoot, "国际站账号验证失败");
            JSONObject user = userRoot.optJSONObject("data");
            if (user == null || !user.optBoolean("is_login", false)) {
                throw protocol("国际站登录未建立可验证会话");
            }
            long mid = parseMid(user.optString("mid", ""));
            String cookies = cookieHeader(jar, userUri);
            if (mid <= 0 || cookies.isEmpty()) throw protocol("国际站登录会话不完整");
            return new LoginResult(mid, cookies);
        } catch (LoginException error) {
            throw error;
        } catch (java.security.GeneralSecurityException error) {
            throw protocol("此设备暂不支持国际站安全登录");
        } catch (IOException error) {
            throw new LoginException("国际站网络连接失败，现有主站账号不变", 0, false);
        } catch (Exception error) {
            throw protocol("国际站登录响应无法处理");
        } finally {
            if (plain != null) java.util.Arrays.fill(plain, (byte) 0);
            if (form != null) java.util.Arrays.fill(form, (byte) 0);
            java.util.Arrays.fill(password, '\0');
        }
    }

    static URI buildPassportUri(String path) {
        return URI.create("https://" + PASSPORT_HOST
                + "/x/intl/passport-login/web/" + path
                + "?lang=en-US&s_locale=en&platform=web");
    }

    static URI buildApiUri(String path) {
        return URI.create("https://" + API_HOST + "/intl/gateway/web/v2/" + path
                + "?s_locale=en&platform=web");
    }

    static boolean isAllowedHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.US);
        return normalized.equals("bilibili.tv") || normalized.endsWith(".bilibili.tv");
    }

    static boolean isAllowedCookieDomain(String requestHost, String cookieDomain) {
        if (!isAllowedHost(requestHost)) return false;
        if (cookieDomain == null || cookieDomain.trim().isEmpty()) return true;
        String domain = cookieDomain.toLowerCase(Locale.US).trim();
        while (domain.startsWith(".")) domain = domain.substring(1);
        return isAllowedHost(domain)
                && (requestHost.equalsIgnoreCase(domain)
                || requestHost.toLowerCase(Locale.US).endsWith("." + domain));
    }

    private static ResponseData request(URI uri, byte[] form, CookieManager jar)
            throws IOException, LoginException {
        if (CookieHandler.getDefault() != null) {
            throw new IOException("global cookie handler is not allowed");
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("X-Bili-Tenantkey", "bstar");
            Map<String, List<String>> requestHeaders = jar.get(uri, Collections.<String, List<String>>emptyMap());
            List<String> cookieValues = requestHeaders.get("Cookie");
            if (cookieValues != null && !cookieValues.isEmpty()) {
                connection.setRequestProperty("Cookie", join(cookieValues));
            }
            if (form == null) {
                connection.setRequestMethod("GET");
            } else {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(form.length);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(form);
                } finally {
                    output.close();
                }
            }
            int status = connection.getResponseCode();
            Map<String, List<String>> headers = connection.getHeaderFields();
            if (status >= 300 && status < 400) {
                throw new LoginException("国际站登录被要求跳转，已停止提交", status, true);
            }
            if (status == 403 || status == 412 || status == 429) {
                throw new LoginException("需要在官方页面完成验证，现有主站账号不变", status, true);
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new LoginException("国际站服务返回 HTTP " + status, status, false);
            }
            if (headers != null) jar.put(uri, headers);
            String body = readBody(connection.getInputStream());
            return new ResponseData(status, body, headers);
        } finally {
            connection.disconnect();
        }
    }

    private static String readBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_RESPONSE_BYTES) throw new IOException("response too large");
                output.write(buffer, 0, count);
            }
            String body = new String(output.toByteArray(), "UTF-8").trim();
            if (body.startsWith("<")) throw new IOException("html response");
            return body;
        } finally {
            input.close();
        }
    }

    private static JSONObject parseObject(ResponseData response, String message)
            throws JSONException, LoginException {
        if (response.body == null || response.body.isEmpty()) throw protocol(message);
        return new JSONObject(response.body);
    }

    private static void checkApiStatus(JSONObject root, String prefix) throws LoginException {
        int code = root.optInt("code", Integer.MIN_VALUE);
        if (code == 0) return;
        boolean verification = code == -105 || hasVerificationHint(root);
        if (verification) {
            throw new LoginException("需要在官方页面完成验证，应用不会绕过验证码", code, true);
        }
        throw new LoginException(prefix + "（错误码 " + code + "）", code, false);
    }

    private static boolean hasVerificationHint(JSONObject root) {
        String text = root.optString("message", "").toLowerCase(Locale.US);
        return text.contains("captcha") || text.contains("verify")
                || text.contains("risk") || text.contains("验证码") || text.contains("验证");
    }

    private static PublicKey parsePublicKey(String pem) throws Exception {
        if (pem == null || !pem.contains("BEGIN PUBLIC KEY") || !pem.contains("END PUBLIC KEY")) {
            throw new IllegalArgumentException("missing public key");
        }
        String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.decode(encoded, Base64.DEFAULT);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static long parseMid(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String cookieHeader(CookieManager jar, URI uri) throws IOException {
        Map<String, List<String>> headers = jar.get(uri, Collections.<String, List<String>>emptyMap());
        List<String> values = headers.get("Cookie");
        return values == null ? "" : join(values);
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append("; ");
            result.append(value);
        }
        return result.toString();
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static LoginException protocol(String message) {
        return new LoginException(message, 0, false);
    }
}
