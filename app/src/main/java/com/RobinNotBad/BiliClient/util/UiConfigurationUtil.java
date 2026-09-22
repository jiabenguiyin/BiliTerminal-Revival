package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.util.DisplayMetrics;

/** Bounds user-controlled display settings so compact screens keep usable controls. */
public final class UiConfigurationUtil {
    public static final float DEFAULT_SCALE = 1.0f;
    public static final float MIN_SCALE = 0.25f;
    public static final float MAX_SCALE = 2.0f;
    public static final int MAX_PADDING_PERCENT = 20;
    public static final int MIN_DENSITY_DPI = 72;
    // Keep the stock density on compact watches. The old 220dp cap reduced
    // every density above ~0.7x to the same value on a 408px display.
    public static final int MIN_SHORTEST_WIDTH_DP = 120;

    private UiConfigurationUtil() {
    }

    public static float normalizeScale(float value) {
        return isFinite(value) && value >= MIN_SCALE && value <= MAX_SCALE
                ? value : DEFAULT_SCALE;
    }

    public static int normalizePadding(int value) {
        return Math.max(0, Math.min(MAX_PADDING_PERCENT, value));
    }

    public static int normalizeDensity(int value, int baseDensityDpi) {
        return normalizeDensity(value, baseDensityDpi, Integer.MAX_VALUE);
    }

    public static int normalizeDensity(int value, int baseDensityDpi, int shortestSidePx) {
        if (value < MIN_DENSITY_DPI) return -1;
        int base = Math.max(MIN_DENSITY_DPI, baseDensityDpi);
        int screenMax = maxDensityForScreen(shortestSidePx);
        int max = Math.max(MIN_DENSITY_DPI,
                Math.min(screenMax, Math.min(640, Math.round(base * 1.5f))));
        return Math.max(MIN_DENSITY_DPI, Math.min(max, value));
    }

    public static int densityForScale(float scale, int baseDensityDpi) {
        return densityForScale(scale, baseDensityDpi, Integer.MAX_VALUE);
    }

    public static int densityForScale(float scale, int baseDensityDpi, int shortestSidePx) {
        int base = Math.max(MIN_DENSITY_DPI, baseDensityDpi);
        int scaled = Math.round(base * normalizeScale(scale));
        int screenMax = maxDensityForScreen(shortestSidePx);
        int max = Math.max(MIN_DENSITY_DPI,
                Math.min(screenMax, Math.min(640, Math.round(base * MAX_SCALE))));
        return Math.max(MIN_DENSITY_DPI, Math.min(max, scaled));
    }

    public static int normalizeDensityForContext(int value, Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return normalizeDensity(value, metrics.densityDpi,
                Math.min(metrics.widthPixels, metrics.heightPixels));
    }

    public static int getBaseDensityDpi(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.max(MIN_DENSITY_DPI, metrics.densityDpi);
    }

    public static void repairStoredValues(Context context) {
        if (SharedPreferencesUtil.getSharedPreferences() == null) return;

        float scale = SharedPreferencesUtil.getFloat("dpi", DEFAULT_SCALE);
        SharedPreferencesUtil.putFloat("dpi", normalizeScale(scale));

        int horizontal = SharedPreferencesUtil.getInt("paddingH_percent", 0);
        int vertical = SharedPreferencesUtil.getInt("paddingV_percent", 0);
        SharedPreferencesUtil.putInt("paddingH_percent", normalizePadding(horizontal));
        SharedPreferencesUtil.putInt("paddingV_percent", normalizePadding(vertical));

        int density = SharedPreferencesUtil.getInt("density", -1);
        if (density >= 0) {
            SharedPreferencesUtil.putInt("density", normalizeDensityForContext(density, context));
        }
    }

    private static int maxDensityForScreen(int shortestSidePx) {
        if (shortestSidePx <= 0 || shortestSidePx == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(MIN_DENSITY_DPI,
                shortestSidePx * DisplayMetrics.DENSITY_DEFAULT / MIN_SHORTEST_WIDTH_DP);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
