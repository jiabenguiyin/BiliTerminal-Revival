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
