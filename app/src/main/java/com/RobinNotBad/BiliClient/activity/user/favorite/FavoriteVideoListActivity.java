package com.RobinNotBad.BiliClient.activity.user.favorite;

import android.content.Intent;
import android.os.Bundle;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.SearchableVideoListActivity;
import com.RobinNotBad.BiliClient.activity.video.info.AddFavoriteActivity;
import com.RobinNotBad.BiliClient.api.FavoriteApi;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.FavoriteFolder;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavoriteVideoListActivity extends SearchableVideoListActivity {
    private static final int MANAGE_FAVORITES = 82;
    private long mid;
    private long fid;
    private boolean allFolders;
    private long pendingDeleteAid = -1;
    private long lastDeleteTap;
    private final Set<Long> deleting = new HashSet<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mid = getIntent().getLongExtra("mid", 0);
        fid = getIntent().getLongExtra("fid", 0);
        allFolders = getIntent().getBooleanExtra("all_folders", false);
        setPageName(allFolders ? "全部收藏" : getIntent().getStringExtra("name"));
        startVideoList(savedInstanceState, allFolders ? R.string.library_search_all_favorites
                : R.string.library_search_favorites);
    }

    @Override protected ApiResult loadVideos(int page, String keyword, ApiResult previous, List<VideoCard> output)
            throws Exception {
        if (allFolders && keyword.trim().isEmpty()) {
            return FavoriteApi.getAllFavoriteVideos(mid, previous.result, output);
        }
        long targetFid = fid;
        if (allFolders) {
            if (page == 1) {
                ArrayList<FavoriteFolder> folders = FavoriteApi.getFavoriteFolders(mid);
                if (folders.isEmpty()) return new ApiResult(0, "").setBottom(true);
                targetFid = folders.get(0).id;
            } else targetFid = Long.parseLong(previous.business);
        }
        ApiResult result = FavoriteApi.getFavoriteVideos(targetFid, page, keyword, allFolders, output);
        result.business = String.valueOf(targetFid);
        return result;
    }

    @Override protected boolean deduplicateVideos() { return true; }

    @Override protected void configureVideoAdapter() {
        videoCardAdapter.setOnLongClickListener(position -> {
            if (position < 0 || position >= videoList.size()
                    || mid != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
            VideoCard selected = videoList.get(position);
            if (allFolders) {
                startActivityForResult(new Intent(this, AddFavoriteActivity.class)
                        .putExtra("aid", selected.aid), MANAGE_FAVORITES);
                return;
            }
            if (deleting.contains(selected.aid)) return;
            long now = System.currentTimeMillis();
            if (pendingDeleteAid != selected.aid || now - lastDeleteTap >= 4000) {
                pendingDeleteAid = selected.aid;
                lastDeleteTap = now;
                MsgUtil.showMsg("再次长按删除");
                return;
            }
            pendingDeleteAid = -1;
            deleting.add(selected.aid);
            CenterThreadPool.run(() -> {
                try {
                    if (mid != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
                    int result = FavoriteApi.deleteFavorite(selected.aid, fid);
                    runOnUiThread(() -> {
                        if (mid != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
                        if (result == 0) {
                            int index = -1;
                            for (int i = 0; i < videoList.size(); i++) {
                                if (videoList.get(i).aid == selected.aid) { index = i; break; }
                            }
                            if (index >= 0) {
                                videoList.remove(index);
                                videoCardAdapter.notifyItemRemoved(index);
                                videoCardAdapter.notifyItemRangeChanged(index, videoList.size() - index);
                            }
                            MsgUtil.showMsg("删除成功");
                            if (videoList.isEmpty()) reloadVideos();
                        } else MsgUtil.showMsg("删除失败，错误码：" + result);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> MsgUtil.err(error));
                } finally {
                    runOnUiThread(() -> deleting.remove(selected.aid));
                }
            });
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_FAVORITES) reloadVideos();
    }
}
