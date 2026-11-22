package com.RobinNotBad.BiliClient.adapter;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.api.CommentTranslationApi;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.protobuf.CodedOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReplyTranslationInstrumentedTest {
    private final Instrumentation ui = InstrumentationRegistry.getInstrumentation();
    private final AtomicInteger requests = new AtomicInteger();
    private final ArrayList<Reply> replies = new ArrayList<>();
    private TestActivity host;
    private ReplyAdapter adapter;
    private ReplyAdapter.ReplyHolder holder;
    private Field clientField;
    private Object previousClient;
    private long previousMid, previousBound, previousExpiry;
    private String previousKey;
    private volatile byte[] responseBytes;

    @Before public void setup() throws Exception {
        previousMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        previousBound = SharedPreferencesUtil.getLong(SharedPreferencesUtil.APP_RECOMMEND_BOUND_MID, 0);
        previousExpiry = SharedPreferencesUtil.getLong(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY_EXPIRES_AT, 0);
        previousKey = SharedPreferencesUtil.getString(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY, "");
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 42);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_BOUND_MID, 42);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY_EXPIRES_AT, 0);
        SharedPreferencesUtil.putString(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY, "translation-test-only");
        responseBytes = frame(new byte[0]);
        clientField = CommentTranslationApi.class.getDeclaredField("client");
        clientField.setAccessible(true);
        previousClient = clientField.get(null);
        clientField.set(null, new OkHttpClient.Builder().addInterceptor(chain -> {
            requests.incrementAndGet();
            assertTrue(chain.request().url().encodedPath().endsWith("/TranslateReply"));
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").header("grpc-status", "0")
                    .body(ResponseBody.create(MediaType.parse("application/grpc+proto"), responseBytes)).build();
        }).build());
        host = (TestActivity) ui.startActivitySync(new Intent(ui.getTargetContext(), TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui.runOnMainSync(() -> {
            adapter = new ReplyAdapter(host, replies, 123, 0, 1, 0, 9);
            holder = (ReplyAdapter.ReplyHolder) adapter.onCreateViewHolder(new FrameLayout(host), 1);
        });
    }

    @After public void cleanup() throws Exception {
        if (host != null) {
            ui.runOnMainSync(host::finish);
            ui.waitForIdleSync();
        }
        if (clientField != null) clientField.set(null, previousClient);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, previousMid);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_BOUND_MID, previousBound);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY_EXPIRES_AT, previousExpiry);
        SharedPreferencesUtil.putString(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY, previousKey);
    }

    @Test public void boundAccountDoesNotOfferUnknownUnsupportedOrMalformedReplies() throws Exception {
        Object[] unavailable = {null, JSONObject.NULL, 0, 1, -1, 4, true, "2", 2.5, new JSONObject()};
        for (Object value : unavailable) {
            Reply reply = parse(value);
            assertFalse(reply.canTranslate());
            bind(reply);
            ui.runOnMainSync(() -> {
                assertEquals(View.GONE, holder.translate.getVisibility());
                assertFalse(holder.translate.isEnabled());
                assertFalse(holder.translate.performClick());
            });
        }
        assertEquals("Binding must never probe every comment", 0, requests.get());
    }

    @Test public void officialPositiveStatesAreEligibleButBindingSendsNoRequests() throws Exception {
        for (int value : new int[]{2, 3}) {
            Reply reply = parse(value);
            assertTrue(reply.canTranslate());
            bind(reply);
            ui.runOnMainSync(() -> {
                assertEquals(View.VISIBLE, holder.translate.getVisibility());
                assertTrue(holder.translate.isEnabled());
                assertEquals(host.getString(R.string.reply_translate), holder.translate.getText().toString());
            });
        }
        assertEquals(0, requests.get());
    }

    @Test public void nestedAndDetailRepliesPreserveTheirOwnEligibility() throws Exception {
        JSONObject root = fixture(1).put("replies", new JSONArray().put(fixture(2)));
        Reply parsed = new Reply(true, root);
        assertFalse(parsed.canTranslate());
        assertTrue(parsed.childMsgList.get(0).canTranslate());
        bind(parsed.childMsgList.get(0));
        ui.runOnMainSync(() -> adapter.isDetail = true);
        bind(parsed);
        ui.runOnMainSync(() -> {
            assertEquals(View.GONE, holder.translate.getVisibility());
            assertFalse(holder.translate.performClick());
        });
        assertEquals(0, requests.get());
    }

    @Test public void eligibleReplyStillRequiresValidMatchingAppCredentials() throws Exception {
        Reply reply = parse(2);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_BOUND_MID, 77);
        bind(reply); assertHidden();
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_BOUND_MID, 42);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY_EXPIRES_AT, 1);
        bind(reply); assertHidden();
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY_EXPIRES_AT, 0);
        SharedPreferencesUtil.putString(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY, "");
        bind(reply); assertHidden();
        assertEquals(0, requests.get());
    }

    @Test public void recycledEligibleRowClearsItsClickActionOnOrdinaryComment() throws Exception {
        bind(parse(2));
        Reply ordinary = parse(null);
        ordinary.rpid = 5678;
        bind(ordinary);
        ui.runOnMainSync(() -> {
            assertEquals(View.GONE, holder.translate.getVisibility());
            assertFalse(holder.translate.performClick());
        });
        assertEquals(0, requests.get());
    }

    @Test public void successfulTranslationCanToggleLocallyAndCannotLeakAcrossAccounts() throws Exception {
        Reply reply = parse(2);
        responseBytes = translatedFrame(reply.rpid, "测试译文");
        bind(reply);
        ui.runOnMainSync(() -> assertTrue(holder.translate.performClick()));
        await(() -> {
            adapter.onBindViewHolder(holder, 1);
            return holder.translate.isEnabled() && "测试译文".contentEquals(holder.message.getText());
        });
        assertEquals(1, requests.get());
        ui.runOnMainSync(() -> {
            assertEquals(host.getString(R.string.reply_show_original), holder.translate.getText().toString());
            holder.translate.performClick();
            adapter.onBindViewHolder(holder, 1);
            assertEquals(reply.message.toString(), holder.message.getText().toString());
            holder.translate.performClick();
            adapter.onBindViewHolder(holder, 1);
            assertEquals("测试译文", holder.message.getText().toString());
        });
        // A previously fetched translation remains usable offline for the same account.
        reply.translationSwitch = 0;
        SharedPreferencesUtil.putString(SharedPreferencesUtil.APP_RECOMMEND_ACCESS_KEY, "");
        bind(reply);
        ui.runOnMainSync(() -> assertEquals(View.VISIBLE, holder.translate.getVisibility()));
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 77);
        ui.runOnMainSync(() -> {
            assertTrue(holder.translate.performClick()); // stale UI action is guarded too
            adapter.onBindViewHolder(holder, 1);
            assertEquals(reply.message.toString(), holder.message.getText().toString());
            assertEquals(View.GONE, holder.translate.getVisibility());
        });
        assertEquals(1, requests.get());
    }

    @Test public void noTranslationResponseHidesTheButtonAndDoesNotRetry() throws Exception {
        bind(parse(2));
        ui.runOnMainSync(() -> holder.translate.performClick());
        await(() -> {
            adapter.onBindViewHolder(holder, 1);
            return requests.get() == 1 && holder.translate.getVisibility() == View.GONE;
        });
        ui.runOnMainSync(() -> assertFalse(holder.translate.performClick()));
        assertEquals(1, requests.get());
    }

    private Reply parse(Object state) throws Exception { return new Reply(true, fixture(state)); }

    private JSONObject fixture(Object state) throws Exception {
        JSONObject control = new JSONObject();
        if (state != null) control.put("translation_switch", state);
        return new JSONObject().put("rpid", 1234).put("oid", 123).put("root", 0).put("parent", 0)
                .put("like", 0).put("ctime", 1).put("rcount", 0)
                .put("member", new JSONObject().put("mid", 9).put("uname", "Fixture").put("avatar", "")
                        .put("level_info", new JSONObject().put("current_level", 1))
                        .put("is_senior_member", 0).put("vip", new JSONObject().put("vipStatus", 0).put("nickname_color", "")))
                .put("content", new JSONObject().put("message", "Original comment 原评论"))
                .put("reply_control", control).put("up_action", new JSONObject().put("like", false).put("reply", false));
    }

    private void bind(Reply reply) {
        ui.runOnMainSync(() -> {
            replies.clear(); replies.add(reply);
            adapter.onBindViewHolder(holder, adapter.isDetail ? 0 : 1);
        });
    }

    private void assertHidden() {
        ui.runOnMainSync(() -> assertEquals(View.GONE, holder.translate.getVisibility()));
    }

    private void await(Callable<Boolean> condition) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] done = {false};
        while (SystemClock.uptimeMillis() < deadline) {
            ui.runOnMainSync(() -> {
                try { done[0] = condition.call(); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            if (done[0]) return;
            SystemClock.sleep(40);
        }
        fail("Translation state did not settle");
    }

    private static byte[] translatedFrame(long rpid, String text) throws Exception {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        CodedOutputStream c = CodedOutputStream.newInstance(content); c.writeString(1, text); c.flush();
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        CodedOutputStream r = CodedOutputStream.newInstance(reply); r.writeByteArray(17, content.toByteArray()); r.flush();
        ByteArrayOutputStream entry = new ByteArrayOutputStream();
        CodedOutputStream e = CodedOutputStream.newInstance(entry); e.writeInt64(1, rpid); e.writeByteArray(2, reply.toByteArray()); e.flush();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        CodedOutputStream m = CodedOutputStream.newInstance(result); m.writeByteArray(1, entry.toByteArray()); m.flush();
        return frame(result.toByteArray());
    }

    private static byte[] frame(byte[] payload) {
        byte[] framed = new byte[payload.length + 5];
        for (int i = 0; i < 4; i++) framed[4 - i] = (byte) (payload.length >>> (i * 8));
        System.arraycopy(payload, 0, framed, 5, payload.length);
        return framed;
    }
}
