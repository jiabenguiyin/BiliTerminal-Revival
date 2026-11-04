package com.RobinNotBad.BiliClient.activity.user.favorite;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.favorite.FavoriteFolderAdapter;
import com.RobinNotBad.BiliClient.api.FavoriteApi;
import com.RobinNotBad.BiliClient.model.FavoriteFolder;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.ArrayList;

//收藏夹列表
//2023-08-07
//2024-07-25

public class FavoriteFolderListActivity extends RefreshListActivity {
    private static final int EDIT_FOLDER = 84;
    private long accountMid;
    private int generation;
    private boolean closed;
    private boolean changing;
    private AlertDialog activeDialog;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageName("收藏");

        accountMid = currentAccount();
        setOnRefreshListener(() -> { if (!changing) loadFolders(); else setRefreshing(false); });
        loadFolders();
    }

    private long currentAccount() { return SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0); }
    private boolean available(long account) { return !closed && !isFinishing() && currentAccount() == account; }

    private void loadFolders() {
        int request = ++generation;
        long mid = accountMid = currentAccount();
        if (mid == 0) {
            emptyView.setText("请先登录");
            showEmptyView();
            setRefreshing(false);
            return;
        }
        setRefreshing(true);
        CenterThreadPool.run(() -> {
            try {
                ArrayList<FavoriteFolder> folderList = FavoriteApi.getFavoriteFolders(mid);
                runOnUiThread(() -> {
                    if (request != generation || !available(mid)) return;
                    FavoriteFolderAdapter adapter = new FavoriteFolderAdapter(this, folderList, mid);
                    adapter.setOnFolderLongClickListener(this::showFolderMenu);
                    setAdapter(adapter);
                    hideEmptyView();
                    setRefreshing(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (request != generation || !available(mid)) return;
                    setRefreshing(false);
                    MsgUtil.showMsgLong("收藏夹加载失败，请下拉重试");
                });
            }
        });

    }

    private boolean showFolderMenu(FavoriteFolder folder) {
        if (!available(accountMid) || accountMid == 0 || folder.ownerMid != accountMid
                || changing || (activeDialog != null && activeDialog.isShowing())) return false;
        final long selectedAccount=accountMid;
        AlertDialog menu = new AlertDialog.Builder(this).setTitle(folder.name)
                .setItems(new String[]{"编辑", "删除", "排序"}, (dialog, which) -> {
                    if (which == 0) startActivityForResult(new Intent(this, FavoriteFolderEditActivity.class)
                            .putExtra("media_id", folder.id).putExtra("owner_mid", accountMid), EDIT_FOLDER);
                    else if(which==1) prepareDelete(folder);
                    else showDialog(com.RobinNotBad.BiliClient.util.FavoriteVideoOrder.dialog(this,
                            com.RobinNotBad.BiliClient.util.FavoriteVideoOrder.read(selectedAccount,folder.id),order->{
                        if(!available(selectedAccount))return;
                        com.RobinNotBad.BiliClient.util.FavoriteVideoOrder.save(selectedAccount,folder.id,order);
                        startActivity(new Intent(this,FavoriteVideoListActivity.class).putExtra("mid",selectedAccount)
                                .putExtra("fid",folder.id).putExtra("name",folder.name));
                    }));
                }).create();
        showDialog(menu);
        return true;
    }

    private void showDialog(AlertDialog dialog) {
        activeDialog = dialog;
        MsgUtil.prepareAlertDialog(dialog);
        dialog.setOnDismissListener(ignored -> { if (activeDialog == dialog) activeDialog = null; });
        dialog.show();
    }

    private void prepareDelete(FavoriteFolder selected) {
        if (!available(accountMid) || changing) return;
        long account = accountMid;
        changing = true;
        setRefreshing(true);
        CenterThreadPool.run(() -> {
            try {
                FavoriteFolder folder = FavoriteApi.getFavoriteFolderInfo(selected.id);
                runOnUiThread(() -> {
                    if (!available(account)) return;
                    changing = false;
                    setRefreshing(false);
                    if (folder.ownerMid != account) { MsgUtil.showMsg("只能删除自己的收藏夹"); return; }
                    if (folder.isDefault()) { MsgUtil.showMsg("默认收藏夹不能删除"); return; }
                    confirmDelete(folder, account);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!available(account)) return;
                    changing = false;
                    setRefreshing(false);
                    MsgUtil.showMsgLong("读取失败，未删除收藏夹：" + error.getMessage());
                });
            }
        });
    }

    private void confirmDelete(FavoriteFolder folder, long account) {
        AlertDialog confirm = new AlertDialog.Builder(this).setTitle("删除收藏夹")
                .setMessage("确定删除「" + folder.name + "」及其中的 " + folder.videoCount + " 个收藏吗？\n"
                        + "此操作无法撤销。视频若还保存在其他收藏夹中，会继续保留在那里。")
                .setNegativeButton("取消", null).setPositiveButton("删除", null).create();
        showDialog(confirm);
        confirm.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!available(account) || changing) { confirm.dismiss(); return; }
            changing = true;
            confirm.setCancelable(false);
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            confirm.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            CenterThreadPool.run(() -> {
                try {
                    ApiResult result = FavoriteApi.deleteFavoriteFolder(folder);
                    runOnUiThread(() -> {
                        if (!available(account)) return;
                        changing = false;
                        confirm.dismiss();
                        if (result.code == 0) { MsgUtil.showMsg("收藏夹已删除"); loadFolders(); }
                        else MsgUtil.showMsgLong("删除失败：" + result.message + "（" + result.code + "）");
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (!available(account)) return;
                        changing = false;
                        confirm.dismiss();
                        MsgUtil.showMsgLong("删除失败，请刷新确认收藏夹状态：" + error.getMessage());
                    });
                }
            });
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_FOLDER && resultCode == RESULT_OK) loadFolders();
    }

    @Override protected void onResume() {
        super.onResume();
        if (accountMid != currentAccount()) {
            if (activeDialog != null) activeDialog.dismiss();
            changing = false;
            setAdapter(new FavoriteFolderAdapter(this, new ArrayList<>(), currentAccount()));
            loadFolders();
        }
    }

    @Override protected void onDestroy() {
        closed = true;
        generation++;
        if (activeDialog != null) activeDialog.dismiss();
        super.onDestroy();
    }
}
