package com.RobinNotBad.BiliClient.activity.video.local;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.adapter.video.LocalVideoAdapter;
import com.RobinNotBad.BiliClient.model.LocalVideo;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;

import java.util.ArrayList;

public class LocalListActivity extends InstanceActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private final ArrayList<LocalVideo> videoList = new ArrayList<>(10);
    private LocalVideoAdapter adapter;
    private TextView emptyTip;
    private int longClickPosition = -1;
    private long longClickTimestamp;
    private boolean started;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_main_refresh);
        setMenuClick();

        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::refresh);
        emptyTip = findViewById(R.id.emptyTip);
        TextView pageName = findViewById(R.id.pageName);
        pageName.setText("缓存");

        if (!FileUtil.checkStoragePermission()) FileUtil.requestStoragePermission(this);

        CenterThreadPool.run(() -> {
            runOnUiThread(() -> swipeRefreshLayout.setRefreshing(true));
            scanCurrentStorage();
            adapter = new LocalVideoAdapter(this, videoList);
            adapter.setOnLongClickListener(position -> {
                long timestamp = System.currentTimeMillis();
                if (longClickPosition == position && timestamp - longClickTimestamp < 4000) {
                    LocalVideo item = videoList.get(position);
                    CenterThreadPool.run(() -> VideoStorageUtil.deleteLocator(this, item.folderLocator));
                    MsgUtil.showMsg("删除成功");
                    videoList.remove(position);
                    adapter.notifyItemRemoved(position + 1);
                    adapter.notifyItemRangeChanged(position + 1, videoList.size() - position);
                    longClickPosition = -1;
                    checkEmpty();
                } else {
                    longClickPosition = position;
                    longClickTimestamp = timestamp;
                    MsgUtil.showMsg("再次长按删除");
                }
            });
            runOnUiThread(() -> {
                recyclerView.setLayoutManager(getLayoutManager());
                recyclerView.setAdapter(adapter);
                swipeRefreshLayout.setRefreshing(false);
                started = true;
            });
        });
    }

    private void scanCurrentStorage() {
        try {
            VideoStorageUtil.Node root = VideoStorageUtil.currentRoot(this);
            VideoStorageUtil.ensureNoMedia(this, root);
            scan(root);
        } catch (Exception e) {
            MsgUtil.showMsg("缓存目录不可访问，请重新授权");
        }
    }

    private void scan(VideoStorageUtil.Node folder) {
        for (VideoStorageUtil.Node video : folder.listChildren()) {
            if (!video.isDirectory()) continue;
            LocalVideo localVideo = new LocalVideo();
            localVideo.title = video.getName();
            localVideo.folderLocator = video.getLocator();
            VideoStorageUtil.Node progressFile = video.find(".playback_progress");
            localVideo.progressFileLocator = progressFile == null ? "" : progressFile.getLocator();
            VideoStorageUtil.Node cover = video.find("cover.png");
            localVideo.cover = cover == null ? "" : cover.getUriString();
            localVideo.pageList = new ArrayList<>();
            localVideo.pageFolderLocators = new ArrayList<>();
            localVideo.danmakuFileList = new ArrayList<>();
            localVideo.videoFileList = new ArrayList<>();
            localVideo.subtitleFolderLocators = new ArrayList<>();
            localVideo.audioOnlyList = new ArrayList<>();
            localVideo.sizeList = new ArrayList<>();

            VideoStorageUtil.Node media = firstMedia(video);
            if (media != null) {
                if (video.find(".DOWNLOADING") != null) continue;
                localVideo.sizeList.add(media.length());
                localVideo.videoFileList.add(media.getUriString());
                localVideo.audioOnlyList.add("audio.m4a".equals(media.getName()));
                localVideo.pageFolderLocators.add(video.getLocator());
                addOptionalRefs(video, localVideo);
                localVideo.calcTotalSize();
                videoList.add(localVideo);
                continue;
            }

            for (VideoStorageUtil.Node page : video.listChildren()) {
                if (!page.isDirectory() || page.find(".DOWNLOADING") != null) continue;
                VideoStorageUtil.Node pageMedia = firstMedia(page);
                if (pageMedia == null) continue;
                localVideo.pageList.add(page.getName());
                localVideo.pageFolderLocators.add(page.getLocator());
                localVideo.sizeList.add(pageMedia.length());
                localVideo.videoFileList.add(pageMedia.getUriString());
                localVideo.audioOnlyList.add("audio.m4a".equals(pageMedia.getName()));
                addOptionalRefs(page, localVideo);
            }
            localVideo.calcTotalSize();
            if (!localVideo.videoFileList.isEmpty()) videoList.add(localVideo);
        }
        checkEmpty();
    }

    private static VideoStorageUtil.Node firstMedia(VideoStorageUtil.Node folder) {
        VideoStorageUtil.Node video = folder.find("video.mp4");
        return video != null ? video : folder.find("audio.m4a");
    }

    private static void addOptionalRefs(VideoStorageUtil.Node folder, LocalVideo localVideo) {
        VideoStorageUtil.Node danmaku = folder.find("danmaku.xml");
        VideoStorageUtil.Node subtitles = folder.find("subtitles");
        localVideo.danmakuFileList.add(danmaku == null ? "" : danmaku.getUriString());
        localVideo.subtitleFolderLocators.add(subtitles == null ? "" : subtitles.getLocator());
    }

    private void checkEmpty() {
        runOnUiThread(() -> emptyTip.setVisibility(videoList.isEmpty() ? View.VISIBLE : View.GONE));
    }

    public void refresh() {
        if (!started) return;
        CenterThreadPool.run(() -> {
            runOnUiThread(() -> swipeRefreshLayout.setRefreshing(true));
            int oldSize = videoList.size();
            videoList.clear();
            scanCurrentStorage();
            runOnUiThread(() -> {
                adapter.notifyItemRangeRemoved(1, oldSize);
                adapter.notifyItemRangeInserted(1, videoList.size());
                swipeRefreshLayout.setRefreshing(false);
            });
        });
    }
}
