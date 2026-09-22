package com.RobinNotBad.BiliClient.activity.browser;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

public class BrowserActivity extends BaseActivity {
    private static final String HOME_URL = "https://jp.031030.xyz/";

    private WebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private BrowserRelayProxy relayProxy;
    private boolean relayMode;
    private boolean routeReady;
    private boolean proxyApplied;
    private boolean destroyed;
    private int routeGeneration;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_browser);
        } catch (Throwable error) {
            MsgUtil.showMsg("系统 WebView 不可用");
            finish();
            return;
        }

        webView = findViewById(R.id.browser_webview);
        addressBar = findViewById(R.id.browser_address);
        progressBar = findViewById(R.id.browser_progress);
        backButton = findViewById(R.id.browser_back);
        forwardButton = findViewById(R.id.browser_forward);
        ImageButton homeButton = findViewById(R.id.browser_home);
        ImageButton refreshButton = findViewById(R.id.browser_refresh);
        ImageButton externalButton = findViewById(R.id.browser_external);
        ImageButton goButton = findViewById(R.id.browser_go);

        configureWebView();

        backButton.setOnClickListener(view -> {
            if (!routeReady) { finish(); return; }
            if (webView.canGoBack()) webView.goBack();
            else finish();
        });
        forwardButton.setOnClickListener(view -> {
            if (routeReady && webView.canGoForward()) webView.goForward();
        });
        homeButton.setOnClickListener(view -> loadUrl(HOME_URL));
        refreshButton.setOnClickListener(view -> {
            if (routeReady) webView.reload();
            else configureRouting(null);
        });
        externalButton.setOnClickListener(view -> openExternally(webView.getUrl()));
        goButton.setOnClickListener(view -> submitAddress());
        addressBar.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO || enterPressed) {
                submitAddress();
                return true;
            }
            return false;
        });

        configureRouting(savedInstanceState);
    }

    private void configureRouting(Bundle savedInstanceState) {
        routeReady = false;
        relayMode = NetWorkUtil.isRelayActive();
        int generation = ++routeGeneration;
        webView.stopLoading();
        webView.getSettings().setBlockNetworkLoads(true);
        String currentUrl = webView.getUrl();
        Runnable ready = () -> {
            if (destroyed || generation != routeGeneration) return;
            routeReady = true;
            webView.getSettings().setBlockNetworkLoads(false);
            if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
                updateNavigationState();
            } else {
                loadUrl(isWebUrl(currentUrl) ? currentUrl : HOME_URL);
            }
        };
        try {
            if (relayMode) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    MsgUtil.showMsg("当前系统 WebView 不支持网页中继，已阻止直连");
                    return;
                }
                if (relayProxy != null) relayProxy.close();
                // Keep relay-only routing when the tunnel fails, but do not
                // interrupt browsing with a misleading "use direct access"
                // toast. The page/WebView error remains the visible result.
                relayProxy = new BrowserRelayProxy(() -> {
                });
                // No DIRECT fallback, including implicit localhost bypass rules.
                ProxyConfig config = new ProxyConfig.Builder()
                        .addProxyRule("http://127.0.0.1:" + relayProxy.getPort())
                        .removeImplicitRules().build();
                proxyApplied = true;
                ProxyController.getInstance().setProxyOverride(config, this::runOnUiThread, ready);
            } else if (proxyApplied) {
                ProxyController.getInstance().clearProxyOverride(this::runOnUiThread, () -> {
                    if (destroyed || generation != routeGeneration) return;
                    proxyApplied = false;
                    if (relayProxy != null) {
                        relayProxy.close();
                        relayProxy = null;
                    }
                    ready.run();
                });
            } else {
                ready.run();
            }
        } catch (Exception error) {
            MsgUtil.showMsg("网页网络配置失败，已停止加载");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null && relayMode != NetWorkUtil.isRelayActive()) configureRouting(null);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setBlockNetworkLoads(true);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            settings.setDisplayZoomControls(false);
            settings.setAllowContentAccess(false);
        }
        settings.setAllowFileAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSaveFormData(false);
        settings.setSavePassword(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            webView.removeJavascriptInterface("searchBoxJavaBridge_");
            webView.removeJavascriptInterface("accessibility");
            webView.removeJavascriptInterface("accessibilityTraversal");
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isWebUrl(url)) return false;
                openExternally(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                addressBar.setText(url);
                updateNavigationState();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                addressBar.setText(url);
                updateNavigationState();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                MsgUtil.showMsg("网页证书无效，已停止加载");
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
                openExternally(url));
    }

    private void submitAddress() {
        loadUrl(normalizeInput(addressBar.getText().toString()));
        addressBar.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
        }
    }

    private String normalizeInput(String input) {
        String value = input == null ? "" : input.trim();
        if (TextUtils.isEmpty(value)) return HOME_URL;
        if (isWebUrl(value)) return value;
        if (!value.contains(" ") && value.contains(".")) return "https://" + value;
        return "https://cn.bing.com/search?q=" + Uri.encode(value);
    }

    private boolean isWebUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String scheme = Uri.parse(url).getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private void loadUrl(String url) {
        if (!routeReady) return;
        if (!isWebUrl(url)) {
            MsgUtil.showMsg("仅支持 HTTP 或 HTTPS 网页");
            return;
        }
        webView.loadUrl(url);
    }

    private void openExternally(String url) {
        if (TextUtils.isEmpty(url)) return;
        if (relayMode) {
            MsgUtil.showMsg("中继模式下不跳转外部应用，避免绕过中继");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            MsgUtil.showMsg("没有可打开此链接的应用");
        } catch (Throwable error) {
            MsgUtil.showMsg("无法打开此链接");
        }
    }

    private void updateNavigationState() {
        backButton.setEnabled(true);
        backButton.setAlpha(1f);
        forwardButton.setEnabled(webView.canGoForward());
        forwardButton.setAlpha(webView.canGoForward() ? 1f : 0.35f);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (routeReady && webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ++routeGeneration;
        if (webView != null) {
            webView.stopLoading();
            webView.getSettings().setBlockNetworkLoads(true);
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        BrowserRelayProxy proxy = relayProxy;
        relayProxy = null;
        if (proxyApplied) {
            try {
                ProxyController.getInstance().clearProxyOverride(this::runOnUiThread, () -> {
                    if (proxy != null) proxy.close();
                });
            } catch (Exception ignored) {
                if (proxy != null) proxy.close();
            }
        } else if (proxy != null) {
            proxy.close();
        }
        super.onDestroy();
    }
}
