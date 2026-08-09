package com.RobinNotBad.BiliClient.util;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
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
        cookieMap.put(key, value);
    }

    public String get(String key) {
        return cookieMap.get(key);
    }

    public String getOrDefault(String key, String defaultVal) {
        String val = cookieMap.get(key);
        return val != null ? val : defaultVal;
    }

    public boolean containsKey(String key) {
        return cookieMap.containsKey(key);
    }

    public void remove(String key) {
        cookieMap.remove(key);
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
