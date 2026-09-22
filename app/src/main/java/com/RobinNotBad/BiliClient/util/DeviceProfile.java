package com.RobinNotBad.BiliClient.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.RobinNotBad.BiliClient.model.DashData;

/**
 * 设备能力三档分级（1.1.6 新增）
 *
 * 目标：当前发布版本统一走稳定的单播放器路径，避免视频轨和音频轨分离后在
 * 倍速、拖动、暂停组合操作下出现严重时钟失步。设备档位仍保留，用于其他兼容
 * 性策略和后续重新启用高性能方案时的能力判断。
 * 所有播放器/API 层的差异化决策统一走这里，避免散落判断。
 *
 * 档位：
 *   COMPAT   兼容档 —— Android 4.x 或内存 ≤128MB 或 双核低内存：软解优先、durl 单播放器、
 *                      自动画质 720P、不请求 4K、放宽超时
 *   BALANCED 均衡档 —— 中端能力判断（当前仍使用单播放器）
 *   HIGH_END 高性能档 —— 高端能力判断（当前仍使用单播放器）
 */
public final class DeviceProfile {

    public static final int OVERRIDE_AUTO = 0;
    public static final int OVERRIDE_COMPAT = 1;
    public static final int OVERRIDE_HIGH_END = 2;

    public enum Tier {
        /** 低端：保稳定优先 */
        COMPAT,
        /** 中端：功能与性能均衡 */
        BALANCED,
        /** 高端：完整能力不限制 */
        HIGH_END
    }

    private static volatile Tier cachedTier = null;

    private DeviceProfile() {
    }

    /** 应用启动时调用一次（BiliTerminal.onCreate），缓存检测结果。 */
    public static void init(Context context) {
        cachedTier = detect(context);
    }

    public static Tier get() {
        Tier override = getOverride();
        if (override != null) return override;
        Tier tier = cachedTier;
        if (tier == null) {
            tier = detectWithoutContext();
            cachedTier = tier;
        }
        return tier;
    }

    private static Tier getOverride() {
        if (SharedPreferencesUtil.sharedPreferences == null) return null;
        switch (SharedPreferencesUtil.getInt(SharedPreferencesUtil.DEVICE_PROFILE_OVERRIDE,
                OVERRIDE_AUTO)) {
            case OVERRIDE_COMPAT:
                return Tier.COMPAT;
            case OVERRIDE_HIGH_END:
                return Tier.HIGH_END;
            default:
                return null;
        }
    }

