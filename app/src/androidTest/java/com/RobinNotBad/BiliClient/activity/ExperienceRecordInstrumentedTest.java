package com.RobinNotBad.BiliClient.activity;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.UIPreviewActivity;
import com.RobinNotBad.BiliClient.activity.user.ExperienceRecordActivity;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ExperienceRecordInstrumentedTest {
    private final Instrumentation ui = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> saved = new HashMap<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient original;
    private ExperienceRecordActivity activity;
    private UIPreviewActivity preview;
    private volatile boolean failRequest;
    private CountDownLatch firstResponse;

    @Before public void setup() throws Exception {
        SharedPreferences preferences = SharedPreferencesUtil.getSharedPreferences();
        for (String key : new String[]{SharedPreferencesUtil.mid, SharedPreferencesUtil.cookies,
                "dpi", "density", "player_ui_round"}) saved.put(key, preferences.getAll().get(key));
        preferences.edit().putFloat("dpi", 1f).putInt("density", -1)
                .putBoolean("player_ui_round", false).commit();
        selectAccount(42);
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) field.get(null);
        original = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            if (!chain.request().url().encodedPath().endsWith("/x/member/web/exp/log"))
                throw new IOException("Unexpected fixture request");
            String cookie = chain.request().header("Cookie");
            boolean first = cookie != null && cookie.contains("SESSDATA=fixture-42");
            inFlight.incrementAndGet();
            requests.incrementAndGet();
            try {
                if (first && firstResponse != null) firstResponse.await(6, TimeUnit.SECONDS);
                String reason = first ? "账号42" : "账号43";
                String body = failRequest ? "{\"code\":-101}" : "{\"code\":0,\"data\":{\"count\":3,\"list\":["
                        + "{\"delta\":5,\"time\":\"2026-11-05 12:00:00\",\"reason\":\"" + reason + "\"},"
                        + "{\"delta\":-10,\"time\":\"2026-11-04 12:00:00\",\"reason\":\"调整\"},"
                        + "{\"delta\":0,\"time\":\"2026-11-03 12:00:00\",\"reason\":\"核对\"}]}}";
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("fixture")
                        .body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException(error);
            } finally {
                inFlight.decrementAndGet();
            }
        }).build());
    }

    @After public void cleanup() {
        if (firstResponse != null) firstResponse.countDown();
        ui.runOnMainSync(() -> {
            if (preview != null) preview.finish();
            if (activity != null) activity.finish();
        });
        await(() -> inFlight.get() == 0);
        clients.set(original);
        SharedPreferences.Editor editor = SharedPreferencesUtil.getSharedPreferences().edit();
        for (Map.Entry<String, Object> entry : saved.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) editor.remove(key);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        }
        editor.commit();
    }

    @Test public void displaysSignedChangesAndRefreshes() {
        open();
        await(this::loaded);
        assertEquals(3, activity.recyclerView.getAdapter().getItemCount());
        assertEquals("+5", rowText(0, R.id.experience_delta));
        assertEquals("2026-11-05 12:00:00", rowText(0, R.id.experience_time));
        assertEquals("-10", rowText(1, R.id.experience_delta));
        assertEquals("0", rowText(2, R.id.experience_delta));
        int previous = requests.get();
        ui.runOnMainSync(() -> {
            try {
                Field field = androidx.swiperefreshlayout.widget.SwipeRefreshLayout.class.getDeclaredField("mListener");
                field.setAccessible(true);
                ((androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener)
                        field.get(activity.swipeRefreshLayout)).onRefresh();
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        await(() -> loaded() && requests.get() > previous);
    }

    @Test public void loginErrorOffersRetryInsteadOfEmptySuccess() {
        failRequest = true;
        open();
        await(() -> activity.emptyView.isShown() && !activity.swipeRefreshLayout.isRefreshing());
        assertTrue(activity.emptyView.getText().toString().contains("登录信息已失效"));
        failRequest = false;
        ui.runOnMainSync(() -> activity.emptyView.performClick());
        await(this::loaded);
        assertEquals(3, activity.recyclerView.getAdapter().getItemCount());
    }

    @Test public void delayedResponseFromPreviousAccountCannotReplaceCurrentRecords() {
        firstResponse = new CountDownLatch(1);
        open();
        await(() -> requests.get() == 1);
        preview = (UIPreviewActivity) ui.startActivitySync(new Intent(ui.getTargetContext(), UIPreviewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui.runOnMainSync(() -> { selectAccount(43); preview.finish(); });
        await(this::loaded);
        assertEquals("账号43", rowText(0, R.id.experience_reason));
        firstResponse.countDown();
        await(() -> inFlight.get() == 0);
        ui.waitForIdleSync();
        assertEquals("账号43", rowText(0, R.id.experience_reason));
    }

    private void selectAccount(long mid) {
        SharedPreferencesUtil.getSharedPreferences().edit().putLong(SharedPreferencesUtil.mid, mid)
                .putString(SharedPreferencesUtil.cookies,
                        "DedeUserID=" + mid + "; SESSDATA=fixture-" + mid + "; bili_jct=csrf-" + mid).commit();
    }

    private void open() {
        activity = (ExperienceRecordActivity) ui.startActivitySync(
                new Intent(ui.getTargetContext(), ExperienceRecordActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private boolean loaded() {
        return !activity.swipeRefreshLayout.isRefreshing() && activity.recyclerView.isShown()
                && activity.recyclerView.getAdapter().getItemCount() > 0;
    }

    private String rowText(int position, int resource) {
        ui.runOnMainSync(() -> activity.recyclerView.scrollToPosition(position));
        await(() -> activity.recyclerView.findViewHolderForAdapterPosition(position) != null);
        AtomicReference<String> text = new AtomicReference<>();
        ui.runOnMainSync(() -> {
            RecyclerView.ViewHolder holder = activity.recyclerView.findViewHolderForAdapterPosition(position);
            text.set(((TextView) holder.itemView.findViewById(resource)).getText().toString());
        });
        return text.get();
    }

    private interface Condition { boolean ready(); }

    private void await(Condition condition) {
        long deadline = SystemClock.uptimeMillis() + 9000;
        AtomicBoolean ready = new AtomicBoolean();
        do {
            ui.runOnMainSync(() -> ready.set(condition.ready()));
            if (ready.get()) return;
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Experience UI did not reach the expected state");
    }
}
