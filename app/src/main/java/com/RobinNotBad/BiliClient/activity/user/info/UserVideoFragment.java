package com.RobinNotBad.BiliClient.activity.user.info;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.activity.base.RefreshListFragment;
import com.RobinNotBad.BiliClient.adapter.video.UserVideoAdapter;
import com.RobinNotBad.BiliClient.api.UserInfoApi;
import com.RobinNotBad.BiliClient.api.FavoritePlaybackSource;
import com.RobinNotBad.BiliClient.api.UploaderPlaybackSource;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.player.PlayerOptionsMenu;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//用户视频
//2023-09-30
//2024-05-03

public class UserVideoFragment extends RefreshListFragment {

    private long mid;
    private ArrayList<VideoCard> videoList;
    private UserVideoAdapter adapter;
    private String searchKeyword = "";
    private volatile int searchGeneration;
    private int listSort;
    private boolean listLoading;
    private com.RobinNotBad.BiliClient.api.UserVideoListSource listSource;
    private PlayerOptionsMenu sortMenu;
    private volatile int playbackGeneration;
    private boolean playbackOpening;
    private PlayerOptionsMenu uploadOrderMenu;
    private UploaderPlaybackSource uploaderPlaybackSource = new UploaderPlaybackSource();

    public UserVideoFragment() {

    }

