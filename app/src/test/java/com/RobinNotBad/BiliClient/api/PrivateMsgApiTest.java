package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.RobinNotBad.BiliClient.model.PrivateMessage;
import com.RobinNotBad.BiliClient.model.PrivateMsgSession;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PrivateMsgApiTest {
    @Test
    public void emptyUserListDoesNotBuildInvalidSubstring() throws Exception {
        assertTrue(PrivateMsgApi.getUsersInfo(new ArrayList<>()).isEmpty());
    }

    @Test
    public void nullOrInvalidUserListDoesNotRequestUsers() throws Exception {
        assertTrue(PrivateMsgApi.getUsersInfo(null).isEmpty());

        ArrayList<Long> invalidUids = new ArrayList<>();
        invalidUids.add(null);
        invalidUids.add(0L);
        assertTrue(PrivateMsgApi.getUsersInfo(invalidUids).isEmpty());
    }

    @Test
    public void systemSessionKeepsItsOwnAccountIdentity() throws Exception {
        PrivateMsgSession session = new PrivateMsgSession();
        session.accountName = "举报反馈";
        session.accountAvatar = "https://example.com/report.png";

        assertTrue(session.hasAccountInfo());
        assertEquals("举报反馈", session.resolveName("普通用户"));
        assertEquals("https://example.com/report.png",
                session.resolveAvatar("https://example.com/user.png"));
    }

    @Test
    public void emptyConversationRefreshStartsFromZero() {
        assertEquals(0, PrivateMsgApi.getRefreshBeginSeqno(new ArrayList<>()));

        ArrayList<PrivateMessage> messages = new ArrayList<>();
        PrivateMessage message = new PrivateMessage();
        message.msgSeqno = 1234;
        messages.add(message);
        assertEquals(1234, PrivateMsgApi.getRefreshBeginSeqno(messages));
    }

    @Test
    public void sessionPageExposesUnreadCursorWithoutMessageContent() throws Exception {
        JSONObject session = new JSONObject()
                .put("talker_id", 42)
                .put("session_type", 1)
                .put("system_msg_type", 0)
                .put("session_ts", "987654321")
                .put("unread_count", 2);
        JSONObject root = new JSONObject().put("data", new JSONObject()
                .put("has_more", 1)
                .put("session_list", new JSONArray().put(session)));

        PrivateMsgApi.SessionPage page = PrivateMsgApi.parseSessionPage(root);

        assertTrue(page.hasMore);
        assertEquals(987654321L, page.nextCursor);
        assertEquals(2, page.sessions.get(0).unread);
    }

    @Test public void sessionBusinessFailureKeepsTheNumericCodeInsteadOfPretendingToBeTransportFailure() throws Exception {
        try {
            PrivateMsgApi.requireSuccessfulSessionResponse(new JSONObject().put("code", -412).put("message", "private server text"));
            org.junit.Assert.fail("Expected a session business error");
        } catch (PrivateMsgApi.SessionApiException error) {
            assertEquals(-412, error.code);
            assertTrue(error.getMessage().contains("-412"));
            assertTrue(!error.getMessage().contains("private server text"));
        }
        PrivateMsgApi.requireSuccessfulSessionResponse(new JSONObject().put("code", 0));
    }
}
