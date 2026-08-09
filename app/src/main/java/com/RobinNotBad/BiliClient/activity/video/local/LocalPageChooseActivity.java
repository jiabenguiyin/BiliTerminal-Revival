package com.RobinNotBad.BiliClient.activity.video.local;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.adapter.video.PageChooseAdapter;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;

import java.util.ArrayList;

public class LocalPageChooseActivity extends BaseActivity {

    private int longClickPosition = -1;
    private long longClickTimestamp;
    private boolean deleted = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        findViewById(R.id.top).setOnClickListener(view -> finish());
        TextView textView = findViewById(R.id.pageName);
        textView.setText("请选择分页");

        Intent intent = getIntent();
        ArrayList<String> pageList = intent.getStringArrayListExtra("pageList");
        ArrayList<String> videoFileList = intent.getStringArrayListExtra("videoFileList");
        ArrayList<String> danmakuFileList = intent.getStringArrayListExtra("danmakuFileList");
        ArrayList<String> subtitleFolderLocators = intent.getStringArrayListExtra("subtitleFolderLocators");
        ArrayList<String> pageFolderLocators = intent.getStringArrayListExtra("pageFolderLocators");
        @SuppressWarnings("unchecked")
        ArrayList<Boolean> audioOnlyList = (ArrayList<Boolean>) intent.getSerializableExtra("audioOnlyList");
        String folderLocator = intent.getStringExtra("folderLocator");

        PageChooseAdapter adapter = new PageChooseAdapter(this, pageList);
        adapter.setOnItemClickListener(position -> {
            if (!valid(position, pageList, videoFileList, danmakuFileList)) {
                MsgUtil.showMsg("分页数据已变更，请重新进入");
                return;
            }
            PlayerData playerData = new PlayerData(PlayerData.TYPE_LOCAL);
            String mediaPath = videoFileList.get(position);
            playerData.videoUrl = mediaPath;
            playerData.danmakuUrl = danmakuFileList.get(position);
            playerData.title = pageList.get(position);
            try {
                Intent player = PlayerApi.jumpToPlayer(playerData);
                if (audioOnlyList != null && position < audioOnlyList.size()
                        && Boolean.TRUE.equals(audioOnlyList.get(position))) {
                    player.putExtra("audio_only", true);
                }
                if (subtitleFolderLocators != null && position < subtitleFolderLocators.size()) {
                    player.putExtra("local_subtitle_root", subtitleFolderLocators.get(position));
                }
                startActivity(player);
            } catch (ActivityNotFoundException e) {
                MsgUtil.showMsg("没有找到播放器，请检查是否安装");
            } catch (Exception e) {
                MsgUtil.err(e);
            }
        });

        adapter.setOnItemLongClickListener(position -> {
            if (!valid(position, pageList, videoFileList, danmakuFileList)) return;
            long timestamp = System.currentTimeMillis();
            if (longClickPosition == position && timestamp - longClickTimestamp < 4000) {
                String pageLocator = pageFolderLocators != null && position < pageFolderLocators.size()
                        ? pageFolderLocators.remove(position) : null;
                pageList.remove(position);
                videoFileList.remove(position);
                danmakuFileList.remove(position);
                if (subtitleFolderLocators != null && position < subtitleFolderLocators.size()) {
                    subtitleFolderLocators.remove(position);
                }
                if (audioOnlyList != null && position < audioOnlyList.size()) {
                    audioOnlyList.remove(position);
                }
                CenterThreadPool.run(() -> {
                    if (pageLocator != null) VideoStorageUtil.deleteLocator(this, pageLocator);
                    if (pageList.isEmpty() && folderLocator != null) {
                        VideoStorageUtil.deleteLocator(this, folderLocator);
                    }
                });
                adapter.notifyItemRemoved(position);
                adapter.notifyItemRangeChanged(position, pageList.size() - position);
                MsgUtil.showMsg("删除成功");
                longClickPosition = -1;
                deleted = true;
            } else {
                longClickPosition = position;
                longClickTimestamp = timestamp;
                MsgUtil.showMsg("再次长按删除");
            }
        });

        recyclerView.setLayoutManager(new CustomLinearManager(this));
        recyclerView.setAdapter(adapter);
    }

    private static boolean valid(int position, ArrayList<String> pages,
                                 ArrayList<String> videos, ArrayList<String> danmaku) {
        return pages != null && videos != null && danmaku != null && position >= 0
                && position < pages.size() && position < videos.size() && position < danmaku.size();
    }

    @Override
    protected void onDestroy() {
        InstanceActivity instance = BiliTerminal.getInstanceActivityOnTop();
        if (deleted && instance instanceof LocalListActivity && !instance.isDestroyed()) {
            ((LocalListActivity) instance).refresh();
        }
        super.onDestroy();
    }
}
