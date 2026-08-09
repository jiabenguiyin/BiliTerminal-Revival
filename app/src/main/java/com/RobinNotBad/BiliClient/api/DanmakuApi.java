package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.ProtobufParser;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
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
     * @param segmentIndex 分包索引（6min 一包，从 0 开始）
     * @return 弹幕分段响应
     * @throws IOException   IO异常
     * @throws JSONException JSON异常
     */
    public static DmSegMobileReply getVideoDanmakuSegment(long aid, long cid, int segmentIndex)
            throws IOException, JSONException {
        String baseUrl = "https://api.bilibili.com/x/v2/dm/wbi/web/seg.so";

        // 构建 URL 参数
        String url = baseUrl + "?type=1" +
                "&oid=" + cid +
                "&pid=" + aid +
                "&segment_index=" + segmentIndex;

        // 使用 WBI 签名
        url = ConfInfoApi.signWBI(url);

        Logu.d("新版弹幕API", "URL: " + url);

        // 发送请求获取 protobuf 数据
        Response response = NetWorkUtil.get(url, NetWorkUtil.webHeaders);
        byte[] data = Objects.requireNonNull(response.body()).bytes();

        // 解析 protobuf 数据
        DmSegMobileReply reply = ProtobufParser.parseDmSegMobileReply(data);
        Logu.d("新版弹幕API", "分段 " + segmentIndex + " 获取到 " + reply.elems.size() + " 条弹幕");

        return reply;
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
        List<DmSegMobileReply> allSegments = new ArrayList<>();

        // 计算需要获取的分段数（每段 6 分钟 = 360 秒）
        int segmentCount = (maxDuration / 360) + 1;

        Logu.d("新版弹幕API", "视频时长: " + maxDuration + "秒, 需要获取 " + segmentCount + " 个分段");

        // 逐段获取弹幕
        for (int i = 0; i < segmentCount; i++) {
            try {
                DmSegMobileReply segment = getVideoDanmakuSegment(aid, cid, i + 1);
                if (segment != null && !segment.elems.isEmpty()) {
                    allSegments.add(segment);
                } else if (segment != null && segment.elems.isEmpty()) {
                    // 如果某一段为空，可能已经到达最后
                    Logu.d("新版弹幕API", "分段 " + (i + 1) + " 为空，停止获取");
                    break;
                }
            } catch (Exception e) {
                Logu.e("新版弹幕API", "获取分段 " + (i + 1) + " 失败: " + e.getMessage());
                // 继续尝试下一段
            }
        }

        int totalDanmaku = 0;
        for (DmSegMobileReply segment : allSegments) {
            totalDanmaku += segment.elems.size();
        }
        Logu.d("新版弹幕API", "共获取到 " + totalDanmaku + " 条弹幕");

        return allSegments;
    }
}
