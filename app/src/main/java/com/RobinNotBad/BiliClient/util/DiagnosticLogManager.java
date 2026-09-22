package com.RobinNotBad.BiliClient.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.RobinNotBad.BiliClient.api.AppInfoApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiagnosticLogManager {
    private static final Object FILE_LOCK = new Object();
    private static final AtomicBoolean UPLOAD_RUNNING = new AtomicBoolean(false);
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bili-diagnostics");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final String FILE_NAME = "diagnostics.ndjson";
    private static final String INSTALL_ID_KEY = "diagnostic_install_id";
    // Keep uploads small enough for slow watch networks and older TLS stacks.
    private static final int UPLOAD_BATCH_SIZE = 12;
    private static final int UPLOAD_MAX_ATTEMPTS = 3;
    private static final int MAX_LOCAL_LINES = 2000;
    private static final long MAX_LOCAL_BYTES = 2L * 1024L * 1024L;
    private static final int TRIM_LINE_MARGIN = 128;
    private static final long TRIM_BYTE_MARGIN = 256L * 1024L;
    private static final String REDACTED = "[redacted]";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)((?:cookie|set-cookie|sessdata|bili_jct|csrf(?:_token)?|token|access[_-]?key|access[_-]?token|refresh[_-]?token|authorization|password|passwd|pwd|relay[_-]?token)\\s*[=:>]\\s*)(\\\"?[^\\s,;&}\\]]+\\\"?)");
    private static final Pattern PRIVATE_TEXT_PATTERN = Pattern.compile(
            "(?i)((?:comment|message|content|keyword|search|query|title|uname|nickname)\\s*[=:>]\\s*)(\\\"?[^,;&}\\]]+\\\"?)");

    private static Context appContext;
    private static int localLineCount;

    private DiagnosticLogManager() {
    }

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        synchronized (FILE_LOCK) {
            localLineCount = readLines(getLogFile()).size();
            JSONObject details = new JSONObject();
            try {
                details.put("cold_start", true);
            } catch (JSONException ignored) {
            }
            String line = buildEventLine("app_start", details);
            if (line != null) appendLine(line);
        }
    }

    public static void record(String eventName) {
        record(eventName, null);
    }

    public static void record(String eventName, JSONObject details) {
        if (appContext == null) return;
        final String line = buildEventLine(eventName, details);
        if (line == null) return;
        LOG_EXECUTOR.execute(() -> {
            synchronized (FILE_LOCK) {
                appendLine(line);
            }
        });
    }

    public static void recordLog(String level, String tag, String message) {
        JSONObject details = new JSONObject();
        try {
            details.put("level", safeValue(level));
            details.put("tag", safeValue(tag));
            details.put("message", sanitizeTextForDiagnostics(message));
        } catch (JSONException ignored) {
        }
        record("debug_log", details);
    }

    public static void recordNetwork(String method, String endpoint, int statusCode,
                                     boolean relay, long durationMs, String error) {
        String safeEndpoint = compactEndpoint(endpoint);
        if (safeEndpoint.endsWith("/terminal/upload/diagnostics")) return;
        JSONObject details = new JSONObject();
        try {
            details.put("method", safeValue(method));
            details.put("endpoint", safeEndpoint);
            details.put("status", statusCode);
            details.put("relay", relay);
            details.put("duration_ms", Math.max(0L, durationMs));
            if (error != null && !error.isEmpty()) details.put("error", safeValue(error));
        } catch (JSONException ignored) {
        }
        record("network_request", details);
    }

    public static void recordLifecycle(Activity activity, String state) {
        if (activity == null) return;
        JSONObject details = new JSONObject();
        try {
            details.put("screen", activity.getClass().getSimpleName());
            details.put("state", state);
        } catch (JSONException ignored) {
        }
        record("activity_lifecycle", details);
    }

    public static void recordTouch(Activity activity, MotionEvent event) {
        if (activity == null || event == null || event.getActionMasked() != MotionEvent.ACTION_UP) return;
        // Keep the event itself on compatibility devices, but avoid a recursive walk of
        // the entire view tree for every tap. Screen/coordinates remain available for
        // diagnostics without adding visible UI latency on old watches and phones.
        View target = DeviceProfile.get() == DeviceProfile.Tier.COMPAT
                ? null : findTouchTarget(activity, event.getRawX(), event.getRawY());
        JSONObject details = new JSONObject();
        try {
            details.put("screen", activity.getClass().getSimpleName());
            details.put("action", "tap");
            details.put("x", Math.round(event.getRawX()));
            details.put("y", Math.round(event.getRawY()));
            if (target != null) {
                details.put("view", target.getClass().getSimpleName());
                details.put("view_id", getViewIdName(target));
            }
        } catch (JSONException ignored) {
        }
        record("ui_interaction", details);
    }

    public static void recordKey(Activity activity, int keyCode) {
        if (activity == null) return;
        JSONObject details = new JSONObject();
        try {
            details.put("screen", activity.getClass().getSimpleName());
            details.put("action", "key");
            details.put("key", KeyEvent.keyCodeToString(keyCode));
        } catch (JSONException ignored) {
        }
        record("ui_interaction", details);
    }

    public static void recordBeforeCrash(Throwable error, String threadName) {
        if (appContext == null) return;
        JSONObject details = new JSONObject();
        try {
            details.put("error", error == null ? "unknown" : error.getClass().getSimpleName());
            details.put("thread", safeValue(threadName));
            details.put("stack", safeStack(error));
        } catch (JSONException ignored) {
        }
        String line = buildEventLine("uncaught_exception", details);
        if (line == null) return;
        synchronized (FILE_LOCK) {
            appendLine(line);
        }
    }

    public static void uploadNow(UploadCallback callback) {
        if (appContext == null || !UPLOAD_RUNNING.compareAndSet(false, true)) {
            if (callback != null) callback.onFinished(false, -1, "busy");
            return;
        }
        CenterThreadPool.run(() -> {
            boolean success = true;
            int uploaded = 0;
            String failureReason = "";
            try {
                while (true) {
                    Batch batch;
                    synchronized (FILE_LOCK) {
                        batch = readBatch();
                    }
                    if (batch.events.length() == 0) break;
                    // 1.1.6: 兼容档设备单次上报上限（剩余留在本地下次再传），
                    // 降低慢速网络/低端设备的流量与 I/O 负担
                    int batchLimit = DeviceProfile.diagnosticBatchLimit();
                    if (batchLimit > 0 && uploaded + batch.events.length() > batchLimit) break;
                    AppInfoApi.DiagnosticUploadResult result = uploadBatchWithRetry(batch.events);
                    if (!result.success) {
                        success = false;
                        JSONObject details = new JSONObject();
                        try {
                            details.put("batch_size", batch.events.length());
                            details.put("failure_type", result.failureType.name());
                            details.put("server_code", result.serverCode);
                        } catch (JSONException ignored) {
                        }
                        record("diagnostic_upload_failed", details);
                        failureReason = result.failureType == AppInfoApi.DiagnosticUploadResult.FailureType.NETWORK
                                ? "network" : "server";
                        break;
                    }
                    uploaded += batch.events.length();
                    synchronized (FILE_LOCK) {
                        removeFirstLines(batch.linesConsumed);
                    }
                }
            } catch (Exception error) {
                success = false;
                failureReason = "internal";
                Log.e("diagnostics", "upload failed: " + error.getClass().getSimpleName());
                JSONObject details = new JSONObject();
                try {
                    details.put("error", error.getClass().getSimpleName());
                } catch (JSONException ignored) {
                }
                record("diagnostic_upload_exception", details);
            } finally {
                UPLOAD_RUNNING.set(false);
                if (callback != null) callback.onFinished(success, uploaded, failureReason);
            }
        });
    }

    /**
     * Watch networks often wake up before their route/TLS socket is usable.
     * Retry only the current batch; failed events stay in the local file.
     */
    private static AppInfoApi.DiagnosticUploadResult uploadBatchWithRetry(JSONArray events) {
        AppInfoApi.DiagnosticUploadResult result = null;
        for (int attempt = 1; attempt <= UPLOAD_MAX_ATTEMPTS; attempt++) {
            result = AppInfoApi.uploadDiagnostics(getInstallId(), events);
            if (result.success || result.failureType == AppInfoApi.DiagnosticUploadResult.FailureType.SERVER_REJECTED) {
                return result;
            }
            if (attempt < UPLOAD_MAX_ATTEMPTS) {
                try {
                    Thread.sleep(attempt == 1 ? 400L : 1200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return result;
                }
            }
        }
        return result;
    }

    public interface UploadCallback {
        void onFinished(boolean success, int uploadedEventCount, String failureReason);
    }

    static String sanitizeTextForDiagnostics(String value) {
        if (value == null) return "";
        String trimmed = value.replace('\n', ' ').replace('\r', ' ').trim();
        if ((trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("<"))
                && trimmed.length() > 32) {
            return "[structured payload omitted length=" + trimmed.length() + "]";
        }
        int embeddedPayload = firstStructuredPayloadIndex(trimmed);
        if (embeddedPayload > 0 && trimmed.length() - embeddedPayload > 32) {
            trimmed = trimmed.substring(0, embeddedPayload)
                    + "[structured payload omitted length=" + (trimmed.length() - embeddedPayload) + "]";
        }
        String sanitized = replaceUrls(trimmed);
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer " + REDACTED);
        sanitized = SENSITIVE_ASSIGNMENT_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED);
        sanitized = PRIVATE_TEXT_PATTERN.matcher(sanitized).replaceAll("$1" + REDACTED);
        return sanitized.length() <= 240 ? sanitized : sanitized.substring(0, 240);
    }

    private static int firstStructuredPayloadIndex(String value) {
        int result = -1;
        int objectIndex = value.indexOf('{');
        int arrayIndex = value.indexOf('[');
        int xmlIndex = value.indexOf('<');
        if (objectIndex >= 0) result = objectIndex;
        if (arrayIndex >= 0 && (result < 0 || arrayIndex < result)) result = arrayIndex;
        if (xmlIndex >= 0 && (result < 0 || xmlIndex < result)) result = xmlIndex;
        return result;
    }

    static boolean isSensitiveKeyForDiagnostics(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
        return normalized.equals("cookie")
                || normalized.equals("setcookie")
                || normalized.equals("sessdata")
                || normalized.equals("bilijct")
                || normalized.equals("csrf")
                || normalized.equals("csrftoken")
                || normalized.equals("token")
                || normalized.equals("authorization")
                || normalized.equals("password")
                || normalized.equals("passwd")
                || normalized.equals("pwd")
                || normalized.equals("accesskey")
                || normalized.equals("accesstoken")
                || normalized.equals("refreshtoken")
                || normalized.equals("relaytoken")
                || normalized.equals("mid")
                || normalized.equals("uid")
                || normalized.equals("userid")
                || normalized.equals("uname")
                || normalized.equals("nickname");
    }

    private static String buildEventLine(String eventName, JSONObject details) {
        if (eventName == null || !eventName.matches("[a-z0-9_.-]{1,48}")) return null;
        try {
            JSONObject event = new JSONObject();
            event.put("time", System.currentTimeMillis());
            event.put("name", eventName);
            event.put("details", sanitizeDetails(details));
            return event.toString();
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static JSONObject sanitizeDetails(JSONObject source) throws JSONException {
        JSONObject safe = new JSONObject();
        if (source == null) return safe;
        JSONArray names = source.names();
        if (names == null) return safe;
        for (int i = 0; i < names.length() && i < 30; i++) {
            String key = names.optString(i, "");
            if (!key.matches("[a-zA-Z0-9_.-]{1,48}")) continue;
            if (isSensitiveKeyForDiagnostics(key)) {
                safe.put(key, REDACTED);
                continue;
            }
            Object value = source.opt(key);
            if (value instanceof Boolean || value instanceof Number) safe.put(key, value);
            else if (value instanceof String) safe.put(key, safeValue((String) value));
        }
        return safe;
    }

    private static String safeValue(String value) {
        return sanitizeTextForDiagnostics(value);
    }

    private static String safeStack(Throwable error) {
        if (error == null || error.getStackTrace() == null) return "";
        StringBuilder stack = new StringBuilder();
        StackTraceElement[] elements = error.getStackTrace();
        for (int i = 0; i < elements.length && i < 8; i++) {
            if (i > 0) stack.append(" <- ");
            StackTraceElement element = elements[i];
            stack.append(element.getClassName()).append('.').append(element.getMethodName())
                    .append(':').append(element.getLineNumber());
        }
        return safeValue(stack.toString());
    }

    private static String replaceUrls(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(compactEndpoint(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String compactEndpoint(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return "[invalid endpoint]";
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            return host.toLowerCase(Locale.US) + path;
        } catch (Exception ignored) {
            if (!url.contains("?") && !url.contains("=") && url.matches("[A-Za-z0-9._/-]{1,180}")) {
                return url;
            }
            return "[invalid endpoint]";
        }
    }

    private static View findTouchTarget(Activity activity, float rawX, float rawY) {
        if (activity.getWindow() == null) return null;
        View root = activity.getWindow().getDecorView();
        View target = findDeepestVisibleView(root, rawX, rawY);
        View current = target;
        while (current != null && !current.isClickable() && !current.isLongClickable()) {
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return current != null ? current : target;
    }

    private static View findDeepestVisibleView(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isShown()) return null;
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(Math.round(rawX), Math.round(rawY))) return null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = findDeepestVisibleView(group.getChildAt(i), rawX, rawY);
                if (child != null) return child;
            }
        }
        return view;
    }

    private static String getViewIdName(View view) {
        if (view.getId() == View.NO_ID) return "none";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Exception ignored) {
            return String.valueOf(view.getId());
        }
    }

    private static int appendLine(String line) {
        File file = getLogFile();
        if (file == null) return 0;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, true), UTF_8))) {
            writer.write(line);
            writer.newLine();
            localLineCount++;
        } catch (Exception error) {
            return localLineCount;
        }

        // Avoid rewriting the whole log file for every event after the limit is reached.
        // This matters on Android 4.x devices where diagnostic events can arrive quickly.
        if (file.length() > MAX_LOCAL_BYTES + TRIM_BYTE_MARGIN
                || localLineCount > MAX_LOCAL_LINES + TRIM_LINE_MARGIN) {
            trimLogFile(file);
        }
        return localLineCount;
    }

    private static void trimLogFile(File file) {
        List<String> lines = readLines(file);
        int from = Math.max(0, lines.size() - MAX_LOCAL_LINES);
        if (from > 0) lines = new ArrayList<>(lines.subList(from, lines.size()));
        long bytes = byteSize(lines);
        while (bytes > MAX_LOCAL_BYTES && lines.size() > 1) {
            bytes -= lines.remove(0).getBytes(UTF_8).length + 1L;
        }
        writeLines(file, lines);
        localLineCount = lines.size();
    }

    private static long byteSize(List<String> lines) {
        long size = 0;
        for (String line : lines) size += line.getBytes(UTF_8).length + 1L;
        return size;
    }

    private static Batch readBatch() {
        File file = getLogFile();
        JSONArray events = new JSONArray();
        if (file == null || !file.exists()) return new Batch(events, 0);
        List<String> lines = readLines(file);
        int consumed = 0;
        for (String line : lines) {
            if (events.length() >= UPLOAD_BATCH_SIZE) break;
            consumed++;
            try {
                events.put(new JSONObject(line));
            } catch (JSONException ignored) {
            }
        }
        return new Batch(events, consumed);
    }

    private static void removeFirstLines(int count) {
        if (count <= 0) return;
        File file = getLogFile();
        if (file == null || !file.exists()) return;
        List<String> lines = readLines(file);
        if (count >= lines.size()) {
            if (!file.delete()) writeLines(file, new ArrayList<>());
            localLineCount = 0;
            return;
        }
        List<String> remaining = new ArrayList<>(lines.subList(count, lines.size()));
        writeLines(file, remaining);
        localLineCount = remaining.size();
    }

    private static List<String> readLines(File file) {
        List<String> lines = new ArrayList<>();
        if (file == null || !file.exists()) return lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line);
            }
        } catch (Exception ignored) {
        }
        return lines;
    }

    private static void writeLines(File file, List<String> lines) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, false), UTF_8))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (Exception ignored) {
        }
    }

    private static String getInstallId() {
        String id = SharedPreferencesUtil.getString(INSTALL_ID_KEY, "");
        if (!id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        SharedPreferencesUtil.putString(INSTALL_ID_KEY, id);
        return id;
    }

    private static File getLogFile() {
        return appContext == null ? null : new File(appContext.getFilesDir(), FILE_NAME);
    }

    private static final class Batch {
        final JSONArray events;
        final int linesConsumed;

        Batch(JSONArray events, int linesConsumed) {
            this.events = events;
            this.linesConsumed = linesConsumed;
        }
    }
}
