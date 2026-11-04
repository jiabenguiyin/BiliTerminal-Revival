package com.RobinNotBad.BiliClient.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import androidx.appcompat.app.AlertDialog;
import com.RobinNotBad.BiliClient.api.UserRelationApi;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.FollowRelationUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Shared profile/author actions. Opening a menu, cancelling or reading never posts. */
public final class UserRelationActions {
    private static final WeakHashMap<Activity,AlertDialog> OPEN=new WeakHashMap<>();
    private static final Set<String> PENDING=new HashSet<>();
    private UserRelationActions() {}
    public static boolean show(Context context,UserInfo user,Runnable changed) {
        Controller action=create(context,user,changed);
        if(action!=null)action.loadMenu();
        return true;
    }
    public static void toggleFollow(Context context,UserInfo user,Runnable changed) {
        Controller action=create(context,user,changed);
        if(action==null)return;
        if(FollowRelationUtil.isBlockedAttribute(user.relationAttribute)){action.loadMenu();return;}
        if(FollowRelationUtil.isFollowingAttribute(user.relationAttribute))action.confirm(FollowRelationUtil.getUnfollowAction(user.relationAttribute));
        else action.submit(1);
    }
    private static Controller create(Context context,UserInfo user,Runnable changed) {
        Activity owner=null;
        while(context instanceof ContextWrapper){
            if(context instanceof Activity){owner=(Activity)context;break;}
            Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;
        }
        if(owner==null||owner.isFinishing()||user==null||user.mid<=0)return null;
        AlertDialog existing=OPEN.get(owner);if(existing!=null&&existing.isShowing())return null;
        try {
            UserRelationApi.Session session=UserRelationApi.capture();
            if(user.mid==session.mid){MsgUtil.showMsg("不能对自己执行此操作");return null;}
            Controller result=new Controller(owner,user,changed,session);
            synchronized(PENDING){if(PENDING.contains(result.key)){MsgUtil.showMsg("正在处理，请稍候");return null;}}
            return result;
        }catch(Exception error){MsgUtil.showMsg(error.getMessage());return null;}
    }
    private static final class Controller {
        final Activity owner;final UserInfo user;final Runnable changed;final UserRelationApi.Session session;
        final long target;final String key;
        Controller(Activity owner,UserInfo user,Runnable changed,UserRelationApi.Session session){
            this.owner=owner;this.user=user;this.changed=changed;this.session=session;target=user.mid;key=session.mid+":"+target;
        }
        boolean current(){return !owner.isFinishing()&&(Build.VERSION.SDK_INT<17||!owner.isDestroyed())&&session.isCurrent()&&user.mid==target;}
        void display(AlertDialog dialog){
            TerminalDialogStyle.prepare(dialog);
            androidx.lifecycle.Lifecycle lifecycle=owner instanceof androidx.lifecycle.LifecycleOwner
                    ?((androidx.lifecycle.LifecycleOwner)owner).getLifecycle():null;
            androidx.lifecycle.LifecycleEventObserver cleanup=(source,event)->{
                if(event==androidx.lifecycle.Lifecycle.Event.ON_DESTROY)dialog.dismiss();
            };
            if(lifecycle!=null)lifecycle.addObserver(cleanup);
            dialog.setOnDismissListener(ignored->{
                if(OPEN.get(owner)==dialog)OPEN.remove(owner);
                if(lifecycle!=null)lifecycle.removeObserver(cleanup);
            });
            OPEN.put(owner,dialog);dialog.show();
        }
        void loadMenu(){
            AlertDialog loading=new AlertDialog.Builder(owner).setTitle("用户操作").setMessage("正在获取用户关系…").setNegativeButton("取消",null).create();
            display(loading);
            CenterThreadPool.run(()->{
                try {
                    int attribute=UserRelationApi.getAttribute(session,target);
                    owner.runOnUiThread(()->{
                        if(!current()||!loading.isShowing()){loading.dismiss();return;}
                        user.relationAttribute=attribute;user.followed=FollowRelationUtil.isFollowingAttribute(attribute);
                        if(changed!=null)changed.run();loading.dismiss();menu();
                    });
                }catch(Exception error){owner.runOnUiThread(()->{boolean visible=loading.isShowing();loading.dismiss();if(current()&&visible)MsgUtil.err(error);});}
            });
        }
        void menu(){
            if(!current())return;
            final boolean blocked=FollowRelationUtil.isBlockedAttribute(user.relationAttribute);
            final boolean following=FollowRelationUtil.isFollowingAttribute(user.relationAttribute);
            String[] labels=blocked?new String[]{"移出黑名单"}:new String[]{following?"取消关注":"关注","拉黑"};
            display(new AlertDialog.Builder(owner).setTitle(user.name).setItems(labels,(dialog,which)->{
                if(!current())return;
                int action=blocked?6:which==1?5:following?FollowRelationUtil.getUnfollowAction(user.relationAttribute):1;
                if(action==1)submit(action);else confirm(action);
            }).setNegativeButton("取消",null).create());
        }
        void confirm(int action){
            if(!current())return;
            String title=action==5?"拉黑用户":action==6?"移出黑名单":"取消关注";
            String label=action==5?"拉黑":action==6?"移出":"取消关注";
            String message=action==5?"确定拉黑「"+user.name+"」吗？拉黑可能同时解除关注关系。"
                    :action==6?"将「"+user.name+"」移出黑名单？不会自动重新关注。":"确定取消关注「"+user.name+"」吗？";
            java.util.concurrent.atomic.AtomicBoolean submitted=new java.util.concurrent.atomic.AtomicBoolean();
            display(new AlertDialog.Builder(owner).setTitle(title).setMessage(message).setNegativeButton("取消",null)
                    .setPositiveButton(label,(dialog,which)->{if(submitted.compareAndSet(false,true))submit(action);}).create());
        }
        void submit(int action){
            if(!current())return;
            synchronized(PENDING){if(!PENDING.add(key))return;}
            MsgUtil.showMsg("正在处理…");
            CenterThreadPool.run(()->{
                try {
                    int code=UserRelationApi.modify(session,target,action);
                    if(code!=0){owner.runOnUiThread(()->{if(current())MsgUtil.showMsg("操作失败（"+code+"）");});return;}
                    int expected=action==5?128:action==1?(user.followsMe?6:2):0;
                    try{
                        int actual=UserRelationApi.getAttribute(session,target);
                        boolean matching=action==5?FollowRelationUtil.isBlockedAttribute(actual)
                                :action==1?FollowRelationUtil.isFollowingAttribute(actual)
                                :!FollowRelationUtil.isFollowingAttribute(actual)&&!FollowRelationUtil.isBlockedAttribute(actual);
                        if(matching)expected=actual;
                    }catch(Exception ignored){}
                    final int attribute=expected;
                    owner.runOnUiThread(()->{
                        if(!current())return;
                        user.relationAttribute=attribute;user.followed=FollowRelationUtil.isFollowingAttribute(attribute);
                        if(action==5){user.beRelationAttribute=0;user.followsMe=false;}
                        if(changed!=null)changed.run();
                        MsgUtil.showMsg(action==5?"已拉黑":action==6?"已移出黑名单":action==1?"关注成功":"已取消关注");
                    });
                }catch(Exception error){owner.runOnUiThread(()->{if(current())MsgUtil.err(error);});}
                finally{synchronized(PENDING){PENDING.remove(key);}}
            });
        }
    }
}
