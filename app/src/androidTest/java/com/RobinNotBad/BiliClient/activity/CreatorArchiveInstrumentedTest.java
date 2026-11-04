package com.RobinNotBad.BiliClient.activity;

import android.app.*;
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
import com.RobinNotBad.BiliClient.activity.browser.BrowserActivity;
import com.RobinNotBad.BiliClient.activity.user.*;
import com.RobinNotBad.BiliClient.api.CreatorArchiveApi;
import com.RobinNotBad.BiliClient.api.CreatorArchiveApi.*;
import com.RobinNotBad.BiliClient.util.*;
import okhttp3.*;
import okio.Buffer;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** All HTTP terminates inside a local fixture interceptor. No real archive is modified. */
@RunWith(AndroidJUnit4.class)
public class CreatorArchiveInstrumentedTest {
    private final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    private final ArrayList<Activity> owners=new ArrayList<>();
    private final ConcurrentLinkedQueue<Request> requests=new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<HttpUrl> posts=new ConcurrentLinkedQueue<>();
    private final Map<String,Object> saved=new HashMap<>();
    private AtomicReference<OkHttpClient> clients;private OkHttpClient oldClient;
    private volatile int deleteCode;private volatile boolean offline,removed,statFailure,unknownOwner,foreignOwner,missingResult;
    private volatile CountDownLatch gate;
    private CreatorArchiveActivity owner;
    private static final String[] KEYS={SharedPreferencesUtil.mid,SharedPreferencesUtil.cookies,"dpi","density","paddingH_percent","paddingV_percent","player_ui_round","ui_landscape"};

