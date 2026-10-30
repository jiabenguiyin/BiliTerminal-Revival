package com.RobinNotBad.BiliClient.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class BilibiliImageUrlUtil {
    private BilibiliImageUrlUtil() {
    }

    public static String stripResizeSuffix(String url) {
        if (url == null || url.isEmpty()) return url;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || !isBilibiliImageHost(host)) return url;

            int pathEnd = url.length();
            int query = url.indexOf('?');
            int fragment = url.indexOf('#');
            if (query >= 0) pathEnd = Math.min(pathEnd, query);
            if (fragment >= 0) pathEnd = Math.min(pathEnd, fragment);

            int lastSlash = url.lastIndexOf('/', pathEnd - 1);
            int resizeMarker = url.lastIndexOf('@', pathEnd - 1);
            return resizeMarker > lastSlash
                    ? url.substring(0, resizeMarker) + url.substring(pathEnd)
                    : url;
        } catch (URISyntaxException ignored) {
            return url;
        }
    }

    private static boolean isBilibiliImageHost(String host) {
        String normalized = host.toLowerCase(Locale.US);
        return normalized.equals("hdslb.com") || normalized.endsWith(".hdslb.com")
                || normalized.equals("biliimg.com") || normalized.endsWith(".biliimg.com");
    }
}
