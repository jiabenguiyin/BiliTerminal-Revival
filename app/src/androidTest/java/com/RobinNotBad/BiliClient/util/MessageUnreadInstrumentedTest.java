package com.RobinNotBad.BiliClient.util;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.MenuActivity;
import com.RobinNotBad.BiliClient.activity.message.MessageActivity;
import com.RobinNotBad.BiliClient.activity.message.MessageSettingsActivity;
import com.RobinNotBad.BiliClient.activity.message.NoticeActivity;
import com.RobinNotBad.BiliClient.ui.MessageBadgeStyle;
import com.google.android.material.button.MaterialButton;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MessageUnreadInstrumentedTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> saved=new HashMap<>();
    private final AtomicInteger acknowledgements=new AtomicInteger(),replyReads=new AtomicInteger();
    private final AtomicInteger sessionLists = new AtomicInteger(), settingsWrites = new AtomicInteger();
    private final AtomicInteger systemReads = new AtomicInteger(), olderSessionPages = new AtomicInteger();
    private final AtomicReference<String> lastAckBody = new AtomicReference<>();
    private volatile int at,reply,like,system,privateUnread;
    private volatile boolean feedFailure,privateFailure,extras;
    private volatile boolean bulkSessions, rejectAcknowledgement, systemFeedFailure;
    private AtomicReference<OkHttpClient> clients;private OkHttpClient original;
    private MenuActivity menu;private MessageActivity messages;private NoticeActivity notice;
    private com.RobinNotBad.BiliClient.activity.settings.SettingPrefActivity preferences;
    private MessageSettingsActivity messageSettings;

    @Before public void setup()throws Exception {
        SharedPreferences prefs=inst.getTargetContext().getSharedPreferences("default",0);SharedPreferencesUtil.sharedPreferences=prefs;
        List<String> keys=new ArrayList<>(Arrays.asList(SharedPreferencesUtil.mid,SharedPreferencesUtil.cookies,SharedPreferencesUtil.csrf,
                SharedPreferencesUtil.MESSAGE_UPDATE_NUM,SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,SharedPreferencesUtil.MESSAGE_MENU_ARROW_DOT_ENABLE,SharedPreferencesUtil.MESSAGE_REFRESH_ON_RETURN,SharedPreferencesUtil.MENU_SORT,"menu_message","dpi","density","player_ui_round"));
        for(String tag:inst.getTargetContext().getResources().getStringArray(R.array.tutorial_list))keys.add("tutorial_ver_"+tag);
        for(String key:keys)saved.put(key,prefs.getAll().get(key));
        SharedPreferences.Editor edit=prefs.edit().putLong(SharedPreferencesUtil.mid,42)
                .putString(SharedPreferencesUtil.cookies,"DedeUserID=42; SESSDATA=fixture; bili_jct=fixture").putString(SharedPreferencesUtil.csrf,"fixture")
                .putBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,true).putBoolean("menu_message",true)
                .remove(SharedPreferencesUtil.MESSAGE_MENU_ARROW_DOT_ENABLE)
                .remove(SharedPreferencesUtil.MESSAGE_REFRESH_ON_RETURN)
                .putString(SharedPreferencesUtil.MENU_SORT,"message;recommend;popular;precious;live;search;dynamic;myspace;local;settings")
                .putFloat("dpi",1f).putInt("density",-1).putBoolean("player_ui_round",false);
        for(String tag:inst.getTargetContext().getResources().getStringArray(R.array.tutorial_list))edit.putInt("tutorial_ver_"+tag,99);
        edit.commit();NetWorkUtil.refreshHeaders();MessageUnreadCounter.initialize();
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);original=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            String path=chain.request().url().encodedPath();JSONObject data=new JSONObject();
            try {
                int code=0;
                if(path.endsWith("/msgfeed/unread")) {
                    code=feedFailure?-101:0;data.put("at",at).put("reply",reply).put("like",like).put("sys_msg",system);
                } else if(path.endsWith("/single_unread")) {
                    code=privateFailure?-101:0;data.put("follow_unread",privateUnread).put("unfollow_unread",0)
                            .put("unfollow_push_msg",extras?1:0).put("dustbin_unread",extras?1:0).put("custom_unread",extras?1:0);
                } else if (path.endsWith("/link_setting/get")) {
                    code = -101; // The local switch must remain usable if the account API fails.
                } else if (path.endsWith("/link_setting/set")) {
                    settingsWrites.incrementAndGet();
                } else if (path.endsWith("/get_sessions")) {
                    sessionLists.incrementAndGet();
                    JSONArray rows = new JSONArray();
                    HttpUrl url = chain.request().url();
                    if (bulkSessions && "2".equals(url.queryParameter("sort_rule"))) {
                        String endTs = url.queryParameter("end_ts");
                        if (endTs == null) {
                            rows.put(new JSONObject().put("talker_id", 11).put("session_ts", 900)
                                    .put("unread_count", 0));
                            data.put("has_more", 1);
                        } else if ("900".equals(endTs)) {
                            olderSessionPages.incrementAndGet();
                            rows.put(new JSONObject().put("talker_id", 99).put("session_ts", 800)
                                    .put("max_seqno", "9007199254740997").put("unread_count", privateUnread));
                            data.put("has_more", 0);
                        }
                    }
                    data.put("session_list", rows);
                }
                else if(path.endsWith("/msgfeed/reply")) {
                    replyReads.incrementAndGet();reply=0;
                    data.put("items",new JSONArray()).put("cursor",new JSONObject().put("is_end",true));
                } else if (path.endsWith("/msgfeed/at")) {
                    at = 0;
                } else if (path.endsWith("/msgfeed/like")) {
                    like = 0;
                } else if (path.endsWith("/query_user_notify") || path.endsWith("/query_unified_notify")) {
                    boolean unified = path.endsWith("/query_unified_notify");
                    String size = chain.request().url().queryParameter("page_size");
                    // Match the real service: an oversized personal feed returns HTTP 200 / code -6.
                    if (systemFeedFailure || !(unified ? "10" : "20").equals(size)) {
                        code = -6;
                    } else {
                        long cursor = unified ? 990 : 800;
                        data.put("system_notify_list", new JSONArray().put(new JSONObject().put("id", cursor)
                                .put("cursor", cursor).put("title", "通知").put("time_at", "2026-11-04")));
                    }
                } else if (path.endsWith("/update_cursor")) {
                    systemReads.incrementAndGet();
                    if ("990".equals(chain.request().url().queryParameter("cursor"))) system = 0;
                    else code = -400;
                } else if (path.endsWith("/update_ack")) {
                    acknowledgements.incrementAndGet();
                    okio.Buffer body = new okio.Buffer();
                    chain.request().body().writeTo(body);
                    lastAckBody.set(body.readUtf8());
                    if (rejectAcknowledgement) code = -400;
                    else if (bulkSessions && lastAckBody.get().contains("talker_id=99&session_type=1&ack_seqno=9007199254740997")) {
                        privateUnread = 0;
                    }
                }
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"),new JSONObject().put("code",code).put("data",data).toString())).build();
            }catch(JSONException e){throw new java.io.IOException(e);}
        }).build());
    }
    @After public void cleanup()throws Exception {
        inst.runOnMainSync(()->{if(messageSettings!=null)messageSettings.finish();if(preferences!=null)preferences.finish();if(notice!=null)notice.finish();if(menu!=null)menu.finish();if(messages!=null)messages.finish();});inst.waitForIdleSync();
        settleChecks();clients.set(original);MessageUnreadCounter.invalidate();
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> e:saved.entrySet()) {
            Object v=e.getValue();String k=e.getKey();if(v==null)edit.remove(k);else if(v instanceof Integer)edit.putInt(k,(Integer)v);
            else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof String)edit.putString(k,(String)v);
            else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);
        }
        edit.commit();NetWorkUtil.refreshHeaders();
    }
    @Test public void oldPersistedThreeIsNotDisplayedWithoutAFreshConfirmation()throws Exception {
        feedFailure=privateFailure=true;SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM,3);openMenu();settleChecks();
        assertEquals("消息",label());assertEquals(0,MessageUnreadCounter.getCount());assertNull(button().getIcon());
    }
    @Test public void likesAndSystemMessagesLightTheArrowAndHighlightTheMenuEntry()throws Exception {
        like=2;system=1;openMessages();await(()->MessageUnreadCounter.getCount()==3&&messageListLoaded()&&arrowUnread());
        assertTrue(arrowUnread());capture("unread-collapsed.png");
        assertTrue(((TextView)messages.findViewById(R.id.like_text)).getText().toString().contains("2未读"));
        assertTrue(((TextView)messages.findViewById(R.id.system_text)).getText().toString().contains("1未读"));
        openMenu();await(()->label().contains("(3)"));assertNotNull(button().getIcon());assertTrue(button().getStrokeWidth()>0);capture("unread-expanded.png");
    }
    @Test public void openingAndReopeningTheMessageOverviewDoesNotReadEverything()throws Exception {
        at=1;reply=2;like=3;system=4;privateUnread=5;openMessages();await(()->messageListLoaded()&&MessageUnreadCounter.getCount()==15);
        inst.runOnMainSync(messages::finish);inst.waitForIdleSync();openMessages();await(()->messageListLoaded()&&MessageUnreadCounter.getCount()==15);
        assertEquals(0,acknowledgements.get());assertEquals(0,replyReads.get());assertTrue(arrowUnread());
        openMenu();await(()->label().contains("(15)"));
    }
    @Test public void readingRepliesKeepsTheUnreadLikesAndPrivateMessages()throws Exception {
        reply=2;like=4;privateUnread=3;openMessages();await(()->MessageUnreadCounter.getCount()==9&&messageListLoaded());
        Instrumentation.ActivityMonitor monitor=inst.addMonitor(NoticeActivity.class.getName(),null,false);
        try {
            inst.runOnMainSync(()->messages.findViewById(R.id.reply).performClick());
            notice=(NoticeActivity)monitor.waitForActivityWithTimeout(5000);assertNotNull(notice);
            await(()->replyReads.get()>0&&MessageUnreadCounter.getCount()==7);
            inst.runOnMainSync(notice::finish);inst.waitForIdleSync();
            await(()->((TextView)messages.findViewById(R.id.reply_text)).getText().toString().equals("回复我的"));
            assertTrue(arrowUnread());openMenu();await(()->label().contains("(7)"));
        }finally{inst.removeMonitor(monitor);}
    }
    @Test public void disablingReturnRefreshKeepsTheListAndAllowsManualRefresh() throws Exception {
        like = 1;
        openMessages();
        await(this::messageListLoaded);
        assertEquals(1, sessionLists.get());
        RecyclerView list = messages.findViewById(R.id.sessions_list);
        RecyclerView.Adapter<?> originalList = list.getAdapter();
        SwipeRefreshLayout refresh = messages.findViewById(R.id.swipeRefreshLayout);

        CompoundButton option = openMessageRefreshSetting();
        assertTrue(option.isChecked());
        inst.runOnMainSync(option::performClick);
        assertFalse(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_REFRESH_ON_RETURN, true));
        like = 2;
        inst.runOnMainSync(messageSettings::finish);
        inst.waitForIdleSync();
        await(() -> !refresh.isRefreshing() && messages.hasWindowFocus());
        assertSame(originalList, list.getAdapter());
        assertEquals(1, sessionLists.get());
        assertTrue(((TextView) messages.findViewById(R.id.like_text)).getText().toString().contains("1未读"));

        assertTrue(refresh.isEnabled());
        Field listener = SwipeRefreshLayout.class.getDeclaredField("mListener");
        listener.setAccessible(true);
        inst.runOnMainSync(((SwipeRefreshLayout.OnRefreshListener) listener.get(refresh))::onRefresh);
        await(() -> list.getAdapter() != originalList && !refresh.isRefreshing());
        assertEquals(2, sessionLists.get());
        assertTrue(((TextView) messages.findViewById(R.id.like_text)).getText().toString().contains("2未读"));

        CompoundButton persistedOption = openMessageRefreshSetting();
        assertFalse(persistedOption.isChecked());
        inst.runOnMainSync(persistedOption::performClick);
        inst.runOnMainSync(messageSettings::finish);
        await(() -> sessionLists.get() == 3 && !refresh.isRefreshing());
        assertEquals(0, settingsWrites.get());
    }

    private CompoundButton openMessageRefreshSetting() throws Exception {
        Instrumentation.ActivityMonitor monitor = inst.addMonitor(MessageSettingsActivity.class.getName(), null, false);
        try {
            inst.runOnMainSync(() -> messages.findViewById(R.id.setting_btn).performClick());
            messageSettings = (MessageSettingsActivity) monitor.waitForActivityWithTimeout(5000);
            assertNotNull(messageSettings);
            await(() -> messageSettings.findViewById(R.id.setting_switch) != null
                    && !((SwipeRefreshLayout) messageSettings.findViewById(R.id.swipeRefreshLayout)).isRefreshing());
            return messageSettings.findViewById(R.id.setting_switch);
        } finally {
            inst.removeMonitor(monitor);
        }
    }

    @Test public void endpointFailuresKeepKnownUnreadUntilSuccessfulZeroIsConfirmed()throws Exception {
        reply=2;privateUnread=3;openMenu();await(()->MessageUnreadCounter.getCount()==5);settleChecks();
        privateFailure=true;reply=1;refreshTo(4);
        feedFailure=true;privateFailure=false;privateUnread=0;refreshTo(1);
        privateFailure=true;refreshTo(1);
        feedFailure=privateFailure=false;reply=0;refreshTo(0);
        await(()->label().equals("消息"));assertNull(button().getIcon());
    }
    @Test public void changingAccountsCannotReviveThePreviousAccountsResult()throws Exception {
        reply=3;openMenu();await(()->MessageUnreadCounter.getCount()==3);settleChecks();
        MessageUnreadState.Check old=MessageUnreadCounter.beginCheck();
        inst.runOnMainSync(()->{reply=5;SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,43);});
        await(()->MessageUnreadCounter.getCount()==5);
        assertFalse(MessageUnreadCounter.complete(old,99,0));assertEquals(5,MessageUnreadCounter.getCount());
        inst.runOnMainSync(()->{reply=1;SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,42);});
        await(()->MessageUnreadCounter.getCount()==1);assertFalse(MessageUnreadCounter.complete(old,99,0));
    }
    @Test public void extraServerBucketsDoNotBecomeThreeOrdinaryPrivateMessages()throws Exception {
        extras=true;openMenu();settleChecks();assertEquals(0,MessageUnreadCounter.getCount());assertEquals("消息",label());assertNull(button().getIcon());
    }
    @Test public void markAllReadRequiresConfirmationAndFindsAnOlderPrivateConversation() throws Exception {
        reply = at = like = system = privateUnread = 1;
        bulkSessions = true;
        openMessages();
        await(() -> messageListLoaded() && MessageUnreadCounter.getCount() == 5);
        int initialOlderSessionPages = olderSessionPages.get();
        assertTrue(messages.findViewById(R.id.system).isShown());
        assertToolbarReadAction();
        Rect group = bounds(messages.findViewById(R.id.message_actions));
        assertTrue(Math.abs(group.centerX() - bounds(messages.findViewById(R.id.top)).centerX()) <= 1);
        assertTrue(Math.abs(group.centerY() - bounds(messages.findViewById(R.id.timeText)).centerY()) <= 1);
        capture("message-toolbar-square.png");
        Rect before = bounds(messages.findViewById(R.id.mark_all_read));
        inst.runOnMainSync(() -> ((androidx.core.widget.NestedScrollView)messages.findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN));
        inst.waitForIdleSync();
        assertEquals(before, bounds(messages.findViewById(R.id.mark_all_read)));
        AlertDialog canceled = openMarkReadConfirmation();
        inst.runOnMainSync(() -> canceled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        assertEquals(5, MessageUnreadCounter.getCount());
        assertEquals(0, replyReads.get());
        assertEquals(0, systemReads.get());
        assertEquals(0, acknowledgements.get());
        AlertDialog confirmed = openMarkReadConfirmation();
        inst.runOnMainSync(() -> confirmed.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(() -> messages.findViewById(R.id.mark_all_read).isEnabled() && MessageUnreadCounter.getCount() == 0);
        assertEquals(1, replyReads.get());
        assertEquals(1, systemReads.get());
        assertEquals(initialOlderSessionPages + 1, olderSessionPages.get());
        assertEquals(1, acknowledgements.get());
        assertTrue(lastAckBody.get().contains("talker_id=99&session_type=1&ack_seqno=9007199254740997"));
    }

    @Test public void roundToolbarSupportsLargeUiAndCancelLeavesUnreadUntouched() throws Exception {
        SharedPreferencesUtil.getSharedPreferences().edit().putBoolean("player_ui_round", true).putFloat("dpi", 1.4f).commit();
        reply = 1;
        openMessages();
        await(() -> messageListLoaded() && MessageUnreadCounter.getCount() == 1);
        waitForTouchFocus();
        assertToolbarReadAction();
        inst.runOnMainSync(() -> {
            TextView title = messages.findViewById(R.id.pageName);
            assertTrue("Round title text clipped", title.getLayout().getLineWidth(0)
                    <= title.getWidth() - title.getCompoundPaddingLeft() - title.getCompoundPaddingRight());
        });
        capture("message-toolbar-round.png");
        View feedback = messages.findViewById(R.id.mark_all_read);
        assertEquals(android.os.Build.VERSION.SDK_INT >= 21 ? "android.graphics.drawable.RippleDrawable"
                : "android.graphics.drawable.StateListDrawable", feedback.getBackground().getClass().getName());
        inst.runOnMainSync(() -> feedback.setPressed(true));
        SystemClock.sleep(120);
        capture("message-toolbar-feedback.png");
        inst.runOnMainSync(() -> feedback.setPressed(false));
        Rect action = bounds(messages.findViewById(R.id.mark_all_read));
        long down = SystemClock.uptimeMillis();
        MotionEvent press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, action.centerX(), action.centerY(), 0);
        MotionEvent release = MotionEvent.obtain(down, down + 30, MotionEvent.ACTION_UP, action.centerX(), action.centerY(), 0);
        inst.sendPointerSync(press); inst.sendPointerSync(release); press.recycle(); release.recycle();
        Field field = MessageActivity.class.getDeclaredField("markAllReadDialog");
        field.setAccessible(true);
        await(() -> {
            try { return field.get(messages) != null && ((AlertDialog)field.get(messages)).isShowing(); }
            catch (IllegalAccessException error) { throw new AssertionError(error); }
        });
        AlertDialog confirmation = (AlertDialog)field.get(messages);
        inst.runOnMainSync(() -> confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        inst.waitForIdleSync();
        assertEquals(1, MessageUnreadCounter.getCount());
        assertEquals(0, replyReads.get());
        assertEquals(0, systemReads.get());
        assertEquals(0, acknowledgements.get());
        waitForTouchFocus();
        Instrumentation.ActivityMonitor settings = inst.addMonitor(MessageSettingsActivity.class.getName(), null, false);
        try {
            Rect gear = bounds(messages.findViewById(R.id.setting_btn));
            long when = SystemClock.uptimeMillis();
            MotionEvent pressGear = MotionEvent.obtain(when, when, MotionEvent.ACTION_DOWN, gear.centerX(), gear.centerY(), 0);
            MotionEvent releaseGear = MotionEvent.obtain(when, when + 30, MotionEvent.ACTION_UP, gear.centerX(), gear.centerY(), 0);
            inst.sendPointerSync(pressGear); inst.sendPointerSync(releaseGear); pressGear.recycle(); releaseGear.recycle();
            messageSettings = (MessageSettingsActivity)settings.waitForActivityWithTimeout(5000);
            assertNotNull(messageSettings);
            assertEquals(0, replyReads.get());
            assertEquals(0, acknowledgements.get());
        } finally { inst.removeMonitor(settings); }
    }

    @Test public void enlargedSquareToolbarStacksWithoutCrowdingItsActions() throws Exception {
        SharedPreferencesUtil.putFloat("dpi", 2f);
        like = 1;
        openMessages();
        await(() -> messageListLoaded() && MessageUnreadCounter.getCount() == 1);
        int narrow = Math.min(200, messages.findViewById(R.id.top).getWidth());
        inst.runOnMainSync(() -> messages.getWindow().setLayout(narrow, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        await(() -> messages.findViewById(R.id.top).getWidth() == narrow
                && messages.findViewById(R.id.timeText).getBottom() <= messages.findViewById(R.id.message_heading).getTop());
        assertToolbarReadAction();
        assertTrue(bounds(messages.findViewById(R.id.timeText)).bottom
                <= bounds(messages.findViewById(R.id.message_heading)).top);
        capture("message-toolbar-large-square.png");
        AlertDialog confirmation = openMarkReadConfirmation();
        inst.runOnMainSync(() -> confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        inst.waitForIdleSync();
        assertEquals(1, MessageUnreadCounter.getCount());
        assertEquals(0, acknowledgements.get());
    }

    private Rect bounds(View view) {
        Rect rect = new Rect();
        inst.runOnMainSync(() -> {
            assertTrue(view.getGlobalVisibleRect(rect));
            assertEquals(view.getWidth(), rect.width());
            assertEquals(view.getHeight(), rect.height());
        });
        return rect;
    }

    private void waitForTouchFocus() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 6000;
        while (!messages.getWindow().getDecorView().hasWindowFocus() && SystemClock.uptimeMillis() < deadline) {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.accessibilityservice.AccessibilityServiceInfo info = inst.getUiAutomation().getServiceInfo();
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                        | android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                inst.getUiAutomation().setServiceInfo(info);
                android.view.accessibility.AccessibilityNodeInfo root = inst.getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    boolean warning = false;
                    for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId("android:id/message")) {
                        warning |= node.getText() != null && node.getText().toString().startsWith("Detected problems with API compatibility");
                        node.recycle();
                    }
                    if (warning) for (android.view.accessibility.AccessibilityNodeInfo button : root.findAccessibilityNodeInfosByViewId("android:id/button1")) {
                        button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK); button.recycle();
                    }
                    root.recycle();
                }
            }
            SystemClock.sleep(50);
        }
        assertTrue("Message window did not receive focus", messages.getWindow().getDecorView().hasWindowFocus());
    }

    private void assertToolbarReadAction() {
        View top = messages.findViewById(R.id.top);
        TextView title = messages.findViewById(R.id.pageName);
        assertEquals("Title wrapped", 1, title.getLayout().getLineCount());
        assertEquals("Title ellipsized", 0, title.getLayout().getEllipsisCount(0));
        assertEquals("Title clipped", title.length(), title.getLayout().getLineEnd(0));
        for (int id : new int[]{R.id.mark_all_read, R.id.setting_btn}) {
            View action = messages.findViewById(id);
            View parent = action;
            while (parent != top && parent.getParent() instanceof View) parent = (View)parent.getParent();
            assertSame(top, parent);
            Rect rect = bounds(action);
            assertEquals("Toolbar feedback must stay circular", rect.width(), rect.height());
            assertFalse(Rect.intersects(rect, bounds(messages.findViewById(R.id.pageName))));
            assertFalse(Rect.intersects(rect, bounds(messages.findViewById(R.id.timeText))));
            assertTrue(rect.height() >= Math.min(32, 30 * messages.getResources().getDisplayMetrics().density));
            assertNotNull(action.getContentDescription());
        }
        assertFalse(Rect.intersects(bounds(messages.findViewById(R.id.mark_all_read)), bounds(messages.findViewById(R.id.setting_btn))));
        assertTrue(bounds(messages.findViewById(R.id.setting_btn)).left
                - bounds(messages.findViewById(R.id.mark_all_read)).right >= 2);
    }

    @Test public void openingSystemNotificationsClearsOnlyTheirUnreadCount() throws Exception {
        system = privateUnread = 1;
        openMessages();
        await(() -> messageListLoaded() && MessageUnreadCounter.getCount() == 2);
        Instrumentation.ActivityMonitor monitor = inst.addMonitor(NoticeActivity.class.getName(), null, false);
        try {
            inst.runOnMainSync(() -> messages.findViewById(R.id.system).performClick());
            notice = (NoticeActivity) monitor.waitForActivityWithTimeout(5000);
            assertNotNull(notice);
            await(() -> notice.recyclerView.getAdapter() != null && MessageUnreadCounter.getCount() == 1);
            assertEquals(2, notice.recyclerView.getAdapter().getItemCount());
            assertEquals(1, systemReads.get());
            assertEquals(0, acknowledgements.get());
        } finally {
            inst.removeMonitor(monitor);
        }
    }

    @Test public void systemOnlyUnreadClearsAfterBulkConfirmation() throws Exception {
        system = 1;
        openMessages();
        await(() -> messageListLoaded() && MessageUnreadCounter.getCount() == 1);
        AlertDialog dialog = openMarkReadConfirmation();
        inst.runOnMainSync(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(() -> messages.findViewById(R.id.mark_all_read).isEnabled()
                && MessageUnreadCounter.getCount() == 0);
        assertEquals(1, systemReads.get());
        assertEquals(0, acknowledgements.get());
    }

    @Test public void systemNoticeLoadErrorKeepsUnreadAndCanRetryInPlace() throws Exception {
        system = 1;
        systemFeedFailure = true;
        openMessages();
        await(() -> messageListLoaded() && MessageUnreadCounter.getCount() == 1);
        Instrumentation.ActivityMonitor monitor = inst.addMonitor(NoticeActivity.class.getName(), null, false);
        try {
            inst.runOnMainSync(() -> messages.findViewById(R.id.system).performClick());
            notice = (NoticeActivity) monitor.waitForActivityWithTimeout(5000);
            assertNotNull(notice);
            await(() -> notice.emptyView.isShown()
                    && notice.getString(R.string.message_system_load_failed).contentEquals(notice.emptyView.getText()));
            assertEquals(0, systemReads.get());
            assertEquals(1, MessageUnreadCounter.getCount());

            systemFeedFailure = false;
            inst.runOnMainSync(() -> notice.emptyView.performClick());
            await(() -> notice.recyclerView.getAdapter() != null && MessageUnreadCounter.getCount() == 0);
            assertFalse(notice.emptyView.isShown());
            assertEquals(2, notice.recyclerView.getAdapter().getItemCount());
            assertEquals(1, systemReads.get());
        } finally {
            inst.removeMonitor(monitor);
        }
    }

    @Test public void failedAcknowledgementKeepsTheRemainingUnreadCount() throws Exception {
        reply = privateUnread = 1;
        bulkSessions = rejectAcknowledgement = true;
        openMessages();
        await(() -> messageListLoaded() && MessageUnreadCounter.getCount() == 2);
        AlertDialog dialog = openMarkReadConfirmation();
        inst.runOnMainSync(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        await(() -> messages.findViewById(R.id.mark_all_read).isEnabled() && MessageUnreadCounter.getCount() == 1);
        assertEquals(1, replyReads.get());
        assertEquals(1, acknowledgements.get());
        assertTrue(arrowUnread());
    }

    @Test public void confirmingAfterAccountSwitchDoesNotMarkEitherAccountRead() throws Exception {
        reply = privateUnread = 1;
        bulkSessions = true;
        openMessages();
        await(() -> messageListLoaded() && MessageUnreadCounter.getCount() == 2);
        int initialOlderSessionPages = olderSessionPages.get();
        AlertDialog dialog = openMarkReadConfirmation();
        inst.runOnMainSync(() -> {
            SharedPreferencesUtil.getSharedPreferences().edit().putLong(SharedPreferencesUtil.mid, 43)
                    .putString(SharedPreferencesUtil.cookies, "DedeUserID=43; SESSDATA=other; bili_jct=other").apply();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        settleChecks();
        assertEquals(0, replyReads.get());
        assertEquals(0, acknowledgements.get());
        assertEquals(initialOlderSessionPages, olderSessionPages.get());
    }

    private AlertDialog openMarkReadConfirmation() throws Exception {
        inst.runOnMainSync(() -> messages.findViewById(R.id.mark_all_read).performClick());
        Field field = MessageActivity.class.getDeclaredField("markAllReadDialog");
        field.setAccessible(true);
        AlertDialog dialog = (AlertDialog) field.get(messages);
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }
    @Test public void disablingAndReenablingChecksHidesThenRefreshesTheIndicator()throws Exception {
        like=3;openMessages();await(()->MessageUnreadCounter.getCount()==3&&messageListLoaded()&&arrowUnread());
        inst.runOnMainSync(()->SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,false));
        await(()->MessageUnreadCounter.getCount()==0&&!arrowUnread());assertNull(MessageUnreadCounter.beginCheck());
        inst.runOnMainSync(()->SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,true));
        await(()->MessageUnreadCounter.getCount()==3&&arrowUnread());
    }
    @Test public void returningToTheMenuRefreshesMessagesThatArrivedAfterStartup()throws Exception {
        openMenu();settleChecks();assertEquals("消息",label());inst.runOnMainSync(menu::finish);inst.waitForIdleSync();
        like=2;SystemClock.sleep(5100);openMenu();await(()->label().contains("(2)"));
    }
    @Test public void arrowDotPreferencePersistsWithoutDisablingMessageCountsOrEntryHighlight()throws Exception {
        like=3;openMessages();await(()->messageListLoaded()&&MessageUnreadCounter.getCount()==3&&arrowUnread());
        openMenu();await(()->label().contains("(3)")&&menuArrowUnread());
        android.widget.CompoundButton option=openDotSetting();assertTrue(option.isChecked());
        inst.runOnMainSync(option::performClick);
        assertFalse(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_MENU_ARROW_DOT_ENABLE,true));
        assertTrue(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,false));
        assertEquals(3,MessageUnreadCounter.getCount());capture("arrow-dot-preference-off.png");
        inst.runOnMainSync(preferences::finish);inst.waitForIdleSync();
        await(()->!menuArrowUnread()&&label().contains("(3)"));assertNotNull(button().getIcon());assertTrue(button().getStrokeWidth()>0);
        capture("arrow-dot-off-message-still-highlighted.png");
        option=openDotSetting();assertFalse(option.isChecked());inst.runOnMainSync(option::performClick);
        inst.runOnMainSync(preferences::finish);inst.waitForIdleSync();await(()->menuArrowUnread()&&label().contains("(3)"));
        inst.runOnMainSync(menu::finish);inst.waitForIdleSync();await(this::arrowUnread);
        inst.runOnMainSync(()->SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.MESSAGE_MENU_ARROW_DOT_ENABLE,false));
        await(()->!arrowUnread());assertEquals(3,MessageUnreadCounter.getCount());
    }
    private boolean menuArrowUnread(){return TextViewCompat.getCompoundDrawablesRelative((TextView)menu.findViewById(R.id.pageName))[0] instanceof MessageBadgeStyle.UnreadArrow;}
    private android.widget.CompoundButton openDotSetting()throws Exception {
        preferences=(com.RobinNotBad.BiliClient.activity.settings.SettingPrefActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),
                com.RobinNotBad.BiliClient.activity.settings.SettingPrefActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final androidx.recyclerview.widget.RecyclerView list=preferences.findViewById(R.id.recyclerView);
        final int[] position={-1};
        inst.runOnMainSync(()->{
            try {
                Field f=com.RobinNotBad.BiliClient.adapter.SettingsAdapter.class.getDeclaredField("list");f.setAccessible(true);
                List<com.RobinNotBad.BiliClient.model.SettingSection> sections=(List<com.RobinNotBad.BiliClient.model.SettingSection>)f.get(list.getAdapter());
                for(int i=0;i<sections.size();i++)if(SharedPreferencesUtil.MESSAGE_MENU_ARROW_DOT_ENABLE.equals(sections.get(i).id))position[0]=i;
                assertTrue(position[0]>=0);list.scrollToPosition(position[0]);
            }catch(Exception e){throw new AssertionError(e);}
        });
        await(()->list.findViewHolderForAdapterPosition(position[0])!=null);
        return list.findViewHolderForAdapterPosition(position[0]).itemView.findViewById(R.id.setting_switch);
    }
    private void refreshTo(int expected)throws Exception {
        inst.runOnMainSync(()->MessageUnreadCounter.refresh(true));settleChecks();await(()->MessageUnreadCounter.getCount()==expected);
    }
    private void settleChecks()throws Exception {SystemClock.sleep(150);await(()->!MessageUnreadCounter.isChecking());SystemClock.sleep(150);await(()->!MessageUnreadCounter.isChecking());}
    private void openMenu() {
        menu=(MenuActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MenuActivity.class).putExtra("from","message").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync();
    }
    private void openMessages(){
        Instrumentation.ActivityMonitor monitor=inst.addMonitor(MessageActivity.class.getName(),null,false);
        try {
            inst.runOnMainSync(()->inst.getTargetContext().startActivity(new Intent(inst.getTargetContext(),MessageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            messages=(MessageActivity)monitor.waitForActivityWithTimeout(5000);assertNotNull(messages);
        }finally{inst.removeMonitor(monitor);}
    }
    private boolean arrowUnread(){return TextViewCompat.getCompoundDrawablesRelative((TextView)messages.findViewById(R.id.pageName))[0] instanceof MessageBadgeStyle.UnreadArrow;}
    private boolean messageListLoaded(){androidx.recyclerview.widget.RecyclerView list=messages.findViewById(R.id.sessions_list);return list!=null&&list.getAdapter()!=null;}
    private MaterialButton button() {
        try{Field f=MenuActivity.class.getDeclaredField("messageButton");f.setAccessible(true);MaterialButton b=(MaterialButton)f.get(menu);assertNotNull(b);return b;}
        catch(Exception e){throw new AssertionError(e);}
    }
    private String label(){return button().getText().toString();}
    private interface Check{boolean get();}
    private void await(Check check)throws Exception {
        long until=SystemClock.uptimeMillis()+6000;boolean[] ready={false};
        do{inst.runOnMainSync(()->ready[0]=check.get());if(ready[0])return;SystemClock.sleep(25);}while(SystemClock.uptimeMillis()<until);
        fail("Unread UI did not reach the expected state");
    }
    private void capture(String name)throws Exception {
        inst.waitForIdleSync();SystemClock.sleep(150);inst.waitForIdleSync();
        inst.runOnMainSync(()->{
            android.view.View root=(preferences!=null&&!preferences.isFinishing()?preferences:menu!=null?menu:messages).getWindow().getDecorView();
            Bitmap screen=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
            try(FileOutputStream out=new FileOutputStream(new File(inst.getTargetContext().getFilesDir(),name))){
                root.draw(new Canvas(screen));screen.compress(Bitmap.CompressFormat.PNG,100,out);
            }catch(Exception e){throw new AssertionError(e);}finally{screen.recycle();}
        });
    }
}
