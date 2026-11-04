package com.RobinNotBad.BiliClient.activity.message;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.adapter.message.PrivateMsgSessionsAdapter;
import com.RobinNotBad.BiliClient.api.MessageApi;
import com.RobinNotBad.BiliClient.api.PrivateMsgApi;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.model.PrivateMsgSession;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MessageUnreadCounter;
import com.RobinNotBad.BiliClient.util.MessageUnreadState;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class MessageActivity extends InstanceActivity {
    private RecyclerView sessionsView;
    private SwipeRefreshLayout refresh;
    private int requestSequence;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        asyncInflate(R.layout.activity_message,(view,resId)->{
            refresh=findViewById(R.id.swipeRefreshLayout);
            refresh.setEnabled(true);refresh.setOnRefreshListener(this::loadMessages);
            findViewById(R.id.setting_btn).setOnClickListener(v->startActivity(new Intent(this,MessageSettingsActivity.class)));
            openNotice(R.id.reply,"reply");openNotice(R.id.like,"like");
            openNotice(R.id.at,"at");openNotice(R.id.system,"system");
            sessionsView=findViewById(R.id.sessions_list);
            sessionsView.setNestedScrollingEnabled(false);sessionsView.setItemAnimator(null);
            sessionsView.setLayoutManager(new CustomLinearManager(this));
            loadMessages();
            TutorialHelper.showTutorialList(this,R.array.tutorial_message,5);
        });
    }
    private void openNotice(int id,String type) {
        findViewById(id).setOnClickListener(v->startActivity(new Intent(this,NoticeActivity.class).putExtra("type",type)));
    }
    @Override protected void onResume() {
        super.onResume();if(sessionsView!=null)loadMessages();
    }
    private void unreadLabel(int id,String title,int count) {
        TextView text=findViewById(id);
        text.setText(count>0?title+" ("+count+"未读)":title);
        text.setTextColor(count>0?0xffff8eaa:0xffffffff);
    }
    private void loadMessages() {
        final int sequence=++requestSequence;
        final long requestedAccount=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        final MessageUnreadState.Check check=MessageUnreadCounter.beginCheck();
        refresh.setRefreshing(true);
        CenterThreadPool.run(()->{
            JSONObject stats=null;Integer privateCount=null;
            ArrayList<PrivateMsgSession> sessions=null;HashMap<Long,UserInfo> users=new HashMap<>();
            Exception error=null;
            try{stats=MessageApi.getUnread();}catch(Exception e){error=e;}
            try{privateCount=MessageApi.checkPrivateMsgUnread();}catch(Exception e){if(error==null)error=e;}
            try {
                sessions=PrivateMsgApi.getSessionsList(20);
                Collections.sort(sessions,(a,b)->(b.unread>0?1:0)-(a.unread>0?1:0));
                ArrayList<Long> ids=new ArrayList<>();
                for(PrivateMsgSession session:sessions)if(!session.hasAccountInfo())ids.add(session.talkerUid);
                if(!ids.isEmpty())try{users=PrivateMsgApi.getUsersInfo(ids);}catch(Exception ignored){}
            }catch(Exception e){error=e;}
            final JSONObject result=stats;final Integer privateResult=privateCount;
            final ArrayList<PrivateMsgSession> rows=sessions;final HashMap<Long,UserInfo> names=users;
            final Exception failure=error;
            runOnUiThread(()->{
                if(isDestroyed()||sequence!=requestSequence||requestedAccount!=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0))return;
                refresh.setRefreshing(false);
                if(result!=null) {
                    unreadLabel(R.id.reply_text,"回复我的",result.optInt("reply"));
                    unreadLabel(R.id.like_text,"收到的赞",result.optInt("like"));
                    unreadLabel(R.id.at_text,"@我",result.optInt("at"));
                    unreadLabel(R.id.system_text,"系统通知",result.optInt("system"));
                }
                if(result==null&&privateResult==null)MessageUnreadCounter.fail(check);
                else MessageUnreadCounter.complete(check,result==null?null:Integer.valueOf(MessageApi.countInteractionUnread(result)),privateResult);
                // Listing conversations is not a read acknowledgement. Unread survives
                // until the relevant notice/chat is actually read and the server confirms it.
                if(rows!=null)sessionsView.setAdapter(new PrivateMsgSessionsAdapter(this,rows,names));
                else if(failure!=null)MsgUtil.err(failure);
                View scroll=findViewById(R.id.scrollView);
                scroll.setFocusable(true);scroll.setFocusableInTouchMode(true);scroll.requestFocus();
            });
        });
    }
}
