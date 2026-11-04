package com.RobinNotBad.BiliClient.activity.player;

import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.netease.hearttouch.brotlij.Brotli;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Inflater;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class PlayerDanmuClientListener extends WebSocketListener {
    public long mid = 0;
    public long roomid = 0;
    public String key = "";
    private int seq = 1;
    private final MessageData messageData = new MessageData();

    private Timer heartTimer = null;

    public PlayerActivity playerActivity;


    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        super.onOpen(webSocket, response);
        Log.e("debug", "WebSocket已连接");

        if (heartTimer != null) heartTimer.cancel();

        //发送认证包
        try {
            JSONObject object = new JSONObject();
            if (SharedPreferencesUtil.getBoolean("live_by_guest", false)) object.put("uid", 0);
            else object.put("uid", mid);
            object.put("roomid", roomid);
            object.put("protover", 3);
            object.put("platform", "web");
            String buvid = NetWorkUtil.getCookies().get("buvid3");
            object.put("buvid", buvid == null ? "" : buvid);
            object.put("type", 2);
            object.put("key", key);

            webSocket.send(messageData.getData(3, 7, object.toString().getBytes(Charset.forName("UTF-8"))));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class MessageData {

        private byte[] getPacket(int protocolVersion, int actionCode, byte... data) {
            int headerSize = 16;
            int totalSize = headerSize + data.length;
            byte[] packet = new byte[totalSize];

            packet[0] = (byte) (totalSize >> 24);
            packet[1] = (byte) (totalSize >> 16);
            packet[2] = (byte) (totalSize >> 8);
            packet[3] = (byte) (totalSize);

            packet[4] = (byte) (0);
            packet[5] = (byte) (headerSize);

            packet[6] = (byte) 0;
            packet[7] = (byte) protocolVersion;

            packet[8] = (byte) 0;
            packet[9] = (byte) 0;
            packet[10] = (byte) 0;
            packet[11] = (byte) actionCode;

            packet[12] = (byte) (seq >> 24);
            packet[13] = (byte) (seq >> 16);
            packet[14] = (byte) (seq >> 8);
            packet[15] = (byte) (seq);
            seq++;

            System.arraycopy(data, 0, packet, headerSize, data.length);
            Log.d("BiliClient", "getPackage totalLen=" + totalSize + ", data=" + new String(data) + ", result=" + ByteString.of(packet).hex());
            return packet;
        }

        public ByteString getData(int protocolVersion, int actionCode, byte... data) {
            return ByteString.of(getPacket(protocolVersion, actionCode, data));
        }

        public ByteString getBrotliData(int protocolVersion, int actionCode, byte... data) {
            byte[] encodedData = Brotli.compress(getPacket(protocolVersion, actionCode, data));
            return ByteString.of(getPacket(3, actionCode, encodedData));
        }

    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
        super.onMessage(webSocket, bytes);
        handlePackets(webSocket, bytes.toByteArray());
    }

    @Override
    public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        super.onClosed(webSocket, code, reason);
        Log.e("debug", "WebSocket连接关闭：" + reason + "(" + code + ")");

        if (heartTimer != null) heartTimer.cancel();
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
        super.onFailure(webSocket, t, response);

        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        t.printStackTrace(printWriter);

        Log.e("debug", "WebSocket连接失败：" + writer);

        if (heartTimer != null) heartTimer.cancel();
    }

    private void handlePackets(WebSocket webSocket, byte[] packetBytes) {
        int offset = 0;
        while (offset + 16 <= packetBytes.length) {
            int packetLength = readInt(packetBytes, offset);
            int headerLength = readUnsignedShort(packetBytes, offset + 4);
            int protocolVersion = readUnsignedShort(packetBytes, offset + 6);
            int actionCode = readInt(packetBytes, offset + 8);
            if (packetLength < headerLength || headerLength < 16
                    || packetLength > packetBytes.length - offset) {
                Log.e("debug", "直播弹幕数据包长度异常：" + packetLength);
                return;
            }

            byte[] body = Arrays.copyOfRange(packetBytes, offset + headerLength, offset + packetLength);
            if (actionCode == 8) {
                startHeartbeat(webSocket);
            } else if (actionCode == 5) {
                if (protocolVersion == 3) {
                    try {
                        handlePackets(webSocket, Brotli.decompress(body));
                    } catch (Exception error) {
                        Log.e("debug", "直播弹幕 Brotli 解压失败", error);
                    }
                } else if (protocolVersion == 2) {
                    try {
                        handlePackets(webSocket, inflate(body));
                    } catch (Exception error) {
                        Log.e("debug", "直播弹幕压缩数据解压失败", error);
                    }
                } else {
                    handleJson(body);
                }
            } else if (actionCode == 3 && body.length >= 4 && playerActivity != null) {
                long online = readUnsignedInt(body, 0);
                playerActivity.runOnUiThread(() -> playerActivity.online_number = String.valueOf(online));
            }
            offset += packetLength;
        }
    }

    private void startHeartbeat(WebSocket webSocket) {
        if (heartTimer != null) heartTimer.cancel();
        Log.e("debug", "弹幕流认证成功");
        heartTimer = new Timer("live-danmaku-heartbeat", true);
        heartTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    webSocket.send(messageData.getData(1, 2,
                            "".getBytes(Charset.forName("UTF-8"))));
                } catch (Exception e) {
                    Log.e("debug", "发送直播弹幕心跳失败", e);
                }
            }
        }, 3000, 32000);
    }

    private byte[] inflate(byte[] data) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream output = new ByteArrayOutputStream(data.length * 2);
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) break;
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private int readUnsignedShort(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    private int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private long readUnsignedInt(byte[] data, int offset) {
        return ((long) (data[offset] & 0xff) << 24)
                | ((long) (data[offset + 1] & 0xff) << 16)
                | ((long) (data[offset + 2] & 0xff) << 8)
                | (long) (data[offset + 3] & 0xff);
    }

    // 解压后的直播包可能连续拼接多条 JSON，逐个对象解析，避免后一条消息被丢弃。
    private void handleJson(byte[] body) {
        String text = new String(body, Charset.forName("UTF-8"));
        int objectStart = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (objectStart < 0) {
                if (current == '{') {
                    objectStart = i;
                    depth = 1;
                }
                continue;
            }

            if (inString) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }

            if (current == '"') inString = true;
            else if (current == '{') depth++;
            else if (current == '}' && --depth == 0) {
                handleJsonObject(text.substring(objectStart, i + 1));
                objectStart = -1;
            }
        }
    }

    private void handleJsonObject(String json) {
        try {
            JSONObject result = new JSONObject(json);

            JSONObject data;
            String command = result.getString("cmd");
            if (command.startsWith("DANMU_MSG:")) command = "DANMU_MSG";
            switch (command) {

                //聊天弹幕
                case "DANMU_MSG":
                    if (playerActivity == null) break;
                    JSONArray info = result.getJSONArray("info");
                    JSONArray member = info.optJSONArray(2);
                    JSONArray options = info.optJSONArray(0);
                    JSONObject senderExtra = options == null ? null : options.optJSONObject(15);
                    JSONObject sender = senderExtra == null ? null : senderExtra.optJSONObject("user");
                    JSONObject base = sender == null ? null : sender.optJSONObject("base");
                    long senderMid = member == null ? 0 : member.optLong(0, 0);
                    if (senderMid <= 0 && sender != null) senderMid = sender.optLong("uid", sender.optLong("mid", 0));
                    String nickname = base == null ? "" : base.optString("name", "");
                    if (nickname.isEmpty() && member != null) nickname = member.optString(1, "");
                    String content = info.getString(1);
                    playerActivity.addLiveChatDanmaku(nickname, content, senderMid);

                    Log.e("debug", "pkg_dm");
                    break;

                //看过的人数
                case "WATCHED_CHANGE":
                    data = result.getJSONObject("data");
                    if (playerActivity != null) {
                        String online = data.getString("text_large");
                        playerActivity.runOnUiThread(() -> playerActivity.online_number = online);
                    }
                    break;

                case "INTERACT_WORD":
                    if (playerActivity == null) break;
                    data = result.getJSONObject("data");

                    //进入直播间
                    if (data.getInt("msg_type") == 1)
                        playerActivity.addDanmaku(data.getString("uname") + " 进入了直播间", Color.CYAN, 12, 4, 0);

                    break;

                //送礼弹幕
                case "SEND_GIFT":
                    if (playerActivity == null) break;
                    data = result.getJSONObject("data");
                    String content2 = data.getString("uname") + " " + data.getString("action") + data.getInt("num") + "个" + data.getString("giftName");
                    playerActivity.addDanmaku(content2, Color.WHITE, 25, 1, Color.argb(160, 255, 80, 80));
                    break;

                //特殊入场
                case "ENTRY_EFFECT":
                    if (playerActivity == null) break;
                    data = result.getJSONObject("data");
                    playerActivity.addDanmaku(data.getString("copy_writing").replace("<%", "").replace("%>", ""), Color.WHITE, 25, 1, Color.argb(160, 80, 80, 255));
                    break;

                //通知消息
                case "NOTICE_MSG":
                    if (playerActivity == null) break;
                    playerActivity.addDanmaku(result.getString("msg_common"), Color.RED, 25, 1, Color.argb(60, 255, 255, 255));
                    break;

                //直播间消息修改
                case "ROOM_CHANGE":
                    if (playerActivity == null) break;
                    data = result.getJSONObject("data");
                    playerActivity.runOnUiThread(() -> {
                        try {
                            playerActivity.text_title.setText(data.getString("title"));
                        } catch (Exception ignore) {
                        }
                    });
                    break;

                default:
                    break;
            }

        } catch (Exception e) {
            Writer writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);

            Log.e("debug", "解析普通包时错误：" + writer);
        }
    }
}
