package com.RobinNotBad.BiliClient.player;

public final class PlaybackVolume {
    public static final String ENABLED="player_fine_volume", GAIN="player_fine_volume_percent";
    public static final String SEEK_BUTTONS="player_show_seek_buttons", SEEK_SECONDS="player_seek_seconds";
    public static int percent(int value) { return Math.max(0,Math.min(100,value)); }
    public static int adjust(int value,int step,boolean up) { return percent(percent(value)+(up?1:-1)*Math.max(1,Math.min(30,step))); }
    public static int seekSeconds(int value) { return Math.max(1,Math.min(120,value)); }
    public static float gain(boolean enabled,int percent) { return enabled?percent(percent)/100f:1f; }
}
