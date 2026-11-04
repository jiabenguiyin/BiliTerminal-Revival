package com.RobinNotBad.BiliClient.util;

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
import com.RobinNotBad.BiliClient.activity.user.AccountSwitchActivity;
import com.RobinNotBad.BiliClient.adapter.dynamic.UserDynamicAdapter;
import com.RobinNotBad.BiliClient.adapter.user.UpListAdapter;
import com.RobinNotBad.BiliClient.api.UserRelationApi;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.ui.UserRelationActions;
import org.json.*;
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
public class UserRelationActionsInstrumentedTest {
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    final Map<String,Object> saved=new HashMap<>();
    final List<Integer> actions=Collections.synchronizedList(new ArrayList<>());
    AtomicReference<OkHttpClient> clients;OkHttpClient original;AccountSwitchActivity host;
    volatile int serverAttribute,postCode;
    UserInfo user;View header;Button follow;
    @Before public void setup()throws Exception {
        SharedPreferences prefs=ui.getTargetContext().getSharedPreferences("default",0);SharedPreferencesUtil.sharedPreferences=prefs;
        for(String k:new String[]{"mid","cookies","csrf","dpi","density","player_ui_round",AccountManager.ACCOUNTS_KEY,AccountManager.LOGGED_OUT_KEY})saved.put(k,prefs.getAll().get(k));
        prefs.edit().putLong("mid",42).putString("cookies","DedeUserID=42; bili_jct=fixture").putString("csrf","fixture")
                .putFloat("dpi",1).putInt("density",-1).putBoolean("player_ui_round",false).putString(AccountManager.ACCOUNTS_KEY,"[]").putBoolean(AccountManager.LOGGED_OUT_KEY,true).commit();
        NetWorkUtil.refreshHeaders();
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);original=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            String path=chain.request().url().encodedPath();int code=0;JSONObject data=new JSONObject();
            try {
                if(path.endsWith("/x/relation")) {
                    assertEquals("777",chain.request().url().queryParameter("fid"));
                    assertEquals("DedeUserID=42; bili_jct=fixture",chain.request().header("Cookie"));
                    data.put("attribute",serverAttribute);
                }else if(path.endsWith("/relation/modify")) {
                    Buffer b=new Buffer();chain.request().body().writeTo(b);HttpUrl form=HttpUrl.parse("https://fixture.invalid/?"+b.readUtf8());
                    assertEquals("777",form.queryParameter("fid"));assertEquals("fixture",form.queryParameter("csrf"));
                    assertEquals("DedeUserID=42; bili_jct=fixture",chain.request().header("Cookie"));
                    int action=Integer.parseInt(form.queryParameter("act"));actions.add(action);code=postCode;
                    if(code==0)serverAttribute=action==5?128:action==1?2:0;
                }
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                        .body(ResponseBody.create(MediaType.parse("application/json"),new JSONObject().put("code",code).put("data",data).toString())).build();
            }catch(JSONException e){throw new IOException(e);}
        }).build());
        host=(AccountSwitchActivity)ui.startActivitySync(new Intent(ui.getTargetContext(),AccountSwitchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        user=new UserInfo();user.mid=777;user.name="测试UP主";user.avatar="";user.sign="";user.notice="";
        user.vip_nickname_color="";user.sys_notice="";user.officialDesc="";
    }
    @After public void cleanup()throws Exception {
        ui.runOnMainSync(()->{AlertDialog d=current();if(d!=null)d.dismiss();if(host!=null)host.finish();});ui.waitForIdleSync();
        await(()->!pending());clients.set(original);
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> e:saved.entrySet()) {Object v=e.getValue();String k=e.getKey();if(v==null)edit.remove(k);
            else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);
            else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof String)edit.putString(k,(String)v);}
        edit.commit();NetWorkUtil.refreshHeaders();
    }
    void bindProfile() {
        ui.runOnMainSync(()->{
            UserDynamicAdapter adapter=new UserDynamicAdapter(host,new ArrayList<>(),user);
            RecyclerView.ViewHolder holder=adapter.createViewHolder(new FrameLayout(host),0);adapter.bindViewHolder(holder,0);
            header=holder.itemView;follow=header.findViewById(R.id.followBtn);
        });
    }
    @Test public void profileBlockCancelConfirmAndUnblockPreserveExplicitIntent()throws Exception {
        bindProfile();ui.runOnMainSync(()->assertTrue(follow.performLongClick()));awaitMenu();
        assertEquals("关注",current().getListView().getAdapter().getItem(0));assertEquals("拉黑",current().getListView().getAdapter().getItem(1));
        choose(1);awaitConfirm("拉黑");capture("relation-block-confirm.png");
        ui.runOnMainSync(()->current().getButton(-2).performClick());assertTrue(actions.isEmpty());
        ui.runOnMainSync(follow::performLongClick);awaitMenu();choose(1);awaitConfirm("拉黑");
        ui.runOnMainSync(()->{Button yes=current().getButton(-1);yes.performClick();yes.performClick();});
        await(()->!pending()&&user.relationAttribute==128);
        assertEquals(Arrays.asList(5),new ArrayList<>(actions));assertEquals("已拉黑",follow.getText().toString());
        ui.runOnMainSync(follow::performLongClick);awaitMenu();
        assertEquals(1,current().getListView().getAdapter().getCount());assertEquals("移出黑名单",current().getListView().getAdapter().getItem(0));
        choose(0);awaitConfirm("移出");ui.runOnMainSync(()->current().getButton(-1).performClick());
        await(()->!pending()&&user.relationAttribute==0);
        assertEquals(Arrays.asList(5,6),new ArrayList<>(actions));assertFalse(user.followed);assertEquals("关注",follow.getText().toString());
    }
    @Test public void videoAuthorLongPressUsesTheSameMenuAndCanFollow()throws Exception {
        ui.runOnMainSync(()->{
            UpListAdapter adapter=new UpListAdapter(host,new ArrayList<>(Arrays.asList(user)));
            com.RobinNotBad.BiliClient.adapter.user.UserListAdapter.Holder holder=adapter.createViewHolder(new FrameLayout(host),0);
            adapter.bindViewHolder(holder,0);assertTrue(holder.itemView.performLongClick());
        });
        awaitMenu();assertTrue(actions.isEmpty());choose(0);await(()->!pending()&&user.followed);
        assertEquals(Arrays.asList(1),new ArrayList<>(actions));assertEquals(2,user.relationAttribute);
    }
    @Test public void normalFollowAndConfirmedUnfollowUseDistinctCorrectActions()throws Exception {
        bindProfile();ui.runOnMainSync(follow::performClick);await(()->!pending()&&user.followed);assertEquals(2,user.relationAttribute);
        ui.runOnMainSync(follow::performClick);awaitConfirm("取消关注");ui.runOnMainSync(()->current().getButton(-2).performClick());
        assertEquals(Arrays.asList(1),new ArrayList<>(actions));assertTrue(user.followed);
        ui.runOnMainSync(follow::performClick);awaitConfirm("取消关注");ui.runOnMainSync(()->current().getButton(-1).performClick());
        await(()->!pending()&&!user.followed);assertEquals(Arrays.asList(1,2),new ArrayList<>(actions));
    }
    @Test public void failedBlockAndAccountSwitchDoNotChangeLocalRelationship()throws Exception {
        bindProfile();ui.runOnMainSync(follow::performLongClick);awaitMenu();choose(1);awaitConfirm("拉黑");postCode=22015;
        ui.runOnMainSync(()->current().getButton(-1).performClick());await(()->actions.size()==1&&!pending());
        assertEquals(0,user.relationAttribute);assertFalse(user.followed);
        ui.runOnMainSync(follow::performLongClick);awaitMenu();choose(1);awaitConfirm("拉黑");
        ui.runOnMainSync(()->{SharedPreferencesUtil.putLong("mid",43);current().getButton(-1).performClick();});
        SystemClock.sleep(200);assertEquals(1,actions.size());assertEquals(0,user.relationAttribute);
    }
    @Test public void capturedSessionCannotMutateAnotherAccountOrSelf()throws Exception {
        UserRelationApi.Session session=UserRelationApi.capture();SharedPreferencesUtil.putLong("mid",43);
        try{UserRelationApi.modify(session,777,5);fail("Account switch must reject the operation");}catch(IOException expected){}
        assertTrue(actions.isEmpty());SharedPreferencesUtil.putLong("mid",42);
        try{UserRelationApi.modify(UserRelationApi.capture(),42,5);fail("Self action must be rejected");}catch(IOException expected){}
        SharedPreferencesUtil.putLong("mid",0);ui.runOnMainSync(()->UserRelationActions.show(host,user,null));
        assertNull(current());assertTrue(actions.isEmpty());
    }
    @Test public void leavingTheOwnerClosesItsMenuWithoutChangingRelationships()throws Exception {
        ui.runOnMainSync(()->UserRelationActions.show(host,user,null));awaitMenu();
        ui.runOnMainSync(host::finish);await(()->current()==null);assertTrue(actions.isEmpty());
    }
    void choose(int position){ui.runOnMainSync(()->{ListView list=current().getListView();list.performItemClick(list.getChildAt(position),position,list.getAdapter().getItemId(position));});}
    void awaitMenu()throws Exception {await(()->current()!=null&&current().isShowing()&&current().getListView()!=null&&current().getListView().getAdapter()!=null);}
    void awaitConfirm(String label)throws Exception {await(()->current()!=null&&current().isShowing()&&current().getButton(-1)!=null&&label.contentEquals(current().getButton(-1).getText()));}
    AlertDialog current(){try{Field f=UserRelationActions.class.getDeclaredField("OPEN");f.setAccessible(true);return (AlertDialog)((Map)f.get(null)).get(host);}catch(Exception e){throw new AssertionError(e);}}
    boolean pending(){try{Field f=UserRelationActions.class.getDeclaredField("PENDING");f.setAccessible(true);Set s=(Set)f.get(null);synchronized(s){return !s.isEmpty();}}catch(Exception e){throw new AssertionError(e);}}
    interface Check{boolean get();}
    void await(Check check)throws Exception {long end=SystemClock.uptimeMillis()+7000;boolean[] ready={false};do{ui.runOnMainSync(()->ready[0]=check.get());if(ready[0])return;SystemClock.sleep(25);}while(SystemClock.uptimeMillis()<end);fail("Relationship UI did not settle");}
    void capture(String name)throws Exception {ui.waitForIdleSync();SystemClock.sleep(150);ui.runOnMainSync(()->{View view=current().getWindow().getDecorView();Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(host.getFilesDir(),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}});}
}
