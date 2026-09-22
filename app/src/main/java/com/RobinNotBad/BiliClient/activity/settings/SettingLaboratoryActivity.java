package com.RobinNotBad.BiliClient.activity.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.adapter.SettingsAdapter;
import com.RobinNotBad.BiliClient.model.SettingSection;
import com.RobinNotBad.BiliClient.service.DownloadService;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SettingLaboratoryActivity extends RefreshListActivity {

    private static final int REQUEST_VIDEO_TREE = 3101;

    @SuppressLint({"MissingInflatedId", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageName("实验室");

        boolean debugBuild = BiliTerminal.isDebugBuild();

        final List<SettingSection> sectionList = new ArrayList<>() {
            {
                add(new SettingSection("title", "可用性", "", "", ""));
                add(new SettingSection("switch", "新版弹幕获取方式", "new_danmaku_api",
                        getString(R.string.desc_new_danmaku_api), "true"));
                add(new SettingSection("switch", "私信未读标记", SharedPreferencesUtil.PRIVATE_MSG_UNREAD_BADGE_ENABLE,
                        getString(R.string.desc_private_msg_unread_badge_enable), "false"));

                add(new SettingSection("title", "下载", "", "", ""));
                add(new SettingSection("switch", "使用旧版下载器", "dev_download_old",
                        getString(R.string.setting_lab_download_old), "false"));
                add(new SettingSection("action", "缓存存储位置", "",
                        "当前：" + VideoStorageUtil.describeCurrent(SettingLaboratoryActivity.this), "",
                        (android.view.View.OnClickListener) view -> showStorageLocationDialog()));
                add(new SettingSection("input_string", "图片下载路径", "save_path_pictures",
                        getString(R.string.setting_lab_path_pictures), FileUtil.getPicturePath().toString()));

                add(new SettingSection("title", "UI", "", "", ""));
                add(new SettingSection("switch", "横屏模式", "ui_landscape", getString(R.string.setting_lab_ui_landscape),
                        "false"));
                add(new SettingSection("input_string", "开屏文字", "ui_splashtext",
                        getString(R.string.setting_lab_splashtext), "复活版\n连接中"));
                add(new SettingSection("switch", "文字跑马灯", "marquee_enable", getString(R.string.setting_lab_marquee),
                        "true"));

                add(new SettingSection("title", "播放器", "", "", ""));
                add(new SettingSection("switch", "播放器旋屏兼容方案", "dev_player_rotate_software",
                        "在极少数手表上（如小米手表），系统旋屏存在显示不全的问题。打开此开关，播放器将会使用软件旋屏方法。", "false"));
                add(new SettingSection("switch", "显示视频分段", "player_show_viewpoints",
                        "显示视频的章节看点信息，可快速跳转到指定章节", "false"));
                add(new SettingSection("switch", "系统媒体控件", SharedPreferencesUtil.PLAYER_MEDIA_SESSION_ENABLE,
                        getString(R.string.setting_lab_media_session), "false"));
                add(new SettingSection("switch", "互动视频调试", "player_interaction_debug",
                        "在互动视频播放时，在左侧倍速按钮上方显示调试按钮，可以查看和修改互动视频的变量", "false"));

                add(new SettingSection("title", "调试", "", "", ""));
                add(new SettingSection("switch", "允许Logu.v", "dev_logv", getString(R.string.setting_lab_logv),
                        String.valueOf(debugBuild)));
                add(new SettingSection("switch", "允许Logu.d", "dev_logd", "", String.valueOf(debugBuild)));
                add(new SettingSection("switch", "允许Logu.i", "dev_logi", "", String.valueOf(debugBuild)));
                add(new SettingSection("switch", "详细显示数据解析报错", "dev_jsonerr_detailed",
                        getString(R.string.setting_lab_jsonerr_detailed), String.valueOf(debugBuild)));
                add(new SettingSection("switch", "详细显示列表报错", "dev_recyclererr_detailed",
                        getString(R.string.setting_lab_recyclererr_detailed), String.valueOf(debugBuild)));
            }
        };

        recyclerView.setHasFixedSize(true);
        SettingsAdapter adapter = new SettingsAdapter(this, sectionList);
        setAdapter(adapter);
        setRefreshing(false);
    }

    private void showStorageLocationDialog() {
        if (DownloadService.started) {
            MsgUtil.showMsg("请先暂停正在进行的下载任务");
            return;
        }
        List<FileUtil.StorageLocation> locations = FileUtil.getVideoStorageLocations(this);
        int extra = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? 1 : 0;
        String[] labels = new String[locations.size() + extra];
        String currentMode = VideoStorageUtil.getCurrentMode();
        String currentRef = VideoStorageUtil.getCurrentReference();
        for (int i = 0; i < locations.size(); i++) {
            FileUtil.StorageLocation location = locations.get(i);
            boolean current = VideoStorageUtil.MODE_FILE.equals(currentMode)
                    && currentRef.equals(location.path.getAbsolutePath());
            labels[i] = location.label + (current ? "（当前）" : "") + "\n" + location.path;
        }
        if (extra == 1) {
            labels[labels.length - 1] = "选择其他文件夹（SAF）"
                    + (VideoStorageUtil.MODE_SAF.equals(currentMode) ? "（当前）" : "")
                    + "\n可授权外置 SD 卡中的已有缓存目录";
        }
        AlertDialog storageDialog = new AlertDialog.Builder(this)
                .setTitle("选择缓存存储位置")
                .setItems(labels, (dialog, which) -> {
                    if (which < locations.size()) confirmFileStorageLocation(locations.get(which));
                    else launchSafDirectoryPicker();
                })
                .setNegativeButton("取消", null)
                .create();
        MsgUtil.prepareAlertDialog(storageDialog);
        storageDialog.show();
    }

    private void confirmFileStorageLocation(FileUtil.StorageLocation location) {
        String message = "新的缓存会保存到：\n" + location.path
                + "\n\n已有缓存不会自动迁移。应用卸载时，该目录中的缓存可能被系统删除。";
        AlertDialog confirmDialog = new AlertDialog.Builder(this)
                .setTitle("切换缓存位置？")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认切换", (dialog, which) -> {
                    if (!FileUtil.prepareVideoStorageLocation(location.path)) {
                        MsgUtil.showMsg("该存储位置不可写");
                        return;
                    }
                    VideoStorageUtil.selectFileRoot(location.path);
                    MsgUtil.showMsg("缓存位置已切换");
                    recreate();
                })
                .create();
        MsgUtil.prepareAlertDialog(confirmDialog);
        confirmDialog.show();
    }

    private void launchSafDirectoryPicker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            MsgUtil.showMsg("Android 5.0 以下不支持 SAF 目录授权");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_VIDEO_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VIDEO_TREE || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri treeUri = data.getData();
        try {
            VideoStorageUtil.selectSafRoot(this, treeUri, data.getFlags());
            MsgUtil.showMsg("已获得目录读写权限");
            recreate();
        } catch (SecurityException | IOException e) {
            MsgUtil.showMsg("目录授权失败：" + e.getMessage());
        }
    }
}
