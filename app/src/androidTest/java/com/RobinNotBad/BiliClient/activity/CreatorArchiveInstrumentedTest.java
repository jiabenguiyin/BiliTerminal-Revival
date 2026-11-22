package com.RobinNotBad.BiliClient.activity;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.browser.BrowserActivity;
import com.RobinNotBad.BiliClient.activity.user.*;
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity;
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
    private final ConcurrentLinkedQueue<JSONObject> titlePosts=new ConcurrentLinkedQueue<>();
    private volatile String archiveTitle="确认用测试稿件";
    private volatile int titleCode;
    private final Map<String,Object> saved=new HashMap<>();
    private AtomicReference<OkHttpClient> clients;private OkHttpClient oldClient;
    private volatile int deleteCode;private volatile boolean offline,removed,statFailure,unknownOwner,foreignOwner,missingResult;
    private volatile CountDownLatch gate;
    private boolean largeList, unpublished;
    private CreatorArchiveActivity owner;
    private static final String[] KEYS={SharedPreferencesUtil.mid,SharedPreferencesUtil.cookies,"dpi","density","paddingH_percent","paddingV_percent","player_ui_round","ui_landscape"};

    @Before public void setup() throws Exception {
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);oldClient=clients.get();
        SharedPreferences prefs = SharedPreferencesUtil.getSharedPreferences();
        for (String k : KEYS) saved.put(k, prefs.getAll().get(k));
        String tutorialKey = "tutorial_ver_" + ui.getTargetContext().getResources().getStringArray(R.array.tutorial_list)[1];
        saved.put(tutorialKey, prefs.getAll().get(tutorialKey));
        prefs.edit().putFloat("dpi",1f).putInt("density",-1).putInt("paddingH_percent",0).putInt("paddingV_percent",0)
                .putBoolean("player_ui_round",false).putBoolean("ui_landscape",false).putInt(tutorialKey, 99).commit();
        account(42);
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            Request r=chain.request();requests.add(r);String path=r.url().encodedPath();String json="{\"code\":-404}";
            if(path.endsWith("/nav"))json="{\"code\":0,\"data\":{\"mid\":"+SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)+",\"isLogin\":true}}";
            else if(path.endsWith("/archive/delete")){
                Buffer b=new Buffer();r.body().writeTo(b);posts.add(HttpUrl.parse("https://fixture.invalid/?"+b.readUtf8()));
                CountDownLatch held=gate;if(held!=null)try{held.await(6,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}
                if(offline)throw new IOException("fixture offline");
                if(deleteCode==0)removed=true;json=missingResult?"{}":"{\"code\":"+deleteCode+"}";
            }else if(path.equals("/x/vu/web/edit")){
                Buffer body=new Buffer();r.body().writeTo(body);
                JSONObject payload;try{payload=new JSONObject(body.readUtf8());}catch(Exception e){throw new IOException(e);}
                titlePosts.add(payload);
                assertTrue(r.body().contentType().toString().startsWith("application/json"));
                assertEquals("csrf-42",r.url().queryParameter("csrf"));
                CountDownLatch held=gate;if(held!=null)try{held.await(6,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}
                if(offline)throw new IOException("fixture offline");
                if(titleCode==0)archiveTitle=payload.optString("title");
                json="{\"code\":"+titleCode+"}";
            }else if(path.endsWith("/archives")){
                int page = Integer.parseInt(r.url().queryParameter("pn"));
                String keyword = r.url().queryParameter("keyword");
                int count = removed || "不存在".equals(keyword) ? 0 : largeList ? 101 : 21;
                json = page(page, count, count == 0 ? "" : row(1000 + page));
            }else if(path.endsWith("/archive/view"))json="{\"code\":0,\"data\":{\"archive\":{\"aid\":1001,\"bvid\":\"BVfixture\",\"title\":"+JSONObject.quote(archiveTitle)
                    +",\"mid\":"+(unknownOwner?0:foreignOwner?99:42)+",\"state\":0,\"copyright\":1,\"tid\":17,\"tag\":\"测试,手表\",\"cover\":\"//fixture/cover\",\"desc\":\"原简介\",\"unknown_flag\":{\"keep\":1}},"
                    +"\"videos\":[{\"filename\":\"part-1\",\"title\":\"分P一\"},{\"filename\":\"part-2\",\"title\":\"分P二\"}]}}";
            else if(path.endsWith("/web-interface/view")) {
                json = "{\"code\":0,\"data\":{\"aid\":1001,\"bvid\":\"BV1Fixture01\",\"title\":\"测试视频\","
                        + "\"owner\":{\"mid\":42,\"name\":\"测试 UP\",\"face\":\"\"},\"pic\":\"\",\"desc\":\"\","
                        + "\"pubdate\":1,\"duration\":30,\"pages\":[{\"cid\":1,\"part\":\"测试视频\"}],"
                        + "\"stat\":{\"view\":123,\"like\":4,\"coin\":0,\"reply\":1,\"danmaku\":0}}}";
            }
            else if(path.endsWith("/archive/relation")) json="{\"code\":0,\"data\":{}}";
            else if(path.equals("/x/v2/reply")) json="{\"code\":0,\"data\":{\"replies\":[],\"page\":{\"count\":0,\"num\":1,\"size\":20}}}";
            else if(path.endsWith("/index/stat"))json=statFailure?"{\"code\":-403}":"{\"code\":0,\"data\":{\"total_fans\":12,\"incr_fans\":1}}";
            else if(path.endsWith("/index/scrolls"))json="{\"code\":0,\"data\":{\"scrolls\":[]}}";
            return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(ResponseBody.create(MediaType.parse("application/json"),json)).build();
        }).build());
    }
    @After public void cleanup()throws Exception{
        if(gate!=null)gate.countDown();
        await(()->!fieldBoolean("deleting")&&!fieldBoolean("editing"));
        ui.runOnMainSync(()->{for(Activity a:owners)a.finish();});ui.waitForIdleSync();SystemClock.sleep(100);
        SharedPreferences.Editor e=SharedPreferencesUtil.getSharedPreferences().edit();
        for(String k:saved.keySet()){Object v=saved.get(k);if(v==null)e.remove(k);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Long)e.putLong(k,(Long)v);
            else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else e.putBoolean(k,(Boolean)v);}e.commit();clients.set(oldClient);
    }
    private boolean hasRequest(String key,String value){for(Request r:requests)if(value.equals("path".equals(key)?r.url().encodedPath():r.url().queryParameter(key)))return true;return false;}
    private void account(long mid){SharedPreferencesUtil.getSharedPreferences().edit().putLong(SharedPreferencesUtil.mid,mid)
            .putString(SharedPreferencesUtil.cookies,"DedeUserID="+mid+"; SESSDATA=fixture-"+mid+"; bili_jct=csrf-"+mid).commit();}
    private String row(long aid) {
        String bvid = String.format(Locale.US, "BV1Fixture%02d", aid - 1000);
        return "{\"Archive\":{\"aid\":" + aid + ",\"mid\":" + (unknownOwner ? 0 : foreignOwner ? 99 : 42)
                + ",\"bvid\":\"" + bvid + "\",\"title\":\"用于管理测试的本人稿件（不是真实视频）\","
                + "\"state\":" + (unpublished ? "-1,\"state_desc\":\"审核中\"" : "0")
                + "},\"stat\":{\"view\":123,\"like\":4}}";
    }
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
    @Test public void titleSearchUsesServerPaginationAndKeepsStatusFilter() throws Exception {
        open();
        search("剪辑 & 手表");
        await(() -> !fieldBoolean("busy") && status().contains("找到 21"));
        assertTrue(hasRequest("keyword", "剪辑 & 手表"));
        ui.runOnMainSync(() -> owner.findViewById(R.id.creator_next).performClick());
        await(() -> !fieldBoolean("busy") && status().contains("第 2 页"));
        assertTrue(hasArchiveRequest("2", null, "剪辑 & 手表", 42));
        ui.runOnMainSync(() -> owner.findViewById(R.id.creator_filter).performClick());
        await(() -> dialog() != null);
        ui.runOnMainSync(() -> dialog().getListView().performItemClick(dialog().getListView().getChildAt(1), 1, 1));
        await(() -> !fieldBoolean("busy") && status().contains("第 1 页"));
        assertTrue(hasArchiveRequest("1", "pubed", "剪辑 & 手表", 42));
        search("不存在");
        await(() -> !fieldBoolean("busy") && list().getAdapter().getItemCount() == 0);
        assertEquals(ui.getTargetContext().getString(R.string.creator_search_empty),
                ((TextView) owner.findViewById(R.id.creator_empty)).getText().toString());
        ui.runOnMainSync(() -> {
            owner.findViewById(R.id.creator_search_toggle).performClick();
            owner.findViewById(R.id.library_clear).performClick();
        });
        await(() -> !fieldBoolean("busy") && status().startsWith("共 21"));
    }

    @Test public void archiveNumbersFindExactMatchesBeyondTheFirstSearchBatch() throws Exception {
        largeList = true;
        open();
        for (String query : Arrays.asList("av001006", "bv1Fixture06")) {
            search(query);
            await(() -> !fieldBoolean("busy") && status().contains("已查至第 4 页"));
            assertEquals(0, list().getAdapter().getItemCount());
            assertFalse(owner.findViewById(R.id.creator_previous).isEnabled());
            ui.runOnMainSync(() -> owner.findViewById(R.id.creator_next).performClick());
            await(() -> !fieldBoolean("busy") && list().getAdapter().getItemCount() == 1);
            AlertDialog details = openDetails();
            assertTrue(detailText(details).contains("BV1Fixture06"));
            assertTrue(detailText(details).contains("av1006"));
            ui.runOnMainSync(details::dismiss);
        }
        assertFalse(hasRequest("pn", "7"));
        assertTrue(posts.isEmpty());
    }

    @Test public void publishedDetailsCopyNumberAndOpenTheActualCommentsTab() throws Exception {
        open();
        AlertDialog details = openDetails();
        assertTrue(detailText(details).contains("播放 123"));
        assertTrue(detailText(details).contains("收藏 —"));
        assertTrue(detailText(details).contains("BV1Fixture01"));
        ui.runOnMainSync(() -> {
            assertTrue(details.getButton(AlertDialog.BUTTON_NEUTRAL).isShown());
            details.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        });
        await(() -> {
            ClipboardManager clipboard = (ClipboardManager) owner.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard.getPrimaryClip();
            return clip != null && "BV1Fixture01".contentEquals(clip.getItemAt(0).getText());
        });
        AlertDialog actions = openDetails();
        Instrumentation.ActivityMonitor monitor = ui.addMonitor(VideoInfoActivity.class.getName(), null, false);
        try {
            ui.runOnMainSync(() -> actions.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            VideoInfoActivity video = (VideoInfoActivity) monitor.waitForActivityWithTimeout(8000);
            assertNotNull(video);
            owners.add(video);
            await(() -> ((ViewPager) video.findViewById(R.id.viewPager)).getCurrentItem() == 1
                    && video.replyFragment != null && video.replyFragment.getView() != null);
            assertEquals(1001, video.getIntent().getLongExtra("aid", 0));
        } finally {
            ui.removeMonitor(monitor);
        }
    }

    @Test public void unpublishedDetailsKeepStatusAndHideVideoAndComments() throws Exception {
        unpublished = true;
        open();
        AlertDialog details = openDetails();
        assertTrue(detailText(details).contains("审核中"));
        assertFalse(details.getButton(AlertDialog.BUTTON_NEGATIVE).isShown());
        assertEquals(owner.getString(android.R.string.ok),
                details.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        assertTrue(details.getButton(AlertDialog.BUTTON_NEUTRAL).isShown());
        assertFalse(hasRequest("path", "/x/web-interface/view"));
    }

    @Test public void accountSwitchInvalidatesOpenDetailsAndClearsOldSearch() throws Exception {
        open();
        search("账号 42 的搜索");
        await(() -> !fieldBoolean("busy") && status().contains("账号 42"));
        AlertDialog details = openDetails();
        Instrumentation.ActivityMonitor monitor = ui.addMonitor(VideoInfoActivity.class.getName(),
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        try {
            account(43);
            ui.runOnMainSync(() -> details.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            await(() -> !fieldBoolean("busy") && status().startsWith("共 21"));
            assertEquals(0, monitor.getHits());
            assertFalse(details.isShowing());
            assertTrue(hasArchiveRequest("1", null, null, 43));
        } finally {
            ui.removeMonitor(monitor);
        }
    }

    private boolean hasArchiveRequest(String page, String filter, String keyword, long mid) {
        for (Request request : requests) {
            HttpUrl url = request.url();
            String cookie = request.header("Cookie");
            if (url.encodedPath().endsWith("/archives") && page.equals(url.queryParameter("pn"))
                    && (filter == null || filter.equals(url.queryParameter("status")))
                    && (keyword == null ? url.queryParameter("keyword") == null
                                       : keyword.equals(url.queryParameter("keyword")))
                    && cookie != null && cookie.contains("SESSDATA=fixture-" + mid)) return true;
        }
        return false;
    }

    private void search(String query) {
        ui.runOnMainSync(() -> {
            owner.findViewById(R.id.creator_search_toggle).performClick();
            ((EditText) owner.findViewById(R.id.library_keyword)).setText(query);
            owner.findViewById(R.id.library_search).performClick();
        });
    }

    private String status() {
        return ((TextView) owner.findViewById(R.id.creator_page_status)).getText().toString();
    }

    private AlertDialog openDetails() {
        await(() -> list().findViewHolderForAdapterPosition(0) != null);
        ui.runOnMainSync(() -> list().findViewHolderForAdapterPosition(0).itemView.performClick());
        await(() -> dialog() != null && dialog().findViewById(android.R.id.message) != null);
        return dialog();
    }

    private String detailText(AlertDialog details) {
        return ((TextView) details.findViewById(android.R.id.message)).getText().toString();
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

    private AlertDialog titleEditor()throws Exception {
        open();ui.runOnMainSync(()->list().findViewHolderForAdapterPosition(0).itemView.performLongClick());
        await(()->dialog()!=null&&dialog().getListView()!=null);
        ui.runOnMainSync(()->{
            android.widget.ListView menu=dialog().getListView();int index=-1;
            for(int i=0;i<menu.getAdapter().getCount();i++)if("修改视频标题".equals(menu.getAdapter().getItem(i).toString()))index=i;
            assertTrue(index>=0);menu.performItemClick(null,index,index);
        });
        await(()->dialog()!=null&&dialog().findViewById(R.id.creator_title_input)!=null);
        return dialog();
    }
    private AlertDialog titleConfirmation(AlertDialog editor,String text){
        ui.runOnMainSync(()->{((EditText)editor.findViewById(R.id.creator_title_input)).setText(text);editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        await(()->dialog()!=null&&dialog().findViewById(android.R.id.message)!=null);
        return dialog();
    }
    @Test public void titleEditRequiresConfirmationPreservesPartsAndSubmitsExactlyOnce()throws Exception {
        AlertDialog editor=titleEditor();
        await(()->((android.view.inputmethod.InputMethodManager)owner.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                .isActive(editor.findViewById(R.id.creator_title_input)));
        ui.runOnMainSync(()->{
            assertEquals(0,editor.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            assertTrue(editor.findViewById(R.id.creator_title_input).hasFocus());
        });
        capture(editor.getWindow().getDecorView(),"creator-title-editor.png");
        AlertDialog confirm=titleConfirmation(editor,"新的测试标题");
        assertTrue(titlePosts.isEmpty());
        ui.runOnMainSync(()->{
            assertTrue(detailText(confirm).contains("原题：确认用测试稿件"));assertTrue(detailText(confirm).contains("新题：新的测试标题"));
        });
        capture(confirm.getWindow().getDecorView(),"creator-title-confirm.png");
        gate=new CountDownLatch(1);
        ui.runOnMainSync(()->confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick());await(()->titlePosts.size()==1);
        ui.runOnMainSync(()->confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick());assertEquals(1,titlePosts.size());
        gate.countDown();await(()->!fieldBoolean("editing")&&!fieldBoolean("busy"));
        JSONObject payload=titlePosts.peek();assertEquals("新的测试标题",payload.getString("title"));
        assertEquals("原简介",payload.getString("desc"));assertEquals(1,payload.getJSONObject("unknown_flag").getInt("keep"));
        assertEquals(2,payload.getJSONArray("videos").length());assertEquals("分P二",payload.getJSONArray("videos").getJSONObject(1).getString("title"));
        assertTrue(posts.isEmpty());
    }
    @Test public void titleEditRejectsBlankAndAccountSwitchBeforeSubmission()throws Exception {
        AlertDialog editor=titleEditor();
        ui.runOnMainSync(()->{((EditText)editor.findViewById(R.id.creator_title_input)).setText("  ");editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(editor.isShowing());assertNotNull(((EditText)editor.findViewById(R.id.creator_title_input)).getError());});
        AlertDialog confirm=titleConfirmation(editor,"新的测试标题");account(43);
        ui.runOnMainSync(()->confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick());await(()->!fieldBoolean("busy"));
        assertTrue(titlePosts.isEmpty());
    }
    @Test public void concurrentExternalTitleEditIsNotOverwritten()throws Exception {
        AlertDialog confirm=titleConfirmation(titleEditor(),"终端准备的新标题");archiveTitle="其他客户端的新标题";
        ui.runOnMainSync(()->confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(()->!fieldBoolean("editing")&&dialog()!=null);
        ui.runOnMainSync(()->assertTrue(detailText(dialog()).contains("稿件信息已变化")));
        assertTrue(titlePosts.isEmpty());
    }
    @Test public void interruptedTitleSubmissionDoesNotRetryOrPretendSuccess()throws Exception {
        AlertDialog confirm=titleConfirmation(titleEditor(),"新的测试标题");offline=true;
        ui.runOnMainSync(()->confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(()->!fieldBoolean("editing")&&dialog()!=null);
        ui.runOnMainSync(()->assertTrue(detailText(dialog()).contains("无法确认")));
        assertEquals(1,titlePosts.size());
    }
    @Test public void titleSecurityVerificationErrorKeepsTheArchiveAndOffersOfficialManager()throws Exception {
        AlertDialog confirm=titleConfirmation(titleEditor(),"新的测试标题");titleCode=340022;
        ui.runOnMainSync(()->confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(()->!fieldBoolean("editing")&&dialog()!=null);
        ui.runOnMainSync(()->assertTrue(detailText(dialog()).contains("安全验证")));
        assertEquals(1,titlePosts.size());assertEquals(1,list().getAdapter().getItemCount());
    }
}
