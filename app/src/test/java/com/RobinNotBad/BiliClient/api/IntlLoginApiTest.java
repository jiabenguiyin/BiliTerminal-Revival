package com.RobinNotBad.BiliClient.api;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntlLoginApiTest {
    @Test
    public void acceptsOnlyOfficialInternationalHosts() {
        assertTrue(IntlLoginApi.isAllowedHost("passport.bilibili.tv"));
        assertTrue(IntlLoginApi.isAllowedHost("api.bilibili.tv"));
        assertFalse(IntlLoginApi.isAllowedHost("evilbilibili.tv"));
        assertFalse(IntlLoginApi.isAllowedHost("bilibili.tv.evil.example"));
        assertFalse(IntlLoginApi.isAllowedHost("passport.bilibili.com"));
    }

    @Test
    public void rejectsForeignCookieDomains() {
        assertTrue(IntlLoginApi.isAllowedCookieDomain(
                "passport.bilibili.tv", ".bilibili.tv"));
        assertFalse(IntlLoginApi.isAllowedCookieDomain(
                "passport.bilibili.tv", ".biliintl.com"));
        assertFalse(IntlLoginApi.isAllowedCookieDomain(
                "passport.bilibili.tv", ".evil.bilibili.tv"));
    }
}
