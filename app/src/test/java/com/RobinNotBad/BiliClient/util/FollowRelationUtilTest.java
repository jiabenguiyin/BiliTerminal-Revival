package com.RobinNotBad.BiliClient.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FollowRelationUtilTest {
    @Test
    public void mapsAllRelationshipStates() {
        assertEquals(FollowRelationUtil.NONE, FollowRelationUtil.getState(false, false));
        assertEquals(FollowRelationUtil.FOLLOW_BACK, FollowRelationUtil.getState(false, true));
        assertEquals(FollowRelationUtil.FOLLOWING, FollowRelationUtil.getState(true, false));
        assertEquals(FollowRelationUtil.MUTUAL, FollowRelationUtil.getState(true, true));

        assertEquals("关注", FollowRelationUtil.getButtonText(false, false));
        assertEquals("回关", FollowRelationUtil.getButtonText(false, true));
        assertEquals("已关注", FollowRelationUtil.getButtonText(true, false));
        assertEquals("互粉", FollowRelationUtil.getButtonText(true, true));
    }

    @Test
    public void parsesBilibiliFollowingAttributes() {
        assertFalse(FollowRelationUtil.isFollowingAttribute(0));
        assertTrue(FollowRelationUtil.isFollowingAttribute(1));
        assertTrue(FollowRelationUtil.isFollowingAttribute(2));
        assertTrue(FollowRelationUtil.isFollowingAttribute(6));
        assertFalse(FollowRelationUtil.isFollowingAttribute(128));

        assertEquals(4, FollowRelationUtil.getUnfollowAction(1));
        assertEquals(2, FollowRelationUtil.getUnfollowAction(2));
        assertEquals(2, FollowRelationUtil.getUnfollowAction(6));
        assertEquals("回关", FollowRelationUtil.getButtonText(0, 1));
        assertEquals("互粉", FollowRelationUtil.getButtonText(6, 6));
    }
}
