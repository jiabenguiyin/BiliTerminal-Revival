package com.RobinNotBad.BiliClient.util;
import org.json.JSONObject;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DiagnosticUploadOrderTest {
    private String event(String name,int id)throws Exception{return new JSONObject().put("name",name).put("id",id).toString();}
    private List<String> logs(int count)throws Exception{List<String> out=new ArrayList<>();for(int i=0;i<count;i++)out.add(event("debug_log",i));return out;}
    @Test public void cappedUploadContainsRecentContextAndLastPlayerGeometryDespiteDebugSpam()throws Exception{
        List<String> lines=logs(2000);lines.set(100,event("player_layout",100));lines.set(200,event("player_control_touch",200));
        List<String> out=DiagnosticUploadOrder.prioritize(lines,192);List<String> head=out.subList(0,192);
        assertTrue(head.contains(lines.get(100)));assertTrue(head.contains(lines.get(200)));assertTrue(head.contains(lines.get(1999)));
        assertFalse(head.contains(lines.get(0)));assertEquals(2000,out.size());assertEquals("debug_log",new JSONObject(lines.get(0)).getString("name"));
    }
    @Test public void reorderingNeverDropsOrDuplicatesAnyRecord()throws Exception{
        List<String> lines=logs(400);lines.set(100,lines.get(200));List<String> out=DiagnosticUploadOrder.prioritize(lines,192);
        ArrayList<String> a=new ArrayList<>(lines),b=new ArrayList<>(out);Collections.sort(a);Collections.sort(b);assertEquals(a,b);
    }
    @Test public void selectedAndRemainingRecordsKeepTheirOwnChronologicalOrder()throws Exception{
        List<String> lines=logs(400);lines.set(1,event("player_layout",1));List<String> out=DiagnosticUploadOrder.prioritize(lines,192);
        for(List<String> group:Arrays.asList(out.subList(0,192),out.subList(192,out.size()))){int previous=-1;
            for(String line:group){int id=new JSONObject(line).getInt("id");assertTrue(id>previous);previous=id;}}
    }
    @Test public void unlimitedOrSmallUploadRetainsOriginalOrder()throws Exception{
        List<String> lines=logs(10);assertEquals(lines,DiagnosticUploadOrder.prioritize(lines,0));assertEquals(lines,DiagnosticUploadOrder.prioritize(lines,192));
    }
    @Test public void malformedLinesAreKeptForExistingBatchParserToHandle()throws Exception{
        List<String> lines=logs(20);lines.set(5,"broken");List<String> out=DiagnosticUploadOrder.prioritize(lines,4);assertEquals(20,out.size());assertTrue(out.contains("broken"));
    }
    @Test public void tinyLimitsDoNotOverReserveCriticalEvents()throws Exception{
        List<String> lines=logs(20);lines.set(1,event("player_layout",1));lines.set(2,event("player_control_touch",2));
        List<String> out=DiagnosticUploadOrder.prioritize(lines,1);assertEquals(lines.get(1),out.get(0));assertEquals(lines.size(),out.size());
    }
    @Test public void cappedUploadKeepsActualNetworkAndSessionFailuresAheadOfRoutineTraffic()throws Exception{
        List<String> lines=logs(400);
        lines.set(20,new JSONObject().put("name","network_request").put("id",20)
                .put("details",new JSONObject().put("status",-1).put("error","SocketTimeoutException")).toString());
        lines.set(30,new JSONObject().put("name","private_sessions_page").put("id",30)
                .put("details",new JSONObject().put("code",-412)).toString());
        lines.set(40,event("reported_network_error",40));
        lines.set(50,new JSONObject().put("name","network_request").put("id",50)
                .put("details",new JSONObject().put("status",200)).toString());
        List<String> head=DiagnosticUploadOrder.prioritize(lines,192).subList(0,192);
        assertTrue(head.contains(lines.get(20)));assertTrue(head.contains(lines.get(30)));
        assertTrue(head.contains(lines.get(40)));assertFalse(head.contains(lines.get(50)));
    }
}
