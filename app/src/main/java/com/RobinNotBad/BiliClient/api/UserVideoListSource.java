package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.VideoCard;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One query owns one cursor. Oldest-first walks real server pages backwards. */
public class UserVideoListSource {
    public static final int NEWEST = 0, MOST_PLAYED = 1, MOST_FAVORITED = 2, OLDEST = 3;
    public static int normalize(int order) { return order >= NEWEST && order <= OLDEST ? order : NEWEST; }
    public static String apiOrder(int order) {
        return order == MOST_PLAYED ? "click" : order == MOST_FAVORITED ? "stow" : "pubdate";
    }
    public static final class Page {
        public final List<VideoCard> videos;
        public final boolean bottom;
        Page(List<VideoCard> videos, boolean bottom) { this.videos = videos; this.bottom = bottom; }
    }
    private final long mid;
    private final int order;
    private int nextPage = 1;
    private boolean initialized, bottom;
    public UserVideoListSource(long mid, int order) { this.mid = mid; this.order = normalize(order); }
    protected UserInfoApi.UserVideoPage loadPage(int page, List<VideoCard> output) throws Exception {
        return UserInfoApi.getUserVideoPage(mid, page, apiOrder(order), output);
    }
    public Page next(FavoritePlaybackSource.RequestGuard guard) throws Exception {
        guard.check();
        if (bottom) return new Page(new ArrayList<>(), true);
        int page = nextPage;
        ArrayList<VideoCard> videos = new ArrayList<>();
        UserInfoApi.UserVideoPage result = loadPage(page, videos);
        guard.check(); check(result);
        if (order == OLDEST && !initialized) {
            int total = result.totalPages();
            if (total < 0) throw new IOException("暂时无法定位最早投稿，请重试");
            for (int attempt = 0; total > 0 && page != total; attempt++) {
                if (attempt >= 3) throw new IOException("投稿数量正在变化，请重试");
                page = total; videos.clear();
                result = loadPage(page, videos);
                guard.check(); check(result); total = result.totalPages();
                if (total < 0) throw new IOException("暂时无法定位最早投稿，请重试");
            }
            if (total == 0) videos.clear();
        }
        if (order == OLDEST) Collections.reverse(videos);
        boolean done = order == OLDEST ? page <= 1 : result.isBottom;
        guard.check();
        // A failed/cancelled request must be retryable at exactly the same page.
        nextPage = page + (order == OLDEST ? -1 : 1);
        initialized = true; bottom = done;
        return new Page(videos, done);
    }
    private static void check(UserInfoApi.UserVideoPage page) throws IOException {
        if (page.code != 0) throw new IOException("投稿列表加载失败（" + page.code + "）");
    }
}
