package com.RobinNotBad.BiliClient.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FollowApiSearchTest {
    @Test public void encodesFollowingSearchKeywordAndUsesSupportedPageSize() {
        assertEquals("https://api.bilibili.com/x/relation/followings/search?vmid=42&pn=3&ps=50&name=%E7%8C%AB+%26+dog",
                FollowApi.buildFollowingSearchUrl(42, 3, "猫 & dog"));
    }
}
