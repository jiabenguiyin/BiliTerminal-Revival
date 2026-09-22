package com.RobinNotBad.BiliClient.activity.player;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.model.DanmakuElem;
import com.RobinNotBad.BiliClient.model.DmSegMobileReply;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.android.BiliProtobufDanmakuParser;
import master.flame.danmaku.ui.widget.DanmakuView;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DanmakuWindowRenderInstrumentedTest {
    @Test public void incrementalWindowRendersAtEightyHoursAndAfterBackwardSeek() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity host = instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        CountDownLatch prepared = new CountDownLatch(1);
        CountDownLatch shownLate = new CountDownLatch(1);
        CountDownLatch shownEarly = new CountDownLatch(1);
        AtomicReference<DanmakuView> viewRef = new AtomicReference<>();
        AtomicReference<PlayerActivity> playerRef = new AtomicReference<>();
        BiliProtobufDanmakuParser parser = new BiliProtobufDanmakuParser();
        Method apply = PlayerActivity.class.getDeclaredMethod("applyDanmakuWindow");
        apply.setAccessible(true);
        try {
            instrumentation.runOnMainSync(() -> {
                DanmakuView view = new DanmakuView(host);
                view.setBackgroundColor(Color.BLACK);
                host.setContentView(view);
                viewRef.set(view);
                PlayerActivity player = new PlayerActivity();
                playerRef.set(player);
                set(player, "mDanmakuView", view);
                set(player, "windowDanmakuParser", parser);
                view.setCallback(new DrawHandler.Callback() {
                    @Override public void prepared() { prepared.countDown(); }
                    @Override public void updateTimer(DanmakuTimer timer) {}
                    @Override public void drawingFinished() {}
                    @Override public void danmakuShown(BaseDanmaku item) {
                        if ("hour 80".contentEquals(item.text)) shownLate.countDown();
                        if ("hour 2".contentEquals(item.text)) shownEarly.countDown();
                    }
                });
                parser.setDanmakuSegments(Collections.emptyList());
                view.enableDanmakuDrawingCache(true);
                view.prepare(parser, DanmakuContext.create());
            });
            assertTrue("Renderer preparation timed out", prepared.await(15, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> {
                viewRef.get().start(288000000L);
                applyWindow(playerRef.get(), apply, 801, 288001500, "hour 80");
            });
            assertTrue("No visible danmaku at hour 80", shownLate.await(12, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> assertPainted(viewRef.get()));
            instrumentation.runOnMainSync(() -> {
                viewRef.get().seekTo(7200000L);
                applyWindow(playerRef.get(), apply, 21, 7201500, "hour 2");
            });
            assertTrue("No visible danmaku after backward seek", shownEarly.await(12, TimeUnit.SECONDS));
            instrumentation.runOnMainSync(() -> assertPainted(viewRef.get()));
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (viewRef.get() != null) viewRef.get().release();
                host.finish();
            });
        }
    }

    private static void applyWindow(PlayerActivity player, Method apply,
                                    int index, int progress, String text) {
        DanmakuElem item = new DanmakuElem();
        item.content = text;
        item.progress = progress;
        item.mode = 5;
        item.fontsize = 25;
        item.color = 0xffffff;
        DmSegMobileReply reply = new DmSegMobileReply();
        reply.elems.add(item);
        Map<Integer, DmSegMobileReply> window = new HashMap<>();
        window.put(index, reply);
        set(player, "pendingDanmakuWindow", window);
        try { apply.invoke(player); } catch (Exception e) { throw new AssertionError(e); }
    }

    private static void assertPainted(DanmakuView view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(bitmap));
            int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            int nonBlack = 0;
            for (int pixel : pixels) if ((pixel & 0xffffff) != 0) nonBlack++;
            assertTrue("Danmaku view must paint text, not just report parsed data", nonBlack > 20);
        } finally {
            bitmap.recycle();
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = PlayerActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
