package com.RobinNotBad.BiliClient.activity;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.*;
import com.RobinNotBad.BiliClient.activity.video.local.*;
import com.RobinNotBad.BiliClient.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Injects actual touch events: performClick() would miss the overlaid-empty-view regression. */
@RunWith(AndroidJUnit4.class)
public class CacheNavigationInstrumentedTest {
    private final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    private final Context context=ui.getTargetContext();
    private final ArrayList<Activity> owners=new ArrayList<>();
    private final Map<String,Object> saved=new HashMap<>();
    private File directory;
    private LocalListActivity cache;
    private static final String[] KEYS={"mid","cookies","save_path_video",VideoStorageUtil.PREF_MODE,VideoStorageUtil.PREF_TREE_URI,
            "dpi","density","paddingH_percent","paddingV_percent","player_ui_round","ui_landscape"};

    @Before public void setup()throws Exception{
        SharedPreferences prefs=SharedPreferencesUtil.getSharedPreferences();for(String key:KEYS)saved.put(key,prefs.getAll().get(key));
        directory=new File(context.getFilesDir(),"cache-navigation-"+System.nanoTime());assertTrue(directory.mkdir());
        prefs.edit().putLong("mid",0).putString("cookies","").putString("save_path_video",directory.getAbsolutePath())
                .putString(VideoStorageUtil.PREF_MODE,VideoStorageUtil.MODE_FILE).remove(VideoStorageUtil.PREF_TREE_URI)
                .putFloat("dpi",1f).putInt("density",-1).putInt("paddingH_percent",0).putInt("paddingV_percent",0)
                .putBoolean("player_ui_round",false).putBoolean("ui_landscape",false).commit();
    }
    @After public void cleanup()throws Exception{
        ui.runOnMainSync(()->{for(Activity a:owners)if(!a.isFinishing())a.finish();});ui.waitForIdleSync();
        remove(directory);
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(String key:KEYS){Object v=saved.get(key);if(v==null)edit.remove(key);else if(v instanceof String)edit.putString(key,(String)v);
            else if(v instanceof Integer)edit.putInt(key,(Integer)v);else if(v instanceof Long)edit.putLong(key,(Long)v);
            else if(v instanceof Float)edit.putFloat(key,(Float)v);else edit.putBoolean(key,(Boolean)v);}edit.commit();
    }
    private void remove(File file){File[] rows=file.listFiles();if(rows!=null)for(File child:rows)remove(child);assertTrue(file.delete());}
    private <T extends Activity>T launch(Class<T> type)throws Exception{
        Instrumentation.ActivityMonitor monitor=ui.addMonitor(type.getName(),null,false);
        try{ui.runOnMainSync(()->context.startActivity(new Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            T a=type.cast(monitor.waitForActivityWithTimeout(7000));assertNotNull(a);owners.add(a);return a;
        }finally{ui.removeMonitor(monitor);}
    }
    private void openCache()throws Exception{
        cache=launch(LocalListActivity.class);
        await(()->((androidx.recyclerview.widget.RecyclerView)cache.findViewById(R.id.recyclerView)).getAdapter()!=null
                &&!((androidx.swiperefreshlayout.widget.SwipeRefreshLayout)cache.findViewById(R.id.swipeRefreshLayout)).isRefreshing());
    }
    private void tap(View view){
        long focusDeadline=SystemClock.uptimeMillis()+7000;
        while(!view.hasWindowFocus()&&SystemClock.uptimeMillis()<focusDeadline){
            dismissDebugCompatibilityWarning();
            SystemClock.sleep(30);
        }
        assertTrue("Touch target window did not receive focus",view.hasWindowFocus());
        float[] point=new float[2];ui.runOnMainSync(()->{
            Rect rect=new Rect();assertTrue(view.getLocalVisibleRect(rect));assertTrue(rect.width()>0&&rect.height()>0);
            int[] origin=new int[2];view.getLocationOnScreen(origin);
            point[0]=origin[0]+rect.centerX();point[1]=origin[1]+rect.centerY();
        });
        if(android.os.Build.VERSION.SDK_INT>=18){
            android.accessibilityservice.AccessibilityServiceInfo info=ui.getUiAutomation().getServiceInfo();
            info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;ui.getUiAutomation().setServiceInfo(info);
            if(android.os.Build.VERSION.SDK_INT>=21){info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;ui.getUiAutomation().setServiceInfo(info);}
            String resource=view.getResources().getResourceName(view.getId());Rect screen=null;
            long until=SystemClock.uptimeMillis()+3000;
            do{
                android.view.accessibility.AccessibilityNodeInfo root=focusedAccessibilityRoot();
                if(root!=null){
                    java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(resource);
                    for(android.view.accessibility.AccessibilityNodeInfo node:nodes){if(node.isVisibleToUser()){Rect candidate=new Rect();node.getBoundsInScreen(candidate);if(!candidate.isEmpty())screen=candidate;}node.recycle();}
                    root.recycle();
                }
                if(screen!=null)break;SystemClock.sleep(40);
            }while(SystemClock.uptimeMillis()<until);
            if(screen==null){
                capture(cache,"cache-accessibility-failure.png");
                android.view.accessibility.AccessibilityNodeInfo node=focusedAccessibilityRoot();
                if(node!=null){dumpAccessibility(node,0);node.recycle();}
            }
            assertNotNull("No visible screen-space target for "+resource,screen);
            android.util.Log.i("CacheNavigationTest",resource+" view="+point[0]+","+point[1]+" screen="+screen);
            point[0]=screen.centerX();point[1]=screen.centerY();
        }
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0);
        MotionEvent up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,point[0],point[1],0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{
            if(android.os.Build.VERSION.SDK_INT>=18){assertTrue(ui.getUiAutomation().injectInputEvent(down,true));assertTrue(ui.getUiAutomation().injectInputEvent(up,true));}
            else{ui.sendPointerSync(down);ui.sendPointerSync(up);}
        }finally{down.recycle();up.recycle();}
        ui.waitForIdleSync();
    }
    private android.view.accessibility.AccessibilityNodeInfo focusedAccessibilityRoot(){
        if(android.os.Build.VERSION.SDK_INT>=21){
            android.view.accessibility.AccessibilityNodeInfo focused=null;
            for(android.view.accessibility.AccessibilityWindowInfo window:ui.getUiAutomation().getWindows()){
                if(focused==null&&window.isFocused())focused=window.getRoot();
                window.recycle();
            }
            if(focused!=null)return focused;
        }
        return ui.getUiAutomation().getRootInActiveWindow();
    }
    private void dumpAccessibility(android.view.accessibility.AccessibilityNodeInfo node,int depth){
        if(depth>10)return;
        Rect bounds=new Rect();node.getBoundsInScreen(bounds);
        android.util.Log.i("CacheNavigationTest","NODE "+node.getViewIdResourceName()+" visible="+node.isVisibleToUser()+" bounds="+bounds+" text="+node.getText());
        for(int i=0;i<node.getChildCount();i++){
            android.view.accessibility.AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null){dumpAccessibility(child,depth+1);child.recycle();}
        }
    }
    private void dismissDebugCompatibilityWarning(){
        if(!com.RobinNotBad.BiliClient.BuildConfig.DEBUG||android.os.Build.VERSION.SDK_INT<28)return;
        // Accessibility requests must run off the app's UI thread to avoid blocking its response.
        android.accessibilityservice.AccessibilityServiceInfo info=ui.getUiAutomation().getServiceInfo();
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        ui.getUiAutomation().setServiceInfo(info);
        android.view.accessibility.AccessibilityNodeInfo root=focusedAccessibilityRoot();
        if(root==null)return;
        try{
            boolean warning=false;
            for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByViewId("android:id/message")){
                warning|=node.getText()!=null&&node.getText().toString().startsWith("Detected problems with API compatibility");
                node.recycle();
            }
            if(warning)for(android.view.accessibility.AccessibilityNodeInfo button:root.findAccessibilityNodeInfosByViewId("android:id/button1")){
                button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);button.recycle();
                android.util.Log.i("CacheNavigationTest","Dismissed Android debug-only API compatibility warning");
            }
        }finally{root.recycle();}
    }
    private void await(Callable<Boolean> condition){long end=SystemClock.uptimeMillis()+7000;AtomicBoolean ready=new AtomicBoolean();
        do{ui.runOnMainSync(()->{try{ready.set(condition.call());}catch(Exception e){throw new AssertionError(e);}});if(ready.get())return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<end);fail("Timed out waiting for cache navigation");}
    private void capture(Activity owner,String name){ui.waitForIdleSync();ui.runOnMainSync(()->{View root=owner.getWindow().getDecorView();Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name))){root.draw(new Canvas(b));b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}});}

    @Test public void emptyCacheTitleReceivesRealTouchInsteadOfOpeningDownloads()throws Exception{
        openCache();await(()->cache.findViewById(R.id.emptyTip).getVisibility()==View.VISIBLE);
        Instrumentation.ActivityMonitor menu=ui.addMonitor(MenuActivity.class.getName(),new Instrumentation.ActivityResult(0,null),true);
        Instrumentation.ActivityMonitor downloads=ui.addMonitor(DownloadListActivity.class.getName(),new Instrumentation.ActivityResult(0,null),true);
        try{tap(cache.findViewById(R.id.top));await(()->menu.getHits()+downloads.getHits()>0);assertEquals("empty overlay must leave header reachable",1,menu.getHits());assertEquals(0,downloads.getHits());}
        finally{ui.removeMonitor(menu);ui.removeMonitor(downloads);}
        capture(cache,"cache-empty-navigation.png");
    }
    @Test public void emptyHintOpensDownloadsAndBackReturnsToReachableMenu()throws Exception{
        SharedPreferencesUtil.putBoolean("player_ui_round",true);SharedPreferencesUtil.putFloat("dpi",1.5f);
        openCache();await(()->cache.findViewById(R.id.emptyTip).getVisibility()==View.VISIBLE);
        Instrumentation.ActivityMonitor downloads=ui.addMonitor(DownloadListActivity.class.getName(),null,false);
        DownloadListActivity target;
        try{tap(cache.findViewById(R.id.emptyTip));target=(DownloadListActivity)downloads.waitForActivityWithTimeout(5000);assertNotNull(target);owners.add(target);}
        finally{ui.removeMonitor(downloads);}
        ui.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(target::isFinishing);
        Instrumentation.ActivityMonitor menu=ui.addMonitor(MenuActivity.class.getName(),null,false);
        try{tap(cache.findViewById(R.id.top));Activity opened=menu.waitForActivityWithTimeout(5000);assertNotNull(opened);owners.add(opened);}
        finally{ui.removeMonitor(menu);}
    }
    @Test public void allSharedEmptyLayoutsKeepHeaderAndBodyTouchesSeparate()throws Exception{
        TestActivity host=launch(TestActivity.class);
        for(int layout:new int[]{R.layout.activity_simple_main_refresh,R.layout.activity_simple_refresh,R.layout.activity_simple_main_list}){
            int[] hits={0,0};View[] root={null};
            ui.runOnMainSync(()->{root[0]=host.getLayoutInflater().inflate(layout,null);host.setContentView(root[0]);
                View top=root[0].findViewById(R.id.top),empty=root[0].findViewById(R.id.emptyTip);
                top.setOnClickListener(v->hits[0]++);empty.setVisibility(View.VISIBLE);empty.setOnClickListener(v->hits[1]++);});
            ui.waitForIdleSync();tap(root[0].findViewById(R.id.top));assertEquals(1,hits[0]);assertEquals(0,hits[1]);
            tap(root[0].findViewById(R.id.emptyTip));assertEquals(1,hits[0]);assertEquals(1,hits[1]);
        }
    }
    @Test public void nonEmptyCacheStillShowsVideoAndLeavesHeaderReachable()throws Exception{
        File video=new File(directory,"缓存测试视频");assertTrue(video.mkdir());try(FileOutputStream out=new FileOutputStream(new File(video,"video.mp4"))){out.write(1);}
        openCache();ui.runOnMainSync(()->{assertEquals(View.GONE,cache.findViewById(R.id.emptyTip).getVisibility());
            assertEquals(2,((androidx.recyclerview.widget.RecyclerView)cache.findViewById(R.id.recyclerView)).getAdapter().getItemCount());});
        Instrumentation.ActivityMonitor menu=ui.addMonitor(MenuActivity.class.getName(),new Instrumentation.ActivityResult(0,null),true);
        try{tap(cache.findViewById(R.id.top));await(()->menu.getHits()==1);}finally{ui.removeMonitor(menu);}
        capture(cache,"cache-nonempty-navigation.png");
    }
    @Test public void storageEntryHasVisibleIconAndStillOpensStorage()throws Exception{
        SettingMainActivity settings=launch(SettingMainActivity.class);await(()->settings.findViewById(R.id.storageSetting)!=null);
        int iconId=context.getResources().getIdentifier("storageSetting_icon","id",context.getPackageName());assertTrue("storage entry icon missing",iconId!=0);
        View card=settings.findViewById(R.id.storageSetting);
        await(()->card.getWidth()>0&&card.getHeight()>0);
        ui.runOnMainSync(()->{ImageView icon=settings.findViewById(iconId);assertNotNull(icon.getDrawable());
            TextView title=settings.findViewById(context.getResources().getIdentifier("storageSetting_title","id",context.getPackageName()));
            View description=settings.findViewById(context.getResources().getIdentifier("storageSetting_description","id",context.getPackageName()));
            assertTrue("title and description must not overlap",title.getBottom()<=description.getTop());
            ((ScrollView)settings.findViewById(R.id.scrollView)).scrollTo(0,card.getTop());});
        ui.waitForIdleSync();SystemClock.sleep(120);capture(settings,"settings-storage-icon.png");
        Instrumentation.ActivityMonitor storage=ui.addMonitor(SettingStorageActivity.class.getName(),null,false);
        try{tap(card);Activity opened=storage.waitForActivityWithTimeout(5000);assertNotNull(opened);owners.add(opened);}
        finally{ui.removeMonitor(storage);}
    }
}
