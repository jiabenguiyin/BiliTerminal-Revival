package com.RobinNotBad.BiliClient.api;

import android.os.Build;
import android.util.Base64;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Direct Bilibili BAPI translation calls; comment text is never sent to a third party. */
public final class CommentTranslationApi {
    private static final String ENDPOINT =
            "https://grpc.biliapi.net/bilibili.main.community.reply.v1.Reply/TranslateReply";
    private static final String CONTENT_TYPE = "application/grpc+proto";
    private static volatile OkHttpClient client;

    private CommentTranslationApi() {
    }

    public static String translate(long oid, int type, long rpid) throws IOException {
        if (oid <= 0 || type <= 0 || rpid <= 0) throw new IllegalArgumentException("无效的评论翻译目标");

        String accessKey = getBoundAccessKey();
        String buvid = cookieValue("buvid3");
        byte[] requestMessage = encodeRequest(oid, type, rpid);
        Request.Builder request = new Request.Builder()
                .url(ENDPOINT)
                .header("Content-Type", CONTENT_TYPE)
                .header("TE", "trailers")
                .header("User-Agent", AppRecommendApi.appUserAgent())
                .header("Authorization", "identify_v1 " + accessKey)
                .header("x-bili-metadata-bin", encodeMetadata(accessKey, buvid))
                .header("x-bili-device-bin", encodeDevice(buvid));
        if (!buvid.isEmpty()) request.header("x-bili-buvid", buvid);
        request.post(RequestBody.create(MediaType.parse(CONTENT_TYPE), frame(requestMessage)));

        try (Response response = getClient().newCall(request.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("B站评论翻译 HTTP " + response.code());
            String status = response.header("grpc-status");
            if (status != null && !"0".equals(status)) {
                throw new IOException("B站评论翻译服务状态 " + status);
            }
            if (response.body() == null) throw new IOException("评论翻译没有返回内容");
            return parseResponse(response.body().bytes(), rpid);
        }
    }

    private static OkHttpClient getClient() {
        OkHttpClient current = client;
        if (current != null) return current;
        synchronized (CommentTranslationApi.class) {
            current = client;
            if (current == null) {
                client = current = NetWorkUtil.setOkHttpSsl(new OkHttpClient.Builder())
                        .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .connectTimeout(8, TimeUnit.SECONDS)
                        .readTimeout(16, TimeUnit.SECONDS)
                        .writeTimeout(8, TimeUnit.SECONDS)
                        .build();
            }
        }
        return current;
    }

    private static String getBoundAccessKey() throws IOException {
        String accessKey = SharedPreferencesUtil.getString(
                SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY, "").trim();
        long expiresAt = SharedPreferencesUtil.getLong(
                SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY_EXPIRES_AT, 0L);
        long boundMid = SharedPreferencesUtil.getLong(
                SharedPreferencesUtil.APP_RECOMMEND_BOUND_MID, 0L);
        long currentMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0L);

        if (accessKey.isEmpty() || boundMid <= 0 || (expiresAt > 0 && System.currentTimeMillis() >= expiresAt)) {
            throw new IOException("请先在设置-实验室绑定 App 账号后使用评论翻译");
        }
        if (currentMid <= 0) {
            throw new IOException("请先登录，再使用评论翻译");
        }
        if (currentMid != boundMid) {
            throw new IOException("当前账号与实验室绑定的 App 账号不同，请重新绑定后再翻译");
        }
        return accessKey;
    }

