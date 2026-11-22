package com.RobinNotBad.BiliClient.activity.user;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.BuildConfig;
import com.RobinNotBad.BiliClient.activity.SplashActivity;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.activity.settings.login.SpecialLoginActivity;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.LoginTextFile;
import com.RobinNotBad.BiliClient.util.MySpaceOrder;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MySpacePersonalizationInstrumentedTest {
    private final Instrumentation ui = InstrumentationRegistry.getInstrumentation();
    private final List<Activity> owners = new ArrayList<>();
    private final List<File> files = new ArrayList<>();
    private final List<Instrumentation.ActivityMonitor> monitors = new ArrayList<>();
    private Map<String, ?> saved;
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient original;

    @Before public void setup() throws Exception {
        saved = new HashMap<>(SharedPreferencesUtil.getSharedPreferences().getAll());
        SharedPreferencesUtil.getSharedPreferences().edit().putFloat("dpi", 1f).putInt("density", -1)
                .putBoolean("player_ui_round", false).putBoolean("ui_landscape", false)
                .putBoolean(SharedPreferencesUtil.ASYNC_INFLATE_ENABLE, false)
                .putBoolean("creative_enable", false).remove(MySpaceOrder.PREFERENCE).commit();
        Field network = NetWorkUtil.class.getDeclaredField("INSTANCE");
        network.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>)network.get(null);
        original = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> { throw new IOException("Offline fixture"); }).build());
        assertTrue(AccountManager.saveLogin(cookie(42), "fixture-refresh"));
    }

    @After public void cleanup() {
        ui.runOnMainSync(() -> { for (Activity a : owners) if (!a.isFinishing()) a.finish(); });
        ui.waitForIdleSync();
        for (Instrumentation.ActivityMonitor monitor : monitors) ui.removeMonitor(monitor);
        for (File file : files) if (file.exists()) assertTrue(file.delete());
        clients.set(original);
        SharedPreferences.Editor restore = SharedPreferencesUtil.getSharedPreferences().edit().clear();
        for (Map.Entry<String, ?> entry : saved.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) restore.putString(key, (String)value);
            else if (value instanceof Long) restore.putLong(key, (Long)value);
            else if (value instanceof Integer) restore.putInt(key, (Integer)value);
            else if (value instanceof Float) restore.putFloat(key, (Float)value);
            else if (value instanceof Boolean) restore.putBoolean(key, (Boolean)value);
            else if (value instanceof Set) restore.putStringSet(key, (Set<String>)value);
        }
        assertTrue(restore.commit());
        NetWorkUtil.refreshHeaders();
    }

    @Test public void savedOrderKeepsProfilePinnedAndHiddenActionsHidden() throws Exception {
        SharedPreferencesUtil.putString(MySpaceOrder.PREFERENCE, "account_switch;favorite");
        MySpaceActivity activity = launch(MySpaceActivity.class, true);
        await(() -> activity.findViewById(R.id.myspace_actions) != null);
        LinearLayout actions = activity.findViewById(R.id.myspace_actions);
        assertEquals(R.id.myinfo, actions.getChildAt(0).getId());
        assertEquals(R.id.account_switch, actions.getChildAt(1).getId());
        assertEquals(R.id.favorite, actions.getChildAt(2).getId());
        assertEquals(View.GONE, activity.findViewById(R.id.creative).getVisibility());
        Rect sort = bounds(activity.findViewById(R.id.myspace_sort));
        Rect title = bounds(activity.findViewById(R.id.pageName));
        Rect time = bounds(activity.findViewById(R.id.timeText));
        assertTrue(sort.width() > 0 && sort.height() > 0);
        assertFalse(Rect.intersects(sort, title));
        assertFalse(Rect.intersects(sort, time));
    }

    @Test public void headerKeepsSortBesideTitleOnStandardAndRoundLayouts() throws Exception {
        assertTrue(BuildConfig.BETA);
        MySpaceActivity standard = launch(MySpaceActivity.class, true);
        await(() -> standard.findViewById(R.id.myspace_header_group) != null);
        assertHeaderPlacement(standard, false);
        ui.runOnMainSync(standard::finish);
        ui.waitForIdleSync();

        SharedPreferencesUtil.putBoolean("player_ui_round", true);
        MySpaceActivity round = launch(MySpaceActivity.class, true);
        await(() -> round.findViewById(R.id.myspace_header_group) != null);
        assertHeaderPlacement(round, true);
    }

    private void assertHeaderPlacement(MySpaceActivity activity, boolean round) {
        Rect top = bounds(activity.findViewById(R.id.top));
        Rect group = bounds(activity.findViewById(R.id.myspace_header_group));
        Rect title = bounds(activity.findViewById(R.id.pageName));
        Rect sort = bounds(activity.findViewById(R.id.myspace_sort));
        Rect clock = bounds(activity.findViewById(R.id.timeText));
        int tolerance = Math.round(12 * activity.getResources().getDisplayMetrics().density);
        assertFalse(Rect.intersects(sort, title));
        assertFalse(Rect.intersects(sort, clock));
        assertTrue("Sort action should follow the title", sort.left >= title.right);
        assertTrue("Title and sort action should remain a compact group", sort.left - title.right <= tolerance);
        if (round) {
            assertTrue("Round title and sort action should be centered together", Math.abs(group.centerX() - top.centerX()) <= 1);
            assertTrue("Round clock should stay centered", Math.abs(clock.centerX() - top.centerX()) <= tolerance);
            assertTrue("Header row should sit below the clock", group.top > clock.bottom);
        } else {
            assertTrue("Standard header should align to the start edge", group.left - top.left <= tolerance);
            assertTrue("Wide layout clock should remain on the opposite side", clock.centerX() > group.centerX());
        }
    }

    @Test public void profileSearchStaysBesideTitleBelowRoundClockAndStillOpens() {
        prepareProfileHeader();
        SharedPreferencesUtil.putBoolean("player_ui_round", true);
        UserInfoActivity activity = launch(UserInfoActivity.class, true);
        await(() -> activity.findViewById(R.id.user_video_search).getWidth() > 0 && activity.hasWindowFocus());
        assertProfileHeader(activity, true);
        captureHeader(activity, "profile-round.png");
        Rect search = bounds(activity.findViewById(R.id.user_video_search));
        long down = SystemClock.uptimeMillis();
        pointer(down, MotionEvent.ACTION_DOWN, search.centerX(), search.centerY());
        pointer(down, MotionEvent.ACTION_UP, search.centerX(), search.centerY());
        await(() -> !activity.hasWindowFocus());
        ui.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        await(activity::hasWindowFocus);
        SharedPreferencesUtil.putBoolean("player_ui_round", false);
        ui.runOnMainSync(activity::setRound);
        ui.waitForIdleSync();
        assertProfileHeader(activity, false);
        captureHeader(activity, "profile-standard.png");
    }

    @Test public void enlargedRoundTitlesKeepBothActionsVisible() {
        prepareProfileHeader();
        SharedPreferencesUtil.putFloat("dpi", 1.4f);
        SharedPreferencesUtil.putBoolean("player_ui_round", true);
        MySpaceActivity mine = launch(MySpaceActivity.class, true);
        await(() -> mine.findViewById(R.id.myspace_header_group) != null);
        ui.runOnMainSync(() -> ((android.widget.TextView) mine.findViewById(R.id.pageName)).setText("我的个人页面和账户信息"));
        ui.waitForIdleSync();
        assertHeaderPlacement(mine, true);
        assertFullyVisible(mine.findViewById(R.id.myspace_sort));
        assertInsideRoundHeader(mine, R.id.myspace_sort);
        captureHeader(mine, "myspace-round-large.png");
        ui.runOnMainSync(mine::finish);
        ui.waitForIdleSync();
        UserInfoActivity profile = launch(UserInfoActivity.class, true);
        await(() -> profile.findViewById(R.id.user_video_search).getWidth() > 0);
        ui.runOnMainSync(() -> profile.setPageName("用户信息和投稿动态列表"));
        ui.waitForIdleSync();
        assertProfileHeader(profile, true);
        assertFullyVisible(profile.findViewById(R.id.user_video_search));
        assertInsideRoundHeader(profile, R.id.user_video_search);
        captureHeader(profile, "profile-round-large.png");
    }

    private void prepareProfileHeader() {
        SharedPreferences.Editor prefs = SharedPreferencesUtil.getSharedPreferences().edit();
        for (String tutorial : ui.getTargetContext().getResources().getStringArray(R.array.tutorial_list))
            prefs.putInt("tutorial_ver_" + tutorial, Integer.MAX_VALUE);
        prefs.putBoolean("tutorial_pager_UserInfoActivity", false).commit();
    }

    private void assertProfileHeader(UserInfoActivity activity, boolean round) {
        Rect top = bounds(activity.findViewById(R.id.top));
        Rect row = bounds(activity.findViewById(R.id.userinfo_header_group));
        Rect title = bounds(activity.findViewById(R.id.pageName));
        Rect search = bounds(activity.findViewById(R.id.user_video_search));
        Rect clock = bounds(activity.findViewById(R.id.timeText));
        assertFalse(Rect.intersects(title, search));
        assertFalse(Rect.intersects(search, clock));
        assertTrue(search.left >= title.right);
        assertTrue(Math.abs(title.centerY() - search.centerY()) <= 1);
        if (round) {
            assertTrue(Math.abs(row.centerX() - top.centerX()) <= 1);
            assertTrue(Math.abs(clock.centerX() - top.centerX()) <= 1);
            assertTrue(row.top > clock.bottom);
            assertTrue(search.left - title.right <= Math.ceil(5 * activity.getResources().getDisplayMetrics().density));
        } else {
            assertTrue(search.right < clock.left);
        }
        assertTrue(bounds(activity.findViewById(R.id.viewPager)).top >= top.bottom);
    }

    private void assertFullyVisible(View view) {
        Rect visible = bounds(view);
        assertEquals(view.getWidth(), visible.width());
        assertEquals(view.getHeight(), visible.height());
    }

    private void assertInsideRoundHeader(Activity activity, int id) {
        Rect top = bounds(activity.findViewById(R.id.top));
        Rect action = bounds(activity.findViewById(id));
        double radius = top.width() / 2d;
        double cx = top.left + radius, cy = top.top + radius;
        for (int x : new int[]{action.left, action.right}) for (int y : new int[]{action.top, action.bottom})
            assertTrue("Action corner would be clipped by circular display: " + action,
                    Math.pow(x - cx, 2) + Math.pow(y - cy, 2) <= radius * radius);
    }

    private void captureHeader(Activity activity, String name) {
        ui.runOnMainSync(() -> {
            View top = activity.findViewById(R.id.top);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(top.getWidth(), top.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            try (FileOutputStream output = new FileOutputStream(new File(activity.getFilesDir(), name))) {
                top.draw(new android.graphics.Canvas(bitmap));
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            } catch (IOException error) { throw new AssertionError(error); }
            finally { bitmap.recycle(); }
        });
    }

    @Test public void topButtonOpensEditorAndMovesAreAppliedOnReturn() throws Exception {
        MySpaceActivity activity = launch(MySpaceActivity.class, true);
        await(() -> activity.findViewById(R.id.myspace_sort) != null);
        Instrumentation.ActivityMonitor editor = ui.addMonitor(MySpaceSortActivity.class.getName(), null, false);
        monitors.add(editor);
        ui.runOnMainSync(() -> activity.findViewById(R.id.myspace_sort).performClick());
        MySpaceSortActivity sort = (MySpaceSortActivity)editor.waitForActivityWithTimeout(5000);
        assertNotNull(sort); owners.add(sort);
        RecyclerView list = sort.findViewById(R.id.recyclerView);
        await(() -> list.findViewHolderForAdapterPosition(0) != null);
        ui.runOnMainSync(() -> list.findViewHolderForAdapterPosition(0).itemView.performClick());
        AlertDialog dialog = dialog(sort);
        ui.runOnMainSync(() -> dialog.getListView().performItemClick(null, 3, 3));
        assertEquals("edit_profile", lastKey());
        ui.runOnMainSync(sort::finish);
        await(() -> {
            AtomicReference<Boolean> applied = new AtomicReference<>(false);
            ui.runOnMainSync(() -> {
                LinearLayout actions = activity.findViewById(R.id.myspace_actions);
                applied.set(actions.getChildCount() == 13 && actions.getChildAt(12).getId() == R.id.edit_profile);
            });
            return applied.get();
        });
        assertEquals(R.id.myinfo, ((LinearLayout)activity.findViewById(R.id.myspace_actions)).getChildAt(0).getId());
    }

    @Test public void resetRequiresConfirmationAndSurvivesReopening() throws Exception {
        SharedPreferencesUtil.putString(MySpaceOrder.PREFERENCE, "account_switch;favorite");
        MySpaceSortActivity sort = launch(MySpaceSortActivity.class, true);
        ui.runOnMainSync(() -> sort.findViewById(R.id.myspace_order_reset).performClick());
        ui.runOnMainSync(() -> dialog(sort).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        await(() -> dialog(sort) == null);
        assertTrue(SharedPreferencesUtil.getString(MySpaceOrder.PREFERENCE, "").startsWith("account_switch"));
        ui.runOnMainSync(() -> sort.findViewById(R.id.myspace_order_reset).performClick());
        ui.runOnMainSync(() -> dialog(sort).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(() -> SharedPreferencesUtil.getString(MySpaceOrder.PREFERENCE, "").startsWith("edit_profile;follow"));
        assertEquals(MySpaceOrder.encode(MySpaceOrder.parse("")), SharedPreferencesUtil.getString(MySpaceOrder.PREFERENCE, ""));
        ui.runOnMainSync(sort::finish);
        MySpaceActivity home = launch(MySpaceActivity.class, true);
        await(() -> home.findViewById(R.id.myspace_actions) != null);
        assertEquals(R.id.edit_profile, ((LinearLayout)home.findViewById(R.id.myspace_actions)).getChildAt(1).getId());
    }

    @Test public void draggingChangesOrderWithoutRemovingActions() throws Exception {
        MySpaceSortActivity sort = launch(MySpaceSortActivity.class, true);
        RecyclerView list = sort.findViewById(R.id.recyclerView);
        await(() -> list.findViewHolderForAdapterPosition(1) != null);
        await(list::hasWindowFocus);
        ui.waitForIdleSync();
        Rect start = bounds(list.findViewHolderForAdapterPosition(0).itemView);
        Rect end = bounds(list.findViewHolderForAdapterPosition(1).itemView);
        long down = SystemClock.uptimeMillis();
        pointer(down, MotionEvent.ACTION_DOWN, start.centerX(), start.centerY());
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 200);
        float destination = end.centerY() + end.height() / 3f;
        for (int step = 1; step <= 8; step++) {
            pointer(down, MotionEvent.ACTION_MOVE, start.centerX(), start.centerY() + (destination - start.centerY()) * step / 8f);
            SystemClock.sleep(35);
        }
        SystemClock.sleep(200);
        pointer(down, MotionEvent.ACTION_UP, end.centerX(), destination);
        await(() -> SharedPreferencesUtil.getString(MySpaceOrder.PREFERENCE, "").startsWith("follow;edit_profile"));
        assertEquals(12, list.getAdapter().getItemCount());
    }

    @Test public void pickedFileFillsInputAndOnlyConfirmChangesAccount() throws Exception {
        File file = file("import");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{(byte)255, (byte)254});
            out.write(LoginTextFile.encode(cookie(43), "fixture-43").getBytes("UTF-16LE"));
        }
        SpecialLoginActivity activity = launch(SpecialLoginActivity.class, true);
        pick(Intent.ACTION_OPEN_DOCUMENT, Activity.RESULT_OK, uri(file));
        ui.runOnMainSync(() -> activity.findViewById(R.id.login_file_action).performClick());
        await(() -> input(activity).getText().toString().contains("fixture-43"));
        assertEquals(42, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
        assertEquals(View.GONE, activity.findViewById(R.id.copy).getVisibility());
        monitors.add(ui.addMonitor(SplashActivity.class.getName(), new Instrumentation.ActivityResult(0, null), true));
        ui.runOnMainSync(() -> activity.findViewById(R.id.confirm).performClick());
        assertEquals(43, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
    }

    @Test public void canceledAndInvalidImportsKeepExistingTextAndLogin() throws Exception {
        SpecialLoginActivity activity = launch(SpecialLoginActivity.class, true);
        String text = LoginTextFile.encode(cookie(42), "keep");
        ui.runOnMainSync(() -> input(activity).setText(text));
        pick(Intent.ACTION_OPEN_DOCUMENT, Activity.RESULT_CANCELED, null);
        ui.runOnMainSync(() -> activity.findViewById(R.id.login_file_action).performClick());
        ui.waitForIdleSync();
        assertEquals(text, input(activity).getText().toString());
        File invalid = file("invalid");
        try (FileOutputStream out = new FileOutputStream(invalid)) { out.write("not login JSON".getBytes("UTF-8")); }
        result(activity, 301, Activity.RESULT_OK, uri(invalid));
        await(() -> !busy(activity));
        assertEquals(text, input(activity).getText().toString());
        assertEquals(42, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
    }

    @Test public void exportWritesUtf8ToChosenContentUriAndTruncatesOldFile() throws Exception {
        File file = file("export");
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(new byte[4096]); }
        SpecialLoginActivity activity = launch(SpecialLoginActivity.class, false);
        pick(Intent.ACTION_CREATE_DOCUMENT, Activity.RESULT_OK, uri(file));
        ui.runOnMainSync(() -> activity.findViewById(R.id.login_file_action).performClick());
        await(() -> !busy(activity) && file.length() > 0 && file.length() < 4096);
        try (FileInputStream in = new FileInputStream(file)) {
            LoginTextFile.Login login = LoginTextFile.parse(LoginTextFile.read(in));
            assertEquals(cookie(42), login.cookies);
            assertEquals("fixture-refresh", login.refreshToken);
        }
    }

    @Test public void canceledExportAndAccountSwitchCannotWriteStaleLogin() throws Exception {
        SpecialLoginActivity activity = launch(SpecialLoginActivity.class, false);
        File destination = file("cancel");
        pick(Intent.ACTION_CREATE_DOCUMENT, Activity.RESULT_CANCELED, null);
        ui.runOnMainSync(() -> activity.findViewById(R.id.login_file_action).performClick());
        ui.waitForIdleSync();
        assertNull(field(activity, "pendingExport"));
        ui.runOnMainSync(() -> {
            try { setField(activity, "pendingExport", LoginTextFile.encode(cookie(42), "fixture-refresh")); }
            catch (IOException error) { throw new AssertionError(error); }
            AccountManager.saveLogin(cookie(43), "fixture-new");
        });
        result(activity, 302, Activity.RESULT_OK, uri(destination));
        ui.waitForIdleSync();
        assertEquals(0, destination.length());
        assertEquals(43, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
    }

    @Test public void legacyExportShowsUsablePathAndRoundTrips() throws Exception {
        SpecialLoginActivity activity = launch(SpecialLoginActivity.class, false);
        File directory = new File(activity.getExternalFilesDir(null), "login-export");
        Set<String> existing = new java.util.HashSet<>();
        if (directory.list() != null) java.util.Collections.addAll(existing, directory.list());
        ui.runOnMainSync(() -> {
            try { setField(activity, "pendingExport", LoginTextFile.encode(cookie(42), "fixture-refresh")); }
            catch (IOException error) { throw new AssertionError(error); }
            invoke(activity, "exportLegacy", new Class<?>[0]);
        });
        await(() -> !busy(activity) && dialog(activity) != null);
        File exported = null;
        if (directory.listFiles() != null) for (File child : directory.listFiles()) if (!existing.contains(child.getName())) {
            exported = child; files.add(child);
        }
        assertNotNull(exported);
        try (FileInputStream in = new FileInputStream(exported)) { assertEquals(cookie(42), LoginTextFile.parse(LoginTextFile.read(in)).cookies); }
    }

    private <T extends Activity>T launch(Class<T> type, boolean login) {
        T activity = type.cast(ui.startActivitySync(new Intent(ui.getTargetContext(), type)
                .putExtra("login", login).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        owners.add(activity); return activity;
    }
    private void pick(String action, int code, Uri uri) throws Exception {
        IntentFilter filter = new IntentFilter(action);
        filter.addCategory(Intent.CATEGORY_OPENABLE); filter.addDataType("*/*");
        monitors.add(ui.addMonitor(filter, new Instrumentation.ActivityResult(code, uri == null ? null : new Intent().setData(uri)), true));
    }
    private File file(String name) throws IOException {
        File file = File.createTempFile("login-fixture-" + name, ".txt", ui.getTargetContext().getCacheDir());
        files.add(file); return file;
    }
    private Uri uri(File file) { return FileProvider.getUriForFile(ui.getTargetContext(), "com.RobinNotBad.BiliClient.FileProvider", file); }
    private EditText input(SpecialLoginActivity activity) { return activity.findViewById(R.id.loginInput); }
    private static String cookie(int mid) { return "DedeUserID=" + mid + "; SESSDATA=fixture-" + mid + "; bili_jct=fixture-csrf"; }
    private String lastKey() {
        List<MySpaceOrder.Item> items = MySpaceOrder.parse(SharedPreferencesUtil.getString(MySpaceOrder.PREFERENCE, ""));
        return items.get(items.size() - 1).key;
    }
    private boolean busy(Object activity) { return (Boolean)field(activity, "busy"); }
    private AlertDialog dialog(Object activity) { return (AlertDialog)field(activity, "dialog"); }
    private Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void setField(Object object, String name, Object value) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void result(Activity activity, int request, int result, Uri uri) {
        ui.runOnMainSync(() -> invoke(activity, "onActivityResult", new Class<?>[]{int.class, int.class, Intent.class},
                request, result, new Intent().setData(uri)));
    }
    private void invoke(Object object, String name, Class<?>[] types, Object... args) {
        try { Method method = object.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(object, args); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private Rect bounds(View view) {
        Rect rect = new Rect(); ui.runOnMainSync(() -> { assertTrue(view.getGlobalVisibleRect(rect)); }); return rect;
    }
    private void pointer(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        ui.sendPointerSync(event); event.recycle();
    }
    private void await(java.util.concurrent.Callable<Boolean> condition) {
        long until = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < until) {
            try { if (condition.call()) return; } catch (Exception error) { throw new AssertionError(error); }
            SystemClock.sleep(30);
        }
        fail("Timed out waiting for UI");
    }
}
