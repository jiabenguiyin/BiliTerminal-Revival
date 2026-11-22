package com.RobinNotBad.BiliClient.api;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExperienceRecordApiTest {
    @Test
    public void detectsOnlyNewShareReward() throws Exception {
        ArrayList<ExperienceRecordApi.Record> before = ExperienceRecordApi.parseRecords(
                new JSONObject("{\"code\":0,\"data\":{\"list\":["
                        + "{\"delta\":5,\"time\":\"2026-11-07 10:00:00\",\"reason\":\"分享视频\"}]}}"));
        ArrayList<ExperienceRecordApi.Record> unchanged = ExperienceRecordApi.parseRecords(
                new JSONObject("{\"code\":0,\"data\":{\"list\":["
                        + "{\"delta\":5,\"time\":\"2026-11-07 10:00:00\",\"reason\":\"分享视频\"}]}}"));
        ArrayList<ExperienceRecordApi.Record> after = ExperienceRecordApi.parseRecords(
                new JSONObject("{\"code\":0,\"data\":{\"list\":["
                        + "{\"delta\":5,\"time\":\"2026-11-07 10:01:00\",\"reason\":\"分享视频\"},"
                        + "{\"delta\":5,\"time\":\"2026-11-07 10:00:00\",\"reason\":\"分享视频\"}]}}"));

        assertFalse(ExperienceRecordApi.hasNewShareReward(before, unchanged));
        assertTrue(ExperienceRecordApi.hasNewShareReward(before, after));
    }
}
