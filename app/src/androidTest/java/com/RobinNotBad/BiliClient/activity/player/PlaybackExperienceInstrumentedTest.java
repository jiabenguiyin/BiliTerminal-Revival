package com.RobinNotBad.BiliClient.activity.player;
import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.player.*;
import com.RobinNotBad.BiliClient.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlaybackExperienceInstrumentedTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> old=new HashMap<>();
    private PlayerActivity player;private File media;
    @Before public void before()throws Exception{
        for(String key:new String[]{"player_from_last","player_loop","player_autolandscape","player_subtitle_autoshow","player_audio_only",
                SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK,PlaybackVolume.ENABLED,PlaybackVolume.SEEK_BUTTONS,PageMotion.DISABLED})pref(key,false);
        pref(PlaybackSegments.MODE,1);pref(PlaybackVolume.GAIN,100);pref(PlaybackVolume.SEEK_SECONDS,10);pref(SharedPreferencesUtil.PLAYER_VOLUME_STEP,5);
        media=new File(inst.getTargetContext().getCacheDir(),"experience-fixture.mp4");
        try(InputStream in=inst.getContext().getAssets().open("player-gesture.mp4");OutputStream out=new FileOutputStream(media)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);
        }
        // Android 4.0's mediaserver cannot open app-private paths. A provider FD
        // exercises the platform decoder without widening the fixture's filesystem permissions.
        String source=Build.VERSION.SDK_INT<=15?androidx.core.content.FileProvider.getUriForFile(inst.getTargetContext(),
                "com.RobinNotBad.BiliClient.FileProvider",media).toString():media.getAbsolutePath();
        player=(PlayerActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),PlayerActivity.class)
                .putExtra("url",source).putExtra("title","播放体验测试").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(()->player.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY));
        await(()->Boolean.TRUE.equals(get("isPrepared")));
        main(()->{call("playerPause");set("bangumiPlayback",true);set("segmentPolicy",new PlaybackSegments(
                "[{\"clipType\":\"CLIP_TYPE_OP\",\"start\":1,\"end\":20},{\"clipType\":\"CLIP_TYPE_ED\",\"start\":40,\"end\":55}]"));});
    }
    @After public void after(){
        if(player!=null){main(player::finish);inst.waitForIdleSync();}
        for(Map.Entry<String,Object> e:old.entrySet()){
            if(e.getValue()==null)SharedPreferencesUtil.removeValue(e.getKey());else if(e.getValue() instanceof Boolean)SharedPreferencesUtil.putBoolean(e.getKey(),(Boolean)e.getValue());
            else SharedPreferencesUtil.putInt(e.getKey(),(Integer)e.getValue());
        }
        if(media!=null)media.delete();
    }
    @Test public void manualSkipButtonSeeksRealDecoderToTheOfficialEnd()throws Exception{
        AtomicInteger textChanges=new AtomicInteger();
        main(()->{((android.widget.TextView)player.findViewById(R.id.segment_skip_btn)).addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){textChanges.incrementAndGet();}
            public void afterTextChanged(android.text.Editable s){}
        });call("updateSegmentSkip",new Class[]{long.class},new Object[]{6000L});
            call("updateSegmentSkip",new Class[]{long.class},new Object[]{6100L});});
        assertEquals("Progress ticks must not repeatedly re-layout an unchanged label",1,textChanges.get());
        assertEquals(View.VISIBLE,player.findViewById(R.id.segment_skip_btn).getVisibility());
        main(()->{call("seekToPosition",new Class[]{long.class},new Object[]{6000L});});
        await(()->Math.abs(((IMediaPlayer)get("ijkPlayer")).getCurrentPosition()-6000)<800);
        main(()->player.findViewById(R.id.segment_skip_btn).performClick());
        assertEquals(20000,get("video_now"));
        await(()->Math.abs(((IMediaPlayer)get("ijkPlayer")).getCurrentPosition()-20000)<800);
    }
    @Test public void autoModeUsesTheSameSeekAndDoesNotRepeatForBufferedOldPositions()throws Exception{
        pref(PlaybackSegments.MODE,2);AtomicInteger seeks=new AtomicInteger();wrap(seeks,null);
        main(()->{set("isPlaying",true);call("updateSegmentSkip",new Class[]{long.class},new Object[]{6000L});});
        assertEquals(1,seeks.get());await(()->!Boolean.TRUE.equals(get("videoSeekPending")));
        main(()->{call("playerPause");set("isPlaying",true);call("updateSegmentSkip",new Class[]{long.class},new Object[]{6000L});call("playerPause");});
        assertEquals(1,seeks.get());assertEquals(View.GONE,player.findViewById(R.id.segment_skip_btn).getVisibility());
    }
    @Test public void ordinaryVideosAndLockedPlayersDoNotExposeManualSkip(){
        main(()->{set("bangumiPlayback",false);call("updateSegmentSkip",new Class[]{long.class},new Object[]{6000L});});
        assertEquals(View.GONE,player.findViewById(R.id.segment_skip_btn).getVisibility());
        pref(PlaybackSegments.MODE,0);
        main(()->{set("bangumiPlayback",true);call("updateSegmentSkip",new Class[]{long.class},new Object[]{6000L});
            int before=(Integer)get("video_now");player.findViewById(R.id.segment_skip_btn).performClick();assertEquals(before,get("video_now"));});
        assertEquals(View.GONE,player.findViewById(R.id.segment_skip_btn).getVisibility());
        pref(PlaybackSegments.MODE,1);
        main(()->{set("bangumiPlayback",true);call("updateSegmentSkip",new Class[]{long.class},new Object[]{6000L});
            call("setScreenLocked",new Class[]{boolean.class},new Object[]{true});});
        assertEquals(View.GONE,player.findViewById(R.id.segment_skip_btn).getVisibility());
    }
    @Test public void softwareVolumeChangesDecoderGainWithoutChangingSystemVolume()throws Exception{
        pref(PlaybackVolume.ENABLED,true);pref(PlaybackVolume.GAIN,35);float[] gain={-1};wrap(null,gain);
        AudioManager audio=(AudioManager)player.getSystemService(Context.AUDIO_SERVICE);int original=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        main(()->call("applyPlaybackGain"));assertEquals(.35f,gain[0],.001f);
        main(()->player.changeVolume(false));assertEquals(.30f,gain[0],.001f);assertEquals(original,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        pref(PlaybackVolume.ENABLED,false);main(()->call("applyPlaybackGain"));assertEquals(1f,gain[0],0f);
    }
    @Test public void fineGainIsAlsoAppliedToIndependentDashAudio()throws Exception{
        pref(PlaybackVolume.ENABLED,true);pref(PlaybackVolume.GAIN,35);float[] gain={-1};
        IMediaPlayer audio=(IMediaPlayer)Proxy.newProxyInstance(IMediaPlayer.class.getClassLoader(),new Class[]{IMediaPlayer.class},(p,m,a)->{
            if(m.getName().equals("setVolume"))gain[0]=(Float)a[0];return primitive(m.getReturnType());});
        main(()->{set("dashAudioPlayer",audio);call("applyPlaybackGain");});assertEquals(.35f,gain[0],.001f);
    }
    @Test public void seekButtonsUseConfiguredIntervalAndDisappearForLive()throws Exception{
        pref(PlaybackVolume.SEEK_BUTTONS,true);pref(PlaybackVolume.SEEK_SECONDS,7);
        main(()->{call("updateSeekButtons");long from=((IMediaPlayer)get("ijkPlayer")).getCurrentPosition();
            player.findViewById(R.id.seek_forward_btn).performClick();assertEquals((int)(from+7000),get("video_now"));});
        await(()->!Boolean.TRUE.equals(get("videoSeekPending")));
        main(()->{long from=((IMediaPlayer)get("ijkPlayer")).getCurrentPosition();
            player.findViewById(R.id.seek_back_btn).performClick();assertEquals((int)Math.max(0,from-7000),get("video_now"));});
        main(()->{set("isLiveMode",true);call("updateSeekButtons");});
        assertEquals(View.GONE,player.findViewById(R.id.seek_forward_btn).getVisibility());
    }
    @Test public void pageMotionDisablesAndRestoresRawPlayerWindowWithoutChangingImagePreference(){
        final int[] original={0};main(()->original[0]=player.getWindow().getAttributes().windowAnimations);
        boolean images=SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.LOAD_TRANSITION,false);
        pref(PageMotion.DISABLED,true);main(()->PageMotion.apply(player));assertEquals(0,player.getWindow().getAttributes().windowAnimations);
        pref(PageMotion.DISABLED,false);main(()->PageMotion.apply(player));assertEquals(original[0],player.getWindow().getAttributes().windowAnimations);
        assertEquals(images,SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.LOAD_TRANSITION,false));
    }
    @Test public void watchLayoutKeepsSkipAndExpandedSeekActionsInsideVisibleControls()throws Exception{
        pref(PlaybackVolume.SEEK_BUTTONS,true);main(()->{call("updateSeekButtons");call("showcon");
            call("updateSegmentSkip",new Class[]{long.class},new Object[]{6000L});});
        await(()->player.findViewById(R.id.segment_skip_btn).getWidth()>0);
        main(()->{View skip=player.findViewById(R.id.segment_skip_btn);Rect r=new Rect();assertTrue(skip.getGlobalVisibleRect(r));
            assertEquals(skip.getWidth(),r.width());assertEquals(skip.getHeight(),r.height());
            View controls=(View)get("layout_control");Bitmap image=Bitmap.createBitmap(controls.getWidth(),controls.getHeight(),Bitmap.Config.ARGB_8888);
            controls.draw(new Canvas(image));try(FileOutputStream out=new FileOutputStream(new File(player.getFilesDir(),"experience-player.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(IOException e){throw new AssertionError(e);}finally{image.recycle();}
            call("showCompactPlayerTools");PlayerOptionsMenu menu=(PlayerOptionsMenu)get("playbackOptionsMenu");
            assertEquals("快进10秒",menu.getMenu().findItem(107).getTitle());menu.dismiss();
        });
    }
    @Test public void audioModeKeepsRightToolbarHiddenAfterBoundsUpdates()throws Exception{
        audioMode();
        main(()->call("updateRightToolbarBounds"));inst.waitForIdleSync();
        assertEquals(View.GONE,player.findViewById(R.id.right_control).getVisibility());
        main(()->{((Runnable)get("hidecon")).run();call("updateRightToolbarBounds");});inst.waitForIdleSync();
        main(()->call("showcon"));inst.waitForIdleSync();
        assertEquals(View.GONE,player.findViewById(R.id.right_control).getVisibility());
        main(()->player.findViewById(R.id.audio_more).performClick());inst.waitForIdleSync();
        assertEquals(View.GONE,player.findViewById(R.id.right_control).getVisibility());
    }
    @Test public void audioDashboardSurvivesHiddenControlsAndUsesRealPlaybackAndSeek()throws Exception{
        pref(PlaybackVolume.SEEK_SECONDS,7);
        audioMode();main(()->((Runnable)get("hidecon")).run());
        await(()->player.findViewById(R.id.audio_play).getWidth()>0);
        assertEquals(View.GONE,player.findViewById(R.id.bottom_buttons).getVisibility());
        tap(player.findViewById(R.id.audio_play));assertTrue((Boolean)get("isPlaying"));
        tap(player.findViewById(R.id.audio_play));assertFalse((Boolean)get("isPlaying"));
        main(()->call("seekToPosition",new Class[]{long.class},new Object[]{0L}));
        await(()->!Boolean.TRUE.equals(get("videoSeekPending")));
        main(()->player.findViewById(R.id.audio_forward).performClick());
        assertEquals(7000,get("video_now"));
        await(()->!Boolean.TRUE.equals(get("videoSeekPending")));
        assertTrue(((android.widget.TextView)player.findViewById(R.id.audio_time)).getText().toString().startsWith("00:07"));
        main(()->player.findViewById(R.id.audio_rewind).performClick());assertEquals(0,get("video_now"));
        main(()->{
            View controls=player.findViewById(R.id.control_layout);Bitmap image=Bitmap.createBitmap(controls.getWidth(),controls.getHeight(),Bitmap.Config.ARGB_8888);
            controls.draw(new Canvas(image));try(FileOutputStream out=new FileOutputStream(new File(player.getFilesDir(),"audio-dashboard.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(IOException e){throw new AssertionError(e);}finally{image.recycle();}
        });
    }
    @Test public void audioMenusChangeSpeedAndTimerPausesWithoutLeavingThePlayer()throws Exception{
        audioMode();
        main(()->{call("showAudioOptions");PlayerOptionsMenu menu=(PlayerOptionsMenu)get("playbackOptionsMenu");
            assertFalse(menu.getMenu().findItem(2).isEnabled());menu.getMenu().performIdentifierAction(1,0);
            menu=(PlayerOptionsMenu)get("playbackOptionsMenu");menu.getMenu().performIdentifierAction(2,0);
            assertEquals(.75f,(Float)get("currentPlaybackSpeed"),.001f);
            call("showAudioOptions");menu=(PlayerOptionsMenu)get("playbackOptionsMenu");menu.getMenu().performIdentifierAction(8,0);
            menu=(PlayerOptionsMenu)get("playbackOptionsMenu");assertNotNull(menu.getMenu().findItem(102));menu.dismiss();
            call("playerResume");call("setAudioSleepDelay",new Class[]{long.class},new Object[]{200L});
        });
        await(()->!Boolean.TRUE.equals(get("isPlaying")));assertEquals(0L,get("audioSleepDeadline"));
        main(()->{call("setAudioSleepDelay",new Class[]{long.class},new Object[]{15000L});
            Bundle state=new Bundle();call("onSaveInstanceState",new Class[]{Bundle.class},new Object[]{state});
            assertEquals(get("audioSleepDeadline"),state.getLong("audio_sleep_deadline"));
            call("playerResume");set("playbackSwitchInFlight",true);call("setAudioSleepDelay",new Class[]{long.class},new Object[]{100L});});
        await(()->Long.valueOf(0).equals(get("audioSleepDeadline")));
        assertFalse((Boolean)get("isPlaying"));assertEquals(Boolean.FALSE,get("pendingMediaPlaybackRequest"));
        assertEquals(Boolean.FALSE,get("resumeAfterPrepare"));main(()->set("playbackSwitchInFlight",false));
    }
    @Test public void audioBlankTapTogglesTopBarAndKeepsSpeedOutOfTheHeader()throws Exception{
        audioMode();main(()->call("showcon"));
        assertEquals(View.VISIBLE,player.findViewById(R.id.top).getVisibility());
        assertEquals(View.GONE,player.findViewById(R.id.text_speed).getVisibility());
        android.widget.ScrollView panel=player.findViewById(R.id.audio_panel);
        main(()->{
            long t=SystemClock.uptimeMillis();float x=Math.max(1,panel.getWidth()-3),y=Math.max(1,panel.getHeight()-3);
            MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);
            MotionEvent up=MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,x,y,0);
            panel.dispatchTouchEvent(down);panel.dispatchTouchEvent(up);down.recycle();up.recycle();
        });inst.waitForIdleSync();
        assertEquals(View.GONE,player.findViewById(R.id.top).getVisibility());
        main(()->{
            long t=SystemClock.uptimeMillis();float x=Math.max(1,panel.getWidth()-3),y=Math.max(1,panel.getHeight()-3);
            MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);
            MotionEvent up=MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,x,y,0);
            panel.dispatchTouchEvent(down);panel.dispatchTouchEvent(up);down.recycle();up.recycle();
        });inst.waitForIdleSync();
        assertEquals(View.VISIBLE,player.findViewById(R.id.top).getVisibility());
    }
    @Test public void lockedAudioPanelCannotPlaySeekOrOpenMenus()throws Exception{
        audioMode();
        main(()->{call("setScreenLocked",new Class[]{boolean.class},new Object[]{true});call("updateAudioDashboard");
            player.findViewById(R.id.audio_play).performClick();player.findViewById(R.id.audio_forward).performClick();
            player.findViewById(R.id.audio_more).performClick();});
        assertFalse((Boolean)get("isPlaying"));assertEquals(0,get("video_now"));
        assertFalse(player.findViewById(R.id.audio_progress).isEnabled());
        assertTrue(get("playbackOptionsMenu")==null||!((PlayerOptionsMenu)get("playbackOptionsMenu")).isShowing());
        main(()->call("setScreenLocked",new Class[]{boolean.class},new Object[]{false}));
        assertTrue(player.findViewById(R.id.audio_play).isEnabled());
    }
    private void audioMode()throws Exception{
        main(()->{set("bangumiPlayback",false);set("isAudioOnlyMode",true);call("updateAudioOnlyUI");call("showcon");});
        await(()->player.findViewById(R.id.audio_play).getHeight()>0);
    }
    @Test public void roundAudioPanelKeepsActionsInsideScrollableSafeArea()throws Exception{
        pref("player_ui_round",true);
        main(()->set("screen_round",true));audioMode();main(()->((Runnable)get("hidecon")).run());
        main(()->{android.widget.ScrollView scroll=player.findViewById(R.id.audio_panel);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());});
        inst.waitForIdleSync();SystemClock.sleep(100);
        View more=player.findViewById(R.id.audio_more);Rect rect=new Rect();assertTrue(more.getGlobalVisibleRect(rect));
        assertEquals(more.getHeight(),rect.height());
        tap(more);PlayerOptionsMenu menu=(PlayerOptionsMenu)get("playbackOptionsMenu");assertTrue(menu.isShowing());
        main(menu::dismiss);
        main(()->{
            View controls=player.findViewById(R.id.control_layout);Bitmap image=Bitmap.createBitmap(controls.getWidth(),controls.getHeight(),Bitmap.Config.ARGB_8888);
            controls.draw(new Canvas(image));try(FileOutputStream out=new FileOutputStream(new File(player.getFilesDir(),"audio-dashboard-round.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(IOException e){throw new AssertionError(e);}finally{image.recycle();}
        });
    }
    private void tap(View view){main(()->{int[] xy=new int[2];view.getLocationInWindow(xy);long t=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(t,t+(action==MotionEvent.ACTION_UP?50:0),action,xy[0]+view.getWidth()/2f,xy[1]+view.getHeight()/2f,0);player.dispatchTouchEvent(e);e.recycle();}
    });inst.waitForIdleSync();}
    private static Object primitive(Class<?> c){if(c==boolean.class)return false;if(c==int.class)return 0;if(c==long.class)return 0L;if(c==float.class)return 0f;return null;}
    private void wrap(AtomicInteger seeks,float[] gain){main(()->{IMediaPlayer original=(IMediaPlayer)get("ijkPlayer");
        set("ijkPlayer",Proxy.newProxyInstance(IMediaPlayer.class.getClassLoader(),new Class[]{IMediaPlayer.class},(p,m,a)->{
            if(seeks!=null&&m.getName().equals("seekTo"))seeks.incrementAndGet();if(gain!=null&&m.getName().equals("setVolume"))gain[0]=(Float)a[0];
            try{return m.invoke(original,a);}catch(InvocationTargetException e){throw e.getCause();}
        }));});}
    private void pref(String k,Object v){if(!old.containsKey(k))old.put(k,SharedPreferencesUtil.getSharedPreferences().getAll().get(k));
        if(v instanceof Boolean)SharedPreferencesUtil.putBoolean(k,(Boolean)v);else SharedPreferencesUtil.putInt(k,(Integer)v);}
    private interface Check{boolean ok();}
    private void await(Check c)throws Exception{long end=SystemClock.uptimeMillis()+18000;boolean[] ok={false};
        do{main(()->ok[0]=c.ok());if(ok[0])return;SystemClock.sleep(50);}while(SystemClock.uptimeMillis()<end);throw new AssertionError("Timed out");}
    private Object get(String n){try{Field f=PlayerActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(player);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String n,Object v){try{Field f=PlayerActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(player,v);}catch(Exception e){throw new AssertionError(e);}}
    private void call(String n){call(n,new Class[0],new Object[0]);}
    private void call(String n,Class[] t,Object[] a){try{Method m=PlayerActivity.class.getDeclaredMethod(n,t);m.setAccessible(true);m.invoke(player,a);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable r){inst.runOnMainSync(r);}
}
