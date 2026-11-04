package com.RobinNotBad.BiliClient.activity.player;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.player.*;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CachedHeadsetPlaybackInstrumentedTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> old=new HashMap<>();
    private Context context;
    private PlayerActivity player;
    private File directory;
    private LocalVideo group;
    private String token;
    private VideoStorageUtil.Node safFixture;
    private SpySource spy;

    private static final class SpySource extends CollectionPlaybackSource {
        volatile int networkCalls;
        @Override public CollectionPlaybackQueue loadQueue(long aid)throws Exception{networkCalls++;throw new IOException("offline queue used network");}
        @Override public PlayerData loadItem(long aid,int page,PlayerData current)throws Exception{networkCalls++;throw new IOException("offline item used network");}
        @Override public void reportHistory(long aid,long cid,long seconds){networkCalls++;}
    }

    @Before public void setup()throws Exception {
        context=inst.getTargetContext();
        for(String key:new String[]{"player_from_last","player_loop","player_autolandscape","player_subtitle_autoshow",
                SharedPreferencesUtil.PLAYER_MEDIA_SESSION_ENABLE,"player_audio_only","pref_switch_danmaku"}) pref(key,false);
        pref("player_show_online",true); // Offline playback must not start its network counter.
        pref(SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK,true);
        pref(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,true);
        pref("player_longclick",true);pref(SharedPreferencesUtil.PLAYER_LONGPRESS_SWITCH,true);
        pref("player_scale",true);
        old.put(SharedPreferencesUtil.PLAYER_CACHED_PLAYBACK_SCOPE,SharedPreferencesUtil.getSharedPreferences().getAll().get(SharedPreferencesUtil.PLAYER_CACHED_PLAYBACK_SCOPE));
        SharedPreferencesUtil.putInt(SharedPreferencesUtil.PLAYER_CACHED_PLAYBACK_SCOPE,0);
        directory=new File(context.getCacheDir(),"cache-headset-"+UUID.randomUUID());assertTrue(directory.mkdirs());
        group=new LocalVideo();group.title="离线歌单";
        for(int i=0;i<3;i++) {
            File folder=new File(directory,"track-"+i);assertTrue(folder.mkdirs());
            File media=new File(folder,i==1?"audio.m4a":"video.mp4");
            try(InputStream in=inst.getContext().getAssets().open(i==1?"collection-audio.m4a":"collection-playback.mp4");OutputStream out=new FileOutputStream(media)){copy(in,out);}
            CachedPlaybackItem item=new CachedPlaybackItem();item.title="离线歌曲 "+(i+1);
            item.media=media.getAbsolutePath();item.folder=folder.getAbsolutePath();item.audioOnly=i==1;
            group.playbackItems.add(item);
        }
        launch(group,group.playbackItems.get(0));
    }

    private void launch(LocalVideo selected,CachedPlaybackItem first)throws Exception {
        token=CachedPlaybackSnapshot.create(context,Collections.singletonList(selected),selected,first.media);
        Intent intent=new Intent(context,PlayerActivity.class).putExtra("url",first.media).putExtra("title",first.title)
                .putExtra("danmaku",first.danmaku).putExtra("audio_only",first.audioOnly)
                .putExtra("local_progress_folder",first.folder).putExtra("local_subtitle_root",first.subtitles)
                .putExtra(CachedPlaybackSnapshot.TOKEN,token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        player=(PlayerActivity)inst.startActivitySync(intent);spy=new SpySource();
        main(()->{
            player.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    |android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            player.getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    |android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    |android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE|android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            set("resumeAfterPrepare",false);set("collectionPlaybackSource",spy);
            if((Boolean)field("isPrepared"))call("playerPause");
        });
        await(()->(Boolean)field("isPrepared")&&field("cachedPlaybackSnapshot")!=null,15000);
        main(()->{call("playerPause");call("showcon");});
        assertEquals(0,spy.networkCalls);
    }

    @After public void close() {
        if(player!=null)main(()->player.finish());
        if(safFixture!=null)safFixture.deleteRecursive();
        if(directory!=null)delete(directory);
        for(Map.Entry<String,Object> e:old.entrySet()) {
            if(e.getValue()==null)SharedPreferencesUtil.removeValue(e.getKey());
            else if(e.getValue() instanceof Boolean)SharedPreferencesUtil.putBoolean(e.getKey(),(Boolean)e.getValue());
            else SharedPreferencesUtil.putInt(e.getKey(),(Integer)e.getValue());
        }
    }

    @Test public void decoderCompletionPlaysCachedAudioWithoutNetworkAndSavesTheOldTrackProgress()throws Exception {
        main(()->{
            IMediaPlayer decoder=(IMediaPlayer)field("ijkPlayer");
            call("seekPlaybackPlayers",new Class[]{long.class},new Object[]{decoder.getDuration()-350});
            set("isPlaying",true);call("startPlaybackPlayers");
        });
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),12000);
        main(()->call("playerPause"));
        assertEquals(group.playbackItems.get(1).media,field("video_url"));
        assertTrue((Boolean)field("isLocalAudioFile"));assertTrue((Boolean)field("isAudioOnlyMode"));
        assertNull(field("onlineTimer"));assertEquals(0,spy.networkCalls);
        File progress=new File(group.playbackItems.get(0).folder,".playback_progress");assertTrue(progress.isFile());
        try(BufferedReader in=new BufferedReader(new FileReader(progress))){assertTrue(Long.parseLong(in.readLine())>0);}
    }

    @Test public void actualSystemMediaNextAndPreviousSwitchOneCachedTrackAtATime()throws Exception {
        assertNotNull(field("mediaSession"));
        media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),12000);
        assertFalse((Boolean)field("isPlaying"));
        media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(()->((Integer)field("currentCachedIndex"))==2&&(Boolean)field("isPrepared"),12000);
        assertFalse((Boolean)field("isLocalAudioFile"));assertFalse((Boolean)field("isAudioOnlyMode"));
        media(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),12000);
        assertEquals(group.playbackItems.get(1).folder,field("localProgressFolderLocator"));
        assertEquals(0,spy.networkCalls);
    }

    @Test public void cachedAudioReturnsToItsOwnSavedPositionWithoutNetwork()throws Exception {
        SharedPreferencesUtil.putBoolean("player_from_last",true);
        main(()->call("skipPlaybackItem",new Class[]{boolean.class},new Object[]{true}));
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),12000);
        assertTrue((Boolean)field("isAudioOnlyMode"));
        main(()->call("seekToPosition",new Class[]{long.class},new Object[]{2000L}));
        await(()->((IMediaPlayer)field("ijkPlayer")).getCurrentPosition()>=1500,5000);
        long saved=((IMediaPlayer)field("ijkPlayer")).getCurrentPosition();
        main(()->call("skipPlaybackItem",new Class[]{boolean.class},new Object[]{true}));
        await(()->((Integer)field("currentCachedIndex"))==2&&(Boolean)field("isPrepared"),12000);
        main(()->call("skipPlaybackItem",new Class[]{boolean.class},new Object[]{false}));
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),12000);
        assertEquals(saved,(Long)field("progress_history"),350);
        await(()->Math.abs(((IMediaPlayer)field("ijkPlayer")).getCurrentPosition()-saved)<500,5000);
        assertTrue((Boolean)field("isAudioOnlyMode")); assertEquals(0,spy.networkCalls);
    }

    @Test public void longPressFlickSwitchesCachedVideoAndAudioOnceThenRestoresNormalSpeed()throws Exception {
        for(boolean scaling:new boolean[]{true,false}) {
            SharedPreferencesUtil.putBoolean("player_scale",scaling);main(()->call("setVideoGestures"));
            flickCached(true,1);
            assertTrue((Boolean)field("isLocalAudioFile"));
            flickCached(false,0);
            assertFalse((Boolean)field("isLocalAudioFile"));
        }
        assertEquals(0,spy.networkCalls);
    }

    private void flickCached(boolean next,int target)throws Exception {
        final android.view.View surface=(android.view.View)field("layout_control");
        await(()->surface.getWidth()>0&&surface.getHeight()>0&&!surface.isLayoutRequested(),5000);
        long[] down={0};float[] point=new float[2];
        main(()->{
            ((Runnable)field("hidecon")).run();call("cancelPendingSingleTap");call("setMediaPlaying",new Class[]{boolean.class},new Object[]{true});
            point[0]=surface.getWidth()*.65f;point[1]=surface.getHeight()*.5f;down[0]=SystemClock.uptimeMillis();
            touchSurface(surface,down[0],android.view.MotionEvent.ACTION_DOWN,point[0],point[1]);
        });
        await(()->(Boolean)field("onLongClick"),4000);
        main(()->touchSurface(surface,down[0],android.view.MotionEvent.ACTION_MOVE,point[0],point[1]+(next?1:-1)*surface.getHeight()*.45f));
        await(()->((Integer)field("currentCachedIndex"))==target&&(Boolean)field("isPrepared"),12000);
        main(()->{
            call("playerPause");assertFalse((Boolean)field("onLongClick"));
            assertEquals("x 1.0",((android.widget.TextView)field("text_speed")).getText().toString());
            touchSurface(surface,down[0],android.view.MotionEvent.ACTION_MOVE,point[0],point[1]+(next?1:-1)*surface.getHeight()*.47f);
            touchSurface(surface,down[0],android.view.MotionEvent.ACTION_UP,point[0],point[1]+(next?1:-1)*surface.getHeight()*.47f);
            assertEquals(target,((Integer)field("currentCachedIndex")).intValue());
        });
        assertEquals(0,spy.networkCalls);
    }
    private void touchSurface(android.view.View surface,long down,int action,float x,float y) {
        android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        surface.dispatchTouchEvent(event);event.recycle();
    }

    @Test public void repeatOneDoesNotBlockExplicitHeadphoneNextAndKeyRepeatsAreIgnored()throws Exception {
        main(()->{
            set("collectionPlaybackOrder",CollectionPlaybackQueue.REPEAT_ONE);
            long now=SystemClock.uptimeMillis();
            assertTrue(player.onKeyDown(KeyEvent.KEYCODE_MEDIA_NEXT,new KeyEvent(now,now,0,KeyEvent.KEYCODE_MEDIA_NEXT,2)));
        });
        SystemClock.sleep(200);assertEquals(0,field("currentCachedIndex"));
        media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),12000);
        assertEquals(CollectionPlaybackQueue.REPEAT_ONE,field("collectionPlaybackOrder"));
        assertEquals(0,spy.networkCalls);
    }

    @Test public void reverseOfflinePlaybackContinuesThroughAudioAndStopsAtFirstItem() throws Exception {
        media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(() -> Integer.valueOf(1).equals(field("currentCachedIndex")) && (Boolean) field("isPrepared"), 12000);
        media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(() -> Integer.valueOf(2).equals(field("currentCachedIndex")) && (Boolean) field("isPrepared"), 12000);
        main(() -> set("collectionPlaybackOrder", CollectionPlaybackQueue.REVERSE));
        for (int target : new int[]{1, 0}) {
            main(() -> {
                IMediaPlayer decoder = (IMediaPlayer) field("ijkPlayer");
                call("seekPlaybackPlayers", new Class[]{long.class}, new Object[]{decoder.getDuration() - 350});
                set("isPlaying", true); call("startPlaybackPlayers");
            });
            await(() -> Integer.valueOf(target).equals(field("currentCachedIndex")) && (Boolean) field("isPrepared"), 12000);
            main(() -> call("playerPause"));
        }
        main(() -> {
            IMediaPlayer decoder = (IMediaPlayer) field("ijkPlayer");
            call("seekPlaybackPlayers", new Class[]{long.class}, new Object[]{decoder.getDuration() - 350});
            set("isPlaying", true); call("startPlaybackPlayers");
        });
        await(() -> (Boolean) field("playbackCompleted") && !(Boolean) field("isPlaying"), 6000);
        assertEquals(0, field("currentCachedIndex")); assertEquals(0, spy.networkCalls);
    }

    @Test public void headphoneKeysWorkWhilePlayerIsLocked()throws Exception {
        main(()->call("setScreenLocked",new Class[]{boolean.class},new Object[]{true}));
        media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),12000);
        assertTrue((Boolean)field("screenLocked"));assertEquals(0,spy.networkCalls);
    }

    @Test public void audioListeningKeepsMediaControlWhenActivityGoesToBackground()throws Exception {
        main(()->{
            set("isAudioOnlyMode",true);set("cachedUserAudioOnlyMode",true);
            set("isPlaying",true);call("startPlaybackPlayers");
        });
        if(Build.VERSION.SDK_INT<21) {
            // Legacy headless emulators do not consistently implement accessibility Home.
            main(()->{player.onUserLeaveHint();assertTrue(player.moveTaskToBack(true));});
        } else assertTrue(inst.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME));
        await(()->!(Boolean)field("isActivityVisible"),5000);
        assertTrue("Home should retain listening playback",(Boolean)field("backgroundPlaybackActive"));
        media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),12000);
        assertTrue((Boolean)field("isPlaying"));assertEquals(0,spy.networkCalls);
    }

    @Test public void missingFilesAreSkippedWhenTheOfflineSnapshotIsOpened()throws Exception {
        assertTrue(new File(group.playbackItems.get(1).media).delete());
        CachedPlaybackSnapshot snapshot=CachedPlaybackSnapshot.read(context,token);
        assertEquals(2,snapshot.items.size());
        assertEquals(group.playbackItems.get(2).media,snapshot.items.get(1).media);
        assertEquals(0,spy.networkCalls);
    }

    @Test public void forcedCachedAudioListeningContinuesWhenTheNextCachedTrackIsVideo()throws Exception {
        media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),12000);
        assertFalse((Boolean)field("cachedUserAudioOnlyMode"));
        main(()->call("setMediaPlaying",new Class[]{boolean.class},new Object[]{true}));
        if(Build.VERSION.SDK_INT<21)main(()->{player.onUserLeaveHint();assertTrue(player.moveTaskToBack(true));});
        else assertTrue(inst.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME));
        await(()->!(Boolean)field("isActivityVisible"),5000);
        media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(()->((Integer)field("currentCachedIndex"))==2&&(Boolean)field("isPrepared"),12000);
        assertFalse((Boolean)field("isLocalAudioFile"));assertTrue((Boolean)field("isAudioOnlyMode"));
        assertTrue((Boolean)field("isPlaying"));assertEquals(0,spy.networkCalls);
    }

    @Test public void localMenuHasNoOnlineQualityAndTurningItOffStopsQueueNavigation()throws Exception {
        main(()->{
            player.findViewById(R.id.button_quality).performClick();
            com.RobinNotBad.BiliClient.player.PlayerOptionsMenu menu=(com.RobinNotBad.BiliClient.player.PlayerOptionsMenu)field("playbackOptionsMenu");
            assertEquals(1,menu.getMenu().size());assertNull(menu.getMenu().findItem(1));
            assertTrue(menu.getMenu().performIdentifierAction(2,0));menu.dismiss();
            assertNull(field("mediaSession"));
            long t=SystemClock.uptimeMillis();
            player.onKeyDown(KeyEvent.KEYCODE_MEDIA_NEXT,new KeyEvent(t,t,0,KeyEvent.KEYCODE_MEDIA_NEXT,0));
            player.onKeyUp(KeyEvent.KEYCODE_MEDIA_NEXT,new KeyEvent(t,t+30,1,KeyEvent.KEYCODE_MEDIA_NEXT,0));
        });
        SystemClock.sleep(200);assertEquals(0,field("currentCachedIndex"));assertEquals(0,spy.networkCalls);
    }

    @Test public void downloadMetadataRetainsCollectionAndPartOrder()throws Exception {
        long testAid=Long.MAX_VALUE-10;
        VideoInfo info=new VideoInfo();info.aid=testAid;info.cids=new ArrayList<>(Arrays.asList(101L,102L));
        info.collection=new com.RobinNotBad.BiliClient.model.Collection();info.collection.id=81;
        com.RobinNotBad.BiliClient.model.Collection.Section section=new com.RobinNotBad.BiliClient.model.Collection.Section();
        for(long aid:new long[]{testAid-1,testAid}) {
            com.RobinNotBad.BiliClient.model.Collection.Episode ep=new com.RobinNotBad.BiliClient.model.Collection.Episode();ep.aid=aid;section.episodes.add(ep);
        }
        info.collection.sections.add(section);
        File metadata=new File(new File(context.getFilesDir(),"download-identities"),testAid+".json");
        try {
            CachedCollectionMetadata.remember(info);
            DownloadSection task=new DownloadSection();task.aid=testAid;task.cid=102;
            VideoStorageUtil.Node folder=VideoStorageUtil.root(context,VideoStorageUtil.MODE_FILE,group.playbackItems.get(1).folder);
            CachedCollectionMetadata.writeCompleted(context,task,folder);
            CachedPlaybackItem item=new CachedPlaybackItem();CachedCollectionMetadata.readInto(folder,item);
            assertEquals(testAid,item.aid);assertEquals(102,item.cid);assertEquals(81,item.collectionId);
            assertEquals(1,item.collectionIndex);assertEquals(1,item.partIndex);
        } finally {metadata.delete();}
    }

    @Test public void twoChoiceScopeSettingBindsAndRecyclesWithoutSelectingAnInvisibleThirdOption() {
        main(()->{
            View row=android.view.LayoutInflater.from(new android.view.ContextThemeWrapper(player,R.style.Theme_BiliClient))
                    .inflate(R.layout.cell_setting_choose3,null,false);
            com.RobinNotBad.BiliClient.adapter.SettingsAdapter.ThreeChooseHolder holder=new com.RobinNotBad.BiliClient.adapter.SettingsAdapter.ThreeChooseHolder(row);
            SettingSection setting=new SettingSection("choose3","缓存范围",SharedPreferencesUtil.PLAYER_CACHED_PLAYBACK_SCOPE,"","0",new String[]{"当前合集","全部缓存"});
            holder.bind(setting);assertEquals(View.GONE,row.findViewById(R.id.setting_choose3_third).getVisibility());
            row.findViewById(R.id.setting_choose3_second).performClick();
            assertEquals(1,SharedPreferencesUtil.getInt(SharedPreferencesUtil.PLAYER_CACHED_PLAYBACK_SCOPE,-1));
            holder.bind(new SettingSection("choose3","三选项",SharedPreferencesUtil.PLAYER_CACHED_PLAYBACK_SCOPE,"","0",new String[]{"一","二","三"}));
            assertEquals(View.VISIBLE,row.findViewById(R.id.setting_choose3_third).getVisibility());
        });
    }

    @Test public void safCachedPlaybackSwitchesDocumentUrisAndKeepsTheirProgressFolders()throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT>=21);
        String tree=SharedPreferencesUtil.getString(VideoStorageUtil.PREF_TREE_URI,"");
        assertFalse("Select a SAF test tree before running this test",tree.isEmpty());
        VideoStorageUtil.Node root=VideoStorageUtil.root(context,VideoStorageUtil.MODE_SAF,tree);
        safFixture=root.getOrCreateDirectory(".cached-headset-"+UUID.randomUUID());
        LocalVideo safGroup=new LocalVideo();safGroup.title="SAF离线歌单";
        for(int i=0;i<2;i++) {
            VideoStorageUtil.Node folder=safFixture.getOrCreateDirectory("track-"+i);
            VideoStorageUtil.Node media=folder.getOrCreateFile("video.mp4","video/mp4");
            try(InputStream in=inst.getContext().getAssets().open("collection-playback.mp4");OutputStream out=media.openOutput(false)){copy(in,out);}
            CachedPlaybackItem item=new CachedPlaybackItem();item.title="SAF "+i;item.media=media.getUriString();item.folder=folder.getLocator();safGroup.playbackItems.add(item);
        }
        main(()->player.finish());inst.waitForIdleSync();player=null;
        launch(safGroup,safGroup.playbackItems.get(0));media(KeyEvent.KEYCODE_MEDIA_NEXT);
        await(()->((Integer)field("currentCachedIndex"))==1&&(Boolean)field("isPrepared"),15000);
        assertTrue(((String)field("video_url")).startsWith("content://"));
        assertEquals(safGroup.playbackItems.get(1).folder,field("localProgressFolderLocator"));
        assertEquals(0,spy.networkCalls);
    }

    private void media(int key)throws Exception {
        if(Build.VERSION.SDK_INT>=21)Modern.send(context,field("mediaSession"),key);
        else {
            AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            long t=SystemClock.uptimeMillis();audio.dispatchMediaKeyEvent(new KeyEvent(t,t,0,key,0));
            audio.dispatchMediaKeyEvent(new KeyEvent(t,t+30,1,key,0));
        }
    }
    @android.annotation.TargetApi(21) private static final class Modern {
        static void send(Context context,Object wrapper,int key)throws Exception {
            Field field=wrapper.getClass().getDeclaredField("mediaSession");field.setAccessible(true);
            android.media.session.MediaSession session=(android.media.session.MediaSession)field.get(wrapper);
            android.media.session.MediaController control=new android.media.session.MediaController(context,session.getSessionToken());
            long t=SystemClock.uptimeMillis();assertTrue(control.dispatchMediaButtonEvent(new KeyEvent(t,t,0,key,0)));
            assertTrue(control.dispatchMediaButtonEvent(new KeyEvent(t,t+30,1,key,0)));
        }
    }
    private interface Check{boolean get();}
    private void await(Check check,long timeout)throws Exception {
        long until=SystemClock.uptimeMillis()+timeout;boolean[] okay={false};
        do {main(()->okay[0]=check.get());if(okay[0])return;SystemClock.sleep(60);}while(SystemClock.uptimeMillis()<until);
        fail("Timed out: local="+field("currentCachedIndex")+", ready="+field("isPrepared")+", playing="+field("isPlaying")+", network="+spy.networkCalls);
    }
    private void pref(String key,boolean value){old.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));SharedPreferencesUtil.putBoolean(key,value);}
    private Object field(String name){try{Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(player);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object value){try{Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(player,value);}catch(Exception e){throw new AssertionError(e);}}
    private void call(String name){call(name,new Class[0],new Object[0]);}
    private void call(String name,Class[] types,Object[] values){try{Method m=PlayerActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(player,values);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable r){inst.runOnMainSync(r);}
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
    private static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
}
