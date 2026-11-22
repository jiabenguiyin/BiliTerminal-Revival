package com.RobinNotBad.BiliClient.util;

import java.net.URI;
import java.util.Locale;

public final class OfficialLoginUrlPolicy {
    private OfficialLoginUrlPolicy() {
    }

    public static boolean allowsMainLoginUrl(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getUserInfo() != null || host == null) return false;
            String normalized = host.toLowerCase(Locale.US);
            return isDomainOrSubdomain(normalized, "bilibili.com")
                    || normalized.equals("b23.tv")
                    || isDomainOrSubdomain(normalized, "geetest.com")
                    || isDomainOrSubdomain(normalized, "geevisit.com")
                    || isDomainOrSubdomain(normalized, "gsensebot.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isDomainOrSubdomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
