package com.RobinNotBad.BiliClient.activity.user;

import android.content.*;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
    private final Set<Long> uncertainTitles=new HashSet<>();
    private final ArrayList<AlertDialog> dialogs=new ArrayList<>();
    private final ArchiveAdapter adapter=new ArchiveAdapter();
    private Session session;
    private RecyclerView list;
    private SwipeRefreshLayout swipe;
    private TextView status,empty,filter;
    private View previous, searchBar;
    private TextView next;
    private EditText searchInput;
    private String keyword = "";
    private int page=1,selection,generation;
    private boolean busy,hasMore,deleting,returningFromOfficial,created;
    private AlertDialog deleteDialog;
    private AlertDialog titleDialog;
    private boolean editing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);setContentView(R.layout.activity_creator_archives);setPageName("稿件管理");setTopbarExit();
        list=findViewById(R.id.creator_list);list.setLayoutManager(new com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager(this));list.setAdapter(adapter);
        swipe=findViewById(R.id.creator_swipe);status=findViewById(R.id.creator_page_status);empty=findViewById(R.id.creator_empty);
        filter=findViewById(R.id.creator_filter);previous=findViewById(R.id.creator_previous);next=findViewById(R.id.creator_next);
        if (state != null && state.getLong("creator_account") == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
            keyword = state.getString("creator_keyword", "");
            selection = state.getInt("creator_filter");
        }
        filter.setText(LABELS[selection] + " ▾");
        bindSearch();
        filter.setOnClickListener(view->{
            if(deleting||editing){MsgUtil.showMsg("正在处理稿件，请稍候");return;}
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("稿件状态").setSingleChoiceItems(LABELS,selection,(d,which)->{
                d.dismiss();selection=which;filter.setText(LABELS[which]+" ▾");
                resetPage();
            }).setNegativeButton(android.R.string.cancel,null).create();show(dialog);
        });
        previous.setOnClickListener(view->{if(!busy&&!deleting&&page>1)load(page-1,false);});
        next.setOnClickListener(view->{if(!busy&&!deleting&&hasMore)load(page+1,false);});
        swipe.setOnRefreshListener(()->load(1,true));
        empty.setOnClickListener(view -> {
            if (!busy && !deleting && !editing) {
                if (hasMore && CreatorArchiveApi.isArchiveId(keyword)) load(page + 1, false);
                else load(1, true);
            }
        });
        status.setOnClickListener(view->{if(!busy&&!deleting)load(page,true);});
        created=true;load(1,true);
    }
    private void bindSearch() {
        searchBar = findViewById(R.id.library_search_bar);
        searchInput = findViewById(R.id.library_keyword);
        searchInput.setSaveEnabled(false);
        searchInput.setHint(R.string.creator_search_hint);
        searchInput.setContentDescription(getString(R.string.creator_search_hint));
        View clear = findViewById(R.id.library_clear);
        clear.setVisibility(keyword.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        findViewById(R.id.creator_search_toggle).setOnClickListener(view -> {
            if (deleting || editing) return;
            if (searchBar.getVisibility() == View.VISIBLE) {
                hideSearch();
                return;
            }
            searchBar.setVisibility(View.VISIBLE);
            searchInput.setText(keyword);
            searchInput.requestFocus();
            searchInput.setSelection(searchInput.length());
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) searchInput.post(() -> {
                if (searchInput.hasFocus()) keyboard.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            });
        });
        clear.setOnClickListener(view -> {
            searchInput.setText("");
            submitSearch();
        });
        findViewById(R.id.library_search).setOnClickListener(view -> submitSearch());
        searchInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                submitSearch();
                return true;
            }
            return false;
        });
    }

    private void hideSearch() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
        searchBar.setVisibility(View.GONE);
    }

    private void submitSearch() {
        if (deleting || editing) return;
        keyword = searchInput.getText().toString().trim();
        hideSearch();
        resetPage();
    }

    private void resetPage() {
        items.clear();
        adapter.notifyDataSetChanged();
        page = 1;
        hasMore = false;
        load(1, true);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putLong("creator_account", session == null ? 0 : session.mid);
        state.putString("creator_keyword", keyword);
        state.putInt("creator_filter", selection);
    }

    @Override protected void onResume() {
        super.onResume();
        if(created&&(returningFromOfficial||session!=null&&!session.isCurrent())){
            returningFromOfficial=false;load(1,true);
        }
    }
    private void load(int requested,boolean explicitRefresh) {
        if(deleting||editing){swipe.setRefreshing(false);return;}
        final int request=++generation;
        try {
            if (session == null || !session.isCurrent()) {
                if (session != null) {
                    closeDialogs();
                    keyword = "";
                    searchInput.setText("");
                    hideSearch();
                }
                items.clear();adapter.notifyDataSetChanged();page=1;hasMore=false;uncertain.clear();uncertainTitles.clear();
                requested = 1;
                session=null;session=CreatorArchiveApi.capture();
            }
        }catch(Failure error){busy=false;status.setText(error.getMessage());empty.setText(error.getMessage()+"\n登录后点击重试");empty.setVisibility(View.VISIBLE);swipe.setRefreshing(false);buttons();return;}
        final Session account = session;
        final int type = selection;
        final int requestedPage = requested;
        final String query = keyword;
        busy = true;
        swipe.setRefreshing(true);
        buttons();
        status.setText(query.isEmpty() ? "正在加载本人稿件…" : getString(R.string.creator_search_loading));
        empty.setVisibility(View.GONE);
        CenterThreadPool.run(()->{
            try{
                Page result = CreatorArchiveApi.archives(account, requestedPage, FILTERS[type], query);
                runOnUiThread(()->{
                    if(!accepts(request,account))return;
                    items.clear();items.addAll(result.items);page=result.number;hasMore=result.hasMore;
                    if(explicitRefresh){uncertain.clear();uncertainTitles.clear();}
                    adapter.notifyDataSetChanged();list.scrollToPosition(0);busy=false;swipe.setRefreshing(false);buttons();
                    if (query.isEmpty()) {
                        status.setText("共 " + result.total + " 个稿件 · 第 " + page + " 页");
                        empty.setText("暂无" + LABELS[type] + "\n下拉或点击刷新");
                    } else {
                        String summary = CreatorArchiveApi.isArchiveId(query)
                                ? (result.hasMore ? getString(R.string.creator_search_progress, page)
                                                  : getString(R.string.creator_search_done))
                                : getString(R.string.creator_search_page, result.total, page);
                        status.setText(summary + "\n" + query);
                        empty.setText(result.hasMore ? R.string.creator_search_more : R.string.creator_search_empty);
                    }
                    empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
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
            deleting=false;editing=false;busy=false;
            if(deleteDialog!=null)deleteDialog.dismiss();
            load(1,true);return false;
        }
        return true;
    }
    private void buttons() {
        boolean idLookup = CreatorArchiveApi.isArchiveId(keyword);
        previous.setEnabled(!idLookup && !busy && !deleting && !editing && page > 1);
        next.setEnabled(!busy && !deleting && !editing && hasMore);
        next.setText(idLookup ? R.string.creator_search_continue : R.string.creator_next_page);
    }
    private static String count(long number){return number<0?"—":StringUtil.toWan(number);}
    private void details(Archive item) {
        if (!isCurrentItem(item)) return;
        String av = "av" + item.aid;
        String number = item.bvid.isEmpty() ? av : item.bvid;
        String ids = item.bvid.isEmpty() ? av : item.bvid + "\n" + av;
        String metrics = "\n播放 " + count(item.views) + " · 点赞 " + count(item.likes)
                + "\n评论 " + count(item.replies) + " · 收藏 " + count(item.favorites);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.creator_archive_details)
                .setMessage(item.title + "\n" + item.stateText + "\n" + ids + metrics)
                .setNeutralButton(R.string.creator_archive_copy_number, (dialog, which) -> {
                    if (!isCurrentItem(item)) return;
                    StringUtil.copyText(this, number);
                    MsgUtil.showMsg(getString(R.string.creator_archive_number_copied));
                });
        if (item.state == 0) {
            builder.setNegativeButton(R.string.creator_archive_open_video, (dialog, which) -> openVideo(item, false));
            builder.setPositiveButton(R.string.creator_archive_open_comments, (dialog, which) -> openVideo(item, true));
        } else {
            builder.setPositiveButton(android.R.string.ok, null);
        }
        show(builder.create());
    }

    private boolean isCurrentItem(Archive item) {
        if (session != null && session.isCurrent() && item.accountMid == session.mid) return true;
        load(1, true);
        return false;
    }

    private void menu(Archive item){
        if (!isCurrentItem(item)) return;
        ArrayList<String> actions = new ArrayList<>();
        ArrayList<Runnable> handlers = new ArrayList<>();
        actions.add(getString(R.string.creator_archive_details));
        handlers.add(() -> details(item));
        if (item.state == 0) {
            actions.add(getString(R.string.creator_archive_open_video));
            handlers.add(() -> openVideo(item, false));
        }
        actions.add("官方稿件管理");
        handlers.add(() -> official(item));
        if (item.ownerMid <= 0 || item.ownerMid == session.mid) {
            if(item.state==0){
                actions.add(getString(R.string.creator_title_edit));
                handlers.add(() -> prepareTitleEdit(item));
            }
            actions.add("删除本人稿件");
            handlers.add(() -> prepareDelete(item));
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(item.title)
                .setItems(actions.toArray(new String[0]), (d, which) -> handlers.get(which).run())
                .setNegativeButton(android.R.string.cancel, null).create();
        show(dialog);
    }

    private void openVideo(Archive item, boolean comments) {
        if (!isCurrentItem(item) || item.state != 0) return;
        startActivity(new Intent(this, VideoInfoActivity.class)
                .putExtra("aid", item.aid)
                .putExtra(VideoInfoActivity.EXTRA_OPEN_COMMENTS, comments));
    }
    private void prepareDelete(Archive item){
        if(busy||deleting||editing||deleteDialog!=null&&deleteDialog.isShowing()){MsgUtil.showMsg("正在处理，请稍候");return;}
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

    private void prepareTitleEdit(Archive item) {
        if(busy||deleting||editing||titleDialog!=null&&titleDialog.isShowing())return;
        if(uncertainTitles.contains(item.aid)){MsgUtil.showMsgLong("上次修改结果待确认，请先刷新稿件列表");return;}
        if(!isCurrentItem(item))return;
        final Session account=session;final int request=generation;
        editing=true;buttons();MsgUtil.showMsg("正在获取当前稿件信息…");
        CenterThreadPool.run(()->{
            try {
                TitleEdit edit=CreatorArchiveApi.prepareTitleEdit(account,item);
                runOnUiThread(()->{
                    if(!accepts(request,account))return;
                    editing=false;buttons();editTitleDialog(account,item,edit,edit.title);
                });
            }catch(Exception error){runOnUiThread(()->{
                if(!accepts(request,account))return;
                editing=false;buttons();message("无法修改标题",CreatorArchiveApi.message(error),item);
            });}
        });
    }

    private void editTitleDialog(Session account,Archive item,TitleEdit edit,String draft) {
        View body=LayoutInflater.from(this).inflate(R.layout.dialog_creator_title,null);
        EditText input=body.findViewById(R.id.creator_title_input);
        input.setText(draft);input.setSelection(input.length());
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.creator_title_edit)
                .setView(body).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton("下一步",null).create();
        titleDialog=dialog;show(dialog);
        if(dialog.getWindow()!=null){
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            if(!account.isCurrent()){dialog.dismiss();load(1,true);return;}
            final String title;
            try {
                title=CreatorArchiveApi.validateTitle(input.getText().toString());
                if(title.equals(edit.title))throw new Failure(-10005,"标题没有变化");
            }catch(Failure error){input.setError(error.getMessage());input.requestFocus();return;}
            InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);
            dialog.dismiss();confirmTitleEdit(account,item,edit,title);
        });
        input.requestFocus();
        input.post(()->{
            if(!dialog.isShowing())return;
            InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void confirmTitleEdit(Session account,Archive item,TitleEdit edit,String title) {
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("确认修改标题")
                .setMessage("原题："+edit.title+"\n\n新题："+title+"\n\n"+(edit.bvid.isEmpty()?"av"+edit.aid:edit.bvid)
                        +"\n发布账号：UID "+account.mid+"\n\n修改后可能需要 B 站重新审核。")
                .setNegativeButton("返回编辑",(d,w)->{
                    if(account.isCurrent())editTitleDialog(account,item,edit,title);
                }).setPositiveButton("确认提交",null).create();
        titleDialog=dialog;show(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            if(editing||deleting||busy)return;
            if(!account.isCurrent()){dialog.dismiss();load(1,true);return;}
            editing=true;buttons();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.dismiss();
            CenterThreadPool.run(()->{
                try {
                    boolean confirmed=CreatorArchiveApi.editTitle(account,edit,title);
                    runOnUiThread(()->{
                        if(isDestroyed())return;editing=false;buttons();
                        if(!account.isCurrent()){MsgUtil.showMsgLong("账号已变化，请回原账号核对标题修改结果");load(1,true);return;}
                        MsgUtil.showMsgLong(confirmed?"标题已修改":"修改已提交，等待 B 站审核或更新，请稍后刷新核对");
                        load(page,true);
                    });
                }catch(Exception error){runOnUiThread(()->{
                    if(isDestroyed())return;editing=false;buttons();
                    if(!account.isCurrent()){MsgUtil.showMsgLong("账号已变化，请回原账号核对修改结果");load(1,true);return;}
                    if(error instanceof Failure&&((Failure)error).code!=-10002)message("未完成标题修改",error.getMessage(),item);
                    else {uncertainTitles.add(item.aid);message("修改结果待确认","无法确认本次提交结果，请先刷新稿件列表核对，暂勿重复提交。",item);}
                });}
            });
        });
    }
    private void official(Archive item){
        if (!isCurrentItem(item)) return;
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
    private void show(AlertDialog dialog) {
        dialogs.add(dialog);
        dialog.setOnDismissListener(ignored -> dialogs.remove(dialog));
        MsgUtil.prepareAlertDialog(dialog);
        dialog.show();
    }

    private void closeDialogs() {
        for (AlertDialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
        dialogs.clear();
        deleteDialog = null;
        titleDialog = null;
    }

    @Override protected void onDestroy() {
        generation++;
        closeDialogs();
        super.onDestroy();
    }

    private final class ArchiveAdapter extends RecyclerView.Adapter<ArchiveHolder>{
        @NonNull @Override public ArchiveHolder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new ArchiveHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_creator_archive,parent,false));}
        @Override public int getItemCount(){return items.size();}
        @Override public void onBindViewHolder(@NonNull ArchiveHolder holder,int position){
            Archive item=items.get(position);holder.title.setText(item.title);holder.state.setText(item.stateText+(uncertain.contains(item.aid)?" · 删除结果待确认":""));
            holder.metrics.setText("播放 "+count(item.views)+" · 点赞 "+count(item.likes)+" · 评论 "+count(item.replies));
            holder.delete.setVisibility(item.ownerMid>0&&session!=null&&item.ownerMid!=session.mid?View.GONE:View.VISIBLE);
            holder.delete.setEnabled(!deleting&&!editing);
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
