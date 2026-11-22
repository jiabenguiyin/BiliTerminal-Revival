package com.RobinNotBad.BiliClient.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BilibiliVideoLinkParserTest {
    @Test public void extractsVideoUrlFromOfficialShareText() {
        String link = "https://www.bilibili.com/video/BV1ab411c7mD/?share_source=copy";
        assertEquals(link, BilibiliVideoLinkParser.extractVideoUrl("【视频标题】\n" + link));
    }

    @Test public void acceptsAvAndShortShareLinks() {
        assertEquals("https://m.bilibili.com/video/av12345",
                BilibiliVideoLinkParser.extractVideoUrl("https://m.bilibili.com/video/av12345"));
        assertEquals("https://b23.tv/AbC123",
                BilibiliVideoLinkParser.extractVideoUrl("复制链接：https://b23.tv/AbC123。"));
    }

    @Test public void rejectsNonVideoAndLookalikeHosts() {
        assertNull(BilibiliVideoLinkParser.extractVideoUrl("https://www.bilibili.com/read/cv123"));
        assertNull(BilibiliVideoLinkParser.extractVideoUrl("https://bilibili.com.evil.test/video/BV1ab411c7mD"));
        assertNull(BilibiliVideoLinkParser.extractVideoUrl("https://live.bilibili.com/123"));
    }
}
