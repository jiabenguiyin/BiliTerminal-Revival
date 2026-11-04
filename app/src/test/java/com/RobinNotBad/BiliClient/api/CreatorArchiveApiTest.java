package com.RobinNotBad.BiliClient.api;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CreatorArchiveApiTest {
    private JSONObject response(String rows, int page, int size, int count) throws Exception {
        return new JSONObject("{\"code\":0,\"data\":{\"page\":{\"pn\":"+page+",\"ps\":"+size+",\"count\":"+count+"},\"arc_audits\":"+rows+"}}");
    }
    private String row(long aid, String extra) {
        return "{\"Archive\":{\"aid\":"+aid+",\"title\":\"测试稿件\""+extra+"},\"stat\":{\"view\":110,\"like\":2}}";
    }
    @Test public void preservesLongIdAndRealStatsWithBoundedPagination() throws Exception {
        CreatorArchiveApi.Page first=CreatorArchiveApi.parsePage(response("["+row(9876543210L,",\"mid\":42,\"state\":0")+"]",1,1,2),42,1);
        assertTrue(first.hasMore);assertEquals(9876543210L,first.items.get(0).aid);
        assertEquals(42,first.items.get(0).accountMid);assertEquals(42,first.items.get(0).ownerMid);
        assertEquals(110,first.items.get(0).views);assertEquals(-1,first.items.get(0).favorites);
        assertFalse(CreatorArchiveApi.parsePage(response("["+row(2,"")+"]",2,1,2),42,2).hasMore);
    }
    @Test public void removesDuplicateIdsButDoesNotGuessStateOrOwnership() throws Exception {
        CreatorArchiveApi.Page page=CreatorArchiveApi.parsePage(response("["+row(1,"")+","+row(1,"")+"]",1,20,2),42,1);
        assertEquals(1,page.items.size());assertEquals(0,page.items.get(0).ownerMid);
        assertEquals(Integer.MIN_VALUE,page.items.get(0).state);assertEquals("状态待确认",page.items.get(0).stateText);
    }
    @Test public void acceptsGenuineEmptyAccount() throws Exception {
        CreatorArchiveApi.Page page=CreatorArchiveApi.parsePage(response("[]",1,20,0),42,1);
        assertTrue(page.items.isEmpty());assertFalse(page.hasMore);
        JSONObject omitted=response("[]",1,20,0);omitted.getJSONObject("data").remove("arc_audits");
        assertTrue(CreatorArchiveApi.parsePage(omitted,42,1).items.isEmpty());
    }
    @Test public void doesNotTurnMissingDataOrBadPaginationIntoEmptySuccess() throws Exception {
        for(String json:new String[]{"{}","{\"code\":0}","{\"code\":0,\"data\":{}}",
                "{\"code\":0,\"data\":{\"page\":{\"count\":3}}}",
                response("[]",2,20,3).toString(),response("[]",1,0,3).toString(),
                response("[{\"Archive\":{\"aid\":0}}]",1,20,1).toString()}) {
            try{CreatorArchiveApi.parsePage(new JSONObject(json),42,1);fail(json);}catch(CreatorArchiveApi.Failure expected){}
        }
    }
    @Test public void distinguishesLoginVerificationAndPermissionErrors() throws Exception {
        for(int code:new int[]{-101,-111,-403,340022,-352,-412,22015}) {
            try{CreatorArchiveApi.parsePage(new JSONObject("{\"code\":"+code+"}"),42,1);fail();}
            catch(CreatorArchiveApi.Failure error){assertEquals(code,error.code);assertFalse(error.getMessage().isEmpty());}
        }
    }
    @Test public void normalizesOnlyKnownRelativeCoverForms() throws Exception {
        for(String path:new String[]{"/bfs/archive/test.jpg","//i0.hdslb.com/test.jpg","javascript:bad"}) {
            String cover=CreatorArchiveApi.parsePage(response("["+row(1,",\"cover\":\""+path+"\"")+"]",1,20,1),42,1).items.get(0).cover;
            assertEquals(path.startsWith("/bfs/")?"https://i0.hdslb.com"+path:path.startsWith("//")?"https:"+path:"",cover);
        }
    }
}
