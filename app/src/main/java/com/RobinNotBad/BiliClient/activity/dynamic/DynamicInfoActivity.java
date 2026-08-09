package com.RobinNotBad.BiliClient.activity.dynamic;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.reply.ReplyFragment;
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter;
import com.RobinNotBad.BiliClient.api.ReplyApi;
import com.RobinNotBad.BiliClient.event.ReplyEvent;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.util.AnimationUtils;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

//动态信息页面
//2023-10-03

public class DynamicInfoActivity extends BaseActivity {

    ReplyFragment rFragment;
    private long seek_reply;

    @SuppressLint({"MissingInflatedId", "InflateParams"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_viewpager);
        seek_reply = getIntent().getLongExtra("seekReply", -1);

        Intent intent = getIntent();
        long id = intent.getLongExtra("id", 0);
        long passedCommentId = intent.getLongExtra("commentId", 0);
        int passedCommentType = intent.getIntExtra("commentType", 0);

        TextView pageName = findViewById(R.id.pageName);
        pageName.setText("动态详情");

        TutorialHelper.showTutorialList(this, R.array.tutorial_dynamic_info, 6);
        TerminalContext.getInstance().getDynamicById(id)
                .observe(this, (dynamicResult) -> dynamicResult.onSuccess((dynamic) -> {
                    List<Fragment> fragmentList = new ArrayList<>();
                    DynamicInfoFragment diFragment = DynamicInfoFragment.newInstance(id);
                    fragmentList.add(diFragment);
                    long commentId = passedCommentId > 0 ? passedCommentId
                            : dynamic.comment_id > 0 ? dynamic.comment_id : id;
                    int commentType = passedCommentType > 0 ? passedCommentType
                            : dynamic.comment_type > 0 ? dynamic.comment_type : ReplyApi.REPLY_TYPE_DYNAMIC;
                    dynamic.comment_id = commentId;
                    dynamic.comment_type = commentType;
                    int replyCount = dynamic.stats == null ? 0 : dynamic.stats.reply;
                    long upMid = dynamic.userInfo == null ? -1 : dynamic.userInfo.mid;
                    rFragment = ReplyFragment.newInstance(commentId, commentType, replyCount, seek_reply, upMid);
                    rFragment.setManager(dynamic.userInfo);
                    rFragment.replyType = commentType;
                    fragmentList.add(rFragment);
                    ViewPagerFragmentAdapter vpfAdapter = new ViewPagerFragmentAdapter(getSupportFragmentManager(), fragmentList);
                    ViewPager viewPager = findViewById(R.id.viewPager);
                    viewPager.setOffscreenPageLimit(fragmentList.size());
                    viewPager.setAdapter(vpfAdapter);  //没啥好说的，教科书式的ViewPager使用方法
                    View view;
                    if ((view = diFragment.getView()) != null) view.setVisibility(View.GONE);
                    if (seek_reply != -1) viewPager.setCurrentItem(1);

                    AnimationUtils.crossFade(findViewById(R.id.loading), diFragment.getView());
                    viewPager.post(() -> {
                        View dynamicView = diFragment.getView();
                        if (dynamicView == null) return;
                        View scrollView = dynamicView.findViewById(R.id.scrollView);
                        if (scrollView == null) return;
                        scrollView.setFocusable(true);
                        scrollView.setFocusableInTouchMode(true);
                        scrollView.requestFocus();
                    });
                    TutorialHelper.showPagerTutorial(this, 2);
                }).onFailure((e) -> {
                    MsgUtil.err(e);
                    ((ImageView) findViewById(R.id.loading)).setImageResource(R.mipmap.loading_2233_error);
                }));

    }

    @Override
    protected boolean eventBusEnabled() {
        return true;
    }

    @Subscribe(threadMode = ThreadMode.ASYNC, sticky = true, priority = 1)
    public void onEvent(ReplyEvent event) {
        if (rFragment == null) return;
        rFragment.notifyReplyInserted(event);
    }

    @Override
    protected void onDestroy() {
        TerminalContext.getInstance().leaveDetailPage();
        super.onDestroy();
    }
}
