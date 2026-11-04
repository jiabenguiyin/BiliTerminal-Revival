package com.RobinNotBad.BiliClient.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import com.RobinNotBad.BiliClient.activity.browser.BrowserActivity;
import com.RobinNotBad.BiliClient.api.VideoReportApi;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.util.*;
import java.util.List;

/** Tied to one fragment view and one immutable video target. */
public final class VideoReportController {
    private final Activity owner;private final View button;private final long aid;
    private androidx.lifecycle.LifecycleEventObserver observer;
    private boolean closed,busy;private VideoReportDialog dialog;private AlertDialog official;
    public VideoReportController(Activity owner,View button,long aid){this.owner=owner;this.button=button;this.aid=aid;
        button.setEnabled(true);button.setVisibility(aid>0?View.VISIBLE:View.GONE);button.setOnClickListener(v->open());
        if(owner instanceof androidx.lifecycle.LifecycleOwner){
            observer=(source,event)->{if(event==androidx.lifecycle.Lifecycle.Event.ON_DESTROY)close();};
            ((androidx.lifecycle.LifecycleOwner)owner).getLifecycle().addObserver(observer);
        }}
    private boolean alive(){return !closed&&!owner.isFinishing()&&(android.os.Build.VERSION.SDK_INT<17||!owner.isDestroyed());}
    public void open(){
        if(!alive()||busy||dialog!=null&&dialog.isShowing())return;
        final VideoReportApi.Session session;
        try{session=VideoReportApi.Session.capture(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0),aid);}
        catch(Exception error){MsgUtil.showMsgLong(error.getMessage());return;}
        busy=true;button.setEnabled(false);MsgUtil.showMsg("正在获取举报原因…");
        CenterThreadPool.run(()->{
            try{List<VideoReportApi.Reason> reasons=VideoReportApi.reasons();owner.runOnUiThread(()->{
                if(!alive())return;busy=false;button.setEnabled(true);
                if(!session.isCurrent()){MsgUtil.showMsgLong("账号已变化，请重新打开举报");return;}
                dialog=new VideoReportDialog(owner,aid,reasons,(reason,note)->{
                    if(reason.officialRequired)offerOfficial("该类型需要补充证明，请前往官方投诉页填写");else submit(session,reason,note);
                });dialog.show();
            });}catch(Exception error){owner.runOnUiThread(()->{if(!alive())return;busy=false;button.setEnabled(true);offerOfficial("未能获取举报原因，请稍后重试或使用官方投诉页");});}
        });
    }
    private void submit(VideoReportApi.Session session,VideoReportApi.Reason reason,String note){
        if(!alive()||busy)return;
        if(!session.isCurrent()){MsgUtil.showMsgLong("账号已变化，未提交举报");return;}
        busy=true;button.setEnabled(false);MsgUtil.showMsg("正在提交举报…");
        CenterThreadPool.run(()->{
            try{ApiResult result=session.submit(reason,note);owner.runOnUiThread(()->{
                if(!alive())return;busy=false;button.setEnabled(true);
                if(!session.isCurrent()){MsgUtil.showMsgLong("账号已变化，请回原账号核对提交结果");return;}
                if(result.code==0)MsgUtil.showMsg("举报已提交");
                else offerOfficial("举报未提交（"+result.code+"）。如 B 站要求验证，可在官方投诉页完成");
            });}catch(Exception error){owner.runOnUiThread(()->{if(!alive())return;busy=false;button.setEnabled(true);offerOfficial("未能确认举报结果，请先核对，暂勿重复提交");});}
        });
    }
    private void offerOfficial(String message){
        if(!alive())return;
        official=new AlertDialog.Builder(owner).setTitle("视频举报").setMessage(message+"\n\n目标：av"+aid+"\n官方页面可能需要重新登录。")
                .setNegativeButton("取消",null).setPositiveButton("官方投诉页",(d,w)->owner.startActivity(new Intent(owner,BrowserActivity.class).putExtra("url",VideoReportApi.officialUrl(aid)))).create();
        MsgUtil.prepareAlertDialog(official);official.show();
    }
    public void close(){if(closed)return;closed=true;busy=false;button.setEnabled(true);if(observer!=null)((androidx.lifecycle.LifecycleOwner)owner).getLifecycle().removeObserver(observer);if(dialog!=null)dialog.dismiss();if(official!=null)official.dismiss();button.setOnClickListener(null);}
}
