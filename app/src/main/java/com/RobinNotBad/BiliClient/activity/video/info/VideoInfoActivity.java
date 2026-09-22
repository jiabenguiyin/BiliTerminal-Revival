package com.RobinNotBad.BiliClient.activity.video.info;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.reply.ReplyFragment;
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter;
import com.RobinNotBad.BiliClient.event.ReplyEvent;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.util.AnimationUtils;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

//视频详情页，但这只是个壳，瓤是VideoInfoFragment、VideoReplyFragment、VideoRcmdFragment

public class VideoInfoActivity extends BaseActivity {

    private long aid;
    private String bvid;

    private List<Fragment> fragmentList;
    public ReplyFragment replyFragment;
    public Fragment contentFragment;
    private long seek_reply;
    private long seek_root;
    private ImageView loading;
    private ViewPager viewPager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean detailLoadFinished;

    //private MediaViewPager2Adapter mediaViewPager2Adapter;

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_viewpager);    //这里async是否反而会减慢速度，仅有一个viewpager的页面加载已经足够快了吧

        Intent intent = getIntent();
        String type = intent.getStringExtra("type");
        if (type == null) type = "video";
        this.aid = intent.getLongExtra("aid", 114514);
        this.bvid = intent.getStringExtra("bvid");
        this.seek_reply = intent.getLongExtra("seekReply", -1);
        this.seek_root = intent.getLongExtra("seekRoot", seek_reply);

        viewPager = findViewById(R.id.viewPager);
        loading = findViewById(R.id.loading);
        if (type.equals("media")) initMediaInfoView();
        else initVideoInfoView();
    }


    public void initMediaInfoView() {
        setPageName("番剧详情");

        fragmentList = new ArrayList<>(2);
        contentFragment = BangumiInfoFragment.newInstance(aid);
        fragmentList.add(contentFragment);
        replyFragment = ReplyFragment.newInstance(aid, 1, seek_reply == -1, seek_root, seek_reply);
        fragmentList.add(replyFragment);

        viewPager.setOffscreenPageLimit(1);
        ViewPagerFragmentAdapter vpfAdapter = new ViewPagerFragmentAdapter(getSupportFragmentManager(), fragmentList);
        viewPager.setAdapter(vpfAdapter);
        if (seek_reply != -1) viewPager.setCurrentItem(1);
        if (SharedPreferencesUtil.getBoolean("first_videoinfo", true)) {
            MsgUtil.showMsgLong("提示：本页面可以左右滑动");
            SharedPreferencesUtil.putBoolean("first_videoinfo", false);
        }
    }

    protected void initVideoInfoView() {
        TutorialHelper.showTutorialList(this, R.array.tutorial_video, 1);
        TutorialHelper.showPagerTutorial(this, 3);

        setPageName("视频详情");
        mainHandler.postDelayed(() -> {
            if (detailLoadFinished || isFinishing() || isDestroyed()) return;
            detailLoadFinished = true;
            loading.setImageResource(R.mipmap.loading_2233_error);
            MsgUtil.showMsg("获取信息超时！\n请检查网络连接后重试");
        }, 20L * 1000L);
        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(this, (result) -> result.onSuccess((videoInfo) -> {
            if (detailLoadFinished || isFinishing() || isDestroyed()) return;
            detailLoadFinished = true;
            mainHandler.removeCallbacksAndMessages(null);
            aid = videoInfo.aid;
            bvid = videoInfo.bvid;
            fragmentList = new ArrayList<>(3);
            contentFragment = VideoInfoFragment.newInstance(videoInfo.aid, bvid);
            fragmentList.add(contentFragment);
            long ownerMid = videoInfo.staff == null || videoInfo.staff.isEmpty()
                    ? 0L : videoInfo.staff.get(0).mid;
            int replyCount = videoInfo.stats == null ? 0 : videoInfo.stats.reply;
            replyFragment = ReplyFragment.newInstance(videoInfo.aid, 1, replyCount,
                    seek_root, seek_reply, ownerMid);
            replyFragment.setManager(videoInfo.staff == null ? new ArrayList<>() : videoInfo.staff);
            fragmentList.add(replyFragment);
            if (SharedPreferencesUtil.getBoolean("related_enable", true)) {
                VideoRcmdFragment vrFragment = VideoRcmdFragment.newInstance(videoInfo.aid);
                fragmentList.add(vrFragment);
            }
            viewPager.setOffscreenPageLimit(1);
            ViewPagerFragmentAdapter vpfAdapter = new ViewPagerFragmentAdapter(getSupportFragmentManager(), fragmentList);
            viewPager.setAdapter(vpfAdapter);
            if (seek_reply != -1) viewPager.setCurrentItem(1);
        }).onFailure((error) -> {
            if (detailLoadFinished || isFinishing() || isDestroyed()) return;
            detailLoadFinished = true;
            mainHandler.removeCallbacksAndMessages(null);
            loading.setImageResource(R.mipmap.loading_2233_error);
            Logu.e("video-detail", "视频详情加载失败: "
                    + (error == null ? "unknown" : error.getClass().getSimpleName()));
            MsgUtil.showMsg("视频信息加载失败，视频可能已删除或链接已失效");
        }));
    }

    public void setCurrentAid(long aid) {
        if (replyFragment != null) runOnUiThread(() -> replyFragment.refresh(aid));
    }

    public void crossFade(View fragmentView) {
        AnimationUtils.crossFade(loading, fragmentView);
        fragmentView.post(() -> {
            View scrollView = fragmentView.findViewById(R.id.scrollView);
            if (scrollView != null) {
                scrollView.setFocusable(true);
                scrollView.setFocusableInTouchMode(true);
                scrollView.requestFocus();
            }
        });
    }

    @Override
    protected boolean eventBusEnabled() {
        return true;
    }

    @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
    public void onEvent(ReplyEvent event) {
        replyFragment.notifyReplyInserted(event);
    }

    @Override
    protected void onDestroy() {
        Logu.d("onDestroy");
        mainHandler.removeCallbacksAndMessages(null);
        TerminalContext.getInstance().leaveDetailPage();
        super.onDestroy();
    }
}
