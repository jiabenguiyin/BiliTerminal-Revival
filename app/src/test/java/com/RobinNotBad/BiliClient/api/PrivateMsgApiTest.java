package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.RobinNotBad.BiliClient.model.PrivateMessage;
import com.RobinNotBad.BiliClient.model.PrivateMsgSession;

import java.util.ArrayList;

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
}
