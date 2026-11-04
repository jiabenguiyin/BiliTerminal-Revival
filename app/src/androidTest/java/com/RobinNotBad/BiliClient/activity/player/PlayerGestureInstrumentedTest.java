package com.RobinNotBad.BiliClient.activity.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.RelativeLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayerGestureInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> oldPreferences = new HashMap<>();
    private PlayerActivity player;
    private View controls;
    private View video;
    private File media;

    @Before public void startLocalPlayer() throws Exception {
        preference("player_scale", true);
        preference("player_doublemove", true);
        preference("player_singlemove", true);
        preference(SharedPreferencesUtil.PLAYER_ANTI_MISTOUCH, true);
        preference(SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK, false);
        preference("player_longclick", true);
        preference(com.RobinNotBad.BiliClient.util.PlayerSettingsUtil.LONGPRESS_HINT_ENABLED,true);
        preference(com.RobinNotBad.BiliClient.util.PlayerSettingsUtil.LONGPRESS_HINT_SCALE,.75f);
        preference(SharedPreferencesUtil.PLAYER_LONGPRESS_SWITCH, true);
        preference(SharedPreferencesUtil.PLAYER_BRIGHTNESS_GESTURE, true);
        preference(SharedPreferencesUtil.PLAYER_SWIPE_SEEK, true);
        preference(SharedPreferencesUtil.PLAYER_ZOOM_TAP_SEEK, false);
        preference("player_autolandscape", false);
        preference("player_subtitle_autoshow", false);
        preference(SharedPreferencesUtil.PLAYER_SCREEN_LOCK, true);
        preference("player_ui_round", false);
        preference("player_double_tap_mode", 0);
        media = new File(instrumentation.getTargetContext().getCacheDir(), "gesture-test.mp4");
        try (InputStream input = instrumentation.getContext().getAssets().open("player-gesture.mp4");
             FileOutputStream output = new FileOutputStream(media)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        long fixtureId = System.nanoTime();
        player = (PlayerActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), PlayerActivity.class)
                .putExtra("url", media.getAbsolutePath()).putExtra("title", "Gesture test")
                .putExtra("fixture_id", fixtureId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertEquals("Previous player activity was reused", fixtureId,
                player.getIntent().getLongExtra("fixture_id", -1));
        long until = SystemClock.uptimeMillis() + 15000L;
        while (!((Boolean) field("isPrepared")) && SystemClock.uptimeMillis() < until) {
            SystemClock.sleep(100L);
        }
        assertTrue("Local player did not prepare", (Boolean) field("isPrepared"));
        assertFalse("Local video inherited live mode", (Boolean) field("isLiveMode"));
        boolean[] laidOut={false};long layoutDeadline=SystemClock.uptimeMillis()+5000;
        do {
            instrumentation.runOnMainSync(()->{
                View surface=(View)field("layout_control"), picture=(View)field("layout_video");
                laidOut[0]=surface.getWidth()>0&&surface.getHeight()>0&&picture.getWidth()>0&&picture.getHeight()>0
                        &&!surface.isLayoutRequested()&&!picture.isLayoutRequested()&&field("progress_str")!=null;
            });
            if(!laidOut[0])SystemClock.sleep(30);
        } while(!laidOut[0]&&SystemClock.uptimeMillis()<layoutDeadline);
        assertTrue("Player geometry must be laid out before setting pan boundaries",laidOut[0]);
        instrumentation.runOnMainSync(() -> {
            controls = (View) field("layout_control");
            video = (View) field("layout_video");
            invoke("playerPause");
            set("video_width", controls.getWidth());
            set("video_height", controls.getHeight());
            set("video_origX", video.getX());
            set("video_origY", video.getY());
            invoke("hideControlsForTest");
        });
    }

    @After public void cleanUp() {
        if (player != null) {
            instrumentation.runOnMainSync(player::finish);
            instrumentation.waitForIdleSync();
            long deadline = SystemClock.uptimeMillis() + 5000L;
            while (!player.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50L);
            assertTrue("Previous player did not finish destruction", player.isDestroyed());
        }
        for (Map.Entry<String, Object> entry : oldPreferences.entrySet()) {
            if (entry.getValue() == null) SharedPreferencesUtil.removeValue(entry.getKey());
            else if (entry.getValue() instanceof Boolean) {
                SharedPreferencesUtil.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            } else if(entry.getValue() instanceof Float)SharedPreferencesUtil.putFloat(entry.getKey(),(Float)entry.getValue());
            else SharedPreferencesUtil.putInt(entry.getKey(), (Integer) entry.getValue());
        }
        if (media != null) media.delete();
    }

    @Test public void singleTapIsOptInAndTracksControlVisibility() {
        zoom();
        tap();
        awaitSingleTap();
        assertFalse((Boolean) field("zoomedSingleFingerSeekMode"));
        assertEquals(View.VISIBLE, ((View) field("layout_top")).getVisibility());
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_ZOOM_TAP_SEEK, true);
        tap();
        awaitSingleTap();
        assertFalse((Boolean) field("zoomedSingleFingerSeekMode"));
        tap();
        awaitSingleTap();
        assertTrue((Boolean) field("zoomedSingleFingerSeekMode"));
        instrumentation.runOnMainSync(() -> invoke("hideControlsForTest"));
        assertFalse((Boolean) field("zoomedSingleFingerSeekMode"));
    }

    @Test public void fastReleaseWithoutMoveDoesNotOpenControlsOrCountAsADoubleTap() {
        for (boolean scaling : new boolean[]{true, false}) for (boolean antiMistouch : new boolean[]{true, false}) {
            configureSwipeFixture(scaling, antiMistouch);
            instrumentation.runOnMainSync(() -> {
                float x = controls.getWidth() * 0.25f, end = controls.getWidth() * 0.65f;
                float y = seekGestureY(x, end);
                long down = SystemClock.uptimeMillis();
                event(down, MotionEvent.ACTION_DOWN, x, y);
                event(down, MotionEvent.ACTION_UP, end, y);
            });
            assertNoTapAfterSwipe("release-only; scaling=" + scaling + ", anti=" + antiMistouch);
        }
    }

    @Test public void coalescedOutAndBackHistoryIsStillASwipeEvenWhenReleaseReturnsToStart() {
        for (boolean scaling : new boolean[]{true, false}) for (boolean antiMistouch : new boolean[]{true, false}) {
            configureSwipeFixture(scaling, antiMistouch);
            instrumentation.runOnMainSync(() -> {
                float x = controls.getWidth() * 0.25f, end = controls.getWidth() * 0.65f;
                float y = seekGestureY(x, end);
                long down = SystemClock.uptimeMillis();
                event(down, MotionEvent.ACTION_DOWN, x, y);
                MotionEvent release = MotionEvent.obtain(down, down + 10, MotionEvent.ACTION_MOVE, end, y, 0);
                release.addBatch(down + 20, x, y, 1f, 1f, 0);
                release.setAction(MotionEvent.ACTION_UP);
                controls.dispatchTouchEvent(release); release.recycle();
            });
            assertNoTapAfterSwipe("coalesced out/back; scaling=" + scaling + ", anti=" + antiMistouch);
        }
    }

    @Test public void shortDiagonalSwipeDoesNotRequireSeekOrVolumeRecognitionToSuppressClick() {
        for (boolean scaling : new boolean[]{true, false}) for (boolean antiMistouch : new boolean[]{true, false}) {
            configureSwipeFixture(scaling, antiMistouch);
            instrumentation.runOnMainSync(() -> {
                float x = controls.getWidth() * 0.25f;
                float delta = ViewConfiguration.get(player).getScaledTouchSlop() * 0.8f;
                float y = seekGestureY(x, x + delta);
                long down = SystemClock.uptimeMillis();
                event(down, MotionEvent.ACTION_DOWN, x, y);
                event(down, MotionEvent.ACTION_MOVE, x + delta, y + delta);
                event(down, MotionEvent.ACTION_UP, x + delta, y + delta);
            });
            assertNoTapAfterSwipe("diagonal; scaling=" + scaling + ", anti=" + antiMistouch);
        }
    }

    @Test public void aRealTapWithSmallJitterStillWorksImmediatelyAfterAnIgnoredFling() {
        for (boolean scaling : new boolean[]{true, false}) for (boolean antiMistouch : new boolean[]{true, false}) {
            configureSwipeFixture(scaling, antiMistouch);
            instrumentation.runOnMainSync(() -> {
                float x = controls.getWidth() * 0.25f, end = controls.getWidth() * 0.65f;
                float y = seekGestureY(x, end);
                long down = SystemClock.uptimeMillis();
                event(down, MotionEvent.ACTION_DOWN, x, y);
                event(down, MotionEvent.ACTION_UP, end, y);
            });
            assertNoTapAfterSwipe("before real tap");
            instrumentation.runOnMainSync(() -> {
                float x = controls.getWidth() * 0.25f;
                float delta = ViewConfiguration.get(player).getScaledTouchSlop() / 3f;
                float y = seekGestureY(x, x + delta);
                long down = SystemClock.uptimeMillis();
                event(down, MotionEvent.ACTION_DOWN, x, y);
                event(down, MotionEvent.ACTION_MOVE, x + delta, y);
                event(down, MotionEvent.ACTION_UP, x + delta, y);
            });
            instrumentation.waitForIdleSync();
            assertEquals(View.VISIBLE, ((View) field("layout_top")).getVisibility());
            assertFalse("A valid tap must not become a double-tap playback action", (Boolean) field("isPlaying"));
        }
    }

    @Test public void accessibilityClickStillWorksAfterSwipeCancellation() {
        configureSwipeFixture(true, true);
        instrumentation.runOnMainSync(() -> {
            float x = controls.getWidth() * 0.25f, end = controls.getWidth() * 0.65f;
            float y = seekGestureY(x, end);
            long down = SystemClock.uptimeMillis();
            event(down, MotionEvent.ACTION_DOWN, x, y);
            event(down, MotionEvent.ACTION_UP, end, y);
        });
        assertNoTapAfterSwipe("before accessibility click");
        instrumentation.runOnMainSync(() -> assertTrue(controls.performClick()));
        assertEquals(View.VISIBLE, ((View) field("layout_top")).getVisibility());
    }

    @Test public void longPressShowsItsActualTemporarySpeedUntilReleaseOrCancel() throws Exception {
        for (int action : new int[]{MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL}) {
            long[] down = {0}; float[] point = new float[2];
            instrumentation.runOnMainSync(() -> {
                invoke("hideControlsForTest"); invoke("cancelPendingSingleTap");
                set("isPlaying", true); invoke("startPlaybackPlayers");
                point[0] = controls.getWidth() * 0.3f; point[1] = seekGestureY(point[0], point[0]);
                down[0] = SystemClock.uptimeMillis();
                event(down[0], MotionEvent.ACTION_DOWN, point[0], point[1]);
            });
            long until = SystemClock.uptimeMillis() + 5000;
            while (!(Boolean) field("onLongClick") && SystemClock.uptimeMillis() < until) SystemClock.sleep(40);
            assertTrue("Long press did not activate", (Boolean) field("onLongClick"));
            TextView activeHint = player.findViewById(R.id.long_press_speed_hint);
            until = SystemClock.uptimeMillis() + 3000;
            while (activeHint.getWidth() <= 0 && SystemClock.uptimeMillis() < until) SystemClock.sleep(20);
            instrumentation.runOnMainSync(() -> {
                TextView hint = player.findViewById(R.id.long_press_speed_hint);
                assertEquals(View.VISIBLE, hint.getVisibility()); assertTrue(hint.getText().toString().contains("长按倍速中"));
                assertTrue(hint.getText().toString().endsWith("x"));
                assertTrue("Speed hint must stay at the top", hint.getTop() >= 0 && hint.getBottom() <= controls.getHeight() / 3);
                saveControls("player-longpress-hint.png");
                event(down[0], action, point[0], point[1]);
                assertFalse((Boolean) field("onLongClick")); assertEquals(View.GONE, hint.getVisibility());
                assertEquals("x 1.0", ((TextView) field("text_speed")).getText().toString());
                invoke("playerPause");
            });
        }
    }

    @Test public void longPressHintCanBeHiddenWithoutDisablingTemporarySpeed() {
        SharedPreferencesUtil.putBoolean(com.RobinNotBad.BiliClient.util.PlayerSettingsUtil.LONGPRESS_HINT_ENABLED,false);
        instrumentation.runOnMainSync(()->{
            invoke("startLongPressSpeed");assertTrue((Boolean)field("onLongClick"));
            assertEquals(View.GONE,player.findViewById(R.id.long_press_speed_hint).getVisibility());
            assertFalse("Temporary speed must still change",((TextView)field("text_speed")).getText().toString().equals("x 1.0"));
            invoke("stopLongPressSpeed");assertFalse((Boolean)field("onLongClick"));
            assertEquals("x 1.0",((TextView)field("text_speed")).getText().toString());
        });
    }

    @Test public void hintScaleKeepsTheWholeBannerInsideTheSmallPlayer() {
        float[] sizes=new float[3];int i=0;
        for(float scale:new float[]{.5f,.75f,1.25f}) {
            SharedPreferencesUtil.putFloat(com.RobinNotBad.BiliClient.util.PlayerSettingsUtil.LONGPRESS_HINT_SCALE,scale);
            instrumentation.runOnMainSync(()->invoke("startLongPressSpeed"));instrumentation.waitForIdleSync();SystemClock.sleep(100);
            final int index=i++;
            instrumentation.runOnMainSync(()->{
                TextView hint=player.findViewById(R.id.long_press_speed_hint);Rect visible=new Rect();
                assertTrue(hint.getGlobalVisibleRect(visible));assertEquals(hint.getWidth(),visible.width());assertEquals(hint.getHeight(),visible.height());
                assertTrue(hint.getWidth()<=controls.getWidth()*.73f);assertEquals(1,hint.getMaxLines());
                assertNotNull(hint.getLayout());assertEquals(0,hint.getLayout().getEllipsisCount(0));
                assertTrue(hint.getPaint().measureText(hint.getText().toString())<=hint.getWidth()-hint.getCompoundPaddingLeft()-hint.getCompoundPaddingRight()+1);
                sizes[index]=hint.getTextSize();saveControls("player-longpress-hint-scale-"+index+".png");invoke("stopLongPressSpeed");
            });
        }
        assertTrue(sizes[0]<=sizes[1]);assertTrue(sizes[1]<=sizes[2]);assertTrue(sizes[0]<sizes[2]);
    }

    @Test public void longPressOwnsVerticalMotionWithSwitchingEnabledOrDisabled() throws Exception {
        AudioManager audio=(AudioManager)player.getSystemService(android.content.Context.AUDIO_SERVICE);
        int originalVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try {
            for(boolean enabled:new boolean[]{true,false}) for(float fraction:new float[]{.35f,.65f}) {
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_LONGPRESS_SWITCH,enabled);
                long[] down={0};float[] point=new float[2];float[] brightness={0};int[] volume={0};
                instrumentation.runOnMainSync(()->{
                    invoke("hideControlsForTest");invoke("cancelPendingSingleTap");
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2),0);
                    volume[0]=audio.getStreamVolume(AudioManager.STREAM_MUSIC);brightness[0]=player.getWindow().getAttributes().screenBrightness;
                    set("isPlaying",true);invoke("startPlaybackPlayers");
                    point[0]=controls.getWidth()*fraction;point[1]=controls.getHeight()*.5f;
                    down[0]=SystemClock.uptimeMillis();event(down[0],MotionEvent.ACTION_DOWN,point[0],point[1]);
                });
                long deadline=SystemClock.uptimeMillis()+4000;while(!(Boolean)field("onLongClick")&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(20);
                assertTrue((Boolean)field("onLongClick"));
                instrumentation.runOnMainSync(()->{
                    event(down[0],MotionEvent.ACTION_MOVE,point[0],point[1]-controls.getHeight()*.35f);
                    event(down[0],MotionEvent.ACTION_UP,point[0],point[1]-controls.getHeight()*.35f);
                    assertEquals(volume[0],audio.getStreamVolume(AudioManager.STREAM_MUSIC));
                    assertEquals(brightness[0],player.getWindow().getAttributes().screenBrightness,0f);
                    assertFalse((Boolean)field("onLongClick"));assertEquals("x 1.0",((TextView)field("text_speed")).getText().toString());
                    assertFalse((Boolean)field("brightnessAdjusting"));assertFalse((Boolean)field("verticalAdjusting"));
                    invoke("playerPause");
                });
            }
        } finally { audio.setStreamVolume(AudioManager.STREAM_MUSIC,originalVolume,0); }
    }

    @Test public void zoomedLongPressCanPanWithoutChangingSpeedVolumeOrBrightness()throws Exception{
        zoom();AudioManager audio=(AudioManager)player.getSystemService(android.content.Context.AUDIO_SERVICE);
        int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);float brightness=player.getWindow().getAttributes().screenBrightness;
        for(boolean switching:new boolean[]{true,false}){
            SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_LONGPRESS_SWITCH,switching);
            instrumentation.runOnMainSync(()->{
                video.setX((Float)field("video_origX"));video.setY((Float)field("video_origY"));
                invoke("hideControlsForTest");invoke("cancelPendingSingleTap");
                float x=controls.getWidth()*.5f,y=controls.getHeight()*.5f;long down=SystemClock.uptimeMillis();
                event(down,MotionEvent.ACTION_DOWN,x,y);invoke("startLongPressSpeed");
                assertTrue((Boolean)field("longPressPanSequence"));assertFalse((Boolean)field("longPressSwitchSequence"));
                String speed=((TextView)field("text_speed")).getText().toString();float before=video.getX();
                event(down,MotionEvent.ACTION_MOVE,x+controls.getWidth()*.20f,y-controls.getHeight()*.15f);
                assertNotEquals(before,video.getX(),.1f);assertTrue((Boolean)field("onLongClick"));
                assertEquals(speed,((TextView)field("text_speed")).getText().toString());
                assertEquals(volume,audio.getStreamVolume(AudioManager.STREAM_MUSIC));assertEquals(brightness,player.getWindow().getAttributes().screenBrightness,0f);
                assertEquals(View.GONE,((View)field("layout_top")).getVisibility());
                event(down,MotionEvent.ACTION_UP,x+controls.getWidth()*.20f,y-controls.getHeight()*.15f);
                assertFalse((Boolean)field("onLongClick"));assertEquals("x 1.0",((TextView)field("text_speed")).getText().toString());
                assertFalse((Boolean)field("longPressPanSequence"));
            });
        }
    }

    @Test public void delayedVideoLayoutDoesNotUndoAnAlreadyZoomedPan()throws Exception{
        zoom();float[] moved={0};
        instrumentation.runOnMainSync(()->{
            invoke("changeVideoSize");float x=controls.getWidth()*.5f,y=controls.getHeight()*.5f;long down=SystemClock.uptimeMillis();
            event(down,MotionEvent.ACTION_DOWN,x,y);event(down,MotionEvent.ACTION_MOVE,x+controls.getWidth()*.20f,y);
            event(down,MotionEvent.ACTION_UP,x+controls.getWidth()*.20f,y);moved[0]=video.getX();assertTrue(moved[0]>0);
        });
        SystemClock.sleep(160);instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(()->assertEquals("Delayed centering must preserve the newer pan",moved[0],video.getX(),1f));
    }

    @Test public void ordinaryVerticalDragStillAdjustsVolumeWithoutALongPress() {
        AudioManager audio=(AudioManager)player.getSystemService(android.content.Context.AUDIO_SERVICE);
        int saved=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try {
            instrumentation.runOnMainSync(()->{
                invoke("hideControlsForTest");audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2),0);
                int before=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                float x=controls.getWidth()*.65f,y=controls.getHeight()*.7f;long down=SystemClock.uptimeMillis();
                event(down,MotionEvent.ACTION_DOWN,x,y);event(down,MotionEvent.ACTION_MOVE,x,y-controls.getHeight()*.4f);
                event(down,MotionEvent.ACTION_UP,x,y-controls.getHeight()*.4f);
                assertTrue(audio.getStreamVolume(AudioManager.STREAM_MUSIC)>before);
            });
        } finally {audio.setStreamVolume(AudioManager.STREAM_MUSIC,saved,0);}
    }

    @Test public void expandedToolsLeaveTheLockClearAndLoopRespondsAtItsDrawnPosition() {
        instrumentation.runOnMainSync(() -> {
            invoke("showcon"); player.findViewById(R.id.menu_btn).performClick();
        });
        instrumentation.waitForIdleSync();
        if ((Boolean) field("menu_opened")) {
            instrumentation.runOnMainSync(() -> {
                Rect lock = new Rect(), menu = new Rect();
                assertTrue(player.findViewById(R.id.screen_lock_btn).getGlobalVisibleRect(lock));
                assertTrue(player.findViewById(R.id.right_second).getGlobalVisibleRect(menu));
                assertFalse("Expanded toolbar covers lock: " + menu + " / " + lock, Rect.intersects(menu, lock));
                View loop = player.findViewById(R.id.loop_btn);
                ((android.widget.HorizontalScrollView) player.findViewById(R.id.right_second)).scrollTo(loop.getLeft(), 0);
            });
            instrumentation.waitForIdleSync();
            boolean before = (Boolean) field("loop_enabled");
            tapControl(player.findViewById(R.id.loop_btn));
            assertNotEquals(before, field("loop_enabled")); assertFalse((Boolean) field("screenLocked"));
        } else {
            instrumentation.runOnMainSync(() -> {
                com.RobinNotBad.BiliClient.player.PlayerOptionsMenu menu =
                        (com.RobinNotBad.BiliClient.player.PlayerOptionsMenu) field("playbackOptionsMenu");
                assertTrue(menu.isShowing());
                boolean before = (Boolean) field("loop_enabled");
                assertTrue(menu.getMenu().performIdentifierAction(102, 0));
                assertNotEquals(before, field("loop_enabled")); assertFalse((Boolean) field("screenLocked"));
            });
        }
    }

    private void configureSwipeFixture(boolean scaling, boolean antiMistouch) {
        SharedPreferencesUtil.putBoolean("player_scale", scaling);
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_ANTI_MISTOUCH, antiMistouch);
        instrumentation.runOnMainSync(() -> {
            invoke("setVideoGestures");
            video.setScaleX(scaling ? 2f : 1f); video.setScaleY(scaling ? 2f : 1f);
            if (scaling) ((ViewScaleGestureListener) field("scaleGestureListener")).can_reset = true;
            invoke("cancelPendingSingleTap"); invoke("hideControlsForTest");
        });
        instrumentation.waitForIdleSync();
    }

    private void assertNoTapAfterSwipe(String message) {
        instrumentation.waitForIdleSync();
        assertEquals(message, View.GONE, ((View) field("layout_top")).getVisibility());
        assertEquals(message, 0L, field("timestamp_click"));
        assertFalse(message, (Boolean) field("isPlaying"));
    }

    @Test public void doubleTapDoesNotExecuteSingleTapOrSwitchZoomMode() throws Exception {
        zoom();
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_ZOOM_TAP_SEEK, true);
        CountDownLatch secondTap = new CountDownLatch(1);
        long[] timing = new long[2];
        instrumentation.runOnMainSync(() -> {
            // Let View's posted click callback run before delivering the second tap.
            // Schedule on the UI thread to avoid an extra instrumentation round trip.
            tapOnMainThread();
            controls.postDelayed(() -> {
                timing[0] = (Long) field("timestamp_click");
                timing[1] = SystemClock.uptimeMillis() - timing[0];
                tapOnMainThread();
                secondTap.countDown();
            }, 50L);
        });
        assertTrue("Second test tap was not delivered", secondTap.await(5, TimeUnit.SECONDS));
        assertTrue("Test did not deliver a double tap in time: " + timing[1] + "ms",
                timing[0] != 0L && timing[1] < ViewConfiguration.getDoubleTapTimeout());
        awaitSingleTap();
        assertTrue("Double tap should resume: live=" + field("isLiveMode")
                + ", locked=" + field("screenLocked") + ", clickTime=" + field("timestamp_click")
                + ", disabled=" + field("gesture_click_disabled") + ", prepared=" + field("isPrepared")
                + ", ended=" + field("playbackCompleted") + ", position="
                + ((tv.danmaku.ijk.media.player.IMediaPlayer) field("ijkPlayer")).getCurrentPosition(), (Boolean) field("isPlaying"));
        assertFalse("Double tap must not enter seek mode", (Boolean) field("zoomedSingleFingerSeekMode"));
        assertEquals(2f, video.getScaleX(), 0.01f);
    }

    @Test public void doubleFingerModeKeepsSingleFingerSeekingAndRefreshesHint() {
        SharedPreferencesUtil.putBoolean("player_singlemove", false);
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_ZOOM_TAP_SEEK, true);
        instrumentation.runOnMainSync(() -> invoke("setVideoGestures"));
        zoom();
        tap();
        awaitSingleTap();
        assertFalse((Boolean) field("zoomedSingleFingerSeekMode"));
        assertSeekHintChanges();
    }

    @Test public void seekHintRefreshesWithScalingDisabledToo() {
        SharedPreferencesUtil.putBoolean("player_scale", false);
        instrumentation.runOnMainSync(() -> invoke("setVideoGestures"));
        assertSeekHintChanges();
    }

    @Test public void zoomedSingleFingerDefaultsToPanningAndLiveCannotSeek() {
        zoom();
        float oldX = video.getX();
        drag();
        if(video.getX()<=oldX)instrumentation.runOnMainSync(()->saveControls("player-pan-failure.png"));
        assertTrue("Zoomed single-finger gesture should move the picture: old="+oldX+", x="+video.getX()
                +", width="+field("video_width")+", control="+controls.getWidth()+", scale="+video.getScaleX()
                +", long="+field("onLongClick")+", flick="+field("longPressSwitchSequence")
                +", start="+field("surfaceDownX")+", last="+field("lastSurfaceX")+", prev="+field("previousX")
                +", seek="+field("zoomedSingleFingerSeekMode")+", vertical="+field("verticalAdjusting")
                +", brightness="+field("brightnessAdjusting")+", scaled="+field("gesture_scaled")+", origin="+field("video_origX"), video.getX() > oldX);
        assertFalse((Boolean) field("zoomedSingleFingerSeekMode"));
        instrumentation.runOnMainSync(() -> {
            video.setScaleX(1f);
            video.setScaleY(1f);
            ((ViewScaleGestureListener) field("scaleGestureListener")).can_reset = false;
            set("isLiveMode", true);
        });
        drag();
        assertEquals(View.GONE, ((View) field("swipe_seek_hint")).getVisibility());
    }

    @Test public void lockShowsCurrentStateAndCancelsPendingSingleTap() {
        zoom();
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_ZOOM_TAP_SEEK, true);
        tap();
        instrumentation.runOnMainSync(() -> invoke("setScreenLocked", boolean.class, true));
        awaitSingleTap();
        assertTrue((Boolean) field("screenLocked"));
        assertFalse((Boolean) field("zoomedSingleFingerSeekMode"));
        ImageButton lock = player.findViewById(R.id.screen_lock_btn);
        assertEquals("已锁定，点击解锁播放器", lock.getContentDescription().toString());
        float oldX = video.getX();
        drag();
        assertEquals(oldX, video.getX(), 0.01f);
        instrumentation.runOnMainSync(lock::performClick);
        assertEquals("未锁定，点击锁定播放器", lock.getContentDescription().toString());
    }

    @Test public void lockedSurfaceTapsToggleOnlyLockIcon() {
        assertLockedSurfaceTapBehavior(true);
    }

    @Test public void lockedSurfaceTapsToggleWithScalingDisabled() {
        assertLockedSurfaceTapBehavior(false);
    }

    @Test public void lockedDragLongPressCancelAndMultiplePointersDoNotToggleIcon() {
        instrumentation.runOnMainSync(() -> invoke("setScreenLocked", boolean.class, true));
        View lock = player.findViewById(R.id.screen_lock_btn);
        for (boolean scaling : new boolean[]{true, false}) {
            SharedPreferencesUtil.putBoolean("player_scale", scaling);
            instrumentation.runOnMainSync(() -> invoke("setVideoGestures"));
            for (int visibility : new int[]{View.VISIBLE, View.GONE}) {
                instrumentation.runOnMainSync(() -> lock.setVisibility(visibility));
                drag();
                assertEquals("Locked drag toggled the icon", visibility, lock.getVisibility());
                instrumentation.runOnMainSync(() -> {
                    float x = controls.getWidth() / 2f;
                    float y = controls.getHeight() / 2f;
                    long down = SystemClock.uptimeMillis();
                    event(down, MotionEvent.ACTION_DOWN, x, y);
                    event(down, MotionEvent.ACTION_CANCEL, x, y);
                    event(down, MotionEvent.ACTION_UP, x, y);
                    assertEquals("Cancelled touch toggled the icon", visibility, lock.getVisibility());
                    event(down, MotionEvent.ACTION_DOWN, x, y);
                    MotionEvent release = MotionEvent.obtain(down,
                            down + ViewConfiguration.getLongPressTimeout() + 1L,
                            MotionEvent.ACTION_UP, x, y, 0);
                    controls.dispatchTouchEvent(release);
                    release.recycle();
                    assertEquals("Long press toggled the icon", visibility, lock.getVisibility());
                    event(down, MotionEvent.ACTION_DOWN, x, y);
                    multiPointerEvent(down, MotionEvent.ACTION_POINTER_DOWN
                            | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), x, y);
                    multiPointerEvent(down, MotionEvent.ACTION_MOVE, x + 10, y);
                    multiPointerEvent(down, MotionEvent.ACTION_POINTER_UP
                            | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), x + 10, y);
                    event(down, MotionEvent.ACTION_UP, x + 10, y);
                    assertEquals("Multiple pointers toggled the icon", visibility, lock.getVisibility());
                    assertTrue((Boolean) field("screenLocked"));
                    assertFalse("Locked gestures changed playback", (Boolean) field("isPlaying"));
                });
            }
        }
    }

    private void assertLockedSurfaceTapBehavior(boolean scaling) {
        SharedPreferencesUtil.putBoolean("player_scale", scaling);
        instrumentation.runOnMainSync(() -> {
            invoke("setVideoGestures");
            invoke("showcon");
        });
        instrumentation.waitForIdleSync();
        View lock = player.findViewById(R.id.screen_lock_btn);
        tapControl(lock);
        assertTrue((Boolean) field("screenLocked"));
        int[] lockLocation = new int[2];
        int[] controlsLocation = new int[2];
        instrumentation.runOnMainSync(() -> {
            lock.getLocationInWindow(lockLocation);
            controls.getLocationInWindow(controlsLocation);
        });
        float lockX = lockLocation[0] - controlsLocation[0] + lock.getWidth() / 2f;
        float lockY = lockLocation[1] - controlsLocation[1] + lock.getHeight() / 2f;
        float originalScale = video.getScaleX();
        float originalX = video.getX();
        tap();
        assertEquals("A surface tap should hide the locked icon", View.GONE, lock.getVisibility());
        instrumentation.runOnMainSync(() -> {
            saveControls("player-locked-icon-hidden.png");
            long down = SystemClock.uptimeMillis();
            event(down, MotionEvent.ACTION_DOWN, lockX, lockY);
            event(down, MotionEvent.ACTION_UP, lockX, lockY);
        });
        assertEquals("Tapping the hidden icon area should reveal it", View.VISIBLE, lock.getVisibility());
        assertTrue("Revealing the icon must not unlock", (Boolean) field("screenLocked"));
        tap();
        tap();
        awaitSingleTap();
        assertEquals("Repeated taps should leave the icon visible", View.VISIBLE, lock.getVisibility());
        assertTrue((Boolean) field("screenLocked"));
        assertFalse("Locked taps triggered double-tap playback", (Boolean) field("isPlaying"));
        assertEquals(originalScale, video.getScaleX(), 0.01f);
        assertEquals(originalX, video.getX(), 0.01f);
        for (int id : new int[]{R.id.top, R.id.right_control, R.id.bottom_buttons,
                R.id.videoprogress, R.id.text_speed, R.id.btn_debug}) {
            assertEquals("A locked tap revealed other controls: " + id,
                    View.GONE, player.findViewById(id).getVisibility());
        }
        instrumentation.runOnMainSync(() -> saveControls("player-locked-icon-visible.png"));
        tapControl(lock);
        assertFalse("The visible lock button must still unlock", (Boolean) field("screenLocked"));
        assertEquals(View.VISIBLE, player.findViewById(R.id.top).getVisibility());
    }

    private void multiPointerEvent(long down, int action, float x, float y) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[2];
        for (int index = 0; index < 2; index++) {
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = index;
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[index] = new MotionEvent.PointerCoords();
            coordinates[index].x = x + index * 20;
            coordinates[index].y = y;
            coordinates[index].pressure = 1;
            coordinates[index].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                2, properties, coordinates, 0, 0, 1, 1, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
        controls.dispatchTouchEvent(event);
        event.recycle();
    }

    @Test public void pauseAndVolumeRespondAtTheirDrawnCenters() {
        instrumentation.runOnMainSync(() -> invoke("showcon"));
        instrumentation.waitForIdleSync();
        assertFalse((Boolean) field("isPlaying"));
        tapControl(player.findViewById(R.id.button_video));
        assertTrue("Pause button did not resume at its drawn center", (Boolean) field("isPlaying"));
        tapControl(player.findViewById(R.id.button_video));
        assertFalse("Pause button did not pause at its drawn center", (Boolean) field("isPlaying"));

        AudioManager audio = (AudioManager) player.getSystemService(android.content.Context.AUDIO_SERVICE);
        int oldVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int middle = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2;
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, middle, 0);
            tapControl(player.findViewById(R.id.button_sound_add));
            int increased = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            assertTrue("Volume-up did not receive its drawn-center touch", increased > middle);
            tapControl(player.findViewById(R.id.button_sound_cut));
            assertTrue("Volume-down did not receive its drawn-center touch",
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC) < increased);
        } finally { audio.setStreamVolume(AudioManager.STREAM_MUSIC, oldVolume, 0); }
    }

    @Test public void toolbarDoesNotRepeatInsetsAlreadyOutsidePlayerContent() {
        if (Build.VERSION.SDK_INT < 28) return;
        instrumentation.runOnMainSync(() -> invoke("showcon"));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            View root = player.findViewById(R.id.root_layout);
            View top = player.findViewById(R.id.top);
            int[] location = new int[2];
            root.getLocationOnScreen(location);
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
            int safeTop = insets == null ? 0 : insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top;
            int expectedExtra = Math.max(0, safeTop - location[1]);
            saveControls("player-toolbar-before-or-after.png");
            assertTrue("Toolbar repeats window inset: rootY=" + location[1] + ", safeTop=" + safeTop
                    + ", top=" + top.getTop(), top.getTop() <= expectedExtra + dp(4));
        });
    }

    @Test public void toolbarMarginShrinksWhenInsetsDisappear() {
        if (Build.VERSION.SDK_INT < 28) return;
        instrumentation.runOnMainSync(() -> {
            applyInsets(dp(24), dp(36), dp(32), dp(20));
            applyInsets(0, 0, 0, 0);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                    player.findViewById(R.id.top).getLayoutParams();
            assertTrue("A previous top inset remains after it disappeared", params.topMargin <= dp(4));
            assertTrue("A previous side inset remains after it disappeared",
                    params.leftMargin <= dp(4) && params.rightMargin <= dp(4));
        });
    }

    @Test public void leftLockRemainsFullyVisibleAndClickableOnSquareScreens() {
        instrumentation.runOnMainSync(() -> invoke("showcon"));
        instrumentation.waitForIdleSync();
        ImageButton lock = player.findViewById(R.id.screen_lock_btn);
        instrumentation.runOnMainSync(() -> {
            Rect full = new Rect();
            int[] location = new int[2];
            lock.getLocationInWindow(location);
            int[] rootOffset = new int[2];
            lock.getRootView().getLocationInWindow(rootOffset);
            // getGlobalVisibleRect is expressed in the root view's coordinates.
            full.set(location[0] - rootOffset[0], location[1] - rootOffset[1],
                    location[0] - rootOffset[0] + lock.getWidth(), location[1] - rootOffset[1] + lock.getHeight());
            Rect visible = new Rect();
            assertTrue(lock.getGlobalVisibleRect(visible));
            saveControls("player-square-controls.png");
            assertEquals("Lock button is clipped outside its parent", full, visible);
            int[] controlsLocation = new int[2];
            controls.getLocationInWindow(controlsLocation);
            assertTrue("Lock should be on the left",
                    full.exactCenterX() < controlsLocation[0] - rootOffset[0] + controls.getWidth() / 2f);
        });
        tapControl(lock);
        assertTrue("Lock did not receive its drawn-center tap", (Boolean) field("screenLocked"));
        instrumentation.runOnMainSync(() -> invoke("hideControlsForTest"));
        instrumentation.waitForIdleSync();
        assertEquals("Unlock must remain reachable while locked", View.VISIBLE, lock.getVisibility());
        tapControl(lock);
        assertFalse("Unlock did not receive its drawn-center tap", (Boolean) field("screenLocked"));
        instrumentation.runOnMainSync(() -> {
            SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_SCREEN_LOCK, false);
            invoke("showcon");
            assertEquals("Lock setting must still hide the independent button", View.GONE, lock.getVisibility());
            Rect speed = new Rect();
            assertTrue("Speed label disappeared when the lock button was hidden",
                    player.findViewById(R.id.text_speed).getGlobalVisibleRect(speed));
        });
    }

    private int dp(int value) {
        return Math.round(value * player.getResources().getDisplayMetrics().density);
    }

    private void applyInsets(int left, int top, int right, int bottom) {
        try {
            Method method = PlayerActivity.class.getDeclaredMethod("applyPlayerSafeArea",
                    int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            method.invoke(player, left, top, right, bottom);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void saveControls(String filename) {
        View root = player.findViewById(R.id.root_layout);
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        try (FileOutputStream output = new FileOutputStream(new File(player.getFilesDir(), filename))) {
            root.draw(new Canvas(bitmap));
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }

    private void tapControl(View button) {
        // showcon changes GONE ancestors to VISIBLE. Idle does not imply a completed
        // measure/layout pass; use the settled drawn bounds before injecting a tap.
        Rect previous = new Rect();
        int stable = 0;
        long deadline = SystemClock.uptimeMillis() + 3000;
        while (stable < 3 && SystemClock.uptimeMillis() < deadline) {
            Rect current = new Rect(); boolean[] ready = {true};
            instrumentation.runOnMainSync(() -> {
                ready[0] = button.getGlobalVisibleRect(current) && !current.isEmpty();
                View ancestor = button;
                while (ancestor != null) {
                    if (ancestor.isLayoutRequested()) ready[0] = false;
                    ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
                }
            });
            stable = ready[0] && current.equals(previous) ? stable + 1 : 0;
            previous.set(current);
            SystemClock.sleep(20);
        }
        assertTrue("Control layout did not settle before touch injection", stable >= 3);
        instrumentation.runOnMainSync(() -> {
            Rect rect = new Rect();
            assertTrue("Control is not visible", button.getGlobalVisibleRect(rect));
            int[] rootOffset = new int[2];
            button.getRootView().getLocationInWindow(rootOffset);
            rect.offset(rootOffset[0], rootOffset[1]);
            long down = SystemClock.uptimeMillis();
            MotionEvent press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN,
                    rect.exactCenterX(), rect.exactCenterY(), 0);
            player.dispatchTouchEvent(press);
            press.recycle();
            MotionEvent release = MotionEvent.obtain(down, down + 40L, MotionEvent.ACTION_UP,
                    rect.exactCenterX(), rect.exactCenterY(), 0);
            player.dispatchTouchEvent(release);
            release.recycle();
        });
        instrumentation.waitForIdleSync();
    }

    private void assertSeekHintChanges() {
        instrumentation.runOnMainSync(() -> {
            float start = controls.getWidth() * 0.1f;
            float activation = start + 40 * player.getResources().getDisplayMetrics().density;
            float y = seekGestureY(start, activation + controls.getWidth() * 0.1f);
            long down = SystemClock.uptimeMillis();
            event(down, MotionEvent.ACTION_DOWN, start, y);
            assertEquals("Gesture down was intercepted", start, (Float) field("swipeDownX"), 0.01f);
            event(down, MotionEvent.ACTION_MOVE, activation, y);
            TextView hint = (TextView) field("swipe_seek_hint");
            assertEquals("seek=" + field("swipeSeeking") + ", moved=" + field("gesture_moved")
                    + ", scaled=" + field("gesture_scaled") + ", multi=" + field("multiTouchGesture")
                    + ", start=" + field("swipeDownX") + ", vertical=" + field("verticalAdjusting")
                    + ", width=" + controls.getWidth(), View.VISIBLE, hint.getVisibility());
            String initial = hint.getText().toString();
            set("swipeSeekStartPosition", 10000L);
            event(down, MotionEvent.ACTION_MOVE, activation + controls.getWidth() * 0.1f, y);
            String next = hint.getText().toString();
            assertNotEquals("Hint remained at activation value", initial, next);
            assertTrue(next, next.startsWith("+6"));
            event(down, MotionEvent.ACTION_MOVE, activation - controls.getWidth() * 0.1f, y);
            assertTrue("Backward seek must have a minus sign", hint.getText().toString().startsWith("-"));
            event(down, MotionEvent.ACTION_CANCEL, activation, y);
        });
    }

    private float seekGestureY(float startX, float endX) {
        for (float fraction : new float[]{1f / 3f, 0.65f, 0.25f, 0.8f, 0.5f, 0.1f}) {
            float y = controls.getHeight() * fraction;
            Rect band = new Rect((int) Math.min(startX, endX), (int) y,
                    (int) Math.ceil(Math.max(startX, endX)), (int) y + 1);
            boolean clear = true;
            for (int id : new int[]{R.id.top, R.id.bottom_control, R.id.screen_lock_btn,
                    R.id.text_speed, R.id.btn_debug, R.id.card_bg}) {
                View view = player.findViewById(id);
                if (view.getVisibility() != View.VISIBLE) continue;
                Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
                ((ViewGroup) controls).offsetDescendantRectToMyCoords(view, rect);
                if (Rect.intersects(band, rect)) { clear = false; break; }
            }
            if (clear) return y;
        }
        throw new AssertionError("No unobstructed video row for a seek gesture");
    }

    private void zoom() {
        instrumentation.runOnMainSync(() -> {
            video.setScaleX(2f);
            video.setScaleY(2f);
            ((ViewScaleGestureListener) field("scaleGestureListener")).can_reset = true;
        });
    }

    private void tap() {
        instrumentation.runOnMainSync(this::tapOnMainThread);
    }

    private void tapOnMainThread() {
        long down = SystemClock.uptimeMillis();
        event(down, MotionEvent.ACTION_DOWN, controls.getWidth() / 2f, controls.getHeight() / 2f);
        event(down, MotionEvent.ACTION_UP, controls.getWidth() / 2f, controls.getHeight() / 2f);
    }

    private void drag() {
        instrumentation.runOnMainSync(() -> {
            long down = SystemClock.uptimeMillis();
            float x = controls.getWidth() / 2f;
            float y = controls.getHeight() / 2f;
            event(down, MotionEvent.ACTION_DOWN, x, y);
            event(down, MotionEvent.ACTION_MOVE, x + controls.getWidth() / 4f, y);
            event(down, MotionEvent.ACTION_UP, x + controls.getWidth() / 4f, y);
        });
    }

    private void event(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        controls.dispatchTouchEvent(event);
        event.recycle();
    }

    private void awaitSingleTap() {
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout() + 80L);
        instrumentation.waitForIdleSync();
    }

    private void preference(String key, Object value) {
        oldPreferences.put(key, SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        if (value instanceof Boolean) SharedPreferencesUtil.putBoolean(key, (Boolean) value);
        else if(value instanceof Float)SharedPreferencesUtil.putFloat(key,(Float)value);
        else SharedPreferencesUtil.putInt(key, (Integer) value);
    }

    private Object field(String name) {
        try {
            Field field = PlayerActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(player);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void set(String name, Object value) {
        try {
            Field field = PlayerActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(player, value);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void invoke(String name) { invoke(name, null, null); }

    private void invoke(String name, Class<?> type, Object value) {
        try {
            if ("hideControlsForTest".equals(name)) {
                ((Runnable) field("hidecon")).run();
                return;
            }
            Method method = type == null ? PlayerActivity.class.getDeclaredMethod(name)
                    : PlayerActivity.class.getDeclaredMethod(name, type);
            method.setAccessible(true);
            if (type == null) method.invoke(player);
            else method.invoke(player, value);
        } catch (Exception error) { throw new AssertionError(error); }
    }
}
