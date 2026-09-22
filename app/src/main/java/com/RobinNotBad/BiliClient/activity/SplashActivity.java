package com.RobinNotBad.BiliClient.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.activity.settings.setup.SetupUIActivity;
import com.RobinNotBad.BiliClient.activity.video.RecommendActivity;
import com.RobinNotBad.BiliClient.api.AppInfoApi;
import com.RobinNotBad.BiliClient.api.CookieRefreshApi;
import com.RobinNotBad.BiliClient.api.CookiesApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StartupRoutePolicy;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

//启动页面
//一切的一切的开始

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends Activity {

    private TextView splashTextView;
    private int splashFrame;
    private Timer splashTimer;
    private String splashText = "复活版\n连接中";
    private final AtomicBoolean routeProbeRunning = new AtomicBoolean(false);
    private final AtomicBoolean startupContinued = new AtomicBoolean(false);
    private boolean routeDialogVisible = false;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handled = super.dispatchTouchEvent(event);
        DiagnosticLogManager.recordTouch(this, event);
        return handled;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        DiagnosticLogManager.recordKey(this, keyCode);
        return super.onKeyDown(keyCode, event);
    }

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

        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.setup, false)) {
            probeStartupRoute();
        } else {
            Intent intent = new Intent(this, SetupUIActivity.class);
            startActivity(intent);
            interruptSplash();
            finish();
        }
    }

    private void probeStartupRoute() {
        if (!routeProbeRunning.compareAndSet(false, true) || startupContinued.get()) return;
        splashTextView.setOnClickListener(null);
        splashTextView.setText(splashText);
        BiliTerminal.runBackgroundCompat(() -> {
            try {
                prepareBiliRouteWithRetry();
                routeProbeRunning.set(false);
                continueStartup(true);
            } catch (IOException | JSONException | RuntimeException error) {
                routeProbeRunning.set(false);
                boolean networkAvailable = hasUsableNetwork();
                runOnUiThread(() -> handleStartupFailure(error, networkAvailable));
            }
        });
    }

    private void continueStartup(boolean routeVerified) {
        if (!startupContinued.compareAndSet(false, true)) return;
        BiliTerminal.runBackgroundCompat(() -> {
            if (routeVerified) {
                if (SharedPreferencesUtil.getLong("mid", 0) != 0) checkCookieRefresh();
                try {
                    CookiesApi.checkCookies();
                } catch (Throwable error) {
                    // Cookie maintenance is optional. It must never be reclassified as a
                    // direct-connect failure or send the user back into the relay prompt.
                    Log.e("startup", "cookie maintenance failed", error);
                }
            }

            String firstActivity = resolveFirstActivity();
            Pair<String, Class<? extends InstanceActivity>> entry = MenuActivity.btnNames.get(firstActivity);
            Class<? extends InstanceActivity> activityClass = entry == null ? null : entry.second;
            Intent intent = new Intent(SplashActivity.this,
                    activityClass == null ? RecommendActivity.class : activityClass);
            intent.putExtra("from", firstActivity);

            runOnUiThread(() -> {
                if (isFinishing()) return;
                interruptSplash();
                splashTextView.postDelayed(() -> {
                    if (isFinishing()) return;
                    startActivity(intent);
                    CenterThreadPool.run(() -> AppInfoApi.check(SplashActivity.this));
                    finish();
                }, 100);
            });
        });
    }

    private String resolveFirstActivity() {
        String sortConf = SharedPreferencesUtil.getString(SharedPreferencesUtil.MENU_SORT, "");
        if (!TextUtils.isEmpty(sortConf)) {
            String[] splitName = sortConf.split(";");
            if (splitName.length > 0 && MenuActivity.btnNames.containsKey(splitName[0])) {
                return splitName[0];
            }
        }
        for (Map.Entry<String, Pair<String, Class<? extends InstanceActivity>>> entry
                : MenuActivity.btnNames.entrySet()) {
            return entry.getKey();
        }
        return "recommend";
    }

    /** Prevent a carrier-reset or DNS stall from holding the splash screen forever. */
    private void prepareBiliRouteWithRetry() throws IOException, JSONException {
        try {
            prepareBiliRouteWithTimeout(3, TimeUnit.SECONDS);
        } catch (IOException | JSONException firstError) {
            // A watch waking from overnight idle can report a live network before
            // its first socket is usable. Retry once so that transient wake-up
            // failures do not become a false startup network error.
            if (!hasUsableNetwork() || NetWorkUtil.isRelayActive()) throw firstError;
            try {
                Thread.sleep(350L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw firstError;
            }
            prepareBiliRouteWithTimeout(5, TimeUnit.SECONDS);
        }
    }

    private void prepareBiliRouteWithTimeout() throws IOException, JSONException {
        prepareBiliRouteWithTimeout(3, TimeUnit.SECONDS);
    }

    private void prepareBiliRouteWithTimeout(long timeout, TimeUnit unit)
            throws IOException, JSONException {
        FutureTask<Boolean> probe = new FutureTask<>(NetWorkUtil::prepareBiliRoute);
        Thread probeThread = new Thread(probe, "bili-startup-probe");
        probeThread.setDaemon(true);
        probeThread.start();
        try {
            probe.get(timeout, unit);
        } catch (TimeoutException e) {
            probe.cancel(true);
            throw new IOException("连接 B 站超时，可能被运营商重置或拦截", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            probe.cancel(true);
            throw new IOException("启动时连接 B 站被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof JSONException) throw (JSONException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IOException("启动时连接 B 站失败", cause);
        }
    }

    private void showBiliRelayChoice(Exception error) {
        recordStartupRouteFailure("direct", error);
        interruptSplash();
        splashTextView.setText("直连失败");
        if (!SharedPreferencesUtil.getBoolean("setup", false) || routeDialogVisible) return;
        routeDialogVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(SplashActivity.this)
                .setTitle("连接 B 站失败")
                .setMessage("当前网络可能拦截了 B 站请求。是否仅在本次启动使用复活版中继？\n\n长期只使用中继，可在设置 > 偏好设置中打开“始终使用中继”。")
                .setNegativeButton("暂不启用", (ignored, which) -> {
                    routeDialogVisible = false;
                    NetWorkUtil.disableRelayForSession();
                    continueStartup(false);
                })
                .setPositiveButton("本次启用", (ignored, which) -> {
                    routeDialogVisible = false;
                    NetWorkUtil.enableRelayForSession();
                    probeStartupRoute();
                })
                .setOnCancelListener(ignored -> {
                    routeDialogVisible = false;
                    continueStartup(false);
                })
                .create();
        dialog.setOnDismissListener(ignored -> routeDialogVisible = false);
        MsgUtil.prepareAlertDialog(dialog);
        dialog.show();
    }

    private void showRelayFailure(Exception error) {
        recordStartupRouteFailure("relay", error);
        interruptSplash();
        splashTextView.setText("中继连接失败");
        if (routeDialogVisible) return;
        routeDialogVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("中继连接失败")
                .setMessage("中继当前没有连接成功。可以重试，也可以先进入应用；不会再次弹出启用中继的提示。")
                .setNegativeButton("进入应用", (ignored, which) -> {
                    routeDialogVisible = false;
                    continueStartup(false);
                })
                .setPositiveButton("重试", (ignored, which) -> {
                    routeDialogVisible = false;
                    probeStartupRoute();
                })
                .setOnCancelListener(ignored -> {
                    routeDialogVisible = false;
                    continueStartup(false);
                })
                .create();
        dialog.setOnDismissListener(ignored -> routeDialogVisible = false);
        MsgUtil.prepareAlertDialog(dialog);
        dialog.show();
    }

    private void handleStartupFailure(Exception error, boolean networkAvailable) {
        StartupRoutePolicy.FailureAction action = StartupRoutePolicy.decideFailure(
                networkAvailable, NetWorkUtil.isRelayActive());
        switch (action) {
            case OFFLINE_CONTINUE:
                recordStartupRouteFailure("offline", error);
                interruptSplash();
                // Local cache playback must remain reachable when the device has
                // no network at all. Network-only pages can show their own error
                // later, but startup must not block the menu and cache entry.
                splashTextView.setText("离线模式");
                continueStartup(false);
                break;
            case RELAY_FAILED:
                showRelayFailure(error);
                break;
            case OFFER_RELAY:
            default:
                showBiliRelayChoice(error);
                break;
        }
    }

    private void recordStartupRouteFailure(String category, Exception error) {
        JSONObject details = new JSONObject();
        try {
            details.put("category", category);
            details.put("relay", NetWorkUtil.isRelayActive());
            details.put("error", error == null ? "unknown" : error.getClass().getSimpleName());
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("startup_route_failed", details);
        Log.e("startup", "route probe failed: " + category, error);
    }

    /** A relay cannot help when the device has no active Wi-Fi or mobile network. */
    private boolean hasUsableNetwork() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        NetworkInfo active = manager.getActiveNetworkInfo();
        boolean frameworkConnected = active != null && active.isConnected();

        // Some Android 4.x watch ROMs do not populate the active network,
        // while one of the legacy Wi-Fi/mobile entries is already connected.
        if (!frameworkConnected && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            NetworkInfo[] all = manager.getAllNetworkInfo();
            if (all != null) {
                for (NetworkInfo network : all) {
                    if (network != null && network.isConnected()) {
                        frameworkConnected = true;
                        break;
                    }
                }
            }
        }
        if (!frameworkConnected) return false;

        // ConnectivityManager can remain CONNECTED briefly after Wi-Fi/mobile data
        // is turned off on old watch ROMs. Confirm that a real non-loopback
        // interface is still up and owns a routable address before offering relay.
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return false;
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && !address.isLinkLocalAddress()) return true;
                }
            }
        } catch (SocketException error) {
            Log.w("startup", "unable to inspect active network interfaces", error);
        }
        return false;
    }

    private void checkCookieRefresh() {
        try {
            if (CookieRefreshApi.refreshIfNeeded())
                DiagnosticLogManager.record("cookie_refresh_success");
            SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.cookie_refresh, true);
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
    }
}
