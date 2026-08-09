package com.RobinNotBad.BiliClient.api;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.player.PlayerActivity;
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity;
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity;
import com.RobinNotBad.BiliClient.model.DashAudioStream;
import com.RobinNotBad.BiliClient.model.DashData;
import com.RobinNotBad.BiliClient.model.DashVideoStream;
import com.RobinNotBad.BiliClient.model.HighEnergyData;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.Subtitle;
import com.RobinNotBad.BiliClient.model.SubtitleLink;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.service.DownloadService;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PlayerApi {
    private static final long PLAY_URL_CACHE_MS = 10 * 60 * 1000L;

    public static void startGettingUrl(PlayerData playerData) {
        Context context = BiliTerminal.context;

        Intent intent = new Intent()
                .setClass(context, JumpToPlayerActivity.class)
                .putExtra("data", playerData)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startDownloading(VideoInfo videoInfo, int page, int qn) {
        if (SharedPreferencesUtil.getBoolean("dev_download_old", false)) {
            Context context = BiliTerminal.context;

            Intent intent = new Intent(context, JumpToPlayerActivity.class)
                    .putExtra("data", videoInfo.toPlayerData(page))
                    .putExtra("download", (videoInfo.pagenames.size() == 1 ? 1 : 2)) // 1：单页 2：分页
                    .putExtra("cover", videoInfo.cover)
                    .putExtra("parent_title", videoInfo.title)
                    .putExtra("qn", qn)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        if (videoInfo.cids.size() == 1)
            DownloadService.startDownload(videoInfo.title,
                    videoInfo.aid, videoInfo.cids.get(0),
                    videoInfo.cover,
                    qn, "video", "");
        else
            DownloadService.startDownload(videoInfo.title, videoInfo.pagenames.get(page),
                    videoInfo.aid, videoInfo.cids.get(page),
                    videoInfo.cover,
                    qn, "video", "");
    }

    /**
     * 开始仅音频下载
     *
     * @param videoInfo 视频信息
     * @param page      页码
     * @param qn        清晰度
     * @param audioUrl  音频流URL
     */
    public static void startDownloadingAudioOnly(VideoInfo videoInfo, int page, int qn, String audioUrl) {
        if (videoInfo.cids.size() == 1)
            DownloadService.startDownload(videoInfo.title,
                    videoInfo.aid, videoInfo.cids.get(0),
                    videoInfo.cover,
                    qn, "audio_only", audioUrl);
        else
            DownloadService.startDownload(videoInfo.title, videoInfo.pagenames.get(page),
                    videoInfo.aid, videoInfo.cids.get(page),
                    videoInfo.cover,
                    qn, "audio_only", audioUrl);
    }

    /**
     * 解析视频（DASH格式）
     *
     * @param playerData 传入aid、cid、qn等必要数据
     */
    public static void getVideoDash(PlayerData playerData) throws JSONException, IOException {
        playerData.timeStamp = 0;
        playerData.videoUrl = "";
        playerData.audioUrl = "";
        playerData.danmakuUrl = NetWorkUtil.routeUrlForRelay("https://comment.bilibili.com/" + playerData.cid + ".xml");

        String query = "?"
                + "avid=" + playerData.aid
                + "&cid=" + playerData.cid
                + "&qn=" + playerData.qn
                + "&fnval=16&fnver=0" // 16:DASH格式
                + "&platform=pc"
                + "&voice_balance=1"
                + "&gaia_source=pre-load"
                + "&isGaiaAvoided=true";
        String wbiUrl = "https://api.bilibili.com/x/player/wbi/playurl" + query;
        String legacyUrl = "https://api.bilibili.com/x/player/playurl" + query;
        JSONObject data = requestPlayDataWithFallback(wbiUrl, legacyUrl, true);

        // 解析DASH数据
        if (data.has("dash")) {
            JSONObject dashJson = data.getJSONObject("dash");
            playerData.dashData = DashData.fromJson(dashJson);

            // 设置视频URL（选择指定清晰度的视频流）
            DashVideoStream videoStream = playerData.dashData.getVideoStream(playerData.qn);
            if (videoStream != null) {
                playerData.videoUrl = NetWorkUtil.routeUrlForRelay(firstStreamUrl(videoStream.baseUrl, videoStream.backupUrl));
            }

            // 设置音频URL（选择最高质量的音频流）
            DashAudioStream audioStream = playerData.dashData.getBestAudioStream();
            if (audioStream != null) {
                playerData.audioUrl = NetWorkUtil.routeUrlForRelay(firstStreamUrl(audioStream.baseUrl, audioStream.backupUrl));
            }
        } else {
            getVideo(playerData, true);
            return;
        }

        playerData.cidHistory = data.optLong("last_play_cid", 0);
        playerData.progress = data.optInt("last_play_time", 0);

        if (playerData.cidHistory == 0) {
            playerData.cidHistory = playerData.cid;
            playerData.progress = 0;
        }
        Logu.d("history", playerData.progress + "," + playerData.cidHistory);

        applyPlaybackMetadata(playerData, data);
        playerData.timeStamp = System.currentTimeMillis();
    }

    /**
     * 解析视频
     *
     * @param playerData 传入aid、cid、qn等必要数据，可以使用VideoInfo.toPlayerData
     * @param download   是否下载
     */
    public static void getVideo(PlayerData playerData, boolean download) throws JSONException, IOException {
        // 只有缓存时间和播放地址都有效时才复用；失败请求绝不能占住十分钟缓存。
        long now = System.currentTimeMillis();
        if (isUsableCachedPlayUrl(playerData, now))
            return;

        playerData.timeStamp = 0;
        playerData.videoUrl = "";

        playerData.danmakuUrl = NetWorkUtil.routeUrlForRelay("https://comment.bilibili.com/" + playerData.cid + ".xml");

        boolean html5 = !download && SharedPreferencesUtil.getString("player", "").equals("mtvPlayer");
        // html5方式现在已经仅对小电视播放器保留了

        String query = "?"
                + "avid=" + playerData.aid
                + "&cid=" + playerData.cid
                + (html5 ? "&high_quality=1" : "")
                + "&qn=" + playerData.qn
                + "&fnval=1&fnver=0"
                + "&platform=" + (html5 ? "html5" : "pc")
                + "&voice_balance=1"
                + "&gaia_source=pre-load"
                + "&isGaiaAvoided=true";
        String wbiUrl = "https://api.bilibili.com/x/player/wbi/playurl" + query;
        String legacyUrl = "https://api.bilibili.com/x/player/playurl" + query;
        JSONObject data;
        try {
            data = requestPlayDataWithFallback(wbiUrl, legacyUrl, false);
        } catch (IOException | JSONException error) {
            clearFailedPlayUrl(playerData);
            throw error;
        }
        JSONArray durl = data.optJSONArray("durl");
        playerData.videoUrl = NetWorkUtil.routeUrlForRelay(firstDurlUrl(durl));
        playerData.cidHistory = data.optLong("last_play_cid", 0);
        playerData.progress = data.optInt("last_play_time", 0);

        if (playerData.cidHistory == 0) {
            playerData.cidHistory = playerData.cid;
            playerData.progress = 0;
        }
        Logu.d("history", playerData.progress + "," + playerData.cidHistory);

        applyPlaybackMetadata(playerData, data);
        playerData.timeStamp = now;
    }

    private static boolean isUsableCachedPlayUrl(PlayerData playerData, long now) {
        long age = now - playerData.timeStamp;
        return playerData.timeStamp > 0
                && age >= 0
                && age < PLAY_URL_CACHE_MS
                && playerData.videoUrl != null
                && (playerData.videoUrl.startsWith("https://") || playerData.videoUrl.startsWith("http://"));
    }

    private static void clearFailedPlayUrl(PlayerData playerData) {
        playerData.timeStamp = 0;
        playerData.videoUrl = "";
    }

    private static JSONObject requestPlayDataWithFallback(String wbiUrl, String legacyUrl, boolean requireDash)
            throws JSONException, IOException {
        Exception firstError;
        try {
            JSONObject data = getPlayData(NetWorkUtil.getJson(ConfInfoApi.signWBI(wbiUrl), NetWorkUtil.webHeaders));
            if (hasRequiredPlayData(data, requireDash)) return data;
            firstError = new JSONException(requireDash ? "WBI 接口未返回 DASH 数据" : "WBI 接口未返回 durl 数据");
        } catch (IOException | JSONException error) {
            firstError = error;
        }

        try {
            JSONObject data = getPlayData(NetWorkUtil.getJson(legacyUrl, NetWorkUtil.webHeaders));
            if (hasRequiredPlayData(data, requireDash)) return data;
            throw new JSONException(requireDash ? "兼容接口未返回 DASH 数据" : "兼容接口未返回 durl 数据");
        } catch (IOException error) {
            error.addSuppressed(firstError);
            throw error;
        } catch (JSONException error) {
            error.addSuppressed(firstError);
            throw error;
        }
    }

    private static boolean hasRequiredPlayData(JSONObject data, boolean requireDash) {
        if (requireDash) return data.optJSONObject("dash") != null;
        JSONArray durl = data.optJSONArray("durl");
        return durl != null && durl.length() > 0;
    }

    private static String firstDurlUrl(JSONArray durl) throws JSONException {
        if (durl == null || durl.length() == 0) {
            throw new JSONException("未返回可播放地址，可能需要登录、会员权限或切换清晰度");
        }
        JSONObject item = durl.optJSONObject(0);
        if (item == null) throw new JSONException("播放地址格式异常");
        String url = item.optString("url", item.optString("base_url", ""));
        if (!url.isEmpty()) return url;
        JSONArray backups = item.optJSONArray("backup_url");
        if (backups != null && backups.length() > 0 && !backups.optString(0).isEmpty()) {
            return backups.optString(0);
        }
        throw new JSONException("播放接口返回了空地址");
    }

    private static String firstStreamUrl(String primary, java.util.List<String> backups) throws JSONException {
        if (primary != null && !primary.isEmpty()) return primary;
        if (backups != null) {
            for (String backup : backups) {
                if (backup != null && !backup.isEmpty()) return backup;
            }
        }
        throw new JSONException("DASH 流没有可用地址");
    }

    private static void applyPlaybackMetadata(PlayerData playerData, JSONObject data) {
        JSONArray descriptions = data.optJSONArray("accept_description");
        JSONArray qualities = data.optJSONArray("accept_quality");
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Integer> values = new ArrayList<>();

        if (descriptions != null && qualities != null) {
            int count = Math.min(descriptions.length(), qualities.length());
            for (int i = 0; i < count; i++) {
                labels.add(descriptions.optString(i, "清晰度 " + qualities.optInt(i)));
                values.add(qualities.optInt(i));
            }
        }

        if (labels.isEmpty()) {
            JSONArray formats = data.optJSONArray("support_formats");
            if (formats != null) {
                for (int i = 0; i < formats.length(); i++) {
                    JSONObject format = formats.optJSONObject(i);
                    if (format == null) continue;
                    int quality = format.optInt("quality", 0);
                    if (quality == 0) continue;
                    String label = format.optString("new_description",
                            format.optString("display_desc", "清晰度 " + quality));
                    labels.add(label);
                    values.add(quality);
                }
            }
        }

        playerData.qnStrList = labels.toArray(new String[0]);
        playerData.qnValueList = new int[values.size()];
        for (int i = 0; i < values.size(); i++) playerData.qnValueList[i] = values.get(i);
        Logu.d("qn_str", Arrays.toString(playerData.qnStrList));
        Logu.d("qn_val", Arrays.toString(playerData.qnValueList));
    }

    private static JSONObject getPlayData(JSONObject body) throws JSONException {
        int code = body.optInt("code", 0);
        if (code != 0) {
            String message = body.optString("message", body.optString("msg", "接口返回 " + code));
            throw new JSONException("：" + message);
        }
        if (!body.has("data") || body.isNull("data")) {
            throw new JSONException("：视频接口没有返回可用数据");
        }
        return body.getJSONObject("data");
    }

    /**
     * 解析番剧，和普通视频的api不一样
     *
     * @param playerData 传入aid、cid、qn等必要数据
     */
    public static void getBangumi(PlayerData playerData) throws JSONException, IOException {
        NetWorkUtil.FormData reqData = new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("aid", playerData.aid)
                .put("cid", playerData.cid)
                .put("fnval", 1)
                .put("fnvar", 0)
                .put("qn", playerData.qn)
                .put("season_type", 1)
                .put("session",
                        ToolsUtil.md5(
                                String.valueOf(System.currentTimeMillis() - SystemClock.currentThreadTimeMillis())))
                .put("platform", "pc");

        String url = "https://api.bilibili.com/pgc/player/web/playurl" + reqData.toString();

        JSONObject body = NetWorkUtil.getJson(url);
        Logu.v(body.toString());

        JSONObject data = body.getJSONObject("result");
        JSONArray durl = data.getJSONArray("durl");
        JSONObject video_url = durl.getJSONObject(0);
        playerData.videoUrl = NetWorkUtil.routeUrlForRelay(video_url.getString("url"));

        playerData.danmakuUrl = NetWorkUtil.routeUrlForRelay("https://comment.bilibili.com/" + playerData.cid + ".xml");

        JSONArray accept_description = data.getJSONArray("accept_description");
        JSONArray accept_quality = data.getJSONArray("accept_quality");
        String[] qnStrList = new String[accept_description.length()];
        int[] qnValueList = new int[accept_description.length()];
        for (int i = 0; i < qnStrList.length; i++) {
            qnStrList[i] = accept_description.optString(i);
            qnValueList[i] = accept_quality.optInt(i);
        }
        playerData.qnStrList = qnStrList;
        playerData.qnValueList = qnValueList;
    }

    /**
     * 跳转到播放器
     *
     * @param playerData 传入aid、cid、qn等必要数据
     * @return 播放器跳转Intent
     */
    public static Intent jumpToPlayer(PlayerData playerData) {
        Context context = BiliTerminal.context;
        Logu.v("准备跳转", "--------");
        Logu.v("视频标题", playerData.title);
        Logu.v("视频地址", playerData.videoUrl);
        Logu.v("弹幕地址", playerData.danmakuUrl);
        Logu.v("准备跳转", "--------");

        Intent intent = new Intent();
        switch (SharedPreferencesUtil.getString("player", "null")) {
            case "terminalPlayer":
                intent.setClass(context, PlayerActivity.class);
                intent.putExtra("url", playerData.videoUrl);
                intent.putExtra("danmaku", playerData.danmakuUrl);
                intent.putExtra("title", playerData.title);
                intent.putExtra("aid", playerData.aid);
                intent.putExtra("cid", playerData.cid);
                intent.putExtra("mid", playerData.mid);
                intent.putExtra("progress", playerData.progress);
                intent.putExtra("live_mode", playerData.isLive());
                if (playerData.qnStrList != null && playerData.qnValueList != null) {
                    intent.putExtra("qnStrList", playerData.qnStrList);
                    intent.putExtra("qnValueList", playerData.qnValueList);
                    intent.putExtra("currentQuality", playerData.qn);
                }
                if (playerData.pagenames != null && playerData.cids != null && playerData.pagenames.size() > 1) {
                    intent.putStringArrayListExtra("pagenames", playerData.pagenames);
                    long[] cidArray = new long[playerData.cids.size()];
                    for (int i = 0; i < playerData.cids.size(); i++) {
                        cidArray[i] = playerData.cids.get(i);
                    }
                    intent.putExtra("cids", cidArray);
                    intent.putExtra("currentPageIndex", playerData.currentPageIndex);
                }
                break;

            case "mtvPlayer":
                intent.setClassName(context.getString(R.string.player_package_mtv),
                        "com.xinxiangshicheng.wearbiliplayer.cn.player.PlayerActivity");
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra("cookie", SharedPreferencesUtil.getString("cookies", ""));
                intent.putExtra("mode", (playerData.isLocal() ? "2" : "0"));
                intent.putExtra("url", playerData.videoUrl);
                intent.putExtra("danmaku", playerData.danmakuUrl);
                intent.putExtra("title", playerData.title);
                intent.putExtra("live_mode", playerData.isLive());
                break;

            case "aliangPlayer":
                intent.setClassName(context.getString(R.string.player_package_aliang),
                        "com.aliangmaker.media.PlayVideoActivity");
                intent.putExtra("name", playerData.title);
                intent.putExtra("danmaku", playerData.danmakuUrl);
                intent.putExtra("live_mode", playerData.isLive());

                intent.setData(Uri.parse(playerData.videoUrl));

                if (!playerData.isLocal()) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Cookie", SharedPreferencesUtil.getString("cookies", ""));
                    headers.put("Referer", "https://www.bilibili.com/");
                    intent.putExtra("cookie", (Serializable) headers);
                    intent.putExtra("agent", NetWorkUtil.USER_AGENT_WEB);
                    intent.putExtra("progress", playerData.progress * 1000L);
                }
                intent.setAction(Intent.ACTION_VIEW);

                break;

            default:
                intent.setClass(context, SettingPlayerChooseActivity.class);
                break;
        }
        return intent;
    }

    public static Uri getVideoUri(Context context, String path) {
        File file = new File(path);
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);

        // 因为在文件夹里放了.nomedia标识，现在不能用这个了
        /*
         * Cursor cursor = context.getContentResolver().query(MediaStore.Video.Media.
         * EXTERNAL_CONTENT_URI,
         * new String[]{MediaStore.Video.Media._ID},
         * MediaStore.Video.Media.DATA + "=? ",
         * new String[]{path}, null);
         * if (cursor != null && cursor.moveToFirst()) {
         *
         * @SuppressLint("Range") int id =
         * cursor.getInt(cursor.getColumnIndex(MediaStore.Video.VideoColumns._ID));
         * Uri baseUri = Uri.parse("content://media/external/video/media");
         * cursor.close();
         * return Uri.withAppendedPath(baseUri, String.valueOf(id));
         * } else {
         * if (cursor != null) cursor.close();
         * ContentValues values = new ContentValues();
         * values.put(MediaStore.Video.Media.DATA, path);
         * return context.getContentResolver().insert(MediaStore.Video.Media.
         * EXTERNAL_CONTENT_URI, values);
         * }
         */
    }

    /**
     * 通过本地文件获取字幕
     *
     * @param folder 字幕文件夹
     * @return 字幕列表
     */
    public static SubtitleLink[] getSubtitleLinks(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        SubtitleLink[] links = new SubtitleLink[files != null ? (files.length + 1) : 1];
        if (files != null)
            for (int i = 0; i < files.length; i++) {
                links[i] = new SubtitleLink(i, files[i].getName(), files[i].toString(), false);
            }
        links[links.length - 1] = new SubtitleLink(-1, "不显示字幕", "null", false);
        return links;
    }

    /**
     * 获取视频的字幕链接列表
     *
     * @param aid aid
     * @param cid cid
     * @return 链接列表
     */
    public static SubtitleLink[] getSubtitleLinks(long aid, long cid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid
                + "&cid=" + cid;
        url = ConfInfoApi.signWBI(url);
        JSONObject data = NetWorkUtil.getJson(url).getJSONObject("data");

        JSONArray subtitles = data.getJSONObject("subtitle").getJSONArray("subtitles");
        Log.d("subtitle", subtitles.toString());

        SubtitleLink[] links = new SubtitleLink[subtitles.length() + 1];
        for (int i = 0; i < subtitles.length(); i++) {
            JSONObject subtitle = subtitles.getJSONObject(i);

            long id = subtitle.getLong("id");
            boolean isAI = subtitle.getInt("type") == 1;
            String lang = subtitle.getString("lan_doc");
            String subtitle_url = "https:" + subtitle.getString("subtitle_url");

            SubtitleLink link = new SubtitleLink(id, lang, subtitle_url, isAI);
            links[i] = link;
        }
        links[subtitles.length()] = new SubtitleLink(-1, "不显示字幕", "null", false);
        return links;
    }

    public static java.util.List<com.RobinNotBad.BiliClient.model.ViewPoint> getViewPoints(long aid, long cid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid
                + "&cid=" + cid;
        url = ConfInfoApi.signWBI(url);
        JSONObject data = NetWorkUtil.getJson(url).getJSONObject("data");

        java.util.List<com.RobinNotBad.BiliClient.model.ViewPoint> viewPoints = new java.util.ArrayList<>();
        
        if (data.has("view_points")) {
            JSONArray viewPointsArray = data.getJSONArray("view_points");
            for (int i = 0; i < viewPointsArray.length(); i++) {
                JSONObject vp = viewPointsArray.getJSONObject(i);
                String content = vp.optString("content", "");
                int from = vp.optInt("from", 0);
                int to = vp.optInt("to", 0);
                int type = vp.optInt("type", 0);
                String imgUrl = vp.optString("imgUrl", "");
                String logoUrl = vp.optString("logoUrl", "");
                
                viewPoints.add(new com.RobinNotBad.BiliClient.model.ViewPoint(content, from, to, type, imgUrl, logoUrl));
            }
        }
        
        return viewPoints;
    }

    public static long getInteractionGraphVersion(long aid, long cid) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/player/wbi/v2?aid=" + aid
                + "&cid=" + cid;
        url = ConfInfoApi.signWBI(url);
        JSONObject data = NetWorkUtil.getJson(url).getJSONObject("data");
        
        if (data.has("interaction") && !data.isNull("interaction")) {
            JSONObject interaction = data.getJSONObject("interaction");
            if (interaction.has("graph_version")) {
                return interaction.getLong("graph_version");
            }
        }
        
        return 0;
    }

    /**
     * 通过链接获取字幕
     *
     * @param url 传入链接，可通过getSubtitleLinks()获取
     * @return 逐条字幕的列表，每条包含文本和始末时间，时间以秒为单位
     */
    public static Subtitle[] getSubtitle(String url) throws JSONException, IOException {
        JSONArray body = NetWorkUtil.getJson(url).getJSONArray("body");
        Subtitle[] subtitles = new Subtitle[body.length()];
        for (int i = 0; i < body.length(); i++) {
            JSONObject single = body.getJSONObject(i);
            subtitles[i] = new Subtitle(
                    single.getString("content"),
                    single.getDouble("from"),
                    single.getDouble("to"));
        }
        return subtitles;
    }

    /**
     * 通过本地文件获取字幕
     *
     * @param file 传入json文件
     * @return 逐条字幕的列表，每条包含文本和始末时间，时间以秒为单位
     */
    public static Subtitle[] getSubtitle(File file) throws JSONException {
        String str = FileUtil.readString(file);
        if (str == null)
            return null;

        JSONArray body = new JSONObject(str).getJSONArray("body");
        Subtitle[] subtitles = new Subtitle[body.length()];
        for (int i = 0; i < body.length(); i++) {
            JSONObject single = body.getJSONObject(i);
            subtitles[i] = new Subtitle(
                    single.getString("content"),
                    single.getDouble("from"),
                    single.getDouble("to"));
        }
        return subtitles;
    }


    /** Local subtitle list from a File path or SAF directory locator. */
    public static SubtitleLink[] getSubtitleLinks(Context context, String folderLocator) {
        try {
            VideoStorageUtil.Node folder = VideoStorageUtil.fromLocator(context, folderLocator);
            if (folder == null || !folder.isDirectory()) {
                return new SubtitleLink[]{new SubtitleLink(-1, "不显示字幕", "null", false)};
            }
            ArrayList<SubtitleLink> links = new ArrayList<>();
            int id = 0;
            for (VideoStorageUtil.Node child : folder.listChildren()) {
                String name = child.getName();
                if (child.isFile() && name != null && name.endsWith(".json")) {
                    links.add(new SubtitleLink(id++, name, child.getUriString(), false));
                }
            }
            links.add(new SubtitleLink(-1, "不显示字幕", "null", false));
            return links.toArray(new SubtitleLink[0]);
        } catch (Exception e) {
            return new SubtitleLink[]{new SubtitleLink(-1, "不显示字幕", "null", false)};
        }
    }

    /** Local subtitle body from either a filesystem path or content URI. */
    public static Subtitle[] getSubtitle(Context context, String reference) throws JSONException {
        String str = VideoStorageUtil.readString(context, reference);
        if (str == null) return null;
        JSONArray body = new JSONObject(str).getJSONArray("body");
        Subtitle[] subtitles = new Subtitle[body.length()];
        for (int i = 0; i < body.length(); i++) {
            JSONObject single = body.getJSONObject(i);
            subtitles[i] = new Subtitle(single.getString("content"),
                    single.getDouble("from"), single.getDouble("to"));
        }
        return subtitles;
    }

    /**
     * 获取高能进度条数据
     */
    public static HighEnergyData getHighEnergyData(long cid, long aid) {
        try {
            String url = "https://bvc.bilivideo.com/pbp/data?cid=" + cid;
            if (aid > 0) {
                url += "&aid=" + aid;
            }

            JSONObject response = NetWorkUtil.getJson(url, NetWorkUtil.webHeaders);

            if (response == null) {
                Logu.w("高能进度条", "响应为空");
                return null;
            }

            int code = response.optInt("code", -1);
            if (code != 0 && code != -1) {
                Logu.w("高能进度条", "API返回错误码: " + code);
                return null;
            }

            HighEnergyData data = new HighEnergyData();
            data.stepSec = response.optInt("step_sec", 10);
            data.tagStr = response.optString("tagstr", "");
            data.debug = response.optString("debug", "");

            JSONObject events = response.optJSONObject("events");
            if (events != null) {
                JSONArray defaultArray = events.optJSONArray("default");
                if (defaultArray != null && defaultArray.length() > 0) {
                    float[] eventData = new float[defaultArray.length()];
                    for (int i = 0; i < defaultArray.length(); i++) {
                        eventData[i] = (float) defaultArray.optDouble(i, 0.0);
                    }
                    data.events = eventData;
                    Logu.d("高能进度条", "成功获取 " + eventData.length + " 个数据点，采样间隔: " + data.stepSec + "秒");
                } else {
                    Logu.w("高能进度条", "default数组为空或不存在");
                    data.events = new float[0];
                }
            } else {
                Logu.w("高能进度条", "events对象不存在");
                data.events = new float[0];
            }

            return data;
        } catch (Exception e) {
            Logu.e("高能进度条", "获取失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
