package com.RobinNotBad.BiliClient.activity.message;

import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;

import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.message.NoticeAdapter;
import com.RobinNotBad.BiliClient.api.MessageApi;
import com.RobinNotBad.BiliClient.model.MessageCard;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;

import java.util.ArrayList;
import java.util.List;

public class NoticeActivity extends RefreshListActivity {
    private List<MessageCard> messageList;
    private NoticeAdapter noticeAdapter;
    private MessageCard.Cursor cursor;
    private String pageType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setPageName("详情");

        Intent intent = getIntent();
        pageType = intent.getStringExtra("type");
        messageList = new ArrayList<>();
        final long requestedAccount=com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.getLong(
                com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.mid,0);

        CenterThreadPool.run(() -> {
            try {
                Pair<MessageCard.Cursor, List<MessageCard>> pair;
                switch (pageType) {
                    case "like":
                        pair = MessageApi.getLikeMsg(0, 0);
                        cursor = pair.first;
                        messageList = pair.second;
                        break;
                    case "reply":
                        pair = MessageApi.getReplyMsg(0, 0);
                        cursor = pair.first;
                        messageList = pair.second;
                        break;
                    case "at":
                        pair = MessageApi.getAtMsg(0, 0);
                        cursor = pair.first;
                        messageList = pair.second;
                        break;
                    case "system":
                        messageList = MessageApi.getSystemMsg();
                        break;
                }

                noticeAdapter = new NoticeAdapter(this, messageList);
                runOnUiThread(() -> {
                    if(isDestroyed()||requestedAccount!=com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.getLong(
                            com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.mid,0))return;
                    setAdapter(noticeAdapter);
                    setRefreshing(false);
                    setOnLoadMoreListener(this::continueLoading);
                    com.RobinNotBad.BiliClient.util.MessageUnreadCounter.refresh(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void continueLoading(int i) {
        CenterThreadPool.run(() -> {
            try {
                int lastSize = messageList.size();
                Pair<MessageCard.Cursor, List<MessageCard>> pair;
                switch (pageType) {
                    case "like":
                        pair = MessageApi.getLikeMsg(cursor.id, cursor.time);
                        cursor = pair.first;
                        messageList.addAll(pair.second);
                        break;
                    case "reply":
                        pair = MessageApi.getReplyMsg(cursor.id, cursor.time);
                        cursor = pair.first;
                        messageList.addAll(pair.second);
                        break;
                    case "at":
                        pair = MessageApi.getAtMsg(cursor.id, cursor.time);
                        cursor = pair.first;
                        messageList.addAll(pair.second);
                        break;
                    case "system":
                        messageList = MessageApi.getSystemMsg();
                        break;
                }
                runOnUiThread(() -> noticeAdapter.notifyItemRangeInserted(lastSize, messageList.size() - lastSize));
                bottom = cursor.is_end;
                setRefreshing(false);
            } catch (Exception e) {
                e.printStackTrace();
                page--;
                setRefreshing(false);
            }
        });
    }
}
