package com.RobinNotBad.BiliClient.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CookieMergeUtil {
    private static final Set<String> AUTH_COOKIE_NAMES = new HashSet<>(Arrays.asList(
            "sessdata", "bili_jct", "dedeuserid", "dedeuserid__ckmd5", "ac_time_value"
    ));

    private CookieMergeUtil() {
    }

    public static String merge(String existing, List<String> setCookieHeaders,
                               boolean allowAuthCookieUpdates) {
        Cookies cookies = new Cookies(existing);
        if (setCookieHeaders == null) return cookies.toString();

        for (String header : setCookieHeaders) {
            if (header == null) continue;
            int attributesStart = header.indexOf(';');
            String pair = (attributesStart >= 0 ? header.substring(0, attributesStart) : header).trim();
            int separator = pair.indexOf('=');
            if (separator <= 0) continue;

            String key = pair.substring(0, separator).trim();
            String value = pair.substring(separator + 1).trim();
            if (key.isEmpty()) continue;

            boolean authCookie = AUTH_COOKIE_NAMES.contains(key.toLowerCase(Locale.US));
            if (authCookie && (!allowAuthCookieUpdates || value.isEmpty())) continue;

            if (value.isEmpty()) cookies.remove(key);
            else cookies.set(key, value);
        }
        return cookies.toString();
    }
}
