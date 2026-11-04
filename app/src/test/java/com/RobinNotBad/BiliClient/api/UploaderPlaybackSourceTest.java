package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackQueue;
import org.json.*;
import org.junit.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class UploaderPlaybackSourceTest {
    private static VideoCard card(long id) { VideoCard v = new VideoCard(); v.aid = id; return v; }
    private static List<VideoCard> cards(long... ids) {
        List<VideoCard> out = new ArrayList<>(); for (long id : ids) out.add(card(id)); return out;
    }
    private static class Fake extends UploaderPlaybackSource {
        final List<Integer> requested = new ArrayList<>(); long item; int error;
        long count = 82; boolean hideBoundary;
        @Override protected UserInfoApi.UserVideoPage loadPage(long mid, int page, List<VideoCard> out) {
            assertEquals(99, mid); requested.add(page);
            UserInfoApi.UserVideoPage r = new UserInfoApi.UserVideoPage();
            r.page = page; r.count = count; r.isBottom = page >= 3; r.code = error;
            if (!(hideBoundary && page == 3)) out.addAll(page == 3 ? cards(2,1) : page == 2 ? cards(42,41) : cards(82,81));
            return r;
        }
        @Override protected PlayerData loadItem(long id) {
            item = id; PlayerData p = new PlayerData(); p.aid = id;
            p.cids = new ArrayList<>(Arrays.asList(id + 1000)); return p;
        }
    }
    @Test public void oldestJumpsToLastPageAndStartsWithEarliestWithoutFetchingMiddle() throws Exception {
        Fake source = new Fake(); UploaderPlaybackSource.Prepared p = source.prepare(7,99,true,()->{});
        assertEquals(Arrays.asList(1,3),source.requested); assertEquals(1,source.item);
        assertEquals(Arrays.asList(1L,2L),p.session.ids); assertEquals(2,p.session.nextPage);
        assertTrue(p.session.isUploads()); assertTrue(p.session.uploadOldest); assertFalse(p.session.bottom);
        assertEquals(99,p.session.owner); assertEquals(7,p.session.account); assertTrue(p.data.explicitOnlineQueue);
    }
    @Test public void newestStartsOnFirstPageAndOnlyRequestsThatPage() throws Exception {
        Fake source = new Fake(); UploaderPlaybackSource.Prepared p = source.prepare(7,99,false,()->{});
        assertEquals(Arrays.asList(1),source.requested); assertEquals(82,source.item);
        assertEquals(Arrays.asList(82L,81L),p.session.ids); assertEquals(2,p.session.nextPage);
        assertFalse(p.session.uploadOldest); assertFalse(p.session.bottom);
    }
    @Test public void missingCountNeverPretendsThatFirstPageIsOldest() throws Exception {
        Fake source = new Fake(); source.count = -1;
        try { source.prepare(7,99,true,()->{}); fail(); } catch(IOException e) { assertTrue(e.getMessage().contains("最早")); }
        assertEquals(0,source.item); assertEquals(Arrays.asList(1),source.requested);
    }
    @Test public void newestDoesNotRequireCountMetadata() throws Exception {
        Fake source = new Fake(); source.count = -1;
        assertEquals(82,source.prepare(7,99,false,()->{}).data.aid);
    }
    @Test public void businessErrorsNeverLaunchAnUnrelatedVideo() throws Exception {
        Fake source = new Fake(); source.error = -101;
        try { source.prepare(7,99,false,()->{}); fail(); } catch(IOException e) { assertTrue(e.getMessage().contains("-101")); }
        assertEquals(0,source.item);
    }
    @Test public void cancellationAfterPagePreventsMetadataAndLaunch() throws Exception {
        Fake source = new Fake(); int[] checks = {0};
        try { source.prepare(7,99,true,()->{if(++checks[0]==2)throw new IOException("cancel");}); fail(); }
        catch(IOException e) { assertEquals("cancel",e.getMessage()); }
        assertEquals(0,source.item); assertEquals(Arrays.asList(1),source.requested);
    }
    @Test public void invalidLastPageContinuesTowardFirstPlayableOldPost() throws Exception {
        Fake source = new Fake(); source.hideBoundary = true;
        UploaderPlaybackSource.Prepared p = source.prepare(7,99,true,()->{});
        assertEquals(Arrays.asList(1,3,2),source.requested); assertEquals(41,source.item);
        assertEquals(1,p.session.nextPage); assertFalse(p.session.bottom);
    }
    @Test public void oldestPageCursorDecrementsAndDeduplicatesWithoutMutatingAcceptedSession() throws Exception {
        FavoritePlaybackSource.Session first = FavoritePlaybackSource.Session.uploads(7,99,true,3,false,cards(2,1));
        FavoritePlaybackSource source = new FavoritePlaybackSource() {
            @Override protected ApiResult page(Session s,List<VideoCard> out,RequestGuard guard) {
                assertTrue(s.isUploads()); assertEquals(99,s.owner); assertEquals(2,s.nextPage);
                out.addAll(cards(2,3,4)); return new ApiResult();
            }
        };
        FavoritePlaybackSource.Session next = source.nextPage(first);
        assertEquals(Arrays.asList(1L,2L,3L,4L),next.ids); assertEquals(1,next.nextPage);
        assertEquals(Arrays.asList(1L,2L),first.ids); assertEquals(2,first.nextPage);
        assertEquals(3,next.queue().next(2,CollectionPlaybackQueue.SEQUENTIAL,new Random(1)));
        assertEquals(2,next.queue().next(3,CollectionPlaybackQueue.REVERSE,new Random(1)));
    }
    @Test public void oldFavoriteSnapshotsRemainReadableWithoutNewFields() throws Exception {
        FavoritePlaybackSource.Session favorite = FavoritePlaybackSource.Session.capture(7,7,91,false,"",1,new ApiResult(),false,cards(1));
        JSONObject json = favorite.toJson(); json.remove("kind"); json.remove("upload_oldest");
        FavoritePlaybackSource.Session restored = FavoritePlaybackSource.Session.fromJson(json);
        assertFalse(restored.isUploads()); assertEquals(91,restored.folder); assertEquals(2,restored.nextPage);
    }
    @Test public void uploadSnapshotRestoresDirectionAndFinalZeroPageWithoutMoreRequests() throws Exception {
        FavoritePlaybackSource.Session s = FavoritePlaybackSource.Session.uploads(7,99,true,1,true,cards(2,1));
        FavoritePlaybackSource.Session restored = FavoritePlaybackSource.Session.fromJson(new JSONObject(s.toJson().toString()));
        assertEquals(0,restored.nextPage); assertTrue(restored.bottom); assertTrue(restored.uploadOldest);
        assertEquals(Arrays.asList(1L,2L),restored.ids);
        assertTrue(new FavoritePlaybackSource().nextPage(restored).bottom);
    }
    private JSONObject response(int page, Long count, JSONArray videos) throws Exception {
        JSONObject info = new JSONObject().put("pn",page).put("ps",40);
        if(count!=null)info.put("count",count);
        return new JSONObject().put("code",0).put("data",new JSONObject().put("page",info)
                .put("list",new JSONObject().put("vlist",videos)));
    }
    @Test public void parserUsesLongIdsCountAndFiltersInvalidEntries() throws Exception {
        JSONArray videos = new JSONArray().put(new JSONObject().put("aid",500000000001L).put("title","<em>歌曲</em>"))
                .put(new JSONObject().put("aid",2).put("title","已失效视频")).put(new JSONObject().put("aid",0));
        List<VideoCard> out = new ArrayList<>();
        UserInfoApi.UserVideoPage p = UserInfoApi.parseUserVideoPage(response(3,82L,videos),3,out);
        assertEquals(3,p.totalPages()); assertTrue(p.isBottom); assertEquals(3,p.rawCount);
        assertEquals(1,out.size()); assertEquals(500000000001L,out.get(0).aid); assertEquals("歌曲",out.get(0).title);
    }
    @Test public void shortPageWithoutCountDoesNotSilentlyTruncateArchive() throws Exception {
        JSONArray videos = new JSONArray().put(new JSONObject().put("aid",1));
        UserInfoApi.UserVideoPage p = UserInfoApi.parseUserVideoPage(response(1,null,videos),1,new ArrayList<>());
        assertEquals(-1,p.totalPages()); assertFalse(p.isBottom);
        assertTrue(UserInfoApi.parseUserVideoPage(response(2,null,new JSONArray()),2,new ArrayList<>()).isBottom);
    }
    @Test public void parserRejectsRepeatedPageAndMissingListButPreservesBusinessCode() throws Exception {
        try { UserInfoApi.parseUserVideoPage(response(1,82L,new JSONArray()),2,new ArrayList<>()); fail(); }
        catch(JSONException expected) { assertTrue(expected.getMessage().contains("分页")); }
        try { UserInfoApi.parseUserVideoPage(new JSONObject().put("code",0),1,new ArrayList<>()); fail(); }
        catch(JSONException expected) { assertTrue(expected.getMessage().contains("格式")); }
        assertEquals(-352,UserInfoApi.parseUserVideoPage(new JSONObject().put("code",-352),1,new ArrayList<>()).code);
    }
}
