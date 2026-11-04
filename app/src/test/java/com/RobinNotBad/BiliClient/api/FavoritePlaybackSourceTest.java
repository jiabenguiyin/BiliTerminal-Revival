package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackQueue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FavoritePlaybackSourceTest {
    private VideoCard video(long aid) { VideoCard card = new VideoCard(); card.aid = aid; return card; }
    private FavoritePlaybackSource.Session seed(boolean all, String keyword) throws Exception {
        return FavoritePlaybackSource.Session.capture(7, 7, 91, all, keyword, 1,
                new ApiResult().setOffset(0, 0, "91"), false, Arrays.asList(video(1), video(2)));
    }

    @Test public void acceptedPageKeepsScopeAndDeduplicatesLongIds() throws Exception {
        FavoritePlaybackSource.Session old = seed(false, "音乐");
        FavoritePlaybackSource source = new FavoritePlaybackSource() {
            @Override protected ApiResult page(Session session, List<VideoCard> out, RequestGuard guard) {
                assertEquals(2, session.nextPage); assertEquals(91, session.folder);
                assertEquals("音乐", session.keyword); assertFalse(session.all);
                out.addAll(Arrays.asList(video(2), video(1000000000001L)));
                return new ApiResult().setBottom(true);
            }
        };
        FavoritePlaybackSource.Session next = source.nextPage(old);
        assertEquals(Arrays.asList(1L, 2L), old.ids); assertFalse(old.bottom); assertEquals(2, old.nextPage);
        assertEquals(Arrays.asList(1L, 2L, 1000000000001L), next.ids); assertTrue(next.bottom);
        assertEquals(3, next.nextPage);
    }

    @Test public void businessErrorDoesNotAdvanceAcceptedPageAndCanRetry() throws Exception {
        FavoritePlaybackSource.Session old = seed(false, "");
        final int[] attempts = {0};
        FavoritePlaybackSource source = new FavoritePlaybackSource() {
            @Override protected ApiResult page(Session session, List<VideoCard> out, RequestGuard guard) {
                assertEquals(2, session.nextPage);
                if (attempts[0]++ == 0) return new ApiResult(-101, "未登录");
                out.add(video(3)); return new ApiResult().setBottom(true);
            }
        };
        try { source.nextPage(old); fail(); } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("-101")); }
        assertEquals(2, old.nextPage); assertEquals(2, old.ids.size());
        assertEquals(Arrays.asList(1L, 2L, 3L), source.nextPage(old).ids);
    }

    @Test public void invalidCardsAreExcludedButDuplicateOnlyPageStillAdvances() throws Exception {
        FavoritePlaybackSource.Session old = seed(false, "");
        FavoritePlaybackSource source = new FavoritePlaybackSource() {
            @Override protected ApiResult page(Session s, List<VideoCard> out, RequestGuard guard) {
                VideoCard invalid = video(7); invalid.title = "已失效视频";
                VideoCard media = video(8); media.type = "media_bangumi";
                out.addAll(Arrays.asList(video(2), invalid, media, video(0)));
                return new ApiResult().setBottom(false);
            }
        };
        FavoritePlaybackSource.Session next = source.nextPage(old);
        assertEquals(old.ids, next.ids); assertFalse(next.bottom); assertEquals(3, next.nextPage);
    }

    @Test public void allSearchPreservesCrossFolderSearchAnchor() throws Exception {
        FavoritePlaybackSource.Session old = seed(true, "歌曲");
        FavoritePlaybackSource.Session restored = FavoritePlaybackSource.Session.fromJson(new JSONObject(old.toJson().toString()));
        assertTrue(restored.all); assertEquals("歌曲", restored.keyword); assertEquals("91", restored.cursor.business);
        assertEquals(7, restored.account); assertEquals(7, restored.owner);
    }

    @Test public void bottomNeverRequestsAnotherPage() throws Exception {
        FavoritePlaybackSource.Session old = seed(false, ""); old.bottom = true;
        FavoritePlaybackSource source = new FavoritePlaybackSource() {
            @Override protected ApiResult page(Session s, List<VideoCard> out, RequestGuard guard) { throw new AssertionError(); }
        };
        assertTrue(source.nextPage(old).bottom);
    }

    @Test public void appendPreservesActualRandomBackHistory() {
        CollectionPlaybackQueue queue = new CollectionPlaybackQueue(0, new long[]{1, 2, 3});
        queue.recordPlayed(1, false); queue.recordPlayed(3, false);
        queue.append(new long[]{3, 4, 5});
        assertArrayEquals(new long[]{1, 2, 3, 4, 5}, queue.toArray());
        assertEquals(1, queue.previousForUser(3));
        queue.recordPlayed(1, true);
        assertEquals(3, queue.nextForUser(1, CollectionPlaybackQueue.SHUFFLE, new Random(4)));
    }

    @Test public void cancellationAfterResponseDoesNotCommitItsPage() throws Exception {
        FavoritePlaybackSource.Session old = seed(false, "");
        FavoritePlaybackSource source = new FavoritePlaybackSource() {
            @Override protected ApiResult page(Session s, List<VideoCard> out, RequestGuard guard) {
                out.add(video(3)); return new ApiResult().setBottom(true);
            }
        };
        final int[] checks = {0};
        try {
            source.nextPage(old, () -> { if (++checks[0] == 2) throw new java.io.IOException("cancelled"); });
            fail();
        } catch (java.io.IOException expected) { assertEquals("cancelled", expected.getMessage()); }
        assertEquals(2, old.nextPage); assertEquals(Arrays.asList(1L, 2L), old.ids); assertFalse(old.bottom);
    }

    @Test public void restoredAllFavoritesCursorKeepsPendingOrderAndDoesNotRefetchAcceptedPages() throws Exception {
        AllFavoriteVideosPager.Source source = new AllFavoriteVideosPager.Source() {
            @Override public List<Long> folders() { return Arrays.asList(91L, 92L); }
            @Override public JSONObject page(long folder, int page) throws org.json.JSONException {
                assertEquals(1, page);
                JSONArray list = new JSONArray();
                for (int i = 0; i < 20; i++) list.put(new JSONObject().put("id", folder * 100 + i)
                        .put("type", 2).put("fav_time", 100 - i * 2 - (folder == 91 ? 0 : 1)));
                return new JSONObject().put("code", 0).put("data", new JSONObject().put("medias", list).put("has_more", false));
            }
        };
        List<VideoCard> first = new ArrayList<>();
        ApiResult accepted = AllFavoriteVideosPager.load(null, source, first);
        FavoritePlaybackSource.Session session = FavoritePlaybackSource.Session.capture(7, 7, 0, true, "", 1, accepted, false, first);
        FavoritePlaybackSource.Session restored = FavoritePlaybackSource.Session.fromJson(new JSONObject(session.toJson().toString()));
        List<VideoCard> more = new ArrayList<>();
        ApiResult next = AllFavoriteVideosPager.load((AllFavoriteVideosPager.Cursor) restored.cursor.result,
                new AllFavoriteVideosPager.Source() {
                    @Override public List<Long> folders() { throw new AssertionError(); }
                    @Override public JSONObject page(long folder, int page) { throw new AssertionError("Refetched accepted page"); }
                }, more);
        assertTrue(next.isBottom); assertEquals(20, more.size());
        assertEquals(9110, more.get(0).aid); assertEquals(9219, more.get(19).aid);
    }
}
