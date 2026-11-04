package com.RobinNotBad.BiliClient.activity;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.ui.*;
import com.RobinNotBad.BiliClient.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import okio.Buffer;
import static org.junit.Assert.*;

/** Dynamic and forwarded-child targets are isolated; every HTTP request ends in a fixture. */
@RunWith(AndroidJUnit4.class)
public class DynamicReportInstrumentedTest {
    @Rule public org.junit.rules.TestName testName=new org.junit.rules.TestName();
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();final Map<String,Object> saved=new HashMap<>();
    final ArrayList<DynamicHolder> holders=new ArrayList<>();final ConcurrentLinkedQueue<HttpUrl> posts=new ConcurrentLinkedQueue<>();
    AtomicReference<OkHttpClient> clients;OkHttpClient oldClient;TestActivity owner;volatile int code;
    private static final String[] KEYS={"mid","cookies","dpi","density","paddingH_percent","paddingV_percent","player_ui_round"};
    @Before public void setup()throws Exception{
        SharedPreferences prefs=SharedPreferencesUtil.getSharedPreferences();for(String key:KEYS)saved.put(key,prefs.getAll().get(key));
        prefs.edit().putLong("mid",42).putString("cookies","DedeUserID=42; SESSDATA=fixture; bili_jct=dynamic-csrf")
                .putFloat("dpi",testName.getMethodName().contains("LargeScale")?1.5f:1f).putInt("density",-1).putInt("paddingH_percent",0).putInt("paddingV_percent",0).putBoolean("player_ui_round",false).commit();
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);oldClient=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{Request r=chain.request();if(r.url().encodedPath().endsWith("/dynamic_report/add")){
            Buffer body=new Buffer();r.body().writeTo(body);posts.add(HttpUrl.parse("https://fixture.invalid/?"+body.readUtf8()));assertEquals("dynamic-csrf",r.url().queryParameter("csrf"));
        }return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(ResponseBody.create(MediaType.parse("application/json"),"{\"code\":"+code+"}")).build();}).build());
        owner=(TestActivity)ui.startActivitySync(new Intent(ui.getTargetContext(),TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    @After public void cleanup(){ui.runOnMainSync(()->{for(DynamicHolder h:holders)h.closeReports();if(owner!=null)owner.finish();});ui.waitForIdleSync();
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();for(String key:KEYS){Object v=saved.get(key);if(v==null)edit.remove(key);else if(v instanceof Long)edit.putLong(key,(Long)v);
            else if(v instanceof Integer)edit.putInt(key,(Integer)v);else if(v instanceof Float)edit.putFloat(key,(Float)v);else if(v instanceof String)edit.putString(key,(String)v);else edit.putBoolean(key,(Boolean)v);}edit.commit();clients.set(oldClient);}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new AssertionError(e);}}
    private void await(Callable<Boolean> test){long end=SystemClock.uptimeMillis()+7000;AtomicBoolean done=new AtomicBoolean();do{ui.runOnMainSync(()->{try{done.set(test.call());}catch(Exception e){throw new AssertionError(e);}});if(done.get())return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<end);fail("Dynamic report did not settle");}
    private Dynamic fixture(long id,long author){Dynamic d=new Dynamic();d.dynamicId=id;d.type="DYNAMIC_TYPE_WORD";d.comment_id=id;d.comment_type=17;d.title="";d.content="动态举报入口测试";d.pubTime="刚刚";
        d.userInfo=new UserInfo();d.userInfo.mid=author;d.userInfo.name="较长的动态作者昵称";d.userInfo.avatar="";d.stats=new Stats();d.stats.like=3;return d;}
    private DynamicHolder bind(Dynamic d,boolean child){DynamicHolder[] result={null};ui.runOnMainSync(()->{View row=owner.getLayoutInflater().inflate(child?R.layout.cell_dynamic_child:R.layout.cell_dynamic,null);
        owner.setContentView(row);result[0]=new DynamicHolder(row,owner,child);result[0].showDynamic(owner,d,true);holders.add(result[0]);});ui.waitForIdleSync();return result[0];}
    private DynamicReportController controller(DynamicHolder holder){return (DynamicReportController)field(holder,"reportController");}
    private DynamicReportDialog choose(DynamicHolder holder){DynamicReportController c=controller(holder);
        ui.runOnMainSync(()->holder.itemView.findViewById(holder.isChild?R.id.child_dynamic_report:R.id.item_dynamic_report).performClick());await(()->field(c,"dialog")!=null&&((AlertDialog)field(c,"dialog")).isShowing());
        DynamicReportDialog d=(DynamicReportDialog)field(c,"dialog");ui.runOnMainSync(()->{((RadioGroup)d.findViewById(R.id.dynamic_report_reasons)).check(1);d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});return d;}
    private void submit(DynamicReportDialog d){ui.runOnMainSync(()->{((EditText)d.findViewById(R.id.dynamic_report_note)).setText("请核对动态中的广告内容");d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});}
    private void capture(View root,String name){ui.waitForIdleSync();ui.runOnMainSync(()->{Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);try(FileOutputStream out=new FileOutputStream(new File(owner.getFilesDir(),name))){root.draw(new Canvas(b));b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}});}

    @Test public void mainAndForwardedChildSendTheirOwnDynamicAndAuthorIds(){
        for(boolean child:new boolean[]{false,true}){
            long id=child?123456789012345679L:123456789012345678L,author=child?92:91;DynamicHolder holder=bind(fixture(id,author),child);
            if(!child){ui.runOnMainSync(()->holder.item_dynamic_delete.setVisibility(View.VISIBLE));ui.waitForIdleSync();
                await(()->holder.item_dynamic_delete.getWidth()>0&&holder.item_dynamic_delete.getLeft()>0);
                ui.runOnMainSync(()->{View report=holder.itemView.findViewById(R.id.item_dynamic_report);assertTrue(report.getRight()<=holder.item_dynamic_delete.getLeft());});}
            capture(owner.getWindow().getDecorView(),child?"dynamic-child-report.png":"dynamic-main-report.png");
            submit(choose(holder));await(()->!(Boolean)field(controller(holder),"busy"));HttpUrl request=posts.poll();assertNotNull(request);
            assertEquals(String.valueOf(id),request.queryParameter("dynamic_id"));assertEquals(String.valueOf(author),request.queryParameter("accused_uid"));
            assertEquals("4",request.queryParameter("reason_type"));assertEquals("dynamic-csrf",request.queryParameter("csrf"));assertNull(request.queryParameter("aid"));assertTrue(posts.isEmpty());
        }
    }
    @Test public void focusingDynamicReportDescriptionRequestsKeyboard(){
        DynamicReportDialog dialog=choose(bind(fixture(123456789012345678L,91),false));
        EditText note=dialog.findViewById(R.id.dynamic_report_note);ui.runOnMainSync(()->{
            note.requestFocusFromTouch();note.performClick();
            int mode=dialog.getWindow().getAttributes().softInputMode&WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE;
            assertEquals("soft input mode="+Integer.toHexString(mode),WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,mode);
        });
        assertTrue(note.hasFocus());
        assertTrue(posts.isEmpty());
    }
    @Test public void recyclingClosesDraftAndRebindingNeverReportsOldTarget(){
        Dynamic first=fixture(123456789012345678L,91);DynamicHolder holder=bind(first,false);DynamicReportDialog draft=choose(holder);
        ui.runOnMainSync(()->{holder.showDynamic(owner,first,true);assertTrue(draft.isShowing());holder.showDynamic(owner,fixture(123456789012345680L,93),true);assertFalse(draft.isShowing());});
        assertTrue(posts.isEmpty());submit(choose(holder));await(()->!(Boolean)field(controller(holder),"busy"));
        assertEquals("123456789012345680",posts.peek().queryParameter("dynamic_id"));assertEquals("93",posts.peek().queryParameter("accused_uid"));
        DynamicReportDialog last=choose(holder);ui.runOnMainSync(holder::closeReports);assertFalse(last.isShowing());
    }
    @Test public void cancelAndAccountChangeDoNotSubmitDynamicReport(){
        DynamicHolder holder=bind(fixture(123456789012345678L,91),false);DynamicReportDialog first=choose(holder);
        ui.runOnMainSync(()->first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());assertTrue(posts.isEmpty());
        DynamicReportDialog second=choose(holder);SharedPreferencesUtil.putLong("mid",43);submit(second);assertTrue(posts.isEmpty());
    }
    @Test public void rejectedDynamicReportOffersOfficialPageInsteadOfSuccess(){
        code=-403;DynamicHolder holder=bind(fixture(123456789012345678L,91),false);submit(choose(holder));
        await(()->!(Boolean)field(controller(holder),"busy")&&field(controller(holder),"official")!=null);assertEquals(1,posts.size());
    }
    @Test public void profileSearchIconStaysLeftOfClockAtLargeScale(){
        ui.runOnMainSync(()->{owner.setContentView(R.layout.activity_simple_viewpager);View search=owner.findViewById(R.id.user_video_search);search.setVisibility(View.VISIBLE);((TextView)owner.findViewById(R.id.timeText)).setText("14:08");});
        await(()->owner.findViewById(R.id.user_video_search).getWidth()>0&&owner.findViewById(R.id.timeText).getWidth()>0);
        ui.runOnMainSync(()->{View search=owner.findViewById(R.id.user_video_search),clock=owner.findViewById(R.id.timeText);assertTrue(search.getRight()+MsgUtil.dp(owner,4)<=clock.getLeft()+1);});
        capture(owner.getWindow().getDecorView(),"profile-search-spacing.png");
    }
}
