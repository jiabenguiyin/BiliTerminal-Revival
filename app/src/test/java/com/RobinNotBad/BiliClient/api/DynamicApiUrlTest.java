package com.RobinNotBad.BiliClient.api;

import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DynamicApiUrlTest {

    @Test
    public void filteredFollowFeedUsesCategoryEndpointAndValue() {
        String url = DynamicApi.buildDynamicListUrl(0, 0, "video", true);

        assertTrue(url.startsWith("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?type=video"));
        assertTrue(url.contains("features="));
        assertFalse(url.contains("type=all"));
    }

    @Test
    public void unfilteredFeedKeepsDesktopEndpoint() {
        String url = DynamicApi.buildDynamicListUrl(0, 0, "all", false);

        assertTrue(url.startsWith("https://api.bilibili.com/x/polymer/web-dynamic/desktop/v1/feed/all?type=all"));
    }

    @Test
    public void userSpaceFeedDoesNotApplyFollowFeedType() {
        String url = DynamicApi.buildDynamicListUrl(123, 42, null, false);

        assertTrue(url.startsWith("https://api.bilibili.com/x/polymer/web-dynamic/desktop/v1/feed/space"));
        assertTrue(url.contains("host_mid=42"));
        assertTrue(url.contains("offset=123"));
        assertFalse(url.contains("type="));
    }

    @Test
    public void emptyFollowFeedBusinessErrorIsAccepted() throws Exception {
        Method method = DynamicApi.class.getDeclaredMethod(
                "parseDynamicListResponse", JSONObject.class, java.util.List.class, long.class);
        method.setAccessible(true);

        long offset = (Long) method.invoke(null,
                new JSONObject().put("code", -101).put("message", "账号未登录"),
                new ArrayList<>(), 0L);

        org.junit.Assert.assertEquals(-1L, offset);
    }
}
