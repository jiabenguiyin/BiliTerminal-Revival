package com.RobinNotBad.BiliClient.util;

import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BilibiliVideoLinkParser {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\\\"']+", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_PATH = Pattern.compile("^/video/(BV[A-Za-z0-9]{10}|av[0-9]{1,10})/?$",
            Pattern.CASE_INSENSITIVE);

    private BilibiliVideoLinkParser() {}

    public static String extractVideoUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = trimTrailingPunctuation(matcher.group());
            if (isBilibiliVideoUrl(candidate)) return candidate;
        }
        return null;
    }

    private static boolean isBilibiliVideoUrl(String value) {
        try {
            URL url = new URL(value);
            if (!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol()))
                return false;
            String host = url.getHost().toLowerCase(Locale.ROOT);
            if ("b23.tv".equals(host) || "www.b23.tv".equals(host)) return url.getPath().length() > 1;
            if (!("bilibili.com".equals(host) || host.endsWith(".bilibili.com"))) return false;
            return VIDEO_PATH.matcher(url.getPath()).matches();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0) {
            char ch = value.charAt(end - 1);
            if (".,!?;:)]}>，。！？；：、）》】」』".indexOf(ch) < 0) break;
            end--;
        }
        return value.substring(0, end);
    }
}
