package com.RobinNotBad.BiliClient.activity.settings.login;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.util.CaptchaParamPolicy;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.WebViewAvailability;

import org.json.JSONObject;

/** Local Geetest page used only after the official captcha endpoint supplies parameters. */
public class CaptchaWebViewActivity extends BaseActivity {
    public static final String EXTRA_GT = "gt";
    public static final String EXTRA_CHALLENGE = "challenge";
    public static final String RESULT_CHALLENGE = "geetest_challenge";
    public static final String RESULT_VALIDATE = "geetest_validate";
    public static final String RESULT_SECCODE = "geetest_seccode";
    public static final int RESULT_UNAVAILABLE = RESULT_FIRST_USER;

    private WebView webView;
    private String gt;
    private String challenge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!WebViewAvailability.isAvailable(this)) {
            MsgUtil.showMsgLong("本设备没有可用的网页组件，已返回登录页。请使用二维码登录或其他设备导入。" );
            setResult(RESULT_UNAVAILABLE);
            finish();
            return;
        }

        gt = getIntent().getStringExtra(EXTRA_GT);
        challenge = getIntent().getStringExtra(EXTRA_CHALLENGE);
        if (!CaptchaParamPolicy.areValid(gt, challenge)) {
            Logu.w("CaptchaWebView", "gt/challenge 参数校验失败");
            MsgUtil.showMsg("验证码参数无效，请重新获取");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        setContentView(R.layout.activity_captcha_webview);
        setPageName("安全验证");
        initZoomControls();
        initWebView();
    }

    private void initZoomControls() {
        Button in = findViewById(R.id.btnZoomIn);
        Button out = findViewById(R.id.btnZoomOut);
        Button reset = findViewById(R.id.btnZoomReset);
        in.setOnClickListener(v -> setZoom(1.25f));
        out.setOnClickListener(v -> setZoom(0.8f));
        reset.setOnClickListener(v -> setZoom(1.0f));
    }

    private void setZoom(float scale) {
        if (webView == null) return;
        String script = "javascript:(function(){var v=document.querySelector('meta[name=\\\"viewport\\\"]');"
                + "if(v){v.setAttribute('content','width=device-width,initial-scale=" + scale
                + ",maximum-scale=3,user-scalable=yes');}})();";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, null);
        } else {
            webView.loadUrl(script);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        ViewGroup container = findViewById(R.id.webViewContainer);
        WebView created;
        try {
            created = new WebView(this);
        } catch (Throwable error) {
            Logu.e("CaptchaWebView", "WebView 创建失败: " + error.getClass().getSimpleName());
            setResult(RESULT_UNAVAILABLE);
            finish();
            return;
        }
        created.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(created);
        webView = created;

        WebSettings settings = created.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setAllowFileAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(created, true);
        }
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        created.addJavascriptInterface(new CaptchaBridge(), "Android");
        created.setWebViewClient(new WebViewClient());
        created.loadDataWithBaseURL("https://www.bilibili.com/", buildCaptchaHtml(),
                "text/html", "UTF-8", null);
    }

    private String buildCaptchaHtml() {
        String gtLiteral = JSONObject.quote(gt);
        String challengeLiteral = JSONObject.quote(challenge);
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=3,user-scalable=yes\"><style>"
                + "*{box-sizing:border-box}body{margin:0;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#f5f5f5;padding:16px}#captcha{width:100%;max-width:320px}.loading{text-align:center;color:#666;padding:40px 0;font-size:14px}.error{text-align:center;color:#d33;padding:40px 0;font-size:14px}.retry-btn{margin-top:12px;padding:10px 24px;background:#00a1d6;color:#fff;border:0;border-radius:4px;font-size:14px}</style></head><body>"
                + "<div id=\"captcha\"><div class=\"loading\">正在加载验证...</div></div><script>"
                + "var gt=" + gtLiteral + ",challenge=" + challengeLiteral + ",el=document.getElementById('captcha');"
                + "function load(u,done){var s=document.createElement('script');s.src=u;s.onload=function(){done(null)};s.onerror=function(){done(1)};document.body.appendChild(s)}"
                + "function init(){el.innerHTML='<div class=\"loading\">正在加载验证...</div>';load('https://static.geetest.com/static/js/gt.0.4.9.js',function(e){if(e){fail('加载验证核心失败');return}load('https://static.geetest.com/static/js/click.3.1.2.js',function(e2){if(e2){fail('加载验证库失败');return}if(typeof initGeetest==='undefined'){fail('验证库不可用');return}initGeetest({gt:gt,challenge:challenge,new_captcha:true,product:'popup',offline:false,type:'click',https:true},function(obj){el.innerHTML='';obj.appendTo('#captcha');obj.onSuccess(function(){var r=obj.getValidate();if(r&&r.geetest_validate){Android.onCaptchaResult(r.geetest_challenge||challenge,r.geetest_validate,r.geetest_seccode||r.geetest_validate+'|jordan')}else{Android.onCaptchaError('验证结果为空')}});obj.onError(function(){Android.onCaptchaError('验证失败，请重试')});obj.verify()})})})}"
                + "function fail(m){el.innerHTML='<div class=\"error\">'+m+'<br><button class=\"retry-btn\" onclick=\"init()\">重试</button></div>'}init();</script></body></html>";
    }

    private final class CaptchaBridge {
        @JavascriptInterface
        public void onCaptchaResult(String resultChallenge, String validate, String seccode) {
            runOnUiThread(() -> {
                Intent data = new Intent();
                data.putExtra(RESULT_CHALLENGE, resultChallenge);
                data.putExtra(RESULT_VALIDATE, validate);
                data.putExtra(RESULT_SECCODE, seccode);
                setResult(RESULT_OK, data);
                finish();
            });
        }

        @JavascriptInterface
        public void onCaptchaError(String message) {
            runOnUiThread(() -> {
                MsgUtil.showMsg(message);
                setResult(RESULT_CANCELED);
                finish();
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
