package com.RobinNotBad.BiliClient.player;

import android.content.Context;

import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * Loads the API 16+ DASH implementation only when it is safe to do so.
 * Keeping the implementation behind reflection prevents class verification on
 * Android 4.0 devices that must continue using the legacy player.
 */
public final class UnifiedDashPlayerFactory {
    private static final String IMPLEMENTATION =
            "com.RobinNotBad.BiliClient.player.ExoDashMediaPlayer";

    private UnifiedDashPlayerFactory() {
    }

    public static IMediaPlayer create(Context context, String audioUrl) {
        try {
            Class<?> type = Class.forName(IMPLEMENTATION);
            Object value = type.getConstructor(Context.class, String.class)
                    .newInstance(context, audioUrl);
            return value instanceof IMediaPlayer ? (IMediaPlayer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
