package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QrLoginResponseTest {
    @Test
    public void parsesNormalWebQrStatus() throws Exception {
        QrLoginResponse response = QrLoginResponse.parse(
                "{\"code\":0,\"message\":\"0\",\"data\":{\"code\":86101}}");

        assertTrue(response.hasStatus());
        assertEquals(86101, response.statusCode);
    }

    @Test
    public void reportsNullDataWithoutThrowingOrHidingApiError() throws Exception {
        QrLoginResponse response = QrLoginResponse.parse(
                "{\"code\":-400,\"message\":\"unsupported scanner\",\"data\":null}");

        assertFalse(response.hasStatus());
        assertEquals(-400, response.responseCode);
        assertTrue(response.failureSummary().contains("-400"));
        assertTrue(response.failureSummary().contains("unsupported scanner"));
    }

    @Test
    public void treatsMissingQrStatusAsInvalidResponse() throws Exception {
        QrLoginResponse response = QrLoginResponse.parse(
                "{\"code\":0,\"data\":{\"message\":\"missing status\"}}");

        assertFalse(response.hasStatus());
        assertTrue(response.failureSummary().contains("missing status"));
    }

    @Test
    public void doesNotAcceptNestedStatusWhenEnvelopeFailed() throws Exception {
        QrLoginResponse response = QrLoginResponse.parse(
                "{\"code\":-400,\"message\":\"request rejected\",\"data\":{\"code\":0}}");

        assertFalse(response.hasStatus());
        assertTrue(response.failureSummary().contains("-400"));
    }
}
