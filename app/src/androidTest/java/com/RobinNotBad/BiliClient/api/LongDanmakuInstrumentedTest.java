package com.RobinNotBad.BiliClient.api;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.player.DanmakuWindowLoader;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.protobuf.CodedOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.android.BiliProtobufDanmakuParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LongDanmakuInstrumentedTest {
    @Test public void hundredHourApiOnlyFetchesPlaybackWindowAndParsesEightyHourTimestamp()
            throws Exception {
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<OkHttpClient> instance = (AtomicReference<OkHttpClient>) field.get(null);
        OkHttpClient previous = instance.get();
        boolean relay = NetWorkUtil.BILI_RELAY_ENABLE;
        String oldWbi = SharedPreferencesUtil.getString("wbi_mixin_key", "");
        int oldDate = SharedPreferencesUtil.getInt("last_wbi", 0);
        List<Integer> requests = new ArrayList<>();
        List<Map<Integer, DmSegMobileReply>> delivered = new ArrayList<>();
        byte[] late = segment(288001000, "hour 80");
        OkHttpClient fake = new OkHttpClient.Builder().addInterceptor(chain -> {
            int index = Integer.parseInt(chain.request().url().queryParameter("segment_index"));
            requests.add(index);
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("test").body(ResponseBody.create(
                            MediaType.parse("application/octet-stream"),
                            index == 801 ? late : new byte[0])).build();
        }).build();
        DanmakuWindowLoader loader = new DanmakuWindowLoader(Runnable::run,
                (index, cancellation) -> DanmakuApi.getVideoDanmakuSegment(1, 2, index, cancellation),
                (revision, window) -> delivered.add(window), android.os.SystemClock::elapsedRealtime);
        try {
            NetWorkUtil.BILI_RELAY_ENABLE = false;
            SharedPreferencesUtil.putString("wbi_mixin_key", "0123456789abcdef0123456789abcdef");
            SharedPreferencesUtil.putInt("last_wbi", ConfInfoApi.getDateCurr());
            instance.set(fake);
            loader.update(288000000L, 360000000L);
            assertEquals(Arrays.asList(801, 802, 800), requests);
            assertEquals(1, delivered.get(1).size());
            assertEquals(1, delivered.get(1).get(801).elems.size());
            BiliProtobufDanmakuParser parser = new BiliProtobufDanmakuParser();
            DanmakuContext context = DanmakuContext.create();
            context.getDisplayer().setSize(240, 240);
            parser.setConfig(context).setDisplayer(context.getDisplayer()).setTimer(new DanmakuTimer());
            parser.setDanmakuSegments(new ArrayList<>(delivered.get(3).values()));
            assertEquals(288001000L, parser.getDanmakus().first().time);
            loader.update(288002000L, 360000000L);
            assertEquals(3, requests.size());
        } finally {
            loader.close();
            instance.set(previous);
            NetWorkUtil.BILI_RELAY_ENABLE = relay;
            SharedPreferencesUtil.putString("wbi_mixin_key", oldWbi);
            SharedPreferencesUtil.putInt("last_wbi", oldDate);
        }
    }

    @Test public void twoHourResponseSurvivesEmptyAndFailedSegmentsAndParsesLateTimestamps()
            throws Exception {
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<OkHttpClient> instance = (AtomicReference<OkHttpClient>) field.get(null);
        OkHttpClient previous = instance.get();
        boolean relay = NetWorkUtil.BILI_RELAY_ENABLE;
        String oldWbi = SharedPreferencesUtil.getString("wbi_mixin_key", "");
        int oldDate = SharedPreferencesUtil.getInt("last_wbi", 0);
        List<Integer> requests = new ArrayList<>();
        byte[] late = segment(3961000, "second hour");
        byte[] last = segment(6841000, "near end");
        OkHttpClient fake = new OkHttpClient.Builder().addInterceptor(chain -> {
            String value = chain.request().url().queryParameter("segment_index");
            if (value == null) throw new java.io.IOException("Unexpected test request");
            int index = Integer.parseInt(value);
            requests.add(index);
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(index == 3 ? 503 : 200).message("test")
                    .body(ResponseBody.create(MediaType.parse("application/octet-stream"),
                            index == 12 ? late : index == 20 ? last : new byte[0])).build();
        }).build();
        try {
            NetWorkUtil.BILI_RELAY_ENABLE = false;
            SharedPreferencesUtil.putString("wbi_mixin_key", "0123456789abcdef0123456789abcdef");
            SharedPreferencesUtil.putInt("last_wbi", ConfInfoApi.getDateCurr());
            instance.set(fake);
            List<DmSegMobileReply> segments = DanmakuApi.getAllVideoDanmaku(1, 2, 7200);
            assertEquals(21, requests.size());
            assertEquals(Integer.valueOf(20), requests.get(requests.size() - 1));
            assertEquals(2, segments.size());
            BiliProtobufDanmakuParser parser = new BiliProtobufDanmakuParser();
            DanmakuContext context = DanmakuContext.create();
            context.getDisplayer().setSize(320, 320);
            parser.setConfig(context).setDisplayer(context.getDisplayer());
            parser.setTimer(new DanmakuTimer());
            parser.setDanmakuSegments(segments);
            IDanmakus parsed = parser.getDanmakus();
            assertEquals(2, parsed.size());
            assertEquals(3961000L, parsed.first().time);
            assertEquals(6841000L, parsed.last().time);
        } finally {
            instance.set(previous);
            NetWorkUtil.BILI_RELAY_ENABLE = relay;
            SharedPreferencesUtil.putString("wbi_mixin_key", oldWbi);
            SharedPreferencesUtil.putInt("last_wbi", oldDate);
        }
    }

    private static byte[] segment(int progress, String text) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream elem = CodedOutputStream.newInstance(bytes);
        elem.writeInt32(2, progress);
        elem.writeInt32(3, 1);
        elem.writeInt32(4, 25);
        elem.writeUInt32(5, 0xffffff);
        elem.writeString(7, text);
        elem.flush();
        byte[] payload = bytes.toByteArray();
        bytes.reset();
        CodedOutputStream reply = CodedOutputStream.newInstance(bytes);
        reply.writeByteArray(1, payload);
        reply.flush();
        return bytes.toByteArray();
    }
}
