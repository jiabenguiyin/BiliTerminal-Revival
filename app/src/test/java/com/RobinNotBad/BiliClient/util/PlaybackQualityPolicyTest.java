package com.RobinNotBad.BiliClient.util;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackQualityPolicyTest {
    @Test public void inheritedProfilesPreserveTheOriginalGlobalSetting(){
        assertEquals(64,PlaybackQualityPolicy.resolve(1,64,-1,-1));assertEquals(64,PlaybackQualityPolicy.resolve(2,64,-1,-1));
    }
    @Test public void cellularAndWifiAreIndependent(){assertEquals(16,PlaybackQualityPolicy.resolve(1,32,16,80));assertEquals(80,PlaybackQualityPolicy.resolve(2,32,16,80));}
    @Test public void unknownOrOfflineUsesTheGlobalSetting(){assertEquals(32,PlaybackQualityPolicy.resolve(0,32,16,80));assertEquals(32,PlaybackQualityPolicy.resolve(999,32,16,80));}
    @Test public void automaticIsDifferentFromFollowingTheGlobalSetting(){assertEquals(0,PlaybackQualityPolicy.resolve(1,64,0,-1));assertEquals(64,PlaybackQualityPolicy.resolve(2,64,0,-1));}
    @Test public void invalidProfileDoesNotBecomeAnUnsupportedRequest(){assertEquals(64,PlaybackQualityPolicy.resolve(1,64,999,80));}
    @Test public void invalidGlobalFallsBackToAutomatic(){assertEquals(0,PlaybackQualityPolicy.resolve(0,999,-1,-1));}
    @Test public void explicitManualSelectionWinsOverBothProfiles(){assertEquals(80,PlaybackQualityPolicy.request(80,1,32,16,64));assertEquals(16,PlaybackQualityPolicy.request(16,2,32,64,80));}
    @Test public void manualAutomaticRemainsAnExplicitChoice(){assertEquals(0,PlaybackQualityPolicy.request(0,1,32,16,80));}
    @Test public void aNewItemWithoutManualSelectionResolvesTheCurrentNetwork(){assertEquals(16,PlaybackQualityPolicy.request(-1,1,32,16,80));assertEquals(80,PlaybackQualityPolicy.request(-1,2,32,16,80));}
    @Test public void inheritedVipRequestRemainsAServerAndDevicePolicyDecision(){assertEquals(120,PlaybackQualityPolicy.resolve(2,120,-1,-1));}
    @Test public void invalidManualChoiceFallsBackToTheNetworkDefault(){assertEquals(16,PlaybackQualityPolicy.request(999,1,32,16,80));}
    @Test public void unsupportedSentinelsAreNeverTreatedAsRealQualities(){assertFalse(PlaybackQualityPolicy.valid(-1));assertFalse(PlaybackQualityPolicy.valid(999));assertTrue(PlaybackQualityPolicy.valid(0));}
}
