package com.RobinNotBad.BiliClient.activity.video.info;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.activity.settings.UIPreviewActivity;
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FavoritePagerLifecycleInstrumentedTest {
    /** Uses the production favorite launcher, while avoiding account/network operations. */
    public static class FavoriteProbeFragment extends VideoInfoFragment {
        Button favorite;
        Throwable launchError;
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
            LinearLayout root=new LinearLayout(requireContext());root.setOrientation(LinearLayout.VERTICAL);
            favorite=new Button(requireContext());favorite.setText("收藏");
            favorite.setOnClickListener(v->{
                try {favLauncher.launch(new Intent(requireContext(),AddFavoriteActivity.class).putExtra("aid",1L));}
                catch(Throwable error){launchError=error;}
            });
            root.addView(favorite);
            for(String name:new String[]{"like","coin","fav"}) {
                ImageView image=new ImageView(requireContext());root.addView(image);set(this,name,image);
            }
            return root;
        }
        @Override public void onViewCreated(@NonNull View view,Bundle state) { }
    }

    @Test public void favoriteLauncherSurvivesOffscreenPagerRoundTrip() {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        UIPreviewActivity host=(UIPreviewActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),UIPreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Instrumentation.ActivityMonitor monitor=inst.addMonitor(AddFavoriteActivity.class.getName(),
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
        FavoriteProbeFragment details=new FavoriteProbeFragment();
        Bundle args=new Bundle();args.putLong("aid",1L);details.setArguments(args);
        ViewPager[] pager={null};
        try {
            inst.runOnMainSync(()->{
                pager[0]=new ViewPager(host);pager[0].setId(0x2345678);host.setContentView(pager[0]);
                pager[0].setOffscreenPageLimit(1);
                pager[0].setAdapter(new ViewPagerFragmentAdapter(host.getSupportFragmentManager(),
                        Arrays.asList(details,new Fragment(),new Fragment())));
            });
            awaitAttached(inst,details,pager[0]);
            inst.runOnMainSync(()->{assertNotNull(details.favorite);details.favorite.performClick();});
            inst.waitForIdleSync();
            assertNull("Initial favorite launch",details.launchError);assertEquals(1,monitor.getHits());
            for(int i=0;i<3;i++) {
                inst.runOnMainSync(()->pager[0].setCurrentItem(2,false));inst.waitForIdleSync();
                inst.runOnMainSync(()->pager[0].setCurrentItem(0,false));inst.waitForIdleSync();
                inst.runOnMainSync(()->details.favorite.performClick());inst.waitForIdleSync();
                assertNull("Favorite launcher after details -> recommendations -> details: "+details.launchError,details.launchError);
            }
            assertEquals(4,monitor.getHits());
        } finally {
            inst.removeMonitor(monitor);inst.runOnMainSync(host::finish);inst.waitForIdleSync();
        }
    }

    @Test public void pendingTripleIsCancelledWhenDetailsViewIsDetached()throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        UIPreviewActivity host=(UIPreviewActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),UIPreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        FavoriteProbeFragment details=new FavoriteProbeFragment();Bundle args=new Bundle();args.putLong("aid",1);details.setArguments(args);
        ViewPager[] pager={null};AtomicInteger calls=new AtomicInteger();
        try {
            inst.runOnMainSync(()->{
                pager[0]=new ViewPager(host);pager[0].setId(0x2345679);host.setContentView(pager[0]);
                pager[0].setOffscreenPageLimit(1);
                pager[0].setAdapter(new ViewPagerFragmentAdapter(host.getSupportFragmentManager(),Arrays.asList(details,new Fragment(),new Fragment())));
            });
            awaitAttached(inst,details,pager[0]);
            inst.runOnMainSync(()->{
                Runnable action=calls::incrementAndGet;
                try {
                    java.lang.reflect.Method schedule=VideoInfoFragment.class.getDeclaredMethod("scheduleTripleAction",Runnable.class);
                    schedule.setAccessible(true);schedule.invoke(details,action);
                } catch(Exception error){throw new AssertionError(error);}
                pager[0].setCurrentItem(2,false);
            });
            inst.waitForIdleSync();
            inst.runOnMainSync(()->assertNull("Offscreen details view must be detached",details.getView()));
            SystemClock.sleep(2300);
            assertEquals("Leaving details must cancel the pending triple request",0,calls.get());
            assertNull(get(details,"tripleActionRunnable"));assertEquals(false,get(details,"isTripleInProgress"));
            inst.runOnMainSync(()->pager[0].setCurrentItem(0,false));inst.waitForIdleSync();
            assertNotNull(details.getView());
        }finally{inst.runOnMainSync(host::finish);inst.waitForIdleSync();}
    }

    private static Object get(VideoInfoFragment owner,String name) {
        try{Field f=VideoInfoFragment.class.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
        catch(Exception error){throw new AssertionError(error);}
    }
    private static void awaitAttached(Instrumentation inst,Fragment details,ViewPager pager) {
        long deadline=SystemClock.uptimeMillis()+5000;AtomicBoolean ready=new AtomicBoolean();
        while(SystemClock.uptimeMillis()<deadline) {
            inst.runOnMainSync(()->ready.set(details.getView()!=null&&details.getView().getWindowToken()!=null
                    &&details.getView().getWidth()>0&&pager.getWidth()>0));
            if(ready.get()){inst.waitForIdleSync();return;}SystemClock.sleep(30);
        }
        fail("Pager did not finish its first layout");
    }
    private static void set(VideoInfoFragment owner,String name,Object value) {
        try{Field f=VideoInfoFragment.class.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
        catch(Exception error){throw new AssertionError(error);}
    }
}