    @Before public void setup() throws Exception {
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);oldClient=clients.get();
        SharedPreferences prefs=SharedPreferencesUtil.getSharedPreferences();for(String k:KEYS)saved.put(k,prefs.getAll().get(k));
        prefs.edit().putFloat("dpi",1f).putInt("density",-1).putInt("paddingH_percent",0).putInt("paddingV_percent",0)
                .putBoolean("player_ui_round",false).putBoolean("ui_landscape",false).commit();account(42);
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            Request r=chain.request();requests.add(r);String path=r.url().encodedPath();String json="{\"code\":-404}";
            if(path.endsWith("/nav"))json="{\"code\":0,\"data\":{\"mid\":"+SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)+",\"isLogin\":true}}";
            else if(path.endsWith("/archive/delete")){
                Buffer b=new Buffer();r.body().writeTo(b);posts.add(HttpUrl.parse("https://fixture.invalid/?"+b.readUtf8()));
                CountDownLatch held=gate;if(held!=null)try{held.await(6,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}
                if(offline)throw new IOException("fixture offline");
                if(deleteCode==0)removed=true;json=missingResult?"{}":"{\"code\":"+deleteCode+"}";
            }else if(path.endsWith("/archives")){
                int page=Integer.parseInt(r.url().queryParameter("pn"));json=page(page,removed?0:21,removed?"":row(page==1?1001:1002));
            }else if(path.endsWith("/archive/view"))json="{\"code\":0,\"data\":{\"archive\":{\"aid\":1001,\"bvid\":\"BVfixture\",\"title\":\"确认用测试稿件\",\"mid\":"+(unknownOwner?0:foreignOwner?99:42)+"}}}";
            else if(path.endsWith("/web-interface/view"))json="{\"code\":0,\"data\":{\"aid\":1001,\"owner\":{\"mid\":42}}}";
            else if(path.endsWith("/index/stat"))json=statFailure?"{\"code\":-403}":"{\"code\":0,\"data\":{\"total_fans\":12,\"incr_fans\":1}}";
            else if(path.endsWith("/index/scrolls"))json="{\"code\":0,\"data\":{\"scrolls\":[]}}";
            return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(ResponseBody.create(MediaType.parse("application/json"),json)).build();
        }).build());
    }
    @After public void cleanup()throws Exception{
        if(gate!=null)gate.countDown();
        await(()->!fieldBoolean("deleting"));
        ui.runOnMainSync(()->{for(Activity a:owners)a.finish();});ui.waitForIdleSync();SystemClock.sleep(100);
        SharedPreferences.Editor e=SharedPreferencesUtil.getSharedPreferences().edit();
        for(String k:KEYS){Object v=saved.get(k);if(v==null)e.remove(k);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Long)e.putLong(k,(Long)v);
            else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else e.putBoolean(k,(Boolean)v);}e.commit();clients.set(oldClient);
    }
    private boolean hasRequest(String key,String value){for(Request r:requests)if(value.equals("path".equals(key)?r.url().encodedPath():r.url().queryParameter(key)))return true;return false;}
    private void account(long mid){SharedPreferencesUtil.getSharedPreferences().edit().putLong(SharedPreferencesUtil.mid,mid)
            .putString(SharedPreferencesUtil.cookies,"DedeUserID="+mid+"; SESSDATA=fixture-"+mid+"; bili_jct=csrf-"+mid).commit();}
    private String row(long aid){return "{\"Archive\":{\"aid\":"+aid+",\"mid\":"+(unknownOwner?0:foreignOwner?99:42)+",\"bvid\":\"BVfixture\",\"title\":\"用于管理测试的本人稿件（不是真实视频）\",\"state\":0},\"stat\":{\"view\":123,\"like\":4}}";}
    private String page(int p,int count,String row){return "{\"code\":0,\"data\":{\"page\":{\"pn\":"+p+",\"ps\":20,\"count\":"+count+"},\"arc_audits\":["+row+"]}}";}
    private <T extends Activity>T launch(Class<T> type)throws Exception{
        Instrumentation.ActivityMonitor monitor=ui.addMonitor(type.getName(),null,false);
        try{ui.runOnMainSync(()->ui.getTargetContext().startActivity(new Intent(ui.getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            T a=type.cast(monitor.waitForActivityWithTimeout(8000));assertNotNull(a);owners.add(a);return a;
        }finally{ui.removeMonitor(monitor);}
    }
    private void open()throws Exception{owner=launch(CreatorArchiveActivity.class);await(()->!fieldBoolean("busy")&&list().getAdapter().getItemCount()>0);ui.waitForIdleSync();}
    private RecyclerView list(){return owner.findViewById(R.id.creator_list);}
    private boolean fieldBoolean(String name){if(owner==null)return false;try{Field f=CreatorArchiveActivity.class.getDeclaredField(name);f.setAccessible(true);return f.getBoolean(owner);}catch(Exception e){throw new AssertionError(e);}}
    private AlertDialog dialog(){try{Field f=CreatorArchiveActivity.class.getDeclaredField("dialogs");f.setAccessible(true);ArrayList<AlertDialog>d=(ArrayList<AlertDialog>)f.get(owner);for(int i=d.size()-1;i>=0;i--)if(d.get(i).isShowing())return d.get(i);return null;}catch(Exception e){throw new AssertionError(e);}}
    private AlertDialog confirm(){await(()->list().findViewHolderForAdapterPosition(0)!=null);ui.runOnMainSync(()->list().findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.creator_delete).performClick());
        await(()->dialog()!=null&&dialog().findViewById(R.id.creator_delete_consent)!=null);return dialog();}
    private void submit(AlertDialog d){ui.runOnMainSync(()->{((CheckBox)d.findViewById(R.id.creator_delete_consent)).setChecked(true);d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});}
    private void await(Callable<Boolean> predicate){long end=SystemClock.uptimeMillis()+9000;AtomicBoolean ok=new AtomicBoolean();do{ui.runOnMainSync(()->{try{ok.set(predicate.call());}catch(Exception e){throw new AssertionError(e);}});if(ok.get())return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<end);fail("Timed out waiting for fixture UI");}
    private void capture(View root,String name){ui.waitForIdleSync();ui.runOnMainSync(()->{Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        try(FileOutputStream out=new FileOutputStream(new File(ui.getTargetContext().getFilesDir(),name))){root.draw(new Canvas(b));b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}});}

    @Test public void statisticsFailureStillOffersManagementAndNoFakeNumbers()throws Exception{
        statFailure=true;CreativeCenterActivity center=launch(CreativeCenterActivity.class);
        await(()->center.findViewById(R.id.creator_statistics_status)!=null&&((TextView)center.findViewById(R.id.creator_statistics_status)).getText().toString().contains("权限"));
        ui.runOnMainSync(()->{assertFalse(center.isFinishing());assertEquals("—",((TextView)center.findViewById(R.id.totalFans_number)).getText().toString());assertTrue(center.findViewById(R.id.creator_manage).isEnabled());});
        capture(center.getWindow().getDecorView(),"creator-statistics.png");
    }
    @Test public void paginationAndFilterUsePrivateAccountList()throws Exception{
        open();capture(owner.getWindow().getDecorView(),"creator-list.png");
        ui.runOnMainSync(()->owner.findViewById(R.id.creator_next).performClick());await(()->!fieldBoolean("busy")&&((TextView)owner.findViewById(R.id.creator_page_status)).getText().toString().contains("第 2 页"));
        ui.runOnMainSync(()->owner.findViewById(R.id.creator_filter).performClick());await(()->dialog()!=null);
        ui.runOnMainSync(()->dialog().getListView().performItemClick(dialog().getListView().getChildAt(1),1,1));
        await(()->!fieldBoolean("busy")&&hasRequest("status","pubed"));
        assertTrue(posts.isEmpty());
    }
    @Test public void cancelAndAccountSwitchBeforeConfirmationNeverDelete()throws Exception{
        open();AlertDialog first=confirm();ui.runOnMainSync(()->{assertFalse(first.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());first.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(posts.isEmpty());});
        capture(first.getWindow().getDecorView(),"creator-delete-confirm.png");
        ui.runOnMainSync(()->first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());assertTrue(posts.isEmpty());
        AlertDialog second=confirm();account(43);submit(second);await(()->!fieldBoolean("busy"));assertTrue(posts.isEmpty());
    }
    @Test public void confirmedDeletionPostsCapturedCsrfOnceAndRefreshes()throws Exception{
        open();AlertDialog d=confirm();gate=new CountDownLatch(1);submit(d);await(()->posts.size()==1);
        ui.runOnMainSync(()->d.getButton(AlertDialog.BUTTON_POSITIVE).performClick());assertEquals(1,posts.size());
        gate.countDown();await(()->!fieldBoolean("deleting")&&!fieldBoolean("busy")&&list().getAdapter().getItemCount()==0);
        assertEquals("1001",posts.peek().queryParameter("aid"));assertEquals("csrf-42",posts.peek().queryParameter("csrf"));
        Request r=null;for(Request x:requests)if(x.url().encodedPath().endsWith("/archive/delete"))r=x;
        assertNotNull(r);assertTrue(r.header("Cookie").contains("fixture-42"));assertEquals("https://member.bilibili.com",r.header("Origin"));
        capture(owner.getWindow().getDecorView(),"creator-empty.png");
    }
    @Test public void verificationErrorRetainsArchiveAndOfficialFallbackTargetsManager()throws Exception{
        deleteCode=340022;open();submit(confirm());await(()->!fieldBoolean("deleting")&&dialog()!=null);
        assertEquals(1,list().getAdapter().getItemCount());
        ui.runOnMainSync(()->{assertTrue(((TextView)dialog().findViewById(android.R.id.message)).getText().toString().contains("安全验证"));dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        await(()->dialog()!=null&&((TextView)dialog().findViewById(android.R.id.message)).getText().toString().contains("目标："));
        capture(dialog().getWindow().getDecorView(),"creator-official-confirm.png");
        AtomicReference<Intent> target=new AtomicReference<>();
        boolean canInspect=android.os.Build.VERSION.SDK_INT>=23;
        Instrumentation.ActivityMonitor browser=canInspect?new Instrumentation.ActivityMonitor(){
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                if(intent.getComponent()!=null&&BrowserActivity.class.getName().equals(intent.getComponent().getClassName())){
                    target.set(intent);return new Instrumentation.ActivityResult(0,null);
                }
                return null;
            }
        }:new Instrumentation.ActivityMonitor(BrowserActivity.class.getName(),new Instrumentation.ActivityResult(0,null),true);
        ui.addMonitor(browser);
        try{
            ui.runOnMainSync(()->dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            if(canInspect){await(()->target.get()!=null);assertEquals(CreatorArchiveApi.MANAGER_URL,target.get().getStringExtra("url"));}
            else await(()->browser.getHits()==1);
        }finally{ui.removeMonitor(browser);}
        assertEquals(1,posts.size());
    }
    @Test public void interruptedDeleteNeverAutomaticallyRetries()throws Exception{
        offline=true;open();submit(confirm());await(()->!fieldBoolean("deleting")&&dialog()!=null);
        assertEquals(1,posts.size());ui.runOnMainSync(()->{assertTrue(((TextView)dialog().findViewById(android.R.id.message)).getText().toString().contains("无法确认"));dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});
        ui.runOnMainSync(()->list().findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.creator_delete).performClick());SystemClock.sleep(200);assertEquals(1,posts.size());
    }
    @Test public void missingDeletionResultIsUncertainAndNotReportedAsSuccess()throws Exception{
        missingResult=true;open();submit(confirm());await(()->!fieldBoolean("deleting")&&dialog()!=null);
        assertEquals(1,posts.size());assertEquals(1,list().getAdapter().getItemCount());
        ui.runOnMainSync(()->assertTrue(((TextView)dialog().findViewById(android.R.id.message)).getText().toString().contains("无法确认")));
    }
    @Test public void ownershipProofDeniesForeignAndUsesPublicOwnerWhenPrivateOmitsIt()throws Exception{
        Session account=CreatorArchiveApi.capture();CreatorArchiveApi.verifyAccount(account);
        foreignOwner=true;Archive other=CreatorArchiveApi.parsePage(new JSONObject(page(1,1,row(1001))),42,1).items.get(0);
        try{CreatorArchiveApi.prepareDelete(account,other);fail();}catch(Failure e){assertEquals(-403,e.code);}assertTrue(posts.isEmpty());
        foreignOwner=false;unknownOwner=true;Archive own=CreatorArchiveApi.parsePage(new JSONObject(page(1,1,row(1001))),42,1).items.get(0);
        OwnedArchive proven=CreatorArchiveApi.prepareDelete(account,own);assertEquals(1001,proven.aid);
        assertTrue(hasRequest("path","/x/web-interface/view"));
        account(43);try{CreatorArchiveApi.delete(account,proven);fail();}catch(Failure e){assertEquals(-10001,e.code);}assertTrue(posts.isEmpty());
    }
}
