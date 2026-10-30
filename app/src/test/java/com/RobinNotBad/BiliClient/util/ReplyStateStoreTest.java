package com.RobinNotBad.BiliClient.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReplyStateStoreTest {
    private final ReplyStateStore store = new ReplyStateStore(4);

    private ReplyStateStore.State read(long stamp, int likes) {
        return store.observe(42, 1, 100, 200, 0, likes, false, false, 0, stamp);
    }
    private ReplyStateStore.State react(boolean like, boolean target) {
        return store.react(42, 1, 100, 200, 0, 2, false, false, 0, like, target);
    }

    @Test public void lateReadCannotUndoSuccessfulLike() {
        long old = store.beginRead(); read(old, 2);
        ReplyStateStore.State liked = react(true, true);
        assertEquals(3, liked.likes); assertTrue(liked.liked);
        ReplyStateStore.State late = read(old, 2);
        assertSame(liked, late);
    }
    @Test public void outOfOrderReadsKeepTheLaterRequest() {
        long first = store.beginRead(), second = store.beginRead();
        ReplyStateStore.State newer = read(second, 8);
        assertSame(newer, read(first, 2));
    }
    @Test public void freshReadCanReplaceOldCounts() {
        read(store.beginRead(), 2); react(true, true);
        ReplyStateStore.State fresh = read(store.beginRead(), 9);
        assertEquals(9, fresh.likes); assertFalse(fresh.liked);
    }
    @Test public void dislikeRemovesOneLikeAndNeverGoesNegative() {
        react(true, true);
        ReplyStateStore.State disliked = react(false, true);
        assertEquals(2, disliked.likes); assertFalse(disliked.liked); assertTrue(disliked.hated);
        assertEquals(2, react(false, false).likes);
        read(store.beginRead(), 0);
        assertEquals(0, react(true, false).likes);
    }
    @Test public void sameActionIsIdempotent() {
        assertEquals(3, react(true, true).likes);
        assertEquals(3, react(true, true).likes);
        assertEquals(2, react(true, false).likes);
        assertEquals(2, react(true, false).likes);
    }
    @Test public void accountsTypesAndObjectsAreIsolated() {
        read(store.beginRead(), 2);
        assertNull(store.get(43, 1, 100, 200));
        assertNull(store.get(42, 17, 100, 200));
        assertNull(store.get(42, 1, 101, 200));
    }
    @Test public void deletionCannotBeResurrectedByStaleUpstreamData() {
        read(store.beginRead(), 2);
        ReplyStateStore.State deleted = store.delete(42, 1, 100, 200, 0);
        assertSame(deleted, read(store.beginRead(), 2));
        assertTrue(react(true, true).deleted);
    }
    @Test public void duplicateMutationsAreBlockedAcrossPages() {
        assertTrue(store.beginMutation(42, 1, 100, 200));
        assertFalse(store.beginMutation(42, 1, 100, 200));
        assertTrue(store.beginMutation(43, 1, 100, 200));
        store.endMutation(42, 1, 100, 200);
        assertTrue(store.beginMutation(42, 1, 100, 200));
    }
    @Test public void storeEvictsOlderEntriesForSmallMemoryDevices() {
        for (int i = 0; i < 5; i++)
            store.observe(42, 1, 100, i, 0, i, false, false, 0, store.beginRead());
        assertNull(store.get(42, 1, 100, 0));
        assertEquals(4, store.get(42, 1, 100, 4).likes);
    }
}
