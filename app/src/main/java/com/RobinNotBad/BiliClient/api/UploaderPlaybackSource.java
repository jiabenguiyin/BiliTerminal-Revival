package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Locates a chronological boundary without downloading an UP's entire archive. */
public class UploaderPlaybackSource {
    public static final class Prepared {
        public final FavoritePlaybackSource.Session session;
        public final PlayerData data;
        Prepared(FavoritePlaybackSource.Session session, PlayerData data) { this.session = session; this.data = data; }
    }

    protected UserInfoApi.UserVideoPage loadPage(long mid, int page, List<VideoCard> output) throws Exception {
        return UserInfoApi.getUserVideoPage(mid, page, output);
    }

    protected PlayerData loadItem(long aid) throws Exception {
        VideoInfo info = VideoInfoApi.getVideoInfo(aid);
        if (info == null || info.cids == null || info.cids.isEmpty()) throw new IOException("投稿暂不可播放");
        return info.toPlayerData(0);
    }

    public Prepared prepare(long account, long mid, boolean oldest, FavoritePlaybackSource.RequestGuard guard) throws Exception {
        if (mid <= 0) throw new IOException("UP主信息无效");
        guard.check();
        ArrayList<VideoCard> videos = new ArrayList<>();
        UserInfoApi.UserVideoPage result = loadPage(mid, 1, videos);
        guard.check(); check(result);
        int page = 1;
        if (oldest) {
            if (result.totalPages() < 0) throw new IOException("暂时无法定位最早投稿，请重试或从最新开始");
            int target = Math.max(1, result.totalPages());
            int retries = 0;
            while (target != page) {
                if (++retries > 3) throw new IOException("投稿数量正在变化，请重试");
                guard.check(); videos.clear(); page = target;
                result = loadPage(mid, page, videos);
                guard.check(); check(result);
                if (result.totalPages() < 0) throw new IOException("暂时无法定位最早投稿，请重试");
                if (result.totalPages() == 0) throw new IOException("该UP主暂无可播放投稿");
                target = result.totalPages();
            }
        }
        // A page may contain only removed/invalid cards; bound the scan rather than loading the archive.
        for (int skipped = 0; videos.isEmpty(); skipped++) {
            if (oldest ? page <= 1 : result.isBottom) throw new IOException("该UP主暂无可播放投稿");
            if (skipped >= 5) throw new IOException("暂无可播放投稿，请刷新后重试");
            guard.check(); page += oldest ? -1 : 1;
            result = loadPage(mid, page, videos);
            guard.check(); check(result);
        }
        FavoritePlaybackSource.Session session = FavoritePlaybackSource.Session.uploads(account, mid, oldest,
                page, oldest ? page <= 1 : result.isBottom, videos);
        if (session.ids.isEmpty()) throw new IOException("暂无可播放投稿");
        guard.check();
        PlayerData data = loadItem(session.ids.get(0));
        guard.check();
        if (data == null || data.aid != session.ids.get(0) || data.cids == null || data.cids.isEmpty())
            throw new IOException("投稿信息不完整，请重试");
        data.explicitOnlineQueue = true;
        return new Prepared(session, data);
    }

    private void check(UserInfoApi.UserVideoPage result) throws IOException {
        if (result.code != 0) throw new IOException("投稿列表加载失败（" + result.code + "）");
    }
}
