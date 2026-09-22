package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;

import com.RobinNotBad.BiliClient.model.PrivateMsgSession;

import org.junit.Test;

import java.util.ArrayList;

public class PrivateMsgSessionListTest {
    @Test
    public void removingFirstSessionKeepsEveryOtherSession() {
        ArrayList<PrivateMsgSession> sessions = new ArrayList<>();
        sessions.add(session(100));
        sessions.add(session(200));
        sessions.add(session(300));

        assertEquals(0, PrivateMsgSessionList.removeByTalkerUid(sessions, 100));
        assertEquals(2, sessions.size());
        assertEquals(200, sessions.get(0).talkerUid);
        assertEquals(300, sessions.get(1).talkerUid);
    }

    private static PrivateMsgSession session(long uid) {
        PrivateMsgSession session = new PrivateMsgSession();
        session.talkerUid = uid;
        return session;
    }
}
