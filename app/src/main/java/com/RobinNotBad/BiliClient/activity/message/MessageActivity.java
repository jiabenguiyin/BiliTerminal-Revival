package com.RobinNotBad.BiliClient.activity.message;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.RelativeLayout;
import androidx.appcompat.widget.TooltipCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.adapter.message.PrivateMsgSessionsAdapter;
import com.RobinNotBad.BiliClient.api.MessageApi;
import com.RobinNotBad.BiliClient.api.PrivateMsgApi;
import com.RobinNotBad.BiliClient.api.PrivateSessionLoader;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.model.PrivateMsgSession;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.ui.TerminalDialogStyle;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MessageUnreadCounter;
import com.RobinNotBad.BiliClient.util.MessageUnreadState;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class MessageActivity extends InstanceActivity {
    private RecyclerView sessionsView;
    private SwipeRefreshLayout refresh;
    private View markAllReadButton;
    private TextView privateSessionsTitle;
    private AlertDialog markAllReadDialog;
    private boolean markingRead;
    private boolean loading;
    private View sessionFeedback, sessionRetry;
    private TextView sessionStatus;
    private Runnable enableSessionRetry;
    private boolean hasResumed;
    private long requestedAccount;
    private int requestSequence;

    private boolean toolbarObserved;
    private int toolbarWidth = -1;
    private boolean toolbarRound;

    @Override public void setRound() {
        View heading = findViewById(R.id.message_heading);
        if (heading == null) { super.setRound(); return; }
        View top = findViewById(R.id.top);
        if (!toolbarObserved) {
            toolbarObserved = true;
            top.addOnLayoutChangeListener((v, left, t, right, bottom, oldLeft, oldT, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft) arrangeToolbar(right - left);
            });
        }
        arrangeToolbar(top.getWidth() > 0 ? top.getWidth() : window_width);
    }

    private void arrangeToolbar(int width) {
        if (width <= 0) return;
        boolean round = SharedPreferencesUtil.getBoolean("player_ui_round", false);
        if (toolbarWidth == width && toolbarRound == round) return;
        toolbarWidth = width; toolbarRound = round;
        float density = getResources().getDisplayMetrics().density;
        TextView title = findViewById(R.id.pageName), clock = findViewById(R.id.timeText);
        View heading = findViewById(R.id.message_heading), balance = findViewById(R.id.message_heading_balance);
        // Bound the toolbar controls on tiny screens without shrinking the rest of the page.
        int target = Math.min(Math.round(32 * density), Math.max(32, Math.round(width * .16f)));
        int glyph = Math.min(Math.round(16 * density), Math.max(16, Math.round(width * .08f)));
        int padding = Math.max(0, (target - glyph) / 2), gap = Math.round(target * .125f);
        for (int id : new int[]{R.id.mark_all_read, R.id.setting_btn}) {
            View action = findViewById(id);
            android.widget.LinearLayout.LayoutParams params = (android.widget.LinearLayout.LayoutParams)action.getLayoutParams();
            params.width = params.height = target;
            params.leftMargin = id == R.id.setting_btn ? gap : 0;
            androidx.core.view.MarginLayoutParamsCompat.setMarginStart(params, params.leftMargin);
            action.setLayoutParams(params);
            action.setPadding(padding, padding, padding, padding);
        }
        android.text.TextPaint titlePaint = new android.text.TextPaint(title.getPaint());
        titlePaint.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
        int titleExtra = title.getPaddingLeft() + title.getPaddingRight();
        android.graphics.drawable.Drawable[] icons = androidx.core.widget.TextViewCompat.getCompoundDrawablesRelative(title);
        for (int index : new int[]{0, 2}) if (icons[index] != null)
            titleExtra += Math.max(icons[index].getIntrinsicWidth(), icons[index].getBounds().width()) + title.getCompoundDrawablePadding();
        int titleWidth = (int)Math.ceil(titlePaint.measureText(title.getText().toString())) + titleExtra;
        clock.setTextSize(12);
        int clockWidth = (int)Math.ceil(clock.getPaint().measureText("00:00"));
        int side = Math.round(8 * density), actionsWidth = target * 2 + gap;
        boolean stacked = round || width < 2 * Math.max(titleWidth, clockWidth) + actionsWidth + side * 2;
        RelativeLayout.LayoutParams clockParams = new RelativeLayout.LayoutParams(-2, -2);
        RelativeLayout.LayoutParams rowParams = new RelativeLayout.LayoutParams(stacked ? -2 : -1, -2);
        android.widget.LinearLayout.LayoutParams titleParams = (android.widget.LinearLayout.LayoutParams)title.getLayoutParams();
        title.setMinHeight(target);
        if (stacked) {
            clockParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
            clockParams.topMargin = Math.round(2 * density);
            rowParams.addRule(RelativeLayout.BELOW, R.id.timeText);
            rowParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
            rowParams.bottomMargin = Math.round(2 * density);
            int titleGap = Math.round(target * .3f);
            int available = Math.max(1, Math.round(width * (round ? .82f : .94f)) - actionsWidth - titleGap);
            titleParams.width = Math.min(titleWidth, available);
            titleParams.weight = 0;
            titleParams.rightMargin = titleGap;
            balance.setVisibility(View.GONE);
        } else {
            clockParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            clockParams.addRule(RelativeLayout.CENTER_VERTICAL);
            clockParams.rightMargin = side;
            rowParams.leftMargin = rowParams.rightMargin = side;
            titleParams.width = 0;
            titleParams.weight = 1;
            titleParams.rightMargin = 0;
            balance.setVisibility(View.VISIBLE);
        }
        androidx.core.view.MarginLayoutParamsCompat.setMarginEnd(titleParams, titleParams.rightMargin);
        title.setLayoutParams(titleParams);
        clock.setLayoutParams(clockParams);
        heading.setPadding(0, 0, 0, 0);
        heading.setLayoutParams(rowParams);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        asyncInflate(R.layout.activity_message,(view,resId)->{
            refresh=findViewById(R.id.swipeRefreshLayout);
            refresh.setEnabled(true);refresh.setOnRefreshListener(this::loadMessages);
            findViewById(R.id.setting_btn).setOnClickListener(v->startActivity(new Intent(this,MessageSettingsActivity.class)));
            openNotice(R.id.reply,"reply");openNotice(R.id.like,"like");
            openNotice(R.id.at,"at");openNotice(R.id.system,"system");
            markAllReadButton = findViewById(R.id.mark_all_read);
            markAllReadButton.setOnClickListener(v -> confirmMarkAllRead());
            TooltipCompat.setTooltipText(markAllReadButton, getString(R.string.message_mark_all_read));
            TooltipCompat.setTooltipText(findViewById(R.id.setting_btn), "消息设置");
            sessionsView=findViewById(R.id.sessions_list);
            privateSessionsTitle=findViewById(R.id.private_sessions_title);
            sessionFeedback=findViewById(R.id.private_sessions_feedback);
            sessionStatus=findViewById(R.id.private_sessions_status);
            sessionRetry=findViewById(R.id.private_sessions_retry);
            sessionRetry.setOnClickListener(v -> loadMessages());
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
        super.onResume();
        if (hasResumed && sessionsView != null
                && (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_REFRESH_ON_RETURN, true)
                    || requestedAccount != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))) {
            loadMessages();
        }
        hasResumed = true;
    }
    private void unreadLabel(int id,String title,int count) {
        TextView text=findViewById(id);
        text.setText(count>0?title+" ("+count+"未读)":title);
        text.setTextColor(count>0?0xffff8eaa:0xffffffff);
    }
    private void confirmMarkAllRead() {
        if (markingRead || (markAllReadDialog != null && markAllReadDialog.isShowing())) return;
        final MessageApi.ReadSession session;
        try {
            session = MessageApi.ReadSession.capture(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
        } catch (IOException error) {
            MsgUtil.showMsg(error.getMessage());
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.message_mark_all_read)
                .setMessage(R.string.message_mark_all_read_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.message_confirm_read, (ignored, which) -> {
                    if (session.isCurrent()) loadMessages(session);
                    else MsgUtil.showMsg(getString(R.string.message_mark_read_account_changed));
                }).create();
        markAllReadDialog = dialog;
        dialog.setOnDismissListener(ignored -> { if (markAllReadDialog == dialog) markAllReadDialog = null; });
        TerminalDialogStyle.prepareSeparatedActions(dialog);
        dialog.show();
    }

    private void loadMessages() {
        loadMessages(null);
    }

    private void loadMessages(MessageApi.ReadSession readSession) {
        long currentAccount = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (currentAccount != requestedAccount) sessionsView.setAdapter(null);
        if (loading || markingRead) return;
        loading = true;
        markingRead = readSession != null;
        markAllReadButton.setEnabled(false);
        sessionRetry.setEnabled(false);
        if (enableSessionRetry != null) sessionRetry.removeCallbacks(enableSessionRetry);
        markAllReadButton.setContentDescription(getString(markingRead ? R.string.message_mark_read_running : R.string.message_mark_all_read));
        markAllReadButton.setAlpha(markingRead ? .45f : 1f);
        final int sequence=++requestSequence;
        final long account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        requestedAccount = account;
        final MessageUnreadState.Check initialCheck = markingRead ? null : MessageUnreadCounter.beginCheck();
        refresh.setRefreshing(true);
        CenterThreadPool.run(()->{
            if (readSession != null) readSession.markAllRead();
            final MessageUnreadState.Check check = readSession == null ? initialCheck : MessageUnreadCounter.beginCheck();
            JSONObject stats=null;Integer privateCount=null;
            ArrayList<PrivateMsgSession> sessions=null;HashMap<Long,UserInfo> users=new HashMap<>();
            Exception error=null;
            PrivateSessionLoader.Result sessionResult=null;
            try{stats=MessageApi.getUnread();}catch(Exception e){error=e;}
            try{privateCount=MessageApi.checkPrivateMsgUnread();}catch(Exception e){if(error==null)error=e;}
            try {
                sessionResult=PrivateSessionLoader.loadCurrent(20, privateCount == null ? 0 : privateCount,
                        readSession != null);
                sessions=sessionResult.sessions;
                if (sessionResult.failure != null) error=sessionResult.failure;
                if (sessions != null) Collections.sort(sessions,(a,b)->(b.unread>0?1:0)-(a.unread>0?1:0));
                ArrayList<Long> ids=new ArrayList<>();
                if (sessions != null) for(PrivateMsgSession session:sessions)if(!session.hasAccountInfo())ids.add(session.talkerUid);
                if(!ids.isEmpty())try{users=PrivateMsgApi.getUsersInfo(ids);}catch(Exception ignored){}
            }catch(Exception e){error=e;}
            final JSONObject result=stats;final Integer privateResult=privateCount;
            final ArrayList<PrivateMsgSession> rows=sessions;final HashMap<Long,UserInfo> names=users;
            final Exception failure=error;
            final PrivateSessionLoader.Result privateLoad=sessionResult;
            runOnUiThread(()->{
                if (isDestroyed() || sequence != requestSequence) return;
                loading = false;
                markingRead = false;
                markAllReadButton.setEnabled(true);
                markAllReadButton.setContentDescription(getString(R.string.message_mark_all_read));
                markAllReadButton.setAlpha(1f);
                refresh.setRefreshing(false);
                if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                        || (readSession != null && !readSession.isCurrent())) {
                    if (readSession != null) MsgUtil.showMsg(getString(R.string.message_mark_read_account_changed));
                    loadMessages();
                    return;
                }
                if(result!=null) {
                    unreadLabel(R.id.reply_text,"回复我的",result.optInt("reply"));
                    unreadLabel(R.id.like_text,"收到的赞",result.optInt("like"));
                    unreadLabel(R.id.at_text,"@我",result.optInt("at"));
                    unreadLabel(R.id.system_text,"系统通知",result.optInt("system"));
                }
                if (privateSessionsTitle != null) {
                    privateSessionsTitle.setText(privateResult != null && privateResult > 0
                            ? getString(R.string.message_private_sessions_unread, privateResult)
                            : getString(R.string.message_private_sessions));
                }
                if (result == null && privateResult == null) {
                    MessageUnreadCounter.fail(check);
                } else {
                    Integer interactions = result == null ? null : MessageApi.countInteractionUnread(result);
                    boolean accepted = MessageUnreadCounter.complete(check, interactions, privateResult);
                    if (readSession != null && check != null && !accepted) MessageUnreadCounter.refresh(true);
                }
                if (readSession != null) {
                    if (result == null || privateResult == null) {
                        MsgUtil.showMsg(getString(R.string.message_mark_read_unconfirmed));
                    } else {
                        long remaining = (long) MessageApi.countInteractionUnread(result) + privateResult;
                        MsgUtil.showMsg(remaining == 0 ? getString(R.string.message_mark_read_done)
                                : getString(R.string.message_mark_read_remaining, remaining));
                    }
                }
                // Listing conversations is not a read acknowledgement. Unread survives
                // until the relevant notice/chat is actually read and the server confirms it.
                if(rows!=null)sessionsView.setAdapter(new PrivateMsgSessionsAdapter(this,rows,names));
                showSessionStatus(privateLoad, rows == null ? failure : null, sequence, account);
                View scroll=findViewById(R.id.scrollView);
                scroll.setFocusable(true);scroll.setFocusableInTouchMode(true);scroll.requestFocus();
            });
        });
    }

    private void showSessionStatus(PrivateSessionLoader.Result result, Exception fallback,
                                   int sequence, long account) {
        Exception failure = result == null ? fallback : result.failure;
        if (failure == null) { sessionFeedback.setVisibility(View.GONE); return; }
        sessionFeedback.setVisibility(View.VISIBLE);
        int code = failure instanceof PrivateMsgApi.SessionApiException
                ? ((PrivateMsgApi.SessionApiException) failure).code : 0;
        String text = code == -509 ? getString(R.string.message_private_limited)
                : code != 0 ? getString(R.string.message_private_business_error, code)
                : getString(R.string.message_private_load_failed);
        if (result != null && result.sessions != null) text += "\n" + getString(R.string.message_private_showing_cached);
        final String readyText = text;
        long delay = result == null ? 0 : result.retryAfterMs;
        if (delay > 0) text += "\n" + getString(R.string.message_private_retry_after, (delay + 999) / 1000);
        sessionStatus.setText(text);
        sessionRetry.setEnabled(delay == 0);
        enableSessionRetry = () -> {
            if (!isDestroyed() && account == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                    && sequence == requestSequence && !loading) {
                sessionRetry.setEnabled(true);
                sessionStatus.setText(readyText);
            }
        };
        if (delay > 0) sessionRetry.postDelayed(enableSessionRetry, delay);
    }

    @Override protected void onDestroy() {
        requestSequence++;
        if (sessionRetry != null && enableSessionRetry != null) sessionRetry.removeCallbacks(enableSessionRetry);
        super.onDestroy();
    }
}
