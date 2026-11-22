package com.RobinNotBad.BiliClient.activity.settings.login;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.View;
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
import com.RobinNotBad.BiliClient.util.WebViewAvailability;

import org.json.JSONObject;

/** Native SMS login. Geetest is the only browser-dependent step and has a QR fallback. */
public class SmsLoginActivity extends BaseActivity {
    private static final int REQUEST_CAPTCHA = 702;
    private EditText phone;
    private EditText code;
    private TextView status;
    private View send;
    private View login;
    private boolean busy;
    private String captchaToken;
    private String captchaChallenge;
    private String captchaKey = "";
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_login);
        setPageName("短信登录");
        phone = findViewById(R.id.smsPhone);
        code = findViewById(R.id.smsCode);
        status = findViewById(R.id.smsStatus);
        send = findViewById(R.id.smsSend);
        login = findViewById(R.id.smsLogin);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        send.setOnClickListener(v -> requestCaptcha());
        login.setOnClickListener(v -> submitLogin());
        findViewById(R.id.smsQr).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        findViewById(R.id.smsImport).setOnClickListener(v -> {
            startActivity(new Intent(this, SpecialLoginActivity.class));
            finish();
        });
        if (!WebViewAvailability.isAvailable(this)) {
            status.setText("本设备没有网页组件，短信安全验证不可用，请使用二维码或其他设备导入");
            send.setEnabled(false);
            login.setEnabled(false);
        }
    }

    private void requestCaptcha() {
        if (busy) return;
        String value = phone.getText().toString().trim();
        if (!value.matches("[0-9]{5,20}")) {
            status.setText("请输入有效的手机号");
            return;
        }
        busy = true;
        send.setEnabled(false);
        status.setText("正在获取安全验证…");
        CenterThreadPool.run(() -> {
            try {
                try {
                    CookiesApi.checkCookies();
                } catch (Exception ignored) {
                    // Captcha can still be requested when optional browser cookies fail to initialize.
                }
                JSONObject response = LoginApi.getCaptcha();
                if (response.optInt("code", -1) != 0) {
                    postStatus("验证码接口异常：" + response.optString("message", "请稍后重试"));
                    return;
                }
                JSONObject data = response.getJSONObject("data");
                JSONObject geetest = data.getJSONObject("geetest");
                String token = data.optString("token", "");
                String gt = geetest.optString("gt", "");
                String challenge = geetest.optString("challenge", "");
                if (token.length() == 0 || !CaptchaParamPolicy.areValid(gt, challenge)) {
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
            showFallback();
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            busy = false;
            send.setEnabled(true);
            status.setText("验证未完成，可以重试");
            return;
        }
        String resultChallenge = data.getStringExtra(CaptchaWebViewActivity.RESULT_CHALLENGE);
        String validate = data.getStringExtra(CaptchaWebViewActivity.RESULT_VALIDATE);
        String seccode = data.getStringExtra(CaptchaWebViewActivity.RESULT_SECCODE);
        sendSms(resultChallenge, validate, seccode);
    }

    private void sendSms(String challenge, String validate, String seccode) {
        String value = phone.getText().toString().trim();
        String safeSeccode = seccode == null || seccode.length() == 0
                ? validate + "|jordan" : seccode;
        status.setText("正在发送验证码…");
        CenterThreadPool.run(() -> {
            try {
                LoginApi.LoginResponse response = LoginApi.smsSendWithCookies(value, captchaToken,
                        challenge == null ? captchaChallenge : challenge, validate, safeSeccode);
                if (response.body.optInt("code", -1) != 0) {
                    postStatus("短信发送失败：" + response.body.optString("message", "请稍后重试"));
                    return;
                }
                JSONObject data = response.body.optJSONObject("data");
                captchaKey = data == null ? "" : data.optString("captcha_key", "");
                runOnUiThread(() -> {
                    busy = false;
                    send.setEnabled(false);
                    login.setEnabled(true);
                    status.setText("验证码已发送，请输入短信验证码");
                    startCountdown();
                });
            } catch (Exception error) {
                postStatus("短信发送失败，请检查网络后重试");
            }
        });
    }

    private void submitLogin() {
        if (busy) return;
        String phoneValue = phone.getText().toString().trim();
        String codeValue = code.getText().toString().trim();
        if (codeValue.length() == 0) {
            status.setText("请输入短信验证码");
            return;
        }
        busy = true;
        login.setEnabled(false);
        status.setText("正在登录…");
        CenterThreadPool.run(() -> {
            try {
                LoginApi.LoginResponse response = LoginApi.smsLoginWithCookies(phoneValue, codeValue, captchaKey);
                if (response.body.optInt("code", -1) != 0) {
                    postStatus("登录失败：" + response.body.optString("message", "验证码可能已过期"));
                    return;
                }
                if (!MainLoginUtil.save(response)) {
                    postStatus("登录成功，但登录信息保存失败，请改用二维码登录");
                    return;
                }
                runOnUiThread(this::finishLogin);
            } catch (Exception error) {
                postStatus("登录失败，请检查网络后重试");
            }
        });
    }

    private void finishLogin() {
        busy = false;
        code.setText("");
        InstanceActivity instance = BiliTerminal.getInstanceActivityOnTop();
        if (instance != null && !instance.isDestroyed()) instance.finish();
        startActivity(new Intent(this, SplashActivity.class));
        finish();
    }

    private void startCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override public void onTick(long left) {
                send.setEnabled(false);
                ((TextView) send.findViewById(R.id.smsSendText)).setText("重新发送 " + (left / 1000));
            }

            @Override public void onFinish() {
                send.setEnabled(true);
                ((TextView) send.findViewById(R.id.smsSendText)).setText("重新获取验证码");
            }
        };
        countDownTimer.start();
    }

    private void postStatus(String message) {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            busy = false;
            send.setEnabled(true);
            login.setEnabled(true);
            status.setText(message);
        });
    }

    private void showFallback() {
        new AlertDialog.Builder(this)
                .setTitle("本设备缺少网页组件")
                .setMessage("短信安全验证无法打开，请使用二维码登录或从其他设备导入。")
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

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) countDownTimer.cancel();
        super.onDestroy();
    }
}
