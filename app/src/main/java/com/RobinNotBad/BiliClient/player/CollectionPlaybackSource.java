package com.RobinNotBad.BiliClient.player;

import com.RobinNotBad.BiliClient.api.HistoryApi;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.api.VideoInfoApi;
import com.RobinNotBad.BiliClient.model.Collection;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.model.VideoInfo;

import java.io.IOException;
import java.util.ArrayList;

/** Network work is separated from player lifetime and UI state. */
public class CollectionPlaybackSource {
    public CollectionPlaybackQueue loadQueue(long aid) throws Exception {
        VideoInfo info = VideoInfoApi.getVideoInfo(aid);
        if (info == null) throw new IOException("视频信息加载失败");
        Collection collection = info.isSteinGate ? null : info.collection;
        if (collection == null) return null;
        ArrayList<Long> ids = new ArrayList<>();
        if (collection.sections != null) for (Collection.Section section : collection.sections) {
            if (section == null || section.episodes == null) continue;
            for (Collection.Episode episode : section.episodes) {
                if (episode != null) ids.add(episode.aid > 0 ? episode.aid
                        : episode.arc == null ? -1 : episode.arc.aid);
            }
        }
        if (ids.isEmpty() && collection.cards != null) for (VideoCard card : collection.cards) {
            if (card != null) ids.add(card.aid);
        }
        long[] array = new long[ids.size()];
        for (int i = 0; i < array.length; i++) array[i] = ids.get(i);
        CollectionPlaybackQueue queue = new CollectionPlaybackQueue(collection.id, array);
        return queue.contains(aid) ? queue : null;
    }

    public PlayerData loadItem(long targetAid, int pageIndex, PlayerData current) throws Exception {
        PlayerData data = new PlayerData();
        data.aid = targetAid;
        data.mid = current.mid;
        data.title = current.title;
        data.pagenames = current.pagenames;
        data.cids = current.cids;
        data.danmakuCount = current.danmakuCount;
        data.qn = current.qn;
        if (targetAid != current.aid) {
            VideoInfo info = VideoInfoApi.getVideoInfo(targetAid);
            if (info == null || info.cids == null || info.cids.isEmpty() || info.isSteinGate)
                throw new IOException("合集视频不可播放");
            data.title = info.title;
            data.pagenames = info.pagenames;
            data.cids = info.cids;
            data.mid = info.staff.isEmpty() ? 0 : info.staff.get(0).mid;
            data.danmakuCount = info.stats == null ? -1 : info.stats.danmaku;
        }
        if (data.cids == null || pageIndex < 0 || pageIndex >= data.cids.size())
            throw new IOException("分P信息不完整");
        data.cid = data.cids.get(pageIndex);
        data.currentPageIndex = pageIndex;
        if (data.pagenames != null && data.pagenames.size() > 1 && pageIndex < data.pagenames.size())
            data.title = data.pagenames.get(pageIndex);
        PlayerApi.getVideoForPlayback(data);
        return data;
    }

    public void reportHistory(long aid, long cid, long positionSeconds) throws Exception {
        HistoryApi.reportHistory(aid, cid, positionSeconds);
    }
}
