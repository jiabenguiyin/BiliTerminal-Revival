package com.RobinNotBad.BiliClient.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.widget.TextView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.activity.settings.setup.SetupUIActivity;
import com.RobinNotBad.BiliClient.activity.video.RecommendActivity;
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity;
import com.RobinNotBad.BiliClient.api.AppInfoApi;
import com.RobinNotBad.BiliClient.api.CookieRefreshApi;
import com.RobinNotBad.BiliClient.api.CookiesApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

//启动页面
//一切的一切的开始

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends Activity {

    private TextView splashTextView;
    private int splashFrame;
    private Timer splashTimer;
    private String splashText = "复活版\n连接中";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(BiliTerminal.getFitDisplayContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_BiliClient);
        setContentView(R.layout.activity_splash);
        BiliTerminal.startDeferredInitialization();
        Log.e("debug", "进入应用");

        splashTextView = findViewById(R.id.splashText);
        splashText = SharedPreferencesUtil.getString("ui_splashtext", "复活版\n连接中");

        splashTimer = new Timer();
        splashTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> showSplashText(splashFrame));
                splashFrame++;
                if (splashFrame > splashText.length()) this.cancel();
            }
        }, 100, 100);

        BiliTerminal.runBackgroundCompat(() -> {

            //FileUtil.clearCache(this);  //先清个缓存（为了防止占用过大）
            //不需要了，我把大部分图片的硬盘缓存都关闭了，只有表情包保留，这样既可以缩减缓存占用又能在一定程度上减少流量消耗

            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.setup, false)) {//判断是否设置完成
                try {
                    // Keep startup direct probing within 15 seconds, then reuse the chosen
                    // route for cookie refresh and bootstrap requests.
                    NetWorkUtil.prepareBiliRoute();

                    // 未登录时请求bilibili.com
                    if (SharedPreferencesUtil.getLong("mid", 0) != 0) {
                        checkCookieRefresh();
                    }

                    CookiesApi.checkCookies();

                    String firstActivity = null;
                    String sortConf = SharedPreferencesUtil.getString(SharedPreferencesUtil.MENU_SORT, "");
                    if (!TextUtils.isEmpty(sortConf)) {
                        String[] splitName = sortConf.split(";");
                        for (String name : splitName) {
                            if (!MenuActivity.btnNames.containsKey(name)) {
                                for (Map.Entry<String, Pair<String, Class<? extends InstanceActivity>>> entry : MenuActivity.btnNames.entrySet()) {
                                    firstActivity = entry.getKey();
                                    break;
                                }
                            } else {
                                firstActivity = name;
                            }
                            break;
                        }
                    } else {
                        for (Map.Entry<String, Pair<String, Class<? extends InstanceActivity>>> entry : MenuActivity.btnNames.entrySet()) {
                            firstActivity = entry.getKey();
                            break;
                        }
                    }

                    Class<? extends InstanceActivity> activityClass = Objects.requireNonNull(MenuActivity.btnNames.get(firstActivity)).second;

                    Intent intent = new Intent();
                    intent.setClass(SplashActivity.this, (activityClass != null ? activityClass : RecommendActivity.class));
                    intent.putExtra("from", firstActivity);

                    interruptSplash();

                    splashTextView.postDelayed(() -> {
                        startActivity(intent);
                        CenterThreadPool.run(() -> AppInfoApi.check(SplashActivity.this));
                        finish();
                    }, 100);

                } catch (IOException e) {
                    runOnUiThread(() -> {
                        MsgUtil.err(e);
                        interruptSplash();
                        splashTextView.setText("网络错误");
                        if (SharedPreferencesUtil.getBoolean("setup", false)) {
                            splashTextView.postDelayed(() -> {
                                Intent intent = new Intent();
                                intent.setClass(SplashActivity.this, LocalListActivity.class);
                                startActivity(intent);
                                finish();
                            }, 300);
                        }
                    });
                } catch (JSONException e) {
                    runOnUiThread(() -> MsgUtil.err(e));
                    Intent intent = new Intent();
                    intent.setClass(SplashActivity.this, LocalListActivity.class);
                    startActivity(intent);
                    interruptSplash();
                    finish();
                }
            } else {
                Intent intent = new Intent();
                intent.setClass(SplashActivity.this, SetupUIActivity.class);   //没登录，去初次设置
                startActivity(intent);
                interruptSplash();
                finish();
            }

        });
    }

    private void checkCookieRefresh() {
        try {
            JSONObject cookieInfo = CookieRefreshApi.cookieInfo();
            if (cookieInfo.optBoolean("refresh")) {
                Log.e("Cookies", "需要刷新");
                if (!Objects.equals(SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""), "")) {
                    String correspondPath = CookieRefreshApi.getCorrespondPath(cookieInfo.getLong("timestamp"));
                    Log.e("CorrespondPath", correspondPath);
                    String refreshCsrf = CookieRefreshApi.getRefreshCsrf(correspondPath);
                    Log.e("RefreshCsrf", refreshCsrf);
                    if (CookieRefreshApi.refreshCookie(refreshCsrf)) {
                        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.cookie_refresh, true);
                        DiagnosticLogManager.record("cookie_refresh_success");
                        MsgUtil.showMsg("Cookies已刷新");
                    } else {
                        markCookieRefreshPending("Cookie刷新暂时失败");
                    }
                } else {
                    markCookieRefreshPending("缺少Cookie刷新凭据");
                }
            } else {
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.cookie_refresh, true);
            }
        } catch (JSONException | IOException e) {
            markCookieRefreshPending(e.toString());
        }
    }

    private void markCookieRefreshPending(String reason) {
        // A refresh failure does not prove that the restored Cookie is invalid.
        // Sensitive requests should be attempted and judged by the API response.
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.cookie_refresh, true);
        Log.e("Cookies", "保留现有登录信息，稍后重试刷新：" + reason);
        JSONObject details = new JSONObject();
        try {
            String category = reason != null && reason.toLowerCase().contains("network")
                    ? "network" : "api";
            details.put("category", category);
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("cookie_refresh_pending", details);
    }

    @SuppressLint("SetTextI18n")
    private void showSplashText(int i) {
        if (i > splashText.length()) splashTextView.setText(splashText);
        else splashTextView.setText(splashText.substring(0, i) + "_");
    }

    private void interruptSplash() {
        if (splashTimer != null) splashTimer.cancel();
        splashTimer = null;
        runOnUiThread(() -> splashTextView.setText(splashText));
    }
}
