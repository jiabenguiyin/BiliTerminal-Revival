package com.RobinNotBad.BiliClient.util;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageTargetTest {
    @Test public void nativeVideoPreservesRootAndChildAndDoesNotUseCommentAsOid()throws Exception{
        JSONObject item=new JSONObject().put("type","reply").put("business_id",0).put("item_id",900)
                .put("native_uri","bilibili://video/123?comment_root_id=321&comment_secondary_id=654")
                .put("uri","https://www.bilibili.com/video/BV1i4411Q7Py/");
        MessageTarget target=MessageTarget.like(item);
        assertEquals("reply",target.kind);assertEquals(1,target.type);assertEquals(123,target.oid);
        assertEquals(321,target.root);assertEquals(654,target.reply);assertEquals("BV1i4411Q7Py",target.bvid);
    }
    @Test public void nativeCommentLinkWinsOverStaleItemFields()throws Exception{
        MessageTarget target=MessageTarget.like(new JSONObject().put("type","reply").put("subject_id",888)
                .put("source_id",999).put("item_id",777).put("root_id",666)
                .put("native_uri","bilibili://comment/detail/1/123/321/?anchor=654"));
        assertEquals(123,target.oid);assertEquals(321,target.root);assertEquals(654,target.reply);
    }
    @Test public void commentFoldKeepsPhotoOidSeparateFromDynamicId()throws Exception{
        MessageTarget target=MessageTarget.like(new JSONObject().put("native_uri",
                "bilibili://comment/msg_fold/11/123/321/654/?enterUri=bilibili%3A%2F%2Ffollowing%2Fdetail%2F987"));
        assertEquals("reply",target.kind);assertEquals(11,target.type);assertEquals(123,target.oid);
        assertEquals(987,target.contentId);assertEquals(654,target.reply);
    }
    @Test public void legacyWebAnchorCanSeekWithoutInventingAnOid()throws Exception{
        MessageTarget target=MessageTarget.like(new JSONObject().put("type","reply").put("item_id",456)
                .put("uri","https://www.bilibili.com/video/BV1i4411Q7Py/?from=message#reply789"));
        assertEquals("BV1i4411Q7Py",target.bvid);assertEquals(789,target.reply);
        assertEquals(0,target.oid);assertEquals(0,target.root);
    }
    @Test public void oldExplicitReplyFieldsRemainUsable()throws Exception{
        MessageTarget target=MessageTarget.like(new JSONObject().put("type","reply").put("subject_id",123)
                .put("item_id",444).put("source_id",654).put("root_id",321)
                .put("uri","https://www.bilibili.com/video/BV1i4411Q7Py"));
        assertEquals(123,target.oid);assertEquals(321,target.root);assertEquals(654,target.reply);
    }
    @Test public void plainVideoKeepsTheBvPrefixAndHasNoCommentTarget()throws Exception{
        MessageTarget target=MessageTarget.like(new JSONObject().put("type","video").put("item_id",123)
                .put("uri","https://www.bilibili.com/video/BV1i4411Q7Py/"));
        assertEquals("video",target.kind);assertEquals("BV1i4411Q7Py",target.bvid);
        assertEquals(123,target.oid);assertEquals(0,target.reply);
    }
    @Test public void videoNavigationUsesBvidWhenNumericSubjectMayBeStale(){
        assertEquals(0,MessageTarget.aidForVideoNavigation(123,"BV1i4411Q7Py"));
        assertEquals(123,MessageTarget.aidForVideoNavigation(123,""));
        assertEquals(0,MessageTarget.aidForVideoNavigation(-1,null));
    }
    @Test public void h5CommentPageCarriesTheCorrectReplyTypeAndAnchor(){
        MessageTarget target=MessageTarget.fromUri("https://www.bilibili.com/h5/comment/sub?oid=123&pageType=1&root=321&comment_secondary_id=654");
        assertEquals("reply",target.kind);assertEquals(1,target.type);assertEquals(123,target.oid);
        assertEquals(321,target.root);assertEquals(654,target.reply);
    }
    @Test public void foreignAndMalformedLinksDoNotCreateTargets(){
        assertEquals("",MessageTarget.fromUri("https://bilibili.com.evil.invalid/video/BV1i4411Q7Py/#reply123").kind);
        assertEquals("",MessageTarget.fromUri("javascript:alert(1)").kind);
        assertEquals("",MessageTarget.fromUri("bilibili://video/not-a-video").kind);
        assertEquals("",MessageTarget.fromUri("%broken").kind);
    }
}
