package com.RobinNotBad.BiliClient.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * DASH格式数据
 */
public class DashData {
    public static final int QN_AUTO = 0;
    public static final int QN_360P = 16;
    public static final int QN_480P = 32;
    public static final int QN_720P = 64;
    public static final int QN_720P_60 = 74;
    public static final int QN_1080P = 80;
    public static final int QN_1080P_PLUS = 112;
    public static final int QN_1080P_60 = 116;
    public static final int QN_4K = 120;

    // The player intentionally stops at 4K. 8K, HDR and Dolby Vision are not selectable formats.
    private static final int[] SUPPORTED_QUALITY_ORDER = {
            QN_4K, QN_1080P_60, QN_1080P_PLUS, QN_1080P,
            QN_720P_60, QN_720P, QN_480P, QN_360P
    };

    public int duration; // 视频长度（秒）
    public double minBufferTime; // 最小缓冲时间
    public List<DashVideoStream> videoStreams; // 视频流列表
    public List<DashAudioStream> audioStreams; // 音频流列表
    public DashAudioStream dolbyAudio; // 杜比全景声音频
    public DashAudioStream flacAudio; // 无损音轨音频

    public DashData() {
        videoStreams = new ArrayList<>();
        audioStreams = new ArrayList<>();
    }

    /**
     * 从JSON对象解析DASH数据
     */
    public static DashData fromJson(JSONObject json) throws JSONException {
        DashData dashData = new DashData();
        dashData.duration = json.optInt("duration", 0);
        dashData.minBufferTime = json.optDouble("minBufferTime",
                json.optDouble("min_buffer_time", 1.5));

        // 解析视频流
        dashData.videoStreams = DashVideoStream.fromJsonArray(json.optJSONArray("video"));

        // 解析音频流
        dashData.audioStreams = DashAudioStream.fromJsonArray(json.optJSONArray("audio"));

        // 解析杜比全景声
        JSONObject dolbyObj = json.optJSONObject("dolby");
        if (dolbyObj != null && dolbyObj.optInt("type", 0) > 0) {
            JSONObject dolbyAudioObj = dolbyObj.optJSONObject("audio");
            if (dolbyAudioObj == null && dolbyObj.has("audio") && !dolbyObj.isNull("audio")) {
                // audio may be an array in some responses. Ignore malformed scalar
                // values instead of dereferencing a null JSONArray.
                org.json.JSONArray dolbyAudioArray = dolbyObj.optJSONArray("audio");
                if (dolbyAudioArray != null && dolbyAudioArray.length() > 0) {
                    dolbyAudioObj = dolbyAudioArray.optJSONObject(0);
                }
            }
            if (dolbyAudioObj != null) {
                dashData.dolbyAudio = DashAudioStream.fromJson(dolbyAudioObj);
            }
        }

        // 解析无损音轨
        JSONObject flacObj = json.optJSONObject("flac");
        if (flacObj != null && flacObj.optBoolean("display", false)) {
            JSONObject flacAudioObj = flacObj.optJSONObject("audio");
            if (flacAudioObj != null) {
                dashData.flacAudio = DashAudioStream.fromJson(flacAudioObj);
            }
        }

        return dashData;
    }

    /**
     * 获取指定清晰度的视频流
     */
    public DashVideoStream getVideoStream(int qn) {
        if (qn == QN_AUTO || qn == 125 || qn == 126 || qn == 127) {
            return getHighestVideoStream();
        }
        int[] fallbackQualities = getFallbackQualities(qn);
        for (int fallbackQn : fallbackQualities) {
            DashVideoStream stream = findVideoStream(fallbackQn);
            if (stream != null) return stream;
        }
        return null;
    }

