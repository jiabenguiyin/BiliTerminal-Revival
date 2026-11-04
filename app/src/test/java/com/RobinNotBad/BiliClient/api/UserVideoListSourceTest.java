package com.RobinNotBad.BiliClient.api;
import com.RobinNotBad.BiliClient.model.VideoCard;
import org.junit.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.Assert.*;

public class UserVideoListSourceTest {
    private static class Fake extends UserVideoListSource {
        final List<Integer> calls = new ArrayList<>(); long count = 5; int code; boolean changing;
        Fake(int order) { super(99, order); }
        @Override protected UserInfoApi.UserVideoPage loadPage(int page, List<VideoCard> out) {
            calls.add(page); UserInfoApi.UserVideoPage r = new UserInfoApi.UserVideoPage();
            r.code = code; r.page = page; r.pageSize = 2;
            r.count = changing ? calls.size() % 2 == 1 ? 5 : 7 : count;
            if (count != 0) for (long id : page == 1 ? new long[]{5,4} : page == 2 ? new long[]{3,2} : new long[]{1}) {
                VideoCard v = new VideoCard(); v.aid = id; out.add(v);
            }
            r.isBottom = count == 0 || page >= 3; return r;
        }
    }
    private static List<Long> ids(UserVideoListSource.Page page) {
        List<Long> ids = new ArrayList<>(); for (VideoCard v : page.videos) ids.add(v.aid); return ids;
    }
    @Test public void allApiModesUseServerWideOrderAndKeepLegacyNewestDefault() {
        for (int i = 0; i < 4; i++) assertTrue(UserInfoApi.buildUserVideosUrl(99, 2, UserVideoListSource.apiOrder(i)).contains("order="+new String[]{"pubdate","click","stow","pubdate"}[i]));
        assertTrue(UserInfoApi.buildUserVideosUrl(99, 2).contains("order=pubdate"));
        assertEquals(0, UserVideoListSource.normalize(99));
    }
    @Test public void oldestStartsAtTheRealLastPageThenPaginatesBackward() throws Exception {
        Fake s = new Fake(3); assertEquals(Arrays.asList(1L), ids(s.next(()->{})));
        assertEquals(Arrays.asList(1,3), s.calls);
        assertEquals(Arrays.asList(2L,3L), ids(s.next(()->{})));
        UserVideoListSource.Page last = s.next(()->{});
        assertEquals(Arrays.asList(4L,5L), ids(last)); assertTrue(last.bottom);
        assertTrue(s.next(()->{}).videos.isEmpty()); assertEquals(Arrays.asList(1,3,2,1),s.calls);
    }
    @Test public void popularAndFavoriteOrdersKeepTheServersOrder() throws Exception {
        for (int mode : new int[]{0,1,2}) { Fake s = new Fake(mode); assertEquals(Arrays.asList(5L,4L),ids(s.next(()->{}))); assertEquals(Arrays.asList(3L,2L),ids(s.next(()->{}))); }
    }
    @Test public void businessErrorDoesNotAdvanceOrFabricateAnEmptyPage() throws Exception {
        Fake s = new Fake(0); s.code = -352;
        try { s.next(()->{}); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("-352")); }
        s.code=0; assertEquals(Arrays.asList(5L,4L),ids(s.next(()->{}))); assertEquals(Arrays.asList(1,1),s.calls);
    }
    @Test public void cancellationDoesNotCommitThePageCursor() throws Exception {
        Fake s = new Fake(0); int[] checks={0};
        try { s.next(()->{if(++checks[0]==2)throw new IOException("cancelled");}); fail(); } catch (IOException expected) { }
        assertEquals(Arrays.asList(5L,4L),ids(s.next(()->{}))); assertEquals(Arrays.asList(1,1),s.calls);
    }
    @Test public void unknownOldestBoundaryFailsInsteadOfReversingOnlyTheNewestPage() throws Exception {
        Fake s = new Fake(3); s.count=-1;
        try { s.next(()->{}); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("最早")); }
        assertEquals(Arrays.asList(1),s.calls);
    }
    @Test public void rapidlyChangingArchiveHasABoundedBoundaryRetry() throws Exception {
        Fake s = new Fake(3); s.changing=true;
        try { s.next(()->{}); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("变化")); }
        assertTrue(s.calls.size()<=4);
    }
    @Test public void emptyArchiveIsAnActualEnd() throws Exception {
        for (int mode : new int[]{0,3}) { Fake s = new Fake(mode); s.count=0; UserVideoListSource.Page p=s.next(()->{}); assertTrue(p.bottom);assertTrue(p.videos.isEmpty()); }
    }
    @Test(expected=IllegalArgumentException.class) public void unknownWireOrderCannotBeSent() { UserInfoApi.buildUserVideosUrl(99,1,"oldest"); }
}
