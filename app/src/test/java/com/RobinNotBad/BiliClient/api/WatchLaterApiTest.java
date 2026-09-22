package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class WatchLaterApiTest {
    @Test
    public void missingDataIsTreatedAsAnEmptyList() throws Exception {
        JSONObject response = new JSONObject().put("code", -101).put("message", "账号未登录");

        assertTrue(WatchLaterApi.parseWatchLaterList(response).isEmpty());
    }

    @Test
    public void nullDataIsTreatedAsAnEmptyList() throws Exception {
        JSONObject response = new JSONObject().put("code", 0).put("data", JSONObject.NULL);

        assertTrue(WatchLaterApi.parseWatchLaterList(response).isEmpty());
    }

    @Test
    public void validListIsParsed() throws Exception {
        JSONObject item = new JSONObject()
                .put("aid", 1L)
                .put("bvid", "BV1test")
                .put("title", "测试视频")
                .put("pic", "https://example.test/cover.jpg")
                .put("owner", new JSONObject().put("name", "UP主"))
                .put("stat", new JSONObject().put("view", 12));
        JSONObject response = new JSONObject().put("code", 0)
                .put("data", new JSONObject().put("list", new JSONArray().put(item)));

        assertEquals(1, WatchLaterApi.parseWatchLaterList(response).size());
        assertEquals("BV1test", WatchLaterApi.parseWatchLaterList(response).get(0).bvid);
    }
}
