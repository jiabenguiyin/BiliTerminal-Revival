package com.RobinNotBad.BiliClient.activity.video.local;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.video.DownloadAdapter;
import com.RobinNotBad.BiliClient.listener.OnItemClickListener;
import com.RobinNotBad.BiliClient.listener.OnItemLongClickListener;
import com.RobinNotBad.BiliClient.model.DownloadSection;
import com.RobinNotBad.BiliClient.service.DownloadService;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class DownloadListActivity extends RefreshListActivity {
    public static WeakReference<DownloadListActivity> weakRef;
    DownloadAdapter adapter;
    Timer timer;
    boolean emptyTipShown;
    boolean firstRefresh = true;
    boolean created;
    ArrayList<DownloadSection> sections;
    private float lastPercent = -1;
    private String lastState = null;
    private long lastDownloadingId = -1;
    private int longClickPosition = -1;
    private long longClickTimestamp;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setPageName("下载列表");
        setRefreshing(false);
        weakRef = new WeakReference<>(this);

        CenterThreadPool.run(() -> {
            created = true;
            refreshList(false);

            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (adapter == null || !created || isDestroyed())
                        return;
                    if (DownloadService.section != null) {
                        boolean needUpdate = false;
                        if (lastDownloadingId != DownloadService.section.id) {
                            lastDownloadingId = DownloadService.section.id;
                            needUpdate = true;
                        }
                        if (lastPercent != DownloadService.percent) {
                            lastPercent = DownloadService.percent;
                            needUpdate = true;
                        }
                        if (lastState == null || !lastState.equals(DownloadService.state)) {
                            lastState = DownloadService.state;
                            needUpdate = true;
                        }
                        if (needUpdate) {
                            final int pos = findDownloadingPosition();
                            if (pos >= 0) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        adapter.notifyItemChanged(pos);
                                    }
                                });
                            }
                        }
                    }
                }
            }, 300, 500);
        });

    }

    private int findDownloadingPosition() {
        if (sections == null || DownloadService.section == null)
            return -1;
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).id == DownloadService.section.id) {
                return i;
            }
        }
        return -1;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refreshList(boolean fromOutside) {
        if (this.isDestroyed() || !created)
            return;
        Log.d("debug", "刷新下载列表");

        sections = DownloadService.getAll();

        if (sections == null || sections.isEmpty()) {
            if (!emptyTipShown) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        MsgUtil.showMsg("下载列表为空");
                        showEmptyView();
                    }
                });
                emptyTipShown = true;
            }
        } else {
            for (DownloadSection s : sections) {
                Log.d("debug-download", s.name_short);
            }

            if (emptyTipShown) {
                emptyTipShown = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideEmptyView();
                    }
                });
            }

            if (firstRefresh) {
                adapter = new DownloadAdapter(DownloadListActivity.this, sections);
                adapter.setOnClickListener(new OnItemClickListener() {
                    @Override
                    public void onItemClick(int position) {
                        CenterThreadPool.run(new Runnable() {
                            @Override
                            public void run() {
                                Log.d("debug-download", "click:" + position);
                                if (sections == null || position < 0 || position >= sections.size())
                                    return;

                                DownloadSection section = sections.get(position);
                                if (section.state.equals("downloading")) {
                                    if (DownloadService.isCurrent(section.id)) {
                                        DownloadService.pause();
                                        MsgUtil.showMsg("已暂停，点击可继续");
                                        try {
                                            Thread.sleep(500);
                                        } catch (InterruptedException ignored) {
                                        }
                                        refreshList(false);
                                    } else {
                                        DownloadService.retry(section.id);
                                    }
                                    return;
                                }
                                DownloadService.retry(section.id);
                            }
                        });
                    }
                });

                adapter.setOnLongClickListener(new OnItemLongClickListener() {
                    @Override
                    public void onItemLongClick(int position) {
                        CenterThreadPool.run(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (sections == null || position < 0 || position >= sections.size())
                                        return;

                                    final DownloadSection delete = sections.get(position);
                                    if (delete == null)
                                        return;

                                    long timestamp = System.currentTimeMillis();
                                    if (longClickPosition != position || timestamp - longClickTimestamp > 4000) {
                                        longClickPosition = position;
                                        longClickTimestamp = timestamp;
                                        MsgUtil.showMsg("再次长按删除任务");
                                        return;
                                    }
                                    longClickPosition = -1;

                                    if (DownloadService.isCurrent(delete.id)) {
                                        DownloadService.stopForDelete(delete.id);
                                        MsgUtil.showMsg("正在停止并删除任务");
                                        return;
                                    }

                                    VideoStorageUtil.Node folder = delete.getStoragePath(DownloadListActivity.this, false);
                                    if (folder != null) folder.deleteRecursive();

                                    DownloadService.deleteSection(delete.id);

                                    refreshList(false);
                                    MsgUtil.showMsg("删除成功");
                                } catch (Exception e) {
                                    MsgUtil.err(e);
                                }
                            }
                        });
                    }
                });

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setAdapter(adapter);
                    }
                });
                firstRefresh = false;
            } else {
                adapter.downloadList = sections;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
                Log.d("debug-adapter", String.valueOf(adapter.getItemCount()));
            }
        }

    }

    @Override
    protected void onDestroy() {
        if (timer != null)
            timer.cancel();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        weakRef = null;
        super.onDestroy();
    }
}
