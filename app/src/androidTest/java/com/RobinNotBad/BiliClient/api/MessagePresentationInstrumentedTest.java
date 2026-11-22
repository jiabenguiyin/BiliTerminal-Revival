package com.RobinNotBad.BiliClient.api;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.activity.user.MySpaceActivity;
import com.RobinNotBad.BiliClient.activity.reply.ReplyInfoActivity;
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity;
import com.RobinNotBad.BiliClient.adapter.message.*;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import static org.junit.Assert.*;

/** Uses only local intercepted responses, including the displayed comment thread. */
@RunWith(AndroidJUnit4.class)
public class MessagePresentationInstrumentedTest {
    private static final long OID=963160301L,ROOT=963160302L,TARGET=963160303L;
    private final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    private final Context app=ui.getTargetContext();
    private final List<Activity> activities=new ArrayList<>();
    private final List<Request> requests=Collections.synchronizedList(new ArrayList<>());
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient oldClient;
    private Map<String,?> saved;
    private String targetLink;
    @Before public void setup()throws Exception{
        saved=new HashMap<>(SharedPreferencesUtil.getSharedPreferences().getAll());
        AccountManager.saveLogin("DedeUserID=42; SESSDATA=message-fixture; bili_jct=fixture-csrf","");
        SharedPreferencesUtil.putBoolean("relay_always_on",false);
        targetLink="bilibili://comment/detail/1/"+OID+"/"+ROOT+"/?anchor="+TARGET;
        Field field=NetWorkUtil.class.getDeclaredField("INSTANCE");field.setAccessible(true);
        clients=(AtomicReference<OkHttpClient>)field.get(null);oldClient=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            Request request=chain.request();requests.add(request);
            String path=request.url().encodedPath();
            try{
                JSONObject data=new JSONObject();
                if(path.endsWith("/myinfo"))data.put("mid",42).put("name","硬币小数测试").put("money",14.6);
                else if(path.endsWith("/nav"))data.put("mid",42).put("isLogin",true).put("money",14.6);
                else if(path.endsWith("/x/msgfeed/like")){
                    JSONObject item=new JSONObject().put("type","reply").put("item_id",888)
                            .put("business_id",0).put("title","被点赞的评论").put("native_uri",targetLink)
                            .put("uri","https://www.bilibili.com/video/BV1i4411Q7Py/");
                    JSONObject row=new JSONObject().put("id",771).put("like_time",200).put("counts",1).put("item",item)
                            .put("users",new JSONArray().put(new JSONObject().put("mid",7).put("nickname","点赞用户")
                                    .put("avatar","").put("fans",0).put("follow",false)));
                    data.put("latest",new JSONObject().put("items",new JSONArray().put(row)));
                    data.put("total",new JSONObject().put("items",new JSONArray()).put("cursor",new JSONObject().put("is_end",true)));
                }else if(path.endsWith("/reply/reply")){
                    assertEquals(String.valueOf(OID),request.url().queryParameter("oid"));
                    assertEquals(String.valueOf(ROOT),request.url().queryParameter("root"));
                    assertEquals("1",request.url().queryParameter("type"));
                    data.put("root",reply(ROOT,0)).put("replies",new JSONArray().put(reply(TARGET,ROOT)))
                            .put("page",new JSONObject().put("size",20).put("num",1).put("count",1));
                }
                String body=new JSONObject().put("code",0).put("data",data).toString();
                return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                        .body(ResponseBody.create(MediaType.parse("application/json"),body)).build();
            }catch(JSONException error){throw new IOException(error);}
        }).build());
    }
    private JSONObject reply(long id,long root)throws JSONException{
        return new JSONObject().put("rpid",id).put("oid",OID).put("root",root).put("parent",root)
                .put("ctime",1700000000).put("like",1).put("rcount",1).put("replies",new JSONArray())
                .put("member",new JSONObject().put("mid",42).put("uname","评论用户").put("avatar","").put("is_senior_member",0)
                        .put("level_info",new JSONObject().put("current_level",6))
                        .put("vip",new JSONObject().put("vipStatus",0).put("nickname_color","")))
                .put("reply_control",new JSONObject()).put("content",new JSONObject().put("message",id==TARGET?"目标评论":"根评论"))
                .put("up_action",new JSONObject().put("like",false).put("reply",false));
    }
    @After public void cleanup(){
        ui.runOnMainSync(()->{for(Activity a:activities)a.finish();});ui.waitForIdleSync();
        clients.set(oldClient);
        SharedPreferences.Editor e=SharedPreferencesUtil.getSharedPreferences().edit().clear();
        for(Map.Entry<String,?> row:saved.entrySet()){
            Object value=row.getValue();String key=row.getKey();
            if(value instanceof String)e.putString(key,(String)value);else if(value instanceof Long)e.putLong(key,(Long)value);
            else if(value instanceof Integer)e.putInt(key,(Integer)value);else if(value instanceof Boolean)e.putBoolean(key,(Boolean)value);
            else if(value instanceof Float)e.putFloat(key,(Float)value);else if(value instanceof Set)e.putStringSet(key,(Set<String>)value);
        }
        e.commit();NetWorkUtil.refreshHeaders();
    }
    private <T extends Activity>T launch(Class<T> type,Intent intent){
        T activity=(T)ui.startActivitySync(intent.setClass(app,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activities.add(activity);return activity;
    }
    private static class CaptureContext extends ContextWrapper{
        Intent started;CaptureContext(Context base){super(base);}
        @Override public void startActivity(Intent intent){started=new Intent(intent);}
    }
    @Test public void ownCoinBalanceShowsOneDecimalOnTheActualProfilePage()throws Exception{
        MySpaceActivity page=launch(MySpaceActivity.class,new Intent());
        await(()->((TextView)page.findViewById(R.id.userFans))!=null&&((TextView)page.findViewById(R.id.userFans)).getText().toString().contains("14.6硬币"));
        assertEquals(14.6,UserInfoApi.getCurrentUserCoin(),.000001);
    }
    @Test public void unreadReplyCardsHaveAFrameAndRecyclingRemovesIt()throws Exception{
        TestActivity host=launch(TestActivity.class,new Intent());
        MessageCard card=new MessageCard();card.getType=MessageCard.GET_TYPE_REPLY;card.content="新回复";card.readStateKnown=true;card.isNew=true;
        ui.runOnMainSync(()->{
            NoticeAdapter adapter=new NoticeAdapter(host,Collections.singletonList(card));
            NoticeHolder holder=adapter.onCreateViewHolder(new FrameLayout(host),0);
            adapter.onBindViewHolder(holder,0);assertTrue(holder.noticeCard.getStrokeWidth()>0);
            card.isNew=false;adapter.onBindViewHolder(holder,0);assertEquals(0,holder.noticeCard.getStrokeWidth());
            card.isNew=true;card.readStateKnown=false;adapter.onBindViewHolder(holder,0);assertEquals(0,holder.noticeCard.getStrokeWidth());
            card.readStateKnown=true;adapter.onBindViewHolder(holder,0);adapter.onViewRecycled(holder);
            assertEquals(0,holder.noticeCard.getStrokeWidth());
        });
    }
    @Test public void aLikedChildCommentOpensAndHighlightsTheActualReply()throws Exception{
        MessageCard card=MessageApi.getLikeMsg(0,0).second.get(0);
        TestActivity host=launch(TestActivity.class,new Intent());CaptureContext context=new CaptureContext(host);
        ui.runOnMainSync(()->{
            NoticeHolder holder=new NoticeHolder(View.inflate(context,R.layout.cell_message,null));
            holder.showMessage(card,context);holder.noticeCard.performClick();
        });
        Intent intent=context.started;assertNotNull(intent);assertEquals(ReplyInfoActivity.class.getName(),intent.getComponent().getClassName());
        assertEquals(OID,intent.getLongExtra("oid",0));assertEquals(ROOT,intent.getLongExtra("rpid",0));assertEquals(TARGET,intent.getLongExtra("seekReply",0));
        ReplyInfoActivity thread=launch(ReplyInfoActivity.class,intent);
        await(()->value(thread,"replyAdapter")!=null&&((List<?>)value(thread,"replyList")).size()==2);
        assertEquals(TARGET,value(value(thread,"replyAdapter"),"highlightRpid"));
        boolean replyRequest=false;for(Request q:requests)if(q.url().encodedPath().endsWith("/reply/reply"))replyRequest=true;
        assertTrue(replyRequest);
    }
    @Test public void legacyBvCommentLinksCarryTheSeekTargetInsteadOfOpeningOnlyTheVideo()throws Exception{
        targetLink="";MessageCard card=MessageApi.getLikeMsg(0,0).second.get(0);
        assertEquals(888,card.targetId);
        TestActivity host=launch(TestActivity.class,new Intent());CaptureContext context=new CaptureContext(host);
        ui.runOnMainSync(()->{
            NoticeHolder holder=new NoticeHolder(View.inflate(context,R.layout.cell_message,null));
            holder.showMessage(card,context);holder.itemView.findViewById(R.id.cardView).performClick();
        });
        assertEquals(VideoInfoActivity.class.getName(),context.started.getComponent().getClassName());
        assertEquals(888,context.started.getLongExtra("seekReply",0));assertEquals(888,context.started.getLongExtra("seekRoot",0));
        assertEquals("BV1i4411Q7Py",context.started.getStringExtra("bvid"));
    }
    private Object value(Object owner,String name){
        try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new AssertionError(e);}
    }
    private interface Check{boolean okay();}
    private void await(Check check)throws Exception{
        long end=SystemClock.elapsedRealtime()+15000;boolean[] okay={false};
        while(SystemClock.elapsedRealtime()<end){ui.runOnMainSync(()->okay[0]=check.okay());if(okay[0])return;SystemClock.sleep(50);}
        throw new AssertionError("Timed out");
    }
}
