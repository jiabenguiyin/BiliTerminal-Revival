package com.RobinNotBad.BiliClient.activity.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.util.PlayerSettingsUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDanmakuIterator;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.ui.widget.DanmakuView;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;
import tv.danmaku.ijk.media.player.IMediaPlayer;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LiveDanmakuTimingInstrumentedTest {
    @Test public void liveDefaultsToLongerReadableDurationIndependentlyOfVideoSpeed() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        SharedPreferences preferences = SharedPreferencesUtil.getSharedPreferences();
        Map<String, ?> before = preferences.getAll();
        String liveKey = PlayerSettingsUtil.LIVE_DANMAKU_DURATION_FACTOR;
        preferences.edit().remove(liveKey).putFloat("player_danmaku_speed", 0.5f).commit();
        TestActivity host = (TestActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<DanmakuView> view = new AtomicReference<>();
        AtomicReference<PlayerActivity> player = new AtomicReference<>();
        try {
            for (boolean live : new boolean[]{false, true}) {
                instrumentation.runOnMainSync(() -> {
                    if (view.get() != null) view.get().release();
                    DanmakuView target = new DanmakuView(host);
                    target.setBackgroundColor(Color.BLACK);
                    host.setContentView(target);
                    view.set(target);
                    PlayerActivity controller = new PlayerActivity();
                    player.set(controller);
                    set(controller, "mDanmakuView", target);
                    set(controller, "isDanmakuVisible", true);
                    set(controller, "isLiveMode", live);
                    set(controller, "liveWebSocket", new NoNetworkWebSocket());
                    set(controller, "ijkPlayer", Proxy.newProxyInstance(IMediaPlayer.class.getClassLoader(),
                            new Class<?>[]{IMediaPlayer.class}, (proxy, method, args) -> {
                                if (method.getReturnType() == long.class) return 0L;
                                if (method.getReturnType() == int.class) return 0;
                                if (method.getReturnType() == boolean.class) return false;
                                return null;
                            }));
                    invoke(controller, "streamDanmaku", new Class<?>[]{String.class}, new Object[]{null});
                });
                long deadline = SystemClock.uptimeMillis() + 15000L;
                while (!view.get().isPrepared() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50L);
                assertTrue("Renderer preparation timed out", view.get().isPrepared());
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(() -> {
                    DanmakuContext context = (DanmakuContext) get(player.get(), "mContext");
                    assertEquals(live ? 2f : 0.5f, context.scrollSpeedFactor, 0.001f);
                    BaseDanmaku item = context.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL);
                    assertEquals((long) (context.mDanmakuFactory.REAL_DANMAKU_DURATION
                            * (live ? 2f : 0.5f)), item.duration.value);
                    if (live) assertTrue("Default live duration should be at least eight seconds", item.duration.value >= 8000L);
                    view.get().start(0L);
                    view.get().removeAllDanmakus(true);
                    player.get().addDanmaku("readable-live-test", Color.WHITE);
                });
                if (live) {
                    SystemClock.sleep(5500L);
                    instrumentation.runOnMainSync(() -> {
                        IDanmakus current = view.get().getCurrentVisibleDanmakus();
                        assertNotNull("No visible live danmaku after five seconds", current);
                        boolean found = false;
                        IDanmakuIterator iterator = current.iterator();
                        while (iterator.hasNext()) {
                            if ("readable-live-test".contentEquals(iterator.next().text)) found = true;
                        }
                        assertTrue("Live danmaku disappeared too quickly", found);
                    });
                }
            }
            preferences.edit().putFloat(liveKey, 3f).commit();
            assertEquals(3f, PlayerSettingsUtil.getDanmakuDurationFactor(true), 0.001f);
            assertEquals(0.5f, PlayerSettingsUtil.getDanmakuDurationFactor(false), 0.001f);
            preferences.edit().putFloat(liveKey, Float.NaN).commit();
            assertEquals(2f, PlayerSettingsUtil.getDanmakuDurationFactor(true), 0.001f);
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (view.get() != null) view.get().release();
                host.finish();
            });
            SharedPreferences.Editor restore = preferences.edit();
            for (String key : new String[]{liveKey, "player_danmaku_speed"}) {
                Object value = before.get(key);
                if (value == null) restore.remove(key);
                else if (value instanceof Float) restore.putFloat(key, (Float) value);
                else if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
            }
            restore.commit();
        }
    }

    private static Object get(Object target, String name) {
        try {
            Field field = PlayerActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = PlayerActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object[] args) {
        try {
            Method method = PlayerActivity.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, args);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private static final class NoNetworkWebSocket implements WebSocket {
        @Override public Request request() { return new Request.Builder().url("https://example.invalid").build(); }
        @Override public long queueSize() { return 0; }
        @Override public boolean send(String text) { return false; }
        @Override public boolean send(ByteString bytes) { return false; }
        @Override public boolean close(int code, String reason) { return true; }
        @Override public void cancel() {}
    }
}
