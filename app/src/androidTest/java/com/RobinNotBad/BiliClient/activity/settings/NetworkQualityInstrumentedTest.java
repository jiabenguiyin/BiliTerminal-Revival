package com.RobinNotBad.BiliClient.activity.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter;
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NetworkQualityInstrumentedTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> saved=new HashMap<>();
    private SettingPlayerChooseActivity parent;private SettingQualityActivity chooser;private SettingTerminalPlayerActivity terminal;
    private AtomicReference<OkHttpClient> clients;private OkHttpClient original;
    @Before public void setup()throws Exception {
        SharedPreferences prefs=inst.getTargetContext().getSharedPreferences("default",0);SharedPreferencesUtil.sharedPreferences=prefs;
        for(String key:new String[]{"mid","play_qn","play_qn_mobile","play_qn_wifi","vip_60fps_enabled","vip_60fps_mid","vip_60fps_checked",
                PlayerSettingsUtil.LONGPRESS_HINT_ENABLED,PlayerSettingsUtil.LONGPRESS_HINT_SCALE})saved.put(key,prefs.getAll().get(key));
        prefs.edit().putLong("mid",0).putInt("play_qn",32).remove("play_qn_mobile").remove("play_qn_wifi")
                .putBoolean(PlayerSettingsUtil.LONGPRESS_HINT_ENABLED,true).commit();
        Field field=NetWorkUtil.class.getDeclaredField("INSTANCE");field.setAccessible(true);clients=(AtomicReference<OkHttpClient>)field.get(null);original=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            JSONObject data=new JSONObject();
            try {
                if(chain.request().url().encodedPath().endsWith("/playurl")) {
                    int qn=16;try{qn=Integer.parseInt(chain.request().url().queryParameter("qn"));}catch(Exception ignored){}
                    if(qn<=0)qn=80;
                    data.put("quality",qn).put("timelength",60000).put("accept_quality",new JSONArray().put(80).put(64).put(32).put(16))
                            .put("accept_description",new JSONArray().put("1080P").put("720P").put("480P").put("360P"))
                            .put("durl",new JSONArray().put(new JSONObject().put("url","https://fixture.invalid/"+qn+".mp4").put("length",60000)));
                }
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"),new JSONObject().put("code",0).put("data",data).toString())).build();
            }catch(JSONException e){throw new IOException(e);}
        }).build());
    }
    @After public void cleanup() {
        inst.runOnMainSync(()->{if(chooser!=null)chooser.finish();if(parent!=null)parent.finish();if(terminal!=null)terminal.finish();});
        inst.waitForIdleSync();clients.set(original);
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> entry:saved.entrySet()) {
            String k=entry.getKey();Object v=entry.getValue();if(v==null)edit.remove(k);else if(v instanceof Integer)edit.putInt(k,(Integer)v);
            else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);
        }
        edit.commit();
    }
    @Test public void wifiMobileAndGlobalRowsSaveIndependentlyAndRefreshOnReturn()throws Exception {
        openParent();openFromCard(R.id.qn_mobile_choose);chooseValue(64);
        assertEquals("Mobile selection",64,SharedPreferencesUtil.getInt("play_qn_mobile",-1));
        openFromCard(R.id.qn_wifi_choose);chooseValue(80);
        assertEquals(32,SharedPreferencesUtil.getInt("play_qn",0));assertEquals(64,SharedPreferencesUtil.getInt("play_qn_mobile",-1));assertEquals(80,SharedPreferencesUtil.getInt("play_qn_wifi",-1));
        await(()->((TextView)parent.findViewById(R.id.qn_mobile_tv)).getText().toString().contains("720"));
        assertTrue(((TextView)parent.findViewById(R.id.qn_wifi_tv)).getText().toString().contains("1080"));
        inst.runOnMainSync(()->{ViewGroup scroll=parent.findViewById(R.id.scrollView);scroll.scrollTo(0,parent.findViewById(R.id.qn_choose).getTop());});
        inst.waitForIdleSync();SystemClock.sleep(120);capture(parent,"network-quality-settings.png");
    }
    @Test public void inheritedProfileIsAnExplicitChoiceAndDoesNotRewriteGlobal()throws Exception {
        SharedPreferencesUtil.putInt("play_qn",64);SharedPreferencesUtil.putInt("play_qn_mobile",16);openParent();
        openFromCard(R.id.qn_mobile_choose);chooseValue(-1);
        assertEquals(-1,SharedPreferencesUtil.getInt("play_qn_mobile",0));assertEquals(64,SharedPreferencesUtil.getInt("play_qn",0));
        assertEquals(64,NetworkPlaybackQuality.defaultQuality(inst.getTargetContext()));
    }
    @Test public void unspecifiedVideoQualityResolvesOnlyWhenPlaybackStarts()throws Exception {
        VideoInfo info=new VideoInfo();info.aid=123;info.title="Fixture";info.cids=new ArrayList<>(Arrays.asList(456L));info.pagenames=new ArrayList<>(Arrays.asList("P1"));
        PlayerData data=info.toPlayerData(0);assertEquals(-1,data.qn);
        SharedPreferencesUtil.putInt("play_qn",80);SharedPreferencesUtil.putInt("play_qn_mobile",16);SharedPreferencesUtil.putInt("play_qn_wifi",16);
        PlayerApi.getVideoForPlayback(data);assertEquals(16,data.qn);assertTrue(data.videoUrl.endsWith("/16.mp4"));
    }
    @Test public void explicitPlaybackAndDownloadChoicesWinOverNetworkDefaults()throws Exception {
        SharedPreferencesUtil.putInt("play_qn_mobile",16);SharedPreferencesUtil.putInt("play_qn_wifi",16);
        PlayerData playback=new PlayerData();playback.aid=123;playback.cid=456;playback.qn=80;PlayerApi.getVideoForPlayback(playback);assertEquals(80,playback.qn);
        PlayerData download=new PlayerData();download.aid=123;download.cid=456;download.qn=64;PlayerApi.getVideo(download,true);assertEquals(64,download.qn);
    }
    @Test public void actualActiveNetworkUsesItsOwnConfiguredProfile() {
        SharedPreferencesUtil.putInt("play_qn",32);SharedPreferencesUtil.putInt("play_qn_mobile",16);SharedPreferencesUtil.putInt("play_qn_wifi",80);
        int network=NetworkPlaybackQuality.network(inst.getTargetContext());
        String required=InstrumentationRegistry.getArguments().getString("expected_network");
        if(required!=null)assertEquals(Integer.parseInt(required),network);
        assertTrue("Test emulator must have an active mobile or Wi-Fi route",network==1||network==2);
        assertEquals(network==1?16:80,NetworkPlaybackQuality.defaultQuality(inst.getTargetContext()));
    }
    @Test public void hintToggleAndScaleAreIndependentActualSettingsRows()throws Exception {
        terminal=(SettingTerminalPlayerActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),SettingTerminalPlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->terminal.findViewById(R.id.recyclerView)!=null&&((RecyclerView)terminal.findViewById(R.id.recyclerView)).getAdapter()!=null);
        inst.runOnMainSync(()->{
            RecyclerView list=terminal.findViewById(R.id.recyclerView);SettingsAdapter adapter=(SettingsAdapter)list.getAdapter();
            try {
                Field f=SettingsAdapter.class.getDeclaredField("list");f.setAccessible(true);List<SettingSection> sections=(List<SettingSection>)f.get(adapter);
                for(String key:new String[]{PlayerSettingsUtil.LONGPRESS_HINT_ENABLED,PlayerSettingsUtil.LONGPRESS_HINT_SCALE}) {
                    int pos=-1;for(int i=0;i<sections.size();i++)if(key.equals(sections.get(i).id)){pos=i;break;}assertTrue(pos>=0);
                    RecyclerView.ViewHolder holder=adapter.createViewHolder(new FrameLayout(terminal),adapter.getItemViewType(pos));adapter.bindViewHolder(holder,pos);
                    if(key.equals(PlayerSettingsUtil.LONGPRESS_HINT_ENABLED))((android.widget.CompoundButton)holder.itemView.findViewById(R.id.setting_switch)).setChecked(false);
                    else ((EditText)holder.itemView.findViewById(R.id.setting_input_edittext)).setText("0.5");
                }
            }catch(Exception e){throw new AssertionError(e);}
        });
        assertFalse(SharedPreferencesUtil.getBoolean(PlayerSettingsUtil.LONGPRESS_HINT_ENABLED,true));assertEquals(.5f,PlayerSettingsUtil.getLongPressHintScale(),0);
        assertEquals(32,SharedPreferencesUtil.getInt("play_qn",0));
    }
    @Test public void arbitraryPreferenceKeysAreNotAcceptedByTheChooser()throws Exception {
        Object before=SharedPreferencesUtil.getSharedPreferences().getAll().get("csrf");openChooser("csrf");chooseValue(16);
        assertEquals(before,SharedPreferencesUtil.getSharedPreferences().getAll().get("csrf"));assertEquals(16,SharedPreferencesUtil.getInt("play_qn",0));
    }
    @Test public void expiredVipNormalizationOnlyUpdatesTheSelectedProfile()throws Exception {
        SharedPreferencesUtil.putLong("mid",42);SharedPreferencesUtil.putLong("vip_60fps_mid",42);SharedPreferencesUtil.putBoolean("vip_60fps_enabled",false);
        SharedPreferencesUtil.putLong("vip_60fps_checked",System.currentTimeMillis());SharedPreferencesUtil.putInt("play_qn_mobile",120);SharedPreferencesUtil.putInt("play_qn_wifi",80);
        openChooser(NetworkPlaybackQuality.MOBILE);await(()->SharedPreferencesUtil.getInt("play_qn_mobile",-1)==0);
        assertEquals(80,SharedPreferencesUtil.getInt("play_qn_wifi",-1));assertEquals(32,SharedPreferencesUtil.getInt("play_qn",0));
    }
    private void openParent()throws Exception {
        parent=(SettingPlayerChooseActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),SettingPlayerChooseActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->parent.findViewById(R.id.qn_wifi_choose)!=null);
    }
    private void openChooser(String key)throws Exception {
        chooser=(SettingQualityActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),SettingQualityActivity.class)
                .putExtra(NetworkPlaybackQuality.EXTRA_KEY,key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->((RecyclerView)chooser.findViewById(R.id.recyclerView)).getAdapter()!=null);
    }
    private void openFromCard(int id)throws Exception {
        String expected=id==R.id.qn_mobile_choose?NetworkPlaybackQuality.MOBILE:NetworkPlaybackQuality.WIFI;
        inst.runOnMainSync(()->parent.findViewById(id).performClick());
        await(()->{
            for(android.app.Activity active:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                if(active instanceof SettingQualityActivity&&!active.isFinishing()
                        &&expected.equals(active.getIntent().getStringExtra(NetworkPlaybackQuality.EXTRA_KEY))) {
                    chooser=(SettingQualityActivity)active;return true;
                }
            }return false;
        });
        assertEquals(expected,chooser.getIntent().getStringExtra(NetworkPlaybackQuality.EXTRA_KEY));
        Field field=SettingQualityActivity.class.getDeclaredField("preferenceKey");field.setAccessible(true);assertEquals(expected,field.get(chooser));
    }
    private void chooseValue(int value)throws Exception {
        inst.runOnMainSync(()->{
            RecyclerView list=chooser.findViewById(R.id.recyclerView);QualityChooseAdapter adapter=(QualityChooseAdapter)list.getAdapter();
            int index=-1;for(int i=0;i<adapter.getItemCount();i++) {
                String name=adapter.getName(i);if(value==-1?name.equals(chooser.getString(R.string.quality_inherit)):
                        value==64?name.contains("720P"):value==80?name.contains("1080P"):name.contains("360P")){index=i;break;}
            }assertTrue(index>=0);RecyclerView.ViewHolder holder=adapter.onCreateViewHolder(list,adapter.getItemViewType(index));adapter.onBindViewHolder((QualityChooseAdapter.Holder)holder,index);holder.itemView.performClick();
        });inst.waitForIdleSync();
        SettingQualityActivity finished=chooser;await(finished::isDestroyed);chooser=null;
    }
    private interface Check{boolean get();}
    private void await(Check check)throws Exception {long end=SystemClock.uptimeMillis()+6000;boolean[] ready={false};do{inst.runOnMainSync(()->ready[0]=check.get());if(ready[0])return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<end);fail("Settings UI did not settle");}
    private void capture(android.app.Activity owner,String name) {
        inst.runOnMainSync(()->{View root=owner.findViewById(android.R.id.content);Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
            try(FileOutputStream out=new FileOutputStream(new File(owner.getFilesDir(),name))){root.draw(new Canvas(image));image.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(Exception e){throw new AssertionError(e);}finally{image.recycle();}});
    }
}
