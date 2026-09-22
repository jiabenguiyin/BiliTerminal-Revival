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
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.card.MaterialCardView;

public class MySpaceActivity extends InstanceActivity {

    private ImageView userAvatar;
    private TextView userName, userFans, userExp;
    private MaterialCardView myInfo, editProfile, follow, watchLater, favorite, bangumi, history, creative, vip, accountSwitch, loginRecord, logout;

    private boolean confirmLogout = false;
    private volatile boolean loadingProfile = false;
    private int profileGeneration;

    @Override
    protected void onResume() {
        super.onResume();
        if (myInfo != null) loadProfile();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) loadProfile();
    }

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
            editProfile = findViewById(R.id.edit_profile);
            follow = findViewById(R.id.follow);
            watchLater = findViewById(R.id.watchlater);
            favorite = findViewById(R.id.favorite);
            bangumi = findViewById(R.id.bangumi);
            history = findViewById(R.id.history);
            creative = findViewById(R.id.creative);
            vip = findViewById(R.id.vip);
            accountSwitch = findViewById(R.id.account_switch);
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
        editProfile.setOnClickListener(view -> startActivityForResult(
                new Intent(this, EditProfileActivity.class), 1001));
        watchLater.setOnClickListener(view -> startActivity(new Intent(this, WatchLaterActivity.class)));
        favorite.setOnClickListener(view -> startActivity(new Intent(this, FavoriteFolderListActivity.class)));
        bangumi.setOnClickListener(view -> startActivity(new Intent(this, FollowingBangumisActivity.class)));
        history.setOnClickListener(view -> startActivity(new Intent(this, HistoryActivity.class)));
        creative.setOnClickListener(view -> startActivity(new Intent(this, CreativeCenterActivity.class)));
        if (!SharedPreferencesUtil.getBoolean("creative_enable", true))
            creative.setVisibility(View.GONE);
        vip.setOnClickListener(view -> startActivity(new Intent(this, VipActivity.class)));
        accountSwitch.setOnClickListener(view -> startActivity(new Intent(this, AccountSwitchActivity.class)));
        loginRecord.setOnClickListener(view -> startActivity(new Intent(this, LoginRecordActivity.class)));
        logout.setOnClickListener(view -> {
            if (confirmLogout) {
                String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
                profileGeneration++;
                clearLocalLoginState();
                CenterThreadPool.run(() -> UserInfoApi.exitLogin(cookies));
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
        if (loadingProfile) {
            MsgUtil.showMsg("正在恢复登录状态，请稍候");
            return;
        }
        loadingProfile = true;
        final int generation = ++profileGeneration;
        // Repair the active session before the first profile request. This is
        // important after process death, when only the saved-account record may
        // still contain the usable cookie pair.
        AccountManager.restoreCurrentAccount();
        userName.setText("加载中...");
        userFans.setText("");
        userExp.setText("");
        setProfileAction(view -> loadProfile());

        CenterThreadPool.run(() -> {
            try {
                UserInfo userInfo = UserInfoApi.getCurrentUserInfo();
                int userCoin = UserInfoApi.getCurrentUserCoin();
                if (isActivityAlive()) runOnUiThread(() -> {
                    loadingProfile = false;
                    if (generation != profileGeneration || userInfo.mid !=
                            SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
                    bindProfile(userInfo, userCoin);
                });
            } catch (UserInfoApi.LoginExpiredException error) {
                if (isActivityAlive()) runOnUiThread(() -> {
                    loadingProfile = false;
                    if (generation == profileGeneration) showLoginExpired();
                });
            } catch (Exception error) {
                Log.e("MySpace", "个人资料加载失败", error);
                if (isActivityAlive()) runOnUiThread(() -> {
                    loadingProfile = false;
                    if (generation != profileGeneration) return;
                    userName.setText("资料加载失败");
                    userFans.setText("点击头像重试");
                    userExp.setText("");
                    setProfileAction(view -> loadProfile());
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
        AccountManager.updateCurrentProfile(userInfo);
        userFans.setText(StringUtil.toWan(userInfo.fans) + "粉丝 " + userCoin + "硬币");
        userExp.setText("EXP:" + userInfo.current_exp
                + (userInfo.level >= 6 ? "" : "/" + userInfo.next_exp));
        setProfileAction(view -> startActivity(new Intent(this, UserInfoActivity.class)
                .putExtra("mid", userInfo.mid)));
        follow.setOnClickListener(view -> startActivity(new Intent(this, FollowUsersActivity.class)
                .putExtra("mid", userInfo.mid)
                .putExtra("mode", 0)));
    }

    private void showLoginExpired() {
        // Keep the local session. A failed confirmation can be caused by a
        // transient route, relay, risk-control response, or device clock.
        // Only the explicit logout action may remove credentials.
        userName.setText("登录状态未恢复");
        userFans.setText("账号信息已保留");
        userExp.setText("");
        // The avatar is the visible retry affordance on the watch-sized layout.
        // Retry directly so recovery does not depend on a dialog button being
        // visible or clickable on a narrow screen.
        setProfileAction(view -> loadProfile());
    }

    private void setProfileAction(View.OnClickListener listener) {
        myInfo.setOnClickListener(listener);
        userAvatar.setOnClickListener(listener);
    }

    @Override
    protected void onDestroy() {
        profileGeneration++;
        super.onDestroy();
    }

    private void clearLocalLoginState() {
        AccountManager.removeCurrentAccountAndCredentials();
    }

    private boolean isActivityAlive() {
        return !isFinishing() && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }
}
