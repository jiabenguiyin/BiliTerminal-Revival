package com.RobinNotBad.BiliClient.activity.video.local;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
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
import java.util.Locale;

public class LocalListActivity extends InstanceActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private final ArrayList<LocalVideo> allVideoList = new ArrayList<>(10);
    private final ArrayList<LocalVideo> videoList = new ArrayList<>(10);
    private LocalVideoAdapter adapter;
    private TextView emptyTip;
    private SearchView cacheSearchView;
    private ImageButton searchButton;
    private String searchQuery = "";
    private LocalVideo longClickItem;
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
        emptyTip.setText("尚无已完成的缓存\n下载进度和失败原因请到“下载列表”查看\n点击打开下载列表");
        emptyTip.setOnClickListener(view -> {
            if (allVideoList.isEmpty()) startActivity(new Intent(this, DownloadListActivity.class));
        });
        TextView pageName = findViewById(R.id.pageName);
        pageName.setText("缓存");
        setupCacheSearch(pageName);

        if (!FileUtil.checkStoragePermission()) FileUtil.requestStoragePermission(this);

        CenterThreadPool.run(() -> {
            runOnUiThread(() -> swipeRefreshLayout.setRefreshing(true));
            ArrayList<LocalVideo> scanned = scanCurrentStorage();
            runOnUiThread(() -> {
                allVideoList.clear();
                allVideoList.addAll(scanned);
                applyFilter();
                adapter = new LocalVideoAdapter(this, videoList);
                adapter.setOnLongClickListener(this::onVideoLongClick);
                recyclerView.setLayoutManager(getLayoutManager());
                recyclerView.setAdapter(adapter);
                swipeRefreshLayout.setRefreshing(false);
                started = true;
            });
        });
    }

    private void setupCacheSearch(TextView pageName) {
        RelativeLayout top = findViewById(R.id.top);
        RelativeLayout root = (RelativeLayout) swipeRefreshLayout.getParent();

        searchButton = new ImageButton(this);
        searchButton.setId(R.id.cache_search_button);
        searchButton.setImageResource(android.R.drawable.ic_menu_search);
        searchButton.setColorFilter(Color.WHITE);
        searchButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        searchButton.setPadding(dp(6), dp(6), dp(6), dp(6));
        searchButton.setBackgroundResource(R.drawable.background_button_compat);
        searchButton.setContentDescription(getString(R.string.cache_search_open));
        RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams(dp(38), dp(38));
        boolean roundLayout = com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
                .getBoolean("player_ui_round", false);
        buttonParams.addRule(RelativeLayout.LEFT_OF, roundLayout ? R.id.pageName : R.id.timeText);
        buttonParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        buttonParams.rightMargin = dp(4);
        top.addView(searchButton, buttonParams);

        if (!roundLayout) {
            RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            titleParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            titleParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            titleParams.addRule(RelativeLayout.LEFT_OF, R.id.cache_search_button);
            titleParams.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
            titleParams.addRule(RelativeLayout.CENTER_IN_PARENT, 0);
            titleParams.leftMargin = dp(8);
            titleParams.topMargin = dp(3);
            titleParams.bottomMargin = dp(3);
            pageName.setLayoutParams(titleParams);
        }

        cacheSearchView = new SearchView(this);
        cacheSearchView.setId(R.id.cache_search_field);
        cacheSearchView.setIconifiedByDefault(false);
        cacheSearchView.setSubmitButtonEnabled(false);
        cacheSearchView.setQueryHint(getString(R.string.cache_search_hint));
        cacheSearchView.setBackgroundResource(R.drawable.background_searchbar);
        cacheSearchView.setVisibility(View.GONE);
        RelativeLayout.LayoutParams searchParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        searchParams.addRule(RelativeLayout.BELOW, R.id.top);
        root.addView(cacheSearchView, searchParams);

        cacheSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                setSearchQuery(query);
                hideKeyboard();
                return true;
            }
            @Override public boolean onQueryTextChange(String newText) {
                setSearchQuery(newText);
                return true;
            }
        });
        searchButton.setOnClickListener(view -> {
            if (cacheSearchView.getVisibility() == View.VISIBLE) {
                hideCacheSearch();
            } else {
                showCacheSearch();
            }
        });
    }

    private void showCacheSearch() {
        cacheSearchView.setVisibility(View.VISIBLE);
        updateContentAnchor();
        searchButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        searchButton.setContentDescription(getString(R.string.cache_search_close));
        cacheSearchView.setIconified(false);
        View input = cacheSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (input != null) {
            input.requestFocus();
            input.post(() -> ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT));
        }
    }

    private void hideCacheSearch() {
        hideKeyboard();
        cacheSearchView.setQuery("", false);
        cacheSearchView.clearFocus();
        cacheSearchView.setVisibility(View.GONE);
        searchQuery = "";
        applyFilter();
        updateContentAnchor();
        searchButton.setImageResource(android.R.drawable.ic_menu_search);
        searchButton.setContentDescription(getString(R.string.cache_search_open));
    }

    private void hideKeyboard() {
        View input = cacheSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (input != null) ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void updateContentAnchor() {
        int anchor = cacheSearchView.getVisibility() == View.VISIBLE
                ? R.id.cache_search_field : R.id.top;
        RelativeLayout.LayoutParams listParams = (RelativeLayout.LayoutParams) swipeRefreshLayout.getLayoutParams();
        listParams.addRule(RelativeLayout.BELOW, anchor);
        swipeRefreshLayout.setLayoutParams(listParams);
        RelativeLayout.LayoutParams emptyParams = (RelativeLayout.LayoutParams) emptyTip.getLayoutParams();
        emptyParams.addRule(RelativeLayout.BELOW, anchor);
        emptyTip.setLayoutParams(emptyParams);
    }

    private void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query.trim();
        applyFilter();
    }

    private void applyFilter() {
        videoList.clear();
        String needle = searchQuery.toLowerCase(Locale.ROOT);
        for (LocalVideo video : allVideoList) {
            String title = video.title == null ? "" : video.title;
            if (needle.isEmpty() || title.toLowerCase(Locale.ROOT).contains(needle)) videoList.add(video);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
        longClickItem = null;
    }

    private void updateEmptyState() {
        if (allVideoList.isEmpty()) {
            emptyTip.setText("尚无已完成的缓存\n下载进度和失败原因请到“下载列表”查看\n点击打开下载列表");
            emptyTip.setOnClickListener(view -> startActivity(new Intent(this, DownloadListActivity.class)));
            emptyTip.setClickable(true);
        } else if (videoList.isEmpty()) {
            emptyTip.setText(R.string.cache_search_no_results);
            emptyTip.setOnClickListener(null);
            emptyTip.setClickable(false);
        }
        emptyTip.setVisibility(videoList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void onVideoLongClick(int position) {
        if (position < 0 || position >= videoList.size()) return;
        LocalVideo item = videoList.get(position);
        long timestamp = System.currentTimeMillis();
        if (longClickItem == item && timestamp - longClickTimestamp < 4000) {
            longClickItem = null;
            CenterThreadPool.run(() -> {
                boolean deleted = VideoStorageUtil.deleteLocator(this, item.folderLocator);
                runOnUiThread(() -> {
                    if (!deleted) {
                        MsgUtil.showMsg("删除缓存失败");
                        return;
                    }
                    allVideoList.remove(item);
                    int visibleIndex = videoList.indexOf(item);
                    if (visibleIndex >= 0) {
                        videoList.remove(visibleIndex);
                        adapter.notifyItemRemoved(visibleIndex + 1);
                        adapter.notifyItemRangeChanged(visibleIndex + 1, videoList.size() - visibleIndex);
                    }
                    MsgUtil.showMsg("删除成功");
                    updateEmptyState();
                });
            });
        } else {
            longClickItem = item;
            longClickTimestamp = timestamp;
            MsgUtil.showMsg("再次长按删除");
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private ArrayList<LocalVideo> scanCurrentStorage() {
        ArrayList<LocalVideo> scanned = new ArrayList<>();
        try {
            VideoStorageUtil.Node root = VideoStorageUtil.currentRoot(this);
            VideoStorageUtil.ensureNoMedia(this, root);
            scan(root, scanned);
        } catch (Exception e) {
            runOnUiThread(() -> MsgUtil.showMsg("缓存目录不可访问，请重新授权"));
        }
        return scanned;
    }

    private void scan(VideoStorageUtil.Node folder, ArrayList<LocalVideo> scanned) {
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
                scanned.add(localVideo);
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
            if (!localVideo.videoFileList.isEmpty()) scanned.add(localVideo);
        }
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
        com.RobinNotBad.BiliClient.player.CachedPlaybackItem item = new com.RobinNotBad.BiliClient.player.CachedPlaybackItem();
        int index = localVideo.videoFileList.size() - 1;
        item.title = index < localVideo.pageList.size() ? localVideo.pageList.get(index) : localVideo.title;
        item.media = localVideo.videoFileList.get(index);
        item.cover = localVideo.cover;
        item.danmaku = localVideo.danmakuFileList.get(index);
        item.subtitles = localVideo.subtitleFolderLocators.get(index);
        item.folder = folder.getLocator();
        item.audioOnly = Boolean.TRUE.equals(localVideo.audioOnlyList.get(index));
        com.RobinNotBad.BiliClient.player.CachedCollectionMetadata.readInto(folder, item);
        localVideo.playbackItems.add(item);
    }

    public void refresh() {
        if (!started) return;
        CenterThreadPool.run(() -> {
            runOnUiThread(() -> swipeRefreshLayout.setRefreshing(true));
            ArrayList<LocalVideo> scanned = scanCurrentStorage();
            runOnUiThread(() -> {
                allVideoList.clear();
                allVideoList.addAll(scanned);
                applyFilter();
                swipeRefreshLayout.setRefreshing(false);
            });
        });
    }

    @Override
    public void onBackPressed() {
        if (cacheSearchView != null && cacheSearchView.getVisibility() == View.VISIBLE) {
            hideCacheSearch();
            return;
        }
        super.onBackPressed();
    }
}
