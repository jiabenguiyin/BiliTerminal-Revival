package com.RobinNotBad.BiliClient.activity.user;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import okio.Buffer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UnfollowConfirmationInstrumentedTest {
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    final Map<String,Object> saved=new HashMap<>();final AtomicInteger posts=new AtomicInteger(),success=new AtomicInteger();
    AtomicReference<OkHttpClient> clients;OkHttpClient original;FollowUsersActivity host;AlertDialog dialog;
    @Before public void setup()throws Exception {
        for(String key:new String[]{"mid","dpi","density","player_ui_round","follow_group_mode"})saved.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        SharedPreferencesUtil.getSharedPreferences().edit().putLong("mid",42).putFloat("dpi",1).putInt("density",-1).putBoolean("player_ui_round",false).putBoolean("follow_group_mode",false).commit();
        Field field=NetWorkUtil.class.getDeclaredField("INSTANCE");field.setAccessible(true);clients=(AtomicReference<OkHttpClient>)field.get(null);original=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            if(chain.request().url().encodedPath().endsWith("/relation/modify")) {
                posts.incrementAndGet();Buffer b=new Buffer();chain.request().body().writeTo(b);HttpUrl form=HttpUrl.parse("https://fixture.invalid/?"+b.readUtf8());assertEquals("777",form.queryParameter("fid"));assertEquals("2",form.queryParameter("act"));
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(ResponseBody.create(MediaType.parse("application/json"),"{\"code\":0,\"data\":{\"list\":[],\"total\":0}}" )).build();
        }).build());
    }
    @After public void cleanup(){finish();clients.set(original);SharedPreferences.Editor e=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> x:saved.entrySet()){Object v=x.getValue();if(v==null)e.remove(x.getKey());else if(v instanceof Long)e.putLong(x.getKey(),(Long)v);else if(v instanceof Integer)e.putInt(x.getKey(),(Integer)v);else if(v instanceof Boolean)e.putBoolean(x.getKey(),(Boolean)v);else if(v instanceof Float)e.putFloat(x.getKey(),(Float)v);}e.commit();}
    @Test public void gapAndCancelNeverUnfollowButExplicitConfirmPostsOnlyOnce()throws Exception {
        launch();open();settle();ui.runOnMainSync(()->{
            Rect cancel=rect(dialog.getButton(-2)),confirm=rect(dialog.getButton(-1));assertSeparated(cancel,confirm);
            View root=dialog.getWindow().getDecorView();int[] origin=new int[2];root.getLocationOnScreen(origin);
            boolean row=confirm.left>=cancel.right;
            float x=(row?(cancel.right+confirm.left)/2f:cancel.exactCenterX())-origin[0],y=(row?cancel.exactCenterY():(cancel.bottom+confirm.top)/2f)-origin[1];long now=SystemClock.uptimeMillis();
            MotionEvent down=MotionEvent.obtain(now,now,0,x,y,0),up=MotionEvent.obtain(now,now+40,1,x,y,0);
            root.dispatchTouchEvent(down);root.dispatchTouchEvent(up);down.recycle();up.recycle();assertEquals(0,posts.get());
            dialog.getButton(-2).performClick();
        });assertEquals(0,posts.get());assertEquals(0,success.get());open();
        ui.runOnMainSync(()->{assertEquals("取消关注",dialog.getButton(-1).getText().toString());dialog.getButton(-1).performClick();dialog.getButton(-1).performClick();});
        await(()->success.get()==1);assertEquals(1,posts.get());
    }
    @Test public void denseSquareAndRoundDialogsKeepSeparateReachableActions()throws Exception {
        for(boolean round:new boolean[]{false,true}) {
            SharedPreferencesUtil.getSharedPreferences().edit().putFloat("dpi",round?2:1).putBoolean("player_ui_round",round).commit();launch();open();settle();
            ui.runOnMainSync(()->{
                View root=dialog.getWindow().getDecorView();Rect bounds=rect(root),cancel=rect(dialog.getButton(-2)),confirm=rect(dialog.getButton(-1));assertSeparated(cancel,confirm);
                assertTrue(bounds.contains(cancel));assertTrue(bounds.contains(confirm));assertTrue(root.getHeight()<=host.findViewById(android.R.id.content).getHeight());
                Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));
                try(FileOutputStream out=new FileOutputStream(new File(host.getFilesDir(),"unfollow-confirm-"+round+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{image.recycle();}
                dialog.getButton(-2).performClick();
            });finish();
        }assertEquals(0,posts.get());
    }
    void launch()throws Exception {host=(FollowUsersActivity)ui.startActivitySync(new Intent(ui.getTargetContext(),FollowUsersActivity.class).putExtra("mid",42L).putExtra("mode",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await(()->((RecyclerView)host.findViewById(R.id.recyclerView)).getAdapter()!=null);}
    void open(){ui.runOnMainSync(()->{try{UserInfo u=new UserInfo();u.mid=777;u.name="关注用户";u.relationAttribute=2;Method m=FollowUsersActivity.class.getDeclaredMethod("confirmCancelFollow",UserInfo.class,Runnable.class);m.setAccessible(true);assertEquals(true,m.invoke(host,u,(Runnable)success::incrementAndGet));Field f=FollowUsersActivity.class.getDeclaredField("cancelFollowDialog");f.setAccessible(true);dialog=(AlertDialog)f.get(host);assertNotNull(dialog);}catch(Exception e){throw new AssertionError(e);}});}
    void finish(){ui.runOnMainSync(()->{if(dialog!=null&&dialog.isShowing())dialog.dismiss();if(host!=null)host.finish();});ui.waitForIdleSync();host=null;dialog=null;}
    void settle(){ui.waitForIdleSync();SystemClock.sleep(120);ui.waitForIdleSync();}
    static Rect rect(View v){Rect r=new Rect();assertTrue(v.getGlobalVisibleRect(r));return r;}
    static void assertSeparated(Rect cancel,Rect confirm){String layout="Cancel="+cancel+", confirm="+confirm;assertTrue("gap "+layout,(confirm.left-cancel.right>=12||confirm.top-cancel.bottom>=10));assertTrue("cancel height "+layout,cancel.height()>=32);assertTrue("confirm height "+layout,confirm.height()>=32);assertTrue("cancel width "+layout,cancel.width()>60);assertTrue("confirm width "+layout,confirm.width()>60);}
    interface Check{boolean get();}void await(Check c){long end=SystemClock.uptimeMillis()+8000;boolean[] done={false};do{ui.runOnMainSync(()->done[0]=c.get());if(done[0])return;SystemClock.sleep(25);}while(SystemClock.uptimeMillis()<end);fail("Follow UI did not settle");}
}
