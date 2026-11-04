package com.RobinNotBad.BiliClient.api;
import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import okhttp3.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BangumiExperienceInstrumentedTest {
    private AtomicReference<OkHttpClient> clients;private OkHttpClient previous;
    private final Map<String,Object> old=new HashMap<>();private final List<String> requests=new ArrayList<>();
    private int v2Code;private boolean emptyV2,emptyV1;
    private static final long SEASON=990007,EP=990701,CID=990801;
    @Before public void before()throws Exception{
        for(String key:new String[]{SharedPreferencesUtil.mid,"player_from_last","pgc_recent_42_"+SEASON,"pgc_recent_43_"+SEASON})old.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,42);SharedPreferencesUtil.putBoolean("player_from_last",true);
        SharedPreferencesUtil.removeValue("pgc_recent_42_"+SEASON);SharedPreferencesUtil.removeValue("pgc_recent_43_"+SEASON);
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);previous=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{try{
            String path=chain.request().url().encodedPath();requests.add(path);
            boolean v2=path.contains("/v2/");JSONArray durl=new JSONArray();
            if(!(v2?emptyV2:emptyV1))durl.put(new JSONObject().put("url","https://example.invalid/fixture.mp4"));
            JSONObject data=new JSONObject().put("durl",durl).put("quality",32).put("accept_quality",new JSONArray().put(32))
                    .put("clip_info_list",new JSONArray().put(new JSONObject().put("clipType","CLIP_TYPE_OP").put("start",1).put("end",20)));
            JSONObject body=new JSONObject().put("code",v2?v2Code:0).put("result",v2?new JSONObject().put("video_info",data):data);
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"),body.toString())).build();
        }catch(JSONException e){throw new IOException(e);}}).build());
    }
    @After public void after(){clients.set(previous);for(Map.Entry<String,Object> e:old.entrySet()){
        if(e.getValue()==null)SharedPreferencesUtil.removeValue(e.getKey());else if(e.getValue() instanceof Long)SharedPreferencesUtil.putLong(e.getKey(),(Long)e.getValue());
        else if(e.getValue() instanceof Boolean)SharedPreferencesUtil.putBoolean(e.getKey(),(Boolean)e.getValue());else SharedPreferencesUtil.putString(e.getKey(),(String)e.getValue());}}
    private PlayerData data(){PlayerData p=new PlayerData(PlayerData.TYPE_BANGUMI);p.aid=123;p.cid=CID;p.episodeId=EP;p.seasonId=SEASON;p.qn=32;return p;}
    @Test public void v2ReturnsSegmentsInTheStreamResponseWithoutAnExtraRequest()throws Exception{
        PlayerData p=data();PlayerApi.getBangumi(p);assertEquals(1,requests.size());assertTrue(requests.get(0).endsWith("/v2/playurl"));
        assertTrue(p.clipSegments.contains("CLIP_TYPE_OP"));assertEquals("https://example.invalid/fixture.mp4",p.videoUrl);
    }
    @Test public void missingV2StreamFallsBackToExistingLegacyShape()throws Exception{
        emptyV2=true;PlayerData p=data();PlayerApi.getBangumi(p);assertEquals(2,requests.size());assertTrue(p.clipSegments.contains("CLIP_TYPE_OP"));
    }
    @Test public void authenticationErrorIsNotHiddenByLegacyFallback()throws Exception{
        v2Code=-101;try{PlayerApi.getBangumi(data());fail();}catch(JSONException e){assertTrue(e.getMessage().contains("-101"));}
        assertEquals(1,requests.size());
    }
    @Test public void emptyStreamsFailCleanlyInsteadOfIndexingAnEmptyArray()throws Exception{
        emptyV2=emptyV1=true;try{PlayerApi.getBangumi(data());fail();}catch(JSONException expected){assertNotNull(expected.getMessage());}
        assertEquals(2,requests.size());
    }
    @Test public void historySelectionAndPositionCannotCrossAccountsOrEpisodes()throws Exception{
        BangumiProgressStore.save(42,SEASON,EP,CID,17000);assertEquals(EP,BangumiProgressStore.episode(SEASON));
        assertEquals(17000,BangumiProgressStore.position(SEASON,EP,CID));assertEquals(0,BangumiProgressStore.position(SEASON,EP+1,CID+1));
        PlayerData p=data();PlayerApi.getBangumi(p);assertEquals(17000,p.progress);
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,43);assertEquals(0,BangumiProgressStore.episode(SEASON));
        BangumiProgressStore.save(42,SEASON,EP,CID,99999);assertEquals(0,BangumiProgressStore.episode(SEASON));
        SharedPreferencesUtil.putLong(SharedPreferencesUtil.mid,42);assertEquals(17000,BangumiProgressStore.position(SEASON,EP,CID));
    }
    @Test public void parcelAndBuiltInIntentKeepEpisodeIdentityAndClipData()throws Exception{
        PlayerData p=data();PlayerApi.getBangumi(p);Parcel parcel=Parcel.obtain();
        try{p.writeToParcel(parcel,0);parcel.setDataPosition(0);PlayerData copy=PlayerData.CREATOR.createFromParcel(parcel);
            assertEquals(SEASON,copy.seasonId);assertEquals(EP,copy.episodeId);assertEquals(p.clipSegments,copy.clipSegments);
            old.put("player",SharedPreferencesUtil.getSharedPreferences().getAll().get("player"));SharedPreferencesUtil.putString("player","terminalPlayer");
            android.content.Intent intent=PlayerApi.jumpToPlayer(copy);assertEquals(EP,intent.getLongExtra("pgc_episode",0));
            assertEquals(PlayerData.TYPE_BANGUMI,intent.getIntExtra("content_type",-1));assertEquals(copy.clipSegments,intent.getStringExtra("clip_segments"));
        }finally{parcel.recycle();}
    }
}
