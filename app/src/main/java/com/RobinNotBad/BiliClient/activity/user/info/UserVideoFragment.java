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
    private int searchGeneration;

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
        if (getArguments() != null) {
            mid = getArguments().getLong("mid");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        videoList = new ArrayList<>();
        setOnLoadMoreListener(this::continueLoading);

        final int generation = searchGeneration;
        CenterThreadPool.run(() -> {
            try {
                bottom = (UserInfoApi.getUserVideos(mid, page, searchKeyword, videoList) == 1);
                if (isAdded() && generation == searchGeneration) {
                    setRefreshing(false);
                    adapter = new UserVideoAdapter(requireContext(), mid, videoList);
                    setAdapter(adapter);
                    if (bottom && videoList.isEmpty()) showEmptyView();
                }
            } catch (Exception e) {
                loadFail(e);
            }
        });
    }

    public void search(String keyword) {
        searchKeyword = keyword == null ? "" : keyword.trim();
        final int generation = ++searchGeneration;
        page = 1;
        bottom = false;
        if (videoList == null) return;
        videoList.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(true);
        CenterThreadPool.run(() -> {
            try {
                ArrayList<VideoCard> matches = new ArrayList<>();
                int result = 0;
                int searchPage = 1;
                do {
                    ArrayList<VideoCard> loaded = new ArrayList<>();
                    result = UserInfoApi.getUserVideos(mid, searchPage, "", loaded);
                    for (VideoCard card : loaded) {
                        String title = normalizeSearchTitle(card.title);
                        if (searchKeyword.isEmpty()
                                || title.toLowerCase(Locale.ROOT).contains(searchKeyword.toLowerCase(Locale.ROOT))) {
                            matches.add(card);
                        }
                    }
                    searchPage++;
                } while (result == 0 && searchPage <= 200);
                final int finalResult = result;
                if (isAdded() && generation == searchGeneration) runOnUiThread(() -> {
                    if (generation != searchGeneration || videoList == null) return;
                    videoList.addAll(matches);
                    bottom = finalResult == 1;
                    if (adapter != null) adapter.notifyDataSetChanged();
                    if (bottom && videoList.isEmpty()) showEmptyView();
                    setRefreshing(false);
                });
            } catch (Exception e) { loadFail(e); }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void continueLoading(int page) {
        final int generation = searchGeneration;
        CenterThreadPool.run(() -> {
            try {
                List<VideoCard> list = new ArrayList<>();
                int result = UserInfoApi.getUserVideos(mid, page, searchKeyword, list);
                if (result != -1) {
                    Log.e("debug", "下一页");
                    runOnUiThread(() -> {
                        if (!isAdded() || generation != searchGeneration
                                || adapter == null || videoList == null) return;
                        int insertAt = videoList.size();
                        videoList.addAll(list);
                        if (!list.isEmpty()) adapter.notifyItemRangeInserted(insertAt, list.size());
                    });
                    if (result == 1) {
                        Log.e("debug", "到底了");
                        bottom = true;
                    }
                }
                setRefreshing(false);
            } catch (Exception e) {
                loadFail(e);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private String normalizeSearchTitle(String title) {
        if (title == null || title.isEmpty()) return "";
        CharSequence plain = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                ? Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY)
                : Html.fromHtml(title);
        return plain == null ? title : plain.toString();
    }
}
