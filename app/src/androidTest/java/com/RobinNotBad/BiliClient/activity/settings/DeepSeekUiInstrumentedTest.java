package com.RobinNotBad.BiliClient.activity.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.api.DeepSeekApi;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeepSeekUiInstrumentedTest {
    @Test public void keyVisibilityAndPersistenceWork() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String oldKey = SharedPreferencesUtil.getString("dev_deepseek_apikey", null);
        String legacyKey = SharedPreferencesUtil.getString("dev_catgirl_apikey", null);
        SharedPreferencesUtil.removeValue("dev_deepseek_apikey");
        SharedPreferencesUtil.removeValue("dev_catgirl_apikey");
        SharedPreferencesUtil.putString("dev_deepseek_apikey", "sk-restored-ui-test");
        TestActivity activity = (TestActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(() -> {
                activity.findViewById(R.id.deepseek).performClick();
                EditText key = activity.findViewById(R.id.input_link);
                assertEquals("sk-restored-ui-test", key.getText().toString());
                assertMasked(key);
                key.setText("sk-persistence-test");
                assertMasked(key);
                View toggle = activity.findViewById(R.id.deepseek_toggle_key_visibility);
                toggle.performClick();
                assertNull(key.getTransformationMethod());
                toggle.performClick();
                assertMasked(key);
            });
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
            assertEquals("sk-persistence-test", SharedPreferencesUtil.getString("dev_deepseek_apikey", null));
            TestActivity reopened = (TestActivity) instrumentation.startActivitySync(new Intent(
                    instrumentation.getTargetContext(), TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            try {
                instrumentation.runOnMainSync(() -> {
                    reopened.findViewById(R.id.deepseek).performClick();
                    assertEquals("sk-persistence-test", reopened.input_link.getText().toString());
                    assertMasked(reopened.input_link);
                });
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(() -> assertMasked(reopened.input_link));
            } finally {
                instrumentation.runOnMainSync(reopened::finish);
                instrumentation.waitForIdleSync();
            }
        } finally {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
            if (oldKey == null) SharedPreferencesUtil.removeValue("dev_deepseek_apikey");
            else SharedPreferencesUtil.putString("dev_deepseek_apikey", oldKey);
            if (legacyKey == null) SharedPreferencesUtil.removeValue("dev_catgirl_apikey");
            else SharedPreferencesUtil.putString("dev_catgirl_apikey", legacyKey);
        }
    }

    @Test public void failureRetryThinkingAndKeyControlsWorkWithoutRealKey() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String oldKey = SharedPreferencesUtil.getString("dev_deepseek_apikey", null);
        String legacyKey = SharedPreferencesUtil.getString("dev_catgirl_apikey", null);
        SharedPreferencesUtil.removeValue("dev_deepseek_apikey");
        SharedPreferencesUtil.removeValue("dev_catgirl_apikey");
        TestActivity activity = (TestActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>();
        OkHttpClient fake = DeepSeekApi.clientBuilder().addInterceptor(chain -> {
            Buffer buffer = new Buffer();
            chain.request().body().writeTo(buffer);
            body.set(buffer.readUtf8());
            int code = calls.incrementAndGet() == 1 ? 401 : 200;
            String data = code == 401 ? "Unauthorized" :
                    "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata: [DONE]\n\n";
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("test").body(ResponseBody.create(MediaType.parse("text/event-stream"), data)).build();
        }).build();
        try {
            Field clientField = TestActivity.class.getDeclaredField("deepSeekClient");
            clientField.setAccessible(true);
            instrumentation.runOnMainSync(() -> {
                activity.findViewById(R.id.deepseek).performClick();
                EditText key = activity.findViewById(R.id.input_link);
                assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, key.getInputType() & InputType.TYPE_MASK_VARIATION);
                assertTrue(key.getTransformationMethod() instanceof PasswordTransformationMethod);
                assertFalse(key.isSaveEnabled());
                key.setText("sk-fake-ui-test");
                ((EditText) activity.findViewById(R.id.input_data)).setText("hello");
                ((SwitchMaterial) activity.findViewById(R.id.switch_wbi)).setChecked(false);
                try { clientField.set(activity, fake); } catch (Exception e) { throw new AssertionError(e); }
                activity.findViewById(R.id.request).performClick();
            });
            awaitStatus(instrumentation, activity, "401");
            instrumentation.runOnMainSync(() -> {
                assertEquals(1, activity.conversation.length());
                assertEquals("hello", activity.input_data.getText().toString());
                assertFalse(activity.sw_wbi.isChecked());
                activity.findViewById(R.id.request).performClick();
            });
            awaitStatus(instrumentation, activity, "回答完成");
            assertEquals("disabled", new JSONObject(body.get()).getJSONObject("thinking").getString("type"));
            instrumentation.runOnMainSync(() -> {
                assertEquals(3, activity.conversation.length());
                assertEquals("OK", activity.output.getText().toString());
                assertFalse(activity.sw_wbi.isChecked());
                SharedPreferencesUtil.putString("dev_deepseek_apikey", "sk-fake-ui-test");
                SharedPreferencesUtil.putString("dev_catgirl_apikey", "sk-fake-ui-test");
                activity.findViewById(R.id.deepseek_forget_key).performClick();
                assertEquals("", activity.input_link.getText().toString());
                assertNull(SharedPreferencesUtil.getString("dev_deepseek_apikey", null));
                assertNull(SharedPreferencesUtil.getString("dev_catgirl_apikey", null));
                View send = activity.findViewById(R.id.request);
                send.requestRectangleOnScreen(new Rect(0, 0, send.getWidth(), send.getHeight()), true);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                View send = activity.findViewById(R.id.request);
                Rect visible = new Rect();
                assertTrue(send.getGlobalVisibleRect(visible));
                assertTrue("Send button must be fully reachable", visible.height() >= send.getHeight());
            });
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            try (FileOutputStream output = new FileOutputStream(
                    new File(activity.getFilesDir(), "deepseek-ui-test.png"))) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
            } finally {
                screenshot.recycle();
            }
        } finally {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
            if (oldKey == null) SharedPreferencesUtil.removeValue("dev_deepseek_apikey");
            else SharedPreferencesUtil.putString("dev_deepseek_apikey", oldKey);
            if (legacyKey == null) SharedPreferencesUtil.removeValue("dev_catgirl_apikey");
            else SharedPreferencesUtil.putString("dev_catgirl_apikey", legacyKey);
        }
    }

    private static void assertMasked(EditText key) {
        assertTrue("Must use password masking, not the single-line transformation",
                key.getTransformationMethod() instanceof PasswordTransformationMethod);
        assertFalse(key.getText().toString().equals(
                key.getTransformationMethod().getTransformation(key.getText(), key).toString()));
    }

    private void awaitStatus(Instrumentation instrumentation, TestActivity activity, String expected) {
        AtomicReference<String> status = new AtomicReference<>("");
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync(() -> status.set(
                    ((TextView) activity.findViewById(R.id.deepseek_status)).getText().toString()));
            if (status.get().contains(expected)) {
                instrumentation.waitForIdleSync();
                return;
            }
            SystemClock.sleep(50);
        }
        fail("Status did not reach " + expected);
    }
}
