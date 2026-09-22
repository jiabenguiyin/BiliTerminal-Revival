package com.RobinNotBad.BiliClient.player;

import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.util.RequestCancellation;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DanmakuWindowLoaderTest {
    private static final long HUNDRED_HOURS = 360000000L;
    private final QueueExecutor executor = new QueueExecutor();
    private final List<Integer> requests = new ArrayList<>();
    private final List<Map<Integer, DmSegMobileReply>> snapshots = new ArrayList<>();
    private final long[] now = {0L};

    private DanmakuWindowLoader loader(DanmakuWindowLoader.Source source) {
        return new DanmakuWindowLoader(executor, source,
                (revision, segments) -> snapshots.add(segments), () -> now[0]);
    }

    private DmSegMobileReply load(int index, RequestCancellation cancellation) {
        requests.add(index);
        return new DmSegMobileReply();
    }

    @Test public void hundredHoursPublishesCurrentBeforePrefetchAndNeverLoadsEntireVideo() {
        DanmakuWindowLoader loader = loader(this::load);
        loader.update(0L, HUNDRED_HOURS);
        executor.run();
        assertEquals(Arrays.asList(1, 2), requests);
        assertTrue(snapshots.get(1).containsKey(1));
        assertEquals(1, snapshots.get(1).size());
        assertEquals(2, snapshots.get(2).size());
    }

    @Test public void eightyHourSeekStartsAtSegment801AndBoundsCache() {
        DanmakuWindowLoader loader = loader(this::load);
        loader.update(0L, HUNDRED_HOURS);
        executor.run();
        loader.update(288000000L, HUNDRED_HOURS);
        executor.run();
        assertEquals(Arrays.asList(1, 2, 801, 802, 800), requests);
        Map<Integer, DmSegMobileReply> last = snapshots.get(snapshots.size() - 1);
        assertEquals(3, last.size());
        assertFalse(last.containsKey(1));
        assertTrue(last.containsKey(801));
    }

    @Test public void repeatedTicksAndEmptySegmentsDoNotRepeatRequests() {
        DanmakuWindowLoader loader = loader(this::load);
        loader.update(0L, HUNDRED_HOURS);
        loader.update(1000L, HUNDRED_HOURS);
        assertEquals(1, executor.tasks.size());
        executor.run();
        for (int i = 0; i < 100; i++) loader.update(i * 1000L, HUNDRED_HOURS);
        assertEquals(0, executor.tasks.size());
        assertEquals(Arrays.asList(1, 2), requests);
    }

    @Test public void crossingBoundaryReusesPrefetchAndKeepsOnlyThreeSegments() {
        DanmakuWindowLoader loader = loader(this::load);
        for (int i = 0; i < 12; i++) {
            loader.update(i * DanmakuWindowLoader.SEGMENT_MS, HUNDRED_HOURS);
            executor.run();
        }
        assertEquals(13, requests.size());
        for (Map<Integer, DmSegMobileReply> snapshot : snapshots) assertTrue(snapshot.size() <= 3);
    }

    @Test public void failuresRetryAfterBackoffWithoutBlockingOtherSegments() {
        DanmakuWindowLoader loader = loader((index, cancellation) -> {
            requests.add(index);
            if (index == 1 && now[0] == 0L) throw new IOException("offline");
            return new DmSegMobileReply();
        });
        loader.update(0L, HUNDRED_HOURS);
        executor.run();
        assertEquals(Arrays.asList(1, 1, 2), requests);
        loader.update(0L, HUNDRED_HOURS);
        assertTrue(executor.tasks.isEmpty());
        now[0] = 15000L;
        loader.update(0L, HUNDRED_HOURS);
        executor.run();
        assertEquals(Arrays.asList(1, 1, 2, 1), requests);
    }

    @Test public void closeCancelsActiveRequestAndDropsItsResult() {
        AtomicReference<DanmakuWindowLoader> ref = new AtomicReference<>();
        DanmakuWindowLoader loader = loader((index, cancellation) -> {
            requests.add(index);
            ref.get().close();
            assertTrue(cancellation.isCancelled());
            return new DmSegMobileReply();
        });
        ref.set(loader);
        loader.update(0L, HUNDRED_HOURS);
        executor.run();
        loader.update(288000000L, HUNDRED_HOURS);
        assertEquals(Arrays.asList(1), requests);
        assertEquals(1, snapshots.size());
        assertFalse(loader.isCurrent(1L));
    }

    @Test public void seekDuringRequestCancelsOldResultAndPrioritizesDestination() {
        AtomicReference<DanmakuWindowLoader> ref = new AtomicReference<>();
        DanmakuWindowLoader loader = loader((index, cancellation) -> {
            requests.add(index);
            if (index == 1) {
                ref.get().update(288000000L, HUNDRED_HOURS);
                assertTrue(cancellation.isCancelled());
            }
            return new DmSegMobileReply();
        });
        ref.set(loader);
        loader.update(0L, HUNDRED_HOURS);
        executor.run();
        assertEquals(Arrays.asList(1, 801, 802, 800), requests);
        for (Map<Integer, DmSegMobileReply> snapshot : snapshots) assertFalse(snapshot.containsKey(1));
        assertFalse(loader.isCurrent(1L));
        assertTrue(loader.isCurrent(2L));
    }

    @Test public void endAndShortVideoNeverRequestOutOfRange() {
        DanmakuWindowLoader loader = loader(this::load);
        loader.update(HUNDRED_HOURS, HUNDRED_HOURS);
        executor.run();
        assertEquals(Arrays.asList(1000, 999), requests);
        requests.clear();
        loader.close();
        loader = loader(this::load);
        loader.update(-1L, 1000L);
        executor.run();
        assertEquals(Arrays.asList(1), requests);
    }

    private static final class QueueExecutor implements Executor {
        final List<Runnable> tasks = new ArrayList<>();
        @Override public void execute(Runnable runnable) { tasks.add(runnable); }
        void run() {
            while (!tasks.isEmpty()) tasks.remove(0).run();
        }
    }
}
