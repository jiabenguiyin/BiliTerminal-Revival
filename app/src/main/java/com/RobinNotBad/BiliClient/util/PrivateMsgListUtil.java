package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.model.PrivateMessage;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Keeps private-message history in stable chronological order. */
public final class PrivateMsgListUtil {
    private static final Comparator<PrivateMessage> CHRONOLOGICAL = (left, right) -> {
        int seqCompare = Long.compare(sequence(left), sequence(right));
        if (seqCompare != 0) return seqCompare;
        int timeCompare = Long.compare(timestamp(left), timestamp(right));
        if (timeCompare != 0) return timeCompare;
        return Long.compare(messageId(left), messageId(right));
    };

    private PrivateMsgListUtil() {
    }

    public static void sortChronologically(List<PrivateMessage> messages) {
        if (messages != null && messages.size() > 1) {
            Collections.sort(messages, CHRONOLOGICAL);
        }
    }

    public static void removeExisting(List<PrivateMessage> incoming,
                                      List<PrivateMessage> existing) {
        if (incoming == null || incoming.isEmpty() || existing == null || existing.isEmpty()) return;
        Set<Long> ids = new HashSet<>();
        Set<Long> seqnos = new HashSet<>();
        for (PrivateMessage message : existing) {
            if (message == null) continue;
            if (messageId(message) > 0L) ids.add(messageId(message));
            if (sequence(message) > 0L) seqnos.add(sequence(message));
        }
        for (int i = incoming.size() - 1; i >= 0; i--) {
            PrivateMessage message = incoming.get(i);
            if (message == null
                    || (messageId(message) > 0L && ids.contains(messageId(message)))
                    || (sequence(message) > 0L && seqnos.contains(sequence(message)))) {
                incoming.remove(i);
            }
        }
    }

    private static long sequence(PrivateMessage message) {
        return message == null ? 0L : message.msgSeqno;
    }

    private static long timestamp(PrivateMessage message) {
        return message == null ? 0L : message.timestamp;
    }

    private static long messageId(PrivateMessage message) {
        return message == null ? 0L : message.msgId;
    }
}
