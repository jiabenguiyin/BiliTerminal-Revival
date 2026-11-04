package com.RobinNotBad.BiliClient.activity.user.favorite;

import android.content.Intent;
import android.os.Bundle;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.SearchableVideoListActivity;
import com.RobinNotBad.BiliClient.activity.video.info.AddFavoriteActivity;
import com.RobinNotBad.BiliClient.api.FavoriteApi;
import com.RobinNotBad.BiliClient.api.FavoritePlaybackSource;
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity;
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
    private boolean openingPlayback;
    private volatile String videoOrder="mtime";
    private androidx.appcompat.app.AlertDialog sortDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mid = getIntent().getLongExtra("mid", 0);
        fid = getIntent().getLongExtra("fid", 0);
        allFolders = getIntent().getBooleanExtra("all_folders", false);
        videoOrder=com.RobinNotBad.BiliClient.util.FavoriteVideoOrder.read(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0),fid);
        setPageName(allFolders ? "全部收藏" : getIntent().getStringExtra("name"));
        startVideoList(savedInstanceState, allFolders ? R.string.library_search_all_favorites
                : R.string.library_search_favorites);
        if(!allFolders){
            android.view.View sort=findViewById(R.id.library_sort);sort.setVisibility(android.view.View.VISIBLE);
            updateSortDescription();sort.setOnClickListener(v->showSort());
        }
    }

    private void updateSortDescription(){findViewById(R.id.library_sort).setContentDescription("排序："+com.RobinNotBad.BiliClient.util.FavoriteVideoOrder.label(videoOrder));}
    private void showSort(){
        if(sortDialog!=null&&sortDialog.isShowing())return;
        long account=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        sortDialog=com.RobinNotBad.BiliClient.util.FavoriteVideoOrder.dialog(this,videoOrder,order->{
            if(account!=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)||isDestroyed())return;
            videoOrder=order;com.RobinNotBad.BiliClient.util.FavoriteVideoOrder.save(account,fid,order);
            pendingDeleteAid=-1;updateSortDescription();reloadVideos();
        });MsgUtil.prepareAlertDialog(sortDialog);sortDialog.show();
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
        ApiResult result = FavoriteApi.getFavoriteVideos(targetFid, page, keyword, allFolders, output,allFolders?"mtime":videoOrder);
        result.business = String.valueOf(targetFid);
        return result;
    }

    @Override protected boolean deduplicateVideos() { return true; }

    @Override protected void configureVideoAdapter() {
        videoCardAdapter.setOnItemClickListener(position -> {
            if (openingPlayback || position < 0 || position >= videoList.size()) return;
            VideoCard selected = videoList.get(position);
            if (!FavoritePlaybackSource.playableCard(selected)) {
                MsgUtil.showMsg("该收藏视频已失效");
                return;
            }
            long account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            try {
                FavoritePlaybackSource.Session snapshot = FavoritePlaybackSource.Session.capture(account, mid, fid,
                        allFolders, playbackQuery(), playbackPage(), playbackCursor(), playbackBottom(), videoList);
                snapshot.favoriteOrder=allFolders?"mtime":videoOrder;
                openingPlayback = true;
                CenterThreadPool.run(() -> {
                    String token = null;
                    try { token = FavoritePlaybackSource.create(getApplicationContext(), snapshot); }
                    catch (Exception error) {
                        com.RobinNotBad.BiliClient.util.Logu.w("favorite-playlist", error.getClass().getSimpleName());
                    }
                    final String playbackToken = token;
                    runOnUiThread(() -> {
                        openingPlayback = false;
                        if (isFinishing() || getLifecycle().getCurrentState() == androidx.lifecycle.Lifecycle.State.DESTROYED
                                || account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
                        if (playbackToken == null) MsgUtil.showMsg("收藏连播列表保存失败，本次仅播放该视频");
                        startActivity(new Intent(this, VideoInfoActivity.class).putExtra("aid", selected.aid)
                                .putExtra("bvid", selected.bvid).putExtra("type", "video")
                                .putExtra(FavoritePlaybackSource.TOKEN, playbackToken));
                    });
                });
            } catch (Exception error) { MsgUtil.showMsg("收藏列表已变化，请刷新后重试"); }
        });
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

    @Override protected void onDestroy(){if(sortDialog!=null)sortDialog.dismiss();super.onDestroy();}
}
