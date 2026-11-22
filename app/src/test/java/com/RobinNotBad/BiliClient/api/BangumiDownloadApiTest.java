package com.RobinNotBad.BiliClient.api;
import com.RobinNotBad.BiliClient.model.PlayerData;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class BangumiDownloadApiTest {
    private JSONObject body()throws Exception{return new JSONObject("{\"code\":0,\"result\":{\"video_info\":{\"quality\":32,\"durl\":[{\"url\":\"https://fixture.invalid/episode.mp4\"}],\"accept_quality\":[32,16],\"accept_description\":[\"480P\",\"360P\"]}}}");}
    @Test public void acceptsACompleteUnencryptedSingleFileAndItsActualQualities()throws Exception {
        PlayerData p=new PlayerData();p.cid=9876543210L;BangumiDownloadApi.apply(p,body());
        assertEquals("https://fixture.invalid/episode.mp4",p.videoUrl);assertEquals(32,p.qn);assertEquals(2,p.qnValueList.length);
        JSONObject legacy=body();legacy.put("result",legacy.getJSONObject("result").getJSONObject("video_info"));
        BangumiDownloadApi.apply(p,legacy);assertEquals(32,p.qn);
    }
    @Test public void rejectsPreviewDrmAndMultiFileResponsesAndClearsOldUrls()throws Exception {
        for(String flag:new String[]{"is_preview","is_drm","drm_tech_type"}){
            JSONObject body=body();body.getJSONObject("result").getJSONObject("video_info").put(flag,1);
            PlayerData p=new PlayerData();p.videoUrl="stale";
            try{BangumiDownloadApi.apply(p,body);fail(flag);}catch(JSONException expected){assertEquals("",p.videoUrl);}
        }
        JSONObject multi=body();multi.getJSONObject("result").getJSONObject("video_info").getJSONArray("durl").put(new JSONObject().put("url","https://fixture.invalid/part2.mp4"));
        try{BangumiDownloadApi.apply(new PlayerData(),multi);fail();}catch(JSONException expected){assertTrue(expected.getMessage().contains("单文件"));}
    }
    @Test public void deniedAccessIsNotTreatedAsPlayableOrEmptySuccess()throws Exception {
        try{BangumiDownloadApi.apply(new PlayerData(),new JSONObject().put("code",-10403));fail();}
        catch(JSONException expected){assertTrue(expected.getMessage().contains("-10403"));}
    }
}
