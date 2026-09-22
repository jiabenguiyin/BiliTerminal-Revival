package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class CookiesTest {
    @Test
    public void preservesValuesContainingEquals() {
        Cookies cookies = new Cookies("SESSDATA=abc==; bili_jct=csrf");
        assertEquals("abc==", cookies.get("SESSDATA"));
        assertEquals("csrf", cookies.get("bili_jct"));
    }

    @Test
    public void treatsCookieNamesCaseInsensitivelyWhenRefreshing() {
        Cookies cookies = new Cookies("SESSDATA=old; bili_jct=old-csrf");
        cookies.set("sessdata", "new");
        cookies.set("BILI_JCT", "new-csrf");
        assertEquals("new", cookies.get("SESSDATA"));
        assertEquals("new-csrf", cookies.get("bili_jct"));
        assertEquals("SESSDATA=new; bili_jct=new-csrf", cookies.toString());
    }

    @Test
    public void relayCanMergePassportCookiesWithoutErasingExistingLogin() {
        String existing = "SESSDATA=old; bili_jct=old-csrf; buvid3=device";
        String merged = CookieMergeUtil.merge(existing, Arrays.asList(
                "SESSDATA=new==; Domain=.bilibili.com; Path=/",
                "bili_jct=new-csrf; Domain=.bilibili.com; Path=/"
        ), true);
        Cookies cookies = new Cookies(merged);
        assertEquals("new==", cookies.get("SESSDATA"));
        assertEquals("new-csrf", cookies.get("bili_jct"));
        assertEquals("device", cookies.get("buvid3"));
    }

    @Test
    public void nonPassportOrEmptyAuthCookieCannotDropLogin() {
        String existing = "SESSDATA=keep; bili_jct=keep-csrf";
        String fromApiHost = CookieMergeUtil.merge(existing,
                Arrays.asList("SESSDATA=other; Path=/"), false);
        String emptyFromPassport = CookieMergeUtil.merge(fromApiHost,
                Arrays.asList("SESSDATA=; Max-Age=0; Path=/"), true);
        Cookies cookies = new Cookies(emptyFromPassport);
        assertEquals("keep", cookies.get("SESSDATA"));
        assertEquals("keep-csrf", cookies.get("bili_jct"));
    }
}
