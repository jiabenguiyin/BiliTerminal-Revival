package com.RobinNotBad.BiliClient.activity.reply;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.api.ConfInfoApi;
import com.RobinNotBad.BiliClient.event.ReplyEvent;
import com.RobinNotBad.BiliClient.event.SnackEvent;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import okio.Buffer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReplySendInstrumentedTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final long oid=System.nanoTime(), rpid=oid+10;
    private final String draft="comment latency fixture";
    private final Map<String,Object> saved=new HashMap<>();
    private final AtomicInteger posts=new AtomicInteger(), checks=new AtomicInteger(), uploads=new AtomicInteger();
    private final CountDownLatch postStarted=new CountDownLatch(1), verifyStarted=new CountDownLatch(1), verifyReturned=new CountDownLatch(1);
    private CountDownLatch postGate=new CountDownLatch(0), verifyGate=new CountDownLatch(0), uploadGate=new CountDownLatch(0);
    private final List<ReplyEvent> events=new CopyOnWriteArrayList<>();
    private final List<String> messages=new CopyOnWriteArrayList<>();
    private volatile boolean visible=true, networkError=false, noReply=false;
    private volatile int code=0, type=1;
    private volatile long root=0, parent=0, acknowledgedAt;
    private volatile String postedPictures;
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient original;
    private WriteReplyActivity activity;
    private File image;

    @Before public void setup()throws Exception {
        SharedPreferences prefs=inst.getTargetContext().getSharedPreferences("default",0);
        SharedPreferencesUtil.sharedPreferences=prefs;
        for(String key:new String[]{SharedPreferencesUtil.mid,SharedPreferencesUtil.cookies,SharedPreferencesUtil.csrf,
                "wbi_mixin_key","last_wbi",SharedPreferencesUtil.SNACKBAR_ENABLE})saved.put(key,prefs.getAll().get(key));
        prefs.edit().putLong(SharedPreferencesUtil.mid,42).putString(SharedPreferencesUtil.cookies,"DedeUserID=42; bili_jct=fixture")
                .putString(SharedPreferencesUtil.csrf,"fixture").putString("wbi_mixin_key","0123456789abcdef0123456789abcdef")
                .putInt("last_wbi",ConfInfoApi.getDateCurr()).putBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE,true).commit();
        NetWorkUtil.refreshHeaders();
        Field field=NetWorkUtil.class.getDeclaredField("INSTANCE");field.setAccessible(true);
        clients=(AtomicReference<OkHttpClient>)field.get(null);original=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            Request request=chain.request();String path=request.url().encodedPath();JSONObject response;
            try {
                if(path.endsWith("/reply/add")) {
                    posts.incrementAndGet();postStarted.countDown();
                    if(!postGate.await(8,TimeUnit.SECONDS))throw new IOException("fixture POST gate expired");
                    if(networkError)throw new IOException("fixture offline");
                    Buffer buffer=new Buffer();request.body().writeTo(buffer);
                    HttpUrl form=HttpUrl.parse("https://fixture.invalid/?"+buffer.readUtf8());
                    assertEquals(draft,form.queryParameter("message"));assertEquals(String.valueOf(oid),form.queryParameter("oid"));
                    assertEquals(String.valueOf(type),form.queryParameter("type"));
                    assertEquals(root==0?null:String.valueOf(root),form.queryParameter("root"));
                    assertEquals(root==0?null:String.valueOf(parent),form.queryParameter("parent"));
                    postedPictures=form.queryParameter("pictures");
                    response=new JSONObject().put("code",code).put("data",code==0&&!noReply?new JSONObject().put("reply",reply()):new JSONObject());
                    acknowledgedAt=SystemClock.elapsedRealtime();
                } else if(path.endsWith("/reply/wbi/main")) {
                    checks.incrementAndGet();verifyStarted.countDown();
                    if(!verifyGate.await(8,TimeUnit.SECONDS))throw new IOException("fixture verification gate expired");
                    response=new JSONObject().put("code",0).put("data",new JSONObject()
                            .put("cursor",new JSONObject().put("is_end",true)).put("replies",visible?new JSONArray().put(reply()):new JSONArray()));
                    verifyReturned.countDown();
                } else if(path.contains("upload_bfs")) {
                    uploads.incrementAndGet();
                    if(!uploadGate.await(8,TimeUnit.SECONDS))throw new IOException("fixture upload gate expired");
                    response=new JSONObject().put("code",0).put("data",new JSONObject().put("image_url","https://fixture.invalid/image.jpg")
                            .put("image_width",2).put("image_height",2));
                } else response=new JSONObject().put("code",-404).put("data",new JSONObject());
            } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
            catch(org.json.JSONException e){throw new IOException(e);}
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"),response.toString())).build();
        }).build());
        EventBus.getDefault().register(this);launch();
    }
    private void launch() {
        if(activity!=null){inst.runOnMainSync(activity::finish);inst.waitForIdleSync();}
        activity=(WriteReplyActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),WriteReplyActivity.class)
                .putExtra("oid",oid).putExtra("replyType",type).putExtra("rpid",root).putExtra("parent",parent)
                .putExtra("pos",3).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
        inst.runOnMainSync(()->((EditText)activity.findViewById(R.id.editText)).setText(draft));
    }
    @After public void cleanup()throws Exception {
        postGate.countDown();verifyGate.countDown();uploadGate.countDown();
        if(posts.get()>0&&code==0&&!networkError&&!noReply) {
            long until=SystemClock.uptimeMillis()+8000;
            while(!terminal()&&SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)==42&&SystemClock.uptimeMillis()<until)SystemClock.sleep(25);
        }
        inst.waitForIdleSync();
        if(activity!=null)inst.runOnMainSync(activity::finish);
        inst.waitForIdleSync();EventBus.getDefault().unregister(this);clients.set(original);
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> entry:saved.entrySet()) {
            Object v=entry.getValue();String k=entry.getKey();
            if(v==null)edit.remove(k);else if(v instanceof Long)edit.putLong(k,(Long)v);
            else if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);
            else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
        }
        edit.commit();NetWorkUtil.refreshHeaders();if(image!=null)image.delete();
    }
    @Subscribe(threadMode=ThreadMode.MAIN) public void onReply(ReplyEvent event){if(event.getOid()==oid)events.add(event);}
    @Subscribe(threadMode=ThreadMode.MAIN) public void onSnack(SnackEvent event){messages.add(event.getMessage());}

    @Test public void acceptedReplyClosesBeforeItsVisibilityCheckReturns()throws Exception {
        verifyGate=new CountDownLatch(1);send();assertTrue(verifyStarted.await(5,TimeUnit.SECONDS));
        await(()->activity.isFinishing(),1500);assertTrue(SystemClock.elapsedRealtime()-acknowledgedAt<1500);
        assertEquals(1,posts.get());assertEquals(0,events.size());assertEquals(1,verifyGate.getCount());
        verifyGate.countDown();await(()->events.size()==1,5000);assertEquals(rpid,events.get(0).getMessage().rpid);
    }
    @Test public void sendingStateAppearsImmediatelyAndRepeatedClicksCannotPostAgain()throws Exception {
        postGate=new CountDownLatch(1);send();
        inst.runOnMainSync(()->{
            assertEquals(activity.getString(R.string.reply_send_in_progress),((TextView)activity.findViewById(R.id.send_reply_label)).getText().toString());
            assertFalse(activity.findViewById(R.id.send).isEnabled());assertFalse(activity.findViewById(R.id.editText).isEnabled());
            assertFalse(activity.findViewById(R.id.pick_image).isEnabled());activity.findViewById(R.id.send).performClick();
        });
        assertTrue(postStarted.await(5,TimeUnit.SECONDS));assertEquals(1,posts.get());assertFalse(activity.isFinishing());
        postGate.countDown();await(()->events.size()==1,5000);assertEquals(1,posts.get());
    }
    @Test public void businessFailureRetainsTheDraftAndAllowsAnExplicitRetry()throws Exception {
        code=12016;send();await(()->activity.findViewById(R.id.send).isEnabled(),5000);
        assertFalse(activity.isFinishing());assertEquals(draft,((EditText)activity.findViewById(R.id.editText)).getText().toString());
        assertTrue(activity.findViewById(R.id.editText).isEnabled());assertEquals(0,checks.get());assertTrue(events.isEmpty());
        code=0;send();await(()->events.size()==1,5000);assertEquals(2,posts.get());
    }
    @Test public void networkFailureDoesNotRetryThePostOrLoseTheDraft()throws Exception {
        networkError=true;send();await(()->activity.findViewById(R.id.send).isEnabled(),5000);
        assertEquals(1,posts.get());assertTrue(events.isEmpty());assertEquals(0,checks.get());assertFalse(activity.isFinishing());
        assertEquals(draft,((EditText)activity.findViewById(R.id.editText)).getText().toString());
    }
    @Test public void acceptedButUnconfirmedReplyIsNeitherInsertedNorResubmitted()throws Exception {
        visible=false;send();await(()->activity.isFinishing(),2000);assertTrue(events.isEmpty());
        await(this::terminal,8000);assertTrue(events.isEmpty());assertEquals(1,posts.get());assertEquals(4,checks.get());
        assertTrue(messages.contains(inst.getTargetContext().getString(R.string.reply_send_pending)));
    }
    @Test public void dynamicChildReplyKeepsItsTargetAndDoesNotWaitForVisibility()throws Exception {
        type=17;root=73;parent=74;launch();verifyGate=new CountDownLatch(1);send();
        assertTrue(verifyStarted.await(5,TimeUnit.SECONDS));await(()->activity.isFinishing(),1500);assertTrue(events.isEmpty());
        verifyGate.countDown();await(()->events.size()==1,5000);
        assertEquals(root,events.get(0).getMessage().root);assertEquals(parent,events.get(0).getMessage().parent);
    }
    @Test public void accountChangeDiscardsTheLateConfirmation()throws Exception {
        verifyGate=new CountDownLatch(1);send();assertTrue(verifyStarted.await(5,TimeUnit.SECONDS));await(()->activity.isFinishing(),1500);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,43);verifyGate.countDown();assertTrue(verifyReturned.await(5,TimeUnit.SECONDS));
        SystemClock.sleep(400);inst.waitForIdleSync();assertTrue(events.isEmpty());
        assertFalse(messages.contains(inst.getTargetContext().getString(R.string.reply_send_verified)));
    }
    @Test public void emptyAcknowledgementRetainsTheDraftWithoutClaimingPublication()throws Exception {
        noReply=true;send();await(()->activity.findViewById(R.id.send).isEnabled(),5000);
        assertFalse(activity.isFinishing());assertTrue(events.isEmpty());assertEquals(0,checks.get());
        assertEquals(draft,((EditText)activity.findViewById(R.id.editText)).getText().toString());
        assertTrue(messages.contains(inst.getTargetContext().getString(R.string.reply_send_uncertain)));
    }
    @Test public void attachmentUploadFinishesBeforeOnePostWithTheFrozenImageList()throws Exception {
        image=new File(inst.getTargetContext().getCacheDir(),"reply-upload-fixture.png");
        Bitmap bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        try(FileOutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
        Field images=WriteReplyActivity.class.getDeclaredField("selectedImages");images.setAccessible(true);
        inst.runOnMainSync(()->{try{((ArrayList<Uri>)images.get(activity)).add(Uri.fromFile(image));}catch(Exception e){throw new AssertionError(e);}});
        uploadGate=new CountDownLatch(1);send();await(()->uploads.get()==1,5000);assertEquals(0,posts.get());assertFalse(activity.findViewById(R.id.send).isEnabled());
        uploadGate.countDown();await(()->events.size()==1,5000);assertEquals(1,posts.get());
        assertEquals("https://fixture.invalid/image.jpg",new JSONArray(postedPictures).getJSONObject(0).getString("img_src"));
    }
    private void send(){inst.runOnMainSync(()->activity.findViewById(R.id.send).performClick());}
    private boolean terminal(){return messages.contains(inst.getTargetContext().getString(R.string.reply_send_verified))
            ||messages.contains(inst.getTargetContext().getString(R.string.reply_send_pending));}
    private interface Check{boolean get();}
    private void await(Check check,long timeout)throws Exception {
        long end=SystemClock.uptimeMillis()+timeout;boolean[] ready={false};
        do{inst.runOnMainSync(()->ready[0]=check.get());if(ready[0])return;SystemClock.sleep(25);}while(SystemClock.uptimeMillis()<end);
        fail("Timed out; POSTs="+posts+", visibility checks="+checks+", events="+events.size()+", closing="+activity.isFinishing());
    }
    private JSONObject reply()throws org.json.JSONException {
        return new JSONObject().put("rpid",rpid).put("oid",oid).put("root",root).put("parent",parent).put("like",0).put("action",0).put("ctime",1).put("rcount",0)
                .put("member",new JSONObject().put("mid",42).put("uname","Fixture").put("avatar","").put("level_info",new JSONObject().put("current_level",1))
                        .put("is_senior_member",0).put("vip",new JSONObject().put("vipStatus",0).put("nickname_color","")))
                .put("content",new JSONObject().put("message",draft)).put("reply_control",new JSONObject())
                .put("up_action",new JSONObject().put("like",false).put("reply",false));
    }
}
