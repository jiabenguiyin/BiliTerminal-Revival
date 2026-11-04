package com.RobinNotBad.BiliClient.activity.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.KeyEvent;
import com.RobinNotBad.BiliClient.player.PlayerOptionsMenu;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.api.FavoritePlaybackSource;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackQueue;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackSource;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import static org.junit.Assert.*;

/** Uses the real decoder and player navigation; no real account/API changes. */
@RunWith(AndroidJUnit4.class)
public class FavoritePlaybackInstrumentedTest {
    private static final long FIRST = 500000000001L, SECOND = FIRST + 1, THIRD = FIRST + 2;
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> old = new HashMap<>();
    private PlayerActivity player;
    private File media;
    private String token;
    private final AtomicInteger pageCalls = new AtomicInteger(), upQueueCalls = new AtomicInteger();
    private volatile CountDownLatch gate;
    private volatile boolean fail;
    private volatile int emptyPages;

    @Before public void before() throws Exception {
        for (String key : new String[]{"player_from_last", "player_loop", "player_autolandscape",
                "player_subtitle_autoshow", "player_show_online", "player_show_viewpoints", "player_audio_only",
                SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK}) pref(key, false);
        pref("player_ui_showQualityBtn", true);
        pref(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED, true);
        pref(SharedPreferencesUtil.mid, 42L);
        media = new File(inst.getTargetContext().getCacheDir(), "favorite-playback-fixture.mp4");
        try (InputStream in = inst.getContext().getAssets().open("collection-playback.mp4");
             OutputStream out = new FileOutputStream(media)) {
            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n);
        }
        FavoritePlaybackSource.Session session = FavoritePlaybackSource.Session.capture(42, 42, 91,
                false, "音乐", 1, new ApiResult(), false, Arrays.asList(card(FIRST)));
        token = FavoritePlaybackSource.create(inst.getTargetContext(), session);
        player = (PlayerActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), PlayerActivity.class)
                .putExtra("url", media.getAbsolutePath()).putExtra("title", "收藏连播测试")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> Boolean.TRUE.equals(get("isPrepared")), 20000);
        main(() -> {
            call("playerPause"); set("aid", FIRST); set("cid", FIRST + 10); set("isOnlineVideo", true);
            set("favoritePlaybackToken", token);
            player.getIntent().putExtra("aid", FIRST).putExtra("cid", FIRST + 10).putExtra(FavoritePlaybackSource.TOKEN, token);
            set("favoritePlaybackSource", new FavoritePlaybackSource() {
                @Override protected ApiResult page(Session session, List<VideoCard> out, RequestGuard guard) throws Exception {
                    pageCalls.incrementAndGet();
                    if (gate != null) assertTrue(gate.await(12, TimeUnit.SECONDS));
                    assertEquals(91, session.folder); assertEquals("音乐", session.keyword); assertFalse(session.all);
                    if (fail) throw new IOException("fixture network failure");
                    if (emptyPages-- > 0) { out.add(card(FIRST)); return new ApiResult(); }
                    out.add(card(SECOND)); out.add(card(THIRD));
                    return new ApiResult().setBottom(true);
                }
            });
            set("collectionPlaybackSource", new CollectionPlaybackSource() {
                @Override public CollectionPlaybackQueue loadQueue(long aid) { upQueueCalls.incrementAndGet(); throw new AssertionError("Favorite context must override UP collection"); }
                @Override public PlayerData loadItem(long aid, int page, PlayerData current) {
                    PlayerData data = new PlayerData(); data.aid = aid; data.cid = aid + 10 + page;
                    data.title = "收藏测试 " + aid; data.videoUrl = media.getAbsolutePath(); data.danmakuCount = 0;
                    data.pagenames = new ArrayList<>(Arrays.asList("测试视频"));
                    data.cids = new ArrayList<>(Arrays.asList(data.cid)); data.currentPageIndex = page;
                    if (aid == current.aid && current.cids != null && current.cids.size() > 1) {
                        data.cids = current.cids; data.pagenames = current.pagenames; data.cid = data.cids.get(page);
                    }
                    return data;
                }
                @Override public void reportHistory(long aid, long cid, long seconds) { }
            });
        });
    }

    @After public void after() {
        if (gate != null) gate.countDown();
        if (player != null) { main(player::finish); inst.waitForIdleSync(); }
        for (Map.Entry<String,Object> entry : old.entrySet()) {
            if (entry.getValue() == null) SharedPreferencesUtil.removeValue(entry.getKey());
            else if (entry.getValue() instanceof Long) SharedPreferencesUtil.putLong(entry.getKey(), (Long) entry.getValue());
            else if (entry.getValue() instanceof String) SharedPreferencesUtil.putString(entry.getKey(), (String) entry.getValue());
            else SharedPreferencesUtil.putBoolean(entry.getKey(), (Boolean) entry.getValue());
        }
        if (media != null) media.delete();
    }

    @Test public void currentFavoriteStartsWithoutFetchingTheWholeFolderOrItsUpCollection() throws Exception {
        enable(); assertEquals(0, pageCalls.get()); assertEquals(0, upQueueCalls.get());
        assertArrayEquals(new long[]{FIRST}, ((CollectionPlaybackQueue) get("collectionQueue")).toArray());
    }

    @Test public void decoderCompletionLoadsNextPageAndHeadsetPreviousReturnsToActualItem() throws Exception {
        enable(); complete(); awaitItem(SECOND);
        assertEquals(1, pageCalls.get()); assertEquals(0, upQueueCalls.get());
        main(() -> player.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)));
        awaitItem(FIRST);
        assertEquals(1, pageCalls.get());
        assertEquals(token, player.getIntent().getStringExtra(FavoritePlaybackSource.TOKEN));
    }

    @Test public void duplicateOnlyPagesAdvanceWithoutRepeatingOrLosingLaterVideos() throws Exception {
        emptyPages = 2; enable(); next(); awaitItem(SECOND);
        assertEquals(3, pageCalls.get());
        FavoritePlaybackSource.Session restored = FavoritePlaybackSource.read(inst.getTargetContext(), token, 42);
        assertEquals(Arrays.asList(FIRST, SECOND, THIRD), restored.ids); assertTrue(restored.bottom);
        assertEquals(5, restored.nextPage);
    }

    @Test public void failedPageCanRetryFromMenuWithoutLosingTheCurrentItem() throws Exception {
        fail = true; enable(); next(); await(() -> Boolean.TRUE.equals(get("favoritePageFailed")), 6000);
        assertEquals(FIRST, get("aid"));
        assertEquals(2, FavoritePlaybackSource.read(inst.getTargetContext(), token, 42).nextPage);
        fail = false;
        main(() -> {
            call("showPlaybackOptions"); PlayerOptionsMenu menu = (PlayerOptionsMenu) get("playbackOptionsMenu");
            assertTrue(menu.getMenu().performIdentifierAction(3, 0)); menu.dismiss();
        });
        awaitItem(SECOND); assertEquals(2, pageCalls.get());
    }

    @Test public void disablingPlaybackDiscardsAnInFlightPageAndDoesNotSwitchLater() throws Exception {
        gate = new CountDownLatch(1); enable(); next(); await(() -> pageCalls.get() == 1, 6000);
        main(() -> {
            call("showPlaybackOptions"); PlayerOptionsMenu menu = (PlayerOptionsMenu) get("playbackOptionsMenu");
            assertTrue(menu.getMenu().performIdentifierAction(2, 0)); menu.dismiss();
        });
        gate.countDown(); SystemClock.sleep(400); inst.waitForIdleSync();
        assertEquals(FIRST, get("aid")); assertFalse((Boolean) get("favoritePaging"));
        assertEquals(2, FavoritePlaybackSource.read(inst.getTargetContext(), token, 42).nextPage);
    }

    @Test public void accountChangeDiscardsReturnedPageAndDoesNotLeaveNavigationBusy() throws Exception {
        gate = new CountDownLatch(1); enable(); next(); await(() -> pageCalls.get() == 1, 6000);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 43); gate.countDown();
        await(() -> !Boolean.TRUE.equals(get("favoritePaging")), 6000);
        assertEquals(FIRST, get("aid")); assertFalse((Boolean) get("isPlaying"));
        try { FavoritePlaybackSource.read(inst.getTargetContext(), token, 43); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("账号")); }
    }

    @Test public void shuffleIsEnabledOnlyAfterAllPagesAreReadyThenCyclesToRepeat() throws Exception {
        gate = new CountDownLatch(1); emptyPages = 2; enable();
        main(() -> player.findViewById(R.id.collection_order_btn).performClick());
        await(() -> pageCalls.get() == 1, 6000); assertEquals(0, get("collectionPlaybackOrder"));
        gate.countDown(); await(() -> Integer.valueOf(1).equals(get("collectionPlaybackOrder")), 6000);
        assertTrue(((FavoritePlaybackSource.Session) get("favoriteSession")).bottom);
        main(() -> player.findViewById(R.id.collection_order_btn).performClick());
        assertEquals(2, get("collectionPlaybackOrder"));
        complete(); await(() -> !Boolean.TRUE.equals(get("playbackCompleted")), 6000);
        assertEquals(FIRST, get("aid")); assertEquals(3, pageCalls.get());
    }

    @Test public void longDuplicateRunKeepsItsCheckpointAndMenuRetryContinuesFurtherPages() throws Exception {
        emptyPages = 6; enable(); next();
        await(() -> Boolean.TRUE.equals(get("favoritePageFailed")), 6000);
        assertEquals(5, pageCalls.get());
        assertEquals(7, FavoritePlaybackSource.read(inst.getTargetContext(), token, 42).nextPage);
        main(() -> {
            call("showPlaybackOptions"); PlayerOptionsMenu menu = (PlayerOptionsMenu) get("playbackOptionsMenu");
            assertTrue(menu.getMenu().performIdentifierAction(3, 0)); menu.dismiss();
        });
        awaitItem(SECOND); assertEquals(7, pageCalls.get());
    }

    @Test public void allPartsFinishBeforeMovingToTheNextFavorite() throws Exception {
        enable(); main(() -> {
            set("pagenames", new ArrayList<>(Arrays.asList("第一P", "第二P")));
            set("cids", new ArrayList<>(Arrays.asList(FIRST + 10, FIRST + 11)));
            set("currentPageIndex", 0);
        });
        complete(); await(() -> Integer.valueOf(1).equals(get("currentPageIndex")) && Boolean.TRUE.equals(get("isPrepared")), 12000);
        assertEquals(FIRST, get("aid")); assertEquals(0, pageCalls.get());
        complete(); awaitItem(SECOND); assertEquals(1, pageCalls.get());
    }

    @Test public void sequentialEndStopsEvenIfLegacyLoopIsEnabled() throws Exception {
        enable(); next(); awaitItem(SECOND); next(); awaitItem(THIRD);
        main(() -> set("loop_enabled", true)); complete();
        await(() -> Boolean.TRUE.equals(get("playbackCompleted")) && !Boolean.TRUE.equals(get("isPlaying")), 6000);
        assertEquals(THIRD, get("aid")); assertEquals(1, pageCalls.get());
    }

    @Test public void parcelAndInternalPlayerIntentKeepTheTokenWithoutLeakingToOrdinaryVideos() throws Exception {
        pref("player", "terminalPlayer");
        PlayerData data = new PlayerData(); data.aid = FIRST; data.cid = FIRST + 10; data.favoritePlaybackToken = token;
        Parcel parcel = Parcel.obtain();
        try {
            data.writeToParcel(parcel, 0); parcel.setDataPosition(0);
            PlayerData restored = PlayerData.CREATOR.createFromParcel(parcel);
            assertEquals(token, restored.favoritePlaybackToken);
            assertEquals(token, PlayerApi.jumpToPlayer(restored).getStringExtra(FavoritePlaybackSource.TOKEN));
            assertNull(PlayerApi.jumpToPlayer(new PlayerData()).getStringExtra(FavoritePlaybackSource.TOKEN));
        } finally { parcel.recycle(); }
        try { FavoritePlaybackSource.read(inst.getTargetContext(), "../bad", 42); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("标识")); }
    }

    private VideoCard card(long id) { VideoCard card = new VideoCard(); card.aid = id; return card; }

    @Test public void reverseAtFirstFavoriteStopsWithoutLoadingUnrelatedLaterPages() throws Exception {
        enable(); main(() -> set("collectionPlaybackOrder", CollectionPlaybackQueue.REVERSE));
        complete(); await(() -> Boolean.TRUE.equals(get("playbackCompleted")) && !Boolean.TRUE.equals(get("isPlaying")), 6000);
        assertEquals(FIRST, get("aid")); assertEquals(0, pageCalls.get());
    }

    @Test public void previousInReverseModeCanLoadTheNextFavoritePage() throws Exception {
        enable(); main(() -> {
            set("collectionPlaybackOrder", CollectionPlaybackQueue.REVERSE);
            player.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        });
        awaitItem(SECOND); assertEquals(1, pageCalls.get());
        next(); awaitItem(FIRST); assertEquals(1, pageCalls.get());
    }
    private void enable() throws Exception {
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK, true);
        main(() -> call("refreshCollectionPlaybackSettings"));
        await(() -> get("favoriteSession") != null, 8000);
    }
    private void next() { main(() -> player.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))); }
    private void complete() { main(() -> {
        IMediaPlayer decoder = (IMediaPlayer) get("ijkPlayer");
        call("seekPlaybackPlayers", new Class[]{long.class}, new Object[]{decoder.getDuration() - 350});
        set("isPlaying", true); call("startPlaybackPlayers");
    }); }
    private void awaitItem(long aid) throws Exception {
        await(() -> Long.valueOf(aid).equals(get("aid")) && Boolean.TRUE.equals(get("isPrepared")), 15000);
        main(() -> call("playerPause"));
    }
    private interface Check { boolean get(); }
    private void await(Check check, long timeout) throws Exception {
        long until = SystemClock.uptimeMillis() + timeout; boolean[] value = {false};
        do { main(() -> value[0] = check.get()); if (value[0]) return; SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < until);
        throw new AssertionError("Timed out: aid=" + get("aid") + ", prepared=" + get("isPrepared")
                + ", paging=" + get("favoritePaging") + ", calls=" + pageCalls.get());
    }
    private void pref(String key, Object value) {
        old.put(key, SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        if (value instanceof Long) SharedPreferencesUtil.putLong(key, (Long) value);
        else if (value instanceof String) SharedPreferencesUtil.putString(key, (String) value);
        else SharedPreferencesUtil.putBoolean(key, (Boolean) value);
    }
    private Object get(String name) { try { Field f = PlayerActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(player); } catch (Exception e) { throw new AssertionError(e); } }
    private void set(String name, Object value) { try { Field f = PlayerActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(player, value); } catch (Exception e) { throw new AssertionError(e); } }
    private void call(String name) { call(name, new Class[0], new Object[0]); }
    private void call(String name, Class[] types, Object[] args) { try { Method m = PlayerActivity.class.getDeclaredMethod(name, types); m.setAccessible(true); m.invoke(player, args); } catch (Exception e) { throw new AssertionError(e); } }
    private void main(Runnable task) { inst.runOnMainSync(task); }
}
