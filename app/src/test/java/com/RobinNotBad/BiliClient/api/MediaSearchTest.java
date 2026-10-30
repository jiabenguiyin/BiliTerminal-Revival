package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.VideoCard;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class MediaSearchTest {
    private static JSONObject item(long mediaId) throws Exception {
        return new JSONObject().put("media_id", mediaId).put("season_id", 456)
                .put("title", "<em class=\"keyword\">Title</em> &amp; More")
                .put("cover", "//example.com/cover.jpg").put("areas", "Area")
                .put("index_show", "12 episodes");
    }

    private static JSONObject response(JSONArray items, int pages) throws Exception {
        return new JSONObject().put("code", 0).put("data",
                new JSONObject().put("result", items).put("numPages", pages));
    }

    @Test public void bangumiUsesMediaIdAndStripsTitleMarkup() throws Exception {
        SearchApi.MediaSearchResult result = SearchApi.parseMediaSearchResponse(
                response(new JSONArray().put(item(123)), 2), 1, SearchApi.TYPE_BANGUMI);
        VideoCard card = result.items.get(0);
        assertEquals(123L, card.aid);
        assertEquals("456", card.bvid);
        assertEquals("Title & More", card.title);
        assertEquals("https://example.com/cover.jpg", card.cover);
        assertEquals("Area", card.upName);
        assertEquals("12 episodes", card.view);
        assertEquals(SearchApi.TYPE_BANGUMI, card.type);
        assertTrue(result.hasMore);
    }

    @Test public void filmUsesRequestedTypeAndStopsOnLastPage() throws Exception {
        JSONObject movie = item(789).put("type", "media_bangumi");
        SearchApi.MediaSearchResult result = SearchApi.parseMediaSearchResponse(
                response(new JSONArray().put(movie), 2), 2, SearchApi.TYPE_FILM);
        assertEquals(SearchApi.TYPE_FILM, result.items.get(0).type);
        assertEquals(789L, result.items.get(0).aid);
        assertFalse(result.hasMore);
    }

    @Test public void optionalMetadataAndMissingCoverAreSafe() throws Exception {
        JSONObject movie = new JSONObject().put("media_id", 1).put("title", "Movie")
                .put("new_ep", new JSONObject().put("index_show", "Complete"));
        VideoCard card = SearchApi.parseMediaSearchResponse(
                response(new JSONArray().put(movie), 1), 1, SearchApi.TYPE_FILM).items.get(0);
        assertEquals("", card.cover);
        assertEquals("", card.upName);
        assertEquals("", card.bvid);
        assertEquals("Complete", card.view);
    }

    @Test public void malformedRowsDoNotDiscardValidResults() throws Exception {
        JSONArray rows = new JSONArray().put(JSONObject.NULL).put("invalid").put(item(0))
                .put(item(2).put("title", " ")).put(item(3));
        SearchApi.MediaSearchResult result = SearchApi.parseMediaSearchResponse(
                response(rows, 1), 1, SearchApi.TYPE_BANGUMI);
        assertEquals(1, result.items.size());
        assertEquals(3L, result.items.get(0).aid);
    }

    @Test public void emptyResultsAlwaysStopPagination() throws Exception {
        SearchApi.MediaSearchResult result = SearchApi.parseMediaSearchResponse(
                response(new JSONArray(), 99), 1, SearchApi.TYPE_FILM);
        assertTrue(result.items.isEmpty());
        assertFalse(result.hasMore);
    }

    @Test public void explicitNullResultsAreEmpty() throws Exception {
        JSONObject json = new JSONObject().put("code", 0).put("data",
                new JSONObject().put("result", JSONObject.NULL));
        assertFalse(SearchApi.parseMediaSearchResponse(json, 1, SearchApi.TYPE_FILM).hasMore);
    }

    @Test public void zeroTotalCanOmitResults() throws Exception {
        JSONObject json = new JSONObject().put("code", 0).put("data",
                new JSONObject().put("numResults", 0).put("numPages", 0));
        assertFalse(SearchApi.parseMediaSearchResponse(json, 1, SearchApi.TYPE_FILM).hasMore);
    }

    @Test(expected = IOException.class) public void missingResultWithoutZeroTotalIsAnError() throws Exception {
        SearchApi.parseMediaSearchResponse(new JSONObject().put("code", 0).put("data",
                new JSONObject().put("numResults", 10)), 1, SearchApi.TYPE_FILM);
    }

    @Test(expected = IOException.class) public void verificationWithZeroTotalStillFails() throws Exception {
        SearchApi.parseMediaSearchResponse(new JSONObject().put("code", 0).put("data",
                new JSONObject().put("numResults", 0).put("v_voucher", "required")), 1, SearchApi.TYPE_FILM);
    }

    @Test public void missingPageCountAllowsLoadingUntilEmpty() throws Exception {
        assertTrue(SearchApi.parseMediaSearchResponse(
                response(new JSONArray().put(item(1)), -1), 4, SearchApi.TYPE_FILM).hasMore);
    }

    @Test(expected = IOException.class) public void verificationIsNotAnEmptyResult() throws Exception {
        JSONObject json = new JSONObject().put("code", 0).put("data",
                new JSONObject().put("v_voucher", "verification-required"));
        SearchApi.parseMediaSearchResponse(json, 1, SearchApi.TYPE_BANGUMI);
    }

    @Test(expected = IOException.class) public void serviceErrorsAreNotEmptyResults() throws Exception {
        SearchApi.parseMediaSearchResponse(new JSONObject().put("code", -412), 1, SearchApi.TYPE_FILM);
    }

    @Test(expected = IOException.class) public void malformedResponseIsNotAnEmptyResult() throws Exception {
        SearchApi.parseMediaSearchResponse(new JSONObject().put("code", 0)
                .put("data", new JSONObject().put("result", "invalid")), 1, SearchApi.TYPE_FILM);
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsOtherSearchTypes() throws Exception {
        SearchApi.parseMediaSearchResponse(response(new JSONArray(), 0), 1, "video");
    }

    @Test public void unsafeCoverIsNotLoaded() throws Exception {
        VideoCard card = SearchApi.parseMediaSearchResponse(response(new JSONArray()
                .put(item(1).put("cover", "file:///private/cover")), 1), 1, SearchApi.TYPE_FILM).items.get(0);
        assertEquals("", card.cover);
    }
}
