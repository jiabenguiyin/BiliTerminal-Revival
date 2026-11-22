package com.RobinNotBad.BiliClient.api;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.os.SystemClock;
import android.util.Pair;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.activity.settings.login.SpecialLoginActivity;
import com.RobinNotBad.BiliClient.activity.message.PrivateMsgActivity;
import com.RobinNotBad.BiliClient.activity.message.NoticeActivity;
import com.RobinNotBad.BiliClient.adapter.message.*;
import com.RobinNotBad.BiliClient.helper.sql.DownloadSqlHelper;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.service.DownloadService;
import com.RobinNotBad.BiliClient.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.Deflater;
import okhttp3.*;
import static org.junit.Assert.*;

/** All HTTP is intercepted. No real credentials or remote writes are used. */
@RunWith(AndroidJUnit4.class)
public class CommunityFeedbackInstrumentedTest {
    private final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    private final Context context=ui.getTargetContext();
    private final List<Activity> activities=new ArrayList<>();
    private final ConcurrentLinkedQueue<Request> requests=new ConcurrentLinkedQueue<>();
    private final AtomicInteger sends=new AtomicInteger(),mediaReads=new AtomicInteger();
    private Map<String,?> saved;
    private AtomicReference<OkHttpClient> clients;private OkHttpClient previous;
    private int navCode,sendCode,pgcCode;
    private long navMid=7;
    private boolean legacyOnly,preview;
    private File root;
    private byte[] media;
    private static final long EP=99616001,AID=99616002,CID=99616003;
    private static final String OLD="DedeUserID=42; SESSDATA=old-fixture; bili_jct=old-csrf";
    private static final String NEW="DedeUserID=7; SESSDATA=new%2Cfixture+==; bili_jct=new-csrf";
    @Before public void setup()throws Exception {
        assertFalse(DownloadService.started);
        saved=new HashMap<>(SharedPreferencesUtil.getSharedPreferences().getAll());
        assertTrue(AccountManager.saveLogin(OLD,"old-token"));
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PRIVATE_MSG_AUTO_READ_ENABLE,false);
        root=new File(context.getCacheDir(),"feedback-"+System.nanoTime());assertTrue(root.mkdir());
        VideoStorageUtil.selectFileRoot(root);
        try(InputStream input=ui.getContext().getAssets().open("collection-playback.mp4");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);media=out.toByteArray();
        }
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);previous=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            Request request=chain.request();requests.add(request);String path=request.url().encodedPath();
            try{
                String json="{\"code\":0,\"data\":{}}";
                if(path.endsWith("/nav"))json=new JSONObject().put("code",navCode).put("data",new JSONObject().put("isLogin",navCode==0).put("mid",navMid)).toString();
                else if(path.endsWith("/x/msgfeed/like")){
                    boolean more=request.url().queryParameter("id")!=null;
                    JSONObject latest=new JSONObject().put("items",new JSONArray().put(like(20,200)));
                    JSONObject total=new JSONObject().put("items",new JSONArray().put(like(more?5:10,more?50:100)))
                            .put("cursor",new JSONObject().put("id",more?5:10).put("time",more?50:100).put("is_end",more));
                    json=new JSONObject().put("code",0).put("data",new JSONObject().put("latest",latest).put("total",total)).toString();
                }else if(path.endsWith("/fetch_session_msgs"))json="{\"code\":0,\"data\":{\"messages\":null,\"has_more\":0,\"e_infos\":[]}}";
                else if(path.endsWith("/send_msg")){sends.incrementAndGet();json="{\"code\":"+sendCode+",\"message\":\"fixture\",\"data\":{}}";}
                else if(path.contains("/pgc/player/")){
                    assertEquals(String.valueOf(EP),request.url().queryParameter("ep_id"));
                    assertEquals("1",request.url().queryParameter("download"));
                    JSONObject info=new JSONObject().put("quality",32).put("is_preview",preview?1:0)
                            .put("accept_quality",new JSONArray().put(32)).put("accept_description",new JSONArray().put("480P"));
                    if(!(legacyOnly&&path.contains("/v2/")))info.put("durl",new JSONArray().put(new JSONObject().put("url","https://fixture.invalid/episode.mp4")));
                    json=new JSONObject().put("code",pgcCode).put("result",path.contains("/v2/")?new JSONObject().put("video_info",info):info).toString();
                }else if(path.endsWith("/episode.mp4")){mediaReads.incrementAndGet();return response(request,media,"video/mp4");}
                else if(path.endsWith(".xml")){
                    Deflater deflater=new Deflater(6,true);deflater.setInput("<i/>".getBytes("UTF-8"));deflater.finish();byte[] buffer=new byte[64];int n=deflater.deflate(buffer);deflater.end();
                    return response(request,Arrays.copyOf(buffer,n),"application/octet-stream");
                }else if(path.endsWith("/v2"))json="{\"code\":0,\"data\":{\"subtitle\":{\"subtitles\":[]}}}";
                return response(request,json.getBytes("UTF-8"),"application/json");
            }catch(JSONException error){throw new IOException(error);}
        }).build());
    }
    private Response response(Request request,byte[] body,String type){return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(200).message("fixture").body(ResponseBody.create(MediaType.parse(type),body)).build();}
    private JSONObject like(long id,long time)throws JSONException {
        return new JSONObject().put("id",id).put("like_time",time).put("counts",1).put("users",new JSONArray().put(new JSONObject()
                .put("mid",7).put("nickname","测试用户").put("avatar","").put("fans",0).put("follow",false)))
                .put("item",new JSONObject().put("business_id",1).put("item_id",123).put("type","video").put("uri","https://www.bilibili.com/video/BV1Fixture01")
                        .put("title","测试视频").put("image",""));
    }
    @After public void cleanup()throws Exception {
        if(DownloadService.started){DownloadService.pause();await(()->!DownloadService.started);}
        ui.runOnMainSync(()->{for(Activity activity:activities)activity.finish();});ui.waitForIdleSync();
        try(DownloadSqlHelper helper=new DownloadSqlHelper(context)){helper.getWritableDatabase().delete("download","aid=?",new String[]{String.valueOf(AID)});}
        clients.set(previous);
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit().clear();
        for(Map.Entry<String,?> e:saved.entrySet()){
            Object value=e.getValue();String key=e.getKey();
            if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Long)edit.putLong(key,(Long)value);
            else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);
            else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Set)edit.putStringSet(key,(Set<String>)value);
        }edit.commit();NetWorkUtil.refreshHeaders();
    }
    private <T extends Activity>T launch(Class<T> type,Intent intent){T activity=(T)ui.startActivitySync(intent.setClass(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activities.add(activity);return activity;}
    private void await(Callable<Boolean> predicate)throws Exception {long until=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<until){if(predicate.call())return;SystemClock.sleep(40);}fail("Fixture state did not settle");}
    private boolean bool(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.getBoolean(owner);}
    @Test public void rejectedCookieImportDoesNotOverwriteTheActiveAccount()throws Exception {
        navCode=-101;SpecialLoginActivity page=launch(SpecialLoginActivity.class,new Intent().putExtra("login",true));
        ui.runOnMainSync(()->{((EditText)page.findViewById(R.id.loginInput)).setText(NEW);page.findViewById(R.id.confirm).performClick();});
        await(()->!bool(page,"busy"));assertEquals(42,SharedPreferencesUtil.getLong("mid",0));
        assertTrue(AccountManager.sameSession(OLD,SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"")));assertFalse(page.isFinishing());
    }
    @Test public void verifiedCookieImportUsesCandidateHeadersAndRestoresCsrf()throws Exception {
        Instrumentation.ActivityMonitor monitor=ui.addMonitor(com.RobinNotBad.BiliClient.activity.SplashActivity.class.getName(),new Instrumentation.ActivityResult(0,null),true);
        try{
            SpecialLoginActivity page=launch(SpecialLoginActivity.class,new Intent().putExtra("login",true));
            ui.runOnMainSync(()->{((EditText)page.findViewById(R.id.loginInput)).setText(NEW);page.findViewById(R.id.confirm).performClick();});
            await(()->SharedPreferencesUtil.getLong("mid",0)==7);assertEquals("new-csrf",SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf,""));
            assertTrue(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"").contains("new%2Cfixture+=="));
            boolean candidateUsed=false;for(Request request:requests)if(request.url().encodedPath().endsWith("/nav")
                    &&request.header("Cookie").contains("new%2Cfixture+=="))candidateUsed=true;assertTrue(candidateUsed);
        }finally{ui.removeMonitor(monitor);}
    }
    @Test public void importingAnotherSessionOfTheSameUidDoesNotReuseTheOldRefreshToken() {
        assertTrue(AccountManager.saveLogin("DedeUserID=42; SESSDATA=replaced; bili_jct=replaced-csrf",""));
        assertEquals("",SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token,""));
        assertTrue(AccountManager.saveLogin("DedeUserID=42; SESSDATA=replaced; bili_jct=replaced-csrf","new-token"));
        assertTrue(AccountManager.saveLogin("DedeUserID=42; SESSDATA=replaced; bili_jct=replaced-csrf",""));
        assertEquals("new-token",SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token,""));
    }
    @Test public void likesHaveRealLatestAndHistorySectionsWithoutDuplicatePagination()throws Exception {
        Pair<MessageCard.Cursor,List<MessageCard>> first=MessageApi.getLikeMsg(0,0),more=MessageApi.getLikeMsg(10,100);
        assertEquals(2,first.second.size());assertTrue(first.second.get(0).isNew);assertFalse(first.second.get(1).isNew);
        assertEquals(10,first.first.id);assertEquals(1,more.second.size());assertEquals(5,more.second.get(0).id);
        Request paged=null;for(Request request:requests)if("10".equals(request.url().queryParameter("id")))paged=request;
        assertNotNull(paged);assertEquals("100",paged.url().queryParameter("like_time"));assertNull(paged.url().queryParameter("reply_time"));
        TestActivity host=launch(TestActivity.class,new Intent());
        ui.runOnMainSync(()->{
            LinearLayout content=new LinearLayout(host);content.setOrientation(LinearLayout.VERTICAL);
            ScrollView scroll=new ScrollView(host);scroll.addView(content);host.setContentView(scroll);
            NoticeAdapter adapter=new NoticeAdapter(host,first.second);
            for(int i=0;i<2;i++){
                NoticeHolder holder=adapter.onCreateViewHolder(new FrameLayout(host),0);adapter.onBindViewHolder(holder,i);content.addView(holder.itemView);
                assertTrue(i==0?holder.noticeCard.getStrokeWidth()>0:holder.noticeCard.getStrokeWidth()==0);
                assertEquals(host.getString(i==0?R.string.message_latest_section:R.string.message_history_section),((TextView)holder.itemView.findViewById(R.id.notice_section)).getText().toString());
            }
        });
        capture(host,"notice-read-sections.png");assertEquals(0,sends.get());
    }
    @Test public void thanksDraftAndFailedSendDoNotMarkThanksButSuccessfulSendDoes()throws Exception {
        PrivateMsgActivity chat=launch(PrivateMsgActivity.class,new Intent().putExtra("uid",7L).putExtra("draft","谢谢你的点赞")
                .putExtra("thanks_account",42L).putExtra("thanks_notice_id",9001L));
        assertFalse(MessageThanksState.sent(42,9001,7));assertEquals(0,sends.get());
        sendCode=-403;ui.runOnMainSync(()->chat.findViewById(R.id.send_btn).performClick());await(()->!bool(chat,"sending"));
        assertEquals(1,sends.get());assertFalse(MessageThanksState.sent(42,9001,7));
        sendCode=0;ui.runOnMainSync(()->chat.findViewById(R.id.send_btn).performClick());await(()->!bool(chat,"sending"));
        assertTrue(MessageThanksState.sent(42,9001,7));assertFalse(MessageThanksState.sent(43,9001,7));assertEquals(2,sends.get());
        ui.runOnMainSync(()->{
            MessageCard card=new MessageCard();card.id=9001;card.getType=MessageCard.GET_TYPE_LIKE;card.itemType="video";
            card.user=Arrays.asList(new UserInfo(7,"测试用户","","",0,0,0,false,"",0,"",0));
            NoticeHolder holder=new NoticeAdapter(chat,Arrays.asList(card)).onCreateViewHolder(new FrameLayout(chat),0);
            holder.showMessage(card,chat);assertFalse(holder.likeThanks.isEnabled());
            assertEquals(chat.getString(R.string.message_like_thanked),((TextView)holder.likeThanks).getText().toString());
        });
    }
    @Test public void actualNoticePagePaginatesWithoutRepeatingLatestMessages()throws Exception {
        NoticeActivity page=launch(NoticeActivity.class,new Intent().putExtra("type","like"));
        await(()->!bool(page,"loadingFirstPage"));
        ui.runOnMainSync(()->{
            try{Method load=NoticeActivity.class.getDeclaredMethod("loadPage",boolean.class);load.setAccessible(true);
                for(int i=0;i<4;i++)load.invoke(page,false);
            }catch(Exception error){throw new AssertionError(error);}
        });
        await(()->!bool(page,"loadingMore"));
        ui.runOnMainSync(()->{
            RecyclerView list=page.findViewById(R.id.recyclerView);assertEquals(3,list.getAdapter().getItemCount());
        });
        int reads=0;for(Request request:requests)if(request.url().encodedPath().endsWith("/x/msgfeed/like"))reads++;
        assertEquals(2,reads);capture(page,"actual-notice-sections.png");
    }
    private PlayerData episode(){PlayerData data=new PlayerData(PlayerData.TYPE_BANGUMI);data.aid=AID;data.cid=CID;data.episodeId=EP;data.seasonId=99616;data.seasonType=1;data.mid=42;data.qn=32;return data;}
    @Test public void pgcCompatibilityFallbackKeepsEpisodeAndDoesNotBypassDenials()throws Exception {
        legacyOnly=true;PlayerData data=episode();BangumiDownloadApi.resolve(data);
        assertEquals("https://fixture.invalid/episode.mp4",data.videoUrl);
        assertEquals(2,requests.size());requests.clear();pgcCode=-10403;
        try{BangumiDownloadApi.resolve(episode());fail();}catch(JSONException expected){}
        assertEquals(1,requests.size());assertEquals(0,mediaReads.get());
    }
    @Test public void bangumiQueuePersistsIdentityAndProducesAnOfflinePlayableFile()throws Exception {
        assertTrue(DownloadService.queueBangumi(episode(),"测试番剧","第1集","https://fixture.invalid/cover.png"));
        assertFalse(DownloadService.queueBangumi(episode(),"测试番剧","第1集","https://fixture.invalid/cover.png"));
        try(DownloadSqlHelper helper=new DownloadSqlHelper(context);Cursor cursor=helper.getReadableDatabase().rawQuery("select * from download where aid=?",new String[]{String.valueOf(AID)})){
            assertTrue(cursor.moveToFirst());DownloadSection row=new DownloadSection(cursor);assertEquals(EP,row.episodeId);
            assertEquals(42,row.accountMid);assertEquals(EP,row.toPlayerData().episodeId);assertEquals(PlayerData.TYPE_BANGUMI,row.toPlayerData().type);
        }
        DownloadService.start(-1);
        File file=new File(root,"测试番剧/第1集/video.mp4");
        await(()->file.isFile()&&file.length()==media.length&&!DownloadService.started);
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try{retriever.setDataSource(file.getAbsolutePath());assertTrue(Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))>0);}finally{retriever.release();}
        assertEquals(1,mediaReads.get());for(Request request:requests)assertFalse(request.url().encodedPath().contains("/x/player/")&&request.url().encodedPath().contains("playurl"));
    }
    private void capture(Activity host,String filename){ui.waitForIdleSync();ui.runOnMainSync(()->{
        View root=host.getWindow().getDecorView();Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        try(FileOutputStream output=new FileOutputStream(new File(host.getFilesDir(),filename))){root.draw(new Canvas(image));image.compress(Bitmap.CompressFormat.PNG,100,output);}
        catch(Exception error){throw new AssertionError(error);}finally{image.recycle();}
    });}
}
