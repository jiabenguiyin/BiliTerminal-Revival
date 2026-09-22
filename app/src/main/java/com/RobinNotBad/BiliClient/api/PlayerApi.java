package com.RobinNotBad.BiliClient.api;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

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
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.DeviceProfile;
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
import java.util.List;
import java.util.Locale;
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
        int requestedQn = playerData.qn;
        playerData.timeStamp = 0;
        playerData.videoUrl = "";
        playerData.videoBackupUrls.clear();
        playerData.audioUrl = "";
        playerData.audioBackupUrls.clear();
        // Keep the official URL here; the current global relay mode is applied at use time.
        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";

        String query = buildDashPlayQuery(playerData);
        String wbiUrl = "https://api.bilibili.com/x/player/wbi/playurl" + query;
        String legacyUrl = "https://api.bilibili.com/x/player/playurl" + query;
        JSONObject data = requestPlayDataWithFallback(wbiUrl, legacyUrl, true);

        // 解析DASH数据
        if (data.has("dash")) {
            JSONObject dashJson = data.getJSONObject("dash");
            playerData.dashData = DashData.fromJson(dashJson);

            // 设置视频URL（选择指定清晰度的视频流）
            int selectQn = requestedQn;
            if (requestedQn <= 0 && DeviceProfile.get() == DeviceProfile.Tier.COMPAT) {
                // 1.1.6: 兼容档自动档优先 720P；接口没有 720P 时再回退到 360P
                selectQn = DeviceProfile.autoQuality();
            }
            DashVideoStream videoStream = playerData.dashData.getVideoStream(selectQn);
            if (videoStream == null) {
                throw new JSONException("接口未返回所选清晰度的视频流，请切换普通画质后重试");
            }
            int actualQn = videoStream.id > 0 ? videoStream.id : requestedQn;
            playerData.qn = actualQn;
            playerData.videoUrl = firstStreamUrl(videoStream.baseUrl, videoStream.backupUrl);
            setBackupUrls(playerData, videoStream.backupUrl);
            JSONObject streamDetails = new JSONObject();
            try {
                streamDetails.put("requested_qn", requestedQn);
                streamDetails.put("actual_qn", actualQn);
                streamDetails.put("stream_id", videoStream.id);
                streamDetails.put("width", videoStream.width);
                streamDetails.put("height", videoStream.height);
                streamDetails.put("frame_rate", videoStream.frameRate);
                streamDetails.put("codecid", videoStream.codecid);
                streamDetails.put("device_profile", DeviceProfile.get().name());
            } catch (JSONException ignored) {
            }
            DiagnosticLogManager.record("player_video_stream_selected", streamDetails);

            DashAudioStream audioStream = playerData.dashData.getBestCompatibleAudioStream();
            if (audioStream != null) {
                playerData.audioUrl = firstStreamUrl(audioStream.baseUrl, audioStream.backupUrl);
                setAudioBackupUrls(playerData, audioStream.backupUrl);
            }
        } else {
            if (requestedQn <= 0) {
                // 1.1.6: 兼容档自动档 durl 优先 720P，其余保持原请求档位
                playerData.qn = DeviceProfile.get() == DeviceProfile.Tier.COMPAT
                        ? DeviceProfile.autoQuality() : 80;
            }
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

    static String buildDashPlayQuery(PlayerData playerData) {
        // 1.1.6: 按设备档位钳制请求画质 —— 高端设备请求 4K 顶层档以获取全部清晰度描述；
        // 中低端设备只请求档位上限内的画质，降低响应体积与低端设备的解析负担。
        int topQn = DeviceProfile.requestTopTier4k() ? DashData.QN_4K : DashData.QN_1080P;
        int qn = Math.min(topQn, DeviceProfile.maxQuality());
        // Capable devices use one unified DASH timeline; COMPAT stays on durl.
        boolean fourk = DeviceProfile.useUnifiedDashPlayer();
        return "?"
                + "avid=" + playerData.aid
                + "&cid=" + playerData.cid
                + "&qn=" + qn
                + "&fnval=16&fnver=0"
                + "&fourk=" + (fourk ? 1 : 0)
                + "&platform=pc"
                + "&voice_balance=1"
                + "&gaia_source=pre-load"
                + "&isGaiaAvoided=true";
    }

    public static void getVideoForPlayback(PlayerData playerData) throws JSONException, IOException {
        int requestedQn = playerData.qn;
        // 1.1.6: 兼容档直接走 durl 单播放器，不先请求 DASH 再回退。
        // Android 4.x/低内存设备不需要解析 DASH 元数据，这能减少一次网络请求、
        // JSON 解析和播放器初始化，避免低配机打开视频后明显变慢。
        if (!DeviceProfile.useUnifiedDashPlayer() && !DeviceProfile.useDashDualPlayer()) {
            if (requestedQn <= 0) playerData.qn = DeviceProfile.autoQuality();
            getVideo(playerData, false);
            return;
        }
        try {
            getVideoDash(playerData);
        } catch (IOException | JSONException dashError) {
            fallbackToProgressivePlayback(playerData, requestedQn, dashError);
            return;
        }
        if (!usesProgressivePlayback(playerData.qn)) return;

        int resolvedQn = playerData.qn;
        String dashVideoUrl = playerData.videoUrl;
        ArrayList<String> dashVideoBackups = new ArrayList<>(playerData.videoBackupUrls);
        String dashAudioUrl = playerData.audioUrl;
        ArrayList<String> dashAudioBackups = new ArrayList<>(playerData.audioBackupUrls);
        String[] availableLabels = playerData.qnStrList;
        int[] availableValues = playerData.qnValueList;

        playerData.timeStamp = 0L;
        getVideo(playerData, false);
        if (playerData.qn != resolvedQn) {
            // The progressive endpoint occasionally applies its own downgrade. Keep
            // the already resolved DASH stream instead of unexpectedly dropping to 480P.
            playerData.videoUrl = dashVideoUrl;
            playerData.videoBackupUrls.clear();
            playerData.videoBackupUrls.addAll(dashVideoBackups);
            playerData.audioUrl = dashAudioUrl;
            playerData.audioBackupUrls.clear();
            playerData.audioBackupUrls.addAll(dashAudioBackups);
        }
        playerData.qn = resolvedQn;
        playerData.qnStrList = availableLabels;
        playerData.qnValueList = availableValues;
    }

    private static void fallbackToProgressivePlayback(PlayerData playerData, int requestedQn,
                                                      Exception dashError)
            throws IOException, JSONException {
        playerData.qn = requestedQn;
        playerData.dashData = null;
        try {
            getVideo(playerData, false);
            Logu.w("player-quality", "DASH unavailable, using progressive playback: "
                    + dashError.getClass().getSimpleName());
        } catch (IOException progressiveError) {
            progressiveError.addSuppressed(dashError);
            throw progressiveError;
        } catch (JSONException progressiveError) {
            progressiveError.addSuppressed(dashError);
            throw progressiveError;
        }
    }

    static boolean usesProgressivePlayback(int qn) {
        // Ordinary qualities are available as a muxed stream. Keeping audio and video
        // in one player avoids the clock drift that separate DASH players develop on
        // long videos, seeks and speed changes. High-frame-rate/member qualities still
        // require DASH because the progressive endpoint degrades them to a lower tier.
        return qn == 6 || qn == 16 || qn == 32 || qn == 64 || qn == 80;
    }

    static boolean automaticQualityUsesProgressivePlayback(int requestedQn, int resolvedQn) {
        return requestedQn <= 0 && usesProgressivePlayback(resolvedQn);
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
        playerData.videoBackupUrls.clear();
        playerData.audioUrl = "";
        playerData.audioBackupUrls.clear();

        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";

        String query = buildVideoPlayQuery(playerData);
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
        applyDurlUrls(playerData, durl);
        int responseQuality = data.optInt("quality", playerData.qn);
        if (DashData.isSupportedQuality(responseQuality)) playerData.qn = responseQuality;
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

    static String buildVideoPlayQuery(PlayerData playerData) {
        // 自动档由 DeviceProfile.autoQuality() 预先解析；用户明确选择普通 1080P 时，
        // 兼容档也应按原请求获取单文件 durl，不能因为设备分档静默降到 720P。
        int qn = Math.min(DashData.normalizeRequestedQuality(playerData.qn), DeviceProfile.maxQuality());
        boolean fourk = DeviceProfile.useDashDualPlayer()
                && DeviceProfile.get() != DeviceProfile.Tier.COMPAT;
        return "?"
                + "avid=" + playerData.aid
                + "&cid=" + playerData.cid
                + "&qn=" + qn
                + "&fnval=0&fnver=0"
                + "&fourk=" + (fourk ? 1 : 0)
                + "&platform=pc"
                + "&voice_balance=1"
                + "&gaia_source=pre-load"
                + "&isGaiaAvoided=true";
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
        playerData.videoBackupUrls.clear();
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

    private static void applyDurlUrls(PlayerData playerData, JSONArray durl) throws JSONException {
        if (durl == null || durl.length() == 0) {
            throw new JSONException("未返回可播放地址，可能需要登录、会员权限或切换清晰度");
        }
        JSONObject item = durl.optJSONObject(0);
        if (item == null) throw new JSONException("播放地址格式异常");
        String url = item.optString("url", item.optString("base_url", ""));
        JSONArray backups = item.optJSONArray("backup_url");
        if (backups == null) backups = item.optJSONArray("backupUrl");
        if (url.isEmpty() && backups != null) {
            for (int i = 0; i < backups.length(); i++) {
                String backup = backups.optString(i, "");
                if (!backup.isEmpty()) {
                    url = backup;
                    break;
                }
            }
        }
        if (url.isEmpty()) throw new JSONException("播放接口返回了空地址");

        playerData.videoUrl = url;
        playerData.videoBackupUrls.clear();
        if (backups != null) {
            for (int i = 0; i < backups.length(); i++) {
                addBackupUrl(playerData, backups.optString(i, ""));
            }
        }
        // 1.1.6: 国内镜像优先排序 —— 把 upos-sz-mirror*（国内可达）排前，
        // Akamai 海外边缘（edge.mountaintoys.cn）排后，从源头降低 -10000 硬解打开失败率
        ArrayList<String> candidates = new ArrayList<>();
        candidates.add(playerData.videoUrl);
        candidates.addAll(playerData.videoBackupUrls);
        List<String> ranked = sortUrlsByHost(candidates);
        if (!ranked.isEmpty()) {
            playerData.videoUrl = ranked.get(0);
            playerData.videoBackupUrls.clear();
            for (int i = 1; i < ranked.size(); i++) {
                playerData.videoBackupUrls.add(ranked.get(i));
            }
        }
    }

    private static String firstStreamUrl(String primary, java.util.List<String> backups) throws JSONException {
        // 1.1.6: 主备源一起按主机优先级挑选，而不是无脑用主源（主源常是 Akamai 海外边缘）
        String best = pickBestUrl(primary, backups);
        if (best == null || best.isEmpty()) {
            throw new JSONException("DASH 流没有可用地址");
        }
        return best;
    }

    private static void setBackupUrls(PlayerData playerData, java.util.List<String> backups) {
        playerData.videoBackupUrls.clear();
        if (backups == null) return;
        List<String> candidates = new ArrayList<>();
        for (String backup : backups) {
            if (backup == null || backup.isEmpty()) continue;
            if (!candidates.contains(backup)) candidates.add(backup);
        }
        playerData.videoBackupUrls.addAll(sortUrlsByHost(candidates));
    }

    private static void setAudioBackupUrls(PlayerData playerData, java.util.List<String> backups) {
        playerData.audioBackupUrls.clear();
        if (backups == null) return;
        List<String> candidates = new ArrayList<>();
        for (String backup : backups) {
            if (backup == null || backup.isEmpty() || backup.equals(playerData.audioUrl)
                    || candidates.contains(backup)) continue;
            candidates.add(backup);
        }
        playerData.audioBackupUrls.addAll(sortUrlsByHost(candidates));
    }

    // ==================== 1.1.6: 播放源主机优先级 ====================

    /** 国内镜像 CDN（upos-sz-mirror*，国内网络可达性最好）rank=0；bilivideo.com 其他节点 rank=1；
     *  其他第三方 CDN rank=2；Akamai 海外边缘（mountaintoys/akamaized，国内设备打开失败率高）rank=3。 */
    private static int urlHostRank(String url) {
        if (url == null) return 9;
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return 9;
            String h = host.toLowerCase(Locale.US);
            if (h.contains("mirrorbd") || h.contains("mirrorali") || h.contains("mirrorcos")
                    || h.contains("mirrorks") || h.contains("mirrorbda1")) return 0;
            if (h.contains("bilivideo.com")) return 1;
            if (h.contains("mountaintoys") || h.contains("akamaized")) return 3;
            return 2;
        } catch (Exception ignored) {
            return 9;
        }
    }

    /** 从主备候选里挑出主机优先级最高的 URL（同 rank 保持原顺序）。 */
    private static String pickBestUrl(String primary, java.util.List<String> backups) {
        String best = primary;
        int bestRank = urlHostRank(primary);
        if (backups != null) {
            for (String backup : backups) {
                int rank = urlHostRank(backup);
                if (rank < bestRank) {
                    bestRank = rank;
                    best = backup;
                }
            }
        }
        return best;
    }

    /** 按主机优先级稳定排序 URL 列表。 */
    private static List<String> sortUrlsByHost(List<String> urls) {
        ArrayList<String> sorted = new ArrayList<>();
        for (int rank = 0; rank <= 3; rank++) {
            for (String url : urls) {
                if (urlHostRank(url) == rank) sorted.add(url);
            }
        }
        for (String url : urls) {
            if (urlHostRank(url) > 3) sorted.add(url);
        }
        return sorted;
    }

    private static void addBackupUrl(PlayerData playerData, String backup) {
        if (backup == null || backup.isEmpty() || backup.equals(playerData.videoUrl)
                || playerData.videoBackupUrls.contains(backup)) return;
        playerData.videoBackupUrls.add(backup);
    }

    private static void applyPlaybackMetadata(PlayerData playerData, JSONObject data) {
        ArrayList<Integer> values = extractAdvertisedQualityValues(data);

        ArrayList<String> orderedLabels = new ArrayList<>();
        ArrayList<Integer> orderedValues = new ArrayList<>();
        for (int supportedQuality : DashData.getSupportedQualityOrder()) {
            if (!values.contains(supportedQuality)) continue;
            if (playerData.dashData != null && playerData.dashData.hasVideo()
                    && !playerData.dashData.hasVideoStreamForQuality(supportedQuality)) continue;
            orderedValues.add(supportedQuality);
            orderedLabels.add(DashData.getQualityLabel(supportedQuality));
        }
        if (orderedValues.isEmpty() && playerData.dashData != null && playerData.dashData.hasVideo()) {
            for (int supportedQuality : DashData.getSupportedQualityOrder()) {
                if (!playerData.dashData.hasVideoStreamForQuality(supportedQuality)) continue;
                orderedValues.add(supportedQuality);
                orderedLabels.add(DashData.getQualityLabel(supportedQuality));
            }
        }
        playerData.qnStrList = orderedLabels.toArray(new String[0]);
        playerData.qnValueList = new int[orderedValues.size()];
        for (int i = 0; i < orderedValues.size(); i++) playerData.qnValueList[i] = orderedValues.get(i);
        JSONObject details = new JSONObject();
        try {
            details.put("selected_qn", data.optInt("quality", 0));
            details.put("durl_count", data.optJSONArray("durl") == null ? 0 : data.optJSONArray("durl").length());
            details.put("available_count", orderedValues.size());
            details.put("available_qn", joinQualityValues(orderedValues));
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("player_quality_response", details);
        Logu.d("qn_str", Arrays.toString(playerData.qnStrList));
        Logu.d("qn_val", Arrays.toString(playerData.qnValueList));
    }

    static int[] extractAvailableQualityValues(JSONObject data, DashData dashData) {
        PlayerData playerData = new PlayerData();
        playerData.dashData = dashData;
        applyPlaybackMetadata(playerData, data);
        return playerData.qnValueList;
    }

    private static ArrayList<Integer> extractAdvertisedQualityValues(JSONObject data) {
        ArrayList<Integer> values = new ArrayList<>();
        JSONArray qualities = data.optJSONArray("accept_quality");
        if (qualities != null) {
            for (int i = 0; i < qualities.length(); i++) {
                int quality = qualities.optInt(i);
                if (DashData.isSupportedQuality(quality) && !values.contains(quality)) values.add(quality);
            }
        }
        if (!values.isEmpty()) return values;

        JSONArray formats = data.optJSONArray("support_formats");
        if (formats != null) {
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.optJSONObject(i);
                if (format == null) continue;
                int quality = format.optInt("quality", 0);
                if (DashData.isSupportedQuality(quality) && !values.contains(quality)) values.add(quality);
            }
        }
        return values;
    }

    private static String joinQualityValues(ArrayList<Integer> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) result.append(',');
            result.append(values.get(i));
        }
        return result.toString();
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
        applyDurlUrls(playerData, durl);

        playerData.danmakuUrl = "https://comment.bilibili.com/" + playerData.cid + ".xml";

        applyPlaybackMetadata(playerData, data);
    }

    /**
     * 跳转到播放器
     *
     * @param playerData 传入aid、cid、qn等必要数据
     * @return 播放器跳转Intent
     */
    public static Intent jumpToPlayer(PlayerData playerData) {
        Context context = BiliTerminal.context;
        // Built-in and external players both follow the explicit global relay mode.
        String externalVideoUrl = NetWorkUtil.routeUrlForRelay(playerData.videoUrl);
        String externalDanmakuUrl = NetWorkUtil.routeUrlForRelay(playerData.danmakuUrl);
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
                intent.putStringArrayListExtra("backup_urls", playerData.videoBackupUrls);
                intent.putExtra("audio_url", playerData.audioUrl);
                intent.putStringArrayListExtra("audio_backup_urls", playerData.audioBackupUrls);
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

            case "aliangPlayer":
                intent.setClassName(context.getString(R.string.player_package_aliang),
                        "com.aliangmaker.media.PlayVideoActivity");
                intent.putExtra("name", playerData.title);
                intent.putExtra("danmaku", externalDanmakuUrl);
                intent.putExtra("live_mode", playerData.isLive());

                intent.setData(Uri.parse(externalVideoUrl));

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
        Logu.d("subtitle", "count=" + subtitles.length());

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
