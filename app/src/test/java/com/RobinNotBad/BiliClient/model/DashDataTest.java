package com.RobinNotBad.BiliClient.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.json.JSONException;
import org.json.JSONObject;

public class DashDataTest {
    @Test
    public void highFrameRateQualityDoesNotFallBackToOrdinaryStream() {
        DashData data = new DashData();
        DashVideoStream ordinary = video(80, 1920, 1080, "30");
        DashVideoStream highFrameRate = video(116, 1920, 1080, "60");
        data.videoStreams.add(ordinary);
        data.videoStreams.add(highFrameRate);

        assertEquals(highFrameRate, data.getVideoStream(116));
    }

    @Test
    public void stale1080p60IdFallsBackWhenReturnedStreamIs30Fps() {
        DashData data = new DashData();
        DashVideoStream staleMemberStream = video(116, 1920, 1080, "30");
        DashVideoStream ordinary = video(80, 1920, 1080, "30");
        data.videoStreams.add(staleMemberStream);
        data.videoStreams.add(ordinary);

        assertEquals(ordinary, data.getVideoStream(116));
    }

    @Test
    public void fourKQualitySelectsUltraHdStream() {
        DashData data = new DashData();
        DashVideoStream fullHd = video(116, 1920, 1080, "60");
        DashVideoStream fourK = video(120, 3840, 2160, "60");
        data.videoStreams.add(fullHd);
        data.videoStreams.add(fourK);

        assertEquals(fourK, data.getVideoStream(120));
    }

    @Test
    public void staleFourKIdFallsBackWhenReturnedStreamIsFullHd() {
        DashData data = new DashData();
        DashVideoStream staleMemberStream = video(120, 1920, 1080, "60");
        DashVideoStream ordinary = video(80, 1920, 1080, "30");
        data.videoStreams.add(staleMemberStream);
        data.videoStreams.add(ordinary);

        assertEquals(ordinary, data.getVideoStream(120));
    }

    @Test
    public void automaticQualitySelectsHighestAvailableStream() {
        DashData data = new DashData();
        DashVideoStream fullHd = video(80, 1920, 1080, "30");
        DashVideoStream fourK = video(120, 3840, 2160, "60");
        data.videoStreams.add(fullHd);
        data.videoStreams.add(fourK);

        assertEquals(fourK, data.getVideoStream(0));
        assertEquals(fourK, data.getVideoStream(127));
    }

    @Test
    public void lockedQualityFallsBackThroughTheQualityOrder() {
        DashData data = new DashData();
        data.videoStreams.add(video(32, 854, 480, "30"));

        assertEquals(32, data.getVideoStream(116).id);
    }

    @Test
    public void fourKFallsBackTo1080p60Before1080p() {
        DashData data = new DashData();
        DashVideoStream fullHd = video(80, 1920, 1080, "30");
        DashVideoStream fullHd60 = video(116, 1920, 1080, "60");
        data.videoStreams.add(fullHd);
        data.videoStreams.add(fullHd60);

        assertEquals(fullHd60, data.getVideoStream(120));
    }

    @Test
    public void fourKFallsBackTo1080pPlusAfter1080p60() {
        DashData data = new DashData();
        DashVideoStream fullHd = video(80, 1920, 1080, "30");
        DashVideoStream fullHdPlus = video(112, 1920, 1080, "30");
        data.videoStreams.add(fullHd);
        data.videoStreams.add(fullHdPlus);

        assertEquals(fullHdPlus, data.getVideoStream(120));
    }

    @Test
    public void automaticQualityNeverSelectsFormatsAboveFourK() {
        DashData data = new DashData();
        DashVideoStream fourK = video(120, 3840, 2160, "60");
        DashVideoStream eightK = video(127, 7680, 4320, "60");
        DashVideoStream hdr = video(125, 3840, 2160, "60");
        DashVideoStream dolby = video(126, 3840, 2160, "60");
        data.videoStreams.add(eightK);
        data.videoStreams.add(hdr);
        data.videoStreams.add(dolby);
        data.videoStreams.add(fourK);

        assertEquals(fourK, data.getVideoStream(0));
        assertEquals(fourK, data.getVideoStream(127));
    }

