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
                "wbi_mixin_key", "last_wbi", SharedPreferencesUtil.search_history}) {
            savedPrefs.put(key, preferences.getAll().get(key));
        }
        preferences.edit().putInt("tutorial_ver_search", 99)
                .putBoolean("tutorial_pager_SearchActivity", false)
                .putBoolean("search_suggestions_enable", false)
                .putBoolean(SharedPreferencesUtil.SEARCH_DEFAULT_CONTENT_ENABLE, false)
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
        await(() -> loaded(1, 2));
        assertEquals("Offscreen film tab sent an unnecessary request", 0, filmRequests.get());
        select(2);
        await(() -> loaded(2, 2));
        assertEquals(1, filmRequests.get());
        instrumentation.runOnMainSync(() -> {
            SearchMediaFragment film = media(2);
            film.page = 2;
            film.setRefreshing(true);
            film.listener.onLoad(2);
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
        await(() -> loaded(1, 2) && "Changed".equals(media(1).keyword));
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
        await(() -> loaded(2, 2));
        SearchActivity previous = activity;
        instrumentation.runOnMainSync(activity::recreate);
        await(() -> {
            for (android.app.Activity resumed : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (resumed instanceof SearchActivity && resumed != previous) activity = (SearchActivity) resumed;
            }
            return activity != previous && activity.viewPager != null
                    && activity.viewPager.getCurrentItem() == 2 && loaded(2, 2);
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
        await(() -> loaded(2, 2));
        releaseSlow.countDown();
        assertTrue(slowFinished.await(10, TimeUnit.SECONDS));
        SystemClock.sleep(200);
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            SearchMediaFragment fragment = media(2);
            assertEquals(2, fragment.recyclerView.getAdapter().getItemCount());
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
        assertTrue(input.getTop() >= tabs.getBottom());
        assertTrue(activity.viewPager.getTop() >= input.getBottom());
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
