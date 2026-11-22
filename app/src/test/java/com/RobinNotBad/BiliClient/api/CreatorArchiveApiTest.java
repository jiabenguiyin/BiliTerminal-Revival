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

    @Test public void titleValidationRejectsEmptyOverlongAndControlCharacters() throws Exception {
        assertEquals("新标题",CreatorArchiveApi.validateTitle("  新标题  "));
        StringBuilder max=new StringBuilder();for(int i=0;i<80;i++)max.append("测");
        assertEquals(80,CreatorArchiveApi.validateTitle(max.toString()).length());
        for(String input:new String[]{"", "  ", max+"试", "标题\n换行", "标题\u0000异常"}){
            try{CreatorArchiveApi.validateTitle(input);fail();}catch(CreatorArchiveApi.Failure expected){assertEquals(-10005,expected.code);}
        }
    }
    private JSONObject titleData() throws Exception {
        return new JSONObject("{\"archive\":{\"aid\":1001,\"title\":\"原题\",\"state\":0,\"copyright\":1,\"tid\":17,"
                +"\"tag\":\"测试,手表\",\"cover\":\"//fixture/cover\",\"desc\":\"简介\",\"unknown_flag\":{\"enabled\":1}},"
                +"\"videos\":[{\"filename\":\"part-1\",\"title\":\"分P一\",\"extra\":1},{\"filename\":\"part-2\",\"title\":\"分P二\"}]}");
    }
    @Test public void titlePayloadPreservesUnknownMetadataAndAllPartsWithoutMutatingSource() throws Exception {
        JSONObject data=titleData(),payload=CreatorArchiveApi.titlePayload(data);
        payload.put("title","新题");
        assertEquals("原题",data.getJSONObject("archive").getString("title"));
        assertEquals(1,payload.getJSONObject("unknown_flag").getInt("enabled"));
        assertEquals(2,payload.getJSONArray("videos").length());
        assertEquals("分P二",payload.getJSONArray("videos").getJSONObject(1).getString("title"));
        payload.getJSONArray("videos").getJSONObject(0).put("title","仅测试副本");
        assertEquals("分P一",data.getJSONArray("videos").getJSONObject(0).getString("title"));
    }
    @Test public void incompleteOrSpecialEditingDataIsRejectedBeforeSubmission() throws Exception {
        for(String field:new String[]{"tid","tag","desc","cover","copyright"}){
            JSONObject data=titleData();data.getJSONObject("archive").remove(field);
            try{CreatorArchiveApi.titlePayload(data);fail(field);}catch(CreatorArchiveApi.Failure expected){}
        }
        JSONObject missingPart=titleData();missingPart.getJSONArray("videos").getJSONObject(1).remove("filename");
        try{CreatorArchiveApi.titlePayload(missingPart);fail();}catch(CreatorArchiveApi.Failure expected){}
        JSONObject interactive=titleData();interactive.getJSONObject("archive").put("interactive",1);
        try{CreatorArchiveApi.titlePayload(interactive);fail();}catch(CreatorArchiveApi.Failure expected){}
    }
    @Test public void editConflictCheckIgnoresKeyOrderButDetectsChangesToAnyPart() throws Exception {
        JSONObject before=titleData();JSONObject reordered=new JSONObject(before.toString());
        assertTrue(CreatorArchiveApi.sameJson(new JSONObject("{\"a\":1,\"b\":2}"),new JSONObject("{\"b\":2,\"a\":1}")));
        assertTrue(CreatorArchiveApi.sameJson(before,reordered));
        reordered.getJSONArray("videos").getJSONObject(1).put("title","其他端的新编辑");
        assertFalse(CreatorArchiveApi.sameJson(before,reordered));
    }
}
