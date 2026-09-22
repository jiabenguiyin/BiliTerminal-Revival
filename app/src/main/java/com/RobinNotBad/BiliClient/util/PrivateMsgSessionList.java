package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.model.PrivateMsgSession;

import java.util.List;

public final class PrivateMsgSessionList {
    private PrivateMsgSessionList() {
    }

    public static int removeByTalkerUid(List<PrivateMsgSession> sessions, long talkerUid) {
        if (sessions == null) return -1;
        for (int position = 0; position < sessions.size(); position++) {
            PrivateMsgSession session = sessions.get(position);
            if (session != null && session.talkerUid == talkerUid) {
                sessions.remove(position);
                return position;
            }
        }
        return -1;
    }
}
