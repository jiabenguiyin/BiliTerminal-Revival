package com.RobinNotBad.BiliClient.activity.browser;

import org.junit.Test;
import java.io.IOException;
import java.nio.charset.Charset;
import static org.junit.Assert.*;

public class BrowserProxyRequestTest {
    @Test public void connectPreservesTlsDestination() throws Exception {
        BrowserProxyRequest request = BrowserProxyRequest.parse("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n");
        assertTrue(request.tunnel);
        assertEquals("example.com", request.host);
        assertEquals(443, request.port);
        assertEquals(0, request.initialData.length);
    }

    @Test public void postPreservesBodyFramingAndStripsProxyCredentials() throws Exception {
        BrowserProxyRequest request = BrowserProxyRequest.parse("POST http://example.com/a?q=1 HTTP/1.1\r\n"
                + "Host: wrong.test\r\nContent-Length: 7\r\nCookie: website=1\r\n"
                + "Proxy-Authorization: Basic secret\r\nProxy-Connection: keep-alive\r\n\r\n");
        String head = new String(request.initialData, Charset.forName("ISO-8859-1"));
        assertFalse(request.tunnel);
        assertEquals(80, request.port);
        assertTrue(head.startsWith("POST /a?q=1 HTTP/1.1\r\n"));
        assertTrue(head.contains("Content-Length: 7\r\n"));
        assertTrue(head.contains("Cookie: website=1\r\n"));
        assertTrue(head.contains("Host: example.com\r\n"));
        assertFalse(head.contains("wrong.test"));
        assertFalse(head.contains("secret"));
        assertFalse(head.contains("Proxy-"));
    }

    @Test public void ipv6ConnectIsParsedWithoutBrackets() throws Exception {
        BrowserProxyRequest request = BrowserProxyRequest.parse("CONNECT [2606:4700:4700::1111]:443 HTTP/1.1\r\n\r\n");
        assertEquals("2606:4700:4700::1111", request.host);
    }

    @Test public void nonWebTargetsAreRejected() {
        for (String target : new String[]{"CONNECT example.com:22", "CONNECT example.com:443/path",
                "CONNECT example.com:443?q=1", "GET file:///etc/passwd", "GET https://example.com/",
                "GET http://user:pass@example.com/"}) {
            try {
                BrowserProxyRequest.parse(target + " HTTP/1.1\r\n\r\n");
                fail(target);
            } catch (IOException expected) {}
        }
    }
}
