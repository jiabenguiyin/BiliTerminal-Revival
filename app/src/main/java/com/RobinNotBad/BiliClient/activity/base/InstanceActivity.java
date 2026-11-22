package com.RobinNotBad.BiliClient.activity.base;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.MenuActivity;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

public class InstanceActivity extends BaseActivity {
    private boolean menuClickEnabled;
    private final Runnable unreadObserver=this::refreshMessageIndicator;
    protected void refreshMessageIndicator() {
        boolean hasMenu = menuClickEnabled;
        com.RobinNotBad.BiliClient.ui.MessageBadgeStyle.title(findViewById(R.id.pageName),
                hasMenu ? R.drawable.arrow_down : R.drawable.arrow_back,
                hasMenu ? com.RobinNotBad.BiliClient.util.MessageUnreadCounter.getCount() : 0);
    }
    @Override protected void onResume() {
        super.onResume();
        com.RobinNotBad.BiliClient.util.MessageUnreadCounter.observe(unreadObserver);
    }
    @Override protected void onPause() {
        com.RobinNotBad.BiliClient.util.MessageUnreadCounter.unobserve(unreadObserver);
        super.onPause();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.HOME_SWIPE_DISMISS_DISABLE, true)) {
            setTheme(R.style.Theme_NoSwipe_AppCompat);
        }
        BiliTerminal.setInstance(this);
        super.onCreate(savedInstanceState);
    }

    public void setMenuClick() {
        menuClickEnabled = true;
        findViewById(R.id.top).setOnClickListener(view -> menuClick.run());
        refreshMessageIndicator();
    }

    public Runnable menuClick = (() -> {
        Intent intent = new Intent();
        intent.setClass(this, MenuActivity.class);
        if (getIntent().hasExtra("from"))
            intent.putExtra("from", getIntent().getStringExtra("from"));
        startActivity(intent);
        overridePendingTransition(R.anim.anim_activity_in_down, 0);
    });

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) menuClick.run();
        return super.onKeyDown(keyCode, event);
    }
}
