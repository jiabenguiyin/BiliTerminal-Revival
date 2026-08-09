package com.RobinNotBad.BiliClient.util;

public final class BackgroundPlaybackPolicy {
    private BackgroundPlaybackPolicy() {
    }

    public static boolean shouldContinue(boolean settingEnabled,
                                         boolean audioOnlyMode,
                                         boolean playing,
                                         boolean userLeaving,
                                         boolean screenOff) {
        return (settingEnabled || audioOnlyMode)
                && playing
                && (userLeaving || screenOff);
    }
}
