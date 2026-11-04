package com.RobinNotBad.BiliClient.adapter;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.event.SnackEvent;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.ui.ReplyReportDialog;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.junit.Assert.*;

/** Every HTTP request terminates in the interceptor. No fixture can submit a real report. */
@RunWith(AndroidJUnit4.class)
public class ReplyReportInstrumentedTest {
    private final Instrumentation ui = InstrumentationRegistry.getInstrumentation();
    private final ConcurrentLinkedQueue<HttpUrl> posts = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> feedback = new ConcurrentLinkedQueue<>();
    private final ArrayList<ReplyAdapter> adapters = new ArrayList<>();
    private final ArrayList<ReplyReportDialog> dialogs = new ArrayList<>();
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient oldClient;
    private Map<String, ?> oldPrefs;
    private SnackEvent oldSnack;
    private TestActivity host;
    private volatile CountDownLatch gate;
    private volatile String json = "{\"code\":0}";
    private volatile int httpStatus = 200;
    private volatile boolean networkFailure;

    @Before public void setup() throws Exception {
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) field.get(null);
        oldClient = clients.get();
        oldPrefs = SharedPreferencesUtil.getSharedPreferences().getAll();
        oldSnack = EventBus.getDefault().getStickyEvent(SnackEvent.class);
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            if (request.url().encodedPath().endsWith("/reply/report")) {
                assertEquals("POST", request.method());
                Buffer buffer = new Buffer(); request.body().writeTo(buffer);
                posts.add(HttpUrl.parse("https://fixture.invalid/?" + buffer.readUtf8()));
                requests.add(request);
                CountDownLatch held = gate;
                if (held != null) {
                    try { held.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("fixture interrupted"); }
                }
                if (networkFailure) throw new IOException("fixture offline");
                return response(request, httpStatus, json);
            }
            return response(request, 200, "{\"code\":-404,\"data\":{}}");
        }).build());
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 42);
        SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies,
                "DedeUserID=42; SESSDATA=fixture-session; bili_jct=cookie-csrf");
        SharedPreferencesUtil.putString("csrf", "fallback-csrf");
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true);
        EventBus.getDefault().register(this);
        launch();
    }

    @After public void cleanup() throws Exception {
        if (gate != null) gate.countDown();
        // Keep the interceptor installed until every report worker has finished.
        for (ReplyAdapter adapter : adapters) ready(adapter);
        ui.runOnMainSync(() -> {
            for (ReplyReportDialog dialog : dialogs) if (dialog.isShowing()) dialog.dismiss();
            if (host != null) host.finish();
        });
        ui.waitForIdleSync();
        EventBus.getDefault().unregister(this);
        EventBus.getDefault().removeStickyEvent(SnackEvent.class);
        if (oldSnack != null) EventBus.getDefault().postSticky(oldSnack);
        SharedPreferences.Editor editor = SharedPreferencesUtil.getSharedPreferences().edit();
        for (String key : new String[]{SharedPreferencesUtil.mid, SharedPreferencesUtil.cookies, "csrf",
                SharedPreferencesUtil.SNACKBAR_ENABLE, "dpi", "density", "player_ui_round"}) {
            Object value = oldPrefs.get(key);
            if (value == null) editor.remove(key);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        }
        editor.commit();
        clients.set(oldClient);
    }

    @Subscribe public void feedback(SnackEvent event) { feedback.add(event.getMessage()); }

    @Test public void compactWarningIconKeepsSeparateTouchAreaBesidePin() throws Exception {
        AtomicReference<ReplyAdapter.ReplyHolder> shown=new AtomicReference<>();
        ReplyAdapter adapter=adapter(fixture(8100,0),1,false);
        ui.runOnMainSync(()->{
            ReplyAdapter.ReplyHolder holder=row(adapter,1);
            holder.pinBtn.setVisibility(View.VISIBLE);
            host.setContentView(holder.itemView);shown.set(holder);
        });
        settle();
        ui.runOnMainSync(()->{
            ReplyAdapter.ReplyHolder holder=shown.get();
            assertTrue(holder.reportBtn instanceof android.widget.ImageButton);
            assertNotNull(holder.reportBtn.getDrawable());
            assertTrue(holder.reportBtn.getWidth()>=MsgUtilDp(36));
            assertTrue(holder.reportBtn.getHeight()>=MsgUtilDp(36));
            assertTrue(holder.reportBtn.getRight()<=holder.pinBtn.getLeft());
            assertTrue(holder.pinBtn.getRight()<=holder.itemView.getWidth());
            android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(holder.itemView.getWidth(),holder.itemView.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(host.getFilesDir(),"comment-report-icon.png"))){
                holder.itemView.draw(new android.graphics.Canvas(b));b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
            }catch(Exception error){throw new AssertionError(error);}finally{b.recycle();}
            assertTrue(holder.reportBtn.performClick());dialogs.add(active(adapter));
        });
        assertTrue(posts.isEmpty());
    }

    private int MsgUtilDp(int dp){return Math.round(dp*host.getResources().getDisplayMetrics().density);}

    @Test public void visibleButtonOpensAndBothCancelStagesNeverPost() throws Exception {
        ReplyAdapter adapter = adapter(fixture(8101, 0), 1, false);
        ReplyReportDialog first = openButton(adapter, 1);
        capture(first,"comment-report-reasons.png");
        ui.runOnMainSync(() -> {
            assertFalse(first.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
            choose(first, 7);
            assertTrue(posts.isEmpty());
            first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        assertFalse(first.isShowing());
        ReplyReportDialog second = openButton(adapter, 1);
        ui.runOnMainSync(() -> {
            choose(second, 1);
            second.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(host.getString(R.string.reply_report_submit), second.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            assertTrue(posts.isEmpty());
        });
        capture(second,"comment-report-confirm.png");
        ui.runOnMainSync(()->second.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        assertTrue(posts.isEmpty());
    }

    @Test public void confirmationPostsOnceForVideoBangumiDynamicAndChildTargets() throws Exception {
        for (int type : new int[]{1, 11, 17}) {
            for (boolean child : new boolean[]{false, true}) {
                posts.clear(); feedback.clear();
                Reply root = fixture(8200 + type * 2, 0);
                Reply target = child ? fixture(root.rpid + 1, root.rpid) : root;
                // Type 1 also covers bangumi's aid-based comment section.
                ReplyAdapter adapter = adapter(root, type, true);
                if (child) adapter.replyList.add(target);
                ReplyReportDialog dialog = openButton(adapter, child ? 2 : 0);
                submit(dialog, 8);
                ui.runOnMainSync(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
                ready(adapter);
                await(() -> feedback.contains(host.getString(R.string.reply_report_success)));
                assertEquals(1, posts.size()); assertTarget(posts.peek(), target, type);
                assertEquals("8", posts.peek().queryParameter("reason"));
                assertEquals("cookie-csrf", posts.peek().queryParameter("csrf"));
                assertNull(posts.peek().queryParameter("root"));
                assertNull(posts.peek().queryParameter("parent"));
                assertFalse(target.liked); assertFalse(target.hated); assertFalse(target.isTop);
            }
        }
    }

    @Test public void businessAlreadyReportedAndNetworkFailuresGiveExplicitFeedback() throws Exception {
        ReplyAdapter adapter = adapter(fixture(8400, 0), 17, false);
        for (int mode = 0; mode < 5; mode++) {
            posts.clear(); feedback.clear();
            networkFailure = mode == 2;
            httpStatus = mode == 3 ? 503 : 200;
            json = mode == 0 ? "{\"code\":-403}" : mode == 1 ? "{\"code\":12008}"
                    : mode == 4 ? "{}" : "{\"code\":0}";
            submit(openButton(adapter, 1), 1); ready(adapter);
            String expected = mode == 0 ? host.getString(R.string.reply_report_failed, -403)
                    : host.getString(mode == 1 ? R.string.reply_report_already
                    : mode == 4 ? R.string.reply_report_response_failed : R.string.reply_report_network_failed);
            await(() -> feedback.contains(expected));
            assertEquals(1, posts.size());
            assertFalse(feedback.contains(host.getString(R.string.reply_report_success)));
        }
    }

    private void launch() {
        host = (TestActivity) ui.startActivitySync(new Intent(ui.getTargetContext(), TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        settle();
    }

    private ReplyAdapter adapter(Reply target, int type, boolean detail) {
        ArrayList<Reply> replies = new ArrayList<>(); replies.add(target);
        ReplyAdapter adapter = new ReplyAdapter(host, replies, target.oid, target.root, type, 3, 99);
        adapter.isDetail = detail; adapters.add(adapter);
        return adapter;
    }

    private ReplyAdapter.ReplyHolder row(ReplyAdapter adapter, int position) {
        ReplyAdapter.ReplyHolder row = (ReplyAdapter.ReplyHolder) adapter.onCreateViewHolder(new FrameLayout(host), 1);
        adapter.onBindViewHolder(row, position);
        return row;
    }

    private ReplyReportDialog openButton(ReplyAdapter adapter, int position) {
        AtomicReference<ReplyReportDialog> shown = new AtomicReference<>();
        ui.runOnMainSync(() -> {
            ReplyAdapter.ReplyHolder row = row(adapter, position);
            assertEquals(View.VISIBLE, row.reportBtn.getVisibility());
            assertEquals(host.getString(R.string.reply_report_description), row.reportBtn.getContentDescription());
            assertTrue(row.reportBtn.isFocusable()); assertTrue(row.reportBtn.performClick());
            ReplyReportDialog dialog = active(adapter);
            assertNotNull(dialog); assertTrue(dialog.isShowing()); shown.set(dialog); dialogs.add(dialog);
        });
        return shown.get();
    }

    private ReplyReportDialog active(ReplyAdapter adapter) {
        try {
            Field field = ReplyAdapter.class.getDeclaredField("activeReportDialog"); field.setAccessible(true);
            return (ReplyReportDialog) field.get(adapter);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void choose(ReplyReportDialog dialog, int reason) {
        RadioGroup group = dialog.findViewById(R.id.reply_report_reasons);
        ((android.widget.RadioButton) group.getChildAt(reason)).setChecked(true);
    }

    private void submit(ReplyReportDialog dialog, int reason) {
        ui.runOnMainSync(() -> {
            choose(dialog, reason);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(dialog.isShowing());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
    }

    private void ready(ReplyAdapter adapter) throws Exception {
        Field field = ReplyAdapter.class.getDeclaredField("pendingReportRequests"); field.setAccessible(true);
        await(() -> ((Set<?>) field.get(adapter)).isEmpty());
    }

    private void await(Callable<Boolean> condition) {
        long until = SystemClock.uptimeMillis() + 6000;
        AtomicBoolean done = new AtomicBoolean();
        do {
            ui.runOnMainSync(() -> {
                try { done.set(condition.call()); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            if (done.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < until);
        fail("Timed out waiting for intercepted report");
    }

    private void settle() { ui.waitForIdleSync(); SystemClock.sleep(100); ui.waitForIdleSync(); }

    private void capture(ReplyReportDialog dialog,String name) {
        settle();ui.runOnMainSync(()->{
            View root=dialog.getWindow().getDecorView();
            android.graphics.Rect bounds=new android.graphics.Rect();root.getGlobalVisibleRect(bounds);
            for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE}) {
                android.graphics.Rect action=new android.graphics.Rect();
                assertTrue(dialog.getButton(which).getGlobalVisibleRect(action));assertTrue(bounds.contains(action));
            }
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(host.getFilesDir(),name))) {
                root.draw(new android.graphics.Canvas(bitmap));bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
            }catch(Exception error){throw new AssertionError(error);}finally{bitmap.recycle();}
        });
    }

    private static Reply fixture(long rpid, long root) {
        Reply reply = new Reply(); reply.oid = 90000000001L; reply.rpid = rpid; reply.root = root;
        reply.parent = root; reply.message = "Fixture comment"; reply.pubTime = "Today";
        reply.sender = new UserInfo(); reply.sender.mid = 77; reply.sender.name = "Reader"; reply.sender.avatar = "";
        return reply;
    }

    private static void assertTarget(HttpUrl form, Reply target, int type) {
        assertEquals(String.valueOf(target.oid), form.queryParameter("oid"));
        assertEquals(String.valueOf(target.rpid), form.queryParameter("rpid"));
        assertEquals(String.valueOf(type), form.queryParameter("type"));
    }

    private static Response response(Request request, int status, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
    }
}
