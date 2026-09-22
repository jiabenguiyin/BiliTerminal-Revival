package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

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
}
