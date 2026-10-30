package com.RobinNotBad.BiliClient.api;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.RobinNotBad.BiliClient.model.Reply;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReplyTopParsingInstrumentedTest {
    @Test public void topArraysMarkPinnedAndDoNotDuplicateNormalListEntries() throws Exception {
        JSONObject item = fixture();
        ArrayList<Reply> output = new ArrayList<>();
        ReplyApi.analyzeTopReplies(new JSONObject().put("top_replies", new JSONArray().put(item)), output);
        ReplyApi.analyzeReplyArray(true, new JSONArray().put(item), output);
        assertEquals(1, output.size());
        assertTrue(output.get(0).isTop);
        assertEquals(ReplyApi.TOP_TIP + "A comment", output.get(0).message.toString());
    }

    @Test public void upperTopAndNullTopListsAreHandled() throws Exception {
        ArrayList<Reply> output = new ArrayList<>();
        ReplyApi.analyzeTopReplies(new JSONObject().put("top_replies", JSONObject.NULL), output);
        assertTrue(output.isEmpty());
        ReplyApi.analyzeTopReplies(new JSONObject().put("top_replies", new JSONArray())
                .put("upper", new JSONObject().put("top", fixture())), output);
        assertEquals(1, output.size());
        assertTrue(output.get(0).isTop);
        output.get(0).setTop(false);
        assertEquals("A comment", output.get(0).message.toString());
    }

    private static JSONObject fixture() throws Exception {
        return new JSONObject().put("rpid", 123L).put("oid", 456L).put("root", 0).put("parent", 0)
                .put("member", new JSONObject().put("mid", 42L).put("uname", "Reader").put("avatar", "")
                        .put("level_info", new JSONObject().put("current_level", 1))
                        .put("is_senior_member", 0).put("vip", new JSONObject().put("vipStatus", 0)
                                .put("nickname_color", "")))
                .put("content", new JSONObject().put("message", "A comment"))
                .put("reply_control", new JSONObject()).put("ctime", 1).put("like", 0)
                .put("up_action", new JSONObject().put("like", false).put("reply", false)).put("rcount", 0);
    }
}
