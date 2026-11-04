package com.RobinNotBad.BiliClient.activity.user.favorite;

import android.app.*;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.EditText;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.video.MultiPageActivity;
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity;
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity;
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoFragment;
import com.RobinNotBad.BiliClient.api.FavoritePlaybackSource;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import okhttp3.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FavoritePlaybackLaunchInstrumentedTest {
    private static final long AID = 500000009001L;
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> old = new HashMap<>();
    private final List<Activity> activities = new ArrayList<>();
    private final List<HttpUrl> requests = Collections.synchronizedList(new ArrayList<>());
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient previous;
    private volatile boolean paged;

    @Before public void before() throws Exception {
        for (String key : new String[]{"tags_enable", "related_enable", "first_play", "first_videoinfo"}) pref(key, false);
        pref(SharedPreferencesUtil.mid, 42L);
        Field f = NetWorkUtil.class.getDeclaredField("INSTANCE"); f.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) f.get(null); previous = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            HttpUrl url = chain.request().url(); requests.add(url);
            JSONObject data = new JSONObject();
            try {
                if (url.encodedPath().endsWith("/folder/created/list"))
                    data.put("list", new JSONArray().put(new JSONObject().put("id",91).put("title","音乐").put("mid",42)))
                            .put("has_more",false);
                if (url.encodedPath().endsWith("/resource/list")) {
                    JSONArray medias = new JSONArray();
                    int page = Integer.parseInt(url.queryParameter("pn"));
                    for (int i = 0; i < (paged ? 20 : 2); i++) medias.put(new JSONObject().put("id", AID + (page - 1) * 20 + i).put("title", "测试歌曲" + i)
                            .put("type",2).put("fav_time",100-i).put("upper",new JSONObject().put("name","测试")));
                    data.put("medias",medias).put("has_more",paged && page == 1);
                }
                JSONObject result = new JSONObject().put("code",0).put("data",data);
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"),result.toString())).build();
            } catch (JSONException error) { throw new java.io.IOException(error); }
        }).build());
        VideoInfo info = new VideoInfo(); info.aid=AID; info.bvid=""; info.epid=-1; info.title="测试歌曲";
        info.cover=""; info.description="测试"; info.argueMsg=""; info.duration="01:00"; info.timeDesc="今天";
        info.stats=new Stats(); UserInfo owner=new UserInfo();owner.mid=42;owner.name="测试";owner.avatar="";info.staff.add(owner);
        info.pagenames.addAll(Arrays.asList("第一P","第二P"));info.cids.addAll(Arrays.asList(90001L,90002L));
        TerminalContext.getInstance().putContent(info);
    }

    @After public void after() {
        for (int i=activities.size()-1;i>=0;i--) { Activity activity=activities.get(i); inst.runOnMainSync(activity::finish); }
        inst.waitForIdleSync(); clients.set(previous);
        for(Map.Entry<String,Object> entry:old.entrySet()) {
            if(entry.getValue()==null)SharedPreferencesUtil.removeValue(entry.getKey());
            else if(entry.getValue() instanceof Long)SharedPreferencesUtil.putLong(entry.getKey(),(Long)entry.getValue());
            else SharedPreferencesUtil.putBoolean(entry.getKey(),(Boolean)entry.getValue());
        }
    }

    @Test public void favoriteClickCarriesContextThroughRealDetailsAndMultiPartSelection() throws Exception {
        FavoriteVideoListActivity list=start(false);
        VideoInfoActivity detail=openDetail(list);
        String token=detail.getIntent().getStringExtra(FavoritePlaybackSource.TOKEN);
        FavoritePlaybackSource.Session session=FavoritePlaybackSource.read(inst.getTargetContext(),token,42);
        assertEquals(91,session.folder);assertFalse(session.all);
        VideoInfoFragment fragment=details(detail);
        await(()->read(fragment,VideoInfoFragment.class,"playerData")!=null);
        PlayerData data=(PlayerData)read(fragment,VideoInfoFragment.class,"playerData");assertEquals(token,data.favoritePlaybackToken);
        Instrumentation.ActivityMonitor pages=inst.addMonitor(MultiPageActivity.class.getName(),null,false);
        try {
            main(()->invoke(fragment,"playClick"));
            MultiPageActivity choose=(MultiPageActivity)inst.waitForMonitorWithTimeout(pages,6000);
            assertNotNull(choose);activities.add(choose);
            RecyclerView recycler=choose.findViewById(R.id.recyclerView);
            await(()->recycler.findViewHolderForAdapterPosition(1)!=null);
            Instrumentation.ActivityMonitor jumps=inst.addMonitor(JumpToPlayerActivity.class.getName(),null,false);
            try {
                main(()->recycler.findViewHolderForAdapterPosition(1).itemView.performClick());
                JumpToPlayerActivity jump=(JumpToPlayerActivity)inst.waitForMonitorWithTimeout(jumps,6000);
                assertNotNull(jump);activities.add(jump);
                PlayerData selected=jump.getIntent().getParcelableExtra("data");
                assertEquals(token,selected.favoritePlaybackToken);assertEquals(1,selected.currentPageIndex);assertEquals(90002,selected.cid);
            } finally { inst.removeMonitor(jumps); }
        } finally { inst.removeMonitor(pages); }
    }

    @Test public void allFavoriteSearchLaunchKeepsTheSubmittedSearchScope() throws Exception {
        FavoriteVideoListActivity list=start(true);
        main(()->{
            ((EditText)list.findViewById(R.id.library_keyword)).setText("歌曲");
            list.findViewById(R.id.library_search).performClick();
        });
        await(()->!list.swipeRefreshLayout.isRefreshing()&&list.recyclerView.getAdapter().getItemCount()==2);
        VideoInfoActivity detail=openDetail(list);
        FavoritePlaybackSource.Session session=FavoritePlaybackSource.read(inst.getTargetContext(),
                detail.getIntent().getStringExtra(FavoritePlaybackSource.TOKEN),42);
        assertTrue(session.all);assertEquals("歌曲",session.keyword);assertEquals("91",session.cursor.business);
        assertTrue(session.bottom);assertEquals(Arrays.asList(AID,AID+1),session.ids);
    }

    @Test public void productionNextPageRequestsPreserveFolderAndCrossFolderSearchParameters() throws Exception {
        for(boolean all:new boolean[]{false,true}) {
            VideoCard first=new VideoCard();first.aid=1;
            FavoritePlaybackSource.Session session=FavoritePlaybackSource.Session.capture(42,42,91,all,"歌曲",1,
                    new ApiResult().setOffset(0,0,"91"),false,Arrays.asList(first));
            FavoritePlaybackSource.Session result=new FavoritePlaybackSource().nextPage(session);
            HttpUrl url=null;
            synchronized(requests){for(HttpUrl request:requests)if(request.encodedPath().endsWith("/resource/list"))url=request;}
            assertNotNull(url);assertEquals("91",url.queryParameter("media_id"));assertEquals("2",url.queryParameter("pn"));
            assertEquals("歌曲",url.queryParameter("keyword"));assertEquals(all?"1":"0",url.queryParameter("type"));
            assertTrue(result.bottom);assertEquals(3,result.ids.size());
        }
    }

    @Test public void allFavoritesBrowseRestoresItsMergedCursorAndLoadsBeyondTwentyItems() throws Exception {
        paged = true;
        List<VideoCard> first = new ArrayList<>();
        ApiResult cursor = com.RobinNotBad.BiliClient.api.FavoriteApi.getAllFavoriteVideos(42, null, first);
        assertEquals(20, first.size()); assertFalse(cursor.isBottom);
        FavoritePlaybackSource.Session session = FavoritePlaybackSource.Session.capture(42, 42, 0,
                true, "", 1, cursor, false, first);
        String token = FavoritePlaybackSource.create(inst.getTargetContext(), session);
        FavoritePlaybackSource.Session restored = FavoritePlaybackSource.read(inst.getTargetContext(), token, 42);
        FavoritePlaybackSource.Session next = new FavoritePlaybackSource().nextPage(restored);
        assertTrue(next.bottom); assertEquals(40, next.ids.size()); assertEquals(Long.valueOf(AID + 39), next.ids.get(39));
        int pages = 0;
        synchronized (requests) {
            for (HttpUrl request : requests) if (request.encodedPath().endsWith("/resource/list")) {
                pages++; assertEquals("0", request.queryParameter("type")); assertEquals("", request.queryParameter("keyword"));
            }
        }
        assertEquals(2, pages);
    }

    private FavoriteVideoListActivity start(boolean all)throws Exception {
        FavoriteVideoListActivity list=(FavoriteVideoListActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),FavoriteVideoListActivity.class)
                .putExtra("mid",42L).putExtra("fid",91L).putExtra("name","音乐").putExtra("all_folders",all)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activities.add(list);
        await(()->list.recyclerView.getAdapter()!=null&&list.recyclerView.getAdapter().getItemCount()==2&&!list.swipeRefreshLayout.isRefreshing());
        return list;
    }
    private VideoInfoActivity openDetail(FavoriteVideoListActivity list)throws Exception {
        await(()->list.recyclerView.findViewHolderForAdapterPosition(0)!=null);
        Instrumentation.ActivityMonitor monitor=inst.addMonitor(VideoInfoActivity.class.getName(),null,false);
        try {
            main(()->list.recyclerView.findViewHolderForAdapterPosition(0).itemView.performClick());
            VideoInfoActivity detail=(VideoInfoActivity)inst.waitForMonitorWithTimeout(monitor,6000);
            assertNotNull(detail);activities.add(detail);return detail;
        } finally { inst.removeMonitor(monitor); }
    }
    private VideoInfoFragment details(VideoInfoActivity activity)throws Exception {
        await(()->{for(Fragment f:activity.getSupportFragmentManager().getFragments())if(f instanceof VideoInfoFragment)return true;return false;});
        final VideoInfoFragment[] value={null};main(()->{for(Fragment f:activity.getSupportFragmentManager().getFragments())if(f instanceof VideoInfoFragment)value[0]=(VideoInfoFragment)f;});return value[0];
    }
    private interface Check{boolean ready();}
    private void await(Check check)throws Exception {
        long until=SystemClock.uptimeMillis()+8000;boolean[] ok={false};
        do{main(()->ok[0]=check.ready());if(ok[0])return;SystemClock.sleep(40);}while(SystemClock.uptimeMillis()<until);
        fail("UI did not become ready");
    }
    private Object read(Object object,Class<?> type,String name){try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private void invoke(VideoInfoFragment fragment,String name){try{Method method=VideoInfoFragment.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(fragment);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable action){inst.runOnMainSync(action);}
    private void pref(String key,Object value){old.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));if(value instanceof Long)SharedPreferencesUtil.putLong(key,(Long)value);else SharedPreferencesUtil.putBoolean(key,(Boolean)value);}
}
