package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.RobinNotBad.BiliClient.model.DanmakuElem;
import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.model.HighEnergyData;

import org.junit.Test;

public class HighEnergyDataBuilderTest {
    @Test
    public void mapsGlobalDanmakuTimeIntoTenSecondBuckets() {
        HighEnergyDataBuilder builder = new HighEnergyDataBuilder();
        builder.reset(30000);

        DmSegMobileReply segment = new DmSegMobileReply();
        segment.elems.add(elem(1000));
        segment.elems.add(elem(9999));
        segment.elems.add(elem(10000));
        segment.elems.add(elem(29999));
        segment.elems.add(elem(30000));

        assertTrue(builder.addSegment(segment));
        HighEnergyData data = builder.snapshot();

        assertEquals(3, data.events.length);
        assertEquals(2.0f, data.events[0], 0.001f);
        assertEquals(1.0f, data.events[1], 0.001f);
        assertEquals(1.0f, data.events[2], 0.001f);
    }

    @Test
    public void ignoresEmptyOrOutOfRangeSegments() {
        HighEnergyDataBuilder builder = new HighEnergyDataBuilder();
        builder.reset(10000);

        DmSegMobileReply segment = new DmSegMobileReply();
        segment.elems.add(elem(-1));
        segment.elems.add(elem(10000));

        assertFalse(builder.addSegment(segment));
        assertEquals(1, builder.snapshot().events.length);
        assertEquals(0.0f, builder.snapshot().events[0], 0.001f);
    }

    private DanmakuElem elem(int progress) {
        DanmakuElem elem = new DanmakuElem();
        elem.progress = progress;
        return elem;
    }
}
