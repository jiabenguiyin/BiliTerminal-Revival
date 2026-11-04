package com.RobinNotBad.BiliClient.util;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.MenuActivity;
import com.RobinNotBad.BiliClient.activity.message.MessageActivity;
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
    private volatile int at,reply,like,system,privateUnread;
    private volatile boolean feedFailure,privateFailure,extras;
    private AtomicReference<OkHttpClient> clients;private OkHttpClient original;
    private MenuActivity menu;private MessageActivity messages;private NoticeActivity notice;
    private com.RobinNotBad.BiliClient.activity.settings.SettingPrefActivity preferences;

    @Before public void setup()throws Exception {
        SharedPreferences prefs=inst.getTargetContext().getSharedPreferences("default",0);SharedPreferencesUtil.sharedPreferences=prefs;
        List<String> keys=new ArrayList<>(Arrays.asList(SharedPreferencesUtil.mid,SharedPreferencesUtil.cookies,SharedPreferencesUtil.csrf,
                SharedPreferencesUtil.MESSAGE_UPDATE_NUM,SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,SharedPreferencesUtil.MESSAGE_MENU_ARROW_DOT_ENABLE,SharedPreferencesUtil.MENU_SORT,"menu_message","dpi","density","player_ui_round"));
        for(String tag:inst.getTargetContext().getResources().getStringArray(R.array.tutorial_list))keys.add("tutorial_ver_"+tag);
        for(String key:keys)saved.put(key,prefs.getAll().get(key));
        SharedPreferences.Editor edit=prefs.edit().putLong(SharedPreferencesUtil.mid,42)
                .putString(SharedPreferencesUtil.cookies,"DedeUserID=42; bili_jct=fixture").putString(SharedPreferencesUtil.csrf,"fixture")
                .putBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,true).putBoolean("menu_message",true)
                .remove(SharedPreferencesUtil.MESSAGE_MENU_ARROW_DOT_ENABLE)
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
                } else if(path.endsWith("/get_sessions"))data.put("session_list",new JSONArray());
                else if(path.endsWith("/msgfeed/reply")) {
                    replyReads.incrementAndGet();reply=0;
                    data.put("items",new JSONArray()).put("cursor",new JSONObject().put("is_end",true));
                }else if(path.endsWith("/update_ack"))acknowledgements.incrementAndGet();
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"),new JSONObject().put("code",code).put("data",data).toString())).build();
            }catch(JSONException e){throw new java.io.IOException(e);}
        }).build());
    }
    @After public void cleanup()throws Exception {
        inst.runOnMainSync(()->{if(preferences!=null)preferences.finish();if(notice!=null)notice.finish();if(menu!=null)menu.finish();if(messages!=null)messages.finish();});inst.waitForIdleSync();
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
    @Test public void disablingAndReenablingChecksHidesThenRefreshesTheIndicator()throws Exception {
        like=3;openMessages();await(()->MessageUnreadCounter.getCount()==3&&messageListLoaded());assertTrue(arrowUnread());
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
