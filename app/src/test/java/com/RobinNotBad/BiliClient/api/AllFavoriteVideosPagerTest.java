package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AllFavoriteVideosPagerTest {
    @Test public void mergeSortsAllFoldersAndOnlyBuffersOnePagePerFolder() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AllFavoriteVideosPager.Source source = new AllFavoriteVideosPager.Source() {
            @Override public List<Long> folders() { return Arrays.asList(10042L, 20042L); }
            @Override public JSONObject page(long id, int page) throws org.json.JSONException {
                assertEquals(1, page);
                calls.incrementAndGet();
                JSONArray items = new JSONArray();
                for (int i = 0; i < 20; i++) items.put(video((id == 10042 ? 100 : 200) + i,
                        (id == 10042 ? 200 : 199) - 2 * i));
                return ok(items, false);
            }
        };
        List<VideoCard> first = new ArrayList<>();
        ApiResult a = AllFavoriteVideosPager.load(null, source, first);
        assertEquals(20, first.size());
        assertFalse(a.isBottom);
        for (int i = 0; i < 10; i++) {
            assertEquals(100 + i, first.get(i * 2).aid);
            assertEquals(200 + i, first.get(i * 2 + 1).aid);
        }
        List<VideoCard> second = new ArrayList<>();
        ApiResult b = AllFavoriteVideosPager.load((AllFavoriteVideosPager.Cursor) a.result, source, second);
        assertEquals(20, second.size());
        assertEquals(110, second.get(0).aid);
        assertEquals(219, second.get(19).aid);
        assertTrue(b.isBottom);
        assertEquals(2, calls.get());
    }

    @Test public void duplicateVideosUseTheirMostRecentFavoriteTimeAndKeepLongIds() throws Exception {
        long aid = 117403434486720L;
        AllFavoriteVideosPager.Source source = new AllFavoriteVideosPager.Source() {
            @Override public List<Long> folders() { return Arrays.asList(10042L, 20042L, 20042L); }
            @Override public JSONObject page(long id, int page) throws org.json.JSONException {
                return ok(id == 10042 ? new JSONArray().put(video(aid, 10))
                        : new JSONArray().put(video(aid, 30)).put(video(123, 20)), false);
            }
        };
        List<VideoCard> output = new ArrayList<>();
        ApiResult result = AllFavoriteVideosPager.load(null, source, output);
        assertTrue(result.isBottom);
        assertEquals(2, output.size());
        assertEquals(aid, output.get(0).aid);
        assertEquals(123, output.get(1).aid);
    }

    @Test public void failedNextPageDoesNotLoseBufferedItemsOrAdvancePreviousCursor() throws Exception {
        AtomicInteger code = new AtomicInteger();
        AllFavoriteVideosPager.Source source = new AllFavoriteVideosPager.Source() {
            @Override public List<Long> folders() { return Arrays.asList(10042L, 20042L); }
            @Override public JSONObject page(long id, int page) throws org.json.JSONException {
                if (id == 20042) {
                    if (page == 2 && code.get() != 0) return new JSONObject().put("code", code.get());
                    return ok(new JSONArray().put(video(page == 1 ? 999 : 888, page == 1 ? 850 : 1)), page == 1);
                }
                JSONArray items = new JSONArray();
                for (int i = 0; i < (page == 1 ? 20 : 3); i++) {
                    items.put(video(page * 100 + i, 1000 - page * 100 - i));
                }
                return ok(items, page == 1);
            }
        };
        ApiResult first = AllFavoriteVideosPager.load(null, source, new ArrayList<>());
        List<VideoCard> output = new ArrayList<>();
        code.set(-403);
        assertEquals(-403, AllFavoriteVideosPager.load((AllFavoriteVideosPager.Cursor) first.result,
                source, output).code);
        assertTrue(output.isEmpty());
        code.set(0);
        ApiResult retry = AllFavoriteVideosPager.load((AllFavoriteVideosPager.Cursor) first.result, source, output);
        assertTrue(retry.isBottom);
        assertEquals(5, output.size());
        assertEquals(999, output.get(0).aid);
        assertEquals(200, output.get(1).aid);
        assertEquals(888, output.get(4).aid);
    }

    @Test public void nonVideoPageAndEmptyFolderDoNotHideLaterVideos() throws Exception {
        AllFavoriteVideosPager.Source source = new AllFavoriteVideosPager.Source() {
            @Override public List<Long> folders() { return Arrays.asList(10042L, 20042L); }
            @Override public JSONObject page(long id, int page) throws org.json.JSONException {
                if (id == 20042) return ok(new JSONArray(), false);
                return ok(new JSONArray().put(page == 1 ? video(1, 10).put("type", 21)
                        : video(2, 9)), page == 1);
            }
        };
        List<VideoCard> output = new ArrayList<>();
        assertTrue(AllFavoriteVideosPager.load(null, source, output).isBottom);
        assertEquals(1, output.size());
        assertEquals(2, output.get(0).aid);
    }

    private static JSONObject video(long aid, long time) throws org.json.JSONException {
        return new JSONObject().put("id", aid).put("type", 2).put("title", "Video")
                .put("fav_time", time);
    }

    private static JSONObject ok(JSONArray videos, boolean more) throws org.json.JSONException {
        return new JSONObject().put("code", 0).put("data", new JSONObject()
                .put("medias", videos).put("has_more", more));
    }
}
