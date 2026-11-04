package com.RobinNotBad.BiliClient.activity.user;

import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.browser.BrowserActivity;
import com.RobinNotBad.BiliClient.activity.video.info.VideoInfoActivity;
import com.RobinNotBad.BiliClient.api.CreatorArchiveApi;
import com.RobinNotBad.BiliClient.api.CreatorArchiveApi.*;
import com.RobinNotBad.BiliClient.util.*;
import java.util.*;

/** A bounded page of the signed-in account's private archive list. */
public class CreatorArchiveActivity extends BaseActivity {
    private static final String[] LABELS={"全部稿件","已发布","未发布"};
    private static final String[] FILTERS={CreatorArchiveApi.ALL,CreatorArchiveApi.PUBLISHED,CreatorArchiveApi.UNPUBLISHED};
    private final ArrayList<Archive> items=new ArrayList<>();
    private final Set<Long> uncertain=new HashSet<>();
    private final ArrayList<AlertDialog> dialogs=new ArrayList<>();
    private final ArchiveAdapter adapter=new ArchiveAdapter();
    private Session session;
    private RecyclerView list;
    private SwipeRefreshLayout swipe;
    private TextView status,empty,filter;
    private View previous,next;
    private int page=1,selection,generation;
    private boolean busy,hasMore,deleting,returningFromOfficial,created;
    private AlertDialog deleteDialog;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);setContentView(R.layout.activity_creator_archives);setPageName("稿件管理");setTopbarExit();
        list=findViewById(R.id.creator_list);list.setLayoutManager(new com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager(this));list.setAdapter(adapter);
        swipe=findViewById(R.id.creator_swipe);status=findViewById(R.id.creator_page_status);empty=findViewById(R.id.creator_empty);
        filter=findViewById(R.id.creator_filter);previous=findViewById(R.id.creator_previous);next=findViewById(R.id.creator_next);
        filter.setOnClickListener(view->{
            if(deleting){MsgUtil.showMsg("正在处理删除，请稍候");return;}
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("稿件状态").setSingleChoiceItems(LABELS,selection,(d,which)->{
                d.dismiss();selection=which;filter.setText(LABELS[which]+" ▾");
                items.clear();adapter.notifyDataSetChanged();page=1;hasMore=false;load(1,true);
            }).setNegativeButton(android.R.string.cancel,null).create();show(dialog);
        });
        previous.setOnClickListener(view->{if(!busy&&!deleting&&page>1)load(page-1,false);});
        next.setOnClickListener(view->{if(!busy&&!deleting&&hasMore)load(page+1,false);});
        swipe.setOnRefreshListener(()->load(1,true));
        empty.setOnClickListener(view->{if(!busy&&!deleting)load(1,true);});
        status.setOnClickListener(view->{if(!busy&&!deleting)load(page,true);});
        created=true;load(1,true);
    }
    @Override protected void onResume() {
        super.onResume();
        if(created&&(returningFromOfficial||session!=null&&!session.isCurrent())){
            returningFromOfficial=false;load(1,true);
        }
    }
    private void load(int requested,boolean explicitRefresh) {
        if(deleting){swipe.setRefreshing(false);return;}
        final int request=++generation;
        try {
            if(session==null||!session.isCurrent()){
                items.clear();adapter.notifyDataSetChanged();page=1;hasMore=false;uncertain.clear();
                session=null;session=CreatorArchiveApi.capture();
            }
        }catch(Failure error){busy=false;status.setText(error.getMessage());empty.setText(error.getMessage()+"\n登录后点击重试");empty.setVisibility(View.VISIBLE);swipe.setRefreshing(false);buttons();return;}
        final Session account=session;final int type=selection;busy=true;swipe.setRefreshing(true);buttons();
        status.setText("正在加载本人稿件…");empty.setVisibility(View.GONE);
        CenterThreadPool.run(()->{
            try{
                Page result=CreatorArchiveApi.archives(account,requested,FILTERS[type]);
                runOnUiThread(()->{
                    if(!accepts(request,account))return;
                    items.clear();items.addAll(result.items);page=requested;hasMore=result.hasMore;
                    if(explicitRefresh)uncertain.clear();
                    adapter.notifyDataSetChanged();list.scrollToPosition(0);busy=false;swipe.setRefreshing(false);buttons();
                    status.setText("共 "+result.total+" 个稿件 · 第 "+page+" 页");
                    empty.setText("暂无"+LABELS[type]+"\n下拉或点击刷新");empty.setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);
                });
            }catch(Exception error){runOnUiThread(()->{
                if(!accepts(request,account))return;
                busy=false;swipe.setRefreshing(false);buttons();String message=CreatorArchiveApi.message(error);
                status.setText(message+" · 点击重试");
                if(items.isEmpty()){empty.setText(message+"\n点击重试");empty.setVisibility(View.VISIBLE);}else MsgUtil.showMsgLong(message);
            });}
        });
    }
    private boolean accepts(int request,Session account){
        if(isDestroyed()||generation!=request||session!=account)return false;
        if(!account.isCurrent()){
            deleting=false;busy=false;
            if(deleteDialog!=null)deleteDialog.dismiss();
            load(1,true);return false;
        }
        return true;
    }
    private void buttons(){previous.setEnabled(!busy&&!deleting&&page>1);next.setEnabled(!busy&&!deleting&&hasMore);}
    private static String count(long number){return number<0?"—":StringUtil.toWan(number);}
    private void details(Archive item){
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(item.title).setMessage((item.bvid.isEmpty()?"av"+item.aid:item.bvid)+"\n"+item.stateText
                +"\n播放："+count(item.views)+"\n点赞："+count(item.likes)+"\n评论："+count(item.replies)+"\n收藏："+count(item.favorites))
                .setPositiveButton(android.R.string.ok,null).create();show(dialog);
    }
    private void menu(Archive item){
        ArrayList<String> actions=new ArrayList<>(Arrays.asList("稿件数据","打开视频","官方稿件管理"));
        if(item.ownerMid<=0||session!=null&&item.ownerMid==session.mid)actions.add("删除本人稿件");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(item.title).setItems(actions.toArray(new String[0]),(d,which)->{
            switch(which){case 0:details(item);break;case 1:openVideo(item);break;case 2:official(item);break;case 3:prepareDelete(item);break;}
        }).setNegativeButton(android.R.string.cancel,null).create();show(dialog);
    }
    private void openVideo(Archive item){
        if(item.state!=0){details(item);return;}
        startActivity(new Intent(this,VideoInfoActivity.class).putExtra("aid",item.aid));
    }
    private void prepareDelete(Archive item){
        if(busy||deleting||deleteDialog!=null&&deleteDialog.isShowing()){MsgUtil.showMsg("正在处理，请稍候");return;}
        if(uncertain.contains(item.aid)){MsgUtil.showMsgLong("上次删除结果尚未确认，请先刷新列表核对");return;}
        final Session account=session;final int request=generation;
        if(account==null||!account.isCurrent()){load(1,true);return;}
        deleting=true;buttons();MsgUtil.showMsg("正在核对本人稿件…");
        CenterThreadPool.run(()->{
            try{
                OwnedArchive owned=CreatorArchiveApi.prepareDelete(account,item);
                runOnUiThread(()->{if(!accepts(request,account))return;deleting=false;buttons();confirmDelete(account,item,owned);});
            }catch(Exception error){runOnUiThread(()->{if(!accepts(request,account))return;deleting=false;buttons();
                message("无法删除",CreatorArchiveApi.message(error),item);});}
        });
    }
    private void confirmDelete(Session account,Archive item,OwnedArchive owned){
        CheckBox consent=new CheckBox(this);consent.setId(R.id.creator_delete_consent);
        consent.setText("我确认永久删除此稿件及全部分P，删除后无法恢复");
        int padding=MsgUtil.dp(this,12);consent.setPadding(padding,padding,padding,padding);
        deleteDialog=new AlertDialog.Builder(this).setTitle("删除本人稿件")
                .setMessage("《"+owned.title+"》\n"+(owned.bvid.isEmpty()?"av"+owned.aid:owned.bvid)+"\n发布账号：UID "+account.mid)
                .setView(consent).setNegativeButton(android.R.string.cancel,null).setPositiveButton("删除此稿件",null).create();
        show(deleteDialog);Button positive=deleteDialog.getButton(AlertDialog.BUTTON_POSITIVE);positive.setEnabled(false);positive.setTextColor(0xffffa0aa);
        consent.setOnCheckedChangeListener((button,checked)->positive.setEnabled(checked&&!deleting));
        positive.setOnClickListener(view->{
            if(!consent.isChecked()||deleting)return;
            if(!account.isCurrent()){deleteDialog.dismiss();MsgUtil.showMsgLong("账号已变化，请重新选择稿件");load(1,true);return;}
            deleting=true;positive.setEnabled(false);deleteDialog.dismiss();buttons();
            performDelete(account,item,owned);
        });
    }
    private void performDelete(Session account,Archive item,OwnedArchive owned){
        CenterThreadPool.run(()->{
            try{
                CreatorArchiveApi.delete(account,owned);
                runOnUiThread(()->{
                    if(isDestroyed())return;deleting=false;buttons();
                    if(!account.isCurrent()){MsgUtil.showMsgLong("账号已变化，请回原账号核对删除结果");load(1,true);return;}
                    MsgUtil.showMsg("稿件已删除");load(1,true);
                });
            }catch(Exception error){runOnUiThread(()->{
                if(isDestroyed())return;deleting=false;buttons();
                if(!account.isCurrent()){MsgUtil.showMsgLong("账号已变化，请回原账号核对已发送的删除请求");load(1,true);return;}
                if(error instanceof Failure&&((Failure)error).code!=-10002)message("未完成删除",error.getMessage(),item);
                else{uncertain.add(item.aid);adapter.notifyDataSetChanged();message("删除结果待确认","请求中断，无法确认删除结果。请先刷新列表核对，暂勿重复提交。",item);}
            });}
        });
    }
    private void message(String title,String text,Archive item){
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setMessage(text)
                .setNegativeButton("返回",null).setPositiveButton("官方管理",(d,w)->official(item)).create();show(dialog);
    }
    private void official(Archive item){
        if(session==null||!session.isCurrent()){load(1,true);return;}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("前往官方稿件管理")
                .setMessage("目标：《"+item.title+"》\n"+(item.bvid.isEmpty()?"av"+item.aid:item.bvid)
                        +"\n请在官方页面确认登录账号、找到此稿件，并按提示验证和删除。网页登录状态与应用可能不同。")
                .setNegativeButton(android.R.string.cancel,null).setNeutralButton("复制稿件号",(d,w)->{
                    ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("稿件号",item.bvid.isEmpty()?"av"+item.aid:item.bvid));MsgUtil.showMsg("稿件号已复制");
                }).setPositiveButton("打开官方页面",(d,w)->{
                    returningFromOfficial=true;startActivity(new Intent(this,BrowserActivity.class).putExtra("url",CreatorArchiveApi.MANAGER_URL));
                }).create();show(dialog);
    }
    private void show(AlertDialog dialog){dialogs.add(dialog);MsgUtil.prepareAlertDialog(dialog);dialog.show();}
    @Override protected void onDestroy(){generation++;for(AlertDialog dialog:dialogs)if(dialog.isShowing())dialog.dismiss();dialogs.clear();super.onDestroy();}

    private final class ArchiveAdapter extends RecyclerView.Adapter<ArchiveHolder>{
        @NonNull @Override public ArchiveHolder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new ArchiveHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_creator_archive,parent,false));}
        @Override public int getItemCount(){return items.size();}
        @Override public void onBindViewHolder(@NonNull ArchiveHolder holder,int position){
            Archive item=items.get(position);holder.title.setText(item.title);holder.state.setText(item.stateText+(uncertain.contains(item.aid)?" · 删除结果待确认":""));
            holder.metrics.setText("播放 "+count(item.views)+" · 点赞 "+count(item.likes)+" · 评论 "+count(item.replies));
            holder.delete.setVisibility(item.ownerMid>0&&session!=null&&item.ownerMid!=session.mid?View.GONE:View.VISIBLE);
            holder.delete.setEnabled(!deleting);
            holder.delete.setOnClickListener(view->{int p=holder.getAdapterPosition();if(p>=0&&p<items.size())prepareDelete(items.get(p));});
            holder.itemView.setOnClickListener(view->{int p=holder.getAdapterPosition();if(p>=0&&p<items.size())details(items.get(p));});
            holder.itemView.setOnLongClickListener(view->{int p=holder.getAdapterPosition();if(p>=0&&p<items.size())menu(items.get(p));return true;});
        }
    }
    private static final class ArchiveHolder extends RecyclerView.ViewHolder{
        final TextView title,state,metrics;final View delete;
        ArchiveHolder(View view){super(view);title=view.findViewById(R.id.creator_title);state=view.findViewById(R.id.creator_state);metrics=view.findViewById(R.id.creator_metrics);delete=view.findViewById(R.id.creator_delete);}
    }
}