    static byte[] encodeRequest(long oid, int type, long rpid) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(32);
        CodedOutputStream coded = CodedOutputStream.newInstance(output);
        coded.writeInt64(1, type);
        coded.writeInt64(2, oid);
        coded.writeInt64(3, rpid);
        coded.flush();
        return output.toByteArray();
    }

    private static String encodeMetadata(String accessKey, String buvid) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(128);
        CodedOutputStream coded = CodedOutputStream.newInstance(output);
        coded.writeString(1, accessKey);
        coded.writeString(2, "android");
        coded.writeString(3, "phone");
        coded.writeInt32(4, 9161100);
        coded.writeString(5, "master");
        if (!buvid.isEmpty()) coded.writeString(6, buvid);
        coded.writeString(7, "android");
        coded.flush();
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String encodeDevice(String buvid) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(160);
        CodedOutputStream coded = CodedOutputStream.newInstance(output);
        coded.writeInt32(1, 1);
        coded.writeInt32(2, 9161100);
        if (!buvid.isEmpty()) coded.writeString(3, buvid);
        coded.writeString(4, "android");
        coded.writeString(5, "android");
        coded.writeString(6, "phone");
        coded.writeString(7, "master");
        if (Build.BRAND != null && !Build.BRAND.isEmpty()) coded.writeString(8, Build.BRAND);
        if (Build.MODEL != null && !Build.MODEL.isEmpty()) coded.writeString(9, Build.MODEL);
        if (Build.VERSION.RELEASE != null) coded.writeString(10, Build.VERSION.RELEASE);
        coded.flush();
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static byte[] frame(byte[] message) {
        int size = message.length;
        byte[] framed = new byte[size + 5];
        framed[0] = 0;
        framed[1] = (byte) (size >>> 24);
        framed[2] = (byte) (size >>> 16);
        framed[3] = (byte) (size >>> 8);
        framed[4] = (byte) size;
        System.arraycopy(message, 0, framed, 5, size);
        return framed;
    }

    static String parseResponse(byte[] framed, long requestedRpid) throws IOException {
        if (framed == null || framed.length < 5) throw new IOException("评论翻译响应不完整");
        if (framed[0] != 0) throw new IOException("评论翻译响应使用了不支持的压缩格式");
        int messageSize = ((framed[1] & 255) << 24) | ((framed[2] & 255) << 16)
                | ((framed[3] & 255) << 8) | (framed[4] & 255);
        if (messageSize < 0 || messageSize != framed.length - 5) {
            throw new IOException("评论翻译响应长度无效");
        }

        CodedInputStream response = CodedInputStream.newInstance(framed, 5, messageSize);
        while (!response.isAtEnd()) {
            int tag = response.readTag();
            if (tag == 0) break;
            if (WireFormat.getTagFieldNumber(tag) == 1
                    && WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                String translation = parseReplyEntry(response.readByteArray(), requestedRpid);
                if (translation != null && !translation.trim().isEmpty()) return translation.trim();
            } else if (!response.skipField(tag)) {
                break;
            }
        }
        throw new IOException("B站没有返回这条评论的译文");
    }

    private static String parseReplyEntry(byte[] entryBytes, long requestedRpid) throws IOException {
        CodedInputStream entry = CodedInputStream.newInstance(entryBytes);
        long rpid = 0;
        byte[] replyInfo = null;
        while (!entry.isAtEnd()) {
            int tag = entry.readTag();
            if (tag == 0) break;
            int field = WireFormat.getTagFieldNumber(tag);
            int wire = WireFormat.getTagWireType(tag);
            if (field == 1 && wire == WireFormat.WIRETYPE_VARINT) rpid = entry.readInt64();
            else if (field == 2 && wire == WireFormat.WIRETYPE_LENGTH_DELIMITED) replyInfo = entry.readByteArray();
            else if (!entry.skipField(tag)) break;
        }
        if (rpid != requestedRpid || replyInfo == null) return null;

        CodedInputStream translatedReply = CodedInputStream.newInstance(replyInfo);
        while (!translatedReply.isAtEnd()) {
            int tag = translatedReply.readTag();
            if (tag == 0) break;
            if (WireFormat.getTagFieldNumber(tag) == 17
                    && WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                return parseContentMessage(translatedReply.readByteArray());
            }
            if (!translatedReply.skipField(tag)) break;
        }
        return null;
    }

    private static String parseContentMessage(byte[] contentBytes) throws IOException {
        CodedInputStream content = CodedInputStream.newInstance(contentBytes);
        while (!content.isAtEnd()) {
            int tag = content.readTag();
            if (tag == 0) break;
            if (WireFormat.getTagFieldNumber(tag) == 1
                    && WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                return content.readStringRequireUtf8();
            }
            if (!content.skipField(tag)) break;
        }
        return null;
    }

    private static String cookieValue(String name) {
        try {
            String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            String value = NetWorkUtil.getInfoFromCookie(name, cookies);
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }
}
