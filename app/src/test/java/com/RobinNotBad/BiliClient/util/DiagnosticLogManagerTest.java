package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticLogManagerTest {
    @Test
    public void credentialsAreRedactedFromText() {
        String safe = DiagnosticLogManager.sanitizeTextForDiagnostics(
                "Cookie: SESSDATA=secret; bili_jct=csrf-secret token=token-secret Authorization=Bearer bearer-secret");

        assertFalse(safe.contains("secret"));
        assertTrue(safe.contains("[redacted]"));
    }

    @Test
    public void urlQueryIsRemovedCompletely() {
        String safe = DiagnosticLogManager.sanitizeTextForDiagnostics(
                "GET https://api.bilibili.com/x/web-interface/nav?access_key=secret&keyword=private");

        assertTrue(safe.contains("api.bilibili.com/x/web-interface/nav"));
        assertFalse(safe.contains("access_key"));
        assertFalse(safe.contains("secret"));
        assertFalse(safe.contains("private"));
    }

    @Test
    public void privateInteractionContentIsRedacted() {
        String safe = DiagnosticLogManager.sanitizeTextForDiagnostics("comment=private reply content");

        assertFalse(safe.contains("private reply content"));
        assertTrue(safe.contains("[redacted]"));
    }

    @Test
    public void sensitiveDetailKeysAreRecognized() {
        assertTrue(DiagnosticLogManager.isSensitiveKeyForDiagnostics("SESSDATA"));
        assertTrue(DiagnosticLogManager.isSensitiveKeyForDiagnostics("refresh_token"));
        assertTrue(DiagnosticLogManager.isSensitiveKeyForDiagnostics("user_id"));
    }
}
