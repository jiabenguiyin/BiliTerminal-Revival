package com.RobinNotBad.BiliClient.activity.video;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.Stats;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Task commits are captured locally; no real account, database, or download service is modified. */
@RunWith(AndroidJUnit4.class)
public class AudioDownloadChooserInstrumentedTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final FakeSource source=new FakeSource();
    private QualityChooserActivity activity;
    private VideoInfo info;
    private Application application;
    private Application.ActivityLifecycleCallbacks callback;

    private static class FakeSource extends QualityChooserActivity.DownloadSource {
        volatile int[] qualities=new int[0];
        volatile boolean videoFails,missingAudio;
        volatile CountDownLatch release=new CountDownLatch(0);
        final CountDownLatch entered=new CountDownLatch(1),returned=new CountDownLatch(1);
        final AtomicInteger audioCalls=new AtomicInteger(),queuedAudio=new AtomicInteger(),queuedVideo=new AtomicInteger(),audioQn=new AtomicInteger(-1),videoQn=new AtomicInteger(-1);
        @Override public void loadVideo(PlayerData data)throws Exception {
            if(videoFails)throw new IOException("local fixture failure");
            data.qnValueList=qualities.clone();
        }
        @Override public void loadAudio(PlayerData data)throws Exception {
            audioCalls.incrementAndGet();audioQn.set(data.qn);entered.countDown();
            if(!release.await(5,TimeUnit.SECONDS))throw new IOException("fixture gate timed out");
            data.audioUrl=missingAudio?"":"https://example.invalid/fresh-audio.m4a";returned.countDown();
        }
        @Override public void enqueueAudio(VideoInfo video,int page,String url) {
            assertEquals("https://example.invalid/fresh-audio.m4a",url);assertEquals(0,page);queuedAudio.incrementAndGet();
        }
        @Override public void enqueueVideo(VideoInfo video,int page,int qn) {videoQn.set(qn);queuedVideo.incrementAndGet();}
    }

    @Before public void setup()throws Exception {
        info=new VideoInfo();info.aid=Long.MAX_VALUE-105;info.title="音频缓存操作测试";info.stats=new Stats();
        info.cids.add(105L);info.pagenames.add(info.title);TerminalContext.getInstance().putContent(info);
        application=(Application)inst.getTargetContext().getApplicationContext();
        callback=new Application.ActivityLifecycleCallbacks(){
            @Override public void onActivityCreated(Activity owner,Bundle state) {
                if(owner instanceof QualityChooserActivity) {
                    try{Field f=QualityChooserActivity.class.getDeclaredField("downloadSource");f.setAccessible(true);f.set(owner,source);}
                    catch(Exception error){throw new AssertionError(error);}
                }
            }
            @Override public void onActivityStarted(Activity a){}
            @Override public void onActivityResumed(Activity a){}
            @Override public void onActivityPaused(Activity a){}
            @Override public void onActivityStopped(Activity a){}
            @Override public void onActivitySaveInstanceState(Activity a,Bundle state){}
            @Override public void onActivityDestroyed(Activity a){}
        };
        application.registerActivityLifecycleCallbacks(callback);
    }

    @After public void cleanup()throws Exception {
        source.release.countDown();
        if(activity!=null)finish();
        application.unregisterActivityLifecycleCallbacks(callback);
        Field f=TerminalContext.class.getDeclaredField("contentLruCache");f.setAccessible(true);Object cache=f.get(TerminalContext.getInstance());
        cache.getClass().getMethod("remove",Object.class).invoke(cache,TerminalContext.getInstance().getTerminalKey(info));
    }

    private <T>T main(Callable<T> work) {
        AtomicReference<T> result=new AtomicReference<>();inst.runOnMainSync(()->{
            try{result.set(work.call());}catch(Exception error){throw new AssertionError(error);}
        });return result.get();
    }
    private void await(Callable<Boolean> condition) {
        long until=SystemClock.uptimeMillis()+8000;
        do{if(main(condition))return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<until);
        fail("Chooser condition timed out");
    }
    private void launch(int rows) {
        activity=(QualityChooserActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),QualityChooserActivity.class)
                .putExtra("aid",info.aid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->{RecyclerView view=activity.findViewById(R.id.recyclerView);return view.getAdapter()!=null&&view.getAdapter().getItemCount()==rows;});
    }
    private void click(int row) {
        await(()->((RecyclerView)activity.findViewById(R.id.recyclerView)).findViewHolderForAdapterPosition(row)!=null);
        main(()->{((RecyclerView)activity.findViewById(R.id.recyclerView)).findViewHolderForAdapterPosition(row).itemView.performClick();return null;});
    }
    private void finish() {
        main(()->{activity.finish();return null;});
        await(()->ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity)==Stage.DESTROYED);
    }

    @Test public void emptyQualityListCanQueueAudioWithoutReadingItsFirstElement() {
        launch(1);assertEquals("仅音频",main(()->((QualityChooseAdapter)((RecyclerView)activity.findViewById(R.id.recyclerView)).getAdapter()).getName(0)));
        click(0);await(()->source.queuedAudio.get()==1);
        assertEquals(0,source.audioQn.get());assertEquals(0,source.queuedVideo.get());
    }
    @Test public void realTwoFortyPQualityIsVisibleAndQueuesTheCorrectVideoTier() {
        source.qualities=new int[]{6};launch(2);
        assertEquals("240P",main(()->((QualityChooseAdapter)((RecyclerView)activity.findViewById(R.id.recyclerView)).getAdapter()).getName(0)));
        click(0);await(()->source.queuedVideo.get()==1);assertEquals(6,source.videoQn.get());
    }
    @Test public void ordinaryVideoRowsKeepTheirQualityMapping() {
        source.qualities=new int[]{80,32};launch(3);click(1);
        await(()->source.queuedVideo.get()==1);assertEquals(32,source.videoQn.get());assertEquals(0,source.audioCalls.get());
    }
    @Test public void audioRowWithVideoChoicesStillDoesNotRequireAVideoTier() {
        source.qualities=new int[]{80,32};launch(3);click(2);await(()->source.queuedAudio.get()==1);
        assertEquals(0,source.audioQn.get());
    }
    @Test public void videoMetadataFailureDoesNotBlockAnIndependentAudioRequest() {
        source.videoFails=true;launch(1);click(0);await(()->source.queuedAudio.get()==1);
    }
    @Test public void missingAudioDoesNotQueueAnythingAndCanBeRetried() {
        source.missingAudio=true;launch(1);click(0);
        await(()->{Field f=QualityChooserActivity.class.getDeclaredField("downloadRequestPending");f.setAccessible(true);return source.audioCalls.get()==1&&!f.getBoolean(activity);});
        assertEquals(0,source.queuedAudio.get());assertFalse(activity.isFinishing());
        source.missingAudio=false;click(0);await(()->source.queuedAudio.get()==1);assertEquals(2,source.audioCalls.get());
    }
    @Test public void leavingDuringAudioLookupDoesNotCreateADownloadLater()throws Exception {
        source.release=new CountDownLatch(1);launch(1);click(0);assertTrue(source.entered.await(5,TimeUnit.SECONDS));
        finish();source.release.countDown();assertTrue(source.returned.await(5,TimeUnit.SECONDS));inst.waitForIdleSync();SystemClock.sleep(100);
        assertEquals(0,source.queuedAudio.get());
    }
    @Test public void repeatedTapsDuringLookupOnlyQueueOneTask()throws Exception {
        source.release=new CountDownLatch(1);launch(1);click(0);assertTrue(source.entered.await(5,TimeUnit.SECONDS));click(0);
        assertEquals(1,source.audioCalls.get());source.release.countDown();await(()->source.queuedAudio.get()==1);assertEquals(1,source.audioCalls.get());
    }
}
