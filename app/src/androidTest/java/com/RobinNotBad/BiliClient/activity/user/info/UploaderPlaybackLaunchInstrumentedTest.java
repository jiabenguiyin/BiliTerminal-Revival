package com.RobinNotBad.BiliClient.activity.user.info;

import android.app.*;
import android.content.Intent;
import android.os.SystemClock;
import android.view.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.UIPreviewActivity;
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity;
import com.RobinNotBad.BiliClient.api.*;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.player.PlayerOptionsMenu;
import com.RobinNotBad.BiliClient.util.*;
import okhttp3.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UploaderPlaybackLaunchInstrumentedTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private UIPreviewActivity host;private Activity jump;private UserVideoFragment fragment;
    private final Map<String,Object> old=new HashMap<>();
    private AtomicReference<OkHttpClient> clients;private OkHttpClient previous;
    private final List<Integer> pages=Collections.synchronizedList(new ArrayList<>());
    private volatile CountDownLatch gate;private final AtomicInteger factories=new AtomicInteger();
    private final List<String> orders=Collections.synchronizedList(new ArrayList<>());
    private volatile String blockedOrder;
    private final CountDownLatch sortStarted=new CountDownLatch(1),releaseSort=new CountDownLatch(1),sortFinished=new CountDownLatch(1);
    @Before public void before()throws Exception{
        for(String key:new String[]{SharedPreferencesUtil.mid,"wbi_mixin_key","last_wbi","dpi","density"})old.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,42);SharedPreferencesUtil.putFloat("dpi",1f);SharedPreferencesUtil.putInt("density",-1);
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);previous=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            try{
                HttpUrl url=chain.request().url();JSONObject data=new JSONObject();int code=-404;
                if(url.encodedPath().endsWith("/nav")){code=0;data.put("wbi_img",new JSONObject()
                        .put("img_url","https://example.invalid/0123456789abcdef0123456789abcdef.png")
                        .put("sub_url","https://example.invalid/fedcba9876543210fedcba9876543210.png"));}
                if(url.encodedPath().endsWith("/arc/search")){
                    code=0;assertEquals("99",url.queryParameter("mid"));String order=url.queryParameter("order");
                    assertTrue(Arrays.asList("pubdate","click","stow").contains(order));orders.add(order);
                    assertNotNull(url.queryParameter("w_rid"));assertNotNull(url.queryParameter("wts"));
                    if(order.equals(blockedOrder)) {sortStarted.countDown();try{assertTrue(releaseSort.await(12,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IOException(e);}sortFinished.countDown();}
                    int page=Integer.parseInt(url.queryParameter("pn"));pages.add(page);JSONArray list=new JSONArray();
                    if(page==1)list.put(new JSONObject().put("aid",7003).put("title","最新投稿"));
                    else if(page==2)list.put(new JSONObject().put("aid",7002).put("title","较早投稿"));
                    else if(page==3)list.put(new JSONObject().put("aid",7001).put("title","最早投稿"));
                    if(!"pubdate".equals(order))list=new JSONArray().put(new JSONObject().put("aid",("click".equals(order)?7100:7200)+page).put("title",order+"投稿"+page));
                    data.put("list",new JSONObject().put("vlist",list)).put("page",new JSONObject().put("pn",page).put("ps",1).put("count",3));
                }
                String body=new JSONObject().put("code",code).put("data",data).toString();
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"),body)).build();
            }catch(JSONException e){throw new IOException(e);}
        }).build());
        host=(UIPreviewActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),UIPreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(()->{
            host.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            fragment=UserVideoFragment.newInstance(99);
            host.getSupportFragmentManager().beginTransaction().replace(android.R.id.content,fragment).commitNow();
            fragment.requireView().setBackgroundColor(android.graphics.Color.BLACK);
            set("uploaderPlaybackSource",new UploaderPlaybackSource(){
                @Override protected PlayerData loadItem(long aid)throws Exception{
                    factories.incrementAndGet();if(gate!=null)assertTrue(gate.await(12,TimeUnit.SECONDS));
                    PlayerData p=new PlayerData();p.aid=aid;p.cid=aid+10;p.title="投稿测试";
                    p.cids=new ArrayList<>(Arrays.asList(p.cid));p.pagenames=new ArrayList<>(Arrays.asList("视频"));return p;
                }
            });
        });
        await(()->!fragment.isRefreshing()&&fragment.recyclerView.getAdapter().getItemCount()==3);
    }
    @After public void after(){
        releaseSort.countDown();
        if(gate!=null)gate.countDown();main(()->{if(jump!=null)jump.finish();if(host!=null)host.finish();});inst.waitForIdleSync();clients.set(previous);
        for(Map.Entry<String,Object> e:old.entrySet()){
            if(e.getValue()==null)SharedPreferencesUtil.removeValue(e.getKey());
            else if(e.getValue() instanceof Long)SharedPreferencesUtil.putLong(e.getKey(),(Long)e.getValue());
            else if(e.getValue() instanceof Integer)SharedPreferencesUtil.putInt(e.getKey(),(Integer)e.getValue());
            else if(e.getValue() instanceof Float)SharedPreferencesUtil.putFloat(e.getKey(),(Float)e.getValue());
            else SharedPreferencesUtil.putString(e.getKey(),(String)e.getValue());
        }
    }
    @Test public void uploadHeaderKeepsSeriesEntryAndOpensTwoChronologicalChoices()throws Exception{
        main(()->{
            RecyclerView.Adapter adapter=fragment.recyclerView.getAdapter();
            RecyclerView.ViewHolder series=adapter.createViewHolder(fragment.recyclerView,adapter.getItemViewType(0));adapter.bindViewHolder(series,0);
            assertEquals("视频系列",((android.widget.TextView)series.itemView.findViewById(R.id.text)).getText());
            RecyclerView.ViewHolder action=adapter.createViewHolder(fragment.recyclerView,adapter.getItemViewType(1));adapter.bindViewHolder(action,1);
            assertEquals("投稿连播",((android.widget.TextView)action.itemView.findViewById(R.id.text)).getText());action.itemView.performClick();
            PlayerOptionsMenu menu=(PlayerOptionsMenu)get("uploadOrderMenu");assertTrue(menu.isShowing());
            assertEquals(2,menu.getMenu().size());assertEquals("正序：旧 → 新",menu.getMenu().findItem(1).getTitle());
            assertEquals("倒序：新 → 旧",menu.getMenu().findItem(2).getTitle());
        });
        await(()->((PlayerOptionsMenu)get("uploadOrderMenu")).getDialog().getWindow().getDecorView().getWidth()>0);
        main(()->{
            View decor=((PlayerOptionsMenu)get("uploadOrderMenu")).getDialog().getWindow().getDecorView();
            assertTrue(decor.getWidth()<=host.findViewById(android.R.id.content).getWidth());
            assertTrue(decor.getHeight()<=host.findViewById(android.R.id.content).getHeight());
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            decor.draw(new android.graphics.Canvas(image));
            try(FileOutputStream out=new FileOutputStream(new File(host.getFilesDir(),"uploader-order.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            catch(IOException e){throw new AssertionError(e);}finally{image.recycle();}
        });
    }
    @Test public void oldestChoiceUsesRealSignedPagingAndCarriesPrivateUploadTokenIntoJump()throws Exception{
        launch(true,7001);assertTrue(pages.contains(3));assertFalse(pages.contains(2));
    }
    @Test public void newestChoiceDoesNotFetchArchiveBeforeStarting()throws Exception{
        launch(false,7003);assertFalse(pages.contains(2));assertFalse(pages.contains(3));
    }
    @Test public void leavingPageWhilePreparingCancelsLateLaunch()throws Exception{
        gate=new CountDownLatch(1);Instrumentation.ActivityMonitor monitor=inst.addMonitor(JumpToPlayerActivity.class.getName(),null,false);
        try{
            main(()->invoke("startUploadPlayback",new Class[]{boolean.class},new Object[]{false}));await(()->factories.get()==1);
            main(()->host.getSupportFragmentManager().beginTransaction().remove(fragment).commitNow());gate.countDown();
            assertNull(inst.waitForMonitorWithTimeout(monitor,1200));
        }finally{inst.removeMonitor(monitor);}
    }

    @Test public void sortSharesTheSeriesHeaderAndLoadsAllFourRealPageOrders()throws Exception{
        main(()->{
            RecyclerView.Adapter adapter=fragment.recyclerView.getAdapter();
            RecyclerView.ViewHolder header=adapter.createViewHolder(fragment.recyclerView,adapter.getItemViewType(0));adapter.bindViewHolder(header,0);
            assertEquals("视频系列",((android.widget.TextView)header.itemView.findViewById(R.id.text)).getText());
            assertEquals("最新发布",((android.widget.TextView)header.itemView.findViewById(R.id.user_video_sort)).getText());
            header.itemView.findViewById(R.id.user_video_sort).performClick();
            PlayerOptionsMenu menu=(PlayerOptionsMenu)get("sortMenu");assertTrue(menu.isShowing());assertEquals(4,menu.getMenu().size());menu.dismiss();
        });
        for(int mode:new int[]{1,2,3,0}) {
            chooseSort(mode);long expected=mode==1?7101:mode==2?7201:mode==3?7001:7003;
            await(()->!fragment.isRefreshing()&&firstAid()==expected);
            assertEquals(mode,((Integer)get("listSort")).intValue());
        }
        assertTrue(orders.contains("click"));assertTrue(orders.contains("stow"));
        chooseSort(3);await(()->!fragment.isRefreshing()&&firstAid()==7001);
        main(()->fragment.listener.onLoad(2));await(()->!fragment.isRefreshing()&&((ArrayList<?>)get("videoList")).size()==2);
        main(()->assertEquals(7002,((ArrayList<VideoCard>)get("videoList")).get(1).aid));
        main(()->saveUploadHeader("upload-sort-header.png"));
    }

    @Test public void anOldSortResponseCannotAppendIntoANewerSort()throws Exception{
        blockedOrder="click";chooseSort(1);assertTrue(sortStarted.await(5,TimeUnit.SECONDS));
        chooseSort(2);await(()->!fragment.isRefreshing()&&firstAid()==7201);
        releaseSort.countDown();assertTrue(sortFinished.await(5,TimeUnit.SECONDS));SystemClock.sleep(150);inst.waitForIdleSync();
        main(()->{assertEquals(7201,firstAid());assertEquals(1,((ArrayList<?>)get("videoList")).size());});
    }

    @Test public void localSearchKeepsTheSelectedServerOrderAndResetsItsCursorWhenCleared()throws Exception{
        chooseSort(2);await(()->!fragment.isRefreshing()&&firstAid()==7201);
        main(()->fragment.search("投稿"));await(()->!fragment.isRefreshing()&&((ArrayList<?>)get("videoList")).size()==3);
        main(()->{ArrayList<VideoCard> list=(ArrayList<VideoCard>)get("videoList");assertEquals(7201,list.get(0).aid);assertEquals(7203,list.get(2).aid);fragment.search("");});
        await(()->!fragment.isRefreshing()&&((ArrayList<?>)get("videoList")).size()==1&&firstAid()==7201);
    }
    private long firstAid(){ArrayList<VideoCard> list=(ArrayList<VideoCard>)get("videoList");return list.isEmpty()?0:list.get(0).aid;}
    private void chooseSort(int mode){main(()->{invoke("showListSort",new Class[0],new Object[0]);((PlayerOptionsMenu)get("sortMenu")).getMenu().performIdentifierAction(mode+100,0);});}
    private void saveUploadHeader(String filename){
        View root=host.findViewById(android.R.id.content);android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(b));
        try(FileOutputStream out=host.openFileOutput(filename,0)){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new AssertionError(e);}finally{b.recycle();}
    }
    private void launch(boolean oldest,long aid)throws Exception{
        Instrumentation.ActivityMonitor monitor=inst.addMonitor(JumpToPlayerActivity.class.getName(),null,false);
        try{
            main(()->{invoke("showUploadPlaybackOrder",new Class[0],new Object[0]);
                ((PlayerOptionsMenu)get("uploadOrderMenu")).getMenu().performIdentifierAction(oldest?1:2,0);});
            jump=inst.waitForMonitorWithTimeout(monitor,10000);assertNotNull(jump);
            PlayerData p=jump.getIntent().getParcelableExtra("data");assertEquals(aid,p.aid);assertTrue(p.explicitOnlineQueue);
            FavoritePlaybackSource.Session session=FavoritePlaybackSource.read(inst.getTargetContext(),p.favoritePlaybackToken,42);
            assertTrue(session.isUploads());assertEquals(99,session.owner);assertEquals(oldest,session.uploadOldest);
            assertEquals(aid,session.ids.get(0).longValue());
        }finally{inst.removeMonitor(monitor);}
    }
    private interface Check{boolean ok();}
    private void await(Check c)throws Exception{long end=SystemClock.uptimeMillis()+10000;boolean[] ok={false};
        do{main(()->ok[0]=c.ok());if(ok[0])return;SystemClock.sleep(40);}while(SystemClock.uptimeMillis()<end);throw new AssertionError("Timed out");}
    private Object get(String n){try{Field f=UserVideoFragment.class.getDeclaredField(n);f.setAccessible(true);return f.get(fragment);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String n,Object v){try{Field f=UserVideoFragment.class.getDeclaredField(n);f.setAccessible(true);f.set(fragment,v);}catch(Exception e){throw new AssertionError(e);}}
    private void invoke(String n,Class[] types,Object[] args){try{Method m=UserVideoFragment.class.getDeclaredMethod(n,types);m.setAccessible(true);m.invoke(fragment,args);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable r){inst.runOnMainSync(r);}
}
