package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class LoginRecordApiTest {
    @Test
    public void nullDataIsTreatedAsNoLoginRecords() throws Exception {
        JSONObject response = new JSONObject()
                .put("code", 0)
                .put("data", JSONObject.NULL);

        assertTrue(LoginRecordApi.parseLoginRecords(response).isEmpty());
    }

    @Test
    public void objectDataProducesOneLoginRecord() throws Exception {
        JSONObject response = new JSONObject()
                .put("code", 0)
                .put("data", new JSONObject()
                        .put("mid", 123L)
                        .put("device_name", "Watch")
                        .put("login_type", "二维码")
                        .put("login_time", "2026-08-28 10:00:00")
                        .put("location", "上海")
                        .put("ip", "127.0.0.1"));

        assertEquals(1, LoginRecordApi.parseLoginRecords(response).size());
        assertEquals(123L, LoginRecordApi.parseLoginRecords(response).get(0).mid);
    }
}
