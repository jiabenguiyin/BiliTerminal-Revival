package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.ProtobufParser;
import com.RobinNotBad.BiliClient.util.RequestCancellation;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.Response;

//弹幕api

public class DanmakuApi {

    public static JSONObject sendLiveDanmaku(long roomId, String msg, int color)
            throws IOException, JSONException {
        String csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
        String data = new NetWorkUtil.FormData()
                .put("bubble", 0)
                .put("msg", msg)
                .put("color", color)
                .put("mode", 1)
                .put("fontsize", 25)
                .put("rnd", System.currentTimeMillis() / 1000)
                .put("roomid", roomId)
                .put("csrf", csrf)
                .put("csrf_token", csrf)
                .toString();
        ArrayList<String> headers = new ArrayList<>();
        headers.add("Cookie");
        headers.add(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        headers.add("Origin");
        headers.add("https://live.bilibili.com");
        headers.add("Referer");
        headers.add("https://live.bilibili.com/" + roomId);
        headers.add("User-Agent");
        headers.add(NetWorkUtil.USER_AGENT_WEB);

        try (Response response = NetWorkUtil.post(
                "https://api.live.bilibili.com/msg/send", data, headers)) {
            return new JSONObject(Objects.requireNonNull(response.body()).string());
        }
    }

    // 发送弹幕
    public static int sendVideoDanmakuByBvid(long cid, String msg, String bvid, long progress, int color, int mode)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/dm/post";
        String arg = new NetWorkUtil.FormData()
                .put("type", 1).put("oid", cid).put("msg", msg).put("bvid", bvid)
                .put("progress", progress).put("color", color).put("mode", mode)
                .put("rnd", System.currentTimeMillis() * 1000000)
                .put("csrf", SharedPreferencesUtil.getString("csrf", "")).toString();
        JSONObject result = new JSONObject(
                Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Logu.i(result.toString());
        return result.getInt("code"); // https://socialsisteryi.github.io/bilibili-API-collect/docs/danmaku/action.html#%E5%8F%91%E9%80%81%E8%A7%86%E9%A2%91%E5%BC%B9%E5%B9%95
    }

    public static int sendVideoDanmakuByAid(long cid, String msg, long aid, long progress, int color, int mode)
            throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/dm/post";
        String arg = new NetWorkUtil.FormData()
                .put("type", 1).put("oid", cid).put("msg", msg).put("aid", aid)
                .put("progress", progress).put("color", color).put("mode", mode)
                .put("rnd", System.currentTimeMillis() * 1000000)
                .put("csrf", SharedPreferencesUtil.getString("csrf", "")).toString();
        JSONObject result = new JSONObject(
                Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Logu.i(result.toString());
        return result.getInt("code"); // https://socialsisteryi.github.io/bilibili-API-collect/docs/danmaku/action.html#%E5%8F%91%E9%80%81%E8%A7%86%E9%A2%91%E5%BC%B9%E5%B9%95
    }

    // 点赞弹幕
    public static int likeDanmaku(long dmid, long cid, int op) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v2/dm/thumbup/add";
        String arg = "oid=" + cid + "&dmid=" + dmid + "&op=" + op + "&platform=web_player" + "&csrf="
                + SharedPreferencesUtil.getString("csrf", "");
        JSONObject result = new JSONObject(
                Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Logu.i(result.toString());
        return result.getInt("code");
    }

    // 撤回弹幕
    public static int recallDanmaku(long dmid, long cid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/dm/recall";
        // 文档里就是cid，不是oid
        String arg = "cid=" + cid + "&dmid=" + dmid + "&csrf=" + SharedPreferencesUtil.getString("csrf", "");
        JSONObject result = new JSONObject(
                Objects.requireNonNull(NetWorkUtil.post(url, arg, NetWorkUtil.webHeaders).body()).string());
        Logu.i(result.toString());
        return result.getInt("code");
    }

    /**
     * 获取视频弹幕（新版 API，返回 protobuf 格式）
     * 使用分段获取方式，每段 6 分钟
     *
     * @param aid          稿件 avid
     * @param cid          视频 cid
     * @param segmentIndex 分包索引（6min 一包，从 1 开始）
     * @return 弹幕分段响应
     * @throws IOException   IO异常
     * @throws JSONException JSON异常
     */
    public static DmSegMobileReply getVideoDanmakuSegment(long aid, long cid, int segmentIndex)
            throws IOException, JSONException {
        return getVideoDanmakuSegment(aid, cid, segmentIndex, null);
    }

    public static DmSegMobileReply getVideoDanmakuSegment(long aid, long cid, int segmentIndex,
                                                        RequestCancellation cancellation)
            throws IOException, JSONException {
        if (cancellation != null && cancellation.isCancelled())
            throw new InterruptedIOException("Danmaku cancelled");
        String baseUrl = "https://api.bilibili.com/x/v2/dm/wbi/web/seg.so";

        // 构建 URL 参数
        String url = baseUrl + "?type=1" +
                "&oid=" + cid +
                "&pid=" + aid +
                "&segment_index=" + segmentIndex;

        // 使用 WBI 签名
        url = ConfInfoApi.signWBI(url);

        Logu.d("新版弹幕API", "URL: " + url);

        // 发送请求获取 protobuf 数据。每个分段都要及时关闭响应，
        // 否则长视频连续请求时旧设备容易耗尽连接或文件描述符。
        try (Response response = NetWorkUtil.getCancellable(url, NetWorkUtil.webHeaders, cancellation)) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Danmaku HTTP " + response.code());
            }
            byte[] data = Objects.requireNonNull(response.body()).bytes();

            // 解析 protobuf 数据
            DmSegMobileReply reply = ProtobufParser.parseDmSegMobileReply(data);
            Logu.d("新版弹幕API", "分段 " + segmentIndex + " 获取到 " + reply.elems.size() + " 条弹幕");
            return reply;
        }
    }

    /**
     * 获取视频所有弹幕（新版 API）
     * 自动分段获取所有弹幕
     *
     * @param aid         稿件 avid
     * @param cid         视频 cid
     * @param maxDuration 视频最大时长（秒），用于计算需要获取的分段数
     * @return 所有弹幕分段的列表
     * @throws IOException   IO异常
     * @throws JSONException JSON异常
     */
    public static List<DmSegMobileReply> getAllVideoDanmaku(long aid, long cid, int maxDuration)
            throws IOException, JSONException {
        return getAllVideoDanmaku(aid, cid, maxDuration, () -> false);
    }

    public interface Cancellation {
        boolean isCancelled();
    }

    interface SegmentSource {
        DmSegMobileReply get(int index) throws IOException, JSONException;
    }

    public static List<DmSegMobileReply> getAllVideoDanmaku(
            long aid, long cid, int maxDuration, Cancellation cancellation) throws IOException {
        return loadSegments(maxDuration, index -> {
            if (cancellation.isCancelled()) throw new InterruptedIOException("Danmaku cancelled");
            try {
                return getVideoDanmakuSegment(aid, cid, index);
            } catch (IOException | JSONException e) {
                Logu.e("新版弹幕API", "获取分段 " + index + " 失败: " + e.getMessage());
                throw e;
            }
        });
    }

    static List<DmSegMobileReply> loadSegments(int maxDuration, SegmentSource source) throws IOException {
        List<DmSegMobileReply> allSegments = new ArrayList<>();

        // 计算需要获取的分段数（每段 6 分钟 = 360 秒）。
        // 使用向上取整，避免整除时多发一个无意义请求。
        int segmentCount = calculateSegmentCount(maxDuration);

        Logu.d("新版弹幕API", "视频时长: " + maxDuration + "秒, 需要获取 " + segmentCount + " 个分段");

        // Empty intervals are valid; only the duration determines the final segment.
        for (int i = 0; i < segmentCount; i++) {
            for (int attempt = 0; attempt < 2; attempt++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Danmaku interrupted");
                }
                try {
                    DmSegMobileReply segment = source.get(i + 1);
                    if (segment != null && !segment.elems.isEmpty()) allSegments.add(segment);
                    break;
                } catch (InterruptedIOException e) {
                    // Socket timeouts may be retried; cancellation must stop immediately.
                    if (!(e instanceof java.net.SocketTimeoutException)) throw e;
                } catch (IOException | JSONException ignored) {
                    // Retry once, then continue without discarding successful segments.
                }
            }
        }

        int totalDanmaku = 0;
        for (DmSegMobileReply segment : allSegments) {
            totalDanmaku += segment.elems.size();
        }
        Logu.d("新版弹幕API", "共获取到 " + totalDanmaku + " 条弹幕");

        return allSegments;
    }

    static int calculateSegmentCount(int maxDuration) {
        if (maxDuration <= 0) return 1;
        return (int) (((long) maxDuration + 359L) / 360L);
    }
}
