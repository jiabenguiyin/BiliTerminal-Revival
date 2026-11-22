package com.RobinNotBad.BiliClient.activity.user.favorite;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter;
import com.RobinNotBad.BiliClient.api.FavoriteApi;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.FavoriteFolder;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.ui.TerminalDialogStyle;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.FavoriteVideoOrder;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FavoriteVideoSortActivity extends RefreshListActivity {
    private final ArrayList<VideoCard> videos = new ArrayList<>();
    private final ArrayList<VideoCard> originalOrder = new ArrayList<>();
    private final ArrayList<String> moves = new ArrayList<>();
    private final Set<Long> loadedIds = new HashSet<>();
    private long account;
    private long folderId;
    private FavoriteFolder folder;
    private int nextPage = 1;
    private boolean loading;
    private boolean saving;
    private VideoCard dragged;
    private int dragStart;
    private VideoCardAdapter adapter;
    private TextView save;
    private View cancel;
    private AlertDialog dialog;

    static Intent intent(Context context, long account, long folderId) {
        return new Intent(context, FavoriteVideoSortActivity.class)
                .putExtra("owner_mid", account).putExtra("media_id", folderId);
    }

    @Override protected void onCreate(Bundle state) {
        setForceSingleColumn();
        super.onCreate(state);
        account = getIntent().getLongExtra("owner_mid", 0);
        folderId = getIntent().getLongExtra("media_id", 0);
        if (folderId <= 0 || !isCurrent()) {
            MsgUtil.showMsg("只能调整自己的收藏夹");
            finish();
            return;
        }
        setPageName("手动排序");
        installActions();
        adapter = new VideoCardAdapter(this, videos);
        adapter.setOnItemClickListener(this::choosePosition);
        setAdapter(adapter);
        attachDrag();
        setOnLoadMoreListener(ignored -> loadNextPage());
        emptyView.setOnClickListener(view -> loadNextPage());
        loadNextPage();
    }

    private void installActions() {
        RelativeLayout root = (RelativeLayout) swipeRefreshLayout.getParent();
        View actions = getLayoutInflater().inflate(R.layout.view_favorite_sort_actions, root, false);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(-1, -2);
        params.addRule(RelativeLayout.BELOW, R.id.top);
        root.addView(actions, params);
        for (View view : new View[]{swipeRefreshLayout, emptyView}) {
            RelativeLayout.LayoutParams layout = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layout.addRule(RelativeLayout.BELOW, R.id.favorite_sort_actions);
            view.setLayoutParams(layout);
        }
        save = actions.findViewById(R.id.favorite_sort_save);
        cancel = actions.findViewById(R.id.favorite_sort_cancel);
        save.setOnClickListener(view -> saveOrder());
        cancel.setOnClickListener(view -> closeEditor());
    }

    private boolean isCurrent() {
        return account > 0 && account == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
    }

    private boolean canEdit() {
        return !isDestroyed() && isCurrent() && !saving && !loading;
    }

    private void loadNextPage() {
        if (!canEdit() || bottom || dragged != null) {
            if (!loading && !saving) setRefreshing(false);
            return;
        }
        int requestedPage = nextPage;
        FavoriteFolder currentFolder = folder;
        loading = true;
        setRefreshing(true);
        updateActions();
        CenterThreadPool.run(() -> {
            try {
                FavoriteFolder verified = currentFolder == null ? FavoriteApi.getFavoriteFolderInfo(folderId) : currentFolder;
                if (verified.ownerMid != account || !isCurrent()) throw new IOException("只能调整自己的收藏夹");
                ArrayList<VideoCard> rows = new ArrayList<>();
                ApiResult result = FavoriteApi.getFavoriteVideos(folderId, requestedPage, "", false, rows,
                        FavoriteVideoOrder.FOLDER_ORDER);
                if (result.code != 0) throw new IOException("收藏夹读取失败（" + result.code + "）");
                runOnUiThread(() -> {
                    if (!finishRequest()) return;
                    folder = verified;
                    int before = videos.size();
                    for (VideoCard row : rows) {
                        if (row.aid > 0 && loadedIds.add(row.aid)) {
                            videos.add(row);
                            originalOrder.add(row);
                        }
                    }
                    bottom = result.isBottom;
                    nextPage++;
                    adapter.notifyItemRangeInserted(before, videos.size() - before);
                    if (videos.isEmpty()) {
                        emptyView.setText(bottom ? "没有可排序的视频" : "暂无视频，点击继续加载");
                        showEmptyView();
                    } else {
                        hideEmptyView();
                    }
                    updateActions();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!finishRequest()) return;
                    if (videos.isEmpty()) {
                        emptyView.setText("读取失败，点击重试");
                        showEmptyView();
                    }
                    MsgUtil.showMsgLong(error.getMessage());
                    updateActions();
                });
            }
        });
    }

    private boolean finishRequest() {
        if (isDestroyed()) return false;
        if (!isCurrent()) {
            MsgUtil.showMsg("账号已切换，请重新进入收藏夹");
            finish();
            return false;
        }
        loading = false;
        saving = false;
        setRefreshing(false);
        return true;
    }

    private void attachDrag() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean isLongPressDragEnabled() { return canEdit() && dragged == null; }

            @Override public void onSelectedChanged(RecyclerView.ViewHolder holder, int state) {
                super.onSelectedChanged(holder, state);
                if (state == ItemTouchHelper.ACTION_STATE_DRAG && holder != null && canEdit()) {
                    int position = holder.getAdapterPosition();
                    if (position >= 0 && position < videos.size()) {
                        dragStart = position;
                        dragged = videos.get(position);
                        updateActions();
                    }
                }
            }

            @Override public boolean onMove(RecyclerView list, RecyclerView.ViewHolder from, RecyclerView.ViewHolder to) {
                return canEdit() && dragged != null && moveRow(from.getAdapterPosition(), to.getAdapterPosition());
            }

            @Override public void onSwiped(RecyclerView.ViewHolder holder, int direction) { }

            @Override public void clearView(RecyclerView list, RecyclerView.ViewHolder holder) {
                super.clearView(list, holder);
                if (dragged == null) return;
                int position = videos.indexOf(dragged);
                dragged = null;
                if (position >= 0 && position != dragStart) recordMove(position);
                else updateActions();
            }
        }).attachToRecyclerView(recyclerView);
    }

    private boolean moveRow(int from, int to) {
        if (from < 0 || from >= videos.size() || to < 0 || to >= videos.size() || from == to) return false;
        videos.add(to, videos.remove(from));
        adapter.notifyItemMoved(from, to);
        return true;
    }

    private void recordMove(int to) {
        if (videos.equals(originalOrder)) {
            moves.clear();
        } else {
            long predecessor = to == 0 ? 0 : videos.get(to - 1).aid;
            moves.add(FavoriteApi.videoSortMove(predecessor, videos.get(to).aid));
        }
        updateActions();
    }

    private void choosePosition(int position) {
        if (!canEdit() || dragged != null || position < 0 || position >= videos.size()) return;
        VideoCard video = videos.get(position);
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Integer> targets = new ArrayList<>();
        if (position > 0) { labels.add("上移一位"); targets.add(position - 1); }
        if (position + 1 < videos.size()) { labels.add("下移一位"); targets.add(position + 1); }
        if (position > 1) { labels.add("移到最前"); targets.add(0); }
        if (labels.isEmpty()) return;
        showDialog(new AlertDialog.Builder(this).setTitle(video.title)
                .setItems(labels.toArray(new String[0]), (ignored, which) -> {
                    int target = targets.get(which);
                    if (canEdit() && moveRow(videos.indexOf(video), target)) {
                        recordMove(target);
                        recyclerView.scrollToPosition(target);
                    }
                }).setNegativeButton("取消", null).create());
    }

    private void updateActions() {
        save.setEnabled(canEdit() && dragged == null && !moves.isEmpty());
        save.setText(saving ? "保存中…" : "保存顺序");
        cancel.setEnabled(!saving);
    }

    private void saveOrder() {
        if (!canEdit() || dragged != null || folder == null || moves.isEmpty()) return;
        FavoriteFolder target = folder;
        String sort = TextUtils.join(",", moves);
        saving = true;
        setRefreshing(true);
        updateActions();
        CenterThreadPool.run(() -> {
            try {
                ApiResult result = FavoriteApi.sortFavoriteVideos(target, sort);
                runOnUiThread(() -> {
                    if (!finishRequest()) return;
                    if (result.code == 0) {
                        FavoriteVideoOrder.save(account, folderId, FavoriteVideoOrder.FOLDER_ORDER);
                        setResult(RESULT_OK);
                        MsgUtil.showMsg("顺序已保存");
                        finish();
                    } else {
                        updateActions();
                        MsgUtil.showMsgLong("保存失败（" + result.code + "）：" + result.message);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!finishRequest()) return;
                    updateActions();
                    MsgUtil.showMsgLong("保存结果未确认，请重试或返回收藏夹核对");
                });
            }
        });
    }

    private void closeEditor() {
        if (saving) return;
        if (moves.isEmpty()) {
            finish();
        } else {
            showDialog(new AlertDialog.Builder(this).setTitle("放弃本次排序？")
                    .setMessage("调整尚未保存。")
                    .setNegativeButton("继续编辑", null)
                    .setPositiveButton("放弃", (ignored, which) -> finish()).create());
        }
    }

    private void showDialog(AlertDialog next) {
        if (dialog != null && dialog.isShowing()) return;
        dialog = next;
        next.setOnDismissListener(ignored -> dialog = null);
        TerminalDialogStyle.prepare(next);
        next.show();
    }

    @Override public void onBackPressed() {
        if (!SharedPreferencesUtil.getBoolean("back_disable", false)) closeEditor();
    }

    @Override protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }
}
