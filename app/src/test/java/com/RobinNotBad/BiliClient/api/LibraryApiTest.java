package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.SearchQueryUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;

import okhttp3.HttpUrl;

import static org.junit.Assert.*;

public class LibraryApiTest {
    @Test public void favoritesSearchScopeAndPagingAreEncoded() {
        String query = "\u4e2d\u6587 & A+B";
        HttpUrl current = HttpUrl.parse(FavoriteApi.buildFavoriteVideosUrl(1234567890123L, 3, " " + query + " ", false));
        HttpUrl all = HttpUrl.parse(FavoriteApi.buildFavoriteVideosUrl(1234567890123L, 2, query, true));
        assertEquals("0", current.queryParameter("type"));
        assertEquals("1", all.queryParameter("type"));
        assertEquals("1234567890123", all.queryParameter("media_id"));
        assertEquals(query, current.queryParameter("keyword"));
        assertEquals(query, all.queryParameter("keyword"));
        assertEquals("3", current.queryParameter("pn"));
        assertEquals("20", current.queryParameter("ps"));
    }

    @Test public void historySearchUsesServerEndpointAndNewPageNumber() {
        HttpUrl url = HttpUrl.parse(HistoryApi.buildHistorySearchUrl("\u4e2d\u6587&+", 4));
        assertEquals("/x/web-interface/history/search", url.encodedPath());
        assertEquals("\u4e2d\u6587&+", url.queryParameter("keyword"));
        assertEquals("4", url.queryParameter("pn"));
        assertEquals("all", url.queryParameter("business"));
        assertNull(url.queryParameter("max"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyAllFavoritesSearchCannotBeMistakenForAnAggregateList() {
        FavoriteApi.buildFavoriteVideosUrl(44233921L, 1, "  ", true);
    }

    @Test public void favoriteParserHonorsHasMoreAndOnlyOpensVideos() throws Exception {
        ArrayList<VideoCard> output = new ArrayList<>();
        JSONArray medias = new JSONArray().put(new JSONObject().put("id", 1234567890123L).put("type", 2)
                .put("title", "<em class=\"keyword\">Needle</em> &amp; thread")
                .put("bv_id", "BV-test")).put(new JSONObject().put("id", 77).put("type", 21));
        ApiResult result = FavoriteApi.parseFavoriteVideos(new JSONObject().put("code", 0)
                .put("data", new JSONObject().put("medias", medias).put("has_more", false)), output);
        assertEquals(0, result.code);
        assertTrue(result.isBottom);
        assertEquals(1, output.size());
        assertEquals(1234567890123L, output.get(0).aid);
        assertEquals("Needle & thread", output.get(0).title);
        assertEquals("BV-test", output.get(0).bvid);
    }

    @Test public void filteredNonVideoPageDoesNotPretendFeedEnded() throws Exception {
        ApiResult result = FavoriteApi.parseFavoriteVideos(new JSONObject().put("code", 0)
                .put("data", new JSONObject().put("has_more", true)
                        .put("medias", new JSONArray().put(new JSONObject().put("id", 77).put("type", 21)))),
                new ArrayList<>());
        assertFalse(result.isBottom);
    }

    @Test public void authenticationFailuresAreNotReportedAsEmptyResults() throws Exception {
        ArrayList<VideoCard> output = new ArrayList<>();
        assertEquals(-403, FavoriteApi.parseFavoriteVideos(new JSONObject().put("code", -403), output).code);
        assertEquals(-101, HistoryApi.parseHistoryPage(new JSONObject().put("code", -101), output).code);
        assertTrue(output.isEmpty());
    }

    @Test public void historySearchAllowsMissingCursorAndCompletedProgress() throws Exception {
        ArrayList<VideoCard> output = new ArrayList<>();
        JSONObject item = new JSONObject().put("title", "History").put("progress", -1)
                .put("history", new JSONObject().put("oid", 123L).put("bvid", "BV-test"));
        ApiResult result = HistoryApi.parseHistoryPage(new JSONObject().put("code", 0)
                .put("data", new JSONObject().put("list", new JSONArray().put(item))), output);
        assertEquals(1, output.size());
        assertEquals("\u5df2\u770b\u5b8c", output.get(0).view);
        assertFalse(result.isBottom);
    }

    @Test public void normalHistoryKeepsItsCursorAndSkipsLiveRooms() throws Exception {
        ArrayList<VideoCard> output = new ArrayList<>();
        JSONObject data = new JSONObject().put("list", new JSONArray()
                        .put(new JSONObject().put("history", new JSONObject().put("oid", 123L).put("business", "live"))))
                .put("cursor", new JSONObject().put("max", 1234567890123L).put("view_at", 99).put("business", "archive"));
        ApiResult result = HistoryApi.parseHistoryPage(new JSONObject().put("code", 0).put("data", data), output);
        assertTrue(output.isEmpty());
        assertEquals(1234567890123L, result.offset);
        assertEquals(99L, result.timestamp);
        assertEquals("archive", result.business);
    }

    @Test public void emptyPagesAndPlainTitleAreHandled() throws Exception {
        assertTrue(HistoryApi.parseHistoryPage(new JSONObject().put("code", 0)
                .put("data", new JSONObject().put("list", new JSONArray())), new ArrayList<>()).isBottom);
        assertEquals("A <literal> & B", SearchQueryUtil.plainResultTitle("A <literal> &amp; B"));
    }
}
