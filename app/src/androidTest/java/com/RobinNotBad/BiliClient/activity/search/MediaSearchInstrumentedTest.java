package com.RobinNotBad.BiliClient.activity.search;

import android.app.Instrumentation;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter;
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder;
import com.RobinNotBad.BiliClient.api.ConfInfoApi;
import com.RobinNotBad.BiliClient.api.SearchApi;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MediaSearchInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> savedPrefs = new HashMap<>();
    private final CountDownLatch slowStarted = new CountDownLatch(1);
    private final CountDownLatch releaseSlow = new CountDownLatch(1);
    private final CountDownLatch slowFinished = new CountDownLatch(1);
    private final AtomicInteger filmRequests = new AtomicInteger();
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient originalClient;
    private SearchActivity activity;

    @Before public void setup() throws Exception {
        SharedPreferences preferences = SharedPreferencesUtil.getSharedPreferences();
        for (String key : new String[]{"tutorial_ver_search", "tutorial_pager_SearchActivity",
                "search_suggestions_enable", SharedPreferencesUtil.SEARCH_DEFAULT_CONTENT_ENABLE,
                "wbi_mixin_key", "last_wbi", SharedPreferencesUtil.search_history,
                SharedPreferencesUtil.SEARCH_SHOW_CATEGORY_TABS,"dpi","density","player_ui_round"}) {
            savedPrefs.put(key, preferences.getAll().get(key));
        }
        preferences.edit().putInt("tutorial_ver_search", 99)
                .putBoolean("tutorial_pager_SearchActivity", false)
                .putBoolean("search_suggestions_enable", false)
                .putBoolean(SharedPreferencesUtil.SEARCH_DEFAULT_CONTENT_ENABLE, false)
                .putBoolean(SharedPreferencesUtil.SEARCH_SHOW_CATEGORY_TABS,true)
                .putString("wbi_mixin_key", "0123456789abcdef0123456789abcdef")
                .putInt("last_wbi", ConfInfoApi.getDateCurr()).commit();
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) field.get(null);
        originalClient = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            String type = chain.request().url().queryParameter("search_type");
            String keyword = chain.request().url().queryParameter("keyword");
            String page = chain.request().url().queryParameter("page");
            JSONObject data = new JSONObject();
            try {
                if (SearchApi.TYPE_FILM.equals(type)) filmRequests.incrementAndGet();
                if ("slow".equals(keyword) && SearchApi.TYPE_FILM.equals(type)) {
                    slowStarted.countDown();
                    try { releaseSlow.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
                JSONArray rows = new JSONArray();
                if (SearchApi.TYPE_BANGUMI.equals(type) || SearchApi.TYPE_FILM.equals(type)) {
                    int currentPage = "2".equals(page) ? 2 : 1;
                    for (int i = 0; i < 2; i++) rows.put(new JSONObject()
                            .put("media_id", currentPage * 100 + i).put("season_id", 987)
                            .put("title", keyword + " " + currentPage + "-" + i)
                            .put("areas", "Test").put("index_show", "Complete"));
                }
                data.put("result", rows).put("numPages", 2).put("seid", "");
                String json = new JSONObject().put("code", 0).put("data", data).toString();
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"), json)).build();
            } catch (Exception error) { throw new java.io.IOException(error); }
            finally {
                if ("slow".equals(keyword) && SearchApi.TYPE_FILM.equals(type)) slowFinished.countDown();
            }
        }).build());
        activity = (SearchActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> activity.viewPager != null && activity.viewPager.getAdapter() != null);
    }

    @After public void teardown() {
        releaseSlow.countDown();
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
        if (clients != null) clients.set(originalClient);
        SharedPreferences.Editor editor = SharedPreferencesUtil.getSharedPreferences().edit();
        for (Map.Entry<String, Object> entry : savedPrefs.entrySet()) {
            Object value = entry.getValue();
            if (value == null) editor.remove(entry.getKey());
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof String) editor.putString(entry.getKey(), (String) value);
        }
        editor.commit();
    }

    @Test public void categoriesLoadLazilyAndPaginateWithIndependentKeywords() throws Exception {
        instrumentation.runOnMainSync(() -> {
            TabLayout tabs = activity.findViewById(R.id.search_categories);
            assertEquals(6, tabs.getTabCount());
            assertEquals("番剧", tabs.getTabAt(1).getText());
            assertEquals("影视", tabs.getTabAt(2).getText());
            tabs.getTabAt(1).select();
            activity.keywordInput.setText("Initial");
            activity.searchKeyword("Initial");
        });
        await(() -> loaded(1, 2) || loaded(1, 4));
        assertEquals("Offscreen film tab sent an unnecessary request", 0, filmRequests.get());
        select(2);
        await(() -> loaded(2, 2) || loaded(2, 4));
        assertTrue(filmRequests.get() >= 1 && filmRequests.get() <= 2);
        instrumentation.runOnMainSync(() -> {
            SearchMediaFragment film = media(2);
            // A larger viewport may already have prefetched page two.
            if (film.recyclerView.getAdapter().getItemCount() == 2) {
                film.page = 2;
                film.setRefreshing(true);
                film.listener.onLoad(2);
            }
        });
        await(() -> loaded(2, 4));
        instrumentation.runOnMainSync(() -> assertTrue(media(2).bottom));
        assertEquals(2, filmRequests.get());
        instrumentation.runOnMainSync(() -> {
            activity.keywordInput.setText("Changed");
            activity.searchKeyword("Changed");
        });
        await(() -> media(2) != null && "Changed".equals(media(2).keyword)
                && (loaded(2, 2) || loaded(2, 4)));
        instrumentation.runOnMainSync(() -> assertEquals("Changed", media(2).keyword));
        select(1);
        await(() -> (loaded(1, 2) || loaded(1, 4)) && "Changed".equals(media(1).keyword));
        instrumentation.runOnMainSync(() -> assertLayoutAndRender("media-search-bangumi.png"));
        select(2);
        await(() -> loaded(2, 2) || loaded(2, 4));
        instrumentation.runOnMainSync(() -> assertLayoutAndRender("media-search-film.png"));
    }

    @Test public void recreationRestoresSelectedCategoryAndSubmittedKeyword() {
        select(2);
        instrumentation.runOnMainSync(() -> {
            activity.keywordInput.setText("Restored");
            activity.searchKeyword("Restored");
        });
        await(() -> loaded(2, 2) || loaded(2, 4));
        SearchActivity previous = activity;
        instrumentation.runOnMainSync(activity::recreate);
        await(() -> {
            for (android.app.Activity resumed : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (resumed instanceof SearchActivity && resumed != previous) activity = (SearchActivity) resumed;
            }
            return activity != previous && activity.viewPager != null
                    && activity.viewPager.getCurrentItem() == 2 && (loaded(2, 2) || loaded(2, 4));
        });
        instrumentation.runOnMainSync(() -> {
            assertEquals("Restored", media(2).keyword);
            assertEquals("Restored", activity.keywordInput.getText().toString());
        });
    }

    @Test public void delayedOldSearchCannotOverwriteNewResults() throws Exception {
        select(2);
        instrumentation.runOnMainSync(() -> activity.searchKeyword("slow"));
        assertTrue(slowStarted.await(10, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> activity.searchKeyword("Latest"));
        await(() -> loaded(2, 2) || loaded(2, 4));
        releaseSlow.countDown();
        assertTrue(slowFinished.await(10, TimeUnit.SECONDS));
        SystemClock.sleep(200);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            SearchMediaFragment fragment = media(2);
            int count = fragment.recyclerView.getAdapter().getItemCount();
            assertTrue(count == 2 || count == 4);
            try {
                Field field = SearchMediaFragment.class.getDeclaredField("cards");
                field.setAccessible(true);
                for (VideoCard card : (ArrayList<VideoCard>) field.get(fragment))
                    assertTrue("An old request replaced new results", card.title.startsWith("Latest "));
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            android.widget.TextView title = fragment.recyclerView.findViewHolderForAdapterPosition(0)
                    .itemView.findViewById(R.id.text_title);
            assertEquals("Latest 1-0", title.getText().toString());
        });
    }

    @Test public void bothMediaCategoriesOpenExistingMediaDetails() {
        instrumentation.runOnMainSync(() -> {
            AtomicReference<Intent> opened = new AtomicReference<>();
            ContextWrapper context = new ContextWrapper(activity) {
                @Override public void startActivity(Intent intent) { opened.set(intent); }
            };
            for (String type : new String[]{SearchApi.TYPE_BANGUMI, SearchApi.TYPE_FILM}) {
                VideoCard card = new VideoCard("Title", "", "", "", 123L, "987", type);
                VideoCardAdapter adapter = new VideoCardAdapter(context, Collections.singletonList(card));
                VideoCardHolder holder = adapter.onCreateViewHolder(new FrameLayout(context), 0);
                adapter.onBindViewHolder(holder, 0);
                holder.itemView.performClick();
                assertNotNull(opened.get());
                assertEquals("media", opened.get().getStringExtra("type"));
                assertEquals(123L, opened.get().getLongExtra("aid", 0));
            }
        });
    }

    private void assertLayoutAndRender(String name) {
        View root = activity.findViewById(android.R.id.content);
        View tabs = root.findViewById(R.id.search_categories);
        View input = root.findViewById(R.id.searchbar);
        assertTrue(tabs.getWidth() > 0);
        assertTrue(tabs.getTop() >= input.getBottom());
        assertTrue(activity.viewPager.getTop() >= tabs.getBottom());
        assertTrue(activity.viewPager.getHeight() > 0);
        Rect visible = new Rect();
        View selected = ((TabLayout) tabs).getTabAt(activity.viewPager.getCurrentItem()).view;
        assertTrue(selected.getGlobalVisibleRect(visible));
        assertEquals(selected.getWidth(), visible.width());
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        try (FileOutputStream output = new FileOutputStream(new File(activity.getFilesDir(), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }

    @Test public void categoryStripCollapsesOncePerDragAndRestoresWithoutMovingTheInput() {
        select(1);instrumentation.runOnMainSync(()->activity.searchKeyword("Compact"));await(()->(loaded(1,2)||loaded(1,4)));
        int[] original=new int[2];
        instrumentation.runOnMainSync(()->{
            activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    |View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        });
        instrumentation.waitForIdleSync();SystemClock.sleep(150);
        instrumentation.runOnMainSync(()->{
            View input=activity.findViewById(R.id.searchbar);original[0]=input.getTop();original[1]=activity.viewPager.getHeight();
            activity.onSearchScrollState(media(1),androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING,false);
            activity.onSearchScrolled(media(1),Math.round(100*activity.getResources().getDisplayMetrics().density));
            assertEquals(View.GONE,activity.findViewById(R.id.search_categories).getVisibility());
        });
        instrumentation.waitForIdleSync();SystemClock.sleep(100);
        instrumentation.runOnMainSync(()->{
            assertEquals(original[0],activity.findViewById(R.id.searchbar).getTop());
            assertTrue("Collapsing categories must leave more space for results",activity.viewPager.getHeight()>original[1]);
            activity.onSearchScrolled(media(1),-300);assertEquals(View.GONE,activity.findViewById(R.id.search_categories).getVisibility());
            activity.onSearchScrollState(media(1),androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE,false);
            activity.onSearchScrolled(media(1),-300);assertEquals(View.GONE,activity.findViewById(R.id.search_categories).getVisibility());
            saveSearchScreen("search-categories-collapsed.png");
            activity.onSearchScrollState(media(1),androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING,false);
            activity.onSearchScrolled(media(1),-300);assertEquals(View.VISIBLE,activity.findViewById(R.id.search_categories).getVisibility());
            activity.onSearchScrolled(media(1),300);assertEquals(View.VISIBLE,activity.findViewById(R.id.search_categories).getVisibility());
        });
        instrumentation.waitForIdleSync();SystemClock.sleep(100);
        instrumentation.runOnMainSync(()->{
            assertEquals(original[0],activity.findViewById(R.id.searchbar).getTop());saveSearchScreen("search-categories-expanded.png");
            activity.onSearchScrollState(media(1),androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING,false);
            activity.onSearchScrolled(media(1),300);
        });
        select(2);await(()->(loaded(2,2)||loaded(2,4)));
        instrumentation.runOnMainSync(()->{
            assertEquals(View.VISIBLE,activity.findViewById(R.id.search_categories).getVisibility());
            activity.onSearchScrollState(media(1),androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING,false);
            activity.onSearchScrolled(media(1),300);assertEquals(View.VISIBLE,activity.findViewById(R.id.search_categories).getVisibility());
        });
    }

    private void saveSearchScreen(String filename) {
        View root=activity.findViewById(android.R.id.content);Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));
        try(FileOutputStream out=activity.openFileOutput(filename,0)){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}
    }

    @Test public void defaultWithoutTabsUsesDropdownAndKeepsSelectionWhenRecreated() throws Exception {
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.SEARCH_SHOW_CATEGORY_TABS);
        relaunchSearch();
        instrumentation.runOnMainSync(()->{
            assertEquals(View.GONE,activity.findViewById(R.id.search_categories).getVisibility());
            assertEquals("视频",categoryLabel());
        });
        chooseCategory(1);
        instrumentation.runOnMainSync(()->{
            assertEquals("番剧",categoryLabel());
            assertEquals(View.GONE,activity.findViewById(R.id.search_categories).getVisibility());
            activity.searchKeyword("Dropdown");
        });
        await(()->(loaded(1,2)||loaded(1,4))&&"Dropdown".equals(media(1).keyword));
        instrumentation.waitForIdleSync();SystemClock.sleep(150);
        SearchActivity beforeRecreation=activity;
        instrumentation.runOnMainSync(()->{saveSearchScreen("search-dropdown-default.png");activity.recreate();});
        await(()->{
            for(android.app.Activity candidate:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                if(candidate instanceof SearchActivity&&candidate!=beforeRecreation){activity=(SearchActivity)candidate;return activity.viewPager!=null&&activity.viewPager.getCurrentItem()==1;}
            return false;
        });
        await(()->(loaded(1,2)||loaded(1,4)));
        instrumentation.runOnMainSync(()->{assertEquals("番剧",categoryLabel());assertEquals(View.GONE,activity.findViewById(R.id.search_categories).getVisibility());});
    }

    @Test public void dropdownAndTabsStaySynchronizedAndPreferenceAppliesOnReturn() throws Exception {
        select(2);instrumentation.runOnMainSync(()->{assertEquals("影视",categoryLabel());activity.searchKeyword("Synced");});
        await(()->(loaded(2,2)||loaded(2,4)));chooseCategory(1);await(()->(loaded(1,2)||loaded(1,4)));
        instrumentation.runOnMainSync(()->{
            assertEquals("番剧",categoryLabel());
            assertEquals(1,((TabLayout)activity.findViewById(R.id.search_categories)).getSelectedTabPosition());
        });
        for(boolean enabled:new boolean[]{false,true}) {
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(com.RobinNotBad.BiliClient.activity.settings.SettingPrefActivity.class.getName(),null,false);
            android.app.Activity settings;
            try {
                instrumentation.runOnMainSync(()->activity.startActivity(new Intent(activity,com.RobinNotBad.BiliClient.activity.settings.SettingPrefActivity.class)));
                settings=instrumentation.waitForMonitorWithTimeout(monitor,5000);assertNotNull(settings);
            } finally {instrumentation.removeMonitor(monitor);}
            SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.SEARCH_SHOW_CATEGORY_TABS,enabled);
            instrumentation.runOnMainSync(settings::finish);
            await(()->ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).contains(activity));
            instrumentation.runOnMainSync(()->{
                assertEquals(enabled?View.VISIBLE:View.GONE,activity.findViewById(R.id.search_categories).getVisibility());
                assertEquals("番剧",categoryLabel());assertEquals(1,activity.viewPager.getCurrentItem());
            });
        }
    }

    @Test public void categoryPickerRemainsTouchableOnDenseRoundAndSquareScreens() throws Exception {
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.SEARCH_SHOW_CATEGORY_TABS);
        SharedPreferencesUtil.putFloat("dpi",2f);SharedPreferencesUtil.putInt("density",-1);
        for(boolean round:new boolean[]{false,true}) {
            SharedPreferencesUtil.putBoolean("player_ui_round",round);relaunchSearch();
            instrumentation.waitForIdleSync();SystemClock.sleep(150);
            instrumentation.runOnMainSync(()->{
                View picker=activity.findViewById(R.id.search_category_picker);Rect box=new Rect(),clock=new Rect();
                assertTrue(picker.getGlobalVisibleRect(box));assertEquals(picker.getWidth(),box.width());assertEquals(picker.getHeight(),box.height());
                assertTrue(activity.findViewById(R.id.timeText).getGlobalVisibleRect(clock));assertFalse(Rect.intersects(box,clock));
                android.widget.TextView text=(android.widget.TextView)picker;assertNotNull(text.getLayout());
                saveSearchScreen(round?"search-selector-dense-round.png":"search-selector-dense-square.png");
                assertEquals("Current category must be readable: round="+round+", picker="+picker.getWidth()+", text="+text.getTextSize()+", density="+activity.getResources().getDisplayMetrics().density,0,text.getLayout().getEllipsisCount(0));
                android.widget.TextView title=activity.findViewById(R.id.pageName);
                assertEquals("Search title must remain readable",0,title.getLayout().getEllipsisCount(0));
                int[] pos=new int[2];picker.getLocationInWindow(pos);long now=SystemClock.uptimeMillis();
                for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}) {
                    android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now+(action==0?0:40),action,pos[0]+picker.getWidth()/2f,pos[1]+picker.getHeight()/2f,0);
                    activity.dispatchTouchEvent(event);event.recycle();
                }
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->{
                com.RobinNotBad.BiliClient.player.PlayerOptionsMenu menu=categoryMenu();assertNotNull(menu);assertTrue(menu.isShowing());
                View root=activity.findViewById(android.R.id.content),decor=menu.getDialog().getWindow().getDecorView();
                assertTrue(decor.getWidth()<=root.getWidth());assertTrue(decor.getHeight()<=root.getHeight());menu.dismiss();
            });
        }
    }

    private String categoryLabel(){return ((android.widget.TextView)activity.findViewById(R.id.search_category_picker)).getText().toString();}
    private com.RobinNotBad.BiliClient.player.PlayerOptionsMenu categoryMenu(){
        try{Field f=SearchActivity.class.getDeclaredField("categoryPicker");f.setAccessible(true);return (com.RobinNotBad.BiliClient.player.PlayerOptionsMenu)f.get(activity);}
        catch(Exception error){throw new AssertionError(error);}
    }
    private void chooseCategory(int index){
        instrumentation.runOnMainSync(()->{activity.findViewById(R.id.search_category_picker).performClick();assertTrue(categoryMenu().isShowing());categoryMenu().getMenu().performIdentifierAction(1000+index,0);});
        instrumentation.waitForIdleSync();
    }
    private void relaunchSearch(){
        instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();
        activity=(SearchActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->activity.viewPager!=null&&activity.viewPager.getAdapter()!=null);
    }

    private SearchMediaFragment media(int index) {
        return (SearchMediaFragment) activity.getSupportFragmentManager().findFragmentByTag("f" + index);
    }

    private boolean loaded(int index, int count) {
        SearchMediaFragment fragment = media(index);
        return fragment != null && fragment.recyclerView != null && fragment.recyclerView.getAdapter() != null
                && fragment.recyclerView.getAdapter().getItemCount() == count && !fragment.isRefreshing();
    }

    private void select(int index) {
        instrumentation.runOnMainSync(() -> ((TabLayout) activity.findViewById(R.id.search_categories))
                .getTabAt(index).select());
        instrumentation.waitForIdleSync();
    }

    private void await(Check check) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> done.set(check.get()));
            if (done.get()) return;
            SystemClock.sleep(50);
        }
        AtomicReference<String> state = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            SearchFragment fragment = (SearchFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("f" + activity.viewPager.getCurrentItem());
            state.set(fragment == null ? "no fragment" : "keyword=" + fragment.keyword
                    + ", page=" + fragment.page + ", count=" + (fragment.recyclerView == null
                    || fragment.recyclerView.getAdapter() == null ? -1 : fragment.recyclerView.getAdapter().getItemCount())
                    + ", refreshing=" + fragment.isRefreshing() + ", bottom=" + fragment.bottom);
        });
        fail("Timed out waiting for search UI: " + state.get());
    }

    private interface Check { boolean get(); }
}
