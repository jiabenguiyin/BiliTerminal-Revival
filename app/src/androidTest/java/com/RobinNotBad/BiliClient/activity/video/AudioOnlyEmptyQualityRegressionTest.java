package com.RobinNotBad.BiliClient.activity.video;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.Stats;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import static org.junit.Assert.*;

/** Real chooser + request parsing; every HTTP request is intercepted, no downloads are enqueued. */
@RunWith(AndroidJUnit4.class)
public class AudioOnlyEmptyQualityRegressionTest {
    @Test public void emptyVideoQualitiesMustNotPreventTheAudioRequest()throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        Field field=NetWorkUtil.class.getDeclaredField("INSTANCE");field.setAccessible(true);
        AtomicReference<OkHttpClient> clients=(AtomicReference<OkHttpClient>)field.get(null);
        OkHttpClient previous=clients.get();
        Object oldKey=SharedPreferencesUtil.getSharedPreferences().getAll().get("wbi_mixin_key");
        Object oldDate=SharedPreferencesUtil.getSharedPreferences().getAll().get("last_wbi");
        AtomicInteger audioRequests=new AtomicInteger();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            JSONObject data=new JSONObject();
            try {
                if(chain.request().url().encodedPath().endsWith("/nav")) {
                    data.put("wbi_img",new JSONObject().put("img_url","https://example.invalid/0123456789abcdef0123456789abcdef.png")
                            .put("sub_url","https://example.invalid/fedcba9876543210fedcba9876543210.png"));
                } else if(chain.request().url().encodedPath().endsWith("/playurl")) {
                    if("16".equals(chain.request().url().queryParameter("fnval"))) {
                        audioRequests.incrementAndGet();
                        data.put("dash",new JSONObject().put("video",new JSONArray()).put("audio",new JSONArray()));
                    } else {
                        data.put("quality",0).put("accept_quality",new JSONArray())
                                .put("durl",new JSONArray().put(new JSONObject().put("url","https://example.invalid/video.mp4")));
                    }
                }
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"),new JSONObject().put("code",0).put("data",data).toString())).build();
            }catch(Exception error){throw new java.io.IOException(error);}
        }).build());
        VideoInfo info=new VideoInfo();info.aid=Long.MAX_VALUE-104;info.title="空画质音频缓存测试";
        info.stats=new Stats();info.cids.add(104L);info.pagenames.add(info.title);
        TerminalContext.getInstance().putContent(info);
        QualityChooserActivity activity=null;
        try {
            activity=(QualityChooserActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),QualityChooserActivity.class)
                    .putExtra("aid",info.aid).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            QualityChooserActivity chooser=activity;AtomicInteger rows=new AtomicInteger();
            long until=SystemClock.uptimeMillis()+10000;
            while(SystemClock.uptimeMillis()<until) {
                inst.runOnMainSync(()->{RecyclerView list=chooser.findViewById(R.id.recyclerView);rows.set(list.getAdapter()==null?0:list.getAdapter().getItemCount());});
                if(rows.get()==1)break;SystemClock.sleep(40);
            }
            assertEquals(1,rows.get());
            inst.runOnMainSync(()->assertEquals(0,chooser.qns.length));
            until=SystemClock.uptimeMillis()+5000;AtomicReference<RecyclerView.ViewHolder> item=new AtomicReference<>();
            while(SystemClock.uptimeMillis()<until) {
                inst.runOnMainSync(()->item.set(((RecyclerView)chooser.findViewById(R.id.recyclerView)).findViewHolderForAdapterPosition(0)));
                if(item.get()!=null)break;SystemClock.sleep(30);
            }
            assertNotNull(item.get());inst.runOnMainSync(()->item.get().itemView.performClick());
            until=SystemClock.uptimeMillis()+5000;
            while(audioRequests.get()==0&&SystemClock.uptimeMillis()<until)SystemClock.sleep(40);
            assertTrue("Empty video quality list blocked the independent DASH audio request",audioRequests.get()>0);
        } finally {
            if(activity!=null){QualityChooserActivity ending=activity;inst.runOnMainSync(ending::finish);inst.waitForIdleSync();}
            clients.set(previous);
            android.content.SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
            if(oldKey==null)edit.remove("wbi_mixin_key");else edit.putString("wbi_mixin_key",(String)oldKey);
            if(oldDate==null)edit.remove("last_wbi");else edit.putInt("last_wbi",(Integer)oldDate);edit.commit();
            Field cache=TerminalContext.class.getDeclaredField("contentLruCache");cache.setAccessible(true);Object value=cache.get(TerminalContext.getInstance());
            value.getClass().getMethod("remove",Object.class).invoke(value,TerminalContext.getInstance().getTerminalKey(info));
        }
    }
}
