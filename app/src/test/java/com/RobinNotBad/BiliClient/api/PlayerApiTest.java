package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.RobinNotBad.BiliClient.model.DashData;
import com.RobinNotBad.BiliClient.model.DashVideoStream;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.util.DeviceProfile;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PlayerApiTest {
    @Test
    public void videoRequestUsesProgressiveQualityParameters() {
        PlayerData data = new PlayerData();
        data.aid = 123L;
        data.cid = 456L;
        data.qn = 80;

        String query = PlayerApi.buildVideoPlayQuery(data);

        assertTrue(query.contains("avid=123"));
        assertTrue(query.contains("cid=456"));
        assertTrue(query.contains("qn=80"));
        assertTrue(query.contains("fnval=0"));
        assertTrue(query.contains("fourk="));
    }

    @Test
    public void dashMetadataRequestUsesTheCurrentDeviceCap() {
        PlayerData data = new PlayerData();
        data.aid = 123L;
        data.cid = 456L;
        data.qn = 16;

        String query = PlayerApi.buildDashPlayQuery(data);

        assertTrue(query.contains("avid=123"));
        assertTrue(query.contains("cid=456"));
        assertTrue(query.contains("qn=" + DeviceProfile.maxQuality()));
        assertTrue(query.contains("fnval=16"));
    }

    @Test
    public void ordinaryQualitiesUseSingleProgressivePlayer() {
        assertTrue(PlayerApi.usesProgressivePlayback(6));
        assertTrue(PlayerApi.usesProgressivePlayback(16));
        assertTrue(PlayerApi.usesProgressivePlayback(32));
        assertTrue(PlayerApi.usesProgressivePlayback(64));
        assertTrue(PlayerApi.usesProgressivePlayback(80));
    }

    @Test
    public void highFrameRateAndMemberQualitiesKeepDashPlayback() {
        assertFalse(PlayerApi.usesProgressivePlayback(0));
        assertFalse(PlayerApi.usesProgressivePlayback(74));
        assertFalse(PlayerApi.usesProgressivePlayback(112));
        assertFalse(PlayerApi.usesProgressivePlayback(116));
        assertFalse(PlayerApi.usesProgressivePlayback(120));
    }

    @Test
    public void automaticOrdinaryQualityFallsBackToSinglePlayer() {
        assertTrue(PlayerApi.automaticQualityUsesProgressivePlayback(0, 32));
        assertFalse(PlayerApi.automaticQualityUsesProgressivePlayback(0, 120));
        assertFalse(PlayerApi.automaticQualityUsesProgressivePlayback(80, 80));
    }

    @Test
    public void availableQualityListOnlyContainsAdvertisedPlayableStreams() throws Exception {
        JSONObject response = new JSONObject().put("accept_quality",
                new JSONArray().put(120).put(116).put(112).put(80).put(64).put(32));
        DashData dashData = new DashData();
        dashData.videoStreams.add(video(80, 1920, 1080, "30"));
        dashData.videoStreams.add(video(64, 1280, 720, "30"));

        assertArrayEquals(new int[]{80, 64},
                PlayerApi.extractAvailableQualityValues(response, dashData));
    }

    @Test
    public void missingAdvertisedListFallsBackToActualDashStreams() throws Exception {
        DashData dashData = new DashData();
        dashData.videoStreams.add(video(116, 1920, 1080, "60"));
        dashData.videoStreams.add(video(80, 1920, 1080, "30"));

        assertArrayEquals(new int[]{116, 80},
                PlayerApi.extractAvailableQualityValues(new JSONObject(), dashData));
    }

    private DashVideoStream video(int id, int width, int height, String frameRate) {
        DashVideoStream stream = new DashVideoStream();
        stream.id = id;
        stream.width = width;
        stream.height = height;
        stream.frameRate = frameRate;
        stream.codecid = 7;
        stream.baseUrl = "https://example.test/" + id;
        return stream;
    }

    @Test public void missingQualityDescriptionsUseOnlyTheReturnedProgressiveQuality() throws Exception {
        JSONObject response = new JSONObject().put("quality",80).put("accept_quality",new JSONArray())
                .put("durl",new JSONArray().put(new JSONObject().put("url","https://example.invalid/video.mp4")));
        assertArrayEquals(new int[]{80},PlayerApi.extractAvailableQualityValues(response,null));
    }

    @Test public void responseWithoutAProgressiveUrlDoesNotInventQualityChoices() throws Exception {
        assertArrayEquals(new int[0],PlayerApi.extractAvailableQualityValues(new JSONObject().put("quality",80),null));
    }

    @Test public void responseWithoutActualQualityDoesNotPretendToBe1080p() throws Exception {
        JSONObject response = new JSONObject().put("durl",new JSONArray().put(new JSONObject().put("url","https://example.invalid/video.mp4")));
        assertArrayEquals(new int[0],PlayerApi.extractAvailableQualityValues(response,null));
    }

    @Test public void audioDownloadWorksWithoutAnyVideoOrQualityListAndRefreshesStaleUrls() throws Exception {
        PlayerData data = new PlayerData();data.qn=120;data.videoUrl="https://example.invalid/old-video";data.audioUrl="https://example.invalid/expired";
        PlayerApi.applyAudioDownloadData(data,audioResponse(audio("https://example.invalid/current",128000)));
        assertEquals("https://example.invalid/current",data.audioUrl);assertEquals("",data.videoUrl);
        assertEquals(0,data.qn);assertTrue(data.timeStamp>0);assertFalse(data.dashData.hasVideo());
    }

    @Test public void missingAudioIsReportedWithoutLeavingAnExpiredUrlBehind() throws Exception {
        for(JSONObject response:new JSONObject[]{new JSONObject(),new JSONObject().put("dash",new JSONObject().put("audio",new JSONArray()))}) {
            PlayerData data=new PlayerData();data.audioUrl="https://example.invalid/expired";data.audioBackupUrls.add(data.audioUrl);data.timeStamp=123;
            try {PlayerApi.applyAudioDownloadData(data,response);fail("Missing audio must be reported");}
            catch(org.json.JSONException expected){assertTrue(expected.getMessage().contains("音频流"));}
            assertEquals("",data.audioUrl);assertTrue(data.audioBackupUrls.isEmpty());assertEquals(0,data.timeStamp);
        }
    }

    @Test public void audioDownloadCanUseABackupWhenThePrimaryAddressIsEmpty() throws Exception {
        PlayerData data=new PlayerData();
        JSONObject stream=audio("",128000).put("backup_url",new JSONArray().put("https://example.invalid/backup"));
        PlayerApi.applyAudioDownloadData(data,audioResponse(stream));
        assertEquals("https://example.invalid/backup",data.audioUrl);assertTrue(data.audioBackupUrls.isEmpty());
    }

    @Test public void blankHighBitrateAudioDoesNotHideAUsableTrack() throws Exception {
        PlayerData data=new PlayerData();
        PlayerApi.applyAudioDownloadData(data,audioResponse(audio("",999999),audio("https://example.invalid/usable",64000)));
        assertEquals("https://example.invalid/usable",data.audioUrl);
    }

    @Test public void compatibleAacIsPreferredToAnUnsupportedHigherBitrateTrack() throws Exception {
        PlayerData data=new PlayerData();
        JSONObject opus=audio("https://example.invalid/opus",999999).put("codecs","opus").put("mimeType","audio/webm");
        PlayerApi.applyAudioDownloadData(data,audioResponse(opus,audio("https://example.invalid/aac",128000)));
        assertEquals("https://example.invalid/aac",data.audioUrl);
    }

    @Test public void audioDownloadDoesNotParseOrRequireVideoRepresentations() throws Exception {
        PlayerData data=new PlayerData();JSONObject response=audioResponse(audio("https://example.invalid/audio",128000));
        response.getJSONObject("dash").put("video",new JSONArray().put("invalid video descriptor"));
        PlayerApi.applyAudioDownloadData(data,response);
        assertEquals("https://example.invalid/audio",data.audioUrl);
    }

    private JSONObject audio(String url,long bandwidth)throws Exception {
        return new JSONObject().put("id",30280).put("base_url",url).put("bandwidth",bandwidth)
                .put("mimeType","audio/mp4").put("codecs","mp4a.40.2");
    }

    @Test public void realVideoTwoFortyPResponseDoesNotBecomeAnEmptyQualityList() throws Exception {
        JSONObject response=new JSONObject().put("quality",6).put("accept_quality",new JSONArray().put(6))
                .put("support_formats",new JSONArray().put(new JSONObject().put("quality",6).put("new_description","240P 极速")))
                .put("durl",new JSONArray().put(new JSONObject().put("url","https://example.invalid/video.mp4")));
        assertArrayEquals(new int[]{6},PlayerApi.extractAvailableQualityValues(response,null));
        PlayerData request=new PlayerData();request.qn=6;
        assertTrue(PlayerApi.buildVideoPlayQuery(request).contains("&qn=6&"));
    }
    private JSONObject audioResponse(JSONObject... streams)throws Exception {
        JSONArray audio=new JSONArray();for(JSONObject stream:streams)audio.put(stream);
        return new JSONObject().put("dash",new JSONObject().put("audio",audio));
    }
}
