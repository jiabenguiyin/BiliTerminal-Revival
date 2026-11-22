package com.RobinNotBad.BiliClient.activity.settings.login;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.TextView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.SplashActivity;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.api.LoginApi;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.OfficialLoginUrlPolicy;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.WebViewAvailability;

import java.util.LinkedHashMap;
import java.util.Map;

public class OfficialWebLoginActivity extends BaseActivity {
    private static final String LOGIN_URL =
            "https://passport.bilibili.com/login?gourl=https%3A%2F%2Fwww.bilibili.com%2F";

    private WebView webView;
    private TextView status;
    private View confirmButton;
    private boolean verifying;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_official_web_login);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setPageName(getIntent().getBooleanExtra("add_account", false)
                ? "添加主站账号" : "主站网页登录");

        status = findViewById(R.id.webLoginStatus);
        confirmButton = findViewById(R.id.webLoginConfirm);
        if (!WebViewAvailability.isAvailable(this)) {
            MsgUtil.showMsgLong("本设备没有可用的网页组件，请返回使用二维码登录或其他设备导入。" );
            finish();
            return;
        }
        ViewGroup container = findViewById(R.id.officialWebViewContainer);
        try {
            webView = new WebView(this);
            webView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            container.addView(webView);
        } catch (Throwable error) {
            MsgUtil.showMsgLong("网页组件启动失败，请返回使用二维码登录或其他设备导入。" );
            finish();
            return;
        }
        configureWebView();
        confirmButton.setOnClickListener(v -> verifySession());
        webView.loadUrl(LOGIN_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setSaveFormData(false);
        webView.getSettings().setSavePassword(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        } else {
            CookieSyncManager.createInstance(getApplicationContext());
        }
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !OfficialLoginUrlPolicy.allowsMainLoginUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (OfficialLoginUrlPolicy.allowsMainLoginUrl(url)) {
                    status.setText("完成网页登录后，点下方确认登录");
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                status.setText("安全连接失败，请返回使用扫码登录");
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (OfficialLoginUrlPolicy.allowsMainLoginUrl(failingUrl)) {
                    status.setText("网页登录加载失败，请检查网络或返回扫码登录");
                }
            }
        });
    }

    private void verifySession() {
        if (verifying) return;
        CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.getInstance().sync();
        } else {
            cookieManager.flush();
        }
        String candidate = collectMainCookies(cookieManager);
        if (candidate == null || candidate.trim().isEmpty()) {
            status.setText("还没有检测到主站登录信息");
            return;
        }

        verifying = true;
        confirmButton.setEnabled(false);
        status.setText("正在验证登录状态…");
        CenterThreadPool.run(() -> {
            boolean saved = false;
            String message = "网页登录状态尚未通过验证";
            try {
                long mid = LoginApi.verifyMainWebSession(candidate);
                if (mid > 0 && AccountManager.saveLogin(candidate, "")) {
                    saved = true;
                } else if (mid > 0) {
                    message = "登录信息未能保存，请重试";
                }
            } catch (Exception error) {
                message = "验证失败，请检查网络后重试";
            }
            final boolean success = saved;
            final String resultMessage = message;
            CenterThreadPool.runOnUiThread(() -> {
                if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                        && isDestroyed())) return;
                verifying = false;
                confirmButton.setEnabled(true);
                if (!success) {
                    status.setText(resultMessage);
                    return;
                }
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.setup, true);
                try {
                    LoginApi.requestSSOs();
                } catch (Exception ignored) {
                }
                InstanceActivity instance = BiliTerminal.getInstanceActivityOnTop();
                if (instance != null && !instance.isFinishing()
                        && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                        || !instance.isDestroyed())) instance.finish();
                startActivity(new Intent(this, SplashActivity.class));
                finish();
            });
        });
    }

    private static String collectMainCookies(CookieManager cookieManager) {
        Map<String, String> cookies = new LinkedHashMap<>();
        String[] hosts = new String[]{
                "https://api.bilibili.com",
                "https://www.bilibili.com",
                "https://passport.bilibili.com"
        };
        for (String host : hosts) {
            String raw = cookieManager.getCookie(host);
            if (raw == null) continue;
            for (String part : raw.split(";")) {
                String item = part.trim();
                int separator = item.indexOf('=');
                if (separator <= 0) continue;
                String name = item.substring(0, separator).trim();
                String value = item.substring(separator + 1).trim();
                if (!name.isEmpty() && !value.isEmpty()) cookies.put(name, value);
            }
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (result.length() > 0) result.append("; ");
            result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
