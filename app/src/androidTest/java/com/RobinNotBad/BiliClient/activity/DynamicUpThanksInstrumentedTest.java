package com.RobinNotBad.BiliClient.activity;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity;
import com.RobinNotBad.BiliClient.activity.message.PrivateMsgActivity;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.adapter.message.NoticeHolder;
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicUpAdapter;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.activity.user.FollowUsersActivity;
import com.RobinNotBad.BiliClient.model.MessageCard;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
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
public class DynamicUpThanksInstrumentedTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> saved = new HashMap<>();
    private final AtomicInteger followingReads = new AtomicInteger(), sends = new AtomicInteger();
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient oldClient;
    private TestActivity host;
    private DynamicActivity feed;
    private PrivateMsgActivity chat;
    private AlertDialog dialog;
    private volatile boolean failFollowing;
    private volatile CountDownLatch oldAccountGate;

    @Before public void setup() throws Exception {
        String tutorial = "tutorial_ver_" + inst.getTargetContext().getResources()
                .getStringArray(R.array.tutorial_list)[6];
        SharedPreferences prefs = SharedPreferencesUtil.getSharedPreferences();
        for (String key : new String[]{SharedPreferencesUtil.mid, SharedPreferencesUtil.DYNAMIC_UP_LIST_ENABLE,
                SharedPreferencesUtil.PRIVATE_MSG_AUTO_READ_ENABLE, tutorial, "dpi", "density",
                "player_ui_round", "wbi_mixin_key", "last_wbi", "dynamic_update_baseline",
                SharedPreferencesUtil.DYNAMIC_UPDATE_NUM}) saved.put(key, prefs.getAll().get(key));
        prefs.edit().putLong(SharedPreferencesUtil.mid, 9001L)
                .remove(SharedPreferencesUtil.DYNAMIC_UP_LIST_ENABLE)
                .putBoolean(SharedPreferencesUtil.PRIVATE_MSG_AUTO_READ_ENABLE, false)
                .putInt(tutorial, 100).putFloat("dpi", 1f).putInt("density", -1)
                .putBoolean("player_ui_round", false).commit();
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) field.get(null);
        oldClient = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            String body = "{\"code\":0,\"data\":{\"items\":[],\"has_more\":false,\"messages\":null}}";
            if (path.endsWith("/followings")) {
                followingReads.incrementAndGet();
                String account = chain.request().url().queryParameter("vmid");
                CountDownLatch gate = oldAccountGate;
                if ("9001".equals(account) && gate != null) {
                    try { gate.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { throw new java.io.IOException(e); }
                }
                body = failFollowing ? "{\"code\":-500,\"message\":\"test failure\"}"
                        : "{\"code\":0,\"data\":{\"list\":[{\"mid\":123,\"uname\":\"UP" + account
                        + "\",\"face\":\"\",\"sign\":\"\"}]}}";
            } else if (path.endsWith("/send_msg")) sends.incrementAndGet();
            else if (path.endsWith("/nav")) body = "{\"code\":0,\"data\":{\"wbi_img\":{"
                    + "\"img_url\":\"https://example.invalid/00000000000000000000000000000000.png\","
                    + "\"sub_url\":\"https://example.invalid/11111111111111111111111111111111.png\"}}}";
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
        }).build());
        host = open(TestActivity.class);
    }

    @After public void cleanup() {
        if (oldAccountGate != null) oldAccountGate.countDown();
        inst.runOnMainSync(() -> {
            if (dialog != null) dialog.dismiss();
            if (chat != null) chat.finish();
            if (feed != null) feed.finish();
            if (host != null) host.finish();
        });
        inst.waitForIdleSync();
        clients.set(oldClient);
        SharedPreferences.Editor edit = SharedPreferencesUtil.getSharedPreferences().edit();
        for (Map.Entry<String, Object> e : saved.entrySet()) {
            String key = e.getKey(); Object v = e.getValue();
            if (v == null) edit.remove(key);
            else if (v instanceof Long) edit.putLong(key, (Long) v);
            else if (v instanceof Integer) edit.putInt(key, (Integer) v);
            else if (v instanceof Float) edit.putFloat(key, (Float) v);
            else if (v instanceof Boolean) edit.putBoolean(key, (Boolean) v);
            else if (v instanceof String) edit.putString(key, (String) v);
        }
        edit.commit();
    }

    @Test public void videoLikeThanksOpensOnlyAnEditableDraftAndRecyclingHidesIt() {
        RecordingContext context = new RecordingContext(host);
        inst.runOnMainSync(() -> {
            View card = LayoutInflater.from(host).inflate(R.layout.cell_message, null);
            NoticeHolder holder = new NoticeHolder(card);
            MessageCard message = likes(user(123), user(123), user(0), user(9001));
            holder.showMessage(message, context);
            assertEquals(View.VISIBLE, holder.likeThanks.getVisibility());
            holder.likeThanks.performClick();
            assertEquals(123L, context.launched.getLongExtra("uid", 0));
            assertEquals(host.getString(R.string.message_like_thanks_draft), context.launched.getStringExtra("draft"));
            assertEquals(PrivateMsgActivity.class.getName(), context.launched.getComponent().getClassName());
            message.itemType = "reply";
            holder.showMessage(message, context);
            assertEquals(View.GONE, holder.likeThanks.getVisibility());
            message.itemType = "video"; message.getType = MessageCard.GET_TYPE_AT;
            holder.showMessage(message, context);
            assertEquals(View.GONE, holder.likeThanks.getVisibility());
            message = likes(null, user(-1), user(9001));
            holder.showMessage(message, context);
            assertEquals(View.GONE, holder.likeThanks.getVisibility());
        });
        assertEquals(0, sends.get());
    }

    @Test public void aggregatedLikesRequireRecipientChoiceAndKeepAccountBoundary() throws Exception {
        RecordingContext context = new RecordingContext(host);
        inst.runOnMainSync(() -> {
            NoticeHolder holder = new NoticeHolder(LayoutInflater.from(host).inflate(R.layout.cell_message, null));
            holder.showMessage(likes(user(123), user(456)), context);
            holder.likeThanks.performClick();
            assertNull(context.launched);
            try {
                Field f = NoticeHolder.class.getDeclaredField("thanksDialog"); f.setAccessible(true);
                dialog = (AlertDialog) f.get(holder);
            } catch (Exception e) { throw new AssertionError(e); }
            assertTrue(dialog.isShowing());
            dialog.getListView().performItemClick(null, 1, 1);
            assertEquals(456L, context.launched.getLongExtra("uid", 0));
            context.launched = null;
            SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 9002L);
            holder.likeThanks.performClick();
            assertNull(context.launched);
        });
        assertEquals(0, sends.get());
    }

    @Test public void gratitudeDraftNeverSendsItselfAndEditedTextSurvivesRecreation() throws Exception {
        chat = (PrivateMsgActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), PrivateMsgActivity.class)
                .putExtra("uid", 123L).putExtra("draft", "Thanks draft").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        EditText input = chat.findViewById(R.id.msg_input_et);
        assertEquals("Thanks draft", input.getText().toString());
        Instrumentation.ActivityMonitor monitor = inst.addMonitor(PrivateMsgActivity.class.getName(), null, false);
        try {
            inst.runOnMainSync(() -> { input.setText("Edited draft"); chat.recreate(); });
            chat = (PrivateMsgActivity) monitor.waitForActivityWithTimeout(5000);
            assertNotNull(chat); inst.waitForIdleSync();
            assertEquals("Edited draft", ((EditText) chat.findViewById(R.id.msg_input_et)).getText().toString());
            assertEquals(0, sends.get());
        } finally { inst.removeMonitor(monitor); }
    }

    @Test public void upListDefaultsOnAndTheSameHeaderStillWorksWithAnEmptyFeed() throws Exception {
        feed = open(DynamicActivity.class);
        await(() -> stripVisible() && upName().equals("UP9001"));
        assertEquals(1, followingReads.get());
        RecyclerView list = feed.findViewById(R.id.recyclerView);
        assertEquals(1, list.getAdapter().getItemCount());
        inst.runOnMainSync(() -> {
            View header = list.getChildAt(0);
            android.view.ViewGroup strip = header.findViewById(R.id.dynamic_up_strip);
            assertEquals("The strip should contain only the avatar list and the All action", 2, strip.getChildCount());
            for (int width : new int[]{160, 198, 240, 320}) {
                int px = (int) (width * feed.getResources().getDisplayMetrics().density);
                header.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                header.layout(0, 0, px, header.getMeasuredHeight());
                View users = header.findViewById(R.id.dynamic_up_list), all = header.findViewById(R.id.dynamic_up_all);
                assertTrue(users.getWidth() > 0);
                assertEquals(strip.getPaddingLeft(), users.getLeft());
                assertTrue(users.getRight() <= all.getLeft());
                assertTrue(all.getRight() <= px);
            }
            header.requestLayout();
        });
        SystemClock.sleep(100); inst.waitForIdleSync();
        capture("dynamic-up-list.png", feed.getWindow().getDecorView());
        Instrumentation.ActivityMonitor monitor = inst.addMonitor(FollowUsersActivity.class.getName(), null, false);
        try {
            inst.runOnMainSync(() -> feed.findViewById(R.id.dynamic_up_all).performClick());
            FollowUsersActivity following = (FollowUsersActivity) monitor.waitForActivityWithTimeout(5000);
            assertNotNull(following);
            assertEquals(9001L, following.getIntent().getLongExtra("mid", 0));
            assertEquals(0, following.getIntent().getIntExtra("mode", -1));
            inst.runOnMainSync(following::finish);
        } finally { inst.removeMonitor(monitor); }
    }

    @Test public void upAvatarOpensTheSelectedUserSpace() {
        RecordingContext context = new RecordingContext(host);
        inst.runOnMainSync(() -> {
            RecyclerView.Adapter adapter = new DynamicUpAdapter();
            ((DynamicUpAdapter) adapter).setUsers(Arrays.asList(user(123), user(456)));
            RecyclerView.ViewHolder holder = adapter.createViewHolder(new FrameLayout(context), 0);
            adapter.bindViewHolder(holder, 1);
            holder.itemView.performClick();
            assertNotNull(context.launched);
            assertEquals(456L, context.launched.getLongExtra("mid", 0));
            assertEquals(UserInfoActivity.class.getName(), context.launched.getComponent().getClassName());
        });
    }

    @Test public void disablingTheUpStripMakesNoRequestsAndReturningCanEnableIt() throws Exception {
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.DYNAMIC_UP_LIST_ENABLE, false);
        feed = open(DynamicActivity.class);
        await(() -> feed.recyclerView.getChildCount() > 0 && !feed.swipeRefreshLayout.isRefreshing());
        assertEquals(0, followingReads.get()); assertFalse(stripVisible());
        returnToFeed(true);
        await(this::stripVisible);
        assertEquals(1, followingReads.get());
        returnToFeed(false);
        await(() -> !stripVisible());
        assertEquals(1, followingReads.get());
    }

    @Test public void lateUpResponseCannotPopulateAnotherAccount() throws Exception {
        oldAccountGate = new CountDownLatch(1);
        feed = open(DynamicActivity.class);
        await(() -> followingReads.get() == 1);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 9002L);
        returnToFeed(true);
        await(() -> followingReads.get() == 2 && stripVisible());
        assertEquals("UP9002", upName());
        oldAccountGate.countDown();
        SystemClock.sleep(250); inst.waitForIdleSync();
        assertEquals("UP9002", upName());
    }

    @Test public void failedUpRequestLeavesFeedActionsUsable() throws Exception {
        failFollowing = true;
        feed = open(DynamicActivity.class);
        await(() -> followingReads.get() == 1 && feed.recyclerView.getChildCount() > 0
                && !feed.swipeRefreshLayout.isRefreshing());
        assertFalse(stripVisible());
        assertTrue(feed.findViewById(R.id.write_dynamic).hasOnClickListeners());
        assertTrue(feed.findViewById(R.id.type).hasOnClickListeners());
    }

    @Test public void thanksButtonFitsSmallCards() {
        inst.runOnMainSync(() -> {
            View card = LayoutInflater.from(host).inflate(R.layout.cell_message, null);
            new NoticeHolder(card).showMessage(likes(user(123)), new RecordingContext(host));
            for (int width : new int[]{160, 198, 240, 320}) {
                int px = (int) (width * host.getResources().getDisplayMetrics().density);
                card.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                card.layout(0, 0, px, card.getMeasuredHeight());
                TextView thanks = card.findViewById(R.id.like_thanks);
                assertTrue(thanks.getRight() <= px);
                assertTrue(thanks.getWidth() - thanks.getPaddingLeft() - thanks.getPaddingRight()
                        >= thanks.getPaint().measureText(thanks.getText().toString()));
            }
            host.setContentView(card);
        });
        SystemClock.sleep(100); inst.waitForIdleSync();
        inst.runOnMainSync(() -> {
            View thanks = host.findViewById(R.id.like_thanks);
            assertTrue(thanks.isShown());
            int[] point = new int[2]; thanks.getLocationOnScreen(point);
            assertTrue(point[1] + thanks.getHeight() <= host.getWindow().getDecorView().getHeight());
        });
        capture("video-like-thanks.png", host.getWindow().getDecorView());
    }

    private void returnToFeed(boolean enabled) {
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.DYNAMIC_UP_LIST_ENABLE, enabled);
        try {
            java.lang.reflect.Method onResume = DynamicActivity.class.getDeclaredMethod("onResume");
            onResume.setAccessible(true);
            inst.runOnMainSync(() -> {
                try { onResume.invoke(feed); }
                catch (Exception e) { throw new AssertionError(e); }
            });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        inst.waitForIdleSync();
    }
    private boolean stripVisible() { View v = feed.findViewById(R.id.dynamic_up_strip); return v != null && v.getVisibility() == View.VISIBLE; }
    private String upName() {
        RecyclerView users = feed.findViewById(R.id.dynamic_up_list);
        return users == null || users.getChildCount() == 0 ? "" : ((TextView) users.getChildAt(0).findViewById(R.id.name)).getText().toString();
    }
    private <T extends android.app.Activity> T open(Class<T> cls) {
        T activity = (T) inst.startActivitySync(new Intent(inst.getTargetContext(), cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        inst.waitForIdleSync(); return activity;
    }
    private void await(Check check) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 7000;
        while (SystemClock.uptimeMillis() < deadline) { inst.waitForIdleSync(); if (check.get()) return; SystemClock.sleep(40); }
        assertTrue("Timed out waiting for UI", check.get());
    }
    private interface Check { boolean get(); }
    private static UserInfo user(long mid) { UserInfo user = new UserInfo(); user.mid = mid; user.name = "User" + mid; user.avatar = ""; return user; }
    private static MessageCard likes(UserInfo... users) {
        MessageCard card = new MessageCard(); card.getType = MessageCard.GET_TYPE_LIKE; card.itemType = "video";
        card.content = "点赞了我的视频"; card.user = new ArrayList<>(Arrays.asList(users)); return card;
    }
    private static class RecordingContext extends ContextWrapper {
        Intent launched;
        RecordingContext(Context base) { super(base); }
        @Override public Object getSystemService(String name) {
            if (Context.LAYOUT_INFLATER_SERVICE.equals(name))
                return LayoutInflater.from(getBaseContext()).cloneInContext(this);
            return super.getSystemService(name);
        }
        @Override public void startActivity(Intent intent) { launched = intent; }
    }
    private void capture(String name, View view) {
        inst.waitForIdleSync();
        inst.runOnMainSync(() -> {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            try (FileOutputStream out = new FileOutputStream(new File(host.getFilesDir(), name))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) { throw new AssertionError(e); }
            finally { bitmap.recycle(); }
        });
    }
}
