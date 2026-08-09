package com.RobinNotBad.BiliClient.activity.user;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity;
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteFolderListActivity;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.api.UserInfoApi;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.card.MaterialCardView;

public class MySpaceActivity extends InstanceActivity {

    private ImageView userAvatar;
    private TextView userName, userFans, userExp;
    private MaterialCardView myInfo, follow, watchLater, favorite, bangumi, history, creative, vip, loginRecord, logout;

    private boolean confirmLogout = false;
    private volatile boolean loadingProfile = false;

    @SuppressLint({"SetTextI18n", "InflateParams"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        asyncInflate(R.layout.activity_myspace, (layoutView, resId) -> {
            Log.e("debug", "进入个人页");

            userAvatar = findViewById(R.id.userAvatar);
            userName = findViewById(R.id.userName);
            userFans = findViewById(R.id.userFans);
            userExp = findViewById(R.id.userExp);

            myInfo = findViewById(R.id.myinfo);
            follow = findViewById(R.id.follow);
            watchLater = findViewById(R.id.watchlater);
            favorite = findViewById(R.id.favorite);
            bangumi = findViewById(R.id.bangumi);
            history = findViewById(R.id.history);
            creative = findViewById(R.id.creative);
            vip = findViewById(R.id.vip);
            loginRecord = findViewById(R.id.login_record);
            logout = findViewById(R.id.logout);


            bindStaticActions();
            loadProfile();

            View scrollView = findViewById(R.id.scrollView);
            scrollView.setFocusable(true);
            scrollView.setFocusableInTouchMode(true);
            scrollView.requestFocus();
        });
    }

    private void bindStaticActions() {
        watchLater.setOnClickListener(view -> startActivity(new Intent(this, WatchLaterActivity.class)));
        favorite.setOnClickListener(view -> startActivity(new Intent(this, FavoriteFolderListActivity.class)));
        bangumi.setOnClickListener(view -> startActivity(new Intent(this, FollowingBangumisActivity.class)));
        history.setOnClickListener(view -> startActivity(new Intent(this, HistoryActivity.class)));
        creative.setOnClickListener(view -> startActivity(new Intent(this, CreativeCenterActivity.class)));
        if (!SharedPreferencesUtil.getBoolean("creative_enable", true))
            creative.setVisibility(View.GONE);
        vip.setOnClickListener(view -> startActivity(new Intent(this, VipActivity.class)));
        loginRecord.setOnClickListener(view -> startActivity(new Intent(this, LoginRecordActivity.class)));
        logout.setOnClickListener(view -> {
            if (confirmLogout) {
                CenterThreadPool.run(UserInfoApi::exitLogin);
                clearLocalLoginState();
                MsgUtil.showMsg("账号已退出");
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            } else {
                MsgUtil.showMsg("再点一次退出登录！");
                confirmLogout = true;
            }
        });
    }

    private void loadProfile() {
        if (loadingProfile) return;
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) {
            showLoginExpired();
            return;
        }
        loadingProfile = true;
        userName.setText("加载中...");
        userFans.setText("");
        userExp.setText("");
        myInfo.setOnClickListener(null);

        CenterThreadPool.run(() -> {
            try {
                UserInfo userInfo = UserInfoApi.getCurrentUserInfo();
                int userCoin = UserInfoApi.getCurrentUserCoin();
                loadingProfile = false;
                if (isActivityAlive()) runOnUiThread(() -> bindProfile(userInfo, userCoin));
            } catch (UserInfoApi.LoginExpiredException error) {
                loadingProfile = false;
                if (isActivityAlive()) runOnUiThread(this::showLoginExpired);
            } catch (Exception error) {
                loadingProfile = false;
                Log.e("MySpace", "个人资料加载失败", error);
                if (isActivityAlive()) runOnUiThread(() -> {
                    userName.setText("资料加载失败");
                    userFans.setText("点击头像重试");
                    userExp.setText("");
                    myInfo.setOnClickListener(view -> loadProfile());
                    MsgUtil.showMsg("资料加载失败，请检查网络后重试");
                });
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void bindProfile(UserInfo userInfo, int userCoin) {
        if (userInfo == null || userInfo.mid <= 0) {
            showLoginExpired();
            return;
        }
        Glide.with(MySpaceActivity.this).load(GlideUtil.url(userInfo.avatar))
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(R.mipmap.akari).apply(RequestOptions.circleCropTransform())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(userAvatar);
        userName.setText(userInfo.name);
        userFans.setText(StringUtil.toWan(userInfo.fans) + "粉丝 " + userCoin + "硬币");
        userExp.setText("EXP:" + userInfo.current_exp
                + (userInfo.level >= 6 ? "" : "/" + userInfo.next_exp));
        myInfo.setOnClickListener(view -> startActivity(new Intent(this, UserInfoActivity.class)
                .putExtra("mid", userInfo.mid)));
        follow.setOnClickListener(view -> startActivity(new Intent(this, FollowUsersActivity.class)
                .putExtra("mid", userInfo.mid)
                .putExtra("mode", 0)));
    }

    private void showLoginExpired() {
        clearLocalLoginState();
        MsgUtil.showMsg("登录状态已失效，请重新登录");
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void clearLocalLoginState() {
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.cookies);
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.mid);
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.csrf);
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.refresh_token);
        SharedPreferencesUtil.removeValue(SharedPreferencesUtil.cookie_refresh);
        NetWorkUtil.refreshHeaders();
    }

    private boolean isActivityAlive() {
        return !isFinishing() && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }
}
