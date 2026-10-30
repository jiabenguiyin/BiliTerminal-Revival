package com.RobinNotBad.BiliClient.activity.user.favorite;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.adapter.favorite.FavoriteFolderAdapter;
import com.RobinNotBad.BiliClient.api.FavoriteApi;
import com.RobinNotBad.BiliClient.model.FavoriteFolder;
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

/** All mutations are intercepted locally; these tests never modify a real account. */
@RunWith(AndroidJUnit4.class)
public class FavoriteFolderManageInstrumentedTest {
    private static final long FOLDER = 12345678901242L;
    private static final String INTRO = "Keep & 中文";
    private static final String COVER = "https://i0.hdslb.com/bfs/medialist/old.png";
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final ArrayList<Activity> activities = new ArrayList<>();
    private final AtomicInteger editCalls = new AtomicInteger();
    private final AtomicInteger deleteCalls = new AtomicInteger();
    private final AtomicInteger uploads = new AtomicInteger();
    private final AtomicInteger editCode = new AtomicInteger();
    private final AtomicInteger deleteCode = new AtomicInteger();
    private final AtomicBoolean deleted = new AtomicBoolean();
    private final AtomicBoolean wrongOwner = new AtomicBoolean();
    private final AtomicReference<String> serverTitle = new AtomicReference<>("Work & 学习");
    private final AtomicReference<HttpUrl> lastEdit = new AtomicReference<>();
    private final AtomicReference<HttpUrl> lastDelete = new AtomicReference<>();
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient previousClient;
    private long previousMid;
    private String previousCsrf;

