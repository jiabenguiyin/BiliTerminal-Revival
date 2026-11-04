package com.RobinNotBad.BiliClient.player;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerSafeAreaPolicyTest {
    @Test public void circularPanelRadiusNeverConsumesBothSides(){assertEquals(0,PlayerSafeAreaPolicy.cornerInset(233,466,466,true,24));}
    @Test public void missingOemRoundFlagStillRejectsHalfScreenRadii(){assertEquals(0,PlayerSafeAreaPolicy.cornerInset(225,450,450,false,24));}
    @Test public void normalPhoneCornersRemainProtected(){assertEquals(40,PlayerSafeAreaPolicy.cornerInset(40,1080,1920,false,48));}
    @Test public void decorativeClearanceIsBoundedOnSmallWindows(){assertEquals(10,PlayerSafeAreaPolicy.cornerInset(60,200,200,false,24));assertEquals(16,PlayerSafeAreaPolicy.cornerInset(60,450,450,false,16));}
    @Test public void invalidOrUnknownDimensionsDoNotAddInsets(){for(int[] a:new int[][]{{-1,450,450},{20,0,450},{20,450,0}})assertEquals(0,PlayerSafeAreaPolicy.cornerInset(a[0],a[1],a[2],false,24));}
    @Test public void rotationUsesTheShorterViewportDimension(){assertEquals(PlayerSafeAreaPolicy.cornerInset(40,450,800,false,24),PlayerSafeAreaPolicy.cornerInset(40,800,450,false,24));}
}
