package com.RobinNotBad.BiliClient.event;

import com.RobinNotBad.BiliClient.util.ReplyStateStore;

public final class ReplyStateEvent {
    public final ReplyStateStore.State state;
    public ReplyStateEvent(ReplyStateStore.State state) { this.state = state; }
}
