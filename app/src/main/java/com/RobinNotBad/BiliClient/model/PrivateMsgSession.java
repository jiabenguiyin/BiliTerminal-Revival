package com.RobinNotBad.BiliClient.model;

import org.json.JSONObject;

public class PrivateMsgSession {
    public long talkerUid = 0;
    public int sessionType = 1;
    public int systemMsgType = 0;
    public int unread = 0;
    public int contentType = 0;
    public JSONObject content;
    public String accountName = "";
    public String accountAvatar = "";

    public PrivateMsgSession() {
    }

    public boolean hasAccountInfo() {
        return accountName != null && !accountName.isEmpty();
    }

    public String resolveName(String fallback) {
        return hasAccountInfo() ? accountName : fallback;
    }

    public String resolveAvatar(String fallback) {
        return hasAccountInfo() ? accountAvatar : fallback;
    }
}