    /**
     * Finds the requested stream first, then follows Bilibili's quality fallback
     * order. High-frame-rate variants stay ahead of the same-resolution normal stream.
     */
    private int[] getFallbackQualities(int qn) {
        switch (qn) {
            case QN_4K:
                return new int[]{QN_4K, QN_1080P_60, QN_1080P_PLUS, QN_1080P,
                        QN_720P_60, QN_720P, QN_480P, QN_360P};
            case QN_1080P_60:
                return new int[]{QN_1080P_60, QN_1080P_PLUS, QN_1080P,
                        QN_720P_60, QN_720P, QN_480P, QN_360P};
            case QN_1080P_PLUS:
                return new int[]{QN_1080P_PLUS, QN_1080P, QN_720P_60,
                        QN_720P, QN_480P, QN_360P};
            case QN_1080P:
                return new int[]{QN_1080P, QN_720P_60, QN_720P, QN_480P, QN_360P};
            case QN_720P_60:
                return new int[]{QN_720P_60, QN_720P, QN_480P, QN_360P};
            case QN_720P:
                return new int[]{QN_720P, QN_480P, QN_360P};
            case QN_480P:
                return new int[]{QN_480P, QN_360P};
            case QN_360P:
                return new int[]{QN_360P};
            default:
                return new int[]{QN_360P};
        }
    }

    private DashVideoStream findVideoStream(int qn) {
        return findDirectVideoStream(qn);
    }

    private DashVideoStream findDirectVideoStream(int qn) {
        DashVideoStream exactFallback = null;
        for (DashVideoStream stream : videoStreams) {
            if (!isSelectableStream(stream)) continue;
            if (stream.id == qn) {
                if (!isUsableForRequestedQuality(stream, qn)) continue;
                if (stream.codecid == 7) return stream;
                if (exactFallback == null) exactFallback = stream;
            }
        }
        if (exactFallback != null) return exactFallback;

        return null;
    }

    /** Returns whether this response contains a playable representation for the exact tier. */
    public boolean hasVideoStreamForQuality(int qn) {
        return isSupportedQuality(qn) && findDirectVideoStream(qn) != null;
    }

    /** Returns the highest usable video stream actually present in this response. */
    public DashVideoStream getHighestVideoStream() {
        DashVideoStream best = null;
        boolean hasH264 = false;
        for (DashVideoStream stream : videoStreams) {
            if (!isSelectableStream(stream) || !isUsableForRequestedQuality(stream, stream.id)) continue;
            if (stream.codecid == 7) hasH264 = true;
        }
        for (DashVideoStream stream : videoStreams) {
            if (!isSelectableStream(stream) || !isUsableForRequestedQuality(stream, stream.id)) continue;
            if (hasH264 && stream.codecid != 7) continue;
            if (best == null || compareVideoQuality(stream, best) > 0) best = stream;
        }
        return best;
    }

    public static boolean isSupportedQuality(int qn) {
        for (int supported : SUPPORTED_QUALITY_ORDER) {
            if (supported == qn) return true;
        }
        return false;
    }

    /** Maps automatic and legacy/special-format values to the capped request tier. */
    public static int normalizeRequestedQuality(int qn) {
        // 16 and 64 are the legacy progressive-stream request values used by
        // the download and old-player paths. They must remain intact; turning
        // them into 4K makes a 360P/720P request ask the wrong endpoint tier.
        if (qn == QN_360P || qn == QN_720P) return qn;
        return isSupportedQuality(qn) ? qn : QN_4K;
    }

    public static int[] getSupportedQualityOrder() {
        return SUPPORTED_QUALITY_ORDER.clone();
    }

    public static String getQualityLabel(int qn) {
        switch (qn) {
            case QN_4K:
                return "4K";
            case QN_1080P_60:
                return "1080P 60帧";
            case QN_1080P_PLUS:
                return "1080P+";
            case QN_1080P:
                return "1080P";
            case QN_720P_60:
                return "720P 60帧";
            case QN_720P:
                return "720P";
            case QN_480P:
                return "480P";
            case QN_360P:
                return "360P";
            default:
                return "清晰度 " + qn;
        }
    }

    private boolean isSelectableStream(DashVideoStream stream) {
        if (stream == null || !isSupportedQuality(stream.id)) return false;
        if (stream.width <= 0 || stream.height <= 0) return true;
        return stream.width <= 3840 && stream.height <= 2160;
    }

    private int compareVideoQuality(DashVideoStream left, DashVideoStream right) {
        long leftPixels = (long) left.width * Math.max(1, left.height);
        long rightPixels = (long) right.width * Math.max(1, right.height);
        if (leftPixels != rightPixels) return Long.compare(leftPixels, rightPixels);
        double leftFps = parseFrameRate(left.frameRate);
        double rightFps = parseFrameRate(right.frameRate);
        if (leftFps != rightFps) return Double.compare(leftFps, rightFps);
        if (left.id != right.id) return Integer.compare(left.id, right.id);
        return Long.compare(left.bandwidth, right.bandwidth);
    }

