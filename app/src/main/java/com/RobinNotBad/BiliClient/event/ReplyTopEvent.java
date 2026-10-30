package com.RobinNotBad.BiliClient.event;

public class ReplyTopEvent {
    public final long oid;
    public final int type;
    public final long rpid;
    public final boolean pinned;

    public ReplyTopEvent(long oid, int type, long rpid, boolean pinned) {
        this.oid = oid;
        this.type = type;
        this.rpid = rpid;
        this.pinned = pinned;
    }
}
