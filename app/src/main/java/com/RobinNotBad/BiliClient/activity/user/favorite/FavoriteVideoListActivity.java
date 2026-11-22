package com.RobinNotBad.BiliClient.activity.user.favorite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

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
import com.RobinNotBad.BiliClient.util.FavoriteVideoOrder;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FavoriteVideoListActivity extends SearchableVideoListActivity {
    private static final int MANAGE_FAVORITES = 82;
    private static final int MANUAL_SORT = 85;
    private static final int MAX_REMOVAL_SELECTION = 20;
    private long mid;
    private long fid;
    private boolean allFolders;
    private boolean removing;
    private boolean openingPlayback;
    private volatile String videoOrder="mtime";
    private AlertDialog sortDialog;
    private AlertDialog removalDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mid = getIntent().getLongExtra("mid", 0);
        fid = getIntent().getLongExtra("fid", 0);
        allFolders = getIntent().getBooleanExtra("all_folders", false);
        videoOrder = FavoriteVideoOrder.read(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0), fid);
        setPageName(allFolders ? "全部收藏" : getIntent().getStringExtra("name"));
        startVideoList(savedInstanceState, allFolders ? R.string.library_search_all_favorites
                : R.string.library_search_favorites);
        if(!allFolders){
            android.view.View sort=findViewById(R.id.library_sort);sort.setVisibility(android.view.View.VISIBLE);
            updateSortDescription();sort.setOnClickListener(v->showSort());
        }
    }

    private void updateSortDescription(){findViewById(R.id.library_sort).setContentDescription("排序："+FavoriteVideoOrder.label(videoOrder));}
    private void showSort(){
        if(removing||(sortDialog!=null&&sortDialog.isShowing()))return;
        long account=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        Runnable manualEdit = mid == account && account > 0 ? () -> {
            if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) || isDestroyed()) return;
            startActivityForResult(FavoriteVideoSortActivity.intent(this, account, fid), MANUAL_SORT);
        } : null;
        sortDialog=FavoriteVideoOrder.dialog(this,videoOrder,order->{
            if(account!=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)||isDestroyed())return;
            videoOrder=order;FavoriteVideoOrder.save(account,fid,order);
            updateSortDescription();reloadVideos();
        },manualEdit);MsgUtil.prepareAlertDialog(sortDialog);sortDialog.show();
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
    @Override protected boolean canReloadVideos() { return !removing; }

    @Override protected void configureVideoAdapter() {
        videoCardAdapter.setOnItemClickListener(position -> {
            if (openingPlayback || removing || position < 0 || position >= videoList.size()) return;
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
            if (removing || openingPlayback || position < 0 || position >= videoList.size()
                    || mid <= 0 || mid != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
            VideoCard selected = videoList.get(position);
            if (allFolders) {
                startActivityForResult(new Intent(this, AddFavoriteActivity.class)
                        .putExtra("aid", selected.aid), MANAGE_FAVORITES);
                return;
            }
            if (selected.aid > 0 && fid > 0) showRemovalMenu(selected);
        });
    }

    private void showRemovalMenu(VideoCard selected) {
        if (removalDialog != null && removalDialog.isShowing()) return;
        showRemovalDialog(new AlertDialog.Builder(this).setTitle(selected.title)
                .setItems(new String[]{getString(R.string.favorite_remove_multi), getString(R.string.favorite_remove)},
                        (dialog, which) -> {
                            if (which == 0) chooseRemovals(selected.aid);
                            else confirmRemoval(selected);
                        })
                .setNegativeButton(android.R.string.cancel, null).create());
    }

    private FavoriteApi.RemovalSession removalSession() {
        try {
            return FavoriteApi.RemovalSession.capture(mid, fid);
        } catch (IOException error) {
            MsgUtil.showMsgLong(error.getMessage());
            return null;
        }
    }

    private void confirmRemoval(VideoCard card) {
        FavoriteApi.RemovalSession session = removalSession();
        if (session == null) return;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.favorite_remove_title)
                .setMessage(getString(R.string.favorite_remove_confirm, card.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.favorite_remove, null).create();
        showRemovalDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                view -> removeSelected(session, Collections.singletonList(card.aid), dialog));
    }

    private void chooseRemovals(long initialAid) {
        FavoriteApi.RemovalSession session = removalSession();
        if (session == null) return;
        ArrayList<VideoCard> cards = new ArrayList<>();
        for (VideoCard card : videoList) if (card.aid > 0) cards.add(card);
        if (cards.isEmpty()) return;
        String[] titles = new String[cards.size()];
        boolean[] checked = new boolean[cards.size()];
        Set<Long> selected = new LinkedHashSet<>();
        for (int i = 0; i < cards.size(); i++) {
            VideoCard card = cards.get(i);
            titles[i] = card.title;
            checked[i] = card.aid == initialAid;
            if (checked[i]) selected.add(card.aid);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.favorite_remove_multi_title)
                .setMultiChoiceItems(titles, checked, (choiceDialog, position, isChecked) -> {
                    long aid = cards.get(position).aid;
                    if (!isChecked) selected.remove(Long.valueOf(aid));
                    else if (selected.size() < MAX_REMOVAL_SELECTION) selected.add(aid);
                    else {
                        ((AlertDialog) choiceDialog).getListView().setItemChecked(position, false);
                        MsgUtil.showMsg(getString(R.string.favorite_remove_limit, MAX_REMOVAL_SELECTION));
                    }
                    updateRemovalButton((AlertDialog) choiceDialog, selected.size());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.favorite_remove, null).create();
        showRemovalDialog(dialog);
        updateRemovalButton(dialog, selected.size());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                view -> removeSelected(session, new ArrayList<>(selected), dialog));
    }

    private void updateRemovalButton(AlertDialog dialog, int count) {
        android.widget.Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        button.setText(getString(R.string.favorite_remove_selected, count));
        button.setEnabled(count > 0);
    }

    private void showRemovalDialog(AlertDialog dialog) {
        removalDialog = dialog;
        dialog.setCanceledOnTouchOutside(true);
        MsgUtil.prepareAlertDialog(dialog);
        dialog.setOnDismissListener(ignored -> {
            if (removalDialog == dialog) removalDialog = null;
        });
        dialog.show();
    }

    private void setRemoving(boolean value) {
        removing = value;
        swipeRefreshLayout.setEnabled(!value);
        for (int id : new int[]{R.id.library_keyword, R.id.library_search, R.id.library_clear, R.id.library_sort}) {
            View control = findViewById(id);
            control.setEnabled(!value);
        }
        setRefreshing(value);
    }

    private void removeSelected(FavoriteApi.RemovalSession session, List<Long> ids, AlertDialog dialog) {
        if (removing || ids.isEmpty() || ids.size() > MAX_REMOVAL_SELECTION) return;
        if (!session.isCurrent()) {
            dialog.dismiss();
            MsgUtil.showMsgLong(getString(R.string.favorite_remove_account_changed));
            return;
        }
        if (swipeRefreshLayout.isRefreshing()) {
            MsgUtil.showMsg(getString(R.string.favorite_remove_wait));
            return;
        }
        dialog.dismiss();
        setRemoving(true);
        CenterThreadPool.run(() -> {
            int removed = 0;
            int code = 0;
            boolean uncertain = false;
            try {
                // Reuse the same request as single-video removal; stop on the first failure.
                for (long aid : ids) {
                    code = session.remove(aid);
                    if (code != 0) break;
                    removed++;
                }
            } catch (Exception error) {
                uncertain = true;
            }
            final int count = removed, resultCode = code;
            final boolean checkResult = uncertain;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setRemoving(false);
                boolean accountUnchanged = session.isCurrent();
                if (count > 0 || checkResult || !accountUnchanged) reloadVideos();
                if (!accountUnchanged) MsgUtil.showMsgLong(getString(R.string.favorite_remove_account_changed));
                else if (checkResult) MsgUtil.showMsgLong(getString(R.string.favorite_remove_uncertain, count));
                else if (resultCode != 0) {
                    MsgUtil.showMsgLong(getString(R.string.favorite_remove_failed, count, resultCode));
                } else MsgUtil.showMsg(getString(R.string.favorite_remove_success, count));
            });
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_FAVORITES) reloadVideos();
        if (requestCode == MANUAL_SORT && resultCode == RESULT_OK
                && mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
            videoOrder = FavoriteVideoOrder.read(mid, fid);
            updateSortDescription();
            reloadVideos();
        }
    }

    @Override protected void onDestroy(){
        if(sortDialog!=null)sortDialog.dismiss();
        if(removalDialog!=null)removalDialog.dismiss();
        super.onDestroy();
    }
}
