package com.RobinNotBad.BiliClient.activity.browser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;

/** Parses the first request on a WebView proxy connection, without changing HTTPS. */
public final class BrowserProxyRequest {
    public final String host;
    public final int port;
    public final boolean tunnel;
    public final byte[] initialData;

    private BrowserProxyRequest(String host, int port, boolean tunnel, byte[] initialData) {
        this.host = host;
        this.port = port;
        this.tunnel = tunnel;
        this.initialData = initialData;
    }

    public static BrowserProxyRequest parse(String header) throws IOException {
        try {
            String[] lines = header.split("\r\n");
            String[] first = lines[0].split(" ");
            if (first.length != 3 || !"HTTP/1.1".equals(first[2])) throw new IOException("Invalid proxy request");
            boolean tunnel = "CONNECT".equals(first[0]);
            URI uri = new URI(tunnel ? "https://" + first[1] : first[1]);
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getFragment() != null) {
                throw new IOException("Invalid proxy target");
            }
            if (!tunnel && !"http".equalsIgnoreCase(uri.getScheme())) throw new IOException("HTTP proxy expected");
            int port = uri.getPort() == -1 ? (tunnel ? 443 : 80) : uri.getPort();
            if (port != 80 && port != 443) throw new IOException("Unsupported website port");
            if (tunnel && uri.getRawPath() != null && !uri.getRawPath().isEmpty()) throw new IOException("Invalid CONNECT");
            if (tunnel && uri.getRawQuery() != null) throw new IOException("Invalid CONNECT");
            String host = uri.getHost();
            if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
            if (tunnel) return new BrowserProxyRequest(host, port, true, new byte[0]);
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            boolean webSocket = false;
            for (String line : lines) {
                if ("upgrade: websocket".equalsIgnoreCase(line.trim())) webSocket = true;
            }
            StringBuilder result = new StringBuilder(first[0]).append(' ').append(path).append(" HTTP/1.1\r\n");
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon <= 0) throw new IOException("Invalid header");
                String name = lines[i].substring(0, colon);
                if ("Proxy-Authorization".equalsIgnoreCase(name) || "Proxy-Connection".equalsIgnoreCase(name)
                        || "Host".equalsIgnoreCase(name) || "Connection".equalsIgnoreCase(name)) continue;
                result.append(lines[i]).append("\r\n");
            }
            result.append("Host: ").append(uri.getRawAuthority()).append(webSocket
                    ? "\r\nConnection: Upgrade\r\n\r\n" : "\r\nConnection: close\r\n\r\n");
            return new BrowserProxyRequest(host, port, false,
                    result.toString().getBytes(Charset.forName("ISO-8859-1")));
        } catch (Exception error) {
            throw new IOException("Invalid browser proxy request", error);
        }
    }
}
