package com.RobinNotBad.BiliClient.player;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class CollectionPlaybackQueueTest {
    private CollectionPlaybackQueue queue(long... ids) { return new CollectionPlaybackQueue(19, ids); }

    @Test public void sequentialUsesCollectionOrderAndStopsAtEnd() {
        CollectionPlaybackQueue q = queue(30, 10, 20);
        assertEquals(10, q.next(30, 0, null));
        assertEquals(20, q.next(10, 0, null));
        assertEquals(-1, q.next(20, 0, null));
        assertEquals(-1, q.next(999, 0, null));
    }
    @Test public void duplicateAndInvalidEntriesCannotLoopSequentialPlayback() {
        CollectionPlaybackQueue q = queue(0, 30, 10, 30, -1, 20);
        assertArrayEquals(new long[]{30,10,20}, q.toArray());
        assertEquals(3, q.size());
        assertFalse(q.contains(-1));
    }
    @Test public void randomNeverImmediatelyRepeatsWhenThereAreAlternatives() {
        CollectionPlaybackQueue q = queue(30,10,20);
        Random random = new Random(19);
        for (long current : q.toArray()) for (int i=0; i<100; i++) {
            long next = q.next(current, 1, random);
            assertNotEquals(current, next);
            assertTrue(q.contains(next));
        }
    }
    @Test public void repeatOneStaysWithinTheCurrentCollection() {
        assertEquals(10, queue(10,20).next(10,2,null));
        assertEquals(-1, queue(10,20).next(99,2,null));
        assertEquals(10, queue(10).next(10,1,new Random()));
        assertEquals(-1, queue(10).next(10,0,null));
    }
    @Test public void emptyAndLargeIdentifiersAreSafeAndModeDefaultsToSequential() {
        assertEquals(-1, queue().next(10,0,null));
        assertEquals(9000000000L, queue(8000000000L,9000000000L).next(8000000000L,99,null));
        assertEquals(1, CollectionPlaybackQueue.nextMode(0));
        assertEquals(2, CollectionPlaybackQueue.nextMode(1));
        assertEquals(3, CollectionPlaybackQueue.nextMode(2));
        assertEquals(0, CollectionPlaybackQueue.nextMode(3));
    }

    @Test public void explicitNextOverridesRepeatOneWithoutChangingAutomaticRepeat() {
        CollectionPlaybackQueue q=queue(10,20,30);q.recordPlayed(10,false);
        assertEquals(10,q.next(10,CollectionPlaybackQueue.REPEAT_ONE,null));
        assertEquals(20,q.nextForUser(10,CollectionPlaybackQueue.REPEAT_ONE,null));
        assertTrue(q.canSkipNext(10,CollectionPlaybackQueue.REPEAT_ONE));
        assertFalse(q.canSkipNext(30,CollectionPlaybackQueue.REPEAT_ONE));
    }
    @Test public void randomPreviousRetracesActualPlaybackAndNextCanGoForwardAgain() {
        CollectionPlaybackQueue q=queue(10,20,30,40);
        q.recordPlayed(10,false);q.recordPlayed(40,false);q.recordPlayed(20,false);
        assertEquals(40,q.previousForUser(20));q.recordPlayed(40,true);
        assertEquals(10,q.previousForUser(40));
        assertEquals(20,q.nextForUser(40,CollectionPlaybackQueue.SHUFFLE,new Random()));
        q.recordPlayed(20,false);assertEquals(40,q.previousForUser(20));
    }
    @Test public void startingInMiddleCanGoBackRepeatedlyAndThenForward() {
        CollectionPlaybackQueue q=queue(10,20,30);q.recordPlayed(30,false);
        assertEquals(20,q.previousForUser(30));q.recordPlayed(20,true);
        assertEquals(10,q.previousForUser(20));q.recordPlayed(10,true);
        assertEquals(-1,q.previousForUser(10));
        assertEquals(20,q.nextForUser(10,0,null));
    }
    @Test public void planningWithoutACommitDoesNotMoveHistoryOnLoadFailure() {
        CollectionPlaybackQueue q=queue(10,20,30);q.recordPlayed(10,false);q.recordPlayed(20,false);
        assertEquals(10,q.previousForUser(20));
        assertEquals(10,q.previousForUser(20));
        assertEquals(30,q.nextForUser(20,0,null));
        q.recordPlayed(20,false);assertEquals(10,q.previousForUser(20));
    }

    @Test public void reverseUsesListOrderAndStopsAtItsBeginning() {
        CollectionPlaybackQueue q = queue(30, 10, 20);
        assertEquals(10, q.next(20, CollectionPlaybackQueue.REVERSE, null));
        assertEquals(30, q.next(10, CollectionPlaybackQueue.REVERSE, null));
        assertEquals(-1, q.next(30, CollectionPlaybackQueue.REVERSE, null));
        assertTrue(q.canSkipNext(20, CollectionPlaybackQueue.REVERSE));
        assertFalse(q.canSkipNext(30, CollectionPlaybackQueue.REVERSE));
    }

    @Test public void reversePreviousUsesActualHistoryOrOppositeListDirection() {
        CollectionPlaybackQueue q = queue(30, 10, 20); q.recordPlayed(10, false);
        assertEquals(20, q.previousForUser(10, CollectionPlaybackQueue.REVERSE));
        assertEquals(-1, q.previousForUser(20, CollectionPlaybackQueue.REVERSE));
        q.recordPlayed(30, false);
        assertEquals(10, q.previousForUser(30, CollectionPlaybackQueue.REVERSE));
        q.recordPlayed(10, true);
        assertEquals(30, q.nextForUser(10, CollectionPlaybackQueue.REVERSE, null));
    }
}
