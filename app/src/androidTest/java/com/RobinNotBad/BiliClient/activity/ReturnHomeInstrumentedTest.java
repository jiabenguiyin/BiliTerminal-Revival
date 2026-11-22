package com.RobinNotBad.BiliClient.activity;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicActivity;
import com.RobinNotBad.BiliClient.activity.player.PlayerActivity;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReturnHomeInstrumentedTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> saved = new HashMap<>();
    private final ArrayList<Activity> owners = new ArrayList<>();
    private AtomicReference<OkHttpClient> clients;
    private OkHttpClient oldClient;
    private DynamicActivity home;
    private File media;

    @Before public void setup() throws Exception {
        SharedPreferences prefs = SharedPreferencesUtil.getSharedPreferences();
        String tutorial = "tutorial_ver_" + inst.getTargetContext().getResources().getStringArray(R.array.tutorial_list)[6];
        for (String key : new String[]{"back_disable", SharedPreferencesUtil.DYNAMIC_UP_LIST_ENABLE,
                SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE, tutorial, "wbi_mixin_key", "last_wbi",
                SharedPreferencesUtil.MENU_SORT,
                "player_autolandscape", "player_subtitle_autoshow", "player_show_online", "player_audio_only", "player_from_last", "player_loop"})
            saved.put(key, prefs.getAll().get(key));
        prefs.edit().putBoolean("back_disable", false).putBoolean(SharedPreferencesUtil.DYNAMIC_UP_LIST_ENABLE,false)
                .putBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,false).putInt(tutorial,100)
                .putBoolean("player_autolandscape",false).putBoolean("player_subtitle_autoshow",false)
                .putBoolean("player_show_online",false).putBoolean("player_audio_only",false)
                .putBoolean("player_from_last",false).putBoolean("player_loop",false).commit();
        Field field=NetWorkUtil.class.getDeclaredField("INSTANCE"); field.setAccessible(true);
        clients=(AtomicReference<OkHttpClient>)field.get(null); oldClient=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> {
            String data = chain.request().url().encodedPath().endsWith("/nav")
                    ? "{\"wbi_img\":{\"img_url\":\"https://example.invalid/00000000000000000000000000000000.png\",\"sub_url\":\"https://example.invalid/11111111111111111111111111111111.png\"}}"
                    : "{\"items\":[],\"has_more\":false}";
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"),"{\"code\":0,\"data\":"+data+"}")).build();
        }).build());
        home=(DynamicActivity)inst.startActivitySync(new Intent(inst.getTargetContext(), DynamicActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        owners.add(home); inst.waitForIdleSync();
    }
    @After public void cleanup() {
        inst.runOnMainSync(() -> { for(int i=owners.size()-1;i>=0;i--) if(!owners.get(i).isFinishing()) owners.get(i).finish(); });
        inst.waitForIdleSync(); clients.set(oldClient);
        SharedPreferences.Editor editor=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> e:saved.entrySet()) {
            Object v=e.getValue(); String k=e.getKey();
            if(v==null)editor.remove(k); else if(v instanceof Boolean)editor.putBoolean(k,(Boolean)v);
            else if(v instanceof Integer)editor.putInt(k,(Integer)v); else if(v instanceof String)editor.putString(k,(String)v);
        }
        editor.commit(); if(media!=null)media.delete();
    }
    @Test public void longPressHeaderClearsMultipleDetailsAndReusesTheOriginalHome() throws Exception {
        ShowTextActivity first=detail(home), second=detail(first);
        inst.runOnMainSync(() -> assertTrue(second.findViewById(R.id.top).performLongClick()));
        await(() -> home.hasWindowFocus() && first.isFinishing() && second.isFinishing());
        assertSame(home, BiliTerminal.getInstanceActivityOnTop()); assertFalse(home.isFinishing());
    }
    @Test public void hardwareBackLongPressReturnsHomeWithoutASubsequentSingleBack() throws Exception {
        ShowTextActivity first=detail(home), second=detail(first);
        inst.runOnMainSync(() -> back(second,true));
        await(() -> home.hasWindowFocus() && first.isFinishing() && second.isFinishing());
        assertFalse(home.isFinishing());
    }
    @Test public void shortBackOnlyClosesTheCurrentDetail() throws Exception {
        ShowTextActivity first=detail(home), second=detail(first);
        inst.runOnMainSync(() -> back(second,false));
        await(() -> first.hasWindowFocus() && second.isFinishing());
        assertFalse(first.isFinishing()); assertFalse(home.isFinishing());
    }
    @Test public void disabledHardwareBackDoesNotReturnHome() throws Exception {
        ShowTextActivity first=detail(home);
        SharedPreferencesUtil.putBoolean("back_disable",true);
        inst.runOnMainSync(() -> back(first,true)); inst.waitForIdleSync();
        assertFalse(first.isFinishing()); assertTrue(first.hasWindowFocus());
    }
    @Test public void missingHomeReopensTheConfiguredHomeInsteadOfTheStartupFlow() throws Exception {
        ShowTextActivity detail=detail(home);
        SharedPreferencesUtil.putString(SharedPreferencesUtil.MENU_SORT,"dynamic;recommend");
        inst.runOnMainSync(home::finish);
        Instrumentation.ActivityMonitor monitor=inst.addMonitor(DynamicActivity.class.getName(),null,false);
        try {
            inst.runOnMainSync(() -> detail.findViewById(R.id.top).performLongClick());
            DynamicActivity replacement=(DynamicActivity)monitor.waitForActivityWithTimeout(7000);
            assertNotNull(replacement);owners.add(replacement);
            assertNotSame(home,replacement);
            await(() -> replacement.hasWindowFocus() && detail.isFinishing());
            assertSame(replacement,BiliTerminal.getInstanceActivityOnTop());
        } finally {inst.removeMonitor(monitor);}
    }
    @Test public void longBackFromPlayerReleasesItsDecoderAndClosesDetailStack() throws Exception {
        ShowTextActivity detail=detail(home);
        media=new File(home.getCacheDir(),"return-home-fixture.mp4");
        try(InputStream in=inst.getContext().getAssets().open("player-gesture.mp4");FileOutputStream out=new FileOutputStream(media)) {
            byte[] bytes=new byte[8192]; int n; while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
        }
        PlayerActivity player=launch(detail,PlayerActivity.class,new Intent(inst.getTargetContext(),PlayerActivity.class)
                .putExtra("url",media.getAbsolutePath()).putExtra("title","回主页测试"));
        Field prepared=PlayerActivity.class.getDeclaredField("isPrepared");prepared.setAccessible(true);
        await(() -> { try{return prepared.getBoolean(player);}catch(Exception e){throw new AssertionError(e);} });
        inst.runOnMainSync(() -> player.findViewById(R.id.top).performLongClick());
        await(() -> home.hasWindowFocus() && player.isFinishing() && detail.isFinishing());
        assertTrue(player.getIntent().getBooleanExtra(BiliTerminal.RETURNING_HOME,false));
        assertTrue(detail.getIntent().getBooleanExtra(BiliTerminal.RETURNING_HOME,false));
        Field destroyed=PlayerActivity.class.getDeclaredField("destroyed");destroyed.setAccessible(true);
        await(() -> {try{return destroyed.getBoolean(player);}catch(Exception e){throw new AssertionError(e);} });
        Field decoder=PlayerActivity.class.getDeclaredField("ijkPlayer");decoder.setAccessible(true);
        assertNull(decoder.get(player)); assertFalse(home.isFinishing());
    }
    private ShowTextActivity detail(Activity parent)throws Exception {
        return launch(parent,ShowTextActivity.class,new Intent(inst.getTargetContext(),ShowTextActivity.class)
                .putExtra("title","详情").putExtra("content","本地导航测试"));
    }
    private <T extends Activity>T launch(Activity parent,Class<T> type,Intent intent)throws Exception {
        Instrumentation.ActivityMonitor monitor=inst.addMonitor(type.getName(),null,false);
        try {inst.runOnMainSync(() -> parent.startActivity(intent)); T result=type.cast(monitor.waitForActivityWithTimeout(7000));
            assertNotNull(result);owners.add(result);inst.waitForIdleSync();return result;
        }finally{inst.removeMonitor(monitor);}
    }
    private void back(Activity owner,boolean longPress) {
        long now=SystemClock.uptimeMillis();
        owner.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0));
        if(longPress)owner.dispatchKeyEvent(new KeyEvent(now,now+600,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,1,0,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_LONG_PRESS));
        owner.dispatchKeyEvent(new KeyEvent(now,now+700,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0));
    }
    private interface Check{boolean get();}
    private void await(Check check)throws Exception {long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){inst.waitForIdleSync();if(check.get())return;SystemClock.sleep(40);}assertTrue("Timed out",check.get());}
}
