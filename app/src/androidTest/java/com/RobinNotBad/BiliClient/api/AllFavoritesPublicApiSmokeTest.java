package com.RobinNotBad.BiliClient.api;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** Explicitly invoked live smoke test; uses public data and sends no account Cookie. */
@RunWith(AndroidJUnit4.class)
public class AllFavoritesPublicApiSmokeTest {
    @Test public void publicFolderBrowseLoadsTwoPagesWithoutThe11010SearchRequest() throws Exception {
        ArrayList<String> headers = new ArrayList<>(Arrays.asList(
                "User-Agent", "Mozilla/5.0", "Referer", "https://www.bilibili.com/"));
        JSONObject rejected = NetWorkUtil.getJsonDirect("https://api.bilibili.com/x/v3/fav/resource/list"
                + "?media_id=44233921&pn=1&ps=2&type=1&keyword=&platform=web", headers);
        assertEquals(11010, rejected.optInt("code"));

        AllFavoriteVideosPager.Source source = new AllFavoriteVideosPager.Source() {
            @Override public List<Long> folders() { return Arrays.asList(44233921L); }
            @Override public JSONObject page(long mediaId, int page) throws java.io.IOException, org.json.JSONException {
                return NetWorkUtil.getJsonDirect(FavoriteApi.buildFavoriteVideosUrl(mediaId, page, "", false), headers);
            }
        };
        List<VideoCard> first = new ArrayList<>();
        ApiResult result = AllFavoriteVideosPager.load(null, source, first);
        assertEquals(0, result.code);
        assertEquals(20, first.size());
        assertFalse(result.isBottom);
        List<VideoCard> second = new ArrayList<>();
        ApiResult next = AllFavoriteVideosPager.load((AllFavoriteVideosPager.Cursor) result.result, source, second);
        assertEquals(0, next.code);
        assertEquals(20, second.size());
        Set<Long> seen = new HashSet<>();
        for (VideoCard card : first) assertTrue(seen.add(card.aid));
        for (VideoCard card : second) assertTrue(seen.add(card.aid));
        assertEquals(40, seen.size());
    }
}
