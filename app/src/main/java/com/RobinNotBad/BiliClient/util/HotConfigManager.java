package com.RobinNotBad.BiliClient.util;

import android.util.Base64;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class HotConfigManager {
    private static final String[] CONFIG_URLS = new String[]{
            "https://jp.031030.xyz/terminal/config/get",
            "http://121.4.26.60:2000/terminal/config/get"
    };
    private static final String[] UPDATE_MANIFEST_URLS = new String[]{
            "https://jp.031030.xyz/terminal/update/manifest",
            "http://121.4.26.60:2000/terminal/update/manifest"
    };
    private static final List<String> TRUSTED_HOSTS = Arrays.asList("jp.031030.xyz", "121.4.26.60");
    private static final String CACHE_KEY = "terminal_hot_config_envelope";
    private static final String LAST_CHECK_KEY = "terminal_hot_config_last_check";
    private static final long REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final long CLOCK_SKEW_SECONDS = 300L;

    private static final String PUBLIC_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsiadEwgclKVXxJAjzUOC" +
            "D64/2ua+xmL1GXe3OkxupiGi9kl0vDj6MCiE4F0I/aMsFsPIgPPXtGv2HwZV2iqE" +
            "WgrvDIG2nNYYhvDmF1yv1b/uVOZUWqk061Dcf4WEXUai3x6NX3cq2OfOLXPxSrTA" +
            "oL5soTMstAO9pPtfFxvJmeCXQTcFKQMVNpuSoVRicVyT5e2ll+62TnTthrhZfCkm" +
            "ceXjFuGB9v3yKxIxUFHTt0TA4g44LPG1XefY546As6GYAH4N01+dWXQetr3F7O4o" +
            "IqzOD+W0wuJv+/dK93zrrXoBi6DS/mcHmlqryfiCKfZ0w/BSlsTQYKrUl+9Kpcoa" +
            "/QIDAQAB";

    private static volatile boolean compatEnabled = true;
    private static volatile String compatBaseUrl = "https://jp.031030.xyz/compat/v1/bili";
    private static volatile String compatToken = NetWorkUtil.BILI_RELAY_TOKEN;
    private static volatile String updateManifestUrl = "https://jp.031030.xyz/terminal/update/manifest";

    private HotConfigManager() {
    }

    public static void initialize() {
        applyCached();
        if (System.currentTimeMillis() - SharedPreferencesUtil.getLong(LAST_CHECK_KEY, 0L) >= REFRESH_INTERVAL_MS) {
            CenterThreadPool.run(() -> {
                try {
                    refresh();
                } catch (Throwable error) {
                    Logu.e("hot-config", "refresh failed: " + error);
                }
            });
        }
    }

    public static synchronized boolean refresh() throws Exception {
        ArrayList<String> headers = new ArrayList<>();
        headers.add("User-Agent");
        headers.add(NetWorkUtil.USER_AGENT_WEB);
        IOException lastIo = null;
        JSONException lastJson = null;
        for (String configUrl : CONFIG_URLS) {
            try {
                JSONObject response = NetWorkUtil.getJson(configUrl, headers);
                if (response.optInt("code", -1) != 0) {
                    throw new IOException("hot config server rejected request");
                }
                JSONObject envelope = response.getJSONObject("data");
                JSONObject payload = verifyEnvelope(envelope);
                applyPayload(payload);
                SharedPreferencesUtil.putString(CACHE_KEY, envelope.toString());
                SharedPreferencesUtil.putLong(LAST_CHECK_KEY, System.currentTimeMillis());
                return true;
            } catch (IOException e) {
                lastIo = e;
                Logu.e("hot-config", "refresh endpoint failed: " + configUrl + " " + e);
            } catch (JSONException e) {
                lastJson = e;
                Logu.e("hot-config", "refresh endpoint json failed: " + configUrl + " " + e);
            }
        }
        if (lastJson != null) throw lastJson;
        if (lastIo != null) throw lastIo;
        throw new IOException("hot config unavailable");
    }

    private static void applyCached() {
        String cached = SharedPreferencesUtil.getString(CACHE_KEY, "");
        if (cached.isEmpty()) return;
        try {
            applyPayload(verifyEnvelope(new JSONObject(cached)));
        } catch (Throwable error) {
            SharedPreferencesUtil.removeValue(CACHE_KEY);
            Logu.e("hot-config", "cached config rejected: " + error);
        }
    }

    public static JSONObject verifyEnvelope(JSONObject envelope) throws Exception {
        if (!"SHA256withRSA".equals(envelope.optString("algorithm"))) {
            throw new SecurityException("unsupported hot config signature");
        }
        String payloadBase64 = envelope.getString("payload");
        byte[] publicKeyBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT);
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(payloadBase64.getBytes(Charset.forName("US-ASCII")));
        if (!verifier.verify(Base64.decode(envelope.getString("signature"), Base64.DEFAULT))) {
            throw new SecurityException("invalid hot config signature");
        }
        return new JSONObject(new String(Base64.decode(payloadBase64, Base64.DEFAULT), Charset.forName("UTF-8")));
    }

    private static void applyPayload(JSONObject payload) throws JSONException {
        long now = System.currentTimeMillis() / 1000L;
        if (payload.optInt("schema", 0) != 1) throw new SecurityException("unsupported hot config schema");
        if (payload.optLong("issued_at", now) > now + CLOCK_SKEW_SECONDS) {
            throw new SecurityException("hot config is from the future");
        }
        if (payload.optLong("expires_at", 0L) < now - CLOCK_SKEW_SECONDS) {
            throw new SecurityException("hot config expired");
        }
        if (payload.optInt("min_client_version_code", 0) > BuildConfig.VERSION_CODE) {
            throw new SecurityException("hot config requires a newer client");
        }

        JSONObject relay = payload.optJSONObject("relay");
        if (relay != null) {
            String relayBase = trustedUrl(relay.optString("base_url", NetWorkUtil.BILI_RELAY_BASE));
            NetWorkUtil.configureRelay(
                    relay.optBoolean("enabled", true),
                    relay.optBoolean("auto_fallback", true),
                    relayBase,
                    relay.optString("token", NetWorkUtil.BILI_RELAY_TOKEN),
                    clamp(relay.optInt("direct_retry_count", 2), 1, 3),
                    clamp(relay.optInt("direct_timeout_seconds", 5), 3, 15)
            );
        }

        JSONObject compat = payload.optJSONObject("compat");
        if (compat != null) {
            compatEnabled = compat.optBoolean("enabled", true);
            compatBaseUrl = trustedUrl(compat.optString("base_url", compatBaseUrl));
            compatToken = compat.optString("token", compatToken);
        }

        String manifestUrl = payload.optString("update_manifest_url", updateManifestUrl);
        updateManifestUrl = trustedUrl(manifestUrl);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trustedUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean trustedBackupHttp = "http".equalsIgnoreCase(scheme) && "121.4.26.60".equalsIgnoreCase(host);
            boolean trustedPrimaryHttps = "https".equalsIgnoreCase(scheme);
            if ((!trustedPrimaryHttps && !trustedBackupHttp)
                    || host == null
                    || !TRUSTED_HOSTS.contains(host.toLowerCase())) {
                throw new SecurityException("untrusted hot config URL");
            }
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        } catch (Exception error) {
            throw new SecurityException("invalid hot config URL", error);
        }
    }

    public static boolean isCompatEnabled() {
        return compatEnabled;
    }

    public static String getCompatUrl(String routeId, String originalUrl) {
        if (!compatEnabled || !routeId.matches("[A-Za-z0-9_-]{1,64}")) return "";
        try {
            URI original = new URI(originalUrl);
            String query = original.getRawQuery();
            return compatBaseUrl + "/" + routeId + (query == null || query.isEmpty() ? "" : "?" + query);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static ArrayList<String> withCompatHeader(ArrayList<String> source) {
        ArrayList<String> headers = new ArrayList<>(source);
        headers.add("X-Compat-Token");
        headers.add(compatToken);
        return headers;
    }

    public static String getUpdateManifestUrl() {
        return updateManifestUrl;
    }

    private static JSONObject getVerifiedManifest(ArrayList<String> headers) throws Exception {
        ArrayList<String> urls = new ArrayList<>();
        urls.add(updateManifestUrl);
        for (String url : UPDATE_MANIFEST_URLS) {
            if (!urls.contains(url)) urls.add(url);
        }

        Exception lastError = null;
        for (String url : urls) {
            try {
                JSONObject response = NetWorkUtil.getJson(url, headers);
                if (response.optInt("code", -1) != 0) {
                    throw new IOException("update manifest server rejected request");
                }
                return verifyEnvelope(response.getJSONObject("data"));
            } catch (Exception error) {
                lastError = error;
                Logu.e("terminal-update", "manifest endpoint failed: " + url + " " + error);
            }
        }
        if (lastError != null) throw lastError;
        throw new IOException("update manifest unavailable");
    }

    public static UpdatePlan getVerifiedUpdatePlan(int targetVersionCode) throws Exception {
        ArrayList<String> headers = new ArrayList<>();
        headers.add("User-Agent");
        headers.add(NetWorkUtil.USER_AGENT_WEB);
        JSONObject manifest = getVerifiedManifest(headers);
        if (manifest.optInt("schema", 0) != 1) {
            throw new SecurityException("unsupported update manifest schema");
        }
        if (manifest.optInt("version_code", -1) != targetVersionCode) {
            throw new SecurityException("update manifest version mismatch");
        }
        JSONObject full = manifest.getJSONObject("full");
        String fullUrl = trustedUrl(full.getString("url"));
        String fullSha256 = normalizeSha256(full.optString("sha256", ""));
        long fullSize = full.optLong("size", -1L);
        if (fullSha256.isEmpty()) {
            throw new SecurityException("update manifest has no valid SHA-256");
        }
        UpdatePlan fullPlan = UpdatePlan.full(fullUrl, fullSha256, fullSize);

        JSONArray patches = manifest.optJSONArray("patches");
        if (patches == null || patches.length() == 0) return fullPlan;
        File installedApk = new File(BiliTerminal.context.getApplicationInfo().sourceDir);
        String installedSha256 = sha256(installedApk);
        UpdatePlan best = fullPlan;
        for (int i = 0; i < patches.length(); i++) {
            JSONObject patch = patches.optJSONObject(i);
            if (patch == null
                    || patch.optInt("from_version_code", -1) != BuildConfig.VERSION_CODE
                    || !"bsdiff".equalsIgnoreCase(patch.optString("algorithm"))
                    || !installedSha256.equalsIgnoreCase(normalizeSha256(patch.optString("from_sha256", "")))) {
                continue;
            }
            String patchSha256 = normalizeSha256(patch.optString("sha256", ""));
            long patchSize = patch.optLong("size", -1L);
            if (patchSha256.isEmpty() || patchSize <= 0) continue;
            if (best.differential && patchSize >= best.downloadSize) continue;
            if (!best.differential && fullSize > 0 && patchSize >= fullSize) continue;
            best = UpdatePlan.patch(
                    trustedUrl(patch.getString("url")),
                    patchSha256,
                    patchSize,
                    fullUrl,
                    fullSha256,
                    fullSize
            );
        }
        return best;
    }

    public static String getVerifiedFullUpdateUrl(int targetVersionCode) throws Exception {
        return getVerifiedUpdatePlan(targetVersionCode).fullUrl;
    }

    public static boolean verifyDownloadedUpdate(File file) {
        String expectedHash = SharedPreferencesUtil.getString("terminal_update_expected_sha256", "");
        long expectedSize = SharedPreferencesUtil.getLong("terminal_update_expected_size", -1L);
        if (expectedHash.isEmpty()) return false;
        return verifyFile(file, expectedHash, expectedSize);
    }

    public static boolean verifyFile(File file, String expectedHash, long expectedSize) {
        if (file == null || !file.isFile()) return false;
        if (expectedSize >= 0 && file.length() != expectedSize) return false;
        if (expectedHash == null || expectedHash.isEmpty()) return false;
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder actual = new StringBuilder(64);
            for (byte value : digest.digest()) {
                actual.append(String.format("%02x", value & 0xff));
            }
            return expectedHash.equalsIgnoreCase(actual.toString());
        } catch (Exception error) {
            Logu.e("terminal-update", "hash verify failed: " + error);
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean verifyApkIdentity(File apkFile) {
        if (apkFile == null || !apkFile.isFile()) return false;
        try {
            PackageManager packageManager = BiliTerminal.context.getPackageManager();
            PackageInfo candidate = packageManager.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(), PackageManager.GET_SIGNATURES
            );
            PackageInfo installed = packageManager.getPackageInfo(
                    BiliTerminal.context.getPackageName(), PackageManager.GET_SIGNATURES
            );
            return candidate != null
                    && installed.packageName.equals(candidate.packageName)
                    && candidate.signatures != null
                    && installed.signatures != null
                    && candidate.signatures.length == installed.signatures.length
                    && Arrays.equals(candidate.signatures, installed.signatures);
        } catch (Exception error) {
            Logu.e("terminal-update", "APK identity verify failed: " + error);
            return false;
        }
    }

    private static String sha256(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        }
    }

    private static String normalizeSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}") ? value.toLowerCase() : "";
    }

    public static final class UpdatePlan {
        public final boolean differential;
        public final String downloadUrl;
        public final String downloadSha256;
        public final long downloadSize;
        public final String fullUrl;
        public final String fullSha256;
        public final long fullSize;

        private UpdatePlan(boolean differential, String downloadUrl, String downloadSha256,
                           long downloadSize, String fullUrl, String fullSha256, long fullSize) {
            this.differential = differential;
            this.downloadUrl = downloadUrl;
            this.downloadSha256 = downloadSha256;
            this.downloadSize = downloadSize;
            this.fullUrl = fullUrl;
            this.fullSha256 = fullSha256;
            this.fullSize = fullSize;
        }

        public static UpdatePlan full(String url, String sha256, long size) {
            return new UpdatePlan(false, url, sha256, size, url, sha256, size);
        }

        public static UpdatePlan patch(String patchUrl, String patchSha256, long patchSize,
                                       String fullUrl, String fullSha256, long fullSize) {
            return new UpdatePlan(true, patchUrl, patchSha256, patchSize,
                    fullUrl, fullSha256, fullSize);
        }

        public UpdatePlan asFull() {
            return full(fullUrl, fullSha256, fullSize);
        }
    }
}
