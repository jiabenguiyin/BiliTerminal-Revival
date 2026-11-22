package com.RobinNotBad.BiliClient.activity.settings.login;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.api.IntlLoginApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.IntlAccountStore;

public class IntlPasswordLoginActivity extends BaseActivity {
    private EditText email;
    private EditText password;
    private TextView status;
    private View submit;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intl_password_login);
        setPageName("国际站登录");
        email = findViewById(R.id.intlLoginEmail);
        password = findViewById(R.id.intlLoginPassword);
        password.setTextIsSelectable(false);
        password.setLongClickable(false);
        status = findViewById(R.id.intlLoginStatus);
        submit = findViewById(R.id.intlLoginSubmit);
        submit.setOnClickListener(v -> submitLogin());
        findViewById(R.id.intlLoginOfficial).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.bilibili.tv/en/")));
            } catch (Exception ignored) {
                status.setText("此设备没有可用的浏览器");
            }
        });
    }

    private void submitLogin() {
        if (busy) return;
        String account = email.getText().toString().trim();
        char[] secret = password.getText().toString().toCharArray();
        if (account.isEmpty() || secret.length == 0) {
            java.util.Arrays.fill(secret, '\0');
            status.setText("请输入国际站邮箱和密码");
            return;
        }
        busy = true;
        submit.setEnabled(false);
        status.setText("正在连接国际站官方登录接口…");
        CenterThreadPool.run(() -> {
            String message;
            boolean success = false;
            try {
                IntlLoginApi.LoginResult result = IntlLoginApi.login(account, secret);
                success = IntlAccountStore.save(result.mid, result.cookies);
                message = success ? "国际站登录成功，UID " + result.mid : "国际站会话保存失败";
            } catch (IntlLoginApi.LoginException error) {
                message = error.getMessage();
            } catch (Exception error) {
                java.util.Arrays.fill(secret, '\0');
                message = "国际站登录失败，请稍后重试";
            }
            final boolean resultSuccess = success;
            final String resultMessage = message;
            CenterThreadPool.runOnUiThread(() -> {
                if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                        && isDestroyed())) return;
                busy = false;
                submit.setEnabled(true);
                status.setText(resultMessage);
                if (resultSuccess) password.setText("");
            });
        });
    }
}
