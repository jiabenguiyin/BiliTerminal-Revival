package com.RobinNotBad.BiliClient.activity.settings;

import android.os.Bundle;

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.AnnouncementAdapter;
import com.RobinNotBad.BiliClient.api.AppInfoApi;
import com.RobinNotBad.BiliClient.model.Announcement;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

//公告列表
//2024-02-23

public class AnnouncementsActivity extends RefreshListActivity {
    private final AtomicBoolean loading = new AtomicBoolean();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setPageName("公告列表");
        setOnRefreshListener(this::loadAnnouncements);
        loadAnnouncements();
    }

    private void loadAnnouncements() {
        if (!loading.compareAndSet(false, true)) return;
        CenterThreadPool.run(() -> {
            try {
                ArrayList<Announcement> announcements = AppInfoApi.getAnnouncementList();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (announcements.isEmpty()) {
                        showEmptyView();
                    } else {
                        hideEmptyView();
                        setAdapter(new AnnouncementAdapter(this, announcements));
                    }
                });
            } catch (Exception e) {
                report(e);
                runOnUiThread(() -> {
                    if (!isFinishing()) MsgUtil.showMsg("公告暂时获取失败，请稍后重试");
                });
            } finally {
                loading.set(false);
                setRefreshing(false);
            }
        });
    }

}
