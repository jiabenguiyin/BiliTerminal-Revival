package com.RobinNotBad.BiliClient.activity.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DanmakuLoadingInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> preferences = new HashMap<>();
    private final AtomicInteger segmentRequests = new AtomicInteger();
    private final AtomicInteger metadataRequests = new AtomicInteger();
    private final AtomicInteger xmlRequests = new AtomicInteger();
    private final AtomicReference<String> lastSegmentCid = new AtomicReference<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile String blocked = "";
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient oldClient;
    private PlayerActivity player;
    private File media, xml;

    @Before public void setUp() throws Exception {
        preference(SharedPreferencesUtil.PLAYER_LARGE_DANMAKU_GUARD, true);
        preference(SharedPreferencesUtil.NEW_DANMAKU_API, true);
        preference("pref_switch_danmaku", true);
        preference("player_autolandscape", false);
        preference("player_subtitle_autoshow", false);
        preference("player_audio_only", false);
        preference("player_ui_showDanmakuBtn", true);
        Field instance = NetWorkUtil.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) instance.get(null);
        oldClient = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            String kind;
            byte[] body;
            if (path.endsWith("seg.so")) {
                segmentRequests.incrementAndGet();
                lastSegmentCid.set(chain.request().url().queryParameter("oid"));
                kind = "segment";
                // One ordinary comment: progress=1000, mode=1, fontsize=25, text="test".
                body = new byte[]{10, 14, 8, 1, 16, (byte)232, 7, 24, 1, 32, 25, 58, 4, 116, 101, 115, 116};
                body[1] = (byte)(body.length - 2);
            } else if (path.endsWith("/view")) {
                metadataRequests.incrementAndGet();
                kind = "metadata";
                body = "{\"code\":0,\"data\":{\"stat\":{\"danmaku\":6001}}}".getBytes("UTF-8");
            } else if (path.endsWith(".xml")) {
                xmlRequests.incrementAndGet();
                kind = "xml";
                body = "<i/>".getBytes("UTF-8");
            } else {
                kind = "other";
                body = "{\"code\":0,\"data\":{}}".getBytes("UTF-8");
            }
            if (blocked.equals(kind)) {
                entered.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/octet-stream"), body)).build();
        }).build());
        media = new File(instrumentation.getTargetContext().getCacheDir(), "danmaku-loading-test.mp4");
        try (InputStream input = instrumentation.getContext().getAssets().open("player-gesture.mp4");
             FileOutputStream output = new FileOutputStream(media)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) >= 0) output.write(buffer, 0, n);
        }
        player = (PlayerActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), PlayerActivity.class)
                .putExtra("url", media.getAbsolutePath()).putExtra("title", "Danmaku loading checks")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> (Boolean) field("isPrepared") && player.findViewById(R.id.danmaku_btn).hasOnClickListeners());
        instrumentation.runOnMainSync(() -> invoke("playerPause"));
    }

    @After public void tearDown() {
        release.countDown();
        if (player != null) {
            instrumentation.runOnMainSync(player::finish);
            instrumentation.waitForIdleSync();
        }
        if (clients != null) clients.set(oldClient);
        for (Map.Entry<String, Object> entry : preferences.entrySet()) {
            if (entry.getValue() == null) SharedPreferencesUtil.removeValue(entry.getKey());
            else SharedPreferencesUtil.putBoolean(entry.getKey(), (Boolean) entry.getValue());
        }
        if (media != null) media.delete();
        if (xml != null) xml.delete();
    }

    @Test public void largeKnownVideoAllowsManualLoadOffAndReload() {
        configure(5001);
        assertFalse((Boolean) field("isDanmakuVisible"));
        assertEquals(0, metadataRequests.get() + segmentRequests.get() + xmlRequests.get());
        assertTrue("Automatic suppression changed the saved preference", SharedPreferencesUtil.getBoolean("pref_switch_danmaku", false));
        toggle();
        await(() -> segmentRequests.get() > 0 && prepared());
        assertNotNull(field("pendingDanmakuWindow"));
        toggle();
        assertReleased();
        int before = segmentRequests.get();
        instrumentation.runOnMainSync(() -> invoke("downdanmu"));
        SystemClock.sleep(200);
        assertEquals(before, segmentRequests.get());
        toggle();
        await(() -> segmentRequests.get() > before && prepared());
    }

    @Test public void savedOffSkipsMetadataAndAllContentRequests() {
        SharedPreferencesUtil.putBoolean("pref_switch_danmaku", false);
        configure(-1);
        SystemClock.sleep(200);
        assertEquals(0, metadataRequests.get() + segmentRequests.get() + xmlRequests.get());
        assertReleased();
    }

    @Test public void exactlyFiveThousandLoadsWithoutAnotherMetadataRequest() {
        configure(5000);
        await(() -> segmentRequests.get() > 0 && prepared());
        assertTrue((Boolean) field("isDanmakuVisible"));
        assertEquals(0, metadataRequests.get());
    }

    @Test public void unknownCountIsResolvedBeforeAnyContentRequest() {
        configure(-1);
        await(() -> (Long) field("knownDanmakuCount") == 6001L);
        assertEquals(1, metadataRequests.get());
        assertEquals(0, segmentRequests.get() + xmlRequests.get());
        assertReleased();
    }

    @Test public void manualEnableWinsLateMetadata() throws Exception {
        blocked = "metadata";
        configure(-1);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        toggle();
        await(() -> segmentRequests.get() > 0 && prepared());
        release.countDown();
        SystemClock.sleep(300);
        assertTrue((Boolean) field("isDanmakuVisible"));
        assertEquals(-1L, field("knownDanmakuCount"));
    }

    @Test public void disableCancelsSegmentAndRejectsLateData() throws Exception {
        blocked = "segment";
        configure(5000);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        toggle();
        assertReleased();
        release.countDown();
        SystemClock.sleep(300);
        assertReleased();
    }

    @Test public void disableAlsoCancelsLegacyXmlDownload() throws Exception {
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.NEW_DANMAKU_API, false);
        blocked = "xml";
        configure(5000);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        toggle();
        assertReleased();
        assertNull(field("danmakuXmlRequest"));
        release.countDown();
        SystemClock.sleep(300);
        assertReleased();
    }

    @Test public void sourceChangeResetsManualOverrideAndRejectsOldContent() throws Exception {
        blocked = "segment";
        configure(5001);
        toggle();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> {
            set("cid", 124L);
            set("danmaku_url", "https://comment.bilibili.com/124.xml");
            invoke("resetDanmakuSource", long.class, 6001L);
            invoke("downdanmu");
        });
        release.countDown();
        SystemClock.sleep(300);
        assertReleased();
        assertTrue(SharedPreferencesUtil.getBoolean("pref_switch_danmaku", false));
        int before = segmentRequests.get();
        toggle();
        await(() -> segmentRequests.get() > before && prepared());
        assertEquals("124", lastSegmentCid.get());
    }

    @Test public void audioOnlySuspendsLoadingAndReturningRestoresThePreference() {
        configure(5000);
        await(() -> segmentRequests.get() > 0 && prepared());
        instrumentation.runOnMainSync(() -> {
            set("isAudioOnlyMode", true);
            invoke("updateAudioOnlyUI");
        });
        assertReleased();
        assertTrue(SharedPreferencesUtil.getBoolean("pref_switch_danmaku", false));
        int before = segmentRequests.get();
        instrumentation.runOnMainSync(() -> {
            set("isAudioOnlyMode", false);
            invoke("updateAudioOnlyUI");
        });
        await(() -> segmentRequests.get() > before && prepared());
    }

    @Test public void localXmlIsCountedWithoutPreparingAFullRenderer() throws Exception {
        xml = new File(player.getCacheDir(), "large-danmaku-fixture.xml");
        try (FileOutputStream output = new FileOutputStream(xml)) {
            output.write("<i>".getBytes("UTF-8"));
            for (int i = 0; i < 5002; i++) output.write("<d p=\"1,1,25,16777215,0,0,0,1\">x</d>".getBytes("UTF-8"));
            output.write("</i>".getBytes("UTF-8"));
        }
        instrumentation.runOnMainSync(() -> {
            set("isOnlineVideo", false);
            set("danmaku_url", xml.getAbsolutePath());
            invoke("resetDanmakuSource", long.class, -1L);
            invoke("downdanmu");
        });
        await(() -> (Long) field("knownDanmakuCount") == 5001L);
        assertReleased();
        assertTrue(xml.isFile());
        assertEquals(0, metadataRequests.get() + segmentRequests.get() + xmlRequests.get());
    }

    @Test public void playerCountSurvivesParcelAndIntentTransfer() {
        PlayerData data = new PlayerData();
        data.danmakuCount = 6001;
        Parcel parcel = Parcel.obtain();
        String oldPlayer = SharedPreferencesUtil.getString("player", "null");
        try {
            data.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            PlayerData restored = PlayerData.CREATOR.createFromParcel(parcel);
            assertEquals(6001L, restored.danmakuCount);
            SharedPreferencesUtil.putString("player", "terminalPlayer");
            assertEquals(6001L, PlayerApi.jumpToPlayer(restored).getLongExtra("danmaku_count", -1));
        } finally {
            parcel.recycle();
            SharedPreferencesUtil.putString("player", oldPlayer);
        }
    }

    private void configure(long count) {
        instrumentation.runOnMainSync(() -> {
            set("isOnlineVideo", true);
            set("aid", 42L);
            set("cid", 123L);
            set("danmaku_url", "https://comment.bilibili.com/123.xml");
            invoke("resetDanmakuSource", long.class, count);
            invoke("downdanmu");
        });
    }

    private boolean prepared() { return ((master.flame.danmaku.ui.widget.DanmakuView) field("mDanmakuView")).isPrepared(); }
    private void toggle() { instrumentation.runOnMainSync(() -> player.findViewById(R.id.danmaku_btn).performClick()); }

    private void assertReleased() {
        assertFalse((Boolean) field("isDanmakuVisible"));
        assertNull(field("danmakuWindowLoader"));
        assertNull(field("pendingDanmakuWindow"));
        assertNull(field("windowDanmakuParser"));
        assertNull(field("mContext"));
        assertFalse(prepared());
    }

    private void preference(String key, boolean value) {
        preferences.put(key, SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        SharedPreferencesUtil.putBoolean(key, value);
    }

    private Object field(String name) {
        try { Field f = PlayerActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(player); }
        catch (Exception error) { throw new AssertionError(error); }
    }

    private void set(String name, Object value) {
        try { Field f = PlayerActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(player, value); }
        catch (Exception error) { throw new AssertionError(error); }
    }

    private void invoke(String name) { invoke(name, null, null); }
    private void invoke(String name, Class<?> type, Object value) {
        try {
            Method m = type == null ? PlayerActivity.class.getDeclaredMethod(name) : PlayerActivity.class.getDeclaredMethod(name, type);
            m.setAccessible(true);
            if (type == null) m.invoke(player); else m.invoke(player, value);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void await(Callable<Boolean> predicate) {
        AtomicBoolean done = new AtomicBoolean();
        long end = SystemClock.uptimeMillis() + 15000;
        do {
            instrumentation.runOnMainSync(() -> {
                try { done.set(predicate.call()); } catch (Exception e) { throw new AssertionError(e); }
            });
            if (done.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < end);
        fail("Danmaku state did not settle");
    }
}
