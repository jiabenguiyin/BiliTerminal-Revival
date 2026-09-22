package com.RobinNotBad.BiliClient.activity.reply;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.adapter.ReplyAdapter;
import com.RobinNotBad.BiliClient.api.ReplyApi;
import com.RobinNotBad.BiliClient.event.ReplyEvent;
import com.RobinNotBad.BiliClient.model.ContentType;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

//评论详细信息
//2023-07-22

public class ReplyInfoActivity extends BaseActivity {

    private long oid, rpid, up_mid;
    private long seekReply;
    private int sort = 0;
    private boolean isManager;
    private ContentType type;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private ArrayList<Reply> replyList;
    private ReplyAdapter replyAdapter;
    private boolean bottom = false;
    private int page = 1;
    private boolean refreshing = false;
    private final Runnable clearHighlightRunnable = () -> {
        if (replyAdapter != null) replyAdapter.clearHighlight();
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_refresh);

        Intent intent = getIntent();
        rpid = intent.getLongExtra("rpid", 0);
        seekReply = intent.getLongExtra("seekReply", -1);
        oid = intent.getLongExtra("oid", 0);
        try {
            type = ContentType.getContentType(intent.getIntExtra("type", 1));
        } catch (ContentType.TerminalIllegalTypeCodeException e) {
            throw new RuntimeException(e);
        }
        up_mid = intent.getLongExtra("up_mid", -1);
        isManager = intent.getBooleanExtra("is_manager", false);

        refreshLayout = findViewById(R.id.swipeRefreshLayout);
        recyclerView = findViewById(R.id.recyclerView);
        refreshLayout.setOnRefreshListener(this::refresh);

        setPageName("评论详情");

        if (SharedPreferencesUtil.getBoolean("ui_landscape", false)) {
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            if (Build.VERSION.SDK_INT >= 17) display.getRealMetrics(metrics);
            else display.getMetrics(metrics);
            int paddings = metrics.widthPixels / 6;
            recyclerView.setPadding(paddings, 0, paddings, 0);
        }


