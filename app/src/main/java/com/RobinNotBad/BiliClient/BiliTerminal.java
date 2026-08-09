package com.RobinNotBad.BiliClient;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;
import androidx.multidex.MultiDex;

import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.api.DynamicApi;
import com.RobinNotBad.BiliClient.api.MessageApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.HotConfigManager;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.PlayerCompatibilityUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;

import org.json.JSONException;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class BiliTerminal extends Application {

    @SuppressLint("StaticFieldLeak")
    public static Context context;

    public static boolean DPI_FORCE_CHANGE = false;

    private static WeakReference<InstanceActivity> instance = new WeakReference<>(null);
    private static volatile boolean deferredInitializationStarted;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (context == null) {
            context = this;
            ErrorCatch.getInstance().init(context);
            SharedPreferencesUtil.sharedPreferences = getSharedPreferences("default", MODE_PRIVATE);
            DiagnosticLogManager.initialize(this);
            if (!SharedPreferencesUtil.sharedPreferences.contains("player_background_opt_in_migrated")) {
                SharedPreferencesUtil.sharedPreferences.edit()
                        .putBoolean("player_background", false)
                        .putBoolean("player_background_opt_in_migrated", true)
                        .apply();
            }
            if (PlayerCompatibilityUtil.prefersSoftwareCodec()
                    && !SharedPreferencesUtil.sharedPreferences.contains("player_huawei_software_codec_migrated")) {
                SharedPreferences.Editor editor = SharedPreferencesUtil.sharedPreferences.edit();
                if (!SharedPreferencesUtil.sharedPreferences.contains("player_codec")) {
                    editor.putBoolean("player_codec", false);
                }
                editor.putBoolean("player_huawei_software_codec_migrated", true).apply();
            }
            context = getFitDisplayContext(this);

            boolean debugBuild = isDebugBuild();
            Logu.LOGV_ENABLED = SharedPreferencesUtil.getBoolean("dev_logv", debugBuild);
            Logu.LOGD_ENABLED = SharedPreferencesUtil.getBoolean("dev_logd", debugBuild);
            Logu.LOGI_ENABLED = SharedPreferencesUtil.getBoolean("dev_logi", debugBuild);

        }
    }

    /** Starts optional network work after the first activity has rendered. */
    public static synchronized void startDeferredInitialization() {
        if (deferredInitializationStarted) return;
        deferredInitializationStarted = true;
        runBackgroundCompat(() -> {
            try {
                HotConfigManager.initialize();
            } catch (Throwable error) {
                Logu.e("startup", "hot config initialization failed: " + error);
            }

            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.DYNAMIC_UPDATE_CHECK_ENABLE, true)
                    && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0) {
                try {
                    long updateBaseline = SharedPreferencesUtil.getLong("dynamic_update_baseline", 0);
                    int updateNum = DynamicApi.checkDynamicUpdate("all", updateBaseline);
                    SharedPreferencesUtil.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, updateNum);
                } catch (IOException | JSONException e) {
                    SharedPreferencesUtil.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0);
                }
            }

            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE, true)
                    && SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0) {
                try {
                    int messageUnread = MessageApi.checkMessageUnread();
                    int privateMsgUnread = MessageApi.checkPrivateMsgUnread();
                    SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, messageUnread + privateMsgUnread);
                } catch (IOException | JSONException e) {
                    SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0);
                }
            }
        });
    }

    /** Keeps old Dalvik startup independent from coroutine initialization. */
    public static void runBackgroundCompat(Runnable runnable) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            new Thread(runnable, "bili-legacy-startup").start();
        } else {
            CenterThreadPool.run(runnable);
        }
    }

    public static void setInstance(InstanceActivity instanceActivity) {
        instance = new WeakReference<>(instanceActivity);
    }

    @Nullable
    public static InstanceActivity getInstanceActivityOnTop() {
        return instance.get();
    }

    /**
     * 重写attachBaseContext方法，用于调整应用内dpi
     * 尝试下这种风格代码是否会导致低版本设备异常
     *
     * @param old The origin context.
     */
    public static Context getFitDisplayContext(Context old) {
        float dpiTimes = SharedPreferencesUtil.getFloat("dpi", 1.0F);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return old;
        if (!DPI_FORCE_CHANGE && dpiTimes == 1.0F) return old;
        try {
            DisplayMetrics displayMetrics = old.getResources().getDisplayMetrics();
            Configuration configuration = old.getResources().getConfiguration();
            configuration.densityDpi = (int) (displayMetrics.densityDpi * dpiTimes);
            return old.createConfigurationContext(configuration);
        } catch (Exception e) {
            //MsgUtil.err(e,old);
            return old;
        }
    }

    public static int getVersion() throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
    }

    public static boolean isDebugBuild() {
        return "debug".equals(BuildConfig.BUILD_TYPE);
    }

    public static void jumpToVideo(Context context, long aid) {
        TerminalContext.getInstance().enterVideoDetailPage(context, aid);
    }

    public static void jumpToVideo(Context context, String bvid) {
        TerminalContext.getInstance().enterVideoDetailPage(context, bvid);
    }

    public static void jumpToArticle(Context context, long cvid) {
        TerminalContext.getInstance().enterArticleDetailPage(context, cvid);
    }

    public static void jumpToUser(Context context, long mid) {
        Intent intent = new Intent();
        intent.setClass(context, UserInfoActivity.class);
        intent.putExtra("mid", mid);
        context.startActivity(intent);
    }

}
