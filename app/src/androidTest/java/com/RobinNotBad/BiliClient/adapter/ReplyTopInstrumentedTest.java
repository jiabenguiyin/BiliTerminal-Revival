package com.RobinNotBad.BiliClient.adapter;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.event.ReplyTopEvent;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
public class ReplyTopInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final ConcurrentLinkedQueue<HttpUrl> posts = new ConcurrentLinkedQueue<>();
    private final AtomicInteger responseCode = new AtomicInteger(0);
    private volatile CountDownLatch responseGate;
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient oldClient;
    private long oldMid;
    private String oldCsrf;
    private Object oldTopEnabled;
    private java.util.Map<String, ?> oldDisplayPreferences;
    private TestActivity host;

    @Before public void setUp() throws Exception {
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) field.get(null);
        oldClient = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            int code = 0;
            if (chain.request().url().encodedPath().endsWith("/reply/top")) {
                Buffer body = new Buffer();
                chain.request().body().writeTo(body);
                posts.add(HttpUrl.parse("https://example.invalid/?" + body.readUtf8()));
                CountDownLatch gate = responseGate;
                if (gate != null) {
                    try { gate.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                code = responseCode.get();
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"),
                            "{\"code\":" + code + ",\"data\":{}}")).build();
        }).build());
        oldMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        oldCsrf = SharedPreferencesUtil.getString("csrf", "");
        oldTopEnabled = SharedPreferencesUtil.getSharedPreferences().getAll().get(SharedPreferencesUtil.REPLY_TOP_ENABLED);
        oldDisplayPreferences = SharedPreferencesUtil.getSharedPreferences().getAll();
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.REPLY_TOP_ENABLED);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 42);
        SharedPreferencesUtil.putString("csrf", "pin-test-csrf");
        host = (TestActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @After public void tearDown() {
        if (responseGate != null) responseGate.countDown();
        if (host != null) instrumentation.runOnMainSync(host::finish);
        instrumentation.waitForIdleSync();
        clients.set(oldClient);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, oldMid);
        SharedPreferencesUtil.putString("csrf", oldCsrf);
        if (oldTopEnabled == null) SharedPreferencesUtil.removeValue(SharedPreferencesUtil.REPLY_TOP_ENABLED);
        else SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.REPLY_TOP_ENABLED, (Boolean) oldTopEnabled);
        android.content.SharedPreferences.Editor editor = SharedPreferencesUtil.getSharedPreferences().edit();
        for (String key : new String[]{"dpi", "density", "player_ui_round"}) {
            Object value = oldDisplayPreferences.get(key);
            if (value == null) editor.remove(key);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        }
        editor.commit();
    }

    @Test public void watchConfirmationFitsRoundAndSquareScreensAndRealCancelDoesNotPost() throws Exception {
        for (boolean round : new boolean[]{false, true}) for (float scale : new float[]{.5f, 1f, 2f}) {
            relaunchDisplay(scale, round);
            Reply reply = fixture(8101); ArrayList<Reply> list = new ArrayList<>(); list.add(reply);
            ReplyAdapter adapter = new ReplyAdapter(host, list, reply.oid, 0, 1, 3, 42);
            AtomicReference<AlertDialog> shown = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> { AlertDialog dialog = adapter.createReplyTopDialog(reply); shown.set(dialog); dialog.show(); });
            settle();
            instrumentation.runOnMainSync(() -> {
                AlertDialog dialog = shown.get();
                assertDialogVisible(dialog, round);
                if (scale == 1f) saveDialog(dialog, round ? "pin-dialog-round.png" : "pin-dialog-square.png");
                tap(dialog, dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
            });
            instrumentation.waitForIdleSync();
            assertFalse(shown.get().isShowing()); assertTrue(posts.isEmpty()); assertFalse(reply.isTop);
        }
    }

    @Test public void boundedDialogScrollsTheExplanationWhileConfirmationStaysTouchable() throws Exception {
        relaunchDisplay(2f, true);
        Reply reply = fixture(8102); ArrayList<Reply> list = new ArrayList<>(); list.add(reply);
        ReplyAdapter adapter = new ReplyAdapter(host, list, reply.oid, 0, 17, 3, 42);
        AtomicReference<AlertDialog> shown = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> { AlertDialog dialog = adapter.createReplyTopDialog(reply); shown.set(dialog); dialog.show(); });
        settle();
        instrumentation.runOnMainSync(() -> {
            AlertDialog dialog = shown.get();
            android.widget.TextView message = dialog.findViewById(android.R.id.message);
            message.setText(message.getText()+"\n"+message.getText()+"\n"+message.getText());
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            AlertDialog dialog = shown.get(); assertDialogVisible(dialog, true);
            android.widget.TextView message = dialog.findViewById(android.R.id.message);
            android.widget.ScrollView scroll = (android.widget.ScrollView) message.getParent().getParent();
            assertTrue("Long instructions must scroll without covering the buttons", scroll.getChildAt(0).getHeight() > scroll.getHeight());
            scroll.fullScroll(View.FOCUS_DOWN);
            tap(dialog, dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        });
        await(() -> reply.isTop); awaitReady(adapter);
        assertEquals(1, posts.size()); assertEquals("1", posts.remove().queryParameter("action"));
        instrumentation.runOnMainSync(() -> { AlertDialog dialog = adapter.createReplyTopDialog(reply); shown.set(dialog); dialog.show(); });
        settle();
        instrumentation.runOnMainSync(() -> {
            AlertDialog dialog = shown.get(); assertDialogVisible(dialog, true);
            assertEquals(host.getString(R.string.reply_unpin), ((android.widget.TextView) dialog.findViewById(android.R.id.title)).getText().toString());
            saveDialog(dialog, "unpin-dialog-round.png");
            tap(dialog, dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        });
        await(() -> !reply.isTop); awaitReady(adapter);
        assertEquals(1, posts.size()); assertEquals("0", posts.remove().queryParameter("action"));
    }

    private void relaunchDisplay(float scale, boolean round) {
        SharedPreferencesUtil.putFloat("dpi", scale);
        SharedPreferencesUtil.putBoolean("player_ui_round", round);
        instrumentation.runOnMainSync(host::finish); instrumentation.waitForIdleSync();
        host = (TestActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            host.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (android.os.Build.VERSION.SDK_INT >= 19) host.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        });
        settle();
    }

    private void settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(150); instrumentation.waitForIdleSync(); }

    private void assertDialogVisible(AlertDialog dialog, boolean round) {
        View decor = dialog.getWindow().getDecorView();
        int width = host.findViewById(android.R.id.content).getWidth();
        int height = host.findViewById(android.R.id.content).getHeight();
        android.util.DisplayMetrics physical = new android.util.DisplayMetrics();
        host.getWindowManager().getDefaultDisplay().getRealMetrics(physical);
        assertEquals("Watch fixture must retain its full panel width", physical.widthPixels, width);
        assertEquals("Watch fixture must retain its full panel height", physical.heightPixels, height);
        assertTrue(decor.getWidth() > 0); assertTrue(decor.getHeight() > 0);
        assertTrue("Dialog width exceeds viewport", decor.getWidth() <= width * (round ? .71f : .91f));
        assertTrue("Dialog height exceeds viewport", decor.getHeight() <= height * (round ? .71f : .91f));
        float minimumWidth = Math.min(Math.min(width, height) * (round ? .60f : .80f),
                300 * host.getResources().getDisplayMetrics().density);
        assertTrue("Dialog shrank to stale system-bar metrics: " + decor.getWidth() + " < " + minimumWidth,
                decor.getWidth() >= minimumWidth);
        android.graphics.Rect frame = new android.graphics.Rect(); decor.getGlobalVisibleRect(frame);
        for (int which : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE}) {
            android.widget.Button action = dialog.getButton(which);
            android.graphics.Rect rect = new android.graphics.Rect();
            assertTrue(action.getGlobalVisibleRect(rect)); assertTrue(frame.contains(rect));
            assertEquals("Button is clipped vertically", action.getHeight(), rect.height());
            assertEquals("Button is clipped horizontally", action.getWidth(), rect.width());
        }
        android.graphics.drawable.Drawable background = ((View) dialog.getButton(AlertDialog.BUTTON_POSITIVE).getParent().getParent()).getBackground();
        assertTrue(background instanceof android.graphics.drawable.GradientDrawable);
        if (android.os.Build.VERSION.SDK_INT >= 24)
            assertEquals(0xff262626, ((android.graphics.drawable.GradientDrawable) background).getColor().getDefaultColor());
    }

    private void tap(AlertDialog dialog, View target) {
        int[] point = new int[2]; target.getLocationInWindow(point);
        long time = SystemClock.uptimeMillis();
        for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
            android.view.MotionEvent event = android.view.MotionEvent.obtain(time, time + (action == 0 ? 0 : 50), action,
                    point[0]+target.getWidth()/2f, point[1]+target.getHeight()/2f, 0);
            dialog.dispatchTouchEvent(event); event.recycle();
        }
    }

    private void saveDialog(AlertDialog dialog, String filename) {
        View decor = dialog.getWindow().getDecorView();
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        decor.draw(new android.graphics.Canvas(bitmap));
        try (java.io.FileOutputStream output = host.openFileOutput(filename, 0)) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); }
        catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }

    @Test public void pinAndUnpinUseCorrectThreadAndOnlyUpdateAfterSuccess() {
        for (int type : new int[]{1, 11, 17, 23}) {
            Reply target = fixture(70000 + type);
            ArrayList<Reply> replies = new ArrayList<>();
            replies.add(fixture(60000 + type));
            replies.add(target);
            ReplyAdapter adapter = new ReplyAdapter(host, replies, target.oid, 0, type, 3, 42);
            confirm(adapter, target);
            await(() -> target.isTop);
            awaitReady(adapter);
            instrumentation.runOnMainSync(() -> {
                assertSame(target, replies.get(0));
                assertTrue(target.message.toString().startsWith("[\u7f6e\u9876]"));
            });
            HttpUrl pin = posts.poll();
            assertNotNull(pin);
            assertEquals(String.valueOf(type), pin.queryParameter("type"));
            assertEquals(String.valueOf(target.oid), pin.queryParameter("oid"));
            assertEquals(String.valueOf(target.rpid), pin.queryParameter("rpid"));
            assertEquals("1", pin.queryParameter("action"));
            assertEquals("pin-test-csrf", pin.queryParameter("csrf"));
            confirm(adapter, target);
            await(() -> !target.isTop);
            awaitReady(adapter);
            assertEquals("0", posts.remove().queryParameter("action"));
            assertEquals("A comment", target.message.toString());
        }
    }

    @Test public void forbiddenFailuresAndCancelLeaveStateUntouched() {
        Reply target = fixture(71);
        ArrayList<Reply> replies = new ArrayList<>();
        replies.add(target);
        ReplyAdapter adapter = new ReplyAdapter(host, replies, target.oid, 0, 17, 3, 42);
        instrumentation.runOnMainSync(() -> {
            ReplyAdapter.ReplyHolder row = (ReplyAdapter.ReplyHolder) adapter.onCreateViewHolder(new FrameLayout(host), 1);
            adapter.onBindViewHolder(row, 1);
            assertEquals(View.VISIBLE, row.pinBtn.getVisibility());
            target.root = 123;
            adapter.onBindViewHolder(row, 1);
            assertEquals(View.GONE, row.pinBtn.getVisibility());
            target.root = 0;
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 43);
            adapter.onBindViewHolder(row, 1);
            assertEquals(View.GONE, row.pinBtn.getVisibility());
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 42);
            AlertDialog dialog = adapter.createReplyTopDialog(target);
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        assertTrue(posts.isEmpty());
        for (int code : new int[]{-403, -111, 12029, 12030}) {
            responseCode.set(code);
            confirm(adapter, target);
            await(() -> !posts.isEmpty());
            awaitReady(adapter);
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> assertFalse(target.isTop));
            posts.clear();
        }
    }

    @Test public void pendingRequestBlocksDuplicatePin() {
        Reply target = fixture(72);
        ArrayList<Reply> replies = new ArrayList<>();
        replies.add(target);
        ReplyAdapter adapter = new ReplyAdapter(host, replies, target.oid, 0, 1, 3, 42);
        responseGate = new CountDownLatch(1);
        confirm(adapter, target);
        await(() -> posts.size() == 1);
        confirm(adapter, target);
        instrumentation.runOnMainSync(() -> assertFalse(target.isTop));
        assertEquals(1, posts.size());
        responseGate.countDown();
        await(() -> target.isTop);
        awaitReady(adapter);
        assertEquals(1, posts.size());
    }

    @Test public void disabledPinPowerHidesEveryThreadControlAndBlocksAlreadyOpenDialogs() {
        for (int type : new int[]{1, 11, 17, 23}) {
            Reply reply = fixture(9000 + type); reply.setTop(true);
            ArrayList<Reply> replies = new ArrayList<>(); replies.add(reply);
            ReplyAdapter adapter = new ReplyAdapter(host, replies, reply.oid, 0, type, 3, 42);
            ReplyAdapter.ReplyHolder[] holder = new ReplyAdapter.ReplyHolder[1];
            instrumentation.runOnMainSync(() -> {
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.REPLY_TOP_ENABLED, true);
                ReplyAdapter.ReplyHolder row = (ReplyAdapter.ReplyHolder) adapter.onCreateViewHolder(new FrameLayout(host), 1);
                holder[0] = row;
                adapter.onBindViewHolder(row, 1); assertEquals(View.VISIBLE, row.pinBtn.getVisibility());
                AlertDialog dialog = adapter.createReplyTopDialog(reply); dialog.show();
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.REPLY_TOP_ENABLED, false);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                adapter.onBindViewHolder(row, 1); assertEquals(View.GONE, row.pinBtn.getVisibility());
                assertTrue(reply.isTop); assertTrue(reply.message.toString().startsWith("[置顶]"));
            });
            // AlertDialog posts its button callback. Keep the setting disabled until it runs.
            instrumentation.waitForIdleSync();
            assertTrue(posts.isEmpty());
            instrumentation.runOnMainSync(() -> {
                SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.REPLY_TOP_ENABLED, true);
                adapter.onBindViewHolder(holder[0], 1); assertEquals(View.VISIBLE, holder[0].pinBtn.getVisibility());
            });
        }
        assertTrue(posts.isEmpty());
    }

    @Test public void topStatePreservesSpansAndDoesNotAffectOtherThreads() {
        instrumentation.runOnMainSync(() -> {
            Reply target = fixture(73);
            SpannableStringBuilder text = new SpannableStringBuilder("A comment");
            StyleSpan bold = new StyleSpan(android.graphics.Typeface.BOLD);
            text.setSpan(bold, 2, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            target.message = text;
            ArrayList<Reply> replies = new ArrayList<>();
            replies.add(target);
            ReplyAdapter adapter = new ReplyAdapter(host, replies, target.oid, 0, 17, 3, 42);
            adapter.applyReplyTop(new ReplyTopEvent(target.oid + 1, 17, target.rpid, true));
            adapter.applyReplyTop(new ReplyTopEvent(target.oid, 1, target.rpid, true));
            assertFalse(target.isTop);
            adapter.applyReplyTop(new ReplyTopEvent(target.oid, 17, target.rpid, true));
            target.setTop(true);
            target.setTop(false);
            assertEquals("A comment", target.message.toString());
            assertEquals(2, ((Spanned) target.message).getSpanStart(bold));
        });
    }

    private void confirm(ReplyAdapter adapter, Reply target) {
        instrumentation.runOnMainSync(() -> {
            AlertDialog dialog = adapter.createReplyTopDialog(target);
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
    }

    private void await(java.util.concurrent.Callable<Boolean> predicate) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        AtomicBoolean done = new AtomicBoolean();
        do {
            instrumentation.runOnMainSync(() -> {
                try { done.set(predicate.call()); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            if (done.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Timed out waiting for pin operation");
    }

    private void awaitReady(ReplyAdapter adapter) {
        await(() -> {
            Field field = ReplyAdapter.class.getDeclaredField("pendingTopRequests");
            field.setAccessible(true);
            return ((java.util.Set<?>) field.get(adapter)).isEmpty();
        });
    }

    private static Reply fixture(long id) {
        Reply reply = new Reply();
        reply.rpid = id;
        reply.oid = 90000000001L;
        reply.message = "A comment";
        reply.pubTime = "Today";
        reply.sender = new UserInfo();
        reply.sender.mid = 77;
        reply.sender.name = "Reader";
        reply.sender.avatar = "";
        return reply;
    }
}
