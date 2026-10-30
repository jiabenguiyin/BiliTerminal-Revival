package com.RobinNotBad.BiliClient.activity.settings;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.api.AppRecommendAuthApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.concurrent.atomic.AtomicBoolean;

final class AppRecommendBindingDialog {
    private static final int QR_EXPIRED = 86038;
    private static final int QR_SCANNED = 86090;
    private static final int MAX_POLLS = 120;

    private final Activity activity;
    private final Runnable onBound;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final LinearLayout content;
    private final ImageView qrImage;
    private final TextView status;
    private final AlertDialog dialog;
    private AppRecommendAuthApi.AuthSession session;
    private int pollCount;
    private final Runnable pollTask = this::poll;

    AppRecommendBindingDialog(Activity activity, Runnable onBound) {
        this.activity = activity;
        this.onBound = onBound;

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        content.setPadding(padding, padding, padding, 0);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout qrFrame = new FrameLayout(activity);
        qrFrame.setForegroundGravity(Gravity.CENTER);
        qrImage = new ImageView(activity);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrImage.setAdjustViewBounds(true);
        qrFrame.addView(qrImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        content.addView(qrFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));

        status = new TextView(activity);
        status.setGravity(Gravity.CENTER);
        status.setText("正在获取二维码");
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        dialog = new AlertDialog.Builder(activity)
                .setTitle("绑定 App 推荐账号")
                .setView(scrollView)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            closed.set(true);
            handler.removeCallbacks(pollTask);
        });
    }

    void show() {
        MsgUtil.prepareAlertDialog(dialog);
        dialog.show();
        requestQr();
    }

    private void requestQr() {
        status.setText("正在获取二维码");
        qrImage.setImageDrawable(null);
        CenterThreadPool.run(() -> {
            try {
                AppRecommendAuthApi.AuthSession newSession = AppRecommendAuthApi.createSession();
                activity.runOnUiThread(() -> {
                    if (closed.get() || !dialog.isShowing()) return;
                    session = newSession;
                    Bitmap bitmap = newSession.qrCode;
                    qrImage.setImageBitmap(bitmap);
                    qrImage.setMaxWidth(availableQrSize());
                    qrImage.setMaxHeight(availableQrSize());
                    pollCount = 0;
                    status.setText("请使用官方哔哩哔哩 App 扫码，并在手机上确认");
                    handler.postDelayed(pollTask, 1200L);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (!closed.get() && dialog.isShowing()) {
                        status.setText("获取二维码失败，请关闭后重试");
                    }
                });
            }
        });
    }

    private void poll() {
        if (closed.get() || !dialog.isShowing() || session == null
                || !polling.compareAndSet(false, true)) return;
        if (++pollCount > MAX_POLLS) {
            polling.set(false);
            status.setText("二维码已过期，请关闭后重新获取");
            return;
        }

        AppRecommendAuthApi.AuthSession activeSession = session;
        CenterThreadPool.run(() -> {
            try {
                AppRecommendAuthApi.PollResult result = AppRecommendAuthApi.poll(activeSession);
                activity.runOnUiThread(() -> handlePollResult(result));
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    polling.set(false);
                    if (!closed.get() && dialog.isShowing()) {
                        status.setText("网络波动，正在重试登录状态");
                        handler.postDelayed(pollTask, 1800L);
                    }
                });
            }
        });
    }

    private void handlePollResult(AppRecommendAuthApi.PollResult result) {
        polling.set(false);
        if (closed.get() || !dialog.isShowing()) return;
        if (result.success) {
            long expiresIn = result.expiresIn > 0L ? result.expiresIn : 15552000L;
            SharedPreferencesUtil.putString(
                    SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY, result.accessKey);
            SharedPreferencesUtil.putLong(
                    SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY_EXPIRES_AT,
                    System.currentTimeMillis() + expiresIn * 1000L);
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_BOUND_MID, result.mid);
            status.setText("绑定成功");
            handler.postDelayed(() -> {
                if (dialog.isShowing()) dialog.dismiss();
                MsgUtil.showMsg("App 推荐账号绑定成功");
                onBound.run();
            }, 700L);
            return;
        }

        if (result.code == QR_EXPIRED) {
            status.setText("二维码已过期，请关闭后重新获取");
            return;
        }
        if (result.code == QR_SCANNED) {
            status.setText("已扫码，请在手机上确认登录");
        }
        handler.postDelayed(pollTask, 1500L);
    }

    private int dp(float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private int availableQrSize() {
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(120), Math.min(dp(280), width - dp(48)));
    }
}