    @Before public void before() throws Exception {
        Field f = NetWorkUtil.class.getDeclaredField("INSTANCE");
        f.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) f.get(null);
        previousClient = clients.get();
        previousMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        previousCsrf = SharedPreferencesUtil.getString("csrf", "");
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 42);
        SharedPreferencesUtil.putString("csrf", "folder-test-csrf");
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            JSONObject result;
            try {
                if (path.endsWith("/folder/created/list")) {
                    JSONArray list = new JSONArray().put(metadata(10042));
                    if (!deleted.get()) list.put(metadata(FOLDER));
                    result = ok(new JSONObject().put("list", list).put("count", list.length()));
                } else if (path.endsWith("/folder/info")) {
                    result = ok(metadata(Long.parseLong(chain.request().url().queryParameter("media_id"))));
                } else if (path.endsWith("/folder/edit") || path.endsWith("/folder/del")) {
                    Buffer bytes = new Buffer();
                    chain.request().body().writeTo(bytes);
                    HttpUrl body = HttpUrl.parse("https://example.invalid/?" + bytes.readUtf8());
                    int code;
                    if (path.endsWith("/edit")) {
                        editCalls.incrementAndGet(); lastEdit.set(body); code = editCode.get();
                        if (code == 0) serverTitle.set(body.queryParameter("title"));
                    } else {
                        deleteCalls.incrementAndGet(); lastDelete.set(body); code = deleteCode.get();
                        if (code == 0) deleted.set(true);
                    }
                    result = new JSONObject().put("code", code).put("message", code == 0 ? "OK" : "Denied");
                } else if (path.equals("/x/upload/web/image")) {
                    uploads.incrementAndGet();
                    throw new java.io.IOException("Folder editing must not upload any cover");
                } else result = ok(new JSONObject());
            } catch (Exception error) { throw new java.io.IOException(error); }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"), result.toString())).build();
        }).build());
    }

    @After public void after() {
        for (int i = activities.size() - 1; i >= 0; i--) inst.runOnMainSync(activities.get(i)::finish);
        inst.waitForIdleSync();
        clients.set(previousClient);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, previousMid);
        SharedPreferencesUtil.putString("csrf", previousCsrf);
    }

    @Test public void longPressEditRefreshesNameAndPreservesPrivateMetadata() {
        FavoriteFolderListActivity list = launchList();
        Instrumentation.ActivityMonitor monitor = inst.addMonitor(FavoriteFolderEditActivity.class.getName(), null, false);
        try {
            longPress(list, 2); choose(list, 0);
            FavoriteFolderEditActivity editor = (FavoriteFolderEditActivity) monitor.waitForActivityWithTimeout(7000);
            assertNotNull(editor); activities.add(editor); ready(editor);
            assertEquals(FOLDER, editor.getIntent().getLongExtra("media_id", 0));
            inst.runOnMainSync(() -> {
                assertTrue(((Switch) editor.findViewById(R.id.folder_edit_private)).isChecked());
                ((EditText) editor.findViewById(R.id.folder_edit_name)).setText("New & 中文+");
                editor.findViewById(R.id.folder_edit_save).performClick();
            });
            await(() -> editCalls.get() == 1 && editor.isFinishing());
            assertEquals("12345678901242", lastEdit.get().queryParameter("media_id"));
            assertEquals("1", lastEdit.get().queryParameter("privacy"));
            assertEquals(INTRO, lastEdit.get().queryParameter("intro"));
            assertNull(lastEdit.get().queryParameter("cover"));
            assertEquals(0, uploads.get());
            await(() -> !list.swipeRefreshLayout.isRefreshing() && "New & 中文+".equals(folderTitle(list, 2)));
            inst.runOnMainSync(() -> {
                FavoriteFolderAdapter adapter = (FavoriteFolderAdapter) list.recyclerView.getAdapter();
                FavoriteFolderAdapter.FavoriteHolder holder = adapter.onCreateViewHolder(new FrameLayout(list), 0);
                adapter.onBindViewHolder(holder, 2);
                assertEquals("New & 中文+", ((TextView) holder.itemView.findViewById(R.id.text_title)).getText().toString());
            });
        } finally { inst.removeMonitor(monitor); }
    }

    @Test public void syntheticEntriesHaveNoManagementMenuEvenWhenHolderWasRecycled() {
        FavoriteFolderListActivity list = launchList();
        inst.runOnMainSync(() -> {
            FavoriteFolderAdapter adapter = (FavoriteFolderAdapter) list.recyclerView.getAdapter();
            FavoriteFolderAdapter.FavoriteHolder holder = adapter.onCreateViewHolder(new FrameLayout(list), 0);
            adapter.onBindViewHolder(holder, 2);
            adapter.onBindViewHolder(holder, 0);
            assertFalse(holder.itemView.isLongClickable());
            adapter.onBindViewHolder(holder, adapter.getItemCount() - 1);
            assertFalse(holder.itemView.isLongClickable());
        });
        assertEquals(0, deleteCalls.get());
    }

    @Test public void cancelDeleteDoesNotSendAnyMutation() {
        FavoriteFolderListActivity list = launchList();
        longPress(list, 2); choose(list, 1);
        await(() -> currentDialog(list) != null);
        inst.runOnMainSync(() -> {
            AlertDialog dialog = currentDialog(list);
            assertTrue(((TextView) dialog.findViewById(android.R.id.message)).getText().toString().contains("其他收藏夹"));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        assertEquals(0, deleteCalls.get());
        assertFalse(deleted.get());
    }

    @Test public void confirmedDeleteUsesExactIdAndRefreshesOnlyAfterSuccess() {
        FavoriteFolderListActivity list = launchList();
        longPress(list, 2); choose(list, 1);
        await(() -> currentDialog(list) != null);
        inst.runOnMainSync(() -> currentDialog(list).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(() -> deleted.get() && !list.swipeRefreshLayout.isRefreshing()
                && list.recyclerView.getAdapter().getItemCount() == 3);
        assertEquals(1, deleteCalls.get());
        assertEquals("12345678901242", lastDelete.get().queryParameter("media_ids"));
        assertEquals("folder-test-csrf", lastDelete.get().queryParameter("csrf"));
        assertNull(lastDelete.get().queryParameter("resources"));
    }

    @Test public void failedDeleteKeepsTheFolder() {
        deleteCode.set(-111);
        FavoriteFolderListActivity list = launchList();
        longPress(list, 2); choose(list, 1);
        await(() -> currentDialog(list) != null);
        inst.runOnMainSync(() -> currentDialog(list).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(() -> deleteCalls.get() == 1 && currentDialog(list) == null);
        assertFalse(deleted.get());
        inst.runOnMainSync(() -> assertEquals(4, list.recyclerView.getAdapter().getItemCount()));
    }

    @Test public void defaultFolderCannotBeDeletedOrHaveItsNameEdited() {
        FavoriteFolderListActivity list = launchList();
        longPress(list, 1); choose(list, 1);
        await(() -> !list.swipeRefreshLayout.isRefreshing() && currentDialog(list) == null);
        assertEquals(0, deleteCalls.get());
        FavoriteFolderEditActivity editor = launchEditor(10042); ready(editor);
        inst.runOnMainSync(() -> {
            assertFalse(editor.findViewById(R.id.folder_edit_name).isEnabled());
            ((Switch) editor.findViewById(R.id.folder_edit_private)).setChecked(true);
            editor.findViewById(R.id.folder_edit_save).performClick();
        });
        await(() -> editCalls.get() == 1 && editor.isFinishing());
        assertEquals("1", lastEdit.get().queryParameter("privacy"));
        assertEquals(0, uploads.get());
    }

    @Test public void changingPrivacySendsNoCoverFieldAndNeverUploads() {
        FavoriteFolderEditActivity editor = launchEditor(FOLDER); ready(editor);
        inst.runOnMainSync(() -> {
            assertEquals(0, editor.getResources().getIdentifier("folder_edit_choose_cover", "id", editor.getPackageName()));
            ((Switch) editor.findViewById(R.id.folder_edit_private)).setChecked(false);
            editor.findViewById(R.id.folder_edit_save).performClick();
        });
        await(() -> editor.isFinishing());
        assertEquals(0, uploads.get());
        assertNull(lastEdit.get().queryParameter("cover"));
        assertEquals(INTRO, lastEdit.get().queryParameter("intro"));
        assertEquals("0", lastEdit.get().queryParameter("privacy"));
    }

    @Test public void failedSaveKeepsDraftForRetryWithoutSubmittingCover() {
        editCode.set(-111);
        FavoriteFolderEditActivity editor = launchEditor(FOLDER); ready(editor);
        inst.runOnMainSync(() -> {
            ((EditText) editor.findViewById(R.id.folder_edit_name)).setText("Draft");
            editor.findViewById(R.id.folder_edit_save).performClick();
        });
        await(() -> editCalls.get() == 1 && editor.findViewById(R.id.folder_edit_save).isEnabled());
        assertFalse(editor.isFinishing());
        assertNull(lastEdit.get().queryParameter("cover"));
        editCode.set(0);
        inst.runOnMainSync(() -> editor.findViewById(R.id.folder_edit_save).performClick());
        await(() -> editor.isFinishing());
        assertEquals("Draft", lastEdit.get().queryParameter("title"));
        assertEquals(0, uploads.get());
    }

    @Test public void accountSwitchAndForeignFolderDoNotSendMutations() throws Exception {
        FavoriteFolderEditActivity editor = launchEditor(FOLDER); ready(editor);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 77);
        inst.runOnMainSync(() -> editor.findViewById(R.id.folder_edit_save).performClick());
        assertEquals(0, editCalls.get());
        FavoriteFolder folder = new FavoriteFolder(); folder.id = FOLDER; folder.ownerMid = 42; folder.attr = 3;
        try { FavoriteApi.deleteFavoriteFolder(folder); fail("Account switch must reject deletion"); }
        catch (java.io.IOException expected) { }
        assertEquals(0, deleteCalls.get());
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 42);
        inst.runOnMainSync(editor::finish);
        await(editor::isDestroyed);
        wrongOwner.set(true);
        FavoriteFolderEditActivity foreign = launchEditor(FOLDER);
        await(() -> ((TextView) foreign.findViewById(R.id.folder_edit_status)).getText().toString().contains("只能编辑"));
        assertFalse(foreign.findViewById(R.id.folder_edit_save).isEnabled());
    }

    @Test public void editorIsScrollableAtWatchSizesAndCancelDoesNotUpload() {
        FavoriteFolderEditActivity editor = launchEditor(FOLDER); ready(editor);
        inst.runOnMainSync(() -> {
            for (int dp : new int[]{180, 240, 320}) {
                View root = android.view.LayoutInflater.from(editor).inflate(R.layout.activity_edit_favorite_folder, null);
                int size = Math.round(dp * editor.getResources().getDisplayMetrics().density);
                root.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, size, size);
                android.widget.ScrollView scroll = root.findViewById(R.id.folder_edit_scroll);
                assertTrue(scroll.getChildAt(0).getHeight() >= scroll.getHeight());
                if (dp <= 240) assertTrue(scroll.getChildAt(0).getHeight() > scroll.getHeight());
                assertTrue(root.findViewById(R.id.folder_edit_name).getWidth() > 0);
                assertTrue(root.findViewById(R.id.folder_edit_save).getWidth() <= size);
            }
            saveScreen(editor, "folder-edit.png");
            editor.finish();
        });
        assertEquals(0, uploads.get());
        assertEquals(0, editCalls.get());
    }

    private FavoriteFolderListActivity launchList() {
        FavoriteFolderListActivity list = launch(FavoriteFolderListActivity.class, new Intent());
        await(() -> list.recyclerView.getAdapter() != null && !list.swipeRefreshLayout.isRefreshing());
        return list;
    }
    private FavoriteFolderEditActivity launchEditor(long id) {
        return launch(FavoriteFolderEditActivity.class, new Intent().putExtra("media_id", id).putExtra("owner_mid", 42L));
    }
    private <T extends Activity> T launch(Class<T> type, Intent extra) {
        Intent intent = new Intent(inst.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtras(extra);
        T activity = type.cast(inst.startActivitySync(intent)); activities.add(activity); return activity;
    }
    private void ready(FavoriteFolderEditActivity editor) { await(() -> editor.findViewById(R.id.folder_edit_save).isEnabled()); }
    private void longPress(FavoriteFolderListActivity list, int index) {
        inst.runOnMainSync(() -> {
            FavoriteFolderAdapter a = (FavoriteFolderAdapter) list.recyclerView.getAdapter();
            FavoriteFolderAdapter.FavoriteHolder holder = a.onCreateViewHolder(new FrameLayout(list), 0);
            a.onBindViewHolder(holder, index); assertTrue(holder.itemView.performLongClick());
        });
    }
    private void choose(FavoriteFolderListActivity list, int index) {
        inst.runOnMainSync(() -> {
            AlertDialog d = currentDialog(list); assertNotNull(d);
            d.getListView().performItemClick(null, index, index);
        });
    }
    private String folderTitle(FavoriteFolderListActivity list, int index) {
        FavoriteFolderAdapter adapter = (FavoriteFolderAdapter) list.recyclerView.getAdapter();
        FavoriteFolderAdapter.FavoriteHolder holder = adapter.onCreateViewHolder(new FrameLayout(list), 0);
        adapter.onBindViewHolder(holder, index);
        return ((TextView) holder.itemView.findViewById(R.id.text_title)).getText().toString();
    }
    private AlertDialog currentDialog(FavoriteFolderListActivity list) {
        try { Field f = FavoriteFolderListActivity.class.getDeclaredField("activeDialog"); f.setAccessible(true); return (AlertDialog) f.get(list); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void await(Callable<Boolean> condition) {
        AtomicBoolean matched = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            inst.runOnMainSync(() -> { try { matched.set(condition.call()); } catch (Exception error) { throw new AssertionError(error); } });
            if (matched.get()) return; SystemClock.sleep(40);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Folder management state did not settle");
    }
    private JSONObject metadata(long id) throws Exception {
        return new JSONObject().put("id", id).put("mid", wrongOwner.get() ? 99 : 42)
                .put("attr", id == 10042 ? 0 : 3).put("title", id == 10042 ? "默认收藏夹" : serverTitle.get())
                .put("cover", COVER).put("intro", INTRO).put("media_count", 13);
    }
    private static JSONObject ok(JSONObject data) throws Exception { return new JSONObject().put("code", 0).put("data", data); }
    private void saveScreen(Activity activity, String name) {
        View root = activity.getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        try (FileOutputStream file = new FileOutputStream(new File(activity.getFilesDir(), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, file);
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
