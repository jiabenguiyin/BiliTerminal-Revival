package com.RobinNotBad.BiliClient.service;

import android.app.Instrumentation;
import android.content.*;
import android.database.*;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity;
import com.RobinNotBad.BiliClient.api.ConfInfoApi;
import com.RobinNotBad.BiliClient.helper.sql.DownloadSqlHelper;
import com.RobinNotBad.BiliClient.model.DownloadSection;
import com.RobinNotBad.BiliClient.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.Deflater;
import okhttp3.*;
import static org.junit.Assert.*;

/** Real service and files, with every HTTP request intercepted locally. */
@RunWith(AndroidJUnit4.class)
public class DownloadFailureInstrumentedTest {
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();final Context context=ui.getTargetContext();
    final Map<String,Object> saved=new HashMap<>();final AtomicInteger mediaCalls=new AtomicInteger(),playCalls=new AtomicInteger();
    final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
    AtomicReference<OkHttpClient> clients;OkHttpClient original;DownloadListActivity page;
    File root,folder;long id=-1;volatile String mode="fail",range;
    final byte[] complete={1,2,3,4,5};
    @Before public void setup()throws Exception {
        assertFalse("Owned test emulator has an active download",DownloadService.started);
        List<DownloadSection> existing=DownloadService.getAll();
        if(existing!=null)for(DownloadSection s:existing)assertEquals("Unexpected queued task", "error",s.state);
        SharedPreferences prefs=SharedPreferencesUtil.getSharedPreferences();
        for(String k:new String[]{"wbi_mixin_key","last_wbi","mid","dpi","density"})saved.put(k,prefs.getAll().get(k));
        prefs.edit().putString("wbi_mixin_key","0123456789abcdef0123456789abcdef").putInt("last_wbi",ConfInfoApi.getDateCurr())
                .putLong("mid",0).putFloat("dpi",1f).putInt("density",-1).commit();
        root=new File(context.getFilesDir(),"download-qa-"+System.nanoTime());assertTrue(root.mkdir());folder=new File(root,"qa-video");
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);original=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            String path=chain.request().url().encodedPath();String json="{\"code\":0,\"data\":{}}";
            if(path.contains("playurl")){
                playCalls.incrementAndGet();json="{\"code\":0,\"data\":{\"quality\":16,\"durl\":[{\"url\":\"https://qa.invalid/video.mp4\",\"backup_url\":[]}],\"accept_quality\":[16],\"accept_description\":[\"360P\"]}}";
            }else if(path.endsWith("/v2"))json="{\"code\":0,\"data\":{\"subtitle\":{\"subtitles\":[]}}}";
            else if(path.endsWith(".xml")){
                Deflater d=new Deflater(6,true);d.setInput("<i/>".getBytes("UTF-8"));d.finish();byte[] b=new byte[80];int n=d.deflate(b);d.end();
                return response(chain.request(),200,Arrays.copyOf(b,n),"application/octet-stream",null);
            }else if(path.endsWith("cover.png"))return response(chain.request(),500,new byte[0],"image/png",null);
            else if(path.endsWith("video.mp4")){
                mediaCalls.incrementAndGet();range=chain.request().header("Range");
                if("pause".equals(mode)){entered.countDown();try{if(!release.await(8,TimeUnit.SECONDS))throw new IOException("fixture timed out");}catch(InterruptedException e){throw new IOException(e);}}
                if("fail".equals(mode))return response(chain.request(),567,new byte[0],"video/mp4",null);
                if("resume".equals(mode))return response(chain.request(),206,new byte[]{3,4,5},"video/mp4","bytes 2-4/5");
                return response(chain.request(),200,complete,"video/mp4",null);
            }
            return response(chain.request(),200,json.getBytes("UTF-8"),"application/json",null);
        }).build());
        page=(DownloadListActivity)ui.startActivitySync(new Intent(context,DownloadListActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        DownloadSqlHelper helper=new DownloadSqlHelper(context);
        try{
            ContentValues values=new ContentValues();values.put("type","video_single");values.put("state","none");values.put("aid",9999991L);
            values.put("cid",9999992L);values.put("qn",16);values.put("title","qa-video");values.put("child","");
            values.put("cover","https://qa.invalid/cover.png");values.put("download_type","video");
            values.put("storage_mode",VideoStorageUtil.MODE_FILE);values.put("storage_ref",root.getAbsolutePath());
            id=helper.getWritableDatabase().insertOrThrow("download",null,values);assertTrue(id>0);
        }finally{helper.close();}
    }
    @After public void cleanup()throws Exception {
        DownloadService.pause();release.countDown();await(()->!DownloadService.started&&DownloadService.section==null);
        if(id>0)DownloadService.deleteSection(id);
        if(page!=null)ui.runOnMainSync(page::finish);ui.waitForIdleSync();
        if(clients!=null)clients.set(original);if(root!=null)delete(root);
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object>e:saved.entrySet()){Object v=e.getValue();if(v==null)edit.remove(e.getKey());else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);
            else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);else edit.putString(e.getKey(),(String)v);}edit.commit();
    }
    @Test public void httpFailurePersistsReasonAndUserRetryResumesInsteadOfSilentlyWaiting()throws Exception {
        DownloadService.start(id);await(()->row()!=null&&"error".equals(row().state)&&!DownloadService.started);
        assertTrue(row().lastError.contains("HTTP 567"));assertEquals(1,mediaCalls.get());assertTrue(new File(folder,".DOWNLOADING").exists());
        ui.runOnMainSync(()->page.refreshList(false));
        await(()->{
            RecyclerView list=page.findViewById(R.id.recyclerView);if(list.getChildCount()==0)return false;
            TextView extra=list.getChildAt(0).findViewById(R.id.text_extra);return extra!=null&&extra.getText().toString().contains("HTTP 567");
        });capture("qa-download-error.png");
        try(FileOutputStream out=new FileOutputStream(new File(folder,"video.mp4"))){out.write(new byte[]{1,2});}
        mode="resume";DownloadService.retry(id);await(()->row()==null&&!DownloadService.started);
        assertEquals("bytes=2-",range);assertEquals(2,playCalls.get());assertEquals(2,mediaCalls.get());
        assertArrayEquals(complete,read(new File(folder,"video.mp4")));assertFalse(new File(folder,".DOWNLOADING").exists());
    }
    @Test public void ignoredRangeNeverOverwritesPartialFileAndStaysAnExplicitFailure()throws Exception {
        assertTrue(folder.mkdirs());File partial=new File(folder,"video.mp4");try(FileOutputStream out=new FileOutputStream(partial)){out.write(new byte[]{1,2});}
        mode="ignore";DownloadService.start(id);await(()->row()!=null&&"error".equals(row().state)&&!DownloadService.started);
        assertArrayEquals(new byte[]{1,2},read(partial));assertTrue(row().lastError.contains("206")||row().lastError.contains("续传"));
    }
    @Test public void explicitPauseRemainsResumableAndIsNotMarkedAsNetworkFailure()throws Exception {
        mode="pause";DownloadService.start(id);assertTrue(entered.await(8,TimeUnit.SECONDS));DownloadService.pause();release.countDown();
        await(()->!DownloadService.started&&DownloadService.section==null);assertNotNull(row());assertEquals("none",row().state);assertEquals("",row().lastError);
        assertTrue(new File(folder,".DOWNLOADING").exists());
    }
    @Test public void versionFiveMigrationPreservesQueuedTaskAndAddsEmptyReason() {
        SQLiteDatabase db=SQLiteDatabase.create(null);DownloadSqlHelper helper=new DownloadSqlHelper(context);
        try{
            db.execSQL("CREATE TABLE download(id INTEGER PRIMARY KEY,state TEXT,title TEXT)");
            db.execSQL("INSERT INTO download VALUES(7,'none','old-task')");helper.onUpgrade(db,5,6);
            try(Cursor c=db.rawQuery("SELECT title,state,last_error FROM download WHERE id=7",null)){
                assertTrue(c.moveToFirst());assertEquals("old-task",c.getString(0));assertEquals("none",c.getString(1));assertEquals("",c.getString(2));
            }
        }finally{db.close();helper.close();}
    }
    Response response(Request req,int code,byte[] bytes,String type,String range){Response.Builder b=new Response.Builder().request(req).protocol(Protocol.HTTP_1_1)
            .code(code).message("fixture").body(ResponseBody.create(MediaType.parse(type),bytes));if(range!=null)b.header("Content-Range",range);return b.build();}
    DownloadSection row(){List<DownloadSection> all=DownloadService.getAll();if(all!=null)for(DownloadSection s:all)if(s.id==id)return s;return null;}
    byte[] read(File p)throws Exception{try(InputStream in=new FileInputStream(p);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[]b=new byte[50];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    void delete(File p)throws Exception{assertTrue(p.getCanonicalPath().startsWith(root.getCanonicalPath()));File[]children=p.listFiles();if(children!=null)for(File c:children)delete(c);p.delete();}
    interface Check{boolean get();}
    void await(Check c)throws Exception{long end=SystemClock.uptimeMillis()+12000;do{if(c.get())return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<end);fail("Download did not settle");}
    void capture(String name)throws Exception{ui.waitForIdleSync();ui.runOnMainSync(()->{View v=page.findViewById(android.R.id.content);Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));try(FileOutputStream out=page.openFileOutput(name,0)){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}});}
}
