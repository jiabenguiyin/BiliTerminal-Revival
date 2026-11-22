package com.RobinNotBad.BiliClient.activity.settings.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.SplashActivity;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.api.CookiesApi;
import com.RobinNotBad.BiliClient.api.LoginApi;
import com.RobinNotBad.BiliClient.util.CaptchaParamPolicy;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MainLoginUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.PasswordEncryptUtil;
import com.RobinNotBad.BiliClient.util.WebViewAvailability;

import org.json.JSONObject;

public class LoginStyleActivity extends BaseActivity {
    private static final int REQUEST_CAPTCHA = 701;
    private EditText account;
    private EditText password;
    private TextView status;
    private android.view.View submit;
    private boolean busy;
    private String captchaToken;
    private String captchaChallenge;
    private final boolean fromSetup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_style);
        setPageName("登录");

        account = findViewById(R.id.loginStyleAccount);
        password = findViewById(R.id.loginStylePassword);
        status = findViewById(R.id.loginStyleStatus);
        submit = findViewById(R.id.loginStyleSubmit);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        submit.setOnClickListener(v -> submitPasswordLogin());
        findViewById(R.id.loginStyleSms).setOnClickListener(v ->
                startActivity(new Intent(this, SmsLoginActivity.class)
                        .putExtra("from_setup", getIntent().getBooleanExtra("from_setup", false))));
        findViewById(R.id.loginStyleQr).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class)
                    .putExtra("from_setup", getIntent().getBooleanExtra("from_setup", false)));
            finish();
        });
        findViewById(R.id.loginStyleIntl).setOnClickListener(v ->
                startActivity(new Intent(this, IntlPasswordLoginActivity.class)));
        findViewById(R.id.loginStyleImport).setOnClickListener(v -> {
            startActivity(new Intent(this, SpecialLoginActivity.class));
            finish();
        });

        android.view.View official = findViewById(R.id.loginStyleOfficial);
        if (!WebViewAvailability.isAvailable(this)) {
            official.setVisibility(android.view.View.GONE);
            submit.setEnabled(false);
            findViewById(R.id.loginStyleSms).setEnabled(false);
            status.setText("本设备没有网页组件，请使用二维码或其他设备导入登录");
        } else {
            official.setOnClickListener(v -> startActivity(new Intent(this, OfficialWebLoginActivity.class)));
        }
    }

    private void submitPasswordLogin() {
        if (busy) return;
        String user = account.getText().toString().trim();
        String secret = password.getText().toString();
        if (user.length() == 0 || secret.length() == 0) {
            status.setText("请输入手机号/邮箱和密码");
            return;
        }
        busy = true;
        submit.setEnabled(false);
        status.setText("正在获取安全验证…");
        CenterThreadPool.run(() -> {
            try {
                try {
                    CookiesApi.checkCookies();
                } catch (Exception ignored) {
                    // Captcha can still be requested when optional browser cookies fail to initialize.
                }
                JSONObject captcha = LoginApi.getCaptcha();
                if (captcha.optInt("code", -1) != 0) {
                    postStatus("验证码接口异常：" + captcha.optString("message", "请稍后重试"));
                    return;
                }
                JSONObject data = captcha.getJSONObject("data");
                JSONObject geetest = data.getJSONObject("geetest");
                String token = data.optString("token", "");
                String gt = geetest.optString("gt", "");
                String challenge = geetest.optString("challenge", "");
                if (!CaptchaParamPolicy.areValid(gt, challenge) || token.length() == 0) {
                    postStatus("验证码参数无效，请重试或改用二维码登录");
                    return;
                }
                captchaToken = token;
                captchaChallenge = challenge;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    status.setText("请完成安全验证");
                    startActivityForResult(new Intent(this, CaptchaWebViewActivity.class)
                            .putExtra(CaptchaWebViewActivity.EXTRA_GT, gt)
                            .putExtra(CaptchaWebViewActivity.EXTRA_CHALLENGE, challenge), REQUEST_CAPTCHA);
                });
            } catch (Exception error) {
                postStatus("获取验证失败，请检查网络后重试");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTCHA) return;
        if (resultCode == CaptchaWebViewActivity.RESULT_UNAVAILABLE) {
            busy = false;
            submit.setEnabled(true);
            showBrowserFallback();
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            busy = false;
            submit.setEnabled(true);
            status.setText("验证未完成，可以重试或改用二维码登录");
            return;
        }
        doPasswordLogin(data.getStringExtra(CaptchaWebViewActivity.RESULT_CHALLENGE),
                data.getStringExtra(CaptchaWebViewActivity.RESULT_VALIDATE),
                data.getStringExtra(CaptchaWebViewActivity.RESULT_SECCODE));
    }

    private void doPasswordLogin(String challenge, String validate, String seccode) {
        final String user = account.getText().toString().trim();
        final String secret = password.getText().toString();
        final String safeChallenge = challenge == null ? captchaChallenge : challenge;
        final String safeSeccode = seccode == null || seccode.length() == 0
                ? validate + "|jordan" : seccode;
        status.setText("正在登录…");
        CenterThreadPool.run(() -> {
            try {
                JSONObject keyResponse = LoginApi.getWebKey();
                JSONObject keyData = keyResponse.getJSONObject("data");
                String encrypted = PasswordEncryptUtil.encryptPassword(secret,
                        keyData.optString("hash", ""), keyData.optString("key", ""));
                LoginApi.LoginResponse response = LoginApi.passwordLoginWithCookies(
                        user, encrypted, captchaToken, safeChallenge, validate, safeSeccode);
                int code = response.body.optInt("code", -1);
                if (code != 0) {
                    postStatus("登录失败：" + response.body.optString("message", "请检查账号或验证状态"));
                    return;
                }
                JSONObject data = response.body.optJSONObject("data");
                if (data != null && data.optInt("status", 0) == 2) {
                    postStatus(data.optString("message", "登录环境存在风险，请按 B 站提示处理"));
                    return;
                }
                if (!MainLoginUtil.save(response)) {
                    postStatus("登录成功，但登录信息保存失败，请改用二维码登录");
                    return;
                }
                runOnUiThread(() -> finishLogin());
            } catch (Exception error) {
                postStatus("登录失败，请检查网络后重试");
            }
        });
    }

    private void finishLogin() {
        busy = false;
        submit.setEnabled(true);
        password.setText("");
        InstanceActivity instance = BiliTerminal.getInstanceActivityOnTop();
        if (instance != null && !instance.isDestroyed()) instance.finish();
        startActivity(new Intent(this, SplashActivity.class));
        finish();
    }

    private void postStatus(String text) {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            busy = false;
            submit.setEnabled(true);
            status.setText(text);
        });
    }

    private void showBrowserFallback() {
        new AlertDialog.Builder(this)
                .setTitle("本设备缺少网页组件")
                .setMessage("安全验证无法打开。请改用不依赖浏览器的二维码登录，或从其他设备导入登录信息。")
                .setNegativeButton("取消", null)
                .setNeutralButton("其他设备导入", (d, w) -> {
                    startActivity(new Intent(this, SpecialLoginActivity.class));
                    finish();
                })
                .setPositiveButton("二维码登录", (d, w) -> {
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                }).show();
    }
}
