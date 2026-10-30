package com.RobinNotBad.BiliClient.util;

public final class SearchQueryUtil {

    private SearchQueryUtil() {
    }

    public static String plainResultTitle(String title) {
        if (title == null) return "";
        return title.contains("<em") ? org.jsoup.Jsoup.parseBodyFragment(title).text()
                : StringUtil.htmlToString(title);
    }

    public static Long parseUserUid(String query) {
        if (query == null) return null;
        String value = query.trim();
        if (value.isEmpty()) return null;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return null;
        }
        try {
            long uid = Long.parseLong(value);
            return uid > 0 ? uid : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
