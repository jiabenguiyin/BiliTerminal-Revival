package com.RobinNotBad.BiliClient.player;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackExperienceTest {
    private String clips="[{\"clipType\":\"CLIP_TYPE_OP\",\"start\":1.25,\"end\":20.5},{\"clipType\":\"CLIP_TYPE_ED\",\"start\":80,\"end\":95}]";
    @Test public void officialSecondsBecomeMillisecondsAndOnlyRecognizedTypesSurvive(){
        assertEquals(1250,PlaybackSegments.parse(clips).get(0).startMs);assertEquals(20500,PlaybackSegments.parse(clips).get(0).endMs);
        assertEquals("片尾",PlaybackSegments.parse(clips).get(1).label());
        assertTrue(PlaybackSegments.parse("[{\"clipType\":\"OTHER\",\"start\":1,\"end\":20}]").isEmpty());
    }
    @Test public void malformedIntervalsAndHugeInputsNeverProduceSkips(){
        for(String value:new String[]{null,"broken","[]","[{\"clipType\":\"CLIP_TYPE_OP\",\"start\":-1,\"end\":2}]",
                "[{\"clipType\":\"CLIP_TYPE_OP\",\"start\":20,\"end\":20}]",
                "[{\"clipType\":\"CLIP_TYPE_OP\",\"start\":0,\"end\":86401}]"})assertTrue(PlaybackSegments.parse(value).isEmpty());
    }
    @Test public void segmentBoundsAreStartInclusiveEndExclusiveAndRequireRealDuration(){
        PlaybackSegments p=new PlaybackSegments(clips);assertNull(p.at(1249,100000));assertNotNull(p.at(1250,100000));
        assertNull(p.at(20500,100000));assertNull(p.at(2000,0));assertNull(p.at(2000,20000));
    }
    @Test public void eachSegmentAutomaticallySeeksAtMostOnceEvenAfterDecoderRollback(){
        PlaybackSegments p=new PlaybackSegments(clips);assertEquals(20500,p.automaticTarget(2000,100000,100,2));
        for(long t:new long[]{200,3000,30000,60000})assertEquals(-1,p.automaticTarget(2000,100000,t,2));
        assertEquals(95000,p.automaticTarget(81000,100000,61000,2));assertEquals(-1,p.automaticTarget(81000,100000,90000,2));
    }
    @Test public void manualAndOffNeverAutomaticAndNearEndIsNotInterrupted(){
        PlaybackSegments p=new PlaybackSegments(clips);assertEquals(-1,p.automaticTarget(2000,100000,0,0));
        assertEquals(-1,p.automaticTarget(2000,100000,0,1));assertEquals(-1,p.automaticTarget(19000,100000,0,2));
    }
    @Test public void refreshedQualityMetadataKeepsTheSameEpisodeSkipLatch(){
        PlaybackSegments p=new PlaybackSegments(clips);assertEquals(20500,p.automaticTarget(2000,100000,0,2));
        p.update(clips);assertEquals(-1,p.automaticTarget(2000,100000,10000,2));
        assertEquals(20500,new PlaybackSegments(clips).automaticTarget(2000,100000,10000,2));
    }
    @Test public void autoCooldownAlsoCoversOverlappingSegments(){
        PlaybackSegments p=new PlaybackSegments(clips);assertEquals(20500,p.automaticTarget(2000,100000,0,2));
        assertEquals(-1,p.automaticTarget(81000,100000,100,2));assertEquals(95000,p.automaticTarget(81000,100000,2100,2));
    }
    @Test public void disabledOrInvalidModeIsConservative(){assertEquals(1,PlaybackSegments.normalizeMode(-1));assertEquals(1,PlaybackSegments.normalizeMode(3));assertEquals(0,PlaybackSegments.normalizeMode(0));}
    @Test public void softwareGainSupportsPercentagesBetweenSystemVolumeSteps(){
        assertEquals(0.35f,PlaybackVolume.gain(true,35),0.0001f);assertEquals(1f,PlaybackVolume.gain(false,35),0f);
        assertEquals(35,PlaybackVolume.adjust(34,1,true));assertEquals(34,PlaybackVolume.adjust(35,1,false));
    }
    @Test public void gainAndSeekStepsAreBounded(){
        assertEquals(0f,PlaybackVolume.gain(true,-10),0f);assertEquals(1f,PlaybackVolume.gain(true,1000),0f);
        assertEquals(100,PlaybackVolume.adjust(99,5,true));assertEquals(0,PlaybackVolume.adjust(2,5,false));
        assertEquals(1,PlaybackVolume.seekSeconds(-1));assertEquals(120,PlaybackVolume.seekSeconds(1000));
        assertEquals(1,com.RobinNotBad.BiliClient.util.PlayerSettingsUtil.normalizeInt(PlaybackVolume.SEEK_SECONDS,-1));
        assertEquals(120,com.RobinNotBad.BiliClient.util.PlayerSettingsUtil.normalizeInt(PlaybackVolume.SEEK_SECONDS,1000));
    }
}
