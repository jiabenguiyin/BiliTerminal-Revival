package com.RobinNotBad.BiliClient.api;
import org.json.JSONObject;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VideoReportApiTest {
    @Test public void reasonsUseActualIdsAndFlagRequiredProofInsteadOfGuessingCommentReasons()throws Exception{
        List<VideoReportApi.Reason> reasons=VideoReportApi.parseReasons(new JSONObject("{\"code\":0,\"data\":[{\"tid\":10013,\"name\":\"不良封面/标题\"},{\"tid\":8,\"name\":\"撞车\",\"controls\":[{\"required\":1}]},{\"tid\":10013,\"name\":\"duplicate\"},{\"tid\":4,\"name\":\"hidden\",\"state\":0}]}"));
        assertEquals(2,reasons.size());assertEquals(10013,reasons.get(0).id);assertFalse(reasons.get(0).officialRequired);assertTrue(reasons.get(1).officialRequired);
    }
    @Test public void emptyOrFailedReasonResponsesAreNotUsable()throws Exception{
        for(String json:new String[]{"{}","{\"code\":-101}","{\"code\":0}","{\"code\":0,\"data\":[]}"})
            try{VideoReportApi.parseReasons(new JSONObject(json));fail();}catch(java.io.IOException expected){}
    }
    @Test public void descriptionsAreRequiredBoundedAndOfficialUrlKeepsExactVideo(){
        assertFalse(VideoReportApi.validDescription(null));assertFalse(VideoReportApi.validDescription("  \n "));
        char[] chars=new char[500];Arrays.fill(chars,'中');assertTrue(VideoReportApi.validDescription(new String(chars)));assertFalse(VideoReportApi.validDescription(new String(chars)+"x"));
        assertEquals("https://www.bilibili.com/appeal/?avid=9876543210",VideoReportApi.officialUrl(9876543210L));
        try{VideoReportApi.officialUrl(0);fail();}catch(IllegalArgumentException expected){}
    }
}
