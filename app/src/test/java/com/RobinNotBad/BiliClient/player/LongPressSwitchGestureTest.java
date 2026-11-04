package com.RobinNotBad.BiliClient.player;
import org.junit.Test;
import static org.junit.Assert.*;

public class LongPressSwitchGestureTest {
    private LongPressSwitchGesture armed() { LongPressSwitchGesture g=new LongPressSwitchGesture(); g.arm(100,150,300,1,8); return g; }
    @Test public void fastLargeDownAndUpHaveTheRequestedDirections() {
        for(int sign:new int[]{1,-1}) { LongPressSwitchGesture g=armed(); assertEquals(0,g.update(100,150+sign*12,1000)); assertEquals(sign,g.update(100,150+sign*100,1120)); }
    }
    @Test public void idleHoldingTimeIsNotCountedAsSlowMovement() { LongPressSwitchGesture g=armed();assertEquals(0,g.update(100,152,90000));assertEquals(0,g.update(100,162,91000));assertEquals(1,g.update(100,250,91100)); }
    @Test public void onePhysicalTouchCanSwitchAtMostOnce() { LongPressSwitchGesture g=armed();assertEquals(1,g.update(100,250,1000));assertEquals(0,g.update(100,30,1100)); }
    @Test public void shortOrDiagonalMovementsAreRejected() { assertEquals(0,armed().update(100,190,1000));LongPressSwitchGesture g=armed();assertEquals(0,g.update(160,220,1000));assertEquals(0,g.update(100,270,1020)); }
    @Test public void slowDragNeverTurnsIntoASwitch() { LongPressSwitchGesture g=armed();g.update(100,162,1000);assertEquals(0,g.update(100,180,1400));assertEquals(0,g.update(100,270,1450)); }
    @Test public void insufficientVelocityDoesNotSwitchEvenInsideTheTimeLimit() { LongPressSwitchGesture g=armed();g.update(100,162,1000);assertEquals(0,g.update(100,226,1340)); }
    @Test public void cancelAndNewArmHaveIndependentLifetimes() { LongPressSwitchGesture g=armed();g.cancel();assertEquals(0,g.update(100,250,1000));g.arm(100,150,300,1,8);assertEquals(-1,g.update(100,40,2000)); }
    @Test public void denseSmallScreensKeepTheRequiredDistanceReachable() { LongPressSwitchGesture g=new LongPressSwitchGesture();g.arm(100,100,200,3,8);assertEquals(0,g.update(100,130,1000));assertEquals(1,g.update(100,185,1080)); }
}
