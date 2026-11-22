package com.RobinNotBad.BiliClient.util;
import com.RobinNotBad.BiliClient.model.MessageCard;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class MessageSectionsTest {
    private JSONObject data()throws Exception{return new JSONObject("{\"latest\":{\"items\":[{\"id\":3},{\"id\":2}]},\"total\":{\"items\":[{\"id\":2},{\"id\":1}],\"cursor\":{\"is_end\":false,\"id\":1,\"time\":50}}}");}
    @Test public void mergesLatestBeforeHistoryAndKeepsTheHistoricalCursor()throws Exception {
        MessageSections.Likes page=MessageSections.likes(data(),true);
        assertTrue(page.known);assertEquals(3,page.items.length());assertEquals(3,page.items.getJSONObject(0).getLong("id"));
        assertTrue(page.latestIds.contains(2L));assertFalse(page.latestIds.contains(1L));assertEquals(1,page.cursor.getLong("id"));
    }
    @Test public void paginationDoesNotReinsertTheLatestSection()throws Exception {
        MessageSections.Likes page=MessageSections.likes(data(),false);
        assertEquals(2,page.items.length());assertTrue(page.latestIds.isEmpty());
    }
    @Test public void missingReadBoundaryDoesNotInventAnUnreadStatus()throws Exception {
        MessageCard card=new MessageCard();card.timeStamp=100;
        MessageSections.applyBoundary(card,new JSONObject());assertFalse(card.readStateKnown);
        MessageSections.applyBoundary(card,new JSONObject().put("last_view_at",90));assertTrue(card.isNew);
        MessageSections.applyBoundary(card,new JSONObject().put("last_view_at",100));assertFalse(card.isNew);
    }
    @Test public void genuineEmptySectionsAreAllowedButMissingDataIsNot()throws Exception {
        assertEquals(0,MessageSections.likes(new JSONObject("{\"latest\":{\"items\":[]},\"total\":{\"items\":[]}}"),true).items.length());
        try{MessageSections.likes(new JSONObject(),true);fail();}catch(JSONException expected){}
    }
}
