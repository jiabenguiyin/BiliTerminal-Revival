package com.RobinNotBad.BiliClient.activity.player;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.UIPreviewActivity;
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity;
import com.RobinNotBad.BiliClient.activity.video.MultiPageActivity;
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.Stats;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackQueue;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackSource;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import okhttp3.*;
import okio.Buffer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import static org.junit.Assert.*;

/** Real detail -> transition -> decoder -> back stack; all HTTP, including history POSTs, is intercepted. */
@RunWith(AndroidJUnit4.class)
public class CollectionPlaybackReturnInstrumentedTest {
    private static final long FIRST = 500000000101L, SECOND = FIRST + 1, THIRD = FIRST + 2;
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> preferences = new HashMap<>();
    private final Map<Long, VideoInfo> videos = new HashMap<>();
    private final List<String> history = new CopyOnWriteArrayList<>();
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient previousClient;
    private Instrumentation.ActivityMonitor details, jumps, players, parts;
    private UIPreviewActivity host;
    private VideoInfoActivity original, returned;
    private JumpToPlayerActivity jump;
    private MultiPageActivity chooser;
    private PlayerActivity player;
    private File media, localProgressFolder;
    private volatile boolean failHistory;

    @Before public void setup() throws Exception {
        for (String key : new String[]{"first_play", "first_videoinfo", "back_disable", "player_from_last",
                "player_loop", "player_autolandscape", "player_audio_only", "player_subtitle_autoshow",
                "player_show_online", "player_show_viewpoints", "pref_switch_danmaku", "tags_enable",
                "related_enable", "tutorial_pager_VideoInfoActivity",
                SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK}) preference(key, false);
        preference("player", "terminalPlayer");
        preference("play_qn", 16); preference("play_qn_mobile", 16); preference("play_qn_wifi", 16);
        preference("tutorial_ver_video", 99);
        preference(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED, true);
        remember("wbi_mixin_key"); remember("last_wbi");
        media = new File(inst.getTargetContext().getCacheDir(), "collection-return-fixture.mp4");
        try (InputStream in = inst.getContext().getAssets().open("collection-playback.mp4");
             OutputStream out = new FileOutputStream(media)) {
            byte[] bytes = new byte[8192]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
        }
        Field instance = NetWorkUtil.class.getDeclaredField("INSTANCE"); instance.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) instance.get(null); previousClient = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            // Never call proceed(): neither playback metadata nor a history report leaves this fixture.
            String path = chain.request().url().encodedPath();
            JSONObject data = new JSONObject();
            try {
                if (path.endsWith("/history/report")) {
                    Buffer body = new Buffer(); chain.request().body().writeTo(body);
                    history.add(body.readUtf8());
                    if (failHistory) throw new IOException("intercepted history failure");
                } else if (path.endsWith("/nav")) {
                    data.put("wbi_img", new JSONObject()
                            .put("img_url", "https://example.invalid/0123456789abcdef0123456789abcdef.png")
                            .put("sub_url", "https://example.invalid/fedcba9876543210fedcba9876543210.png"));
                } else if (path.endsWith("/playurl")) {
                    data.put("quality", 16).put("accept_quality", new JSONArray().put(16))
                            .put("accept_description", new JSONArray().put("360P"))
                            .put("durl", new JSONArray().put(new JSONObject().put("url", media.getAbsolutePath())));
                }
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"),
                                new JSONObject().put("code", 0).put("data", data).toString())).build();
            } catch (org.json.JSONException error) { throw new IOException(error); }
        }).build());
        for (long aid : new long[]{FIRST, SECOND, THIRD}) {
            VideoInfo info = new VideoInfo(); info.aid = aid; info.bvid = "";
            info.title = "合集返回测试 " + aid; info.epid = -1; info.argueMsg = "";
            info.cover = ""; info.description = ""; info.duration = "00:04"; info.timeDesc = "";
            info.stats = new Stats(); info.pagenames.add("P1"); info.cids.add(aid + 10);
            videos.put(aid, info); TerminalContext.getInstance().putContent(info);
        }
        details = inst.addMonitor(VideoInfoActivity.class.getName(), null, false);
        jumps = inst.addMonitor(JumpToPlayerActivity.class.getName(), null, false);
        players = inst.addMonitor(PlayerActivity.class.getName(), null, false);
        parts = inst.addMonitor(MultiPageActivity.class.getName(), null, false);
        host = (UIPreviewActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), UIPreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @After public void cleanup() throws Exception {
        main(() -> {
            // Close an unfinished transition first so a failed test cannot enqueue a late history job.
            for (Activity activity : new Activity[]{jump, player, chooser, returned, original, host})
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) activity.finish();
        });
        inst.waitForIdleSync();
        for (Instrumentation.ActivityMonitor monitor : new Instrumentation.ActivityMonitor[]{details, jumps, players, parts})
            if (monitor != null) inst.removeMonitor(monitor);
        if (clients != null) clients.set(previousClient);
        for (Map.Entry<String, Object> entry : preferences.entrySet()) restore(entry.getKey(), entry.getValue());
        Field field = TerminalContext.class.getDeclaredField("contentLruCache"); field.setAccessible(true);
        Object cache = field.get(TerminalContext.getInstance());
        for (VideoInfo info : videos.values()) cache.getClass().getMethod("remove", Object.class)
                .invoke(cache, TerminalContext.getInstance().getTerminalKey(info));
        if (media != null) media.delete();
        if (localProgressFolder != null) {
            new File(localProgressFolder, ".playback_progress").delete(); localProgressFolder.delete();
        }
    }

    @Test public void sequentialCompletionReturnsLatestDetailAndBackDoesNotRelaunchPlayback() throws Exception {
        launch(false);
        main(() -> {
            IMediaPlayer decoder = (IMediaPlayer) field(player, PlayerActivity.class, "ijkPlayer");
            call("seekPlaybackPlayers", new Class[]{long.class}, decoder.getDuration() - 350);
            set("isPlaying", true); call("startPlaybackPlayers");
        });
        awaitItem(SECOND, 0); next(); awaitItem(THIRD, 0);
        back(); assertReturned(THIRD);
        assertHistory(THIRD, THIRD + 10);
        main(returned::onBackPressed); await(() -> resumed(host));
        assertEquals(2, details.getHits()); assertEquals(1, jumps.getHits()); assertEquals(1, players.getHits());
    }

    @Test public void sameVideoPartChangeKeepsChooserAndOriginalDetail() throws Exception {
        launch(true); next(); awaitItem(FIRST, 1);
        back(); await(() -> resumed(chooser));
        assertEquals(1, details.getHits()); assertFalse(original.isFinishing());
        assertResult(FIRST, FIRST + 11, true); assertHistory(FIRST, FIRST + 11);
        main(chooser::onBackPressed); await(() -> resumed(original));
        assertEquals(1, jumps.getHits()); assertEquals(1, players.getHits());
    }

    @Test public void differentVideoAfterPartsRemovesStaleDetailAndPartChooser() throws Exception {
        launch(true); next(); awaitItem(FIRST, 1); next(); awaitItem(SECOND, 0);
        back(); assertReturned(SECOND);
        assertTrue(chooser.isFinishing() || chooser.isDestroyed());
        assertResult(SECOND, SECOND + 10, true); assertHistory(SECOND, SECOND + 10);
        main(returned::onBackPressed); await(() -> resumed(host));
        assertEquals(2, details.getHits()); assertEquals(1, parts.getHits());
    }

    @Test public void revisitingStartingVideoDoesNotReplaceItsDetail() throws Exception {
        launch(false); next(); awaitItem(SECOND, 0);
        main(() -> call("skipPlaybackItem", new Class[]{boolean.class}, false)); awaitItem(FIRST, 0);
        back(); await(() -> resumed(original));
        assertEquals(1, details.getHits()); assertResult(FIRST, FIRST + 10, true);
    }

    @Test public void returningHomeSuppressesAutomaticCurrentVideoDetailNavigation() throws Exception {
        launch(false); next(); awaitItem(SECOND, 0);
        main(() -> {
            player.getIntent().putExtra(com.RobinNotBad.BiliClient.BiliTerminal.RETURNING_HOME, true);
            jump.getIntent().putExtra(com.RobinNotBad.BiliClient.BiliTerminal.RETURNING_HOME, true);
        });
        back(); await(() -> resumed(original));
        assertEquals("Return-home result must not reopen the latest video detail", 1, details.getHits());
        assertTrue(jump.isFinishing() || jump.isDestroyed());
        assertResult(SECOND, SECOND + 10, true);
        assertHistory(SECOND, SECOND + 10);
    }

    @Test public void disablingCollectionAfterAdvanceStillReturnsCurrentVideo() throws Exception {
        launch(false); next(); awaitItem(SECOND, 0);
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED, false);
        main(() -> call("refreshCollectionPlaybackSettings"));
        back(); assertReturned(SECOND);
        assertHistory(SECOND, SECOND + 10);
    }

    @Test public void localResultPreservesReturnPathAndProgressEvenWithDifferentAid() throws Exception {
        launch(false); next(); awaitItem(SECOND, 0);
        localProgressFolder = new File(inst.getTargetContext().getCacheDir(), "collection-return-progress");
        assertTrue(localProgressFolder.isDirectory() || localProgressFolder.mkdirs());
        main(() -> {
            set("isOnlineVideo", false); set("localProgressFolderLocator", localProgressFolder.getAbsolutePath());
            call("seekToPosition", new Class[]{long.class}, 1500L);
        });
        await(() -> ((IMediaPlayer) field(player, PlayerActivity.class, "ijkPlayer")).getCurrentPosition() >= 1000);
        back(); await(() -> resumed(original));
        assertEquals(1, details.getHits()); assertResult(SECOND, SECOND + 10, false);
        try (BufferedReader input = new BufferedReader(new FileReader(new File(localProgressFolder, ".playback_progress")))) {
            Intent result = (Intent) field(player, Activity.class, "mResultData");
            assertEquals(result.getIntExtra("progress", -1), Long.parseLong(input.readLine()));
        }
    }

    @Test public void failedHistoryReportCannotKeepTransitionOpenOrRepeatNavigation() throws Exception {
        launch(false); next(); awaitItem(SECOND, 0);
        failHistory = true;
        back(); assertReturned(SECOND);
        await(() -> history.size() == 2);
        assertHistory(SECOND, SECOND + 10);
        assertEquals(2, details.getHits()); assertEquals(1, players.getHits()); assertEquals(1, jumps.getHits());
    }

    private void launch(boolean multipage) throws Exception {
        if (multipage) { videos.get(FIRST).pagenames.add("P2"); videos.get(FIRST).cids.add(FIRST + 11); }
        main(() -> host.startActivity(new Intent(host, VideoInfoActivity.class).putExtra("aid", FIRST)));
        original = (VideoInfoActivity) details.waitForActivityWithTimeout(8000); assertNotNull(original);
        await(() -> original.contentFragment != null && original.contentFragment.getView() != null
                && original.contentFragment.getView().findViewById(R.id.play).hasOnClickListeners());
        main(() -> original.contentFragment.getView().findViewById(R.id.play).performClick());
        if (multipage) {
            chooser = (MultiPageActivity) parts.waitForActivityWithTimeout(8000); assertNotNull(chooser);
            await(() -> ((RecyclerView) chooser.findViewById(R.id.recyclerView)).findViewHolderForAdapterPosition(0) != null);
            main(() -> ((RecyclerView) chooser.findViewById(R.id.recyclerView))
                    .findViewHolderForAdapterPosition(0).itemView.performClick());
        }
        jump = (JumpToPlayerActivity) jumps.waitForActivityWithTimeout(8000); assertNotNull(jump);
        player = (PlayerActivity) players.waitForActivityWithTimeout(15000); assertNotNull(player);
        await(() -> Boolean.TRUE.equals(field(player, PlayerActivity.class, "isPrepared")));
        main(() -> {
            call("playerPause");
            // Same local decoder fixture as CollectionPlaybackInstrumentedTest; only the logical source is online.
            set("isOnlineVideo", true);
            set("collectionPlaybackSource", new CollectionPlaybackSource() {
                @Override public CollectionPlaybackQueue loadQueue(long aid) {
                    return new CollectionPlaybackQueue(91, new long[]{FIRST, SECOND, THIRD});
                }
                @Override public PlayerData loadItem(long aid, int page, PlayerData current) {
                    PlayerData data = videos.get(aid).toPlayerData(page);
                    data.videoUrl = media.getAbsolutePath(); data.danmakuCount = 0;
                    return data;
                }
                @Override public void reportHistory(long aid, long cid, long seconds) {
                    // Advance-history behavior is covered by CollectionPlaybackInstrumentedTest.
                    // This fixture isolates the final transition report, including its retry.
                }
            });
        });
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK, true);
        main(() -> call("refreshCollectionPlaybackSettings"));
        await(() -> field(player, PlayerActivity.class, "collectionQueue") != null);
    }

    private void next() { main(() -> call("skipPlaybackItem", new Class[]{boolean.class}, true)); }
    private void awaitItem(long aid, int page) throws Exception {
        await(() -> Long.valueOf(aid).equals(field(player, PlayerActivity.class, "aid"))
                && Integer.valueOf(page).equals(field(player, PlayerActivity.class, "currentPageIndex"))
                && Boolean.TRUE.equals(field(player, PlayerActivity.class, "isPrepared")));
        main(() -> {set("isOnlineVideo", true);call("playerPause");});
    }
    private void back() throws Exception {
        main(player::onBackPressed);
        await(() -> history.size() == (failHistory ? 2 : 1));
    }
    private void assertReturned(long aid) throws Exception {
        Intent result = (Intent) field(player, Activity.class, "mResultData");
        assertNotNull("Player did not return a result", result);
        assertTrue("Advanced online fixture lost its logical source identity: " + result.getExtras(), result.getBooleanExtra("online_video", false));
        PlayerData initial = (PlayerData) field(jump, JumpToPlayerActivity.class, "playerData");
        assertTrue("Initial content type="+initial.type, initial.isVideo());
        assertTrue("Initial aid="+initial.aid+", returned aid="+result.getLongExtra("aid",0),initial.aid != result.getLongExtra("aid",0));
        assertEquals("Transition download mode",0,field(jump,JumpToPlayerActivity.class,"download"));
        await(() -> {
            for (Activity active : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                if (active instanceof VideoInfoActivity && active != original
                        && active.getIntent().getLongExtra("aid",0) == aid) { returned=(VideoInfoActivity)active;return true; }
            return false;
        });
        await(() -> resumed(returned) && (original.isFinishing() || original.isDestroyed()));
        assertEquals(aid, returned.getIntent().getLongExtra("aid", 0));
        await(() -> returned.contentFragment != null && returned.contentFragment.getArguments() != null
                && returned.contentFragment.getArguments().getLong("aid") == aid
                && returned.contentFragment.getView() != null);
        assertTrue(jump.isFinishing() || jump.isDestroyed());
        assertFalse(returned.getIntent().hasExtra("data"));
        assertEquals(2, details.getHits()); assertEquals(1, players.getHits()); assertEquals(1, jumps.getHits());
    }
    private void assertResult(long aid, long cid, boolean online) {
        Intent result = (Intent) field(player, Activity.class, "mResultData");
        assertNotNull(result); assertEquals(aid, result.getLongExtra("aid", 0));
        assertEquals(cid, result.getLongExtra("cid", 0)); assertTrue(result.getIntExtra("progress", -1) >= 0);
        assertEquals(online, result.getBooleanExtra("online_video", !online));
        Intent relayed = (Intent) field(jump, Activity.class, "mResultData");
        assertNotNull(relayed); assertEquals(result.getExtras().get("progress"), relayed.getExtras().get("progress"));
        assertEquals(aid, relayed.getLongExtra("aid", 0)); assertEquals(cid, relayed.getLongExtra("cid", 0));
    }
    private void assertHistory(long aid, long cid) throws Exception {
        await(() -> {
            for (String body : history) if (body.contains("aid=" + aid + "&cid=" + cid + "&progress=")) return true;
            return false;
        });
    }
    private boolean resumed(Activity activity) {
        return ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity) == Stage.RESUMED;
    }
    private interface Check { boolean get(); }
    private void await(Check check) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 18000; boolean[] done = {false};
        do { main(() -> done[0] = check.get()); if (done[0]) return; SystemClock.sleep(40); }
        while (SystemClock.uptimeMillis() < deadline);
        fail("Collection return condition timed out");
    }
    private void main(Runnable action) { inst.runOnMainSync(action); }
    private Object field(Object owner, Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void set(String name, Object value) {
        try { Field field = PlayerActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(player, value); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void call(String name) { call(name, new Class[0]); }
    private void call(String name, Class[] types, Object... values) {
        try { Method method = PlayerActivity.class.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(player, values); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void remember(String key) { if (!preferences.containsKey(key)) preferences.put(key, SharedPreferencesUtil.getSharedPreferences().getAll().get(key)); }
    private void preference(String key, Object value) { remember(key); restore(key, value); }
    private void restore(String key, Object value) {
        if (value == null) SharedPreferencesUtil.removeValue(key);
        else if (value instanceof Boolean) SharedPreferencesUtil.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) SharedPreferencesUtil.putInt(key, (Integer) value);
        else SharedPreferencesUtil.putString(key, (String) value);
    }
}
