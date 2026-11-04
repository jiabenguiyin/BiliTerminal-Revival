package com.RobinNotBad.BiliClient.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackResumePointsTest {
    @Test public void returningToAnItemUsesTheSessionPositionInsteadOfStaleHistory() {
        PlaybackResumePoints points = new PlaybackResumePoints();
        points.remember(1, 10, 137000, 262000, false);
        points.remember(2, 20, 5000, 181000, false);
        assertEquals(137000, points.position(1, 10, 10, 0));
        assertEquals(5000, points.position(2, 20, 20, 2000));
    }
    @Test public void differentPartsNeverBorrowThePreviousPartsPosition() {
        PlaybackResumePoints points = new PlaybackResumePoints();
        points.remember(1, 10, 137000, 262000, false);
        assertEquals(0, points.position(1, 11, 10, 137000));
        assertEquals(9000, points.position(1, 11, 11, 9000));
        assertEquals(0, points.position(2, 10, 0, 137000));
    }
    @Test public void finishedItemsStartAgainInsteadOfRestoringTheEndOrOldServerProgress() {
        PlaybackResumePoints points = new PlaybackResumePoints();
        points.remember(1, 10, 261950, 262000, true);
        assertEquals(0, points.position(1, 10, 10, 137000));
    }
    @Test public void recreationRestoresAllPartIdentitiesAndTheirPositions() {
        PlaybackResumePoints points = new PlaybackResumePoints();
        points.remember(1, 10, 137000, 262000, false); points.remember(1, 11, 5000, 181000, false);
        PlaybackResumePoints restored = new PlaybackResumePoints(); restored.restore(points.keys(), points.values());
        assertEquals(137000, restored.position(1, 10, 0, 0)); assertEquals(5000, restored.position(1, 11, 0, 0));
    }
    @Test public void storageIsBoundedAndInvalidOrOversizedPositionsAreSafe() {
        PlaybackResumePoints points = new PlaybackResumePoints();
        for (int i = 1; i <= 140; i++) points.remember(i, i, 100, 80, false);
        assertEquals(128, points.keys().length); assertEquals(0, points.position(1, 1, 0, 0));
        assertEquals(80, points.position(140, 140, 0, 0));
        points.remember(-1, 1, 0, 0, false); points.remember(2, 2, -1, 0, false);
        assertEquals(128, points.keys().length);
        points.restore(new String[]{"bad", "1:2"}, new long[]{100, Long.MAX_VALUE});
        assertEquals(1, points.keys().length); assertEquals(Integer.MAX_VALUE, points.position(1, 2, 0, 0));
    }
}