        refreshLayout.setRefreshing(true);
        TerminalContext.getInstance().getReply(type, oid, rpid).observe(this, (rootReplyResult) -> {
            replyList = new ArrayList<>();
            rootReplyResult.onSuccess((rootReply) -> {
                // The notification may carry a child rpid. Use the API-resolved
                // root id for the child-reply request, otherwise the target can
                // disappear even though it is visible in the normal video page.
                if (rootReply != null && rootReply.rpid > 0) rpid = rootReply.rpid;
                Future<Integer> future = CenterThreadPool.supplyAsyncWithFuture(() -> ReplyApi.getReplies(oid, rpid, page, type, sort, replyList));
                CenterThreadPool.observe(future, (result) -> {
                    if (result != -1) {
                        replyList.add(0, rootReply);
                        replyAdapter = new ReplyAdapter(this, replyList, oid, rpid, type.getTypeCode(), sort, up_mid);
                        replyAdapter.setHighlightRpid(seekReply);
                        replyAdapter.isManager = isManager;
                        replyAdapter.isDetail = true;
                        setOnSortSwitch();
                        recyclerView.setLayoutManager(new CustomLinearManager(this));
                        recyclerView.setAdapter(replyAdapter);
                        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                            @Override
                            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                                super.onScrollStateChanged(recyclerView, newState);
                            }

                            @Override
                            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                                super.onScrolled(recyclerView, dx, dy);
                                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                                assert manager != null;
                                int lastItemPosition = manager.findLastVisibleItemPosition();  //获取最后一个显示的itemPosition
                                int itemCount = manager.getItemCount();
                                if (lastItemPosition >= (itemCount - 3) && dy > 0 && !refreshing && !bottom) {// 滑动到倒数第三个就可以刷新了
                                    refreshing = true;
                                    CenterThreadPool.run(() -> continueLoading()); //加载第二页
                                }
                            }
                        });
                        refreshLayout.setRefreshing(false);
                        scrollToHighlightedReply();
                        if (result == 1) {
                            Log.e("debug", "到底了");
                            bottom = true;
                        }
                    }
                }, (error) -> onPullDataFailed(new Exception(error)));
            }).onFailure((error) -> onPullDataFailed(new Exception(error)));
        });
    }

    private void onPullDataFailed(Exception e) {
        MsgUtil.err(e);
        refreshLayout.setRefreshing(false);
    }

    private void continueLoading() {
        runOnUiThread(() -> refreshLayout.setRefreshing(true));
        page++;
        try {
            List<Reply> list = new ArrayList<>();
            int result = ReplyApi.getReplies(oid, rpid, page, type, sort, list);
            if (result != -1) {
                Log.e("debug", "下一页");
                runOnUiThread(() -> {
                    replyList.addAll(list);
                    replyAdapter.notifyItemRangeInserted(replyList.size() - list.size() + 2, list.size());  //顶上有两个固定项
                    scrollToHighlightedReply();
                    refreshLayout.setRefreshing(false);
                });
                if (result == 1) {
                    //runOnUiThread(()-> MsgUtil.showMsg("到底啦QwQ",this));
                    Log.e("debug", "到底了");
                    bottom = true;
                }
            }
            refreshing = false;
        } catch (Exception e) {
            runOnUiThread(() -> {
                MsgUtil.err(e);
                refreshLayout.setRefreshing(false);
            });
        }
    }

    private void scrollToHighlightedReply() {
        if (seekReply <= 0 || replyList == null || replyAdapter == null || recyclerView == null) return;
        int index = -1;
        for (int i = 0; i < replyList.size(); i++) {
            Reply reply = replyList.get(i);
            if (reply != null && reply.rpid == seekReply) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            if (!bottom && !refreshing) {
                refreshing = true;
                CenterThreadPool.run(this::continueLoading);
            }
            return;
        }
        int adapterPosition = index + 1;
        replyAdapter.setHighlightRpid(seekReply);
        recyclerView.removeCallbacks(clearHighlightRunnable);
        recyclerView.post(() -> {
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(adapterPosition, 0);
            } else {
                recyclerView.scrollToPosition(adapterPosition);
            }
            recyclerView.postDelayed(clearHighlightRunnable, 5000);
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refresh() {
        page = 1;
        refreshLayout.setRefreshing(true);

        TerminalContext.getInstance().getReply(type, oid, rpid).observe(this, (rootReplyResult) -> rootReplyResult.onSuccess((rootReply) -> {
            List<Reply> list = new ArrayList<>();
            if (rootReply != null && rootReply.rpid > 0) rpid = rootReply.rpid;
            Future<Integer> future = CenterThreadPool.supplyAsyncWithFuture(() -> ReplyApi.getReplies(oid, rpid, page, type, sort, list));
            CenterThreadPool.observe(future, (result) -> {
                if (result != -1) {
                    runOnUiThread(() -> {
                        replyList.clear();
                        replyList.add(0, rootReply);
                        replyList.addAll(list);
                        if (replyAdapter == null) {
                            replyAdapter = new ReplyAdapter(this, replyList, oid, rpid, type.getTypeCode(), sort, up_mid);
                            replyAdapter.setHighlightRpid(seekReply);
                            replyAdapter.isDetail = true;
                            setOnSortSwitch();
                            recyclerView.setAdapter(replyAdapter);
                        } else {
                            replyAdapter.notifyDataSetChanged();
                        }
                        scrollToHighlightedReply();
                        refreshLayout.setRefreshing(false);
                    });
                    if (result == 1) {
                        Log.e("debug", "到底了");
                        bottom = true;
                    } else bottom = false;
                }
            }, (error) -> {
                this.onPullDataFailed(new Exception(error));
            });
        }).onFailure((error) -> {
            this.onPullDataFailed(new Exception(error));
        }));
    }

    private void setOnSortSwitch() {
        replyAdapter.setOnSortSwitchListener(position -> {
            sort = (sort == 0 ? 1 : 0);
            refresh();
        });
    }

    @Override
    protected boolean eventBusEnabled() {
        return true;
    }

    @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
    public void onEvent(ReplyEvent event) {
        if (event.getOid() != oid) return;
        Reply reply = event.getMessage();
        if (reply == null || replyList == null || replyAdapter == null) return;
        // Reply detail has one root item and one fixed write row. New child
        // replies belong after the existing children, never at the visible row.
        if (reply.root != 0 && reply.root != rpid) return;
        runOnUiThread(() -> {
            if (replyList == null || replyAdapter == null || recyclerView == null) return;
            for (Reply existing : replyList) {
                if (existing != null && existing.rpid == reply.rpid) return;
            }
            int listIndex = replyList.size();
            replyList.add(reply);
            int adapterPosition = listIndex + 1;
            replyAdapter.notifyItemInserted(adapterPosition);
            recyclerView.post(() -> {
                if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) recyclerView.getLayoutManager())
                            .scrollToPositionWithOffset(adapterPosition, 0);
                } else {
                    recyclerView.scrollToPosition(adapterPosition);
                }
            });
        });
    }
}
