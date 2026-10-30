package com.RobinNotBad.BiliClient.api;

import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class DanmakuCountTest {
    @Test public void readsTheTotalWithoutParsingDanmakuContent() throws Exception {
        for (long count : new long[]{0, 5000, 5001, 5000000000L}) {
            JSONObject result = new JSONObject().put("code", 0).put("data",
                    new JSONObject().put("stat", new JSONObject().put("danmaku", count)));
            assertEquals(count, DanmakuApi.parseVideoDanmakuCount(result));
        }
    }

    @Test public void errorsAndMissingCountsAreNotMistakenForAnEmptyVideo() throws Exception {
        for (String input : new String[]{"{\"code\":-101}", "{\"code\":0}",
                "{\"code\":0,\"data\":{\"stat\":{}}}",
                "{\"code\":0,\"data\":{\"stat\":{\"danmaku\":-1}}}"}) {
            try { DanmakuApi.parseVideoDanmakuCount(new JSONObject(input)); fail(input); }
            catch (IOException expected) { }
        }
    }
}
