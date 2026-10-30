package com.RobinNotBad.BiliClient.activity.message;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.PrivateMessage;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrivateMsgLayoutInstrumentedTest {
    @Test public void inputStaysAtBottomWithEmptyAndNineImagePreviews() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Field clientField = NetWorkUtil.class.getDeclaredField("INSTANCE");
        clientField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<OkHttpClient> clients = (AtomicReference<OkHttpClient>) clientField.get(null);
        OkHttpClient oldClient = clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"),
                        "{\"code\":0,\"data\":{\"messages\":null,\"has_more\":0}}"))
                .build()).build());
        PrivateMsgActivity activity = null;
        ArrayList<File> fixtures = new ArrayList<>();
        try {
            activity = (PrivateMsgActivity) instrumentation.startActivitySync(new Intent(
                    instrumentation.getTargetContext(), PrivateMsgActivity.class)
                    .putExtra("uid", 123L).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final PrivateMsgActivity host = activity;
            long until = SystemClock.uptimeMillis() + 5000L;
            while (host.adapter == null && SystemClock.uptimeMillis() < until) SystemClock.sleep(50L);
            assertNotNull("Offline message list did not load", host.adapter);
            PrivateMessage first = new PrivateMessage(123L, PrivateMessage.TYPE_TEXT,
                    new JSONObject().put("content", "Message A"), 1L, "Sender", 1L, 1L);
            PrivateMessage second = new PrivateMessage(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, -1),
                    PrivateMessage.TYPE_TEXT, new JSONObject().put("content", "Message B"),
                    2L, "Me", 2L, 2L);
            instrumentation.runOnMainSync(() -> {
                host.list.add(first);
                host.list.add(second);
                host.adapter.notifyDataSetChanged();
            });
            SystemClock.sleep(200L);
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> assertBottomLayout(host.getWindow().getDecorView()));

            Field imagesField = PrivateMsgActivity.class.getDeclaredField("selectedImages");
            imagesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<Uri> images = (ArrayList<Uri>) imagesField.get(host);
            for (int i = 0; i < 9; i++) {
                File file = new File(host.getCacheDir(), "msg-layout-" + i + ".png");
                Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.RGB_565);
                bitmap.eraseColor(i % 2 == 0 ? Color.GREEN : Color.MAGENTA);
                try (FileOutputStream output = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
                bitmap.recycle();
                fixtures.add(file);
                images.add(Uri.fromFile(file));
            }
            Field expanded = PrivateMsgActivity.class.getDeclaredField("previewExpanded");
            expanded.setAccessible(true);
            expanded.setBoolean(host, true);
            Method preview = PrivateMsgActivity.class.getDeclaredMethod("updateImagePreview");
            preview.setAccessible(true);
            instrumentation.runOnMainSync(() -> call(preview, host));
            SystemClock.sleep(200L);
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertEquals(9, host.imagePreviewRow.getChildCount());
                assertBottomLayout(host.getWindow().getDecorView());
                assertTrue(host.msgView.getPaddingBottom() >= host.layout_input.getHeight());
                assertMessagesVisible(host);
                host.pickImageBtn.performClick();
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertEquals(View.GONE, host.imagePreviewContainer.getVisibility());
                assertBottomLayout(host.getWindow().getDecorView());
                host.pickImageBtn.performClick();
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                FrameLayout item = (FrameLayout) host.imagePreviewRow.getChildAt(0);
                item.getChildAt(1).performClick();
                assertEquals(8, images.size());
                assertBottomLayout(host.getWindow().getDecorView());
            });
            instrumentation.waitForIdleSync();
            SystemClock.sleep(800L);
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            try (FileOutputStream output = new FileOutputStream(new File(host.getFilesDir(), "private-msg-layout.png"))) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            screenshot.recycle();
            instrumentation.runOnMainSync(() -> {
                View content = (View) host.layout_input.getParent();
                Bitmap rendered = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
                content.draw(new Canvas(rendered));
                try (FileOutputStream output = new FileOutputStream(new File(host.getFilesDir(), "private-msg-content.png"))) {
                    rendered.compress(Bitmap.CompressFormat.PNG, 100, output);
                } catch (Exception error) { throw new AssertionError(error); }
                finally { rendered.recycle(); }
            });
        } finally {
            if (activity != null) instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
            clients.set(oldClient);
            for (File file : fixtures) file.delete();
        }
    }

    @Test public void composerFitsNarrowScreensAndMultilineInput() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            float density = instrumentation.getTargetContext().getResources().getDisplayMetrics().density;
            for (int widthDp : new int[]{120, 170, 200, 400}) {
                FrameLayout root = (FrameLayout) LayoutInflater.from(instrumentation.getTargetContext())
                        .inflate(R.layout.activity_private_msg, null);
                EditText input = root.findViewById(R.id.msg_input_et);
                for (String text : new String[]{"", "A message", "one\ntwo\nthree\nfour\nfive"}) {
                    input.setText(text);
                    for (boolean showPreview : new boolean[]{false, true}) {
                        root.findViewById(R.id.image_preview_container)
                                .setVisibility(showPreview ? View.VISIBLE : View.GONE);
                        int width = Math.round(widthDp * density);
                        int height = Math.round(240 * density);
                        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                        root.layout(0, 0, width, height);
                        View row = root.findViewById(R.id.message_input_row);
                        View composer = root.findViewById(R.id.layout_input);
                        ImageButton pick = root.findViewById(R.id.pick_image_btn);
                        ImageButton send = root.findViewById(R.id.send_btn);
                        assertEquals("Composer floated away from bottom", height, composer.getBottom());
                        assertTrue(input.getWidth() > 0);
                        assertTrue(input.getRight() <= pick.getLeft());
                        assertTrue(pick.getRight() <= send.getLeft());
                        assertEquals(row.getHeight(), send.getBottom());
                        assertEquals(send.getBottom(), pick.getBottom());
                        assertTrue(composer.getTop() >= root.findViewById(R.id.top).getBottom());
                    }
                }
            }
        });
    }

    private static void assertBottomLayout(View root) {
        View input = root.findViewById(R.id.msg_input_et);
        View pick = root.findViewById(R.id.pick_image_btn);
        View send = root.findViewById(R.id.send_btn);
        Rect inputRect = new Rect();
        Rect pickRect = new Rect();
        Rect sendRect = new Rect();
        assertTrue(input.getGlobalVisibleRect(inputRect));
        assertTrue(pick.getGlobalVisibleRect(pickRect));
        assertTrue(send.getGlobalVisibleRect(sendRect));
        assertEquals(send.getHeight(), sendRect.height());
        assertTrue(inputRect.right <= pickRect.left);
        assertTrue(pickRect.right <= sendRect.left);
        assertTrue(Math.abs(inputRect.bottom - sendRect.bottom) <= 1);
    }

    private static void call(Method method, Object target) {
        try { method.invoke(target); }
        catch (Exception error) { throw new AssertionError(error); }
    }

    private static void assertMessagesVisible(PrivateMsgActivity host) {
        for (int i = 0; i < 2; i++) {
            View item = host.msgView.getLayoutManager().findViewByPosition(i);
            assertNotNull("Message disappeared after preview expansion", item);
            TextView text = item.findViewById(R.id.msg_text_content);
            assertEquals(i == 0 ? "Message A" : "Message B", text.getText().toString());
            Rect visible = new Rect();
            // Older messages may scroll off a compact viewport when previews expand.
            if (i == 1) {
                assertTrue("Latest message was covered by input/preview", text.getGlobalVisibleRect(visible));
                int[] inputPosition = new int[2];
                host.layout_input.getLocationOnScreen(inputPosition);
                assertTrue(visible.bottom <= inputPosition[1]);
            }
        }
    }
}
