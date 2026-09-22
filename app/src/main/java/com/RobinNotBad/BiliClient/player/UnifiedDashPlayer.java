package com.RobinNotBad.BiliClient.player;

import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;

/** Common hooks used by the unified DASH player without exposing its runtime dependency. */
public interface UnifiedDashPlayer {
    void setPlaybackSpeed(float speed);

    void setAudioSource(String url, Map<String, String> headers);
}
