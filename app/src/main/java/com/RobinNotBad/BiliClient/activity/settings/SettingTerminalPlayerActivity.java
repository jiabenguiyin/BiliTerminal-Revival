package com.RobinNotBad.BiliClient.activity.settings;

import android.os.Build;
import android.os.Bundle;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter;
import com.RobinNotBad.BiliClient.model.SettingSection;
import com.RobinNotBad.BiliClient.util.PlayerCompatibilityUtil;
import com.RobinNotBad.BiliClient.util.PlayerSettingsUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.List;

public class SettingTerminalPlayerActivity extends RefreshListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageName("内置播放器设置");

        final List<SettingSection> sectionList = new ArrayList<>() {
            {
                add(new SettingSection("switch", "长按倍速", "player_longclick", "", "true"));
                add(new SettingSection("switch",getString(R.string.longpress_hint_enabled),PlayerSettingsUtil.LONGPRESS_HINT_ENABLED,
                        getString(R.string.longpress_hint_enabled_desc),"true"));
                add(new SettingSection("input_float",getString(R.string.longpress_hint_scale),PlayerSettingsUtil.LONGPRESS_HINT_SCALE,
                        getString(R.string.longpress_hint_scale_desc),"0.75"));
                add(new SettingSection("switch", getString(R.string.player_longpress_switch), SharedPreferencesUtil.PLAYER_LONGPRESS_SWITCH,
                        getString(R.string.player_longpress_switch_desc), "true"));
                add(new SettingSection("switch", "洗脑循环", "player_loop", "", "false"));
                add(new SettingSection("switch", "普通播放允许后台", "player_background",
                        "控制普通视频和直播；听视频、听直播始终支持主页键与熄屏播放",
                        "false"));
                add(new SettingSection("switch", "默认横屏", "player_autolandscape", "", "false"));
                add(new SettingSection("switch", "从历史位置播放", "player_from_last",
                        getString(R.string.desc_fromlast),
                        "true"));
                add(new SettingSection("choose3", getString(R.string.pgc_skip_mode),
                        com.RobinNotBad.BiliClient.player.PlaybackSegments.MODE,
                        getString(R.string.pgc_skip_desc), "1", new String[]{"关闭", "手动跳过", "自动跳过一次"}));
                add(new SettingSection("switch", getString(R.string.fine_volume),
                        com.RobinNotBad.BiliClient.player.PlaybackVolume.ENABLED, getString(R.string.fine_volume_desc), "false"));
                add(new SettingSection("switch", getString(R.string.seek_buttons),
                        com.RobinNotBad.BiliClient.player.PlaybackVolume.SEEK_BUTTONS, getString(R.string.seek_buttons_desc), "false"));
                add(new SettingSection("input_int", getString(R.string.seek_seconds),
                        com.RobinNotBad.BiliClient.player.PlaybackVolume.SEEK_SECONDS, getString(R.string.seek_seconds_desc), "10"));
                add(new SettingSection("switch", "显示实时人数", "player_show_online",
                        getString(R.string.desc_showonline),
                        "false"));
                add(new SettingSection("switch", "听视频模式", "player_audio_only",
                        getString(R.string.desc_audio_only), "false"));
                add(new SettingSection("switch", "视频可缩放", "player_scale",
                        getString(R.string.desc_scale), "true"));
                add(new SettingSection("switch", "左右滑动调节进度", SharedPreferencesUtil.PLAYER_SWIPE_SEEK,
                        getString(R.string.desc_swipe_seek), "false"));
                add(new SettingSection("input_int", "音量调节步进（百分比）", SharedPreferencesUtil.PLAYER_VOLUME_STEP,
                        getString(R.string.desc_volume_step), "5"));
                add(new SettingSection("switch", "左右上下滑动调节亮度/音量", SharedPreferencesUtil.PLAYER_BRIGHTNESS_GESTURE,
                        getString(R.string.desc_brightness_gesture), "true"));
                add(new SettingSection("switch", "显示屏幕锁定按钮", SharedPreferencesUtil.PLAYER_SCREEN_LOCK,
                        "防止误触播放控制和手势。锁定后轻点画面隐藏或显示锁图标，点击锁图标解锁", "true"));
                add(new SettingSection("switch", "手势防误触", SharedPreferencesUtil.PLAYER_ANTI_MISTOUCH,
                        "开启后需要更大的移动距离才会触发手势，减少误触；关闭后恢复正常灵敏度", "true"));
                add(new SettingSection("choose3", "双击优先操作", "player_double_tap_mode",
                        getString(R.string.desc_double_tap_action), "1",
                        new String[]{"暂停/继续播放", "复原画面", "放大时复原，否则暂停"}));
                add(new SettingSection("input_float", "互动视频选项按钮大小", "player_interaction_choice_scale",
                        getString(R.string.desc_interaction_choice_scale), "0.7"));
                add(new SettingSection("switch", "缩放时可移动", "player_doublemove",
                        getString(R.string.desc_doublemove),
                        "true"));
                add(new SettingSection("choose", "缩放后移动方式", "player_singlemove",
                        getString(R.string.desc_move_mode), "true",
                        new String[]{"单指移动", "双指移动"}));
                add(new SettingSection("switch", "放大后单击切换移动/进度", SharedPreferencesUtil.PLAYER_ZOOM_TAP_SEEK,
                        getString(R.string.desc_zoom_tap_seek), "false"));

                add(new SettingSection("divider", "", "", "", ""));

                add(new SettingSection("choose", "显示方式", "player_display",
                        getString(R.string.desc_display),
                        String.valueOf(Build.VERSION.SDK_INT < 26),
                        new String[]{"TextureView", "SurfaceView"}));
                add(new SettingSection("choose", "解码方式", "player_codec",
                        getString(R.string.desc_videocodec),
                        String.valueOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                                && !PlayerCompatibilityUtil.prefersSoftwareCodec()),
                        new String[]{"硬件解码", "软件解码"}));
                add(new SettingSection("choose", "音频输出", "player_audio",
                        getString(R.string.desc_audiocodec), "false",
                        new String[]{"OpenSles", "AudioTrack"}));

                add(new SettingSection("divider", "", "", "", ""));

                add(new SettingSection("switch", "显示高能进度条", "player_high_energy",
                        getString(R.string.desc_player_high_energy), "false"));
                add(new SettingSection("switch", "弹幕允许重叠", "player_danmaku_allowoverlap", "", "true"));
                add(new SettingSection("switch", "大弹幕量保护（5000条）", SharedPreferencesUtil.PLAYER_LARGE_DANMAKU_GUARD,
                        getString(R.string.desc_large_danmaku_guard), "false"));
                add(new SettingSection("switch", "合并重复弹幕", "player_danmaku_mergeduplicate", "",
                        "false"));
                add(new SettingSection("switch", "强制为滚动弹幕", "player_danmaku_forceR2L",
                        getString(R.string.desc_danmaku_force_r2l), "false"));
                add(new SettingSection("switch", "显示直播弹幕发送者", "player_danmaku_showsender",
                        getString(R.string.desc_danmaku_showsender), "true"));
                add(new SettingSection("input_int", "弹幕最大行数", "player_danmaku_maxline", "", "10"));
                add(new SettingSection("input_float", "弹幕字号大小", "player_danmaku_size", "", "0.7"));
                add(new SettingSection("input_float", "弹幕不透明度", "player_danmaku_transparency", "",
                        "0.5"));
                add(new SettingSection("input_float", "视频弹幕速度", "player_danmaku_speed", "", "1.0"));
                add(new SettingSection("input_float", "直播弹幕停留倍率",
                        PlayerSettingsUtil.LIVE_DANMAKU_DURATION_FACTOR,
                        "范围1～3，默认2倍时长。数值越大滚动越慢；1为原速。仅影响直播滚动弹幕。", "2.0"));

                add(new SettingSection("divider", "", "", "", ""));

                add(new SettingSection("switch", "自动弹出字幕选择", "player_subtitle_autoshow",
                        getString(R.string.desc_subtitle_autoshow), "true"));
                add(new SettingSection("switch", "允许仅AI字幕", "player_subtitle_ai_allowed",
                        getString(R.string.desc_subtitle_ai_allowed), "false"));

                add(new SettingSection("divider", "", "", "", ""));

                add(new SettingSection("input_float", "字幕校准", "player_subtitle_delta",
                        "将字幕提前/退后一段时间，从而与视频对齐", "0.3"));

                add(new SettingSection("divider", "", "", "", ""));

                add(new SettingSection("switch", "显示旋转按钮", "player_ui_showRotateBtn", "", "true"));
                add(new SettingSection("switch", "显示弹幕按钮", "player_ui_showDanmakuBtn", "", "true"));
                add(new SettingSection("switch", getString(R.string.collection_playback_show),
                        SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK,
                        getString(R.string.collection_playback_desc), "false"));
                add(new SettingSection("choose3", "缓存连播范围", SharedPreferencesUtil.PLAYER_CACHED_PLAYBACK_SCOPE,
                        "当前合集只播已缓存条目，旧缓存按当前分P播放；全部缓存可作为歌单。不会联网补播。更改后从缓存列表重新打开播放生效。", "0",
                        new String[]{"当前合集/分P", "全部缓存"}));
                add(new SettingSection("switch", "显示清晰度按钮", "player_ui_showQualityBtn",
                        "", "true"));
                add(new SettingSection("switch", "显示分P按钮", "player_ui_showPageBtn",
                        "", "true"));
            }
        };

        recyclerView.setHasFixedSize(true);

        SettingsAdapter adapter = new SettingsAdapter(this, sectionList);
        setAdapter(adapter);

        setRefreshing(false);

    }

}
