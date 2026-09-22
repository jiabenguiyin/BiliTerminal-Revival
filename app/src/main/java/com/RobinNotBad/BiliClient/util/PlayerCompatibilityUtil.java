package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import java.util.Locale;

public final class PlayerCompatibilityUtil {
    private PlayerCompatibilityUtil() {
    }

    public static boolean prefersSoftwareCodec() {
        return prefersSoftwareCodec(Build.MANUFACTURER, Build.BRAND, Build.DISPLAY);
    }

    /**
     * The source tree contains empty MIPS placeholders and no armeabi-only
     * decoder libraries. Use the platform player on those devices instead of
     * letting System.loadLibrary crash the process.
     */
    public static boolean shouldUsePlatformMediaPlayer() {
        return shouldUsePlatformMediaPlayer(Build.CPU_ABI, Build.CPU_ABI2);
    }

    static boolean shouldUsePlatformMediaPlayer(String primaryAbi, String secondaryAbi) {
        return !isSupportedNativeAbi(primaryAbi) && !isSupportedNativeAbi(secondaryAbi);
    }

    private static boolean isSupportedNativeAbi(String abi) {
        return "armeabi-v7a".equalsIgnoreCase(abi) || "x86".equalsIgnoreCase(abi);
    }

    public static boolean shouldReduceLongPressLoad(int sdkInt, int memoryClassMb) {
        return sdkInt <= Build.VERSION_CODES.KITKAT || (memoryClassMb > 0 && memoryClassMb <= 192);
    }

    public static float longPressSpeed(int sdkInt, int memoryClassMb) {
        return shouldReduceLongPressLoad(sdkInt, memoryClassMb) ? 2.0f : 3.0f;
    }

    public static boolean supportsHighFrameRate(Context context) {
        if (context == null) return false;
        try {
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = manager == null ? null : manager.getDefaultDisplay();
            return display != null && supportsHighFrameRate(display.getRefreshRate());
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean supportsHighFrameRate(float refreshRate) {
        return refreshRate >= 59.0f;
    }

    static boolean prefersSoftwareCodec(String manufacturer, String brand, String display) {
        String deviceIdentity = ((manufacturer == null ? "" : manufacturer) + " "
                + (brand == null ? "" : brand) + " "
                + (display == null ? "" : display)).toLowerCase(Locale.US);
        return deviceIdentity.contains("huawei") || deviceIdentity.contains("harmony");
    }
}
