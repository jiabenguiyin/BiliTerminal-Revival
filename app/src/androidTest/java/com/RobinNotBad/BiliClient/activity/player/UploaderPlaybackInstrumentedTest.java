package com.RobinNotBad.BiliClient.activity.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.*;
import android.view.KeyEvent;
import com.RobinNotBad.BiliClient.player.PlayerOptionsMenu;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.api.*;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.player.*;
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

/** Real decoder/intent/media-key navigation with a private, deterministic UP archive. */
@RunWith(AndroidJUnit4.class)
public class UploaderPlaybackInstrumentedTest {
    private static final long FIRST=500000007001L, SECOND=FIRST+1, THIRD=FIRST+2;
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> old=new HashMap<>();
    private PlayerActivity player; private File media; private String token;
    private final AtomicInteger pageCalls=new AtomicInteger();
    private volatile CountDownLatch gate;
    @Before public void before() throws Exception {
        for(String key:new String[]{"player_from_last","player_loop","player_autolandscape","player_subtitle_autoshow",
                "player_show_online","player_show_viewpoints","player_audio_only",SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK,
                SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED}) pref(key,false);
        pref("player_ui_showQualityBtn",true);pref(SharedPreferencesUtil.mid,42L);
        media=new File(inst.getTargetContext().getCacheDir(),"uploader-fixture.mp4");
        try(InputStream in=inst.getContext().getAssets().open("collection-playback.mp4");OutputStream out=new FileOutputStream(media)) {
            byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);
        }
        player=(PlayerActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),PlayerActivity.class)
                .putExtra("url",media.getAbsolutePath()).putExtra("title","投稿连播测试").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->Boolean.TRUE.equals(get("isPrepared")),20000);
        main(()->{
            call("playerPause");set("aid",FIRST);set("cid",FIRST+10);set("isOnlineVideo",true);set("explicitOnlineQueue",true);
            set("collectionPlaybackSource",new CollectionPlaybackSource(){
                @Override public CollectionPlaybackQueue loadQueue(long aid){throw new AssertionError("UP context must override own collection");}
                @Override public PlayerData loadItem(long aid,int page,PlayerData current){
                    PlayerData p=new PlayerData();p.aid=aid;p.cid=aid+10+page;p.title="投稿 "+aid;
                    p.videoUrl=media.getAbsolutePath();p.danmakuCount=0;
                    p.cids=new ArrayList<>(Arrays.asList(p.cid));p.pagenames=new ArrayList<>(Arrays.asList("视频"));p.currentPageIndex=page;
                    if(aid==current.aid&&current.cids!=null&&current.cids.size()>1){p.cids=current.cids;p.pagenames=current.pagenames;p.cid=p.cids.get(page);}
                    return p;
                }
                @Override public void reportHistory(long aid,long cid,long seconds){}
            });
        });
    }
    @After public void after(){
        if(gate!=null)gate.countDown();if(player!=null){main(player::finish);inst.waitForIdleSync();}
        for(Map.Entry<String,Object> e:old.entrySet()){
            if(e.getValue()==null)SharedPreferencesUtil.removeValue(e.getKey());
            else if(e.getValue() instanceof Long)SharedPreferencesUtil.putLong(e.getKey(),(Long)e.getValue());
            else if(e.getValue() instanceof String)SharedPreferencesUtil.putString(e.getKey(),(String)e.getValue());
            else SharedPreferencesUtil.putBoolean(e.getKey(),(Boolean)e.getValue());
        }
        if(media!=null)media.delete();
    }
    private void enable(boolean oldest) throws Exception {
        FavoritePlaybackSource.Session s=FavoritePlaybackSource.Session.uploads(42,99,oldest,oldest?2:1,false,
                Arrays.asList(card(FIRST)));
        token=FavoritePlaybackSource.create(inst.getTargetContext(),s);
        main(()->{
            set("favoritePlaybackToken",token);player.getIntent().putExtra(FavoritePlaybackSource.TOKEN,token)
                    .putExtra(FavoritePlaybackSource.EXPLICIT,true);
            set("favoritePlaybackSource",new FavoritePlaybackSource(){
                @Override protected ApiResult page(Session session,List<VideoCard> out,RequestGuard guard)throws Exception{
                    pageCalls.incrementAndGet();assertTrue(session.isUploads());assertEquals(99,session.owner);
                    assertEquals(oldest?1:2,session.nextPage);
                    if(gate!=null)assertTrue(gate.await(12,TimeUnit.SECONDS));
                    out.add(card(SECOND));out.add(card(THIRD));return new ApiResult().setBottom(true);
                }
            });call("refreshCollectionPlaybackSettings");
        });
        await(()->get("favoriteSession")!=null,8000);
    }
    @Test public void explicitEntryWorksWithGlobalSwitchesOffAndNeverChangesThem()throws Exception{
        enable(true);assertEquals(true,get("collectionPlaybackEnabled"));assertEquals(true,get("showCollectionPlayback"));
        assertFalse(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK,true));
        assertFalse(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,true));
        assertEquals(0,pageCalls.get());assertTrue(((FavoritePlaybackSource.Session)get("favoriteSession")).isUploads());
    }
    @Test public void realCompletionLoadsAnotherUploadPageThenHeadsetPreviousReturns()throws Exception{
        enable(true);complete();awaitItem(SECOND);assertEquals(1,pageCalls.get());
        main(()->player.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PREVIOUS)));
        awaitItem(FIRST);assertEquals(1,pageCalls.get());
        assertEquals(0,FavoritePlaybackSource.read(inst.getTargetContext(),token,42).nextPage);
    }
    @Test public void newestTraversalHasCorrectChronologicalLabelsAndPaging()throws Exception{
        enable(false);assertTrue(player.findViewById(R.id.collection_order_btn).getContentDescription().toString().contains("新→旧"));
        next();awaitItem(SECOND);assertEquals(1,pageCalls.get());
        main(()->{set("collectionPlaybackOrder",3);call("updateCollectionPlaybackControls");});
        assertTrue(player.findViewById(R.id.collection_order_btn).getContentDescription().toString().contains("旧→新"));
        next();awaitItem(FIRST);assertEquals(1,pageCalls.get());
    }
    @Test public void allPartsFinishBeforeNextUpload()throws Exception{
        enable(true);main(()->{set("cids",new ArrayList<>(Arrays.asList(FIRST+10,FIRST+11)));
            set("pagenames",new ArrayList<>(Arrays.asList("P1","P2")));set("currentPageIndex",0);});
        complete();await(()->Integer.valueOf(1).equals(get("currentPageIndex"))&&Boolean.TRUE.equals(get("isPrepared")),15000);
        assertEquals(FIRST,get("aid"));assertEquals(0,pageCalls.get());main(()->call("playerPause"));
        complete();awaitItem(SECOND);assertEquals(1,pageCalls.get());
    }
    @Test public void localOffCancelsPendingPageAndRemainsOffAcrossSettingsRefresh()throws Exception{
        gate=new CountDownLatch(1);enable(true);next();await(()->pageCalls.get()==1,6000);
        main(()->{call("showPlaybackOptions");PlayerOptionsMenu m=(PlayerOptionsMenu)get("playbackOptionsMenu");
            assertEquals("投稿连播",m.getMenu().findItem(2).getTitle());m.getMenu().performIdentifierAction(2,0);m.dismiss();
            call("refreshCollectionPlaybackSettings");});gate.countDown();SystemClock.sleep(350);inst.waitForIdleSync();
        assertEquals(FIRST,get("aid"));assertEquals(false,get("collectionPlaybackEnabled"));
        assertFalse(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,true));
        Bundle state=new Bundle();main(()->call("onSaveInstanceState",new Class[]{Bundle.class},new Object[]{state}));
        assertFalse(state.getBoolean("explicit_online_enabled",true));
        assertEquals(1,FavoritePlaybackSource.read(inst.getTargetContext(),token,42).nextPage);
    }
    @Test public void switchedAccountDiscardsReturnedUploadPage()throws Exception{
        gate=new CountDownLatch(1);enable(true);next();await(()->pageCalls.get()==1,6000);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,43L);gate.countDown();
        await(()->!Boolean.TRUE.equals(get("favoritePaging")),6000);assertEquals(FIRST,get("aid"));
        assertFalse((Boolean)get("isPlaying"));
    }
    @Test public void explicitFlagSurvivesParcelAndForcesInternalPlayerOnlyForThisEntry()throws Exception{
        pref("player","systemPlayer");PlayerData p=new PlayerData();p.aid=FIRST;p.explicitOnlineQueue=true;p.favoritePlaybackToken="test-token";
        Parcel parcel=Parcel.obtain();try{p.writeToParcel(parcel,0);parcel.setDataPosition(0);p=PlayerData.CREATOR.createFromParcel(parcel);
            assertTrue(p.explicitOnlineQueue);Intent i=PlayerApi.jumpToPlayer(p);
            assertEquals(PlayerActivity.class.getName(),i.getComponent().getClassName());
            assertTrue(i.getBooleanExtra(FavoritePlaybackSource.EXPLICIT,false));assertEquals("test-token",i.getStringExtra(FavoritePlaybackSource.TOKEN));
            assertEquals("systemPlayer",SharedPreferencesUtil.getString("player",""));
            PlayerData ordinary=new PlayerData();ordinary.videoUrl=media.getAbsolutePath();
            assertFalse(PlayerApi.jumpToPlayer(ordinary).getBooleanExtra(FavoritePlaybackSource.EXPLICIT,false));
        }finally{parcel.recycle();}
    }
    private VideoCard card(long id){VideoCard c=new VideoCard();c.aid=id;return c;}
    private void next(){main(()->player.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_NEXT)));}
    private void complete(){main(()->{IMediaPlayer d=(IMediaPlayer)get("ijkPlayer");
        call("seekPlaybackPlayers",new Class[]{long.class},new Object[]{d.getDuration()-350});set("isPlaying",true);call("startPlaybackPlayers");});}
    private void awaitItem(long id)throws Exception{await(()->Long.valueOf(id).equals(get("aid"))&&Boolean.TRUE.equals(get("isPrepared")),15000);main(()->call("playerPause"));}
    private interface Check{boolean get();}
    private void await(Check check,long timeout)throws Exception{long end=SystemClock.uptimeMillis()+timeout;boolean[] ok={false};
        do{main(()->ok[0]=check.get());if(ok[0])return;SystemClock.sleep(50);}while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Timeout aid="+get("aid")+" pages="+pageCalls.get());}
    private void pref(String key,Object value){old.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        if(value instanceof Long)SharedPreferencesUtil.putLong(key,(Long)value);else if(value instanceof String)SharedPreferencesUtil.putString(key,(String)value);
        else SharedPreferencesUtil.putBoolean(key,(Boolean)value);}
    private Object get(String n){try{Field f=PlayerActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(player);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String n,Object v){try{Field f=PlayerActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(player,v);}catch(Exception e){throw new AssertionError(e);}}
    private void call(String n){call(n,new Class[0],new Object[0]);}
    private void call(String n,Class[] types,Object[] args){try{Method m=PlayerActivity.class.getDeclaredMethod(n,types);m.setAccessible(true);m.invoke(player,args);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable r){inst.runOnMainSync(r);}
}
