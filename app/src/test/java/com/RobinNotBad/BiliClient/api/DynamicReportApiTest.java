package com.RobinNotBad.BiliClient.api;
import org.junit.Test;
import okhttp3.FormBody;
import static org.junit.Assert.*;
public class DynamicReportApiTest {
    @Test public void reasonsHaveTheirOwnIdsIncludingOtherZero(){
        assertEquals(10,DynamicReportApi.reasons().size());assertEquals(4,DynamicReportApi.reasons().get(0).id);
        assertEquals("垃圾广告",DynamicReportApi.reasons().get(0).name);assertEquals(0,DynamicReportApi.reasons().get(9).id);
    }
    @Test public void exactDynamicAndAuthorRemainLongAndNeverBecomeVideoAid(){
        FormBody form=DynamicReportApi.form(123456789012345678L,9876543210L,0,"  具体问题  ","fixture-csrf");
        assertEquals("dynamic_id",form.name(0));assertEquals("123456789012345678",form.value(0));
        assertEquals("accused_uid",form.name(1));assertEquals("9876543210",form.value(1));assertEquals("0",form.value(2));
        assertEquals("具体问题",form.value(3));assertEquals("fixture-csrf",form.value(4));
        assertEquals("https://t.bilibili.com/123456789012345678",DynamicReportApi.officialUrl(123456789012345678L));
    }
    @Test public void rejectsUnrecognizedReasonsAndMissingTargetOrDescription(){
        for(int reason:new int[]{-1,10013,999})try{DynamicReportApi.form(12,42,reason,"描述","x");fail();}catch(IllegalArgumentException expected){}
        try{DynamicReportApi.form(0,42,4,"描述","x");fail();}catch(IllegalArgumentException expected){}
        try{DynamicReportApi.form(12,0,4,"描述","x");fail();}catch(IllegalArgumentException expected){}
        assertFalse(DynamicReportApi.validDescription("  "));
    }
}
