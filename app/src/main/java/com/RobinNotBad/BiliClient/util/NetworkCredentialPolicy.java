package com.RobinNotBad.BiliClient.util;

import java.net.URI;
import java.util.List;

public final class NetworkCredentialPolicy {
    private NetworkCredentialPolicy() {
    }

    public static boolean shouldAttach(String url, boolean relayEnabled, String relayBase,
                                       List<String> allowedHosts) {
        if (url == null || url.isEmpty()) return false;
        if (relayEnabled && relayBase != null && url.startsWith(relayBase + "/")) return true;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return false;
            }
            return isAllowedHost(host, allowedHosts);
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isAllowedHost(String host, List<String> allowedHosts) {
        String lowerHost = host.toLowerCase();
        for (String rule : allowedHosts) {
            String lowerRule = rule.toLowerCase();
            if (lowerRule.startsWith("upos-*.")) {
                String suffix = lowerRule.substring("upos-*".length());
                if (lowerHost.startsWith("upos-")
                        && lowerHost.endsWith(suffix)
                        && lowerHost.length() > suffix.length() + "upos-".length()) return true;
            } else if (lowerRule.startsWith("*.")) {
                String suffix = lowerRule.substring(1);
                if (lowerHost.endsWith(suffix) && lowerHost.length() > suffix.length()) return true;
            } else if (lowerHost.equals(lowerRule)) {
                return true;
            }
        }
        return false;
    }
}
