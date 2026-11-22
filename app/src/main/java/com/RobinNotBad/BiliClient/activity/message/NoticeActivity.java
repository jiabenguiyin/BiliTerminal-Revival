package com.RobinNotBad.BiliClient.activity.message;

import android.os.Bundle;
import android.util.Pair;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.message.NoticeAdapter;
import com.RobinNotBad.BiliClient.api.MessageApi;
import com.RobinNotBad.BiliClient.model.MessageCard;
import com.RobinNotBad.BiliClient.util.*;
import java.util.*;

public class NoticeActivity extends RefreshListActivity {
    private final List<MessageCard> messageList=new ArrayList<>();
    private NoticeAdapter noticeAdapter;
    private MessageCard.Cursor cursor;
    private String pageType;
    private boolean loadingFirstPage,loadingMore;
    private int generation;
    private long visibleAccount;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        pageType=getIntent().getStringExtra("type");
        if(pageType==null)pageType="like";
        setPageName("like".equals(pageType)?"收到的赞":"reply".equals(pageType)?"回复我的":"at".equals(pageType)?"@我":"系统通知");
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setOnRefreshListener(this::loadFirstPage);
        emptyView.setOnClickListener(v->loadFirstPage());
        setOnLoadMoreListener(i->loadPage(false));
        loadFirstPage();
    }
    @Override protected void onResume() {
        super.onResume();
        // Drafts do not mark a thank-you as sent. Rebind after returning from chat.
        if(noticeAdapter!=null){
            if(visibleAccount!=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)){
                messageList.clear();noticeAdapter.notifyDataSetChanged();loadFirstPage();
            }else noticeAdapter.refreshThanks();
        }
    }
    private void loadFirstPage(){loadPage(true);}
    private void loadPage(boolean first) {
        if(first?loadingFirstPage:loadingFirstPage||loadingMore||cursor==null||cursor.is_end)return;
        if(first){loadingFirstPage=true;loadingMore=false;generation++;page=1;hideEmptyView();}
        else loadingMore=true;
        final int request=generation;
        final long account=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        final MessageCard.Cursor from=first?null:cursor;
        setRefreshing(true);
        CenterThreadPool.run(()->{
            try {
                Pair<MessageCard.Cursor,List<MessageCard>> result;
                long id=from==null?0:from.id,time=from==null?0:from.time;
                switch(pageType){
                    case "like":result=MessageApi.getLikeMsg(id,time);break;
                    case "reply":result=MessageApi.getReplyMsg(id,time);break;
                    case "at":result=MessageApi.getAtMsg(id,time);break;
                    case "system":result=new Pair<>(null,MessageApi.getSystemMsg(account));break;
                    default:throw new IllegalArgumentException("未知通知类型");
                }
                runOnUiThread(()->{
                    if(isDestroyed()||request!=generation)return;
                    loadingFirstPage=false;loadingMore=false;setRefreshing(false);
                    if(account!=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)){
                        messageList.clear();
                        if(noticeAdapter!=null)noticeAdapter.notifyDataSetChanged();
                        emptyView.setText(R.string.message_notice_account_changed);showEmptyView();bottom=true;return;
                    }
                    if(first)messageList.clear();
                    visibleAccount=account;
                    int before=messageList.size();
                    Set<Long> seen=new HashSet<>();
                    for(MessageCard message:messageList)if(message.id>0)seen.add(message.id);
                    for(MessageCard message:result.second)
                        if(message.id<=0||seen.add(message.id))messageList.add(message);
                    cursor=result.first;
                    bottom=cursor==null||cursor.is_end
                            ||from!=null&&cursor.id==from.id&&cursor.time==from.time;
                    if(noticeAdapter==null||first){
                        noticeAdapter=new NoticeAdapter(this,messageList);setAdapter(noticeAdapter);
                    }else noticeAdapter.notifyItemRangeInserted(before,messageList.size()-before);
                    if(messageList.isEmpty()){
                        emptyView.setText("system".equals(pageType)?R.string.message_system_empty:R.string.message_notice_empty);
                        showEmptyView();
                    }
                    if(first)MessageUnreadCounter.refresh(true);
                });
            }catch(Exception error){runOnUiThread(()->{
                if(isDestroyed()||request!=generation)return;
                loadingFirstPage=false;loadingMore=false;setRefreshing(false);
                if(!first)page=Math.max(1,page-1);
                if(messageList.isEmpty()){
                    emptyView.setText("system".equals(pageType)?R.string.message_system_load_failed:R.string.message_notice_load_failed);showEmptyView();
                }else MsgUtil.err(error);
            });}
        });
    }
    @Override protected void onDestroy(){generation++;super.onDestroy();}
}
