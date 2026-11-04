package com.RobinNotBad.BiliClient.api;

import org.junit.Test;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ReplyReportApiTest {
    @Test public void encodesOtherNoteWithoutInjectingFormFields() throws Exception {
        String note = "备注 & reason=17 + = ?/#\n第二行🙂";
        String body = ReplyApi.buildReportReplyForm(1234567890123L, 9876543210123L, 1, 0,
                note, "test&csrf=+值");
        Map<String, String> form = decode(body);
        assertEquals(6, form.size());
        assertEquals("1234567890123", form.get("oid"));
        assertEquals("9876543210123", form.get("rpid"));
        assertEquals("0", form.get("reason"));
        assertEquals(note, form.get("content"));
        assertEquals("test&csrf=+值", form.get("csrf"));
        assertFalse(body.contains("备注"));
    }

    @Test public void preservesVideoBangumiAndDynamicBusinessTypesAndChildRpid() throws Exception {
        for (int type : new int[]{1, 11, 12, 14, 17, 23}) {
            Map<String, String> form = decode(ReplyApi.buildReportReplyForm(456, 789, type, 7, null, "test"));
            assertEquals(String.valueOf(type), form.get("type"));
            assertEquals("456", form.get("oid"));
            assertEquals("789", form.get("rpid"));
            assertFalse(form.containsKey("root"));
            assertFalse(form.containsKey("parent"));
        }
    }

    @Test public void everyReasonKeepsItsExactCode() throws Exception {
        for (int reason = 0; reason <= 17; reason++) {
            Map<String, String> form = decode(ReplyApi.buildReportReplyForm(1, 2, 17, reason, "", "test"));
            assertEquals(String.valueOf(reason), form.get("reason"));
        }
    }

    @Test public void otherNoteIsOptionalAndOrdinaryReasonsNeverSendContent() throws Exception {
        assertFalse(decode(ReplyApi.buildReportReplyForm(1, 2, 1, 0, null, "test")).containsKey("content"));
        assertFalse(decode(ReplyApi.buildReportReplyForm(1, 2, 1, 0, "", "test")).containsKey("content"));
        assertFalse(decode(ReplyApi.buildReportReplyForm(1, 2, 1, 1, "private ignored note", "test"))
                .containsKey("content"));
        assertEquals("说明", decode(ReplyApi.buildReportReplyForm(1, 2, 1, 0, " 说明 ", "test")).get("content"));
    }

    @Test public void validatesOptionalNoteUsingUnicodeCodePoints() {
        assertTrue(ReplyApi.isValidReportNote(null));
        assertTrue(ReplyApi.isValidReportNote(""));
        assertFalse(ReplyApi.isValidReportNote("   \n\t"));
        assertFalse(ReplyApi.isValidReportNote("字"));
        StringBuilder note = new StringBuilder();
        for (int i = 0; i < 200; i++) note.append("🙂");
        assertTrue(ReplyApi.isValidReportNote(note.toString()));
        assertFalse(ReplyApi.isValidReportNote(note.append("字").toString()));
        assertThrows(IllegalArgumentException.class,
                () -> ReplyApi.buildReportReplyForm(1, 2, 1, 0, " ", "test"));
    }

    @Test public void rejectsInvalidIdsAndUnknownOrChildPseudoTypes() {
        for (long[] ids : new long[][]{{0, 1}, {-1, 1}, {1, 0}, {1, -1}}) {
            assertFalse(ReplyApi.isValidReportTarget(ids[0], ids[1], 1));
            assertThrows(IllegalArgumentException.class,
                    () -> ReplyApi.buildReportReplyForm(ids[0], ids[1], 1, 1, null, "test"));
        }
        for (int type : new int[]{-1, 0, 3, 999}) {
            assertFalse(ReplyApi.isValidReportTarget(1, 2, type));
            assertThrows(IllegalArgumentException.class,
                    () -> ReplyApi.buildReportReplyForm(1, 2, type, 1, null, "test"));
        }
        assertTrue(ReplyApi.isValidReportTarget(Long.MAX_VALUE, Long.MAX_VALUE, 1));
    }

    @Test public void rejectsUnknownReasons() {
        for (int reason : new int[]{-1, 18, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,
                    () -> ReplyApi.buildReportReplyForm(1, 2, 1, reason, null, "test"));
    }

    @Test public void refusesMissingCsrf() {
        for (String csrf : new String[]{null, "", "  "})
            assertThrows(IllegalArgumentException.class,
                    () -> ReplyApi.buildReportReplyForm(1, 2, 1, 1, null, csrf));
    }

    private Map<String, String> decode(String body) throws Exception {
        Map<String, String> result = new HashMap<>();
        for (String entry : body.split("&")) {
            String[] pair = entry.split("=", 2);
            String key = URLDecoder.decode(pair[0], "UTF-8");
            assertFalse("Duplicate form parameter", result.containsKey(key));
            result.put(key, URLDecoder.decode(pair[1], "UTF-8"));
        }
        return result;
    }
}
