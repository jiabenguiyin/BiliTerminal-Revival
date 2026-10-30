package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;

import com.RobinNotBad.BiliClient.model.PrivateMessage;

import org.junit.Test;

import java.util.ArrayList;

public class PrivateMsgListUtilTest {
    @Test
    public void sortsByServerSequenceNumber() {
        ArrayList<PrivateMessage> messages = new ArrayList<>();
        messages.add(message(30, 3));
        messages.add(message(10, 1));
        messages.add(message(20, 2));

        PrivateMsgListUtil.sortChronologically(messages);

        assertEquals(10L, messages.get(0).msgId);
        assertEquals(20L, messages.get(1).msgId);
        assertEquals(30L, messages.get(2).msgId);
    }

    @Test
    public void removesMessagesAlreadyLoadedByRefresh() {
        ArrayList<PrivateMessage> existing = new ArrayList<>();
        existing.add(message(10, 1));
        ArrayList<PrivateMessage> incoming = new ArrayList<>();
        incoming.add(message(10, 1));
        incoming.add(message(20, 2));

        PrivateMsgListUtil.removeExisting(incoming, existing);

        assertEquals(1, incoming.size());
        assertEquals(20L, incoming.get(0).msgId);
    }

    private static PrivateMessage message(long id, long seqno) {
        PrivateMessage message = new PrivateMessage();
        message.msgId = id;
        message.msgSeqno = seqno;
        return message;
    }
}
