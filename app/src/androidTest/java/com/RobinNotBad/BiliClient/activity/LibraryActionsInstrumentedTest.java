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
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.activity.user.HistoryActivity;
import com.RobinNotBad.BiliClient.activity.user.favorite.*;
import com.RobinNotBad.BiliClient.api.*;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.ui.*;
import com.RobinNotBad.BiliClient.util.*;
import okhttp3.*;
import okio.Buffer;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** All requests are intercepted; tests cannot delete or report live content. */
@RunWith(AndroidJUnit4.class)
public class LibraryActionsInstrumentedTest {
    private final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    private final ArrayList<Activity> owners=new ArrayList<>();private final ArrayList<VideoReportController> controllers=new ArrayList<>();
    private final ConcurrentLinkedQueue<Request> requests=new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<HttpUrl> historyPosts=new ConcurrentLinkedQueue<>(),reportPosts=new ConcurrentLinkedQueue<>();
    private final Set<Long> removed=Collections.newSetFromMap(new ConcurrentHashMap<Long,Boolean>());
    private final Map<String,Object> saved=new HashMap<>();private AtomicReference<OkHttpClient> clients;private OkHttpClient oldClient;
    private HistoryActivity history;private VideoReportController report;private TestActivity host;
    private volatile int historyCode,reportCode;private volatile boolean offline;private volatile CountDownLatch gate;
    private String reasonJson;private List<VideoReportApi.Reason> reasons;
    private static final String[] KEYS={"mid","cookies","csrf","dpi","density","paddingH_percent","paddingV_percent","player_ui_round","favorite_video_order_42_10042"};

