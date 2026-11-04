package com.RobinNotBad.BiliClient.api;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageApiUnreadTest {
    private JSONObject response(JSONObject data)throws JSONException{return new JSONObject().put("code",0).put("data",data);}
    @Test public void ordinaryPrivateMessagesStillAddUp()throws Exception {
        assertEquals(3,MessageApi.parsePrivateUnread(response(new JSONObject().put("follow_unread",2).put("unfollow_unread",1))));
    }
    @Test public void threeExtraBucketMessagesDoNotBecomeThreeOrdinaryUnreadMessages()throws Exception {
        assertEquals(0,MessageApi.parsePrivateUnread(response(new JSONObject().put("unfollow_push_msg",1).put("dustbin_unread",1).put("custom_unread",1))));
    }
    @Test public void extraCategoriesAreNotDoubleCountedWithOrdinaryUnread()throws Exception {
        assertEquals(2,MessageApi.parsePrivateUnread(response(new JSONObject().put("follow_unread",2).put("biz_msg_follow_unread",2).put("dustbin_push_msg",7))));
    }
    @Test public void negativeCountsAreIgnored()throws Exception {
        assertEquals(2,MessageApi.parsePrivateUnread(response(new JSONObject().put("follow_unread",-3).put("unfollow_unread",2))));
    }
    @Test public void largeServerCountsCannotOverflowIntoANegativeBadge()throws Exception {
        assertEquals(Integer.MAX_VALUE,MessageApi.parsePrivateUnread(response(new JSONObject().put("follow_unread",Long.MAX_VALUE).put("unfollow_unread",Integer.MAX_VALUE))));
    }
    @Test public void repliesMentionsLikesAndSystemNotificationsAllCount()throws Exception {
        assertEquals(303,MessageApi.countInteractionUnread(new JSONObject().put("at",1).put("reply",2).put("like",200).put("sys_msg",100)));
    }
    @Test public void emptySuccessfulDataMeansNoUnread()throws Exception {assertEquals(0,MessageApi.parsePrivateUnread(response(new JSONObject())));}
    @Test(expected=JSONException.class) public void failedBusinessCodeCannotBeCountedAsUnread()throws Exception {
        MessageApi.parsePrivateUnread(new JSONObject().put("code",-101).put("data",new JSONObject().put("follow_unread",3)));
    }
    @Test(expected=JSONException.class) public void missingDataIsAnUnknownResultNotAValidCount()throws Exception {
        MessageApi.requireUnreadData(new JSONObject().put("code",0));
    }
    @Test(expected=JSONException.class) public void missingCodeIsNotSuccess()throws Exception {
        MessageApi.requireUnreadData(new JSONObject().put("data",new JSONObject()));
    }
    @Test public void normalizedSystemAliasDoesNotDoubleCountTheRawField()throws Exception {
        assertEquals(3,MessageApi.countInteractionUnread(new JSONObject().put("system",3).put("sys_msg",3)));
    }
    @Test public void interactionSumSaturatesInsteadOfOverflowing()throws Exception {
        assertEquals(Integer.MAX_VALUE,MessageApi.countInteractionUnread(new JSONObject().put("reply",Integer.MAX_VALUE).put("like",Integer.MAX_VALUE)));
    }
}
