package com.RobinNotBad.BiliClient.api;

import org.junit.Test;
import com.RobinNotBad.BiliClient.model.DanmakuElem;
import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DanmakuApiTest {
    @Test
    public void segmentCountCoversWholeVideoWithoutExtraFullSegment() {
        assertEquals(1, DanmakuApi.calculateSegmentCount(1));
        assertEquals(1, DanmakuApi.calculateSegmentCount(360));
        assertEquals(2, DanmakuApi.calculateSegmentCount(361));
        assertEquals(10, DanmakuApi.calculateSegmentCount(3600));
        assertEquals(20, DanmakuApi.calculateSegmentCount(7200));
        assertEquals(21, DanmakuApi.calculateSegmentCount(7201));
        assertEquals(5965233, DanmakuApi.calculateSegmentCount(Integer.MAX_VALUE));
    }

    @Test
    public void unknownDurationStillAttemptsTheFirstSegment() {
        assertEquals(1, DanmakuApi.calculateSegmentCount(0));
        assertEquals(1, DanmakuApi.calculateSegmentCount(-1));
    }

    @Test public void emptyAndFailedIntervalsDoNotHideSecondHour() throws Exception {
        List<Integer> requested = new ArrayList<>();
        List<DmSegMobileReply> loaded = DanmakuApi.loadSegments(7200, index -> {
            requested.add(index);
            if (index == 3) throw new IOException("simulated network failure");
            DmSegMobileReply reply = new DmSegMobileReply();
            if (index == 12 || index == 20) {
                DanmakuElem elem = new DanmakuElem();
                elem.progress = (index - 1) * 360000 + 1000;
                reply.elems.add(elem);
            }
            return reply;
        });
        assertEquals(21, requested.size());
        assertEquals(Integer.valueOf(20), requested.get(requested.size() - 1));
        assertEquals(2, loaded.size());
        assertEquals(3961000, loaded.get(0).elems.get(0).progress);
        assertEquals(6841000, loaded.get(1).elems.get(0).progress);
    }

    @Test public void transientFailureRetriesWithoutDuplicatingData() throws Exception {
        int[] attempts = {0};
        List<DmSegMobileReply> loaded = DanmakuApi.loadSegments(360, index -> {
            if (++attempts[0] == 1) throw new java.net.SocketTimeoutException("timeout");
            DmSegMobileReply reply = new DmSegMobileReply();
            reply.elems.add(new DanmakuElem());
            return reply;
        });
        assertEquals(2, attempts[0]);
        assertEquals(1, loaded.size());
    }

    @Test(expected = InterruptedIOException.class)
    public void cancelledVideoDoesNotRequestRemainingSegments() throws Exception {
        DanmakuApi.loadSegments(7200, index -> {
            throw new InterruptedIOException("cancelled");
        });
    }
}
