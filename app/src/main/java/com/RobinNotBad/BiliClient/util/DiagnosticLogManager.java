package com.RobinNotBad.BiliClient.util;

import android.content.Context;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DiagnosticLogManager {
    private static final Object FILE_LOCK = new Object();
    private static final AtomicBoolean UPLOAD_RUNNING = new AtomicBoolean(false);
    private static final String FILE_NAME = "diagnostics.ndjson";
    private static final String INSTALL_ID_KEY = "diagnostic_install_id";
    private static final int UPLOAD_BATCH_SIZE = 50;
    private static final int MAX_LOCAL_LINES = 200;
    private static final long MAX_LOCAL_BYTES = 256L * 1024L;

    private static Context appContext;

    private DiagnosticLogManager() {
    }

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        JSONObject details = new JSONObject();
        try {
            details.put("cold_start", true);
        } catch (JSONException ignored) {
        }
        String line = buildEventLine("app_start", details);
        if (line != null) {
            synchronized (FILE_LOCK) {
                appendLine(line);
            }
        }
    }

    public static void record(String eventName) {
        record(eventName, null);
    }

    public static void record(String eventName, JSONObject details) {
        if (appContext == null) return;
        final String line = buildEventLine(eventName, details);
        if (line == null) return;
        CenterThreadPool.run(() -> {
            synchronized (FILE_LOCK) {
                appendLine(line);
            }
        });
    }

    public static void recordBeforeCrash(Throwable error, String threadName) {
        if (appContext == null) return;
        JSONObject details = new JSONObject();
        try {
            details.put("error", error == null ? "unknown" : error.getClass().getSimpleName());
            details.put("thread", safeValue(threadName));
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
            if (callback != null) callback.onFinished(false, -1);
            return;
        }
        CenterThreadPool.run(() -> {
            boolean success = true;
            int uploaded = 0;
            try {
                while (true) {
                    Batch batch;
                    synchronized (FILE_LOCK) {
                        batch = readBatch();
                    }
                    if (batch.events.length() == 0) break;
                    if (!AppInfoApi.uploadDiagnostics(getInstallId(), batch.events)) {
                        success = false;
                        break;
                    }
                    uploaded += batch.events.length();
                    synchronized (FILE_LOCK) {
                        removeFirstLines(batch.linesConsumed);
                    }
                }
            } catch (Exception error) {
                success = false;
                Logu.e("diagnostics", "upload failed: " + error.getClass().getSimpleName());
            } finally {
                UPLOAD_RUNNING.set(false);
                if (callback != null) callback.onFinished(success, uploaded);
            }
        });
    }

    public interface UploadCallback {
        void onFinished(boolean success, int uploadedEventCount);
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
        for (int i = 0; i < names.length() && i < 20; i++) {
            String key = names.optString(i, "");
            if (!key.matches("[a-zA-Z0-9_.-]{1,48}")) continue;
            Object value = source.opt(key);
            if (value instanceof Boolean || value instanceof Number) safe.put(key, value);
            else if (value instanceof String) safe.put(key, safeValue((String) value));
        }
        return safe;
    }

    private static String safeValue(String value) {
        if (value == null) return "";
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    private static int appendLine(String line) {
        File file = getLogFile();
        if (file == null) return 0;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, true), "UTF-8"))) {
            writer.write(line);
            writer.newLine();
        } catch (Exception error) {
            return 0;
        }

        List<String> lines = readLines(file);
        if (file.length() > MAX_LOCAL_BYTES || lines.size() > MAX_LOCAL_LINES) {
            int from = Math.max(0, lines.size() - MAX_LOCAL_LINES);
            lines = new ArrayList<>(lines.subList(from, lines.size()));
            writeLines(file, lines);
        }
        return lines.size();
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
            file.delete();
            return;
        }
        writeLines(file, new ArrayList<>(lines.subList(count, lines.size())));
    }

    private static List<String> readLines(File file) {
        List<String> lines = new ArrayList<>();
        if (file == null || !file.exists()) return lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), "UTF-8"))) {
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
                new FileOutputStream(file, false), "UTF-8"))) {
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
