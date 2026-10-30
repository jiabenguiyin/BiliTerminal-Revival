package com.RobinNotBad.BiliClient.api;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReplyTopApiTest {
    @Test public void onlyLoggedInOwnerCanPinRootComments() {
        assertTrue(ReplyApi.canManageReplyTop(42, 42, 0));
        assertFalse(ReplyApi.canManageReplyTop(0, 0, 0));
        assertFalse(ReplyApi.canManageReplyTop(42, 0, 0));
        assertFalse(ReplyApi.canManageReplyTop(42, 43, 0));
        assertFalse(ReplyApi.canManageReplyTop(42, 42, 123));
    }

    @Test public void pinFormKeepsEachCommentBusinessTypeAndLongIds() {
        for (int type : new int[]{1, 11, 12, 14, 17, 23}) {
            String body = ReplyApi.buildTopReplyForm(1234567890123L, 9876543210123L, type, true, "test&csrf");
            assertTrue(body.contains("oid=1234567890123"));
            assertTrue(body.contains("rpid=9876543210123"));
            assertTrue(body.contains("type=" + type));
            assertTrue(body.contains("action=1"));
            assertTrue(body.contains("csrf=test%26csrf"));
        }
    }

    @Test public void unpinUsesActionZero() {
        assertTrue(ReplyApi.buildTopReplyForm(1, 2, 17, false, "test").contains("action=0"));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectInvalidCommentTarget() {
        ReplyApi.buildTopReplyForm(1, 0, 1, true, "test");
    }
}
