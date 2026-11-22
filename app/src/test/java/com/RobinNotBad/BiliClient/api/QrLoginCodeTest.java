package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;

public class QrLoginCodeTest {
    @Test
    public void parsesSuccessfulQrPayload() throws Exception {
        QrLoginCode result = QrLoginCode.parse(
                "{\"code\":0,\"message\":\"0\",\"data\":{\"url\":\"https://example.test/login\",\"qrcode_key\":\"abc123\"}}");

        assertEquals("https://example.test/login", result.url);
        assertEquals("abc123", result.key);
    }

    @Test
    public void reportsBusinessErrorWithoutReadingData() throws Exception {
        try {
            QrLoginCode.parse("{\"code\":-400,\"message\":\"invalid request\",\"data\":null}");
            fail("Expected a business error");
        } catch (JSONException error) {
            assertTrue(error.getMessage().contains("-400"));
            assertTrue(error.getMessage().contains("invalid request"));
        }
    }

    @Test
    public void reportsSuccessfulEnvelopeWithNullData() throws Exception {
        try {
            QrLoginCode.parse("{\"code\":0,\"message\":\"0\",\"data\":null}");
            fail("Expected a missing data error");
        } catch (JSONException error) {
            assertTrue(error.getMessage().contains("未返回有效数据"));
        }
    }

    @Test
    public void rejectsIncompleteQrData() throws Exception {
        try {
            QrLoginCode.parse("{\"code\":0,\"data\":{\"url\":\"https://example.test/login\"}}");
            fail("Expected an incomplete payload error");
        } catch (JSONException error) {
            assertTrue(error.getMessage().contains("不完整"));
        }
    }
}
