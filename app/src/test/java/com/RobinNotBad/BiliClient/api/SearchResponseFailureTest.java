package com.RobinNotBad.BiliClient.api;

import org.json.*;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class SearchResponseFailureTest {
    @Test public void voucherWithoutSeidIsVerificationForAllSearchTypes() throws Exception {
        JSONObject response = new JSONObject().put("code",0).put("data",new JSONObject().put("v_voucher","fixture"));
        try { SearchApi.parseSearchData(response); fail(); }
        catch(SearchException error) { assertEquals(SearchException.Reason.VERIFICATION,error.reason); }
        for(String type:new String[]{SearchApi.TYPE_BANGUMI,SearchApi.TYPE_FILM}) {
            try { SearchApi.parseMediaSearchResponse(response,1,type); fail(); }
            catch(SearchException error) { assertEquals(SearchException.Reason.VERIFICATION,error.reason); }
        }
    }
    @Test public void businessFailuresCannotBecomeEmptyResults() throws Exception {
        for(int code:new int[]{-352,-412,-509,-101,-400}) {
            try { SearchApi.parseSearchData(new JSONObject().put("code",code)); fail(); }
            catch(SearchException error) {
                assertEquals(code==-101?SearchException.Reason.LOGIN:code==-400?SearchException.Reason.SERVICE:
                        SearchException.Reason.VERIFICATION,error.reason);
            }
        }
    }
    @Test public void realResultsMayOmitOptionalSeidButMalformedDataStillFails() throws Exception {
        JSONObject data=new JSONObject().put("result",new JSONArray());
        assertEquals(data,SearchApi.parseSearchData(new JSONObject().put("code",0).put("data",data)));
        try { SearchApi.parseSearchData(new JSONObject().put("code",0).put("data",new JSONObject())); fail(); }
        catch(SearchException error) { assertEquals(SearchException.Reason.CONTENT,error.reason); }
        assertEquals(0,SearchApi.parseSearchData(new JSONObject().put("code",0).put("data",
                new JSONObject().put("numResults",0))).getInt("numResults"));
    }
    @Test public void networkAndVerificationHaveDifferentUserMessages() {
        assertTrue(SearchException.userMessage(new IOException()).contains("网络"));
        SearchException error=new SearchException(SearchException.Reason.VERIFICATION,"需要安全验证");
        assertEquals("需要安全验证",SearchException.userMessage(error));
        assertFalse(SearchException.userMessage(new JSONException("No value for seid")).contains("登录"));
    }
}
