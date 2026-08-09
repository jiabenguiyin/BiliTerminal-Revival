package com.RobinNotBad.BiliClient.activity.reply;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment;
import com.RobinNotBad.BiliClient.adapter.ReplyAdapter;
import com.RobinNotBad.BiliClient.api.ReplyApi;
import com.RobinNotBad.BiliClient.event.ReplyEvent;
import com.RobinNotBad.BiliClient.model.ContentType;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

//视频下评论页面，评论详情见ReplyInfoActivity
//部分通用代码在VideoReplyAdapter内
//2023-07-22

public class ReplyFragment extends RefreshListFragment {

    private boolean dontload;
    protected long aid, mid;
    protected int sort = 3;
    protected int type;
    protected int count;
    protected ArrayList<Reply> replyList;
    protected ReplyAdapter replyAdapter;
    public int replyType = ReplyApi.REPLY_TYPE_VIDEO;
    private long seek;
    private String pagination = "";
    private boolean isManager = false;
    private boolean legacyPaging = false;

    public static ReplyFragment newInstance(long aid, int type) {
        ReplyFragment fragment = new ReplyFragment();
        Bundle args = new Bundle();
        args.putLong("aid", aid);
        args.putInt("type", type);
        fragment.setArguments(args);
        return fragment;
    }

    public static ReplyFragment newInstance(long aid, int type, boolean dontload) {
        ReplyFragment fragment = new ReplyFragment();
        Bundle args = new Bundle();
        args.putLong("aid", aid);
        args.putInt("type", type);
        args.putBoolean("dontload", dontload);
        fragment.setArguments(args);
        return fragment;
    }


    public static ReplyFragment newInstance(long aid, int type, long seek_rpid) {
        ReplyFragment fragment = new ReplyFragment();
        Bundle args = new Bundle();
        args.putLong("aid", aid);
        args.putInt("type", type);
        args.putLong("seek", seek_rpid);
        fragment.setArguments(args);
        return fragment;
    }

    public static ReplyFragment newInstance(long aid, int type, boolean dontload, long seek_rpid) {
        ReplyFragment fragment = new ReplyFragment();
        Bundle args = new Bundle();
        args.putLong("aid", aid);
        args.putInt("type", type);
        args.putBoolean("dontload", dontload);
        args.putLong("seek", seek_rpid);
        fragment.setArguments(args);
        return fragment;
    }

    public static ReplyFragment newInstance(long aid, int type, int count, long seek_rpid, long up_mid) {
        ReplyFragment fragment = new ReplyFragment();
        Bundle args = new Bundle();
        args.putLong("aid", aid);
        args.putInt("count", count);
        args.putInt("type", type);
        args.putLong("seek", seek_rpid);
        args.putLong("mid", up_mid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            aid = getArguments().getLong("aid", 0);
            count = getArguments().getInt("count", 0);
            type = getArguments().getInt("type", 0);
            replyType = type;
            dontload = getArguments().getBoolean("dontload", false);
            seek = getArguments().getLong("seek", -1);
            mid = getArguments().getLong("mid", -1);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        setForceSingleColumn();
        super.onViewCreated(view, savedInstanceState);

        if (SharedPreferencesUtil.getBoolean("ui_landscape", false)) {
            WindowManager windowManager = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            if (Build.VERSION.SDK_INT >= 17) display.getRealMetrics(metrics);
            else display.getMetrics(metrics);
            int paddings = metrics.widthPixels / 6;
            recyclerView.setPadding(paddings, 0, paddings, 0);
        }

        setOnRefreshListener(() -> refresh(aid));
        setOnLoadMoreListener(this::continueLoading);

        Log.e("debug-av号", String.valueOf(aid));

        replyList = new ArrayList<>();
        replyAdapter = createReplyAdapter();
        replyAdapter.count = count;
        replyAdapter.isManager = isManager;
        setOnSortSwitch();
        setAdapter(replyAdapter);
        emptyView.setOnClickListener(v -> refresh(aid));

        if (!dontload) refresh(aid);
    }

    public void setManager(Object source) {
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) return;

        try {
            if (source != null) {
                if (source instanceof List<?>) {
                    List<UserInfo> staffs = (List<UserInfo>) source;
                    for (UserInfo userInfo : staffs) {
                        if (userInfo.mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
                            isManager = true;
                            break;
                        }
                    }
                } else if (source instanceof UserInfo) {
                    isManager = ((UserInfo) source).mid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
                }
            }
        } catch (Exception e) {
            MsgUtil.err(e);
        }
    }

