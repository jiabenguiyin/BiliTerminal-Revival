package com.RobinNotBad.BiliClient.activity.message;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.api.PrivateSessionLoader;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrivateSessionsRefreshInstrumentedTest {
    private final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> saved=new HashMap<>();
    private final AtomicInteger calls=new AtomicInteger();
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient previous;
    private volatile int code;
    private volatile CountDownLatch gate;
    private MessageActivity host;
    private long mid;

    @Before public void setup()throws Exception {
        SharedPreferences prefs=SharedPreferencesUtil.getSharedPreferences();
        for(String key:new String[]{SharedPreferencesUtil.mid,SharedPreferencesUtil.cookies,"dpi","density","paddingH_percent","paddingV_percent"})saved.put(key,prefs.getAll().get(key));
        String tutorial="tutorial_ver_"+ui.getTargetContext().getResources().getStringArray(R.array.tutorial_list)[5];
        saved.put(tutorial,prefs.getAll().get(tutorial));
        prefs.edit().putInt(tutorial,99).putFloat("dpi",1f).putInt("density",-1).putInt("paddingH_percent",0).putInt("paddingV_percent",0).commit();
        mid=System.nanoTime();account(mid);
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);
        clients=(AtomicReference<OkHttpClient>)f.get(null);previous=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            Request request=chain.request();String path=request.url().encodedPath();
            String body="{\"code\":0,\"data\":{\"reply\":0,\"like\":0,\"at\":0,\"system\":0,\"follow_unread\":0,\"unfollow_unread\":0}}";
            if(path.endsWith("/get_sessions")){
                calls.incrementAndGet();CountDownLatch wait=gate;
                if(wait!=null)try{assertTrue(wait.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IOException(e);}
                body=code==0?"{\"code\":0,\"data\":{\"has_more\":0,\"session_list\":[{\"talker_id\":7,\"session_type\":1,\"session_ts\":1234,\"unread_count\":0,\"account_info\":{\"name\":\"缓存测试会话\",\"pic_url\":\"\"}}]}}"
                        :"{\"code\":"+code+"}";
            }
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(ResponseBody.create(MediaType.parse("application/json"),body)).build();
        }).build());
    }
    @After public void cleanup()throws Exception {
        if(gate!=null)gate.countDown();
        if(host!=null){await(()->!loading());ui.runOnMainSync(host::finish);ui.waitForIdleSync();}
        clients.set(previous);
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> value:saved.entrySet()){
            Object v=value.getValue();String k=value.getKey();
            if(v==null)edit.remove(k);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);
            else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof String)edit.putString(k,(String)v);else edit.putBoolean(k,(Boolean)v);
        }edit.commit();
    }
    private void account(long uid){SharedPreferencesUtil.getSharedPreferences().edit().putLong(SharedPreferencesUtil.mid,uid)
            .putString(SharedPreferencesUtil.cookies,"DedeUserID="+uid+"; SESSDATA=message-fixture-"+uid).commit();}
    private void open(){host=(MessageActivity)ui.startActivitySync(new Intent(ui.getTargetContext(),MessageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(()->host.findViewById(R.id.sessions_list)!=null);}
    private void refresh(){ui.runOnMainSync(()->{
        try{Method method=MessageActivity.class.getDeclaredMethod("loadMessages");method.setAccessible(true);method.invoke(host);}
        catch(Exception e){throw new AssertionError(e);}
    });}
    private boolean loading()throws Exception {Field f=MessageActivity.class.getDeclaredField("loading");f.setAccessible(true);return f.getBoolean(host);}
    private RecyclerView list(){return host.findViewById(R.id.sessions_list);}
    private void await(Callable<Boolean> test){long until=SystemClock.uptimeMillis()+7000;boolean[] ok={false};while(SystemClock.uptimeMillis()<until){
        ui.runOnMainSync(()->{try{ok[0]=test.call();}catch(Exception e){throw new AssertionError(e);}});if(ok[0])return;SystemClock.sleep(40);}fail("Message refresh did not settle");}

    @Test public void repeatedRefreshWhileLoadingStartsOneSessionRequest()throws Exception {
        gate=new CountDownLatch(1);open();await(()->calls.get()==1);
        for(int i=0;i<10;i++)refresh();assertEquals(1,calls.get());
        gate.countDown();await(()->!loading()&&list().getAdapter()!=null);
        refresh();await(()->!loading());assertEquals(1,calls.get());
    }
    @Test public void rateLimitShowsInlineRetryAndKeepsTheSuccessfulList()throws Exception {
        open();await(()->!loading()&&list().getAdapter()!=null);
        code=-509;PrivateSessionLoader.Result failed=PrivateSessionLoader.loadCurrent(20,0,true);
        assertNotNull(failed.failure);refresh();await(()->!loading());
        ui.runOnMainSync(()->{
            assertEquals(1,list().getAdapter().getItemCount());
            assertTrue(((TextView)host.findViewById(R.id.private_sessions_status)).getText().toString().contains("上次成功"));
            assertFalse(host.findViewById(R.id.private_sessions_retry).isEnabled());
        });
        for(int i=0;i<4;i++){refresh();await(()->!loading());}assertEquals(2,calls.get());
        ui.runOnMainSync(()->{
            androidx.core.widget.NestedScrollView scroll=host.findViewById(R.id.scrollView);
            scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
        });
        await(()->{
            android.graphics.Rect visible=new android.graphics.Rect();
            return host.findViewById(R.id.private_sessions_retry).getGlobalVisibleRect(visible)&&visible.height()>0;
        });
        ui.runOnMainSync(()->{
            View root=host.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
            try(FileOutputStream output=new FileOutputStream(new File(host.getFilesDir(),"private-sessions-retry.png"))){root.draw(new Canvas(bitmap));bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}
            catch(Exception error){throw new AssertionError(error);}finally{bitmap.recycle();}
        });
    }
    @Test public void firstFailureIsNotAnEmptySuccessAndSwitchingAccountsHidesOldRows()throws Exception {
        open();await(()->!loading()&&list().getAdapter()!=null);
        account(mid+1);code=-509;refresh();await(()->!loading());
        ui.runOnMainSync(()->{
            assertTrue(list().getAdapter()==null||list().getAdapter().getItemCount()==0);
            assertEquals(View.VISIBLE,host.findViewById(R.id.private_sessions_feedback).getVisibility());
            assertTrue(((TextView)host.findViewById(R.id.private_sessions_status)).getText().toString().contains("-509"));
        });
        assertEquals(2,calls.get());
    }
}
