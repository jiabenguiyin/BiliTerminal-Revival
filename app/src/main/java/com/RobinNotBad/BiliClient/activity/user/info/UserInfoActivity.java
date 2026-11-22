package com.RobinNotBad.BiliClient.activity.user.info;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder;
import com.RobinNotBad.BiliClient.adapter.viewpager.ViewPagerFragmentAdapter;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.List;

//用户信息页面
//2023-08-07

public class UserInfoActivity extends BaseActivity {

    UserDynamicFragment udFragment;

    @SuppressLint({"MissingInflatedId", "InflateParams"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_info);

        Intent intent = getIntent();
        long mid = intent.getLongExtra("mid", 114514);

        setPageName("用户信息");

        TutorialHelper.showTutorialList(this, R.array.tutorial_space, 2);

        ViewPager viewPager = findViewById(R.id.viewPager);

        List<Fragment> fragmentList = new ArrayList<>();
        udFragment = UserDynamicFragment.newInstance(mid);
        fragmentList.add(udFragment);
        UserVideoFragment uvFragment = UserVideoFragment.newInstance(mid);
        fragmentList.add(uvFragment);
        UserArticleFragment acFragment = UserArticleFragment.newInstance(mid);
        fragmentList.add(acFragment);
        //UserSeasonFragment ssFragment = UserSeasonFragment.newInstance(mid);
        //fragmentList.add(ssFragment);
        viewPager.setOffscreenPageLimit(fragmentList.size());

        ViewPagerFragmentAdapter vpfAdapter = new ViewPagerFragmentAdapter(getSupportFragmentManager(), fragmentList);

        viewPager.setAdapter(vpfAdapter);  //没啥好说的，教科书式的ViewPager使用方法

        findViewById(R.id.loading).setVisibility(View.GONE);
        View userVideoSearch = findViewById(R.id.user_video_search);
        userVideoSearch.setVisibility(View.VISIBLE);
        userVideoSearch.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            input.setSingleLine(true);
            input.setMinHeight(MsgUtil.dp(this, 42));
            input.setPadding(MsgUtil.dp(this, 8), 0, MsgUtil.dp(this, 8), 0);
            input.setTextColor(Color.WHITE);
            input.setHint("搜索该用户的投稿");
            FrameLayout inputContainer = new FrameLayout(this);
            inputContainer.setPadding(MsgUtil.dp(this, 12), 0, MsgUtil.dp(this, 12), 0);
            inputContainer.addView(input);
            AlertDialog searchDialog = new AlertDialog.Builder(this)
                    .setTitle("搜索投稿")
                    .setView(inputContainer)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("搜索", (dialog, which) -> {
                        viewPager.setCurrentItem(1, false);
                        uvFragment.search(input.getText().toString());
                    })
                    .create();
            MsgUtil.prepareAlertDialog(searchDialog);
            searchDialog.show();
        });

        TutorialHelper.showPagerTutorial(this, 3);

    }

    @Override public void setRound() {
        View header = findViewById(R.id.userinfo_header_group);
        TextView clock = findViewById(R.id.timeText);
        if (header == null || clock == null) {
            super.setRound();
            return;
        }
        boolean round = SharedPreferencesUtil.getBoolean("player_ui_round", false);
        int side = MsgUtil.dp(this, 8), gap = MsgUtil.dp(this, 4);
        RelativeLayout.LayoutParams row = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, MsgUtil.dp(this, 36));
        RelativeLayout.LayoutParams time = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clock.setTextSize(round ? 12 : 14);
        if (round) {
            time.addRule(RelativeLayout.CENTER_HORIZONTAL);
            time.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            time.topMargin = gap / 2;
            row.addRule(RelativeLayout.CENTER_HORIZONTAL);
            row.addRule(RelativeLayout.BELOW, R.id.timeText);
            row.leftMargin = row.rightMargin = roundHeaderSideMargin(clock, gap);
            row.topMargin = gap;
        } else {
            row.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            row.addRule(RelativeLayout.LEFT_OF, R.id.timeText);
            row.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            row.leftMargin = side;
            row.rightMargin = gap;
            row.topMargin = gap / 2;
            time.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            time.addRule(RelativeLayout.CENTER_VERTICAL);
            time.rightMargin = side;
        }
        clock.setAlpha(round ? 0.85f : 1f);
        header.setLayoutParams(row);
        clock.setLayoutParams(time);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DynamicHolder.GO_TO_INFO_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                udFragment.onDynamicRemove(data.getIntExtra("position", 0) - 1);
            }
        }
    }
}
