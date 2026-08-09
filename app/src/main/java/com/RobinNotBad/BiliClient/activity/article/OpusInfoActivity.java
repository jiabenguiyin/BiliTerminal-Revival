package com.RobinNotBad.BiliClient.activity.article;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.dynamic.DynamicInfoActivity;
import com.RobinNotBad.BiliClient.activity.reply.ReplyFragment;
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter;
import com.RobinNotBad.BiliClient.event.ReplyEvent;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.model.Opus;
import com.RobinNotBad.BiliClient.util.AnimationUtils;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class OpusInfoActivity extends BaseActivity {
    private long oid;

    private ReplyFragment replyFragment;
    private long seek_reply;

    private ImageView loadingView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_viewpager);
        Intent intent = getIntent();
        oid = intent.getLongExtra("id", 114514);
        seek_reply = intent.getLongExtra("seekReply", -1);
        boolean fromDynamic = intent.getBooleanExtra("fromDynamic", false);

        setPageName(fromDynamic ? "动态详情" : "文章详情");
        loadingView = findViewById(R.id.loading);

        //TutorialHelper.showTutorialList(this, R.array.tutorial_article, 7);

        ViewPager viewPager = findViewById(R.id.viewPager);


        TerminalContext.getInstance().getOpusById(oid)
                .observe(this, (result) -> result.onSuccess((opus) -> {
                    if (fromDynamic || opus.type == Opus.TYPE_DYNAMIC_OLD_STYLE) {
                        Intent intent1 = new Intent(this, DynamicInfoActivity.class);
                        intent1.putExtra("id", oid);
                        intent1.putExtra("seekReply", seek_reply);
                        intent1.putExtra("commentId", opus.commentId);
                        intent1.putExtra("commentType", opus.commentType);
                        startActivity(intent1);
                        finish();
                        return;
                    }

                    List<Fragment> fragmentList = new ArrayList<>();

                    OpusInfoFragment oiFragment = OpusInfoFragment.newInstance(oid);
                    fragmentList.add(oiFragment);

                    replyFragment = ReplyFragment.newInstance(opus.commentId, opus.commentType, opus.stats.reply, seek_reply, opus.upInfo.mid);
                    replyFragment.setManager(opus.upInfo);
                    fragmentList.add(replyFragment);

                    ViewPagerFragmentAdapter vpfAdapter = new ViewPagerFragmentAdapter(getSupportFragmentManager(), fragmentList);
                    viewPager.setAdapter(vpfAdapter);
                    if (seek_reply != -1) viewPager.setCurrentItem(1);

                    AnimationUtils.crossFade(loadingView, oiFragment.getView());
                    TutorialHelper.showPagerTutorial(this, 2);
                }).onFailure((error) -> {
                    loadingView.setImageResource(R.mipmap.loading_2233_error);
                    MsgUtil.err(error);
                }));


    }

    @Override
    protected boolean eventBusEnabled() {
        return true;
    }

    @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
    public void onEvent(ReplyEvent event) {
        if (replyFragment == null) return;
        replyFragment.notifyReplyInserted(event);
    }

    @Override
    protected void onDestroy() {
        //TerminalContext.getInstance().leaveDetailPage();
        super.onDestroy();
    }
}
