package com.RobinNotBad.BiliClient.activity.user;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.SearchableVideoListActivity;
import com.RobinNotBad.BiliClient.api.HistoryApi;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;

import java.util.List;
import java.util.*;
import com.RobinNotBad.BiliClient.util.*;

public class HistoryActivity extends SearchableVideoListActivity {
    private long account;
    private boolean deleting;
    private AlertDialog activeDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageName("历史记录");
        account=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        startVideoList(savedInstanceState, R.string.library_search_history);
    }

    @Override protected void configureVideoAdapter(){
        videoCardAdapter.setOnLongClickListener(position->{
            if(deleting||position<0||position>=videoList.size())return;
            VideoCard selected=videoList.get(position);
            show(new AlertDialog.Builder(this).setTitle(selected.title).setItems(new String[]{"删除这条记录","多选删除"},(dialog,which)->{
                if(which==0)confirmSingle(selected);else chooseMultiple(selected.aid);
            }).setNegativeButton("取消",null).create());
        });
    }
    @Override protected boolean canReloadVideos(){return !deleting;}

    private HistoryApi.DeleteSession authorization(){
        try{return HistoryApi.DeleteSession.capture(account);}catch(Exception error){MsgUtil.showMsgLong(error.getMessage());return null;}
    }
    private void confirmSingle(VideoCard target){
        HistoryApi.DeleteSession session=authorization();if(session==null)return;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("删除历史记录")
                .setMessage("将同步删除账号的这条观看记录，无法恢复。\n\n《"+target.title+"》")
                .setNegativeButton("取消",null).setPositiveButton("删除",null).create();show(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->delete(session,Collections.singletonList(target.aid),dialog));
    }
    private void chooseMultiple(long initial){
        HistoryApi.DeleteSession session=authorization();if(session==null)return;
        LinkedHashMap<Long,VideoCard> unique=new LinkedHashMap<>();for(VideoCard card:videoList)if(card.aid>0)unique.put(card.aid,card);
        ArrayList<VideoCard> snapshot=new ArrayList<>(unique.values());boolean[] checked=new boolean[snapshot.size()];String[] titles=new String[snapshot.size()];
        for(int i=0;i<snapshot.size();i++){VideoCard row=snapshot.get(i);checked[i]=row.aid==initial;titles[i]=row.title.length()>48?row.title.substring(0,48)+"…":row.title;}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("多选删除（已加载的记录）")
                .setMultiChoiceItems(titles,checked,(d,which,value)->{
                    checked[which]=value;
                    if(selected(checked)>100){checked[which]=false;((AlertDialog)d).getListView().setItemChecked(which,false);MsgUtil.showMsg("每次最多选择100条");}
                    updateDeleteButton((AlertDialog)d,checked);
                }).setNegativeButton("取消",null).setPositiveButton("删除所选",null).create();show(dialog);updateDeleteButton(dialog,checked);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            ArrayList<Long> ids=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])ids.add(snapshot.get(i).aid);
            if(!ids.isEmpty())delete(session,ids,dialog);
        });
    }
    private int selected(boolean[] checked){int count=0;for(boolean value:checked)if(value)count++;return count;}
    private void updateDeleteButton(AlertDialog dialog,boolean[] checked){int count=selected(checked);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("删除所选（"+count+"）");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(count>0&&count<=100);}
    private void show(AlertDialog dialog){
        if(activeDialog!=null&&activeDialog.isShowing())activeDialog.dismiss();activeDialog=dialog;
        MsgUtil.prepareAlertDialog(dialog);dialog.setOnDismissListener(d->{if(activeDialog==dialog)activeDialog=null;});dialog.show();
    }
    private void controls(boolean enabled){
        enable(findViewById(R.id.library_search_bar),enabled);swipeRefreshLayout.setEnabled(enabled);
    }
    private void enable(View view,boolean enabled){if(view==null)return;view.setEnabled(enabled);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)enable(((ViewGroup)view).getChildAt(i),enabled);}
    private void delete(HistoryApi.DeleteSession session,List<Long> targets,AlertDialog confirmation){
        if(deleting)return;
        if(!session.isCurrent()){confirmation.dismiss();MsgUtil.showMsgLong("账号已变化，请重新选择记录");return;}
        final ArrayList<Long> ids=new ArrayList<>(targets);deleting=true;controls(false);confirmation.dismiss();setRefreshing(true);
        MsgUtil.showMsg("正在删除 "+ids.size()+" 条历史记录…");
        CenterThreadPool.run(()->{
            try{
                ApiResult result=session.delete(ids);
                runOnUiThread(()->{
                    if(isDestroyed())return;deleting=false;controls(true);setRefreshing(false);
                    if(!session.isCurrent()){MsgUtil.showMsgLong("账号已变化，请回原账号刷新核对删除结果");account=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);reloadVideos();return;}
                    if(result.code==0){
                        Set<Long> removed=new HashSet<>(ids);Iterator<VideoCard> iterator=videoList.iterator();while(iterator.hasNext())if(removed.contains(iterator.next().aid))iterator.remove();
                        videoCardAdapter.notifyDataSetChanged();MsgUtil.showMsg("已删除 "+ids.size()+" 条历史记录");if(videoList.isEmpty())reloadVideos();
                    }else MsgUtil.showMsgLong("删除失败："+result.message+"（"+result.code+"）");
                });
            }catch(Exception error){runOnUiThread(()->{if(isDestroyed())return;deleting=false;controls(true);setRefreshing(false);
                if(!session.isCurrent()){account=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);reloadVideos();MsgUtil.showMsgLong("账号已变化，请回原账号核对删除结果");}
                else MsgUtil.showMsgLong("删除结果未确认，请刷新历史记录核对");});}
        });
    }
    @Override protected void onResume(){super.onResume();long current=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        if(account!=current&&videoCardAdapter!=null){account=current;if(activeDialog!=null)activeDialog.dismiss();
            if(deleting){videoList.clear();videoCardAdapter.notifyDataSetChanged();emptyView.setText("账号已切换，正在核对请求状态…");showEmptyView();}
            else reloadVideos();}}
    @Override protected void onDestroy(){if(activeDialog!=null)activeDialog.dismiss();super.onDestroy();}

    @Override protected ApiResult loadVideos(int page, String keyword, ApiResult previous, List<VideoCard> output)
            throws Exception {
        return keyword.isEmpty() ? HistoryApi.getHistory(previous, output)
                : HistoryApi.searchHistory(keyword, page, output);
    }
}
