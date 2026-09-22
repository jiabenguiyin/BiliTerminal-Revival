package com.RobinNotBad.BiliClient.activity.user;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.SplashActivity;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.settings.login.LoginActivity;
import com.RobinNotBad.BiliClient.adapter.AccountSwitchAdapter;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.List;

public class AccountSwitchActivity extends BaseActivity implements AccountSwitchAdapter.Listener {
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_switch);
        setPageName("切换账号");

        recyclerView = findViewById(R.id.account_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        findViewById(R.id.add_account).setOnClickListener(this::addAccount);
        reloadAccounts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadAccounts();
    }

    private void reloadAccounts() {
        AccountManager.migrateCurrentAccount();
        List<AccountManager.SavedAccount> accounts = AccountManager.getAccounts();
        long currentMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        recyclerView.setAdapter(new AccountSwitchAdapter(accounts, currentMid, this));
        findViewById(R.id.account_empty).setVisibility(accounts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addAccount(View ignored) {
        AccountManager.migrateCurrentAccount();
        startActivity(new Intent(this, LoginActivity.class).putExtra("add_account", true));
    }

    @Override
    public void onAccountClick(AccountManager.SavedAccount account) {
        long currentMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (account.mid == currentMid) {
            MsgUtil.showMsg("当前正在使用这个账号");
            return;
        }
        AlertDialog switchDialog = new AlertDialog.Builder(this)
                .setTitle("切换账号")
                .setMessage("切换到“" + account.getDisplayName() + "”？")
                .setNegativeButton("取消", null)
                .setPositiveButton("切换", (dialog, which) -> switchAccount(account.mid))
                .create();
        MsgUtil.prepareAlertDialog(switchDialog);
        switchDialog.show();
    }

    private void switchAccount(long mid) {
        if (!AccountManager.switchTo(mid)) {
            MsgUtil.showMsg("切换失败，保存的登录信息可能已损坏");
            reloadAccounts();
            return;
        }
        MsgUtil.showMsg("账号已切换");
        BiliTerminal.prepareForAccountChange();
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(AccountManager.SavedAccount account) {
        AlertDialog deleteDialog = new AlertDialog.Builder(this)
                .setTitle("删除已保存账号")
                .setMessage("删除“" + account.getDisplayName() + "”的本地登录信息？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (AccountManager.removeAccount(account.mid)) {
                        MsgUtil.showMsg("已删除");
                        reloadAccounts();
                    }
                })
                .create();
        MsgUtil.prepareAlertDialog(deleteDialog);
        deleteDialog.show();
    }
}
