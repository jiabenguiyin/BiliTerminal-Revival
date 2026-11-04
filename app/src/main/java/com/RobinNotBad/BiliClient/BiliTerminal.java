package com.RobinNotBad.BiliClient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;
import androidx.multidex.MultiDex;

import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.api.DynamicApi;
import com.RobinNotBad.BiliClient.api.MessageApi;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.DeviceProfile;
import com.RobinNotBad.BiliClient.util.HotConfigManager;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.PlayerCompatibilityUtil;
import com.RobinNotBad.BiliClient.util.PlayerSettingsUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import com.RobinNotBad.BiliClient.util.UiConfigurationUtil;

import org.json.JSONException;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

public class BiliTerminal extends Application {

    @SuppressLint("StaticFieldLeak")
    public static Context context;

    public static boolean DPI_FORCE_CHANGE = false;

    private static WeakReference<InstanceActivity> instance = new WeakReference<>(null);
    private static volatile boolean deferredInitializationStarted;
    private static final Map<Activity, Boolean> activities = new WeakHashMap<>();

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
            AccountManager.migrateCurrentAccount();
            com.RobinNotBad.BiliClient.util.MessageUnreadCounter.initialize();
            PlayerSettingsUtil.repairStoredValues();
            UiConfigurationUtil.repairStoredValues(this);
            String savedPlayer = SharedPreferencesUtil.getString("player", "null");
            if ("mtvPlayer".equals(savedPlayer)) {
                SharedPreferencesUtil.putString("player", "terminalPlayer");
            }
            DiagnosticLogManager.initialize(this);
            DeviceProfile.init(this);
            registerDiagnosticLifecycleCallbacks();
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

            Logu.LOGV_ENABLED = SharedPreferencesUtil.getBoolean("dev_logv", true);
            Logu.LOGD_ENABLED = SharedPreferencesUtil.getBoolean("dev_logd", true);
            Logu.LOGI_ENABLED = SharedPreferencesUtil.getBoolean("dev_logi", true);

        }
    }

    private void registerDiagnosticLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                activities.put(activity, Boolean.TRUE);
                com.RobinNotBad.BiliClient.util.PageMotion.apply(activity);
                DiagnosticLogManager.recordLifecycle(activity, "created");
            }

            @Override
            public void onActivityStarted(Activity activity) {
                DiagnosticLogManager.recordLifecycle(activity, "started");
            }

            @Override
            public void onActivityResumed(Activity activity) {
                com.RobinNotBad.BiliClient.util.PageMotion.apply(activity);
                DiagnosticLogManager.recordLifecycle(activity, "resumed");
            }

            @Override
            public void onActivityPaused(Activity activity) {
                com.RobinNotBad.BiliClient.util.PageMotion.apply(activity);
                DiagnosticLogManager.recordLifecycle(activity, "paused");
            }

            @Override
            public void onActivityStopped(Activity activity) {
                DiagnosticLogManager.recordLifecycle(activity, "stopped");
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                DiagnosticLogManager.recordLifecycle(activity, "state_saved");
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                activities.remove(activity);
                com.RobinNotBad.BiliClient.util.PageMotion.forget(activity);
                DiagnosticLogManager.recordLifecycle(activity, "destroyed");
            }
        });
    }

    /** Finish the task normally so Android cannot restore an unfinished screen after a SIGKILL. */
    public static void exit(Activity caller) {
        com.RobinNotBad.BiliClient.service.DownloadService.pause();
        com.RobinNotBad.BiliClient.service.PlaybackKeepAliveService.stop(caller);
        ArrayList<Activity> open = new ArrayList<>(activities.keySet());
        if (Build.VERSION.SDK_INT >= 21) caller.finishAndRemoveTask();
        else if (Build.VERSION.SDK_INT >= 16) caller.finishAffinity();
        else caller.finish();
        for (Activity activity : open) {
            if (activity != null && !activity.isFinishing()) activity.finish();
        }
    }

    /** Starts optional network work after the first activity has rendered. */
    public static synchronized void startDeferredInitialization() {
        if (deferredInitializationStarted) return;
        deferredInitializationStarted = true;
        // Apply a previously verified relay configuration before SplashActivity
        // probes Bilibili on a separate background task.
        HotConfigManager.applyCachedConfig();
        // Do not queue unread checks behind hot-config and dynamic-feed requests.
        com.RobinNotBad.BiliClient.util.MessageUnreadCounter.refresh(false);
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

        });
    }

    public static synchronized void prepareForAccountChange() {
        deferredInitializationStarted = false;
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
        float dpiTimes = UiConfigurationUtil.normalizeScale(
                SharedPreferencesUtil.getFloat("dpi", UiConfigurationUtil.DEFAULT_SCALE));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return old;
        try {
            DisplayMetrics displayMetrics = old.getResources().getDisplayMetrics();
            Configuration configuration = new Configuration(old.getResources().getConfiguration());
            int baseDensity = Math.max(UiConfigurationUtil.MIN_DENSITY_DPI, displayMetrics.densityDpi);
            int storedDensity = SharedPreferencesUtil.getInt("density", -1);
            int shortestSide = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
            int densityBase = storedDensity >= UiConfigurationUtil.MIN_DENSITY_DPI
                    ? UiConfigurationUtil.normalizeDensity(storedDensity, baseDensity, shortestSide)
                    : baseDensity;
            int fittedDensity = UiConfigurationUtil.densityForScale(
                    dpiTimes, densityBase, shortestSide);
            if (!DPI_FORCE_CHANGE && fittedDensity == displayMetrics.densityDpi) return old;
            configuration.densityDpi = fittedDensity;
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
