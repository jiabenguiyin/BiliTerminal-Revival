package com.RobinNotBad.BiliClient.activity.user;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;

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
    private EditText searchInput;
    private String searchKeyword = "";
    private int listGeneration;

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

        if (mode == 0) setupFollowingSearch();

        recyclerView.setHasFixedSize(true);

        userList = new ArrayList<>();
        setOnRefreshListener(this::reloadList);
        setOnLoadMoreListener(this::continueLoading);

        if (groupMode) {
            loadGroupMode();
        } else {
            loadNormalMode();
        }
    }

    private void setupFollowingSearch() {
        RelativeLayout root = (RelativeLayout) swipeRefreshLayout.getParent();
        View bar = LayoutInflater.from(this).inflate(com.RobinNotBad.BiliClient.R.layout.view_library_search, root, false);
        RelativeLayout.LayoutParams barParams = new RelativeLayout.LayoutParams(-1, -2);
        barParams.addRule(RelativeLayout.BELOW, com.RobinNotBad.BiliClient.R.id.top);
        root.addView(bar, barParams);
        RelativeLayout.LayoutParams listParams = (RelativeLayout.LayoutParams) swipeRefreshLayout.getLayoutParams();
        listParams.addRule(RelativeLayout.BELOW, com.RobinNotBad.BiliClient.R.id.library_search_bar);
        swipeRefreshLayout.setLayoutParams(listParams);
        RelativeLayout.LayoutParams emptyParams = (RelativeLayout.LayoutParams) emptyView.getLayoutParams();
        emptyParams.addRule(RelativeLayout.BELOW, com.RobinNotBad.BiliClient.R.id.library_search_bar);
        emptyView.setLayoutParams(emptyParams);

        searchInput = bar.findViewById(com.RobinNotBad.BiliClient.R.id.library_keyword);
        searchInput.setHint("搜索关注的用户");
        View clear = bar.findViewById(com.RobinNotBad.BiliClient.R.id.library_clear);
        clear.setVisibility(View.INVISIBLE);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        clear.setOnClickListener(v -> {
            searchInput.setText("");
            submitSearch();
        });
        bar.findViewById(com.RobinNotBad.BiliClient.R.id.library_search).setOnClickListener(v -> submitSearch());
        searchInput.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                submitSearch();
                return true;
            }
            return false;
        });
    }

    private void submitSearch() {
        searchKeyword = searchInput.getText().toString().trim();
        searchInput.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        reloadList();
    }

    private void reloadList() {
        listGeneration++;
        page = 1;
        bottom = false;
        userList.clear();
        if (mode == 0 && groupMode && searchKeyword.isEmpty()) {
            loadGroupMode();
            return;
        }
        adapter = new UserListAdapter(this, userList);
        if (canCancelFollow()) {
            adapter.setOnItemLongClickListener((position, user) -> confirmCancelFollow(user, () -> {
                if (position >= 0 && position < userList.size() && userList.get(position).mid == user.mid) {
                    userList.remove(position);
                    adapter.notifyItemRemoved(position);
                }
            }));
        }
        setAdapter(adapter);
        loadNormalPage(1, listGeneration, searchKeyword);
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
        reloadList();
    }

    private void loadNormalPage(int requestedPage, int generation, String keyword) {
        setRefreshing(true);
        CenterThreadPool.run(() -> {
            List<UserInfo> resultList = new ArrayList<>();
            try {
                int result = mode == 0 && !keyword.isEmpty()
                        ? FollowApi.searchFollowingList(mid, requestedPage, keyword, resultList)
                        : mode == 0 ? FollowApi.getFollowingList(mid, requestedPage, resultList)
                        : FollowApi.getFollowerList(mid, requestedPage, resultList);
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != listGeneration) return;
                    int first = userList.size();
                    userList.addAll(resultList);
                    adapter.notifyItemRangeInserted(first, resultList.size());
                    page = requestedPage;
                    bottom = result == 1 || (!keyword.isEmpty() && requestedPage >= 5);
                    setRefreshing(false);
                    if (userList.isEmpty()) {
                        emptyView.setText(keyword.isEmpty() ? "暂无内容" : "没有找到匹配的关注用户");
                        showEmptyView();
                    } else hideEmptyView();
                    if (bottom) Log.e("debug", "到底了");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != listGeneration) return;
                    if (e.getMessage() != null && (e.getMessage().startsWith("22115") || e.getMessage().startsWith("22118"))) {
                        finish();
                        MsgUtil.showMsg(e.getMessage());
                    } else {
                        loadFail(e);
                    }
                });
            }
        });
    }

    private void loadGroupMode() {
        final int generation = listGeneration;
        setRefreshing(true);
        CenterThreadPool.run(() -> {
            try {
                List<FollowTag> tagList = FollowApi.getFollowTags();
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != listGeneration || !searchKeyword.isEmpty()) return;
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
                if (generation != listGeneration) return;
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
        if (groupMode && searchKeyword.isEmpty()) {
            setRefreshing(false);
            return;
        }
        loadNormalPage(page, listGeneration, searchKeyword);
    }

    @Override protected void onDestroy() {
        listGeneration++;
        if (cancelFollowDialog != null) cancelFollowDialog.dismiss();
        super.onDestroy();
    }
}