    @Test
    public void automaticQualityIgnoresStaleHighTierIds() {
        DashData data = new DashData();
        data.videoStreams.add(video(120, 1920, 1080, "30"));
        DashVideoStream fullHd = video(80, 1920, 1080, "30");
        data.videoStreams.add(fullHd);

        assertEquals(fullHd, data.getVideoStream(0));
    }

    @Test
    public void supportedQualityLabelsAreCanonical() {
        assertEquals("1080P+", DashData.getQualityLabel(DashData.QN_1080P_PLUS));
        assertEquals("1080P 60帧", DashData.getQualityLabel(DashData.QN_1080P_60));
        assertEquals("4K", DashData.getQualityLabel(DashData.QN_4K));
    }

    @Test
    public void legacyAndSpecialQualityRequestsAreCappedAtFourK() {
        assertEquals(DashData.QN_4K, DashData.normalizeRequestedQuality(127));
        assertEquals(DashData.QN_4K, DashData.normalizeRequestedQuality(125));
        assertEquals(DashData.QN_4K, DashData.normalizeRequestedQuality(126));
        assertEquals(DashData.QN_1080P_PLUS, DashData.normalizeRequestedQuality(112));
    }

    @Test
    public void progressiveQualityRequestsKeepTheirLegacyValues() {
        assertEquals(DashData.QN_360P, DashData.normalizeRequestedQuality(DashData.QN_360P));
        assertEquals(DashData.QN_720P, DashData.normalizeRequestedQuality(DashData.QN_720P));
    }

    @Test
    public void fourKFallsBackTo720p60Before720pAnd480p() {
        DashData data = new DashData();
        DashVideoStream p480 = video(32, 854, 480, "30");
        DashVideoStream p720 = video(64, 1280, 720, "30");
        DashVideoStream p72060 = video(74, 1280, 720, "60");
        data.videoStreams.add(p480);
        data.videoStreams.add(p720);
        data.videoStreams.add(p72060);

        assertEquals(p72060, data.getVideoStream(120));
        data.videoStreams.remove(p72060);
        assertEquals(p720, data.getVideoStream(120));
        data.videoStreams.remove(p720);
        assertEquals(p480, data.getVideoStream(120));
    }

    @Test
    public void compatibleAudioPrefersAacOverUnsupportedCodec() {
        DashData data = new DashData();
        DashAudioStream opus = audio(30280, 192000, "opus", "audio/webm");
        DashAudioStream aac = audio(30232, 132000, "mp4a.40.2", "audio/mp4");
        data.audioStreams.add(opus);
        data.audioStreams.add(aac);

        assertEquals(aac, data.getBestCompatibleAudioStream());
    }

    @Test
    public void malformedDolbyAudioValueDoesNotCrashDashParsing() throws JSONException {
        JSONObject json = new JSONObject()
                .put("video", new org.json.JSONArray())
                .put("audio", new org.json.JSONArray())
                .put("dolby", new JSONObject().put("type", 1).put("audio", "unavailable"));

        DashData data = DashData.fromJson(json);

        assertNull(data.dolbyAudio);
    }

    private DashVideoStream video(int id, int width, int height, String frameRate) {
        DashVideoStream stream = new DashVideoStream();
        stream.id = id;
        stream.width = width;
        stream.height = height;
        stream.frameRate = frameRate;
        stream.codecid = 7;
        return stream;
    }

    private DashAudioStream audio(int id, long bandwidth, String codecs, String mimeType) {
        DashAudioStream stream = new DashAudioStream();
        stream.id = id;
        stream.bandwidth = bandwidth;
        stream.codecs = codecs;
        stream.mimeType = mimeType;
        return stream;
    }
}
