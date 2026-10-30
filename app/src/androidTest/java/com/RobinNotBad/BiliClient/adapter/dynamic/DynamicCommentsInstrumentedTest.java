package com.RobinNotBad.BiliClient.adapter.dynamic;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextThemeWrapper;
import android.widget.TextView;
import android.widget.ScrollView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager.widget.ViewPager;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicInfoActivity;
import com.RobinNotBad.BiliClient.activity.dynamic.send.SendDynamicActivity;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.model.Dynamic;
import com.RobinNotBad.BiliClient.model.Stats;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DynamicCommentsInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final ConcurrentLinkedQueue<HttpUrl> replyRequests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<HttpUrl> mutationRequests = new ConcurrentLinkedQueue<>();
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient oldClient;
    private TestActivity host;
    private String tutorialKey;
    private int oldTutorialVersion;
    private boolean oldPagerTutorial;
    private String oldWbiKey;
    private int oldWbiDate;

    @Before public void setUp() throws Exception {
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        clients = (AtomicReference<OkHttpClient>) field.get(null);
        oldClient = clients.get();
        oldWbiKey = SharedPreferencesUtil.getString("wbi_mixin_key", "");
        oldWbiDate = SharedPreferencesUtil.getInt("last_wbi", 0);
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            HttpUrl url = chain.request().url();
            if (!"GET".equals(chain.request().method()) && !"HEAD".equals(chain.request().method()))
                mutationRequests.add(url);
            if (url.encodedPath().contains("/reply/")) replyRequests.add(url);
            String body = url.encodedPath().endsWith("/nav")
                    ? "{\"code\":0,\"data\":{\"wbi_img\":{\"img_url\":\"https://example.invalid/00000000000000000000000000000000.png\",\"sub_url\":\"https://example.invalid/11111111111111111111111111111111.png\"}}}"
                    : "{\"code\":0,\"data\":{\"cursor\":{\"is_begin\":true,\"is_end\":true},\"replies\":[]}}";
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
        }).build());
        tutorialKey = "tutorial_ver_" + instrumentation.getTargetContext()
                .getResources().getStringArray(R.array.tutorial_list)[6];
        oldTutorialVersion = SharedPreferencesUtil.getInt(tutorialKey, -1);
        oldPagerTutorial = SharedPreferencesUtil.getBoolean("tutorial_pager_DynamicInfoActivity", true);
        SharedPreferencesUtil.putInt(tutorialKey, 100);
        SharedPreferencesUtil.putBoolean("tutorial_pager_DynamicInfoActivity", false);
        host = (TestActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @After public void tearDown() {
        if (host != null) instrumentation.runOnMainSync(host::finish);
        instrumentation.waitForIdleSync();
        clients.set(oldClient);
        SharedPreferencesUtil.putInt(tutorialKey, oldTutorialVersion);
        SharedPreferencesUtil.putBoolean("tutorial_pager_DynamicInfoActivity", oldPagerTutorial);
        SharedPreferencesUtil.putString("wbi_mixin_key", oldWbiKey);
        SharedPreferencesUtil.putInt("last_wbi", oldWbiDate);
    }

    @Test public void countsRebindAndControlsFitNarrowScreens() {
        instrumentation.runOnMainSync(() -> {
            View card = LayoutInflater.from(host).inflate(R.layout.cell_dynamic, null);
            DynamicHolder holder = new DynamicHolder(card, host, false);
            Dynamic dynamic = fixture(90000000001L, 17001L, 17);
            for (int count : new int[]{0, 65, 12345, Integer.MAX_VALUE}) {
                dynamic.stats.reply = count;
                holder.showDynamic(host, dynamic, true);
                assertEquals(StringUtil.toWan(count), holder.commentCount.getText().toString());
                assertTrue(holder.commentCount.isEnabled());
                assertTrue(holder.commentCount.hasOnClickListeners());
            }
            dynamic.dynamicId = 0;
            holder.showDynamic(host, dynamic, true);
            assertFalse(holder.commentCount.isEnabled());
            dynamic.dynamicId = 90000000001L;
            dynamic.stats.reply = 12345;
            holder.showDynamic(host, dynamic, true);
            assertTrue(holder.commentCount.isEnabled());
            measure(card, 170);
            assertControlsFit(card);
        });
    }

    @Test public void actionRowAdaptsToCardWidthFontsAndLikeIconRebinding() {
        instrumentation.runOnMainSync(() -> {
            for (float scale : new float[]{1f, 1.5f}) {
                Configuration config = new Configuration(host.getResources().getConfiguration());
                config.fontScale = scale;
                Context context = new ContextThemeWrapper(host.createConfigurationContext(config),
                        R.style.Theme_BiliClient);
                View card = LayoutInflater.from(context).inflate(R.layout.cell_dynamic, null);
                DynamicHolder holder = new DynamicHolder(card, host, false);
                Dynamic dynamic = fixture(90000000001L, 17001L, 17);
                for (int widthDp : new int[]{120, 170, 200, 240, 320, 480}) {
                    for (boolean own : new boolean[]{false, true}) {
                        for (boolean liked : new boolean[]{false, true, false}) {
                            dynamic.stats.liked = liked;
                            dynamic.stats.like = Integer.MAX_VALUE;
                            dynamic.stats.reply = Integer.MAX_VALUE;
                            holder.showDynamic(context, dynamic, true);
                            holder.item_dynamic_delete.setVisibility(own ? View.VISIBLE : View.GONE);
                            measure(card, widthDp);
                            assertControlsFit(card);
                        }
                        if (scale == 1f && own && (widthDp == 170 || widthDp == 320)) {
                            dynamic.stats.like = 3;
                            dynamic.stats.reply = 7;
                            holder.showDynamic(context, dynamic, true);
                            holder.item_dynamic_delete.setVisibility(View.VISIBLE);
                            measure(card, widthDp);
                            assertControlsFit(card);
                            saveCard(card, "dynamic-actions-" + widthDp + ".png");
                        }
                    }
                }
                measure(card, 170);
                int smallIcon = actionIcon(holder.likeCount).getBounds().width();
                float smallText = holder.likeCount.getTextSize();
                measure(card, 480);
                assertTrue("Wide cards must enlarge action icons",
                        actionIcon(holder.likeCount).getBounds().width() > smallIcon);
                assertTrue("Wide cards must enlarge labels", holder.likeCount.getTextSize() > smallText);
                assertNotNull("Wide cards use inline actions", holder.likeCount.getCompoundDrawables()[0]);
                measure(card, 170);
                assertNotNull("Small cards keep readable stacked actions",
                        holder.likeCount.getCompoundDrawables()[1]);
            }
        });
    }

    @Test public void commentButtonOpensCorrectThreadWithoutSeekingFakeReply() {
        // Video, image/opus and repost cards use distinct comment OIDs/types.
        for (int type : new int[]{1, 11, 17}) {
            Dynamic dynamic = fixture(90000000100L + type, 80000L + type, type);
            Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                    DynamicInfoActivity.class.getName(), null, false);
            DynamicInfoActivity detail = null;
            try {
                instrumentation.runOnMainSync(() -> {
                    View card = LayoutInflater.from(host).inflate(R.layout.cell_dynamic, null);
                    DynamicHolder holder = new DynamicHolder(card, host, false);
                    holder.showDynamic(host, dynamic, true);
                    assertTrue(holder.commentCount.performClick());
                });
                // Older Android versions may notify a newly added monitor
                // about the preceding activity while it is still finishing.
                long launchDeadline = SystemClock.uptimeMillis() + 5000L;
                do {
                    detail = (DynamicInfoActivity) monitor.waitForActivityWithTimeout(
                            Math.max(1L, launchDeadline - SystemClock.uptimeMillis()));
                    if (detail == null || detail.getIntent().getLongExtra("id", 0) == dynamic.dynamicId) break;
                    detail = null;
                } while (SystemClock.uptimeMillis() < launchDeadline);
                assertNotNull("Comment button did not open dynamic details", detail);
                DynamicInfoActivity opened = detail;
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(() -> {
                    assertEquals(dynamic.dynamicId, opened.getIntent().getLongExtra("id", 0));
                    assertEquals(dynamic.comment_id, opened.getIntent().getLongExtra("commentId", 0));
                    assertEquals(type, opened.getIntent().getIntExtra("commentType", 0));
                    assertFalse(opened.getIntent().hasExtra("seekReply"));
                    ViewPager pager = opened.findViewById(R.id.viewPager);
                    assertEquals(1, pager.getCurrentItem());
                    pager.setCurrentItem(0, false);
                    View button = opened.findViewById(R.id.item_dynamic_comment);
                    assertNotNull(button);
                    button.performClick();
                    assertEquals(1, pager.getCurrentItem());
                });
                long until = SystemClock.uptimeMillis() + 5000L;
                while (!hasReplyRequest(dynamic) && SystemClock.uptimeMillis() < until) SystemClock.sleep(50L);
                assertTrue("Wrong comment OID/type requested", hasReplyRequest(dynamic));
            } finally {
                if (detail != null) {
                    DynamicInfoActivity opened = detail;
                    instrumentation.runOnMainSync(opened::finish);
                }
                instrumentation.removeMonitor(monitor);
                instrumentation.waitForIdleSync();
            }
        }
    }

    @Test public void deleteIconIsCenteredInsideItsTouchTarget() {
        instrumentation.runOnMainSync(() -> {
            for (float scale : new float[]{1f, 1.5f}) {
                Configuration config = new Configuration(host.getResources().getConfiguration());
                config.fontScale = scale;
                Context context = new ContextThemeWrapper(host.createConfigurationContext(config), R.style.Theme_BiliClient);
                View card = LayoutInflater.from(context).inflate(R.layout.cell_dynamic, null);
                DynamicHolder holder = new DynamicHolder(card, host, false);
                holder.showDynamic(context, fixture(90000000001L, 17001L, 17), true);
                holder.item_dynamic_delete.setVisibility(View.VISIBLE);
                for (int widthDp : new int[]{120, 170, 320, 480}) {
                    measure(card, widthDp);
                    View button = holder.item_dynamic_delete;
                    int size = Math.round(48 * button.getResources().getDisplayMetrics().density);
                    assertEquals("Delete touch width changed", size, button.getWidth());
                    assertEquals("Delete touch height changed", size, button.getHeight());
                    assertIconPixelsCentered(button);
                    if (scale == 1f && (widthDp == 170 || widthDp == 320)) {
                        saveCard(card, "dynamic-delete-centered-" + widthDp + ".png");
                    }
                }
            }
        });
    }

    @Test public void repostPreviewKeepsInlineActionsWhenTheyFitAfterScrolling() throws Exception {
        boolean hadMid = SharedPreferencesUtil.getSharedPreferences().contains(SharedPreferencesUtil.mid);
        long oldMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        Object oldForward = TerminalContext.getInstance().getForwardContent();
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, 123456789L);
        Dynamic dynamic = fixture(90000000301L, 80000301L, 1);
        dynamic.stats.like = 19;
        dynamic.stats.reply = 4;
        dynamic.stats.liked = true;
        StringBuilder content = new StringBuilder();
        for (int line = 0; line < 12; line++) content.append("转发预览排版检查\n");
        dynamic.content = content.toString();
        dynamic.major_type = "MAJOR_TYPE_ARCHIVE";
        dynamic.major_object = new VideoCard("测试视频", "测试UP主", "734", "", 80000301L, "");
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(SendDynamicActivity.class.getName(), null, false);
        SendDynamicActivity editor = null;
        try {
            instrumentation.runOnMainSync(() -> {
                View card = LayoutInflater.from(host).inflate(R.layout.cell_dynamic, null);
                DynamicHolder holder = new DynamicHolder(card, host, false);
                holder.showDynamic(host, dynamic, true);
                assertTrue("Forward entry is missing", holder.item_dynamic_share.performClick());
            });
            editor = (SendDynamicActivity) monitor.waitForActivityWithTimeout(10000L);
            assertNotNull("Forward entry did not open the composer", editor);
            SendDynamicActivity opened = editor;
            long deadline = SystemClock.uptimeMillis() + 10000L;
            while ((opened.findViewById(R.id.dynamic_control) == null
                    || opened.findViewById(R.id.dynamic_control).getWidth() == 0)
                    && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50L);
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                View preview = opened.findViewById(R.id.forwardCard);
                View row = preview.findViewById(R.id.dynamic_control);
                assertNotNull("Forward preview was not inflated", row);
                android.view.ViewParent parent = preview.getParent();
                while (!(parent instanceof ScrollView) && parent != null) parent = parent.getParent();
                assertTrue("Forward form must scroll", parent instanceof ScrollView);
                ScrollView scroll = (ScrollView) parent;
                assertTrue("Fixture must require scrolling", scroll.getChildAt(0).getHeight() > scroll.getHeight());
                scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                View preview = opened.findViewById(R.id.forwardCard);
                TextView likes = preview.findViewById(R.id.likes);
                TextView comments = preview.findViewById(R.id.item_dynamic_comment);
                Rect visible = new Rect();
                assertTrue("Forward actions were not reachable after scrolling",
                        preview.findViewById(R.id.dynamic_control).getGlobalVisibleRect(visible));
                assertEquals("19", likes.getText().toString());
                assertEquals("4", comments.getText().toString());
                assertControlsFit(preview);
                saveCard(preview, "dynamic-repost-preview.png");
                for (int id : new int[]{R.id.likes, R.id.item_dynamic_comment, R.id.item_dynamic_share}) {
                    TextView action = preview.findViewById(id);
                    assertNotNull("Nested preview reverted to stacked actions although the content fits",
                            action.getCompoundDrawables()[0]);
                }
            });
            assertTrue("Opening a forward preview must not publish or mutate content", mutationRequests.isEmpty());
        } finally {
            if (editor != null) {
                SendDynamicActivity opened = editor;
                instrumentation.runOnMainSync(opened::finish);
                long deadline = SystemClock.uptimeMillis() + 5000L;
                while (!opened.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50L);
            }
            instrumentation.removeMonitor(monitor);
            if (hadMid) SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid, oldMid);
            else SharedPreferencesUtil.removeValue(SharedPreferencesUtil.mid);
            TerminalContext.getInstance().setForwardContent(oldForward);
        }
    }

    private static void assertIconPixelsCentered(View button) {
        Bitmap bitmap = Bitmap.createBitmap(button.getWidth(), button.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(android.graphics.Color.BLACK);
            button.draw(canvas);
            int left = bitmap.getWidth(), top = bitmap.getHeight(), right = -1, bottom = -1;
            for (int y = 0; y < bitmap.getHeight(); y++) {
                for (int x = 0; x < bitmap.getWidth(); x++) {
                    int pixel = bitmap.getPixel(x, y);
                    if (android.graphics.Color.red(pixel) > 40 && android.graphics.Color.green(pixel) > 40
                            && android.graphics.Color.blue(pixel) > 40) {
                        left = Math.min(left, x);
                        top = Math.min(top, y);
                        right = Math.max(right, x);
                        bottom = Math.max(bottom, y);
                    }
                }
            }
            assertTrue("Delete icon must actually paint pixels", right >= left && bottom >= top);
            float tolerance = 1.5f * button.getResources().getDisplayMetrics().density;
            assertEquals("Delete icon is horizontally off-center", (bitmap.getWidth() - 1) / 2f,
                    (left + right) / 2f, tolerance);
            assertEquals("Delete icon is vertically off-center", (bitmap.getHeight() - 1) / 2f,
                    (top + bottom) / 2f, tolerance);
        } finally {
            bitmap.recycle();
        }
    }

    private boolean hasReplyRequest(Dynamic dynamic) {
        for (HttpUrl url : replyRequests) {
            if (String.valueOf(dynamic.comment_id).equals(url.queryParameter("oid"))
                    && String.valueOf(dynamic.comment_type).equals(url.queryParameter("type"))
                    && url.queryParameter("seek_rpid") == null) return true;
        }
        return false;
    }

    private static Dynamic fixture(long id, long commentId, int commentType) {
        Dynamic dynamic = new Dynamic();
        dynamic.dynamicId = id;
        dynamic.comment_id = commentId;
        dynamic.comment_type = commentType;
        dynamic.userInfo = new UserInfo();
        dynamic.userInfo.name = "\u5c81\u793chana";
        dynamic.userInfo.avatar = "";
        dynamic.pubTime = "2\u5929\u524d";
        dynamic.content = "\u4eca\u5929\u7684\u65b0\u89c6\u9891\u53d1\u5e03\u4e86";
        dynamic.stats = new Stats();
        dynamic.stats.like = 65;
        return dynamic;
    }

    private static void measure(View card, int widthDp) {
        int width = Math.round(widthDp * card.getResources().getDisplayMetrics().density);
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        card.layout(0, 0, width, card.getMeasuredHeight());
    }

    private static void assertControlsFit(View card) {
        List<Rect> bounds = new ArrayList<>();
        for (int id : new int[]{R.id.likes, R.id.item_dynamic_comment,
                R.id.item_dynamic_share, R.id.item_dynamic_delete}) {
            View button = card.findViewById(id);
            if (button.getVisibility() == View.GONE) continue;
            boolean primaryAction = id != R.id.item_dynamic_delete;
            if (primaryAction) assertNotNull("Missing action icon", actionIcon((TextView) button));
            Rect rect = new Rect(0, 0, button.getWidth(), button.getHeight());
            ((ViewGroup) card).offsetDescendantRectToMyCoords(button, rect);
            if (primaryAction) {
                View row = card.findViewById(R.id.dynamic_control);
                assertEquals("Primary action wrapped", row.getTop(), rect.top);
                assertTrue("Unequal action columns", Math.abs(button.getWidth() * 3 - row.getWidth()) <= 3);
                assertTrue("Touch target too short", button.getHeight()
                        >= Math.round(48 * card.getResources().getDisplayMetrics().density));
            } else {
                View header = card.findViewById(R.id.dynamic_header);
                assertTrue("Delete action must stay in the header",
                        rect.top >= header.getTop() && rect.bottom <= header.getBottom());
            }
            assertTrue("Button clipped horizontally", rect.left >= card.getPaddingLeft()
                    && rect.right <= card.getWidth() - card.getPaddingRight());
            assertTrue("Button clipped by divider", rect.bottom <= card.findViewById(R.id.divider).getTop());
            assertTrue("Empty touch area", rect.width() > 0 && rect.height() > 0);
            for (Rect previous : bounds) assertFalse("Overlapping actions", Rect.intersects(previous, rect));
            bounds.add(rect);
            if (primaryAction) {
                TextView label = (TextView) button;
                assertNotNull(label.getLayout());
                for (int line = 0; line < label.getLayout().getLineCount(); line++) {
                    assertTrue("Action label clipped", label.getLayout().getLineWidth(line)
                            <= label.getWidth() - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight() + 1);
                }
            }
        }
    }

    private static android.graphics.drawable.Drawable actionIcon(TextView button) {
        android.graphics.drawable.Drawable[] icons = button.getCompoundDrawables();
        return icons[0] != null ? icons[0] : icons[1];
    }

    private void saveCard(View card, String name) {
        Bitmap bitmap = Bitmap.createBitmap(card.getWidth(), card.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.BLACK);
        card.draw(canvas);
        try (FileOutputStream output = new FileOutputStream(new File(host.getFilesDir(), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (Exception error) {
            throw new AssertionError(error);
        } finally {
            bitmap.recycle();
        }
    }
}
