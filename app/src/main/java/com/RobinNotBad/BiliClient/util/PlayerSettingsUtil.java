package com.RobinNotBad.BiliClient.util;

import java.util.Map;

/** Keeps player settings independent and repairs values written by older builds. */
public final class PlayerSettingsUtil {
    private static final String DOUBLE_TAP_MODE = "player_double_tap_mode";
    private static final String LEGACY_DOUBLE_TAP_PAUSE_FIRST = "player_double_tap_pause_first";
    private static final String INTERACTION_SCALE = "player_interaction_choice_scale";
    private static final String DANMAKU_MAX_LINE = "player_danmaku_maxline";
    private static final String DANMAKU_SIZE = "player_danmaku_size";
    private static final String DANMAKU_TRANSPARENCY = "player_danmaku_transparency";
    private static final String DANMAKU_SPEED = "player_danmaku_speed";

    private PlayerSettingsUtil() {
    }

    public static void repairStoredValues() {
        if (SharedPreferencesUtil.getSharedPreferences() == null) return;
        migrateDoubleTapMode();
        writeFloatIfInvalid(INTERACTION_SCALE, 0.7f, 0.5f, 1.0f);
        writeIntIfInvalid(DANMAKU_MAX_LINE, 10, 1, 100);
        writeFloatIfInvalid(DANMAKU_SIZE, 0.7f, 0.1f, 3.0f);
        writeFloatIfInvalid(DANMAKU_TRANSPARENCY, 0.5f, 0.0f, 1.0f);
        writeFloatIfInvalid(DANMAKU_SPEED, 1.0f, 0.1f, 5.0f);
    }

    /** Migrates the old boolean without changing the user's existing behavior. */
    private static void migrateDoubleTapMode() {
        if (SharedPreferencesUtil.getSharedPreferences().contains(DOUBLE_TAP_MODE)) return;
        boolean pauseFirst = SharedPreferencesUtil.getBoolean(LEGACY_DOUBLE_TAP_PAUSE_FIRST, false);
        SharedPreferencesUtil.putInt(DOUBLE_TAP_MODE, pauseFirst ? 0 : 1);
    }

    public static int getDoubleTapMode() {
        migrateDoubleTapMode();
        int mode = SharedPreferencesUtil.getInt(DOUBLE_TAP_MODE, 1);
        return mode >= 0 && mode <= 2 ? mode : 1;
    }

    public static float getInteractionChoiceScale() {
        return normalizeFloat(INTERACTION_SCALE, readFloatValue(INTERACTION_SCALE, 0.7f));
    }

    public static int getDanmakuMaxLine() {
        return getInt(DANMAKU_MAX_LINE, 10, 1, 100);
    }

    public static float getDanmakuSize() {
        return normalizeFloat(DANMAKU_SIZE, readFloatValue(DANMAKU_SIZE, 0.7f));
    }

    public static float getDanmakuTransparency() {
        return normalizeFloat(DANMAKU_TRANSPARENCY, readFloatValue(DANMAKU_TRANSPARENCY, 0.5f));
    }

    public static float getDanmakuSpeed() {
        return normalizeFloat(DANMAKU_SPEED, readFloatValue(DANMAKU_SPEED, 1.0f));
    }

    public static float normalizeFloat(String key, float value) {
        float fallback;
        float min;
        float max;
        if (INTERACTION_SCALE.equals(key)) {
            fallback = 0.7f;
            min = 0.5f;
            max = 1.0f;
        } else if (DANMAKU_SIZE.equals(key)) {
            fallback = 0.7f;
            min = 0.1f;
            max = 3.0f;
        } else if (DANMAKU_TRANSPARENCY.equals(key)) {
            fallback = 0.5f;
            min = 0.0f;
            max = 1.0f;
        } else if (DANMAKU_SPEED.equals(key)) {
            fallback = 1.0f;
            min = 0.1f;
            max = 5.0f;
        } else {
            return value;
        }
        return !Float.isNaN(value) && !Float.isInfinite(value) && value >= min && value <= max
                ? value : fallback;
    }

    private static int getInt(String key, int fallback, int min, int max) {
        Integer value = readInt(key);
        return value != null && value >= min && value <= max ? value : fallback;
    }

    private static void writeFloatIfInvalid(String key, float fallback, float min, float max) {
        Float value = readFloat(key);
        if (value == null || Float.isNaN(value) || Float.isInfinite(value)
                || value < min || value > max) {
            SharedPreferencesUtil.putFloat(key, fallback);
        } else if (!(SharedPreferencesUtil.getSharedPreferences().getAll().get(key) instanceof Float)) {
            SharedPreferencesUtil.putFloat(key, value);
        }
    }

    private static void writeIntIfInvalid(String key, int fallback, int min, int max) {
        Integer value = readInt(key);
        if (value == null || value < min || value > max) {
            SharedPreferencesUtil.putInt(key, fallback);
        } else if (!(SharedPreferencesUtil.getSharedPreferences().getAll().get(key) instanceof Integer)) {
            SharedPreferencesUtil.putInt(key, value);
        }
    }

    private static Float readFloat(String key) {
        Map<String, ?> all = SharedPreferencesUtil.getSharedPreferences().getAll();
        Object raw = all.get(key);
        if (raw instanceof Number) return ((Number) raw).floatValue();
        if (raw instanceof String) {
            try {
                return Float.parseFloat((String) raw);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Integer readInt(String key) {
        Map<String, ?> all = SharedPreferencesUtil.getSharedPreferences().getAll();
        Object raw = all.get(key);
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw instanceof String) {
            try {
                return Integer.parseInt((String) raw);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static float readFloatValue(String key, float fallback) {
        Float value = readFloat(key);
        return value == null ? fallback : value;
    }
}
