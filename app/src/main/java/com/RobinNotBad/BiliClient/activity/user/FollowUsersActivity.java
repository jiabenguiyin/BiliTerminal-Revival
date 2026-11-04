package com.RobinNotBad.BiliClient.activity.user;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.user.FollowGroupAdapter;
import com.RobinNotBad.BiliClient.adapter.user.UserListAdapter;
import com.RobinNotBad.BiliClient.api.FollowApi;
import com.RobinNotBad.BiliClient.api.UserInfoApi;
import com.RobinNotBad.BiliClient.model.FollowTag;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.List;

//关注列表
//2023-07-22
//2024-05-01

public class FollowUsersActivity extends RefreshListActivity {
    private AlertDialog cancelFollowDialog;

    private long mid;
    private ArrayList<UserInfo> userList;
    private UserListAdapter adapter;
    private FollowGroupAdapter groupAdapter;
    private int mode;
    private boolean groupMode;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mode = getIntent().getIntExtra("mode", 0);
        mid = getIntent().getLongExtra("mid", -1);

        if (mode < 0 || mode > 1 || mid == -1) {
            finish();
            return;
        }

        long currentUserMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        groupMode = mode == 0 && mid == currentUserMid && SharedPreferencesUtil.getBoolean("follow_group_mode", false);

        setPageName(mode == 0 ? "关注列表" : "粉丝列表");

        recyclerView.setHasFixedSize(true);

        userList = new ArrayList<>();

        if (groupMode) {
            loadGroupMode();
        } else {
            loadNormalMode();
        }
    }

    private boolean canCancelFollow() {
        return mode == 0 && mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
    }

    private boolean confirmCancelFollow(UserInfo user, Runnable onSuccess) {
        if (!canCancelFollow() || user == null) return false;
        if (cancelFollowDialog != null && cancelFollowDialog.isShowing()) return true;
        final long accountMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        final boolean[] submitted = {false};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("取消关注")
                .setMessage("确定取消关注「" + user.name + "」吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("取消关注", null)
                .create();
        cancelFollowDialog = dialog;
        dialog.setOnDismissListener(ignored -> { if (cancelFollowDialog == dialog) cancelFollowDialog = null; });
        com.RobinNotBad.BiliClient.ui.TerminalDialogStyle.prepareSeparatedActions(dialog);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (submitted[0]) return;
            submitted[0] = true;
            dialog.dismiss();
            CenterThreadPool.run(() -> {
                try {
                    if (accountMid != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
                        MsgUtil.showMsg("账号已切换，请重新操作");return;
                    }
                    int code = UserInfoApi.followUser(user.mid, false, user.relationAttribute);
                    if (code == 0) {
                        runOnUiThread(onSuccess);
                        MsgUtil.showMsg("已取消关注");
                    } else {
                        MsgUtil.showMsg("取消关注失败：" + code);
                    }
                } catch (Exception e) {
                    MsgUtil.err(e);
                }
            });
        });
        return true;
    }

    private void loadNormalMode() {
        CenterThreadPool.run(() -> {
            try {
                int result = mode == 0 ? FollowApi.getFollowingList(mid, page, userList) : FollowApi.getFollowerList(mid, page, userList);
                adapter = new UserListAdapter(this, userList);
                if (canCancelFollow()) {
                    adapter.setOnItemLongClickListener((position, user) ->
                            confirmCancelFollow(user, () -> {
                                if (position >= 0 && position < userList.size()
                                        && userList.get(position).mid == user.mid) {
                                    userList.remove(position);
                                    adapter.notifyItemRemoved(position);
                                }
                            }));
                }
                setOnLoadMoreListener(this::continueLoading);
                setRefreshing(false);
                setAdapter(adapter);

                if (result == 1) {
                    Log.e("debug", "到底了");
                    setBottom(true);
                }
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().startsWith("22115") || e.getMessage().startsWith("22118"))) {
                    finish();
                    MsgUtil.showMsg(e.getMessage());
                } else {
                    loadFail(e);
                }
            }
        });
    }

    private void loadGroupMode() {
        CenterThreadPool.run(() -> {
            try {
                List<FollowTag> tagList = FollowApi.getFollowTags();
                runOnUiThread(() -> {
                    groupAdapter = new FollowGroupAdapter(this);
                    groupAdapter.setOnGroupExpandListener(tagid -> loadGroupUsers(tagid));
                    if (canCancelFollow()) {
                        groupAdapter.setOnUserLongClickListener(user ->
                                confirmCancelFollow(user, () -> groupAdapter.removeUser(user.mid)));
                    }
                    setAdapter(groupAdapter);
                    for (FollowTag tag : tagList) {
                        if (tag.count > 0) {
                            groupAdapter.addGroup(tag, new ArrayList<>());
                        }
                    }
                    groupAdapter.notifyDataSetChanged();
                    setRefreshing(false);
                });
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().startsWith("22115") || e.getMessage().startsWith("22118"))) {
                    finish();
                    MsgUtil.showMsg(e.getMessage());
                } else {
                    loadFail(e);
                }
            }
        });
    }

    public void loadGroupUsers(int tagid) {
        CenterThreadPool.run(() -> {
            try {
                List<UserInfo> tagUsers = new ArrayList<>();
                int result = FollowApi.getFollowTagUsers(tagid, 1, tagUsers);
                runOnUiThread(() -> {
                    groupAdapter.updateGroupUsers(tagid, tagUsers);
                });
                if (result == 0 && tagUsers.size() == 20) {
                    loadMoreGroupUsers(tagid, tagUsers.size());
                }
            } catch (Exception e) {
                Log.e("debug", "加载分组用户失败", e);
            }
        });
    }

    private void loadMoreGroupUsers(int tagid, int currentCount) {
        CenterThreadPool.run(() -> {
            try {
                int page = (currentCount / 20) + 1;
                List<UserInfo> tagUsers = new ArrayList<>();
                int result = FollowApi.getFollowTagUsers(tagid, page, tagUsers);
                runOnUiThread(() -> {
                    groupAdapter.addGroupUsers(tagid, tagUsers);
                });
                if (result == 0 && tagUsers.size() == 20) {
                    loadMoreGroupUsers(tagid, currentCount + tagUsers.size());
                }
            } catch (Exception e) {
                Log.e("debug", "加载分组用户失败", e);
            }
        });
    }

    private void continueLoading(int page) {
        if (groupMode) {
            setRefreshing(false);
            return;
        }
        CenterThreadPool.run(() -> {
            try {
                List<UserInfo> list = new ArrayList<>();
                int result = mode == 0 ? FollowApi.getFollowingList(mid, page, list) : FollowApi.getFollowerList(mid, page, list);
                Log.e("debug", "下一页");
                runOnUiThread(() -> {
                    userList.addAll(list);
                    adapter.notifyItemRangeInserted(userList.size() - list.size(), list.size());
                });
                if (result == 1) {
                    Log.e("debug", "到底了");
                    setBottom(true);
                }
                setRefreshing(false);
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().startsWith("22115") || e.getMessage().startsWith("22118"))) {
                    finish();
                    MsgUtil.showMsg(e.getMessage());
                } else {
                    loadFail(e);
                }
            }
        });
    }
}
