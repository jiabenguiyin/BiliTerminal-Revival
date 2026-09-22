package com.RobinNotBad.BiliClient.activity.browser;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/** A loopback HTTP proxy. Each connection is a binary stream over authenticated TLS. */
public final class BrowserRelayProxy implements Closeable {
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private final ServerSocket listener;
    private final OkHttpClient client;
    private final HttpUrl endpoint;
    private final String token;
    private final Runnable onFailure;
    private final AtomicBoolean failureReported = new AtomicBoolean();
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();
    private final Set<Connection> connections = Collections.synchronizedSet(new HashSet<>());
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 16, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>());
    private volatile boolean closed;

    public BrowserRelayProxy(Runnable onFailure) throws IOException {
        this.onFailure = onFailure;
        HttpUrl base = HttpUrl.parse(NetWorkUtil.BILI_RELAY_BASE);
        token = NetWorkUtil.BILI_RELAY_TOKEN;
        if (base == null || !base.isHttps() || token == null || token.length() < 24
                || !base.username().isEmpty() || !base.password().isEmpty()) {
            throw new IOException("Invalid browser relay configuration");
        }
        endpoint = base.newBuilder().encodedPath("/browser-tunnel").query(null).fragment(null).build();
        // Do not inherit Bilibili cookies, API interceptors or the WebView proxy itself.
        client = NetWorkUtil.setOkHttpSsl(new OkHttpClient.Builder())
                .proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS).pingInterval(30, TimeUnit.SECONDS).build();
        listener = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        Thread acceptor = new Thread(this::accept, "browser-relay-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public int getPort() {
        return listener.getLocalPort();
    }

    private void accept() {
        while (!closed) {
            try {
                Socket socket = listener.accept();
                socket.setSoTimeout(120000);
                Connection connection = new Connection(socket);
                synchronized (connections) {
                    if (closed) {
                        connection.close();
                        return;
                    }
                    connections.add(connection);
                }
                try {
                    workers.execute(connection::run);
                } catch (RuntimeException error) {
                    connection.close();
                }
            } catch (IOException error) {
                if (!closed) reportFailure();
                return;
            }
        }
    }

    private void reportFailure() {
        if (!closed && failureReported.compareAndSet(false, true)) onFailure.run();
    }

    private final class Connection extends WebSocketListener implements Closeable {
        private final Socket socket;
        private final AtomicBoolean ended = new AtomicBoolean();
        private final CountDownLatch ready = new CountDownLatch(1);
        private volatile WebSocket webSocket;
        private volatile boolean opened;
        private volatile boolean responseStarted;
        private BrowserProxyRequest request;

        Connection(Socket socket) {
            this.socket = socket;
        }

        void run() {
            try {
                socket.setSoTimeout(15000);
                InputStream input = socket.getInputStream();
                request = BrowserProxyRequest.parse(readHeader(input));
                HttpUrl url = endpoint.newBuilder().addQueryParameter("host", request.host)
                        .addQueryParameter("port", String.valueOf(request.port)).build();
                WebSocket ws = client.newWebSocket(new Request.Builder().url(url)
                        .header("X-Relay-Token", token).build(), this);
                webSocket = ws;
                if (ended.get()) {
                    ws.cancel();
                    return;
                }
                if (!ready.await(20, TimeUnit.SECONDS) || !opened || ended.get()) throw new IOException("Tunnel unavailable");
                socket.setSoTimeout(120000);
                byte[] buffer = new byte[16 * 1024];
                int count;
                while (!ended.get() && (count = input.read(buffer)) != -1) {
                    // Bound the queue when upstream is slower than the WebView.
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    while (ws.queueSize() > 128 * 1024 && !ended.get()) {
                        if (System.nanoTime() > deadline) throw new IOException("Tunnel stalled");
                        Thread.sleep(10);
                    }
                    if (ended.get() || !ws.send(ByteString.of(buffer, 0, count))) throw new IOException("Tunnel closed");
                }
            } catch (Exception error) {
                if (!closed && !ended.get()) {
                    reportFailure();
                    if (!responseStarted) {
                        try {
                            socket.getOutputStream().write(("HTTP/1.1 502 Bad Gateway\r\n"
                                    + "Connection: close\r\nContent-Length: 0\r\n\r\n").getBytes(LATIN1));
                        } catch (IOException ignored) {}
                    }
                }
            } finally {
                close();
            }
        }

        @Override
        public void onOpen(WebSocket ws, Response response) {
            webSocket = ws;
            if (ended.get()) {
                ws.cancel();
                ready.countDown();
                return;
            }
            try {
                if (request.tunnel) {
                    socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(LATIN1));
                    responseStarted = true;
                } else if (!ws.send(ByteString.of(request.initialData))) {
                    throw new IOException("Cannot send request");
                }
                opened = true;
            } catch (IOException error) {
                close();
            } finally {
                ready.countDown();
            }
        }

        @Override
        public void onMessage(WebSocket ws, ByteString bytes) {
            try {
                OutputStream output = socket.getOutputStream();
                responseStarted = true;
                output.write(bytes.toByteArray());
            } catch (IOException error) {
                close();
            }
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            close();
        }

        @Override
        public void onFailure(WebSocket ws, Throwable error, Response response) {
            if (response != null) response.close();
            reportFailure();
            ready.countDown();
            // Let run() produce 502 while still waiting for the handshake.
            if (opened) close();
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            ws.close(code, null);
            close();
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            close();
        }

        @Override
        public void close() {
            if (!ended.compareAndSet(false, true)) return;
            ready.countDown();
            try { socket.close(); } catch (IOException ignored) {}
            WebSocket ws = webSocket;
            if (ws != null) ws.cancel();
            connections.remove(this);
        }
    }

    private static String readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int suffix = 0;
        while (result.size() < 32768) {
            int next = input.read();
            if (next == -1) throw new IOException("Incomplete proxy header");
            result.write(next);
            suffix = (suffix << 8) | next;
            if (suffix == 0x0d0a0d0a) return new String(result.toByteArray(), LATIN1);
        }
        throw new IOException("Proxy header too large");
    }

    @Override
    public void close() {
        if (!cleanupStarted.compareAndSet(false, true)) return;
        closed = true;
        try { listener.close(); } catch (IOException ignored) {}
        Connection[] snapshot;
        synchronized (connections) {
            snapshot = connections.toArray(new Connection[0]);
        }
        for (Connection connection : snapshot) connection.close();
        workers.shutdownNow();
        // Closing pooled TLS sockets can perform network I/O. Keep it off the UI thread.
        Thread cleanup = new Thread(() -> {
            client.dispatcher().cancelAll();
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }, "browser-relay-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }
}