    private ReplyAdapter createReplyAdapter() {
        return new ReplyAdapter(requireContext(), replyList, aid, 0, type, sort, mid);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void continueLoading(int page) {
        CenterThreadPool.run(() -> {
            try {
                List<Reply> list = new ArrayList<>();
                int result;
                if (legacyPaging) {
                    result = ReplyApi.getReplies(aid, 0, page,
                            ContentType.getContentType(type), getLegacySort(), list);
                } else {
                    Pair<Integer, String> pageState = ReplyApi.getRepliesLazy(aid, 0, pagination, type, sort, list);
                    result = pageState.first;
                    this.pagination = pageState.second;
                }
                setRefreshing(false);
                if (result != -1) {
                    Log.e("debug", "下一页");
                    runOnUiThread(() -> {
                        int insertStart = replyList.size();
                        replyList.addAll(list);
                        if (replyAdapter != null)
                            replyAdapter.notifyItemRangeInserted(insertStart, list.size());
                    });
                    if (result == 1) {
                        Log.e("debug", "到底了");
                        bottom = true;
                    }
                }
            } catch (Exception e) {
                loadFail(e);
            }
        });
    }

    public void notifyReplyInserted(ReplyEvent replyEvent) {
        if (replyEvent.getOid() != aid) return;
        if (replyList == null || replyAdapter == null) return;
        Reply reply = replyEvent.getMessage();
        showReplyList();
        if (reply.root == 0) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) Objects.requireNonNull(recyclerView.getLayoutManager());
            int pos = 0;
            replyList.add(pos, reply);
            count = Math.max(count + 1, replyList.size());
            replyAdapter.count = count;
            int finalPos = pos;
            runOnUiThread(() -> {
                replyAdapter.notifyItemChanged(0);
                replyAdapter.notifyItemInserted(finalPos + 1);
                layoutManager.scrollToPositionWithOffset(finalPos + 1, 0);
            });
        } else if (replyEvent.getPos() >= 0) {
            replyList.get(replyEvent.getPos()).childMsgList.add(reply);
            replyList.get(replyEvent.getPos()).childCount++;
            runOnUiThread(() -> replyAdapter.notifyItemChanged(replyEvent.getPos() + 1));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refresh(long aid) {
        pagination = "";
        page = 1;
        legacyPaging = false;
        this.aid = aid;
        showReplyList();
        setRefreshing(true);
        CenterThreadPool.run(() -> {
            try {
                List<Reply> list = new ArrayList<>();
                Pair<Integer, String> pageState = loadFirstPage(list);
                int result = pageState.first;
                this.pagination = pageState.second;
                setRefreshing(false);
                if (result == -1) {
                    showReplyMessage("评论加载失败\n点击重试");
                    return;
                }
                if (isAdded()) {
                    runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (replyList != null) replyList.clear();
                        else replyList = new ArrayList<>();
                        replyList.addAll(list);
                        if (replyList.isEmpty()) {
                            count = 0;
                            replyAdapter.count = 0;
                        }
                        replyAdapter.notifyDataSetChanged();
                        showReplyList();
                    });
                    //replyAdapter.notifyItemRangeInserted(0,replyList.size());
                    if (result == 1) {
                        Log.e("debug", "到底了");
                        bottom = true;
                    } else bottom = false;
                }
            } catch (Exception e) {
                loadFail(e);
                showReplyMessage("评论加载失败\n点击重试");
            }
        });
    }

    private Pair<Integer, String> loadFirstPage(List<Reply> list) throws Exception {
        Exception lazyError = null;
        try {
            Pair<Integer, String> pageState = ReplyApi.getRepliesLazy(aid, seek, pagination, type, sort, list);
            if (pageState.first != -1) return pageState;
        } catch (Exception e) {
            lazyError = e;
        }

        list.clear();
        try {
            int result = ReplyApi.getReplies(aid, 0, 1,
                    ContentType.getContentType(type), getLegacySort(), list);
            if (result != -1) {
                legacyPaging = true;
                return new Pair<>(result, "");
            }
        } catch (Exception e) {
            if (lazyError == null) lazyError = e;
        }

        if (lazyError != null) throw lazyError;
        return new Pair<>(-1, "");
    }

    private int getLegacySort() {
        return sort == 3 ? 2 : 0;
    }

    private void showReplyList() {
        runOnUiThread(() -> {
            if (emptyView != null) emptyView.setVisibility(View.GONE);
            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
        });
    }

    private void showReplyMessage(String message) {
        runOnUiThread(() -> {
            if (emptyView == null || recyclerView == null) return;
            emptyView.setText(message);
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        });
    }

    private void setOnSortSwitch() {
        replyAdapter.setOnSortSwitchListener(position -> {
            sort = (sort == 2 ? 3 : 2);
            replyAdapter.sort = this.sort;
            refresh(aid);
        });
    }
}