    private static Tier detect(Context context) {
        int sdk = Build.VERSION.SDK_INT;
        int memClassMb = 0;
        int cores = Runtime.getRuntime().availableProcessors();
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                memClassMb = am.getMemoryClass();
            }
        } catch (Throwable ignored) {
            // 检测失败时按 SDK 判定
        }
        boolean watch = false;
        try {
            watch = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        } catch (Throwable ignored) {
        }
        return classify(sdk, memClassMb, cores, watch);
    }

    private static Tier detectWithoutContext() {
        return classify(Build.VERSION.SDK_INT, 0, Runtime.getRuntime().availableProcessors());
    }

    static Tier classify(int sdk, int memClassMb, int cores) {
        return classify(sdk, memClassMb, cores, false);
    }

    static Tier classify(int sdk, int memClassMb, int cores, boolean watch) {
        // Android 8.1 watch hardware is especially sensitive to two decoders,
        // two network buffers and the extra synchronization thread.
        if (watch) return Tier.COMPAT;
        // Android 4.x 一律兼容档：老设备硬解兼容差、内存小、跑不动双播放器
        if (sdk <= Build.VERSION_CODES.KITKAT) return Tier.COMPAT;
        // 低内存设备（老安卓 5/6/7 手表、低配机）归兼容档
        if (memClassMb > 0 && memClassMb <= 128) return Tier.COMPAT;
        if (memClassMb > 0 && memClassMb <= 192 && cores > 0 && cores <= 2) return Tier.COMPAT;
        // 高端必须有明确的内存证据。部分手表 ROM 的 getMemoryClass() 会失败，
        // 不能因为“未知”就开启 4K、双播放器和高码率音频。
        if (sdk >= Build.VERSION_CODES.Q && memClassMb >= 512) return Tier.HIGH_END;
        if (sdk >= Build.VERSION_CODES.O && memClassMb >= 256) return Tier.HIGH_END;
        // 中端：Android 5+ 且内存 ≥192MB；无法检测内存时保守按均衡档，
        // 这样仍可正常播放，但不会误开放高端档能力。
        if (sdk >= Build.VERSION_CODES.LOLLIPOP
                && (memClassMb <= 0 || memClassMb >= 192)) return Tier.BALANCED;
        return Tier.COMPAT;
    }

    // ==================== 决策方法 ====================

    /** 用户手动画质请求上限。兼容档默认仍是 720P，但允许用户明确选择普通 1080P。 */
    public static int maxQuality() {
        switch (get()) {
            case COMPAT:
                return DashData.QN_1080P;
            case BALANCED:
                return DashData.QN_1080P;
            case HIGH_END:
            default:
                return DashData.QN_4K;     // 高配不限制
        }
    }

    /** 自动档（play_qn=0）默认请求画质。 */
    public static int autoQuality() {
        switch (get()) {
            case COMPAT:
                return DashData.QN_720P;   // 兼容单播放器优先 720P，不可用时由接口回退 360P
            case BALANCED:
                return DashData.QN_720P;
            case HIGH_END:
            default:
                return DashData.QN_1080P;
        }
    }

    /** COMPAT 档关闭 DASH 双播放器（视频+音频分离），走 durl 单播放器，避免老设备解码/内存翻倍。 */
    public static boolean useDashDualPlayer() {
        // 2026-08-31 stability fallback: the dual-clock implementation can
        // drift badly on speed/seek/pause combinations even on fast phones.
        return false;
    }

    /** High-quality DASH path: one ExoPlayer timeline owns both tracks. */
    public static boolean useUnifiedDashPlayer() {
        // ExoPlayer 2.18 requires API 16. Android 4.0/4.0.3 keeps the
        // original IJK/AndroidMediaPlayer path.
        return get() != Tier.COMPAT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    /** COMPAT 档默认软件解码（硬解在老设备上失败率高：-10000）。 */
    public static boolean defaultSoftwareCodec() {
        return get() == Tier.COMPAT;
    }

    /** 是否请求 4K 顶层档（只有高端档需要，降低低端响应体积与解析负担）。 */
    public static boolean requestTopTier4k() {
        return get() == Tier.HIGH_END;
    }

    /** prepare 超时：兼容档放宽到 30s，其余 20s。 */
    public static int prepareTimeoutMs() {
        return get() == Tier.COMPAT ? 30000 : 20000;
    }

    /** 首源连接超时（秒）：兼容档 5s 快速失败切备用源，其余 8s。 */
    public static int connectTimeoutSec() {
        return get() == Tier.COMPAT ? 5 : 8;
    }

    /** 最大在线播放缓冲（字节）：兼容档保留适度余量，避免网络抖动造成音频断续。 */
    public static int maxBufferBytes() {
        return get() == Tier.COMPAT ? 6 * 1024 * 1024 : 15 * 1024 * 1024;
    }

    /** 在线点播缓存时长（毫秒）。直播不使用这个值。 */
    public static int maxCachedDurationMs() {
        return get() == Tier.COMPAT ? 6000 : 12000;
    }

    /** IJK 开始播放前至少缓存的帧数。 */
    public static int minBufferedFrames() {
        return get() == Tier.COMPAT ? 5 : 8;
    }

    /** 诊断上报节流：兼容档每批最多上报条数（0 表示不限制）。 */
    public static int diagnosticBatchLimit() {
        return get() == Tier.COMPAT ? 200 : 0;
    }
}
