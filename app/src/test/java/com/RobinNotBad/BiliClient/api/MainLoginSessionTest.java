package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MainLoginSessionTest {
    private static final String COOKIES =
            "DedeUserID=12345; SESSDATA=session; bili_jct=csrf";

    @Test
    public void acceptsMatchingAuthenticatedSession() throws Exception {
        assertEquals(12345L, MainLoginSession.verifiedMid(
                "{\"code\":0,\"data\":{\"isLogin\":true,\"mid\":12345}}", COOKIES));
    }

    @Test
    public void rejectsLoggedOutOrMismatchedSession() throws Exception {
        assertEquals(0L, MainLoginSession.verifiedMid(
                "{\"code\":0,\"data\":{\"isLogin\":false,\"mid\":0}}", COOKIES));
        assertEquals(0L, MainLoginSession.verifiedMid(
                "{\"code\":0,\"data\":{\"isLogin\":true,\"mid\":999}}", COOKIES));
    }

    @Test
    public void rejectsIncompleteCookieSet() throws Exception {
        assertEquals(0L, MainLoginSession.verifiedMid(
                "{\"code\":0,\"data\":{\"isLogin\":true,\"mid\":12345}}",
                "DedeUserID=12345; SESSDATA=session"));
    }
}
