package com.RobinNotBad.BiliClient.util;

import android.app.Instrumentation;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.SettingMainActivity;
import com.RobinNotBad.BiliClient.activity.settings.SettingStorageActivity;
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StorageCleanupInstrumentedTest {
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    final Context context=ui.getTargetContext();final Map<String,Object> saved=new HashMap<>();
    final ArrayList<File> fixtures=new ArrayList<>();String id,database;File root;
    SettingStorageActivity page;SettingMainActivity main;
    @Before public void setup()throws Exception {
        SharedPreferences prefs=context.getSharedPreferences("default",0);SharedPreferencesUtil.sharedPreferences=prefs;
        for(String key:new String[]{"mid","cookies","dpi","density","player_ui_round","terminal_update_pkg","save_path_video"})saved.put(key,prefs.getAll().get(key));
        prefs.edit().putLong("mid",0).putString("cookies","storage-fixture-cookie").putFloat("dpi",1).putInt("density",-1)
                .putBoolean("player_ui_round",false).remove("terminal_update_pkg").remove("save_path_video").commit();
        id=String.valueOf(System.nanoTime());root=new File(context.getCacheDir(),"storage-native-"+id);
        assertTrue(root.mkdirs());fixtures.add(root);database="storage-fixture-"+id+".db";
        SQLiteDatabase db=context.openOrCreateDatabase(database,0,null);db.execSQL("CREATE TABLE kept(value TEXT)");db.execSQL("INSERT INTO kept VALUES ('history')");db.close();
    }
    @After public void cleanup()throws Exception {
        ui.runOnMainSync(()->{if(page!=null)page.finish();if(main!=null)main.finish();});ui.waitForIdleSync();
        for(int i=fixtures.size()-1;i>=0;i--)fixtures.get(i).delete();context.deleteDatabase(database);
        SharedPreferences.Editor e=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> x:saved.entrySet()){Object v=x.getValue();String k=x.getKey();if(v==null)e.remove(k);
            else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Float)e.putFloat(k,(Float)v);
            else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof String)e.putString(k,(String)v);}e.commit();NetWorkUtil.refreshHeaders();
    }
    File write(File base,String name,boolean old)throws Exception {
        File file=new File(base,name);File parent=file.getParentFile();
        if(!parent.exists()){assertTrue(parent.mkdirs());fixtures.add(parent);}
        assertTrue(file.createNewFile());fixtures.add(file);
        try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[1024]);}
        if(old)assertTrue(file.setLastModified(System.currentTimeMillis()-3*AppCacheFiles.TEMP_MIN_AGE_MS));
        return file;
    }
    void open(){page=(SettingStorageActivity)ui.startActivitySync(new Intent(context,SettingStorageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    void ready()throws Exception {await(()->page.findViewById(R.id.storage_clear_cache).isEnabled());}
    AlertDialog dialog(){try{Field f=SettingStorageActivity.class.getDeclaredField("confirmation");f.setAccessible(true);return (AlertDialog)f.get(page);}catch(Exception e){throw new AssertionError(e);}}
    void request(int id){ui.runOnMainSync(()->page.findViewById(id).performClick());}
    void confirm(){ui.runOnMainSync(()->dialog().getButton(-1).performClick());}
    @Test public void cacheCancelThenConfirmKeepsMediaQueuesUploadsAccountAndDatabase()throws Exception {
        File stale=write(root,"old.tmp",true),recent=write(root,"recent.tmp",false),audio=write(root,"audio.mp3",true);
        File upload=write(root,"pending-upload/file.bin",true),queue=write(context.getCacheDir(),"playback-queues/"+id+".json",true);
        File durable=write(context.getFilesDir(),"storage-native-"+id+"/saved-video.mp4",true);
        File savedMetadata=write(root,"saved-videos/info.json",true),savedVideo=write(root,"saved-videos/video.mp4",true);
        SharedPreferencesUtil.putString("save_path_video",savedMetadata.getParent());
        File image=write(new File(context.getCacheDir(),AppCacheFiles.IMAGE_DIR),"storage-image-"+id+".0",false);
        File link=new File(root,"linked");Process process=Runtime.getRuntime().exec(new String[]{"ln","-s",durable.getParent(),link.getAbsolutePath()});
        assertEquals(0,process.waitFor());fixtures.add(link);assertFalse(AppCacheFiles.safeChild(root.getCanonicalFile(),link));
        open();ready();capture("storage-summary.png");request(R.id.storage_clear_cache);
        ui.runOnMainSync(()->dialog().getButton(-2).performClick());assertTrue(stale.exists());assertTrue(image.exists());
        request(R.id.storage_clear_cache);confirm();ready();
        assertFalse(stale.exists());assertFalse(image.exists());
        for(File file:Arrays.asList(recent,audio,upload,queue,durable,savedMetadata,savedVideo))assertTrue(file.getName(),file.exists());
        assertEquals("storage-fixture-cookie",SharedPreferencesUtil.getString("cookies",""));
        SQLiteDatabase db=context.openOrCreateDatabase(database,0,null);Cursor c=db.rawQuery("SELECT value FROM kept",null);
        try{assertTrue(c.moveToFirst());assertEquals("history",c.getString(0));}finally{c.close();db.close();}
    }
    @Test public void updatePackageIsConfirmedAndCannotChangeUnderTheOpenConfirmation()throws Exception {
        File updates=context.getExternalFilesDir("updates");
        if(updates==null){updates=new File(context.getFilesDir(),"updates");if(!updates.exists()){assertTrue(updates.mkdirs());fixtures.add(updates);}}
        File first=write(updates,"BiliTerminal_"+id+"01.apk",false),second=write(updates,"BiliTerminal_"+id+"02.apk",false);
        SharedPreferencesUtil.putString("terminal_update_pkg",first.getAbsolutePath());open();ready();request(R.id.storage_clear_apk);
        ui.runOnMainSync(()->dialog().getButton(-2).performClick());assertTrue(first.exists());
        request(R.id.storage_clear_apk);SharedPreferencesUtil.putString("terminal_update_pkg",second.getAbsolutePath());confirm();ready();
        assertTrue(first.exists());assertTrue(second.exists());assertEquals(second.getAbsolutePath(),SharedPreferencesUtil.getString("terminal_update_pkg",""));
        request(R.id.storage_clear_apk);confirm();ready();assertFalse(second.exists());assertTrue(first.exists());
        assertEquals("",SharedPreferencesUtil.getString("terminal_update_pkg",""));
        File outside=write(context.getFilesDir(),"storage-native-"+id+"/BiliTerminal_999.apk",false);
        SharedPreferencesUtil.putString("terminal_update_pkg",outside.getAbsolutePath());
        assertTrue(AppStorageManager.clearUpdatePackage(context,outside.getAbsolutePath()).failures>0);assertTrue(outside.exists());
    }
    @Test public void settingsEntryAndManageVideoNavigationWorkOnDenseRoundScreens()throws Exception {
        SharedPreferencesUtil.putFloat("dpi",1.5f);SharedPreferencesUtil.putBoolean("player_ui_round",true);
        Instrumentation.ActivityMonitor openedMain=ui.addMonitor(SettingMainActivity.class.getName(),null,false);
        try {
            ui.runOnMainSync(()->context.startActivity(new Intent(context,SettingMainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            main=(SettingMainActivity)openedMain.waitForActivityWithTimeout(5000);assertNotNull(main);
        }finally{ui.removeMonitor(openedMain);}
        await(()->main.findViewById(R.id.storageSetting)!=null);
        Instrumentation.ActivityMonitor monitor=ui.addMonitor(SettingStorageActivity.class.getName(),null,false);
        try{ui.runOnMainSync(()->main.findViewById(R.id.storageSetting).performClick());page=(SettingStorageActivity)monitor.waitForActivityWithTimeout(5000);assertNotNull(page);ready();}
        finally{ui.removeMonitor(monitor);}
        Instrumentation.ActivityMonitor videos=ui.addMonitor(LocalListActivity.class.getName(),null,true);
        try{request(R.id.storage_manage_videos);assertEquals(1,videos.getHits());}finally{ui.removeMonitor(videos);}
    }
    interface Check{boolean get();}
    void await(Check check)throws Exception {long end=SystemClock.uptimeMillis()+8000;boolean[] value={false};do{ui.runOnMainSync(()->value[0]=check.get());if(value[0])return;SystemClock.sleep(25);}while(SystemClock.uptimeMillis()<end);fail("Storage UI did not settle");}
    void capture(String name)throws Exception {ui.waitForIdleSync();ui.runOnMainSync(()->{View view=page.getWindow().getDecorView();Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}});}
}
