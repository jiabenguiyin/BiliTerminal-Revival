package com.RobinNotBad.BiliClient.adapter;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.FrameLayout;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.activity.reply.ReplyFragment;
import com.RobinNotBad.BiliClient.activity.reply.ReplyInfoActivity;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.model.ContentType;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.ReplyStateSync;
import com.RobinNotBad.BiliClient.util.Result;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import com.RobinNotBad.BiliClient.api.ReplyApi;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import okio.Buffer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReplySyncInstrumentedTest {
    private final Instrumentation ui = InstrumentationRegistry.getInstrumentation();
    private final long oid = System.nanoTime(), rpid = oid + 1;
    private final AtomicInteger likes = new AtomicInteger(2), action = new AtomicInteger(0);
    private final AtomicInteger reads = new AtomicInteger(), posts = new AtomicInteger(), code = new AtomicInteger();
    private volatile CountDownLatch readGate, postGate;
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient previousClient;
    private long previousMid;
    private String previousCsrf;
    private TestActivity host;
    private ReplyInfoActivity detail;
    private ReplyFragment fragment;
    private ArrayList<Reply> parentReplies;
    private ReplyAdapter parentAdapter;
    private int type = 1;

    @Before public void setup() throws Exception {
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE"); field.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) field.get(null); previousClient = clients.get();
        previousMid = ReplyStateSync.account(); previousCsrf = SharedPreferencesUtil.getString("csrf", "");
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 42);
        SharedPreferencesUtil.putString("csrf", "reply-sync-fixture");
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request(); String path = request.url().encodedPath();
            JSONObject body;
            try {
                if (path.endsWith("/reply/reply")) {
                    reads.incrementAndGet();
                    body = new JSONObject().put("code", 0).put("data", new JSONObject()
                            .put("root", fixture(likes.get(), action.get()))
                            .put("page", new JSONObject().put("size", 20).put("count", 0))
                            .put("replies", new JSONArray()));
                    CountDownLatch gate = readGate; if (gate != null) gate.await(5, TimeUnit.SECONDS);
                } else if (path.endsWith("/reply/action") || path.endsWith("/reply/hate") || path.endsWith("/reply/del")) {
                    posts.incrementAndGet();
                    Buffer form = new Buffer(); request.body().writeTo(form);
                    HttpUrl args = HttpUrl.parse("https://fixture.invalid/?" + form.readUtf8());
                    assertEquals(String.valueOf(oid), args.queryParameter("oid"));
                    assertEquals(String.valueOf(rpid), args.queryParameter("rpid"));
                    assertEquals(String.valueOf(type), args.queryParameter("type"));
                    CountDownLatch gate = postGate; if (gate != null) gate.await(5, TimeUnit.SECONDS);
                    int result = code.get();
                    if (result == 0 && path.endsWith("/reply/action")) {
                        boolean target = "1".equals(args.queryParameter("action"));
                        int old = action.getAndSet(target ? 1 : 0);
                        if ((old == 1) != target) likes.addAndGet(target ? 1 : -1);
                    }
                    body = new JSONObject().put("code", result).put("data", new JSONObject());
                } else body = new JSONObject().put("code", -404).put("data", new JSONObject());
            } catch (Exception error) { throw new java.io.IOException(error); }
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                    .message("OK").body(ResponseBody.create(MediaType.parse("application/json"), body.toString())).build();
        }).build());
        host = (TestActivity) ui.startActivitySync(new Intent(ui.getTargetContext(), TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @After public void cleanup() {
        if (readGate != null) readGate.countDown();
        if (postGate != null) postGate.countDown();
        if (detail != null) finish(detail);
        if (host != null) finish(host);
        clients.set(previousClient);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, previousMid);
        SharedPreferencesUtil.putString("csrf", previousCsrf);
    }

    @Test public void videoListAndDetailShareLikesAndPullRefreshBypassesCache() throws Exception { roundTrip(1); }
    @Test public void dynamicListAndDetailShareLikesToo() throws Exception { roundTrip(17); }

    private void roundTrip(int replyType) throws Exception {
        setupParent(replyType);
        // Seed the exact old detail cache, then mutate the independent list instance.
        assertTrue(TerminalContext.getInstance().fetchReply(ContentType.getContentType(type), oid, rpid, true).isSuccess());
        clickLike(); await(() -> parentReplies.get(0).likeCount == 3);
        openDetail(); await(() -> detailReplies().get(0).likeCount == 3);
        assertEquals(3, parentReplies.get(0).likeCount);
        int before = reads.get();
        likes.set(8); action.set(1);
        ui.runOnMainSync(() -> invoke(detail, "refresh"));
        await(() -> reads.get() >= before + 2 && detailReplies().get(0).likeCount == 8
                && parentReplies.get(0).likeCount == 8);
        assertTrue(parentReplies.get(0).liked);
        RecyclerView detailView = detail.findViewById(com.RobinNotBad.BiliClient.R.id.recyclerView);
        await(() -> detailView.findViewHolderForAdapterPosition(0) instanceof ReplyAdapter.ReplyHolder
                && "8".contentEquals(((ReplyAdapter.ReplyHolder) detailView.findViewHolderForAdapterPosition(0)).likeCount.getText()));
        ui.runOnMainSync(() -> saveScreenshot(detail, "reply-detail-synced.png"));
        finish(detail); detail = null;
        await(() -> parentReplies.get(0).likeCount == 8);
        await(() -> fragment.recyclerView.findViewHolderForAdapterPosition(1) instanceof ReplyAdapter.ReplyHolder
                && "8".contentEquals(((ReplyAdapter.ReplyHolder) fragment.recyclerView.findViewHolderForAdapterPosition(1)).likeCount.getText()));
        ui.runOnMainSync(() -> saveScreenshot(host, "reply-list-synced.png"));
        assertEquals("Returning must preserve the loaded list", 1, parentReplies.size());
    }

    @Test public void inFlightOldReadCannotUndoLikeAndDuplicateClicksSendOneRequest() throws Exception {
        setupParent(1);
        readGate = new CountDownLatch(1);
        AtomicReference<Result<Reply>> result = new AtomicReference<>();
        Thread read = new Thread(() -> result.set(ReplyApi.getRootReply(ContentType.Video, oid, rpid)));
        read.start(); await(() -> reads.get() == 1);
        postGate = new CountDownLatch(1);
        clickLike(); await(() -> posts.get() == 1);
        clickLike(); assertEquals(1, posts.get());
        postGate.countDown(); await(() -> parentReplies.get(0).liked);
        readGate.countDown(); read.join(5000);
        assertFalse(read.isAlive()); assertNotNull(result.get());
        assertEquals(3, result.get().getOrThrow().likeCount);
        assertEquals(3, parentReplies.get(0).likeCount);
    }

    @Test public void failedReactionChangesNeitherCountNorState() throws Exception {
        setupParent(1); code.set(-403);
        clickLike(); await(() -> posts.get() == 1);
        await(() -> ReplyStateSync.STORE.beginMutation(42, type, oid, rpid));
        ReplyStateSync.STORE.endMutation(42, type, oid, rpid);
        assertEquals(2, parentReplies.get(0).likeCount); assertFalse(parentReplies.get(0).liked);
    }

    @Test public void deletingRootInDetailRemovesItFromParentList() throws Exception {
        setupParent(1); openDetail(); await(() -> !detailReplies().isEmpty());
        ui.runOnMainSync(() -> {
            ReplyAdapter adapter = (ReplyAdapter) field(detail, "replyAdapter");
            ReplyAdapter.ReplyHolder row = (ReplyAdapter.ReplyHolder) adapter.onCreateViewHolder(new FrameLayout(detail), 1);
            adapter.onBindViewHolder(row, 0);
            row.item_reply_delete.performLongClick(); row.item_reply_delete.performLongClick();
        });
        await(() -> parentReplies.isEmpty());
        await(() -> detail.isFinishing() || detail.isDestroyed());
        assertEquals(1, posts.get());
        assertEquals("Deleting the root must also update the list header count", 0, parentAdapter.count);
    }

    private void setupParent(int replyType) throws Exception {
        type = replyType;
        ui.runOnMainSync(() -> {
            FrameLayout frame = new FrameLayout(host); frame.setId(0x00123456); host.setContentView(frame);
            fragment = ReplyFragment.newInstance(oid, type, true);
            host.getSupportFragmentManager().beginTransaction().add(frame.getId(), fragment).commitNow();
            parentReplies = (ArrayList<Reply>) field(fragment, "replyList");
            try { parentReplies.add(new Reply(true, fixture(2, 0))); }
            catch (Exception error) { throw new AssertionError(error); }
            parentAdapter = (ReplyAdapter) field(fragment, "replyAdapter");
            parentAdapter.count = 1; parentAdapter.notifyDataSetChanged();
            fragment.recyclerView.setVisibility(View.VISIBLE);
            fragment.emptyView.setVisibility(View.GONE);
            fragment.setRefreshing(false);
        });
        ui.waitForIdleSync();
    }

    private void clickLike() {
        ui.runOnMainSync(() -> {
            ReplyAdapter.ReplyHolder row = (ReplyAdapter.ReplyHolder) parentAdapter.onCreateViewHolder(new FrameLayout(host), 1);
            parentAdapter.onBindViewHolder(row, 1); row.likeCount.performClick();
        });
    }

    private void openDetail() {
        detail = (ReplyInfoActivity) ui.startActivitySync(new Intent(ui.getTargetContext(), ReplyInfoActivity.class)
                .putExtra("oid", oid).putExtra("rpid", rpid).putExtra("type", type)
                .putExtra("fixture_id", oid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> field(detail, "replyAdapter") != null);
    }

    private ArrayList<Reply> detailReplies() { return (ArrayList<Reply>) field(detail, "replyList"); }

    private JSONObject fixture(int count, int liked) throws Exception {
        return new JSONObject().put("rpid", rpid).put("oid", oid).put("root", 0).put("parent", 0)
                .put("like", count).put("action", liked).put("ctime", 1).put("rcount", 0)
                .put("member", new JSONObject().put("mid", 42).put("uname", "Fixture").put("avatar", "")
                        .put("level_info", new JSONObject().put("current_level", 1))
                        .put("is_senior_member", 0).put("vip", new JSONObject().put("vipStatus", 0).put("nickname_color", "")))
                .put("content", new JSONObject().put("message", "Comment synchronization fixture"))
                .put("reply_control", new JSONObject()).put("up_action", new JSONObject().put("like", false).put("reply", false));
    }

    private Object field(Object object, String name) {
        try {
            Class<?> cls = object.getClass(); Field f = null;
            while (cls != null) { try { f = cls.getDeclaredField(name); break; } catch (NoSuchFieldException ignored) { cls = cls.getSuperclass(); } }
            if (f == null) throw new NoSuchFieldException(name);
            f.setAccessible(true); return f.get(object);
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private void invoke(Object object, String name) {
        try { Method method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(object); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void await(java.util.concurrent.Callable<Boolean> condition) {
        long until = SystemClock.uptimeMillis() + 6000;
        boolean[] done = new boolean[1];
        do {
            ui.runOnMainSync(() -> { try { done[0] = condition.call(); } catch (Exception error) { throw new AssertionError(error); } });
            if (done[0]) return;
            SystemClock.sleep(40);
        } while (SystemClock.uptimeMillis() < until);
        fail("Timed out waiting for synchronized comments");
    }
    private void finish(android.app.Activity activity) {
        ui.runOnMainSync(activity::finish); ui.waitForIdleSync();
        long until = SystemClock.uptimeMillis() + 4000;
        while (!activity.isDestroyed() && SystemClock.uptimeMillis() < until) SystemClock.sleep(40);
        assertTrue(activity.isDestroyed());
    }

    private void saveScreenshot(android.app.Activity activity, String name) {
        View root = activity.getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        try (FileOutputStream output = new FileOutputStream(new File(activity.getFilesDir(), name))) {
            root.draw(new Canvas(bitmap)); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
