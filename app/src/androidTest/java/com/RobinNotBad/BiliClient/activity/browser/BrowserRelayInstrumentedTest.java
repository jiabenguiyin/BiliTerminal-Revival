package com.RobinNotBad.BiliClient.activity.browser;

import android.app.Activity;
import android.content.Intent;
import android.webkit.WebView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.webkit.WebViewFeature;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BrowserRelayInstrumentedTest {
    private static final Charset ASCII = Charset.forName("ISO-8859-1");

    private String readHeader(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int tail = 0;
        while (bytes.size() < 32768) {
            int b = input.read();
            if (b < 0) break;
            bytes.write(b);
            tail = (tail << 8) | b;
            if (tail == 0x0d0a0d0a) break;
        }
        return new String(bytes.toByteArray(), ASCII);
    }

    @Test public void realHttpsTravelsThroughAuthenticatedRelay() throws Exception {
        AtomicBoolean failed = new AtomicBoolean();
        try (BrowserRelayProxy proxy = new BrowserRelayProxy(() -> failed.set(true));
             Socket socket = new Socket("127.0.0.1", proxy.getPort())) {
            socket.setSoTimeout(30000);
            socket.getOutputStream().write("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n".getBytes(ASCII));
            assertTrue(readHeader(socket.getInputStream()).startsWith("HTTP/1.1 200"));
            try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(socket, "example.com", 443, true)) {
                tls.setSoTimeout(30000);
                tls.startHandshake();
                assertTrue(javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                        .verify("example.com", tls.getSession()));
                tls.getOutputStream().write("GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n".getBytes(ASCII));
                String head = readHeader(tls.getInputStream());
                assertTrue(head, head.startsWith("HTTP/1.1 200"));
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = tls.getInputStream().read(buffer)) != -1) {
                    body.write(buffer, 0, n);
                    assertTrue(body.size() < 1024 * 1024);
                }
                assertTrue(new String(body.toByteArray(), ASCII).contains("Example Domain"));
            }
        }
    }

    @Test public void invalidTokenFailsWithoutDirectFallback() throws Exception {
        String original = NetWorkUtil.BILI_RELAY_TOKEN;
        try {
            NetWorkUtil.BILI_RELAY_TOKEN = "invalid-test-token-not-a-real-secret";
            try (BrowserRelayProxy proxy = new BrowserRelayProxy(() -> {});
                 Socket socket = new Socket("127.0.0.1", proxy.getPort())) {
                socket.setSoTimeout(30000);
                socket.getOutputStream().write("CONNECT example.com:443 HTTP/1.1\r\n\r\n".getBytes(ASCII));
                assertTrue(readHeader(socket.getInputStream()).startsWith("HTTP/1.1 502"));
            }
        } finally {
            NetWorkUtil.BILI_RELAY_TOKEN = original;
        }
    }

    @Test public void unsupportedWebViewBlocksNetwork() throws Exception {
        boolean supported = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE);
        if (supported) return;
        boolean oldAlways = SharedPreferencesUtil.getBoolean("relay_always_on", false);
        boolean oldEnable = NetWorkUtil.BILI_RELAY_ENABLE;
        Activity activity = null;
        try {
            NetWorkUtil.BILI_RELAY_ENABLE = true;
            SharedPreferencesUtil.putBoolean("relay_always_on", true);
            Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), BrowserActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
            Activity current = activity;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                WebView webView = current.findViewById(R.id.browser_webview);
                assertTrue(webView.getSettings().getBlockNetworkLoads());
            });
        } finally {
            if (activity != null) {
                Activity current = activity;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(current::finish);
            }
            SharedPreferencesUtil.putBoolean("relay_always_on", oldAlways);
            NetWorkUtil.BILI_RELAY_ENABLE = oldEnable;
        }
    }
}
