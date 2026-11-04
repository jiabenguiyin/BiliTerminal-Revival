package com.RobinNotBad.BiliClient.activity.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import com.RobinNotBad.BiliClient.player.PlayerOptionsMenu;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackQueue;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackSource;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CollectionPlaybackInstrumentedTest {
    private static final long FIRST=500000000001L, SECOND=500000000002L, THIRD=500000000003L;
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> old=new HashMap<>();
    private PlayerActivity player;
    private File media;
    private FakeSource source;

    private final class FakeSource extends CollectionPlaybackSource {
        volatile int queueCalls, itemCalls;
        volatile boolean empty, fail;
        volatile CountDownLatch gate;
        volatile long historyAid, historyCid;
        volatile int requestedQn;
        @Override public CollectionPlaybackQueue loadQueue(long aid) {
            queueCalls++;
            return empty ? null : new CollectionPlaybackQueue(91,new long[]{FIRST,SECOND,THIRD});
        }
        @Override public PlayerData loadItem(long aid,int page,PlayerData current) throws Exception {
            requestedQn=current.qn;
            itemCalls++;
            if (gate!=null) assertTrue(gate.await(10,TimeUnit.SECONDS));
            if (fail) throw new java.io.IOException("fixture failure");
            PlayerData data=new PlayerData();
            data.aid=aid;data.cid=aid+page+10;data.title="合集测试 "+(aid-FIRST+1);
            data.videoUrl=media.getAbsolutePath();data.danmakuCount=0;data.qn=0;
            data.pagenames=new ArrayList<>(Arrays.asList("测试视频"));
            data.cids=new ArrayList<>(Arrays.asList(data.cid));data.currentPageIndex=page;
            if(aid==current.aid && current.cids!=null && current.cids.size()>1) {
                data.pagenames=current.pagenames;data.cids=current.cids;
                data.cid=data.cids.get(page);
            }
            return data;
        }
        @Override public void reportHistory(long aid,long cid,long seconds) {
            historyAid=aid;historyCid=cid;
        }
    }

    @Before public void start() throws Exception {
        for(String key:new String[]{"player_from_last","player_loop","player_autolandscape",
                "player_subtitle_autoshow","player_show_online","player_show_viewpoints",
                "player_audio_only",SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK}) pref(key,false);
        pref("player_ui_showQualityBtn",true);
        pref(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,true);
        media=new File(inst.getTargetContext().getCacheDir(),"collection-fixture.mp4");
        try(InputStream in=inst.getContext().getAssets().open("collection-playback.mp4");
            FileOutputStream out=new FileOutputStream(media)) {
            byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
        }
        player=(PlayerActivity) inst.startActivitySync(new Intent(inst.getTargetContext(),PlayerActivity.class)
                .putExtra("url",media.getAbsolutePath()).putExtra("title","合集播放测试")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->Boolean.TRUE.equals(field("isPrepared")),15000);
        source=new FakeSource();
        main(()->{
            player.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    |android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    |android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            call("playerPause");set("aid",FIRST);set("cid",FIRST+10);
            set("isOnlineVideo",true);set("collectionPlaybackSource",source);
            player.getIntent().putExtra("aid",FIRST).putExtra("cid",FIRST+10);
            call("showcon");
        });
    }
    @After public void close() {
        if(source!=null&&source.gate!=null)source.gate.countDown();
        if(player!=null)main(()->player.finish());
        for(Map.Entry<String,Object> x:old.entrySet()) {
            if(x.getValue()==null)SharedPreferencesUtil.removeValue(x.getKey());
            else SharedPreferencesUtil.putBoolean(x.getKey(),(Boolean)x.getValue());
        }
        if(media!=null)media.delete();
    }

    @Test public void hiddenByDefaultThenMenuToggleAndAllThreeOrdersWork() throws Exception {
        main(()->call("refreshCollectionPlaybackSettings"));
        assertEquals(0,source.queueCalls);
        assertEquals(View.GONE,player.findViewById(R.id.collection_order_btn).getVisibility());
        enable();
        main(()->{
            player.findViewById(R.id.button_quality).performClick();
            PlayerOptionsMenu menu=(PlayerOptionsMenu)field("playbackOptionsMenu");
            assertEquals(2,menu.getMenu().size());assertTrue(menu.getMenu().findItem(2).isChecked());
            menu.dismiss();
            View order=player.findViewById(R.id.collection_order_btn);
            for(int expected:new int[]{1,2,3,0}){assertTrue(order.performClick());assertEquals(expected,field("collectionPlaybackOrder"));}
            player.findViewById(R.id.button_quality).performClick();
            menu=(PlayerOptionsMenu)field("playbackOptionsMenu");
            assertTrue(menu.getMenu().performIdentifierAction(2,0));menu.dismiss();
            assertEquals(View.GONE,order.getVisibility());
            assertFalse(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,true));
        });
    }

    @Test public void actualDecoderCompletionAdvancesAndUsesTheNewIdentity() throws Exception {
        enable();complete();
        await(()->((Long)field("aid"))==SECOND&&Boolean.TRUE.equals(field("isPrepared")),15000);
        main(()->call("playerPause"));
        assertEquals(SECOND,player.getIntent().getLongExtra("aid",0));
        assertEquals(SECOND+10,field("cid"));
        await(()->source.historyAid==FIRST,5000);
        assertEquals(FIRST+10,source.historyCid);
        assertEquals(View.VISIBLE,player.findViewById(R.id.collection_order_btn).getVisibility());
    }

    @Test public void sequentialStopsAtEndEvenWhenOldLoopAndPartAutoplayAreOn() throws Exception {
        enable();main(()->{set("aid",THIRD);set("loop_enabled",true);set("auto_next_enabled",true);});
        complete();await(()->Boolean.TRUE.equals(field("playbackCompleted")),8000);
        assertEquals(0,source.itemCalls);assertFalse((Boolean)field("isPlaying"));
    }

    @Test public void reverseCompletionAndHeadsetPreviousFollowOppositeDirections() throws Exception {
        enable(); main(() -> {
            set("aid", THIRD); set("collectionPlaybackOrder", CollectionPlaybackQueue.REVERSE);
            ((CollectionPlaybackQueue) field("collectionQueue")).recordPlayed(THIRD, false);
        });
        complete(); await(() -> Long.valueOf(SECOND).equals(field("aid")) && Boolean.TRUE.equals(field("isPrepared")), 15000);
        main(() -> {
            call("playerPause");
            player.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        });
        await(() -> Long.valueOf(THIRD).equals(field("aid")) && Boolean.TRUE.equals(field("isPrepared")), 15000);
        main(() -> {
            set("aid", FIRST); set("loop_enabled", true);
            ((CollectionPlaybackQueue) field("collectionQueue")).recordPlayed(FIRST, false);
        });
        complete(); await(() -> Boolean.TRUE.equals(field("playbackCompleted")) && !Boolean.TRUE.equals(field("isPlaying")), 8000);
        assertEquals(FIRST, field("aid"));
    }

    @Test public void failedNextItemLeavesCurrentVideoAvailableForReplay() throws Exception {
        enable();source.fail=true;complete();
        await(()->source.itemCalls==1&&!Boolean.TRUE.equals(field("playbackSwitchInFlight")),10000);
        assertEquals(FIRST,field("aid"));assertTrue((Boolean)field("isPrepared"));
        assertTrue((Boolean)field("playbackCompleted"));assertFalse((Boolean)field("isPlaying"));
        SystemClock.sleep(350);assertEquals(1,source.itemCalls);
    }

    @Test public void collectionPlaysRemainingPartsBeforeTheNextVideo() throws Exception {
        enable();main(()->{
            set("pagenames",new ArrayList<>(Arrays.asList("P1","P2")));
            set("cids",new ArrayList<>(Arrays.asList(FIRST+10,FIRST+11)));
            set("currentPageIndex",0);
        });
        complete();
        await(()->((Integer)field("currentPageIndex"))==1&&Boolean.TRUE.equals(field("isPrepared")),15000);
        main(()->call("playerPause"));
        assertEquals(FIRST,field("aid"));assertEquals(FIRST+11,field("cid"));
        complete();
        await(()->((Long)field("aid"))==SECOND&&Boolean.TRUE.equals(field("isPrepared")),15000);
        main(()->call("playerPause"));
        assertEquals(2,source.itemCalls);
    }

    @Test public void aNewQueueItemUsesNetworkDefaultsUnlessTheUserPinnedAQuality() throws Exception {
        String[] keys={"play_qn","play_qn_mobile","play_qn_wifi"};Map<String,Object> saved=new HashMap<>();
        for(String key:keys)saved.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        try {
            SharedPreferencesUtil.putInt(keys[0],32);SharedPreferencesUtil.putInt(keys[1],16);SharedPreferencesUtil.putInt(keys[2],16);enable();
            main(()->call("skipPlaybackItem",new Class[]{boolean.class},new Object[]{true}));
            await(()->((Long)field("aid"))==SECOND&&Boolean.TRUE.equals(field("isPrepared")),15000);
            assertEquals(16,source.requestedQn);
            main(()->{
                set("currentQuality",16);set("qnValueList",new int[]{16,80});set("qnStrList",new String[]{"360P","1080P"});call("showQualitySelectorCard");
                androidx.recyclerview.widget.RecyclerView list=player.findViewById(R.id.quality_selector_list);
                androidx.recyclerview.widget.RecyclerView.Adapter adapter=list.getAdapter();
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder=adapter.onCreateViewHolder(list,adapter.getItemViewType(0));
                adapter.onBindViewHolder(holder,0);holder.itemView.findViewById(R.id.btn).performClick();
            });
            assertEquals(16,field("manualPlaybackQuality"));SharedPreferencesUtil.putInt(keys[1],64);SharedPreferencesUtil.putInt(keys[2],64);
            main(()->call("skipPlaybackItem",new Class[]{boolean.class},new Object[]{true}));
            await(()->((Long)field("aid"))==THIRD&&Boolean.TRUE.equals(field("isPrepared")),15000);assertEquals(16,source.requestedQn);
        } finally {
            for(String key:keys){Object value=saved.get(key);if(value==null)SharedPreferencesUtil.removeValue(key);else SharedPreferencesUtil.putInt(key,((Number)value).intValue());}
        }
    }

    @Test public void repeatOneRewindsWithoutLoadingAnotherVideo() throws Exception {
        enable();main(()->set("collectionPlaybackOrder",CollectionPlaybackQueue.REPEAT_ONE));
        complete();
        await(()->((IMediaPlayer)field("ijkPlayer")).getCurrentPosition()<1800
                &&Boolean.TRUE.equals(field("isPlaying")),8000);
        main(()->call("playerPause"));
        assertEquals(FIRST,field("aid"));assertEquals(0,source.itemCalls);
    }

    @Test public void manualNextThenPreviousRestoresTheInterruptedPosition() throws Exception {
        assertInterruptedPositionRestored(false, true);
    }

    @Test public void listeningModeNextThenPreviousAlsoRestoresTheInterruptedPosition() throws Exception {
        assertInterruptedPositionRestored(true, true);
    }

    @Test public void disablingResumeStillStartsRevisitedItemsFromTheBeginning() throws Exception {
        assertInterruptedPositionRestored(false, false);
    }

    private void assertInterruptedPositionRestored(boolean audioOnly, boolean resume) throws Exception {
        SharedPreferencesUtil.putBoolean("player_from_last",resume);
        if (audioOnly) {
            Object previousDecoder=field("ijkPlayer");
            main(()->call("toggleAudioOnlyMode"));
            await(()->field("ijkPlayer")!=null&&field("ijkPlayer")!=previousDecoder
                    &&Boolean.TRUE.equals(field("isPrepared"))&&Boolean.TRUE.equals(field("isAudioOnlyMode")),15000);
        }
        enable();
        main(()->call("seekToPosition",new Class[]{long.class},new Object[]{2000L}));
        await(()->((IMediaPlayer)field("ijkPlayer")).getCurrentPosition()>=1500,5000);
        long position=((IMediaPlayer)field("ijkPlayer")).getCurrentPosition();
        main(()->call("skipPlaybackItem",new Class[]{boolean.class},new Object[]{true}));
        await(()->((Long)field("aid"))==SECOND&&Boolean.TRUE.equals(field("isPrepared")),15000);
        main(()->call("skipPlaybackItem",new Class[]{boolean.class},new Object[]{false}));
        await(()->((Long)field("aid"))==FIRST&&Boolean.TRUE.equals(field("isPrepared")),15000);
        assertEquals(position,(Long)field("progress_history"),350);
        assertEquals(position,player.getIntent().getIntExtra("progress",-1),350);
        if (resume) await(()->Math.abs(((IMediaPlayer)field("ijkPlayer")).getCurrentPosition()-position)<500,5000);
        else assertTrue(((IMediaPlayer)field("ijkPlayer")).getCurrentPosition()<1000);
        assertEquals(audioOnly,field("isAudioOnlyMode"));
        assertFalse((Boolean)field("isPlaying"));
    }

    @Test public void switchingPartsRestoresOnlyTheMatchingPart() throws Exception {
        SharedPreferencesUtil.putBoolean("player_from_last",true); enable();
        main(()->{
            set("pagenames",new ArrayList<>(Arrays.asList("P1","P2")));
            set("cids",new ArrayList<>(Arrays.asList(FIRST+10,FIRST+11)));
            set("currentPageIndex",0);
            call("seekToPosition",new Class[]{long.class},new Object[]{2000L});
        });
        await(()->((IMediaPlayer)field("ijkPlayer")).getCurrentPosition()>=1500,5000);
        long position=((IMediaPlayer)field("ijkPlayer")).getCurrentPosition();
        main(()->call("switchToPage",new Class[]{int.class},new Object[]{1}));
        await(()->((Integer)field("currentPageIndex"))==1&&Boolean.TRUE.equals(field("isPrepared")),15000);
        assertEquals(0L,field("progress_history"));
        main(()->call("switchToPage",new Class[]{int.class},new Object[]{0}));
        await(()->((Integer)field("currentPageIndex"))==0&&Boolean.TRUE.equals(field("isPrepared")),15000);
        assertEquals(position,(Long)field("progress_history"),350);
        await(()->Math.abs(((IMediaPlayer)field("ijkPlayer")).getCurrentPosition()-position)<500,5000);
    }

    @Test public void completedVideoStartsFromTheBeginningWhenRevisited() throws Exception {
        SharedPreferencesUtil.putBoolean("player_from_last",true); enable(); complete();
        await(()->((Long)field("aid"))==SECOND&&Boolean.TRUE.equals(field("isPrepared")),15000);
        main(()->{call("playerPause");call("skipPlaybackItem",new Class[]{boolean.class},new Object[]{false});});
        await(()->((Long)field("aid"))==FIRST&&Boolean.TRUE.equals(field("isPrepared")),15000);
        assertEquals(0L,field("progress_history"));
        assertTrue(((IMediaPlayer)field("ijkPlayer")).getCurrentPosition()<1000);
    }

    @Test public void videoChangesReleaseThePreviousOnlineCounterTimer() {
        java.util.Timer previous=new java.util.Timer();
        main(()->{set("onlineTimer",previous);call("onlineChange");});
        assertNull(field("onlineTimer"));
        try {
            previous.schedule(new java.util.TimerTask(){@Override public void run(){}},10000);
            fail("Old video counter timer was still active");
        } catch (IllegalStateException expected) { }
    }

    @Test public void settingsSwitchEnablesPlaybackButRebindingKeepsMenuChoice() {
        main(()->{
            android.view.View row=android.view.LayoutInflater.from(new android.view.ContextThemeWrapper(
                    player,R.style.Theme_BiliClient)).inflate(
                    R.layout.cell_setting_switch, null, false);
            com.RobinNotBad.BiliClient.adapter.SettingsAdapter.SwitchHolder holder=
                    new com.RobinNotBad.BiliClient.adapter.SettingsAdapter.SwitchHolder(row);
            com.RobinNotBad.BiliClient.model.SettingSection setting=new com.RobinNotBad.BiliClient.model.SettingSection(
                    "switch","显示播放合集",SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK,"","false");
            holder.bind(setting);
            android.widget.CompoundButton toggle=row.findViewById(R.id.setting_switch);
            assertFalse(toggle.isChecked());
            SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,false);
            toggle.performClick();
            assertTrue(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,false));
            SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,false);
            holder.bind(setting);
            assertFalse(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,true));
        });
    }

    @Test public void turningOffDuringNetworkLoadDiscardsTheLateResponse() throws Exception {
        enable();source.gate=new CountDownLatch(1);complete();
        await(()->source.itemCalls==1,8000);
        main(()->{
            player.findViewById(R.id.button_quality).performClick();
            PlayerOptionsMenu menu=(PlayerOptionsMenu)field("playbackOptionsMenu");
            assertFalse(menu.getMenu().findItem(1).isEnabled());
            assertTrue(menu.getMenu().performIdentifierAction(2,0));menu.dismiss();
        });
        source.gate.countDown();SystemClock.sleep(600);
        assertEquals(FIRST,field("aid"));assertFalse((Boolean)field("playbackSwitchInFlight"));
        assertEquals(View.GONE,player.findViewById(R.id.collection_order_btn).getVisibility());
    }

    @Test public void ordinaryVideosAndSettingsOffNeverShowAnOrderControl() throws Exception {
        source.empty=true;
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK,true);
        main(()->call("refreshCollectionPlaybackSettings"));
        await(()->!Boolean.TRUE.equals(field("collectionQueueLoading")),8000);
        main(()->{
            assertEquals(View.GONE,player.findViewById(R.id.collection_order_btn).getVisibility());
            player.findViewById(R.id.button_quality).performClick();
            PlayerOptionsMenu menu=(PlayerOptionsMenu)field("playbackOptionsMenu");
            assertFalse(menu.getMenu().findItem(2).isEnabled());menu.dismiss();
        });
    }

    @Test public void orderControlFitsSmallScreensAndHasAccessibleModeText() throws Exception {
        enable();
        android.graphics.Rect visible=new android.graphics.Rect();
        main(()->{
            View order=player.findViewById(R.id.collection_order_btn);
            assertTrue(order.getContentDescription().toString().contains("顺序播放"));
            android.graphics.Rect rect=new android.graphics.Rect();
            if(!order.getGlobalVisibleRect(rect)) {
                // Short screens keep full-size targets in a scrollable toolbar.
                android.widget.ScrollView scroll=(android.widget.ScrollView)order.getParent().getParent();
                scroll.setSmoothScrollingEnabled(false);
                assertTrue(scroll.fullScroll(View.FOCUS_DOWN));
            }
            int[] point=new int[2];order.getLocationOnScreen(point);
            assertTrue("Order not visible: "+order.getWidth()+"x"+order.getHeight()+" at "+Arrays.toString(point)
                    +" parent="+((View)order.getParent()).getWidth()+"x"+((View)order.getParent()).getHeight()
                    +" root="+player.getWindow().getDecorView().getWidth()+"x"+player.getWindow().getDecorView().getHeight(),
                    order.getGlobalVisibleRect(rect));assertTrue(rect.width()>0&&rect.height()>0);
            assertTrue(rect.left>=0&&rect.right<=player.getWindow().getDecorView().getWidth());
            visible.set(rect);
        });
        long now=SystemClock.uptimeMillis();
        inst.sendPointerSync(android.view.MotionEvent.obtain(now,now,0,visible.centerX(),visible.centerY(),0));
        inst.sendPointerSync(android.view.MotionEvent.obtain(now,now+30,1,visible.centerX(),visible.centerY(),0));
        await(()->((Integer)field("collectionPlaybackOrder"))==CollectionPlaybackQueue.SHUFFLE,3000);
        capture("collection-controls.png");
        main(()->player.findViewById(R.id.button_quality).performClick());
        inst.waitForIdleSync();capture("collection-options.png");
        main(()->((PlayerOptionsMenu)field("playbackOptionsMenu")).dismiss());
    }

    private void capture(String name) throws Exception {
        Bitmap image=inst.getUiAutomation().takeScreenshot();
        boolean black=true;
        if(image!=null)for(int y=0;y<image.getHeight()&&black;y+=3)for(int x=0;x<image.getWidth();x+=3)
            if((image.getPixel(x,y)&0x00ffffff)!=0){black=false;break;}
        if(black) {
            if(image!=null)image.recycle();
            if(name.contains("options"))return;
            View decor=player.getWindow().getDecorView();
            image=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
            Bitmap canvasImage=image;
            main(()->decor.draw(new android.graphics.Canvas(canvasImage)));
        }
        if(image!=null)try(FileOutputStream out=new FileOutputStream(new File(inst.getTargetContext().getCacheDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);image.recycle();}
    }

    private void enable() throws Exception {
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK,true);
        main(()->call("refreshCollectionPlaybackSettings"));
        await(()->field("collectionQueue")!=null,8000);
        main(()->{call("autohideReset");call("showcon");});
        inst.waitForIdleSync();
        await(()->player.findViewById(R.id.collection_order_btn).getWidth()>0,4000);
    }
    private void complete() {
        main(()->{
            IMediaPlayer decoder=(IMediaPlayer)field("ijkPlayer");
            assertTrue(decoder.getDuration()>1000);
            call("seekPlaybackPlayers",new Class[]{long.class},new Object[]{decoder.getDuration()-350});
            set("isPlaying",true);call("startPlaybackPlayers");
        });
    }
    private interface Check { boolean get(); }
    private void await(Check c,long ms)throws Exception {
        long until=SystemClock.uptimeMillis()+ms;
        while(!checkOnMain(c)&&SystemClock.uptimeMillis()<until)SystemClock.sleep(60);
        if (!checkOnMain(c)) {
            IMediaPlayer decoder=(IMediaPlayer)field("ijkPlayer");
            throw new AssertionError("Timed out: aid="+field("aid")+", prepared="+field("isPrepared")
                    +", playing="+field("isPlaying")+", completed="+field("playbackCompleted")
                    +", switching="+field("playbackSwitchInFlight")+", calls="+source.itemCalls
                    +", position="+decoder.getCurrentPosition()+"/"+decoder.getDuration());
        }
    }
    private boolean checkOnMain(Check check) {
        boolean[] value={false};
        main(()->value[0]=check.get());
        return value[0];
    }
    private void pref(String key,boolean value){old.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));SharedPreferencesUtil.putBoolean(key,value);}
    private Object field(String name){try{Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(player);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object v){try{Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(player,v);}catch(Exception e){throw new AssertionError(e);}}
    private void call(String name){call(name,new Class[0],new Object[0]);}
    private void call(String name,Class[] types,Object[] values){try{Method m=PlayerActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(player,values);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable task){inst.runOnMainSync(task);}
}
