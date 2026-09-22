package com.RobinNotBad.BiliClient.util;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class Cookies {
    private final Map<String, String> cookieMap = new LinkedHashMap<>();

    public Cookies(String cookieString) {
        parseCookieString(cookieString);
    }

    private void parseCookieString(String cookieString) {
        cookieMap.clear();
        if (cookieString == null || cookieString.trim().isEmpty()) return;
        String[] cookies = cookieString.split(";");
        for (String cookie : cookies) {
            String normalized = cookie.trim();
            int separator = normalized.indexOf('=');
            if (separator <= 0) continue;
            String key = normalized.substring(0, separator).trim();
            String value = normalized.substring(separator + 1).trim();
            if (!key.isEmpty()) cookieMap.put(key, value);
        }
    }

    public void set(String key, String value) {
        String existingKey = findKey(key);
        cookieMap.put(existingKey == null ? key : existingKey, value);
    }

    public String get(String key) {
        String existingKey = findKey(key);
        return existingKey == null ? null : cookieMap.get(existingKey);
    }

    public String getOrDefault(String key, String defaultVal) {
        String val = cookieMap.get(key);
        return val != null ? val : defaultVal;
    }

    public boolean containsKey(String key) {
        return findKey(key) != null;
    }

    public void remove(String key) {
        String existingKey = findKey(key);
        if (existingKey != null) cookieMap.remove(existingKey);
    }

    private String findKey(String key) {
        if (key == null) return null;
        String wanted = key.toLowerCase(Locale.US);
        for (String existing : cookieMap.keySet()) {
            if (existing.toLowerCase(Locale.US).equals(wanted)) return existing;
        }
        return null;
    }

    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

}
