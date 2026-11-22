package com.RobinNotBad.BiliClient.api;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShareApiTest {
    @Test public void preservesShareStatusCodes() throws Exception {
        assertEquals(0, ShareApi.parseResponseCode(new JSONObject("{\"code\":0}")));
        assertEquals(71000, ShareApi.parseResponseCode(new JSONObject("{\"code\":71000}")));
        assertEquals(-101, ShareApi.parseResponseCode(new JSONObject("{\"code\":-101}")));
    }
}