    @Before public void setup()throws Exception{
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);oldClient=clients.get();
        SharedPreferences prefs=SharedPreferencesUtil.getSharedPreferences();for(String k:KEYS)saved.put(k,prefs.getAll().get(k));
        prefs.edit().putLong("mid",42).putString("cookies","DedeUserID=42; SESSDATA=fixture-session; bili_jct=current-csrf")
                .putString("csrf","stale-csrf").putFloat("dpi",1f).putInt("density",-1).putInt("paddingH_percent",0).putInt("paddingV_percent",0)
                .putBoolean("player_ui_round",false).remove("favorite_video_order_42_10042").commit();
        try(InputStream in=ui.getContext().getAssets().open("video-report-tags.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[]b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);reasonJson=out.toString("UTF-8");}
        reasons=VideoReportApi.parseReasons(new JSONObject(reasonJson));
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            Request r=chain.request();requests.add(r);String path=r.url().encodedPath();JSONObject json=new JSONObject();
            try{
                if(path.endsWith("/history/delete")||path.endsWith("/appeal/v2/submit")){
                    Buffer body=new Buffer();r.body().writeTo(body);HttpUrl form=HttpUrl.parse("https://fixture.invalid/?"+body.readUtf8());
                    boolean history=path.endsWith("/history/delete");if(history)historyPosts.add(form);else reportPosts.add(form);
                    CountDownLatch held=gate;if(held!=null)held.await(6,TimeUnit.SECONDS);
                    if(offline)throw new IOException("fixture offline");int code=history?historyCode:reportCode;
                    if(history&&code==0)for(String key:form.queryParameter("kid").split(","))removed.add(Long.parseLong(key.substring("archive_".length())));
                    json.put("code",code).put("message","fixture");
                }else if(path.endsWith("/appeal/tags"))json=new JSONObject(reasonJson);
                else if(path.endsWith("/history/cursor")||path.endsWith("/history/search")){
                    JSONArray rows=new JSONArray();for(long id:new long[]{11,12,13})if(!removed.contains(id))rows.put(new JSONObject().put("title","历史 "+id).put("cover","").put("author_name","UP").put("progress",30)
                            .put("history",new JSONObject().put("oid",id).put("bvid","BV"+id).put("business","archive")));
                    json.put("code",0).put("data",new JSONObject().put("list",rows).put("has_more",false));
                }else if(path.endsWith("/fav/folder/created/list"))json.put("code",0).put("data",new JSONObject().put("count",1).put("has_more",false)
                        .put("list",new JSONArray().put(new JSONObject().put("id",10042).put("fid",100).put("mid",42).put("title","测试收藏夹").put("media_count",2))));
                else if(path.endsWith("/fav/resource/list")){
                    long first="view".equals(r.url().queryParameter("order"))?2:1;
                    JSONArray rows=new JSONArray();for(long id:new long[]{first,first==1?2:1})rows.put(new JSONObject().put("id",id).put("type",2).put("title","收藏 "+id).put("cover","").put("bvid","BV"+id).put("upper",new JSONObject().put("name","UP")));
                    json.put("code",0).put("data",new JSONObject().put("medias",rows).put("has_more",false));
                }else json.put("code",-404);
            }catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}
            return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(ResponseBody.create(MediaType.parse("application/json"),json.toString())).build();
        }).build());
    }
    @After public void cleanup()throws Exception{
        if(gate!=null)gate.countDown();await(()->(history==null||!bool(history,"deleting"))&&(report==null||!bool(report,"busy")));
        ui.runOnMainSync(()->{for(VideoReportController controller:controllers)controller.close();for(Activity owner:owners)owner.finish();});ui.waitForIdleSync();
        SharedPreferences.Editor e=SharedPreferencesUtil.getSharedPreferences().edit();for(String k:KEYS){Object v=saved.get(k);if(v==null)e.remove(k);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);
            else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof String)e.putString(k,(String)v);else e.putBoolean(k,(Boolean)v);}e.commit();clients.set(oldClient);
    }
    private <T extends Activity>T open(Class<T> type,Intent extra)throws Exception{
        Instrumentation.ActivityMonitor monitor=ui.addMonitor(type.getName(),null,false);
        try{ui.runOnMainSync(()->{Intent intent=new Intent(ui.getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(extra!=null)intent.putExtras(extra);ui.getTargetContext().startActivity(intent);});
            T owner=type.cast(monitor.waitForActivityWithTimeout(7000));assertNotNull(owner);owners.add(owner);return owner;
        }finally{ui.removeMonitor(monitor);}
    }
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new AssertionError(e);}}
    private boolean bool(Object owner,String name){return (Boolean)field(owner,name);}
    private void await(Callable<Boolean> predicate){long until=SystemClock.uptimeMillis()+8000;AtomicBoolean done=new AtomicBoolean();do{ui.runOnMainSync(()->{try{done.set(predicate.call());}catch(Exception e){throw new AssertionError(e);}});if(done.get())return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<until);fail("Timed out waiting for library fixture");}
    private AlertDialog historyDialog(){return (AlertDialog)field(history,"activeDialog");}
    private void openHistory()throws Exception{history=open(HistoryActivity.class,null);await(()->!history.swipeRefreshLayout.isRefreshing()&&history.recyclerView.getAdapter().getItemCount()==3);}
    private AlertDialog historyAction(int action){await(()->history.recyclerView.findViewHolderForAdapterPosition(0)!=null);
        ui.runOnMainSync(()->history.recyclerView.findViewHolderForAdapterPosition(0).itemView.performLongClick());await(()->historyDialog()!=null);
        ui.runOnMainSync(()->historyDialog().getListView().performItemClick(null,action,action));
        await(()->historyDialog()!=null&&historyDialog().getButton(AlertDialog.BUTTON_POSITIVE)!=null);return historyDialog();}
    private void capture(View root,String name){ui.waitForIdleSync();ui.runOnMainSync(()->{Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);try(FileOutputStream out=new FileOutputStream(new File(ui.getTargetContext().getFilesDir(),name))){root.draw(new Canvas(b));b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}});}

    @Test public void favoriteSortPersistsAndPagingUsesTheSameOrder()throws Exception{
        FavoriteVideoListActivity favorites=open(FavoriteVideoListActivity.class,new Intent().putExtra("mid",42L).putExtra("fid",10042L).putExtra("name","测试收藏夹"));
        await(()->favorites.recyclerView.getAdapter().getItemCount()==2);
        ui.runOnMainSync(()->favorites.findViewById(R.id.library_sort).performClick());AlertDialog sort=(AlertDialog)field(favorites,"sortDialog");capture(sort.getWindow().getDecorView(),"favorite-sort.png");
        ui.runOnMainSync(()->sort.getListView().performItemClick(null,1,1));await(()->!favorites.swipeRefreshLayout.isRefreshing()&&"view".equals(FavoriteVideoOrder.read(42,10042)));
        assertTrue(hasFavoriteOrder("view","1"));
        FavoritePlaybackSource.Session snapshot=FavoritePlaybackSource.Session.capture(42,42,10042,false,"",1,new ApiResult(),false,Collections.singletonList(new VideoCard("v","u","1","",2,"BV2")));
        snapshot.favoriteOrder="view";new FavoritePlaybackSource().nextPage(FavoritePlaybackSource.Session.fromJson(snapshot.toJson()));assertTrue(hasFavoriteOrder("view","2"));
    }
    private boolean hasFavoriteOrder(String order,String page){for(Request r:requests)if(r.url().encodedPath().endsWith("/fav/resource/list")&&order.equals(r.url().queryParameter("order"))&&page.equals(r.url().queryParameter("pn")))return true;return false;}
    @Test public void folderLongPressIncludesVideoSorting()throws Exception{
        FavoriteFolderListActivity folders=open(FavoriteFolderListActivity.class,null);await(()->folders.recyclerView.getAdapter()!=null&&folders.recyclerView.getAdapter().getItemCount()==3);
        await(()->folders.recyclerView.findViewHolderForAdapterPosition(1)!=null);
        ui.runOnMainSync(()->folders.recyclerView.findViewHolderForAdapterPosition(1).itemView.performLongClick());
        AlertDialog menu=(AlertDialog)field(folders,"activeDialog");assertNotNull(menu);assertEquals(3,menu.getListView().getAdapter().getCount());
        ui.runOnMainSync(()->menu.getListView().performItemClick(null,2,2));await(()->field(folders,"activeDialog")!=null&&field(folders,"activeDialog")!=menu);
        ui.runOnMainSync(()->((AlertDialog)field(folders,"activeDialog")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());assertTrue(historyPosts.isEmpty());
    }
    @Test public void historyCancelThenSingleDeleteUsesCookieCsrfOnce()throws Exception{
        openHistory();AlertDialog cancel=historyAction(0);ui.runOnMainSync(()->cancel.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());assertTrue(historyPosts.isEmpty());
        AlertDialog confirm=historyAction(0);gate=new CountDownLatch(1);
        ui.runOnMainSync(()->{confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});await(()->historyPosts.size()==1);
        assertEquals("archive_11",historyPosts.peek().queryParameter("kid"));assertEquals("current-csrf",historyPosts.peek().queryParameter("csrf"));
        gate.countDown();await(()->!bool(history,"deleting")&&history.recyclerView.getAdapter().getItemCount()==2);assertEquals(1,historyPosts.size());
    }
    @Test public void multiDeleteTargetsOnlySelectedLoadedRowsAndPreservesSearch()throws Exception{
        openHistory();ui.runOnMainSync(()->{((EditText)history.findViewById(R.id.library_keyword)).setText("历史");history.findViewById(R.id.library_search).performClick();});await(()->!history.swipeRefreshLayout.isRefreshing());
        AlertDialog dialog=historyAction(1);ui.runOnMainSync(()->dialog.getListView().performItemClick(null,1,1));assertTrue(historyPosts.isEmpty());capture(dialog.getWindow().getDecorView(),"history-multi-delete.png");
        ui.runOnMainSync(()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());await(()->!bool(history,"deleting")&&history.recyclerView.getAdapter().getItemCount()==1);
        assertEquals("archive_11,archive_12",historyPosts.peek().queryParameter("kid"));assertEquals("历史",((EditText)history.findViewById(R.id.library_keyword)).getText().toString());
    }
    @Test public void historyFailureKeepsRowsAndAccountSwitchDoesNotSend()throws Exception{
        historyCode=-111;openHistory();AlertDialog first=historyAction(0);ui.runOnMainSync(()->first.getButton(AlertDialog.BUTTON_POSITIVE).performClick());await(()->!bool(history,"deleting"));assertEquals(3,history.recyclerView.getAdapter().getItemCount());
        AlertDialog second=historyAction(0);SharedPreferencesUtil.putLong("mid",43);ui.runOnMainSync(()->second.getButton(AlertDialog.BUTTON_POSITIVE).performClick());assertEquals(1,historyPosts.size());
    }

    private void openReporter()throws Exception{
        host=open(TestActivity.class,null);ui.runOnMainSync(()->{View root=host.getLayoutInflater().inflate(R.layout.fragment_video_info,null);host.setContentView(root);
            ImageView cover=root.findViewById(R.id.img_cover);cover.getLayoutParams().height=Math.round(110*host.getResources().getDisplayMetrics().density);cover.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.WHITE));
            report=new VideoReportController(host,root.findViewById(R.id.video_report_button),9876543210L);controllers.add(report);});ui.waitForIdleSync();
    }
    private VideoReportDialog reportDialog(){return (VideoReportDialog)field(report,"dialog");}
    private VideoReportDialog chooseReport(int tid){
        ui.runOnMainSync(()->host.findViewById(R.id.video_report_button).performClick());await(()->reportDialog()!=null&&reportDialog().isShowing());
        int index=-1;for(int i=0;i<reasons.size();i++)if(reasons.get(i).id==tid)index=i;assertTrue(index>=0);final int chosen=index;
        VideoReportDialog dialog=reportDialog();ui.runOnMainSync(()->{((RadioGroup)dialog.findViewById(R.id.video_report_reasons)).check(chosen+1);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});return dialog;
    }
    @Test public void videoReportIconAndBothCancelStagesNeverSubmit()throws Exception{
        openReporter();capture(host.getWindow().getDecorView(),"video-report-cover.png");
        await(()->host.findViewById(R.id.video_report_button).getWidth()>0);
        ui.runOnMainSync(()->{
            View button=host.findViewById(R.id.video_report_button),cover=host.findViewById(R.id.img_cover);
            assertTrue(button.getRight()<=cover.getRight());assertTrue(button.getTop()>=cover.getTop());
            int[] coverClicks={0};cover.setOnClickListener(v->coverClicks[0]++);
            ViewGroup root=(ViewGroup)host.findViewById(R.id.scrollView);Rect hit=new Rect(0,0,button.getWidth(),button.getHeight());root.offsetDescendantRectToMyCoords(button,hit);
            long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,hit.centerX(),hit.centerY(),0),up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,hit.centerX(),hit.centerY(),0);
            try{root.dispatchTouchEvent(down);root.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}assertEquals(0,coverClicks[0]);
        });await(()->reportDialog()!=null&&reportDialog().isShowing());
        capture(reportDialog().getWindow().getDecorView(),"video-report-reasons.png");ui.runOnMainSync(()->reportDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        VideoReportDialog confirm=chooseReport(10013);ui.runOnMainSync(()->{assertFalse(confirm.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());confirm.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});assertTrue(reportPosts.isEmpty());
    }
    @Test public void videoReportRequiresDescriptionAndPostsExactTargetOnlyOnce()throws Exception{
        openReporter();VideoReportDialog confirm=chooseReport(10013);gate=new CountDownLatch(1);
        ui.runOnMainSync(()->{((EditText)confirm.findViewById(R.id.video_report_note)).setText("封面内容问题，请核对视频开头部分");assertTrue(confirm.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());});capture(confirm.getWindow().getDecorView(),"video-report-confirm.png");
        ui.runOnMainSync(()->{confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});await(()->reportPosts.size()==1);
        assertEquals("9876543210",reportPosts.peek().queryParameter("aid"));assertEquals("10013",reportPosts.peek().queryParameter("tid"));assertEquals("current-csrf",reportPosts.peek().queryParameter("csrf"));assertEquals("false",reportPosts.peek().queryParameter("block_author"));
        gate.countDown();await(()->!bool(report,"busy"));assertEquals(1,reportPosts.size());
    }
    @Test public void focusingVideoReportDescriptionRequestsKeyboard()throws Exception{
        openReporter();VideoReportDialog confirm=chooseReport(10013);EditText note=confirm.findViewById(R.id.video_report_note);
        ui.runOnMainSync(()->{
            note.requestFocusFromTouch();note.performClick();
            int mode=confirm.getWindow().getAttributes().softInputMode&WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE;
            assertEquals("soft input mode="+Integer.toHexString(mode),WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,mode);
        });
        assertTrue(note.hasFocus());
        assertTrue(reportPosts.isEmpty());
    }
    @Test public void reportWithRequiredProofUsesOfficialFlowWithoutPosting()throws Exception{
        openReporter();VideoReportDialog confirm=chooseReport(8);ui.runOnMainSync(()->confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(()->field(report,"official")!=null);assertTrue(reportPosts.isEmpty());
    }
    @Test public void platformVerificationFailureIsNotShownAsSuccessfulReport()throws Exception{
        reportCode=-352;openReporter();VideoReportDialog confirm=chooseReport(10013);
        ui.runOnMainSync(()->{((EditText)confirm.findViewById(R.id.video_report_note)).setText("问题描述");confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        await(()->!bool(report,"busy")&&field(report,"official")!=null);assertEquals(1,reportPosts.size());
    }
    @Test public void changingAccountsWhileEditingReportPreventsSubmission()throws Exception{
        openReporter();VideoReportDialog confirm=chooseReport(10013);SharedPreferencesUtil.putLong("mid",43);
        ui.runOnMainSync(()->{((EditText)confirm.findViewById(R.id.video_report_note)).setText("问题描述");confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});assertTrue(reportPosts.isEmpty());
    }
}
