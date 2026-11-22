package com.RobinNotBad.BiliClient.activity.base;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.user.HistoryActivity;
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteFolderListActivity;
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteVideoListActivity;
import com.RobinNotBad.BiliClient.activity.video.info.AddFavoriteActivity;
import com.RobinNotBad.BiliClient.adapter.favorite.FavoriteFolderAdapter;
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibrarySearchInstrumentedTest {
    private static final String QUERY = "needle & \u4e2d\u6587";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final ConcurrentLinkedQueue<HttpUrl> requests = new ConcurrentLinkedQueue<>();
    private final ArrayList<Activity> activities = new ArrayList<>();
    private final AtomicInteger favoriteCode = new AtomicInteger();
    private final AtomicBoolean deleted = new AtomicBoolean();
    private final ConcurrentLinkedQueue<Long> removedAids = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean severalFavoriteVideos = new AtomicBoolean();
    private final AtomicInteger rejectRemovalAid = new AtomicInteger();
    private final AtomicBoolean noFolders = new AtomicBoolean();
    private final AtomicBoolean aggregateFixture = new AtomicBoolean();
    private final AtomicBoolean manyFolders = new AtomicBoolean();
    private final CountDownLatch slowStarted = new CountDownLatch(1);
    private final CountDownLatch releaseSlow = new CountDownLatch(1);
    private final CountDownLatch slowReturned = new CountDownLatch(1);
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient oldClient;
    private long oldMid;
    private String oldCsrf;
    private String oldCookies;

    @Before public void setUp() throws Exception {
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) field.get(null);
        oldClient = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            HttpUrl url = chain.request().url();
            requests.add(url);
            JSONObject result;
            try {
                if (url.encodedPath().endsWith("/fav/resource/batch-del")) {
                    Buffer body = new Buffer();
                    chain.request().body().writeTo(body);
                    HttpUrl form = HttpUrl.parse("https://example.invalid/delete?" + body.readUtf8());
                    requests.add(form);
                    assertEquals("library-test-csrf", form.queryParameter("csrf"));
                    assertEquals("library-test-session", NetWorkUtil.getInfoFromCookie("SESSDATA",
                            chain.request().header("Cookie")));
                    long aid = Long.parseLong(form.queryParameter("resources").split(":")[0]);
                    if (rejectRemovalAid.get() == aid) {
                        result = new JSONObject().put("code", 11010).put("message", "Denied");
                    } else {
                        removedAids.add(aid);
                        deleted.set(true);
                        result = ok(new JSONObject());
                    }
                } else if (url.encodedPath().contains("/fav/folder/created/")) {
                    JSONArray folders = new JSONArray();
                    boolean second = "2".equals(url.queryParameter("pn"));
                    long[] ids = manyFolders.get() ? (second ? new long[]{210042}
                            : new long[]{10042,20042,30042,40042,50042,60042,70042,80042,90042,100042,
                            110042,120042,130042,140042,150042,160042,170042,180042,190042,200042})
                            : new long[]{10042, 20042};
                    if (!noFolders.get()) for (long id : ids) {
                        folders.put(new JSONObject().put("id", id).put("fid", id / 100)
                                .put("title", "Folder " + id).put("cover", "").put("media_count", 2).put("fav_state", 1));
                    }
                    JSONObject data = new JSONObject().put("list", folders);
                    if (manyFolders.get()) data.put("count", 21).put("has_more", !second);
                    result = ok(data);
                } else if (url.encodedPath().endsWith("/fav/resource/list")) {
                    boolean all = "1".equals(url.queryParameter("type"));
                    boolean searching = QUERY.equals(url.queryParameter("keyword"));
                    boolean second = "2".equals(url.queryParameter("pn"));
                    JSONArray videos = new JSONArray();
                    if (all && url.queryParameter("keyword").trim().isEmpty()) {
                        // Actual upstream response: type=1 is a search scope, not an unfiltered list.
                        result = new JSONObject().put("code", 11010).put("message", "Content does not exist");
                    } else {
                        if (all) {
                            long first = searching ? 201 : 101;
                            videos.put(favorite(first + (second ? 1 : 0))).put(favorite(first + (second ? 2 : 1)));
                        } else if (aggregateFixture.get() && !searching) {
                            if ("10042".equals(url.queryParameter("media_id"))) {
                                for (int i = 0; i < 20; i++) videos.put(favorite(500 + i).put("fav_time", 900 - i));
                            } else videos.put(favorite(600).put("fav_time", 1000))
                                    .put(favorite(500).put("fav_time", 999));
                        } else if (severalFavoriteVideos.get() && !searching) {
                            for (long id = 500; id <= 502; id++) if (!removedAids.contains(id)) videos.put(favorite(id));
                        } else if (!deleted.get()) videos.put(favorite(searching ? 501 : 500));
                        result = favoriteCode.get() == 0 ? ok(new JSONObject().put("medias", videos)
                                .put("has_more", all && !second)) : new JSONObject().put("code", favoriteCode.get()).put("message", "Denied");
                    }
                } else if (url.encodedPath().endsWith("/history/search")) {
                    String keyword = url.queryParameter("keyword");
                    if ("old".equals(keyword)) {
                        slowStarted.countDown();
                        try { releaseSlow.await(8, TimeUnit.SECONDS); }
                        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                        slowReturned.countDown();
                    }
                    long id = "old".equals(keyword) ? 991 : "new".equals(keyword) ? 992
                            : "2".equals(url.queryParameter("pn")) ? 202 : 201;
                    result = ok(new JSONObject().put("list", new JSONArray().put(history(id)))
                            .put("has_more", QUERY.equals(keyword) && !"2".equals(url.queryParameter("pn"))));
                } else if (url.encodedPath().endsWith("/history/cursor")) {
                    result = ok(new JSONObject().put("list", new JSONArray().put(history(1)))
                            .put("cursor", new JSONObject().put("max", 77).put("view_at", 88).put("business", "archive")));
                } else result = ok(new JSONObject());
            } catch (Exception error) { throw new java.io.IOException(error); }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"), result.toString())).build();
        }).build());
        oldMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        oldCsrf = SharedPreferencesUtil.getString("csrf", "");
        oldCookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 42);
        SharedPreferencesUtil.putString("csrf", "library-test-csrf");
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies,
                "DedeUserID=42; SESSDATA=library-test-session; bili_jct=library-test-csrf");
    }

    @After public void tearDown() {
        releaseSlow.countDown();
        for (int i = activities.size() - 1; i >= 0; i--) {
            Activity activity = activities.get(i);
            instrumentation.runOnMainSync(activity::finish);
        }
        instrumentation.waitForIdleSync();
        clients.set(oldClient);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, oldMid);
        SharedPreferencesUtil.putString("csrf", oldCsrf);
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, oldCookies);
    }

    @Test public void allFavoritesEntrySearchPagingAndClearWork() {
        aggregateFixture.set(true);
        FavoriteFolderListActivity folders = launch(FavoriteFolderListActivity.class, null);
        await(() -> folders.recyclerView.getAdapter() != null);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(FavoriteVideoListActivity.class.getName(), null, false);
        try {
            instrumentation.runOnMainSync(() -> {
                FavoriteFolderAdapter adapter = (FavoriteFolderAdapter) folders.recyclerView.getAdapter();
                assertEquals(4, adapter.getItemCount());
                FavoriteFolderAdapter.FavoriteHolder holder = adapter.onCreateViewHolder(new FrameLayout(folders), 0);
                adapter.onBindViewHolder(holder, 0);
                assertEquals("\u5168\u90e8", ((TextView) holder.itemView.findViewById(R.id.text_title)).getText().toString());
                holder.itemView.performClick();
            });
            SearchableVideoListActivity all = (SearchableVideoListActivity) monitor.waitForActivityWithTimeout(7000);
            assertNotNull(all);
            activities.add(all);
            await(() -> all.videoList.size() == 20 && all.videoList.get(0).aid == 600);
            instrumentation.runOnMainSync(() -> all.listener.onLoad(2));
            await(() -> all.bottom && all.videoList.size() == 21);
            assertTrue(hasRequest("/fav/resource/list", "media_id", "10042", "type", "0"));
            assertTrue(hasRequest("/fav/resource/list", "media_id", "20042", "type", "0"));
            assertFalse(hasRequest("/fav/resource/list", "keyword", "", "type", "1"));
            submit(all, QUERY);
            await(() -> all.videoList.size() == 2 && all.videoList.get(0).aid == 201);
            instrumentation.runOnMainSync(() -> all.listener.onLoad(2));
            await(() -> all.bottom && all.videoList.size() == 3 && all.videoList.get(2).aid == 203);
            assertTrue(hasRequest("/fav/resource/list", "keyword", QUERY, "type", "1"));
            assertTrue(hasRequest("/fav/resource/list", "keyword", QUERY, "pn", "2"));
            instrumentation.runOnMainSync(() -> all.findViewById(R.id.library_clear).performClick());
            await(() -> all.videoList.size() == 20 && all.videoList.get(0).aid == 600);
            saveScreen(all, "library-favorites.png");
        } finally { instrumentation.removeMonitor(monitor); }
    }

    @Test public void historySearchRejectsOldResponsesAndRestoresCursor() throws Exception {
        SearchableVideoListActivity history = launch(HistoryActivity.class, null);
        await(() -> history.videoList.size() == 1);
        submit(history, "old");
        assertTrue(slowStarted.await(5, TimeUnit.SECONDS));
        submit(history, "new");
        await(() -> history.videoList.size() == 1 && history.videoList.get(0).aid == 992);
        releaseSlow.countDown();
        assertTrue(slowReturned.await(5, TimeUnit.SECONDS));
        SystemClock.sleep(300);
        instrumentation.runOnMainSync(() -> assertEquals(992L, history.videoList.get(0).aid));
        submit(history, QUERY);
        await(() -> history.videoList.size() == 1 && history.videoList.get(0).aid == 201);
        instrumentation.runOnMainSync(() -> history.listener.onLoad(2));
        await(() -> history.bottom && history.videoList.size() == 2);
        assertTrue(hasRequest("/history/search", "keyword", QUERY, "pn", "2"));
        instrumentation.runOnMainSync(() -> history.findViewById(R.id.library_clear).performClick());
        await(() -> history.videoList.size() == 1 && history.videoList.get(0).aid == 1);
        assertTrue(hasRequest("/history/cursor", "view_at", "0", "max", "0"));
        saveScreen(history, "library-history.png");
    }

    @Test public void allFavoritesManageFoldersRetainsSearchAndNeverDeletesFromAnchor() {
        aggregateFixture.set(true);
        SearchableVideoListActivity all = launch(FavoriteVideoListActivity.class,
                new Intent().putExtra("all_folders", true).putExtra("mid", 42L));
        await(() -> all.videoList.size() == 20);
        submit(all, QUERY);
        await(() -> all.videoList.size() == 2 && all.videoList.get(0).aid == 201);
        await(() -> all.recyclerView.findViewHolderForAdapterPosition(0) != null);
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(AddFavoriteActivity.class.getName(), null, false);
        try {
            instrumentation.runOnMainSync(() -> {
                VideoCardHolder row = (VideoCardHolder) all.recyclerView.findViewHolderForAdapterPosition(0);
                row.itemView.performLongClick();
            });
            Activity manager = monitor.waitForActivityWithTimeout(7000);
            assertNotNull(manager);
            activities.add(manager);
            assertEquals(201L, manager.getIntent().getLongExtra("aid", 0));
            await(() -> ((RefreshListActivity) manager).recyclerView.getAdapter() != null);
            instrumentation.runOnMainSync(manager::finish);
            await(() -> all.videoList.size() == 2 && !all.swipeRefreshLayout.isRefreshing());
            instrumentation.runOnMainSync(() -> assertEquals(QUERY,
                    ((EditText) all.findViewById(R.id.library_keyword)).getText().toString()));
            assertFalse(deleted.get());
        } finally { instrumentation.removeMonitor(monitor); }
    }

    @Test public void folderSearchAndDeletionKeepTheSelectedFolderId() {
        FavoriteVideoListActivity folder = ownFolder();
        await(() -> folder.videoList.size() == 1);
        submit(folder, QUERY);
        await(() -> folder.videoList.size() == 1 && folder.videoList.get(0).aid == 501);
        assertTrue(hasRequest("/fav/resource/list", "keyword", QUERY, "type", "0"));
        instrumentation.runOnMainSync(() -> folder.findViewById(R.id.library_clear).performClick());
        await(() -> folder.videoList.size() == 1 && folder.videoList.get(0).aid == 500);
        openRemovalMenu(folder);
        chooseRemovalAction(folder, 1);
        assertFalse(deleted.get());
        clickRemovalButton(folder, AlertDialog.BUTTON_POSITIVE);
        await(() -> deleted.get() && folder.videoList.isEmpty() && folder.bottom);
        assertTrue(hasRequest("/delete", "resources", "500:2", "media_id", "10042"));
        instrumentation.runOnMainSync(() -> {
            assertEquals(View.VISIBLE, folder.findViewById(R.id.library_search_bar).getVisibility());
            assertEquals(View.VISIBLE, folder.emptyView.getVisibility());
        });
    }

    @Test public void multipleRemovalKeepsUncheckedVideosAndCanBeCancelledOutside() {
        severalFavoriteVideos.set(true);
        FavoriteVideoListActivity folder = ownFolder();
        await(() -> ((SearchableVideoListActivity) folder).videoList.size() == 3);
        openRemovalMenu(folder);
        chooseRemovalAction(folder, 0);
        instrumentation.runOnMainSync(() -> {
            AlertDialog dialog = removalDialog(folder);
            assertTrue(dialog.getListView().isItemChecked(0));
            assertFalse(dialog.getListView().isItemChecked(1));
            MotionEvent outside = MotionEvent.obtain(0, 0, MotionEvent.ACTION_OUTSIDE, -100, -100, 0);
            try { dialog.onTouchEvent(outside); } finally { outside.recycle(); }
            assertFalse(dialog.isShowing());
        });
        assertFalse(deleted.get());

        openRemovalMenu(folder);
        chooseRemovalAction(folder, 0);
        clickRemovalButton(folder, AlertDialog.BUTTON_NEGATIVE);
        assertFalse(deleted.get());

        openRemovalMenu(folder);
        chooseRemovalAction(folder, 0);
        instrumentation.runOnMainSync(() -> {
            AlertDialog dialog = removalDialog(folder);
            clickChoice(dialog, 0);
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
            clickChoice(dialog, 0);
            clickChoice(dialog, 1);
            assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        });
        clickRemovalButton(folder, AlertDialog.BUTTON_POSITIVE);
        SearchableVideoListActivity list = folder;
        await(() -> list.videoList.size() == 1 && list.videoList.get(0).aid == 502);
        assertTrue(hasRequest("/delete", "resources", "500:2", "media_id", "10042"));
        assertTrue(hasRequest("/delete", "resources", "501:2", "media_id", "10042"));
        assertFalse(hasRequest("/delete", "resources", "502:2", "media_id", "10042"));
    }

    @Test public void removalStopsAfterPartialFailureAndReloadsTheFolder() {
        severalFavoriteVideos.set(true);
        rejectRemovalAid.set(501);
        FavoriteVideoListActivity folder = ownFolder();
        await(() -> ((SearchableVideoListActivity) folder).videoList.size() == 3);
        openRemovalMenu(folder);
        chooseRemovalAction(folder, 0);
        instrumentation.runOnMainSync(() -> {
            AlertDialog dialog = removalDialog(folder);
            clickChoice(dialog, 1);
            clickChoice(dialog, 2);
        });
        clickRemovalButton(folder, AlertDialog.BUTTON_POSITIVE);
        SearchableVideoListActivity list = folder;
        await(() -> list.videoList.size() == 2 && list.videoList.get(0).aid == 501);
        assertTrue(hasRequest("/delete", "resources", "501:2", "media_id", "10042"));
        assertFalse(hasRequest("/delete", "resources", "502:2", "media_id", "10042"));
    }

    @Test public void confirmationDoesNotRemoveFavoritesAfterAccountChanges() {
        FavoriteVideoListActivity folder = ownFolder();
        openRemovalMenu(folder);
        chooseRemovalAction(folder, 1);
        instrumentation.runOnMainSync(() -> SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 43));
        clickRemovalButton(folder, AlertDialog.BUTTON_POSITIVE);
        assertFalse(hasRequest("/delete", "resources", "500:2", "media_id", "10042"));
    }

    private FavoriteVideoListActivity ownFolder() {
        return launch(FavoriteVideoListActivity.class,
                new Intent().putExtra("fid", 10042L).putExtra("mid", 42L).putExtra("name", "Folder"));
    }

    private void openRemovalMenu(FavoriteVideoListActivity folder) {
        await(() -> folder.recyclerView.findViewHolderForAdapterPosition(0) != null
                && !folder.swipeRefreshLayout.isRefreshing());
        instrumentation.runOnMainSync(() -> assertTrue(folder.recyclerView
                .findViewHolderForAdapterPosition(0).itemView.performLongClick()));
    }

    private void chooseRemovalAction(FavoriteVideoListActivity folder, int index) {
        instrumentation.runOnMainSync(() -> clickChoice(removalDialog(folder), index));
        instrumentation.waitForIdleSync();
    }

    private static void clickChoice(AlertDialog dialog, int index) {
        ListView list = dialog.getListView();
        list.performItemClick(list.getAdapter().getView(index, null, list), index, list.getItemIdAtPosition(index));
    }

    private void clickRemovalButton(FavoriteVideoListActivity folder, int button) {
        instrumentation.runOnMainSync(() -> removalDialog(folder).getButton(button).performClick());
        instrumentation.waitForIdleSync();
    }

    private static AlertDialog removalDialog(FavoriteVideoListActivity folder) {
        try {
            Field field = FavoriteVideoListActivity.class.getDeclaredField("removalDialog");
            field.setAccessible(true);
            AlertDialog dialog = (AlertDialog) field.get(folder);
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
            return dialog;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @Test public void emptyResultsAndNarrowSearchBarsDoNotCoverControls() {
        noFolders.set(true);
        SearchableVideoListActivity all = launch(FavoriteVideoListActivity.class,
                new Intent().putExtra("all_folders", true).putExtra("mid", 42L));
        await(() -> all.bottom && all.emptyView.getVisibility() == View.VISIBLE);
        assertFalse(hasRequest("/fav/resource/list", "pn", "1", "type", "1"));
        instrumentation.runOnMainSync(() -> {
            Rect empty = new Rect();
            Rect bar = new Rect();
            all.emptyView.getGlobalVisibleRect(empty);
            all.findViewById(R.id.library_search_bar).getGlobalVisibleRect(bar);
            assertTrue(empty.top >= bar.bottom);
            for (int widthDp : new int[]{120, 170, 200, 400}) {
                View search = LayoutInflater.from(all).inflate(R.layout.view_library_search, null);
                EditText input = search.findViewById(R.id.library_keyword);
                input.setText(QUERY + QUERY);
                int width = Math.round(widthDp * all.getResources().getDisplayMetrics().density);
                search.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                search.layout(0, 0, width, search.getMeasuredHeight());
                View clear = search.findViewById(R.id.library_clear);
                View submit = search.findViewById(R.id.library_search);
                assertTrue(input.getWidth() > 0);
                assertTrue(input.getRight() <= clear.getLeft());
                assertTrue(clear.getRight() <= submit.getLeft());
                assertTrue(submit.getRight() <= width);
            }
        });
    }

    @Test public void permissionErrorsCanBeRetriedWithoutLosingSearch() {
        favoriteCode.set(-403);
        SearchableVideoListActivity folder = launch(FavoriteVideoListActivity.class,
                new Intent().putExtra("fid", 10042L).putExtra("mid", 42L).putExtra("name", "Folder"));
        await(() -> folder.emptyView.getVisibility() == View.VISIBLE);
        assertTrue(folder.videoList.isEmpty());
        favoriteCode.set(0);
        submit(folder, QUERY);
        await(() -> folder.videoList.size() == 1 && folder.videoList.get(0).aid == 501);
    }

    @Test public void allFavoritesErrorsRemainVisibleAndRefreshCanRecover() {
        aggregateFixture.set(true);
        favoriteCode.set(-403);
        SearchableVideoListActivity all = launch(FavoriteVideoListActivity.class,
                new Intent().putExtra("all_folders", true).putExtra("mid", 42L));
        await(() -> all.emptyView.getVisibility() == View.VISIBLE && !all.swipeRefreshLayout.isRefreshing());
        assertTrue(all.emptyView.getText().toString().contains("-403"));
        assertTrue(all.videoList.isEmpty());
        favoriteCode.set(0);
        instrumentation.runOnMainSync(() -> all.emptyView.performClick());
        await(() -> all.videoList.size() == 20 && all.videoList.get(0).aid == 600);
        assertFalse(hasRequest("/fav/resource/list", "keyword", "", "type", "1"));
    }

    @Test public void allFolderPagesAreLoadedWithTheirFullMediaIds() throws Exception {
        manyFolders.set(true);
        java.util.List<com.RobinNotBad.BiliClient.model.FavoriteFolder> folders =
                com.RobinNotBad.BiliClient.api.FavoriteApi.getFavoriteFolders(42);
        assertEquals(21, folders.size());
        assertEquals(10042L, folders.get(0).id);
        assertEquals(210042L, folders.get(20).id);
        assertTrue(hasRequest("/fav/folder/created/list", "pn", "2", "ps", "20"));
    }

    private <T extends Activity> T launch(Class<T> type, Intent extras) {
        Intent intent = new Intent(instrumentation.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) intent.putExtras(extras);
        T activity = type.cast(instrumentation.startActivitySync(intent));
        activities.add(activity);
        return activity;
    }

    private void submit(SearchableVideoListActivity activity, String query) {
        instrumentation.runOnMainSync(() -> {
            ((EditText) activity.findViewById(R.id.library_keyword)).setText(query);
            activity.findViewById(R.id.library_search).performClick();
        });
    }

    private boolean hasRequest(String path, String key, String value, String key2, String value2) {
        for (HttpUrl request : requests) if (request.encodedPath().endsWith(path)
                && value.equals(request.queryParameter(key)) && value2.equals(request.queryParameter(key2))) return true;
        return false;
    }

    private void await(Callable<Boolean> predicate) {
        AtomicBoolean matched = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 7000;
        do {
            instrumentation.runOnMainSync(() -> {
                try { matched.set(predicate.call()); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            if (matched.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Library state did not settle");
    }

    private void saveScreen(Activity activity, String name) {
        instrumentation.waitForIdleSync();
        SystemClock.sleep(350);
        instrumentation.runOnMainSync(() -> {
            View root = activity.getWindow().getDecorView();
            Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap));
            try (FileOutputStream output = new FileOutputStream(new File(activity.getFilesDir(), name))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            } catch (Exception error) { throw new AssertionError(error); }
            finally { bitmap.recycle(); }
        });
    }

    private static JSONObject favorite(long id) throws Exception {
        return new JSONObject().put("id", id).put("type", 2).put("title", "Video " + id).put("cover", "")
                .put("bvid", "BV" + id).put("upper", new JSONObject().put("name", "UP"));
    }

    private static JSONObject history(long id) throws Exception {
        return new JSONObject().put("title", "History " + id).put("cover", "").put("author_name", "UP")
                .put("progress", 60).put("history", new JSONObject().put("oid", id).put("bvid", "BV" + id)
                        .put("business", "archive"));
    }

    private static JSONObject ok(JSONObject data) throws Exception {
        return new JSONObject().put("code", 0).put("data", data);
    }
}
