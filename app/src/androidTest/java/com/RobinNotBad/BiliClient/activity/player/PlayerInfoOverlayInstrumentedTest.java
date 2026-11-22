package com.RobinNotBad.BiliClient.activity.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.InteractionVideoData;
import com.RobinNotBad.BiliClient.model.ViewPoint;
import com.RobinNotBad.BiliClient.player.PlayerInfoOverlay;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayerInfoOverlayInstrumentedTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> old = new HashMap<>();
    private PlayerActivity player;
    private File media;

    @Before public void setup() throws Exception {
        for (String key : new String[]{"player_autolandscape", "player_subtitle_autoshow", "player_show_online",
                "player_loop", "player_from_last", "player_audio_only", "player_ui_round"}) pref(key, false);
        pref("player_show_viewpoints", true);
        pref(SharedPreferencesUtil.PLAYER_SCREEN_LOCK, true);
        media = new File(inst.getTargetContext().getCacheDir(), "player-info-fixture.mp4");
        try (InputStream input = inst.getContext().getAssets().open("player-gesture.mp4");
             FileOutputStream output = new FileOutputStream(media)) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
        player = (PlayerActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), PlayerActivity.class)
                .putExtra("url", media.getAbsolutePath()).putExtra("title", "互动数值与章节测试")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> (Boolean) get("isPrepared"));
        main(() -> {
            call("playerPause");
            InteractionVideoData data = new InteractionVideoData();
            InteractionVideoData.InteractionHiddenVar score = new InteractionVideoData.InteractionHiddenVar();
            score.name = "分数"; score.value = 32; score.isShow = 1; score.idV2 = "score";
            InteractionVideoData.InteractionHiddenVar hidden = new InteractionVideoData.InteractionHiddenVar();
            hidden.name = "内部变量"; hidden.value = 99; hidden.isShow = 0;
            data.hiddenVars = Arrays.asList(score, hidden);
            set("interactionData", data);
            ((PlayerInfoOverlay) get("infoOverlay")).setChapters(Arrays.asList(
                    new ViewPoint("开场", 0, 2, 0, "", ""), new ViewPoint("关键时刻", 2, 15, 0, "", "")));
            call("showcon");
        });
        SystemClock.sleep(250); inst.waitForIdleSync();
    }
    @After public void cleanup() {
        if (player != null) { main(player::finish); inst.waitForIdleSync(); }
        for (Map.Entry<String, Object> e : old.entrySet()) {
            if (e.getValue() == null) SharedPreferencesUtil.removeValue(e.getKey());
            else SharedPreferencesUtil.putBoolean(e.getKey(), (Boolean) e.getValue());
        }
        if (media != null) media.delete();
    }

    @Test public void visibleControlsExposeOnlyPublicValuesAndBottomTimeline() {
        assertTrue(player.findViewById(R.id.interaction_values_scroll).isShown());
        assertEquals("分数: 32", ((TextView) player.findViewById(R.id.interaction_values)).getText().toString());
        RecyclerView chapters = player.findViewById(R.id.player_chapter_list);
        assertTrue(chapters.isShown()); assertEquals(2, chapters.getAdapter().getItemCount());
        assertTrue(bounds(chapters).top >= bounds(player.findViewById(R.id.bottom_buttons)).bottom);
        assertTrue(bounds(player.findViewById(R.id.interaction_values_scroll)).bottom
                <= bounds(player.findViewById(R.id.bottom_control)).top);
        assertTrue("Clock must stay in the title row", bounds(player.findViewById(R.id.clock_layout)).bottom
                <= bounds(player.findViewById(R.id.interaction_values_scroll)).top);
        capture("player-info-controls.png");
    }
    @Test public void hidingControlsOrLockingHidesBothRowsAndUnlockingRestoresThem() {
        main(() -> ((Runnable) get("hidecon")).run());
        assertFalse(player.findViewById(R.id.interaction_values_scroll).isShown());
        assertEquals(View.GONE, player.findViewById(R.id.player_chapter_list).getVisibility());
        main(() -> { call("showcon"); call("setScreenLocked", new Class[]{boolean.class}, new Object[]{true}); });
        assertFalse(player.findViewById(R.id.interaction_values_scroll).isShown());
        assertFalse(player.findViewById(R.id.player_chapter_list).isShown());
        main(() -> call("setScreenLocked", new Class[]{boolean.class}, new Object[]{false}));
        assertTrue(player.findViewById(R.id.interaction_values_scroll).isShown());
        assertTrue(player.findViewById(R.id.player_chapter_list).isShown());
    }
    @Test public void timelineTouchSeeksActualDecoderAndDoesNotToggleControls() throws Exception {
        RecyclerView list = player.findViewById(R.id.player_chapter_list);
        main(() -> list.scrollToPosition(1));
        SystemClock.sleep(150); inst.waitForIdleSync();
        View item = list.findViewHolderForAdapterPosition(1).itemView;
        Rect box = bounds(item); Rect visible = new Rect(); assertTrue(list.getGlobalVisibleRect(visible));
        assertTrue(box.intersect(visible));
        main(() -> {
            long time = SystemClock.uptimeMillis();
            dispatch(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, box.centerX(), box.centerY(), 0));
            dispatch(MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, box.centerX(), box.centerY(), 0));
        });
        await(() -> Math.abs(((IMediaPlayer) get("ijkPlayer")).getCurrentPosition() - 2000L) < 700);
        assertTrue(player.findViewById(R.id.player_chapter_list).isShown());
    }
    @Test public void variablesRefreshAfterActionsAndDisappearForOrdinaryVideo() {
        main(() -> call("executeNativeAction", new Class[]{String.class}, new Object[]{"score=score+5"}));
        assertEquals("分数: 37", ((TextView) player.findViewById(R.id.interaction_values)).getText().toString());
        main(() -> { set("interactionData", null); ((PlayerInfoOverlay) get("infoOverlay")).setChapters(null); call("updatePlayerInfoOverlay"); });
        assertEquals(View.GONE, player.findViewById(R.id.interaction_values_scroll).getVisibility());
        assertEquals(View.GONE, player.findViewById(R.id.player_chapter_list).getVisibility());
    }
    @Test public void preferenceAudioAndLiveModesKeepMetadataHidden() {
        pref("player_show_viewpoints", false);
        main(() -> call("updatePlayerInfoOverlay"));
        assertEquals(View.GONE, player.findViewById(R.id.player_chapter_list).getVisibility());
        for (String mode : new String[]{"isAudioOnlyMode", "isLiveMode"}) {
            main(() -> { set(mode, true); call("updatePlayerInfoOverlay"); });
            assertEquals(View.GONE, player.findViewById(R.id.interaction_values_scroll).getVisibility());
            main(() -> set(mode, false));
        }
    }
    private void dispatch(MotionEvent event) { try { player.dispatchTouchEvent(event); } finally { event.recycle(); } }
    private Rect bounds(View view) { int[] xy = new int[2]; view.getLocationOnScreen(xy); return new Rect(xy[0], xy[1], xy[0]+view.getWidth(), xy[1]+view.getHeight()); }
    private void capture(String filename) {
        main(() -> {
            View view = player.getWindow().getDecorView();
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            try (FileOutputStream out = new FileOutputStream(new File(player.getFilesDir(), filename))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
            catch (Exception e) { throw new AssertionError(e); } finally { bitmap.recycle(); }
        });
    }
    private void pref(String key, boolean value) { if (!old.containsKey(key)) old.put(key, SharedPreferencesUtil.getSharedPreferences().getAll().get(key)); SharedPreferencesUtil.putBoolean(key,value); }
    private Object get(String name) { try { Field f=PlayerActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(player); } catch(Exception e) { throw new AssertionError(e); } }
    private void set(String name, Object value) { try { Field f=PlayerActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(player,value); } catch(Exception e) { throw new AssertionError(e); } }
    private void call(String name) { call(name,new Class[0],new Object[0]); }
    private void call(String name,Class[] types,Object[] args) { try { Method m=PlayerActivity.class.getDeclaredMethod(name,types); m.setAccessible(true); m.invoke(player,args); } catch(Exception e) { throw new AssertionError(e); } }
    private void main(Runnable run) { inst.runOnMainSync(run); inst.waitForIdleSync(); }
    private interface Check { boolean get(); }
    private void await(Check check) throws Exception { long end=SystemClock.uptimeMillis()+12000; while(SystemClock.uptimeMillis()<end) { if(check.get())return; SystemClock.sleep(50); } assertTrue("Timed out",check.get()); }
}