    private double parseFrameRate(String frameRate) {
        if (frameRate == null || frameRate.isEmpty()) return 0.0;
        try {
            String value = frameRate.split("/")[0];
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return frameRate.contains("60") || frameRate.contains("59") ? 60.0 : 0.0;
        }
    }

    private boolean matchesRequestedQuality(DashVideoStream stream, int qn) {
        if (qn == QN_720P_60) return stream.width <= 1400 && stream.height >= 700 && stream.height <= 800
                && isHighFrameRate(stream.frameRate);
        if (qn == QN_1080P_60) return stream.width <= 2200 && stream.height >= 1000 && stream.height <= 1200
                && isHighFrameRate(stream.frameRate);
        if (qn == QN_4K) return stream.width >= 3000 || stream.height >= 2000;
        return false;
    }

    /**
     * The quality id is not authoritative after entitlement changes. Bilibili
     * can keep a member-only id while returning an ordinary stream, so the
     * player must validate the dimensions and frame rate before accepting it.
     */
    private boolean isUsableForRequestedQuality(DashVideoStream stream, int qn) {
        if (stream == null) return false;
        if (qn == QN_4K || qn == QN_720P_60 || qn == QN_1080P_60) {
            boolean dimensionsKnown = stream.width > 0 && stream.height > 0;
            boolean frameRateKnown = stream.frameRate != null && !stream.frameRate.isEmpty();
            if (dimensionsKnown && !matchesDimensionsForQuality(stream, qn)) return false;
            if ((qn == QN_720P_60 || qn == QN_1080P_60) && frameRateKnown
                    && !isHighFrameRate(stream.frameRate)) return false;
        }
        return true;
    }

    private boolean matchesDimensionsForQuality(DashVideoStream stream, int qn) {
        if (qn == QN_4K) return stream.width >= 3000 || stream.height >= 2000;
        if (qn == QN_720P_60) return stream.width <= 1400 && stream.height >= 700 && stream.height <= 800;
        if (qn == QN_1080P_60) return stream.width <= 2200 && stream.height >= 1000 && stream.height <= 1200;
        return true;
    }

    private boolean isHighFrameRate(String frameRate) {
        if (frameRate == null || frameRate.isEmpty()) return false;
        try {
            return Double.parseDouble(frameRate.split("/")[0]) >= 50.0;
        } catch (NumberFormatException ignored) {
            return frameRate.contains("60") || frameRate.contains("59");
        }
    }

    /**
     * 获取最高质量的音频流
     */
    public DashAudioStream getBestAudioStream() {
        // 优先返回无损音轨
        if (flacAudio != null) {
            return flacAudio;
        }
        // 其次返回杜比全景声
        if (dolbyAudio != null) {
            return dolbyAudio;
        }
        // 返回最高码率的普通音频流
        if (!audioStreams.isEmpty()) {
            DashAudioStream best = audioStreams.get(0);
            for (DashAudioStream stream : audioStreams) {
                if (stream.bandwidth > best.bandwidth) {
                    best = stream;
                }
            }
            return best;
        }
        return null;
    }

    public DashAudioStream getBestCompatibleAudioStream() {
        if (audioStreams.isEmpty()) return null;
        DashAudioStream best = null;
        for (DashAudioStream stream : audioStreams) {
            if (!isCompatibleAudio(stream)) continue;
            if (best == null || stream.bandwidth > best.bandwidth) best = stream;
        }
        if (best != null) return best;

        best = audioStreams.get(0);
        for (DashAudioStream stream : audioStreams) {
            if (stream.bandwidth > best.bandwidth) best = stream;
        }
        return best;
    }

    private boolean isCompatibleAudio(DashAudioStream stream) {
        String codecs = stream.codecs == null ? "" : stream.codecs.toLowerCase();
        String mimeType = stream.mimeType == null ? "" : stream.mimeType.toLowerCase();
        return codecs.isEmpty() || codecs.contains("mp4a") || mimeType.contains("audio/mp4");
    }

    /**
     * 是否有有效的音频流
     */
    public boolean hasAudio() {
        return !audioStreams.isEmpty() || dolbyAudio != null || flacAudio != null;
    }

    /**
     * 是否有有效的视频流
     */
    public boolean hasVideo() {
        return !videoStreams.isEmpty();
    }
}