    public static UserVideoFragment newInstance(long mid) {
        UserVideoFragment fragment = new UserVideoFragment();
        Bundle args = new Bundle();
        args.putLong("mid", mid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) listSort = com.RobinNotBad.BiliClient.api.UserVideoListSource.normalize(savedInstanceState.getInt("upload_sort", 0));
        if (getArguments() != null) {
            mid = getArguments().getLong("mid");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        videoList = new ArrayList<>();
        adapter = new UserVideoAdapter(requireContext(), mid, videoList);
        adapter.setOnPlayUploads(this::showUploadPlaybackOrder);
        adapter.setOnChooseSort(this::showListSort);
        adapter.setSort(listSort);
        setAdapter(adapter);
        setOnLoadMoreListener(this::continueLoading);
        setOnRefreshListener(this::reloadVideos);
        reloadVideos();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("upload_sort", listSort);
    }

    public void search(String keyword) {
        searchKeyword = keyword == null ? "" : keyword.trim();
        reloadVideos();
    }

    private void showListSort() {
        if (!isAdded() || getView() == null) return;
        if (sortMenu != null) sortMenu.dismiss();
        sortMenu = new PlayerOptionsMenu(requireActivity(), recyclerView);
        for (int order = 0; order < 4; order++)
            sortMenu.getMenu().add(0, order + 100, order, UserVideoAdapter.sortLabel(order))
                    .setCheckable(true).setChecked(order == listSort);
        sortMenu.setOnMenuItemClickListener(item -> {
            int order = item.getItemId() - 100;
            if (order != listSort) {
                listSort = order;
                adapter.setSort(order);
                reloadVideos();
                recyclerView.scrollToPosition(0);
            }
            return true;
        });
        sortMenu.show();
    }

    private void reloadVideos() {
        searchGeneration++;
        if (videoList == null || !isAdded() || getView() == null) return;
        page = 1; bottom = false; listLoading = false;
        listSource = new com.RobinNotBad.BiliClient.api.UserVideoListSource(mid, listSort);
        videoList.clear(); adapter.notifyDataSetChanged();
        continueLoading(1);
    }

    private void continueLoading(int ignoredPage) {
        if (listLoading || bottom || listSource == null) return;
        final int generation = searchGeneration;
        final String query = searchKeyword.toLowerCase(Locale.ROOT);
        final com.RobinNotBad.BiliClient.api.UserVideoListSource source = listSource;
        listLoading = true; setRefreshing(true);
        CenterThreadPool.run(() -> {
            ArrayList<VideoCard> loaded = new ArrayList<>();
            boolean reachedEnd = false;
            Exception failure = null;
            FavoritePlaybackSource.RequestGuard guard = () -> {
                if (generation != searchGeneration) throw new java.io.IOException("投稿查询已取消");
            };
            try {
                // Search remains local because the official keyword filter is unreliable.
                // The selected server order is kept across the scan and later pagination.
                for (int scanned = 0; scanned < (query.isEmpty() ? 1 : 200); scanned++) {
                    guard.check();
                    com.RobinNotBad.BiliClient.api.UserVideoListSource.Page result = source.next(guard);
                    for (VideoCard card : result.videos)
                        if (query.isEmpty() || normalizeSearchTitle(card.title).toLowerCase(Locale.ROOT).contains(query)) loaded.add(card);
                    reachedEnd = result.bottom;
                    if (reachedEnd) break;
                }
            } catch (Exception error) { failure = error; }
            final boolean end = reachedEnd;
            final Exception error = failure;
            CenterThreadPool.runOnUiThread(() -> {
                if (!isAdded() || getView() == null || generation != searchGeneration) return;
                int insertAt = videoList.size() + 2;
                java.util.HashSet<Long> seen = new java.util.HashSet<>();
                for (VideoCard card : videoList) seen.add(card.aid);
                for (VideoCard card : loaded) if (seen.add(card.aid)) videoList.add(card);
                int inserted = videoList.size() + 2 - insertAt;
                if (inserted > 0) adapter.notifyItemRangeInserted(insertAt, inserted);
                bottom = end; listLoading = false; setRefreshing(false);
                if (error != null) MsgUtil.err(error);
            });
        });
    }

    @SuppressWarnings("deprecation")
    private String normalizeSearchTitle(String title) {
        return com.RobinNotBad.BiliClient.util.SearchQueryUtil.plainResultTitle(title == null ? "" : title);
    }

    private void showUploadPlaybackOrder() {
        if (!isAdded() || getView() == null || playbackOpening) return;
        if (uploadOrderMenu != null) uploadOrderMenu.dismiss();
        uploadOrderMenu = new PlayerOptionsMenu(requireActivity(), recyclerView);
        uploadOrderMenu.getMenu().add(0, 1, 0, R.string.uploader_playback_oldest);
        uploadOrderMenu.getMenu().add(0, 2, 1, R.string.uploader_playback_newest);
        uploadOrderMenu.setOnMenuItemClickListener(item -> {
            startUploadPlayback(item.getItemId() == 1);
            return true;
        });
        uploadOrderMenu.show();
    }

    private void startUploadPlayback(boolean oldest) {
        if (playbackOpening || !isAdded() || getView() == null) return;
        final android.content.Context context = requireContext().getApplicationContext();
        final long account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        final int generation = ++playbackGeneration;
        playbackOpening = true;
        MsgUtil.showMsg(getString(R.string.uploader_playback_preparing));
        CenterThreadPool.run(() -> {
            try {
                FavoritePlaybackSource.RequestGuard guard = () -> {
                    if (generation != playbackGeneration || account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                        throw new java.io.IOException("投稿连播已取消或账号已切换");
                };
                UploaderPlaybackSource.Prepared prepared = uploaderPlaybackSource.prepare(account, mid, oldest, guard);
                prepared.data.favoritePlaybackToken = FavoritePlaybackSource.create(context, prepared.session);
                guard.check();
                CenterThreadPool.runOnUiThread(() -> {
                    if (generation != playbackGeneration) return;
                    playbackOpening = false;
                    if (!isAdded() || getView() == null || account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
                    com.RobinNotBad.BiliClient.api.PlayerApi.startGettingUrl(prepared.data);
                });
            } catch (Exception error) {
                CenterThreadPool.runOnUiThread(() -> {
                    if (generation != playbackGeneration) return;
                    playbackOpening = false;
                    if (isAdded() && getView() != null) MsgUtil.showMsg(error instanceof java.io.IOException
                            ? error.getMessage() : getString(R.string.uploader_playback_failed));
                });
            }
        });
    }

    @Override public void onDestroyView() {
        searchGeneration++;
        playbackGeneration++;
        playbackOpening = false;
        if (uploadOrderMenu != null) uploadOrderMenu.dismiss();
        if (sortMenu != null) sortMenu.dismiss();
        uploadOrderMenu = null; sortMenu = null; listSource = null; listLoading = false;
        super.onDestroyView();
    }
}
