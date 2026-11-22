package com.RobinNotBad.BiliClient.activity.message;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.adapter.message.MessageSettingsAdapter;
import com.RobinNotBad.BiliClient.api.MessageApi;
import com.RobinNotBad.BiliClient.model.message.MessageSettingItem;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MessageSettingsActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private MessageSettingsAdapter adapter;
    private final List<MessageSettingItem> settingsList = new ArrayList<>();

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_refresh);

        setPageName("消息设置");

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.setRefreshing(true);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        buildSettingsList(new JSONObject());
        adapter = new MessageSettingsAdapter(this, settingsList, this::onSettingChanged);
        recyclerView.setAdapter(adapter);
        loadSettings();
    }

    private void loadSettings() {
        CenterThreadPool.run(() -> {
            try {
                JSONObject response = MessageApi.getMsgSettings();
                if (response.getInt("code") == 0) {
                    JSONObject currentSettings = response.getJSONObject("data");
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        buildSettingsList(currentSettings);
                        adapter.notifyDataSetChanged();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                } else {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        MsgUtil.showMsg("获取设置失败: " + response.optString("message", "未知错误"));
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    report(e);
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        });
    }

    private void buildSettingsList(JSONObject currentSettings) {
        settingsList.clear();
        settingsList.add(new MessageSettingItem(
                SharedPreferencesUtil.MESSAGE_REFRESH_ON_RETURN,
                getString(R.string.message_refresh_on_return),
                getString(R.string.message_refresh_on_return_desc),
                MessageSettingItem.TYPE_SWITCH,
                SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_REFRESH_ON_RETURN, true),
                null));

        if (currentSettings.has("msg_notify")) {
            int value = currentSettings.optInt("msg_notify", 1);
            settingsList.add(new MessageSettingItem(
                    "msg_notify",
                    "消息提醒",
                    "是否接收消息提醒",
                    MessageSettingItem.TYPE_CHOOSE,
                    value == 1,
                    new String[]{"接收", "不接收"}));
        }

        if (currentSettings.has("show_unfollowed_msg")) {
            int value = currentSettings.optInt("show_unfollowed_msg", 0);
            settingsList.add(new MessageSettingItem(
                    "show_unfollowed_msg",
                    "收起未关注人消息",
                    "收起来自未关注用户的消息",
                    MessageSettingItem.TYPE_SWITCH,
                    value == 1,
                    null));
        }

        if (currentSettings.has("is_group_fold")) {
            int value = currentSettings.optInt("is_group_fold", 0);
            settingsList.add(new MessageSettingItem(
                    "is_group_fold",
                    "收起应援团消息",
                    "折叠应援团相关消息",
                    MessageSettingItem.TYPE_SWITCH,
                    value == 1,
                    null));
        }

        if (currentSettings.has("should_receive_group")) {
            int value = currentSettings.optInt("should_receive_group", 1);
            settingsList.add(new MessageSettingItem(
                    "should_receive_group",
                    "接收应援团消息",
                    "是否接收应援团消息",
                    MessageSettingItem.TYPE_SWITCH,
                    value == 1,
                    null));
        }

        if (currentSettings.has("receive_unfollow_msg")) {
            int value = currentSettings.optInt("receive_unfollow_msg", 1);
            settingsList.add(new MessageSettingItem(
                    "receive_unfollow_msg",
                    "接收未关注人消息",
                    "是否接收未关注用户的消息",
                    MessageSettingItem.TYPE_SWITCH,
                    value == 1,
                    null));
        }

        if (currentSettings.has("ai_intercept")) {
            int value = currentSettings.optInt("ai_intercept", 0);
            settingsList.add(new MessageSettingItem(
                    "ai_intercept",
                    "私信智能拦截",
                    "使用AI智能过滤骚扰私信",
                    MessageSettingItem.TYPE_SWITCH,
                    value == 1,
                    null));
        }
    }

    private void onSettingChanged(String key, boolean value) {
        if (SharedPreferencesUtil.MESSAGE_REFRESH_ON_RETURN.equals(key)) {
            SharedPreferencesUtil.putBoolean(key, value);
            return;
        }
        CenterThreadPool.run(() -> {
            try {
                JSONObject settings = new JSONObject();

                if (key.equals("msg_notify")) {
                    settings.put(key, value ? 1 : 3);
                } else {
                    settings.put(key, value ? 1 : 0);
                }

                JSONObject response = MessageApi.setMsgSettings(settings);
                if (response.getInt("code") == 0) {
                    runOnUiThread(() -> MsgUtil.showMsg("设置已保存"));
                } else {
                    runOnUiThread(() -> {
                        String message = response.optString("message", "未知错误");
                        MsgUtil.showMsg("保存失败: " + message);
                        loadSettings();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    MsgUtil.showMsg("保存失败");
                    loadSettings();
                });
            }
        });
    }
}
