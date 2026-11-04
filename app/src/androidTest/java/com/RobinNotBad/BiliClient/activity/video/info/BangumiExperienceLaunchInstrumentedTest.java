package com.RobinNotBad.BiliClient.activity.video.info;
import android.app.*;
import android.content.Intent;
import android.os.SystemClock;
import android.view.*;
import android.graphics.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.UIPreviewActivity;
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity;
import com.RobinNotBad.BiliClient.adapter.video.MediaEpisodeAdapter;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.player.PlayerOptionsMenu;
import com.RobinNotBad.BiliClient.util.*;
import okhttp3.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BangumiExperienceLaunchInstrumentedTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private UIPreviewActivity host;private Activity jump;private BangumiInfoFragment fragment;
    private AtomicReference<OkHttpClient> clients;private OkHttpClient previous;
    private final Map<String,Object> old=new HashMap<>();
    @Before public void before()throws Exception{
        for(String key:new String[]{SharedPreferencesUtil.mid,"pgc_recent_42_7","pgc_recent_42_8",PageMotion.DISABLED})old.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,42);SharedPreferencesUtil.putBoolean(PageMotion.DISABLED,false);
        SharedPreferencesUtil.removeValue("pgc_recent_42_8");BangumiProgressStore.save(42,7,702,1702,12000);
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);previous=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{try{
            HttpUrl u=chain.request().url();String path=u.encodedPath();JSONObject result=new JSONObject();int code=0;
            long season=u.queryParameter("season_id")==null?7:Long.parseLong(u.queryParameter("season_id"));
            if(path.endsWith("/review/user"))result.put("media",new JSONObject().put("media_id",99).put("season_id",7).put("title","第一季")
                    .put("cover","").put("horizontal_picture","").put("type",1).put("type_name","番剧").put("areas",new JSONArray()));
            else if(path.endsWith("/view/web/season"))result.put("season_id",season).put("media_id",99).put("title",season==7?"第一季":"第二季").put("cover","")
                    .put("seasons",new JSONArray().put(new JSONObject().put("season_id",7).put("season_title","第一季"))
                            .put(new JSONObject().put("season_id",8).put("season_title","第二季")));
            else if(path.endsWith("/season/section")){
                JSONArray episodes=new JSONArray();for(int n=1;n<=2;n++)episodes.put(new JSONObject().put("id",season*100+n).put("aid",7000)
                        .put("cid",1000+season*100+n).put("title",String.valueOf(n)).put("long_title","测试集").put("cover","").put("badge",""));
                result.put("main_section",new JSONObject().put("id",1).put("title","正片").put("type",0).put("episodes",episodes));
            }else code=-404;
            String body=new JSONObject().put("code",code).put("result",result).toString();
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"),body)).build();
        }catch(JSONException e){throw new IOException(e);}}).build());
        host=(UIPreviewActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),UIPreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(()->{host.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            fragment=BangumiInfoFragment.newInstance(99);host.getSupportFragmentManager().beginTransaction().replace(android.R.id.content,fragment).commitNow();});
        await(()->get("bangumi")!=null&&fragment.getView().getVisibility()==View.VISIBLE);
    }
    @After public void after(){main(()->{if(jump!=null)jump.finish();if(host!=null)host.finish();});inst.waitForIdleSync();clients.set(previous);
        for(Map.Entry<String,Object> e:old.entrySet()){
            if(e.getValue()==null)SharedPreferencesUtil.removeValue(e.getKey());else if(e.getValue() instanceof Long)SharedPreferencesUtil.putLong(e.getKey(),(Long)e.getValue());
            else if(e.getValue() instanceof Boolean)SharedPreferencesUtil.putBoolean(e.getKey(),(Boolean)e.getValue());else SharedPreferencesUtil.putString(e.getKey(),(String)e.getValue());
        }}
    @Test public void reenteringSeasonHighlightsLastEpisodeAndOffersContinue(){
        assertEquals(1,get("selectedEpisode"));RecyclerView list=fragment.getView().findViewById(R.id.rv_episode_list);
        assertEquals(1,((MediaEpisodeAdapter)list.getAdapter()).selectedItemIndex);
        assertEquals("继续播放：2",((android.widget.Button)fragment.getView().findViewById(R.id.btn_play)).getText());
    }
    @Test public void seasonChoiceUsesSeasonIdAndDoesNotRequireMediaId()throws Exception{
        main(()->invoke("showSeasonChoices"));PlayerOptionsMenu menu=(PlayerOptionsMenu)get("seasonsMenu");
        await(()->menu.getDialog().getWindow().getDecorView().getWidth()>0);
        main(()->{View decor=menu.getDialog().getWindow().getDecorView();assertTrue(decor.getWidth()<=host.findViewById(android.R.id.content).getWidth());
            Bitmap image=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(image));
            try(FileOutputStream out=new FileOutputStream(new File(host.getFilesDir(),"experience-seasons.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(IOException e){throw new AssertionError(e);}finally{image.recycle();}
            assertTrue(menu.getMenu().findItem(1).isChecked());menu.getMenu().performIdentifierAction(2,0);});
        await(()->((Bangumi)get("bangumi")).info.season_id==8);
        assertEquals(0,get("selectedEpisode"));assertEquals(801,((Bangumi)get("bangumi")).sectionList.get(0).episodeList.get(0).id);
    }
    @Test public void selectedEpisodeIdentityReachesPlaybackTransition()throws Exception{
        Instrumentation.ActivityMonitor monitor=inst.addMonitor(JumpToPlayerActivity.class.getName(),null,false);
        try{main(()->fragment.getView().findViewById(R.id.btn_play).performClick());jump=inst.waitForMonitorWithTimeout(monitor,8000);assertNotNull(jump);
            PlayerData p=jump.getIntent().getParcelableExtra("data");assertEquals(7,p.seasonId);assertEquals(702,p.episodeId);assertEquals(1702,p.cid);
            assertEquals(PlayerData.TYPE_BANGUMI,p.type);
        }finally{inst.removeMonitor(monitor);}
    }
    @Test public void baseActivityPageMotionCanBeEnabledAndRestored(){
        final int[] original={0};main(()->original[0]=host.getWindow().getAttributes().windowAnimations);
        SharedPreferencesUtil.putBoolean(PageMotion.DISABLED,true);main(()->PageMotion.apply(host));assertEquals(0,host.getWindow().getAttributes().windowAnimations);
        SharedPreferencesUtil.putBoolean(PageMotion.DISABLED,false);main(()->PageMotion.apply(host));assertEquals(original[0],host.getWindow().getAttributes().windowAnimations);
    }
    private interface Check{boolean ok();}
    private void await(Check c)throws Exception{long end=SystemClock.uptimeMillis()+14000;boolean[] ok={false};do{main(()->ok[0]=c.ok());if(ok[0])return;SystemClock.sleep(50);}while(SystemClock.uptimeMillis()<end);throw new AssertionError("Timed out");}
    private Object get(String n){try{Field f=BangumiInfoFragment.class.getDeclaredField(n);f.setAccessible(true);return f.get(fragment);}catch(Exception e){throw new AssertionError(e);}}
    private void invoke(String n){try{Method m=BangumiInfoFragment.class.getDeclaredMethod(n);m.setAccessible(true);m.invoke(fragment);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable r){inst.runOnMainSync(r);}
}
