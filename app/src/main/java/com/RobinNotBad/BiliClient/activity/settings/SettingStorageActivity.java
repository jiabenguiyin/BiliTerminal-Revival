package com.RobinNotBad.BiliClient.activity.settings;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.Formatter;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity;
import com.RobinNotBad.BiliClient.util.AppStorageManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.bumptech.glide.Glide;

public class SettingStorageActivity extends BaseActivity {
    private TextView stats;
    private Button caches,apk,refresh;
    private boolean busy;
    private AppStorageManager.Snapshot snapshot;
    private AlertDialog confirmation;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);setContentView(R.layout.activity_setting_storage);setPageName("存储空间");
        stats=findViewById(R.id.storage_stats);caches=findViewById(R.id.storage_clear_cache);
        apk=findViewById(R.id.storage_clear_apk);refresh=findViewById(R.id.storage_refresh);
        caches.setOnClickListener(v->confirm(false));apk.setOnClickListener(v->confirm(true));
        refresh.setOnClickListener(v->inspect());
        findViewById(R.id.storage_manage_videos).setOnClickListener(v->startActivity(new Intent(this,LocalListActivity.class)));
        inspect();
    }
    @Override protected void onResume(){super.onResume();if(stats!=null&&!busy)inspect();}
    private String size(long bytes){return Formatter.formatFileSize(this,Math.max(0,bytes));}
    private boolean closed(){return isFinishing()||getLifecycle().getCurrentState()==androidx.lifecycle.Lifecycle.State.DESTROYED;}
    private void controls(boolean enabled){caches.setEnabled(enabled);refresh.setEnabled(enabled);apk.setEnabled(enabled&&snapshot!=null&&snapshot.apk>0);}
    private void render() {
        stats.setText("设备可用 "+size(snapshot.available)+" / "+size(snapshot.total)
                +"\n\n图片缓存："+size(snapshot.images)+"\n可清理临时文件："+size(snapshot.temporary)+"\n更新安装包："+size(snapshot.apk));
    }
    private void inspect() {
        if(busy)return;busy=true;controls(false);stats.setText("正在计算存储占用…");
        CenterThreadPool.run(()->{
            AppStorageManager.Snapshot result=AppStorageManager.inspect(getApplicationContext());
            runOnUiThread(()->{if(closed())return;snapshot=result;busy=false;render();controls(true);});
        });
    }
    private void confirm(boolean updatePackage) {
        if(busy||confirmation!=null&&confirmation.isShowing())return;
        final String expectedPackage=snapshot==null?"":snapshot.apkPath;
        confirmation=new AlertDialog.Builder(this).setTitle(updatePackage?"清理更新安装包":"清理程序缓存")
                .setMessage(updatePackage?"删除最近下载的更新安装包？不会卸载当前应用，需要更新时可重新下载。"
                        :"清理图片缓存及24小时前的临时文件？\n已缓存的视频、登录账号和记录会保留。")
                .setNegativeButton("取消",null).setPositiveButton("清理",(d,w)->clear(updatePackage,expectedPackage)).create();
        MsgUtil.prepareAlertDialog(confirmation);confirmation.show();
    }
    private void clear(boolean updatePackage,String expectedPackage) {
        if(busy)return;busy=true;controls(false);
        if(!updatePackage)Glide.get(this).clearMemory();
        CenterThreadPool.run(()->{
            AppStorageManager.Cleanup result=updatePackage?AppStorageManager.clearUpdatePackage(getApplicationContext(),expectedPackage)
                    :AppStorageManager.clearCaches(getApplicationContext());
            AppStorageManager.Snapshot after=AppStorageManager.inspect(getApplicationContext());
            runOnUiThread(()->{
                if(closed())return;snapshot=after;busy=false;render();controls(true);
                MsgUtil.showMsg(result.changed?"更新安装包已变化，请刷新后再清理":result.busy?"正在清理，请稍候":result.failures>0
                        ?(result.bytes>0?"已释放 "+size(result.bytes)+"，部分文件未能清理":"暂时无法清理，请稍后重试")
                        :"清理完成，释放 "+size(result.bytes));
            });
        });
    }
    @Override protected void onDestroy(){if(confirmation!=null)confirmation.dismiss();super.onDestroy();}
}
