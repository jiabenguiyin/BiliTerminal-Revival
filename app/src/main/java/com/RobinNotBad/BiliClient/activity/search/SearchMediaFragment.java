package com.RobinNotBad.BiliClient.activity.search;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;

import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter;
import com.RobinNotBad.BiliClient.api.SearchApi;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;

import java.util.ArrayList;

/** Adapted from cyq114514/Re-BiliTerminal's SearchBangumiFragment (GPL-3.0); see THIRD_PARTY_NOTICES.md. */
public class SearchMediaFragment extends SearchFragment {
    private static final String ARG_TYPE = "media_search_type";
    private final ArrayList<VideoCard> cards = new ArrayList<>();
    private VideoCardAdapter adapter;
    private String searchType;
    private int generation;

    public static SearchMediaFragment newInstance(String type) {
        SearchMediaFragment fragment = new SearchMediaFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_TYPE, type);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        searchType = requireArguments().getString(ARG_TYPE, SearchApi.TYPE_BANGUMI);
        adapter = new VideoCardAdapter(requireContext(), cards);
        setAdapter(adapter);
        setOnRefreshListener(this::refreshInternal);
        setOnLoadMoreListener(this::continueLoading);
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (keyword == null || keyword.isEmpty()) setRefreshing(false);
            else refreshInternal();
        });
    }

    @Override public void update(String keyword) {
        generation++;
        super.update(keyword);
    }

    @Override protected void refreshInternal() {
        generation++;
        refreshable = false;
        page = 1;
        bottom = false;
        int count = cards.size();
        cards.clear();
        if (count > 0) adapter.notifyItemRangeRemoved(0, count);
        recyclerView.scrollToPosition(0);
        showEmptyView(false);
        setRefreshing(true);
        continueLoading(1);
    }

    private void continueLoading(int requestedPage) {
        final int requestGeneration = generation;
        final String requestKeyword = keyword;
        final String requestType = searchType;
        CenterThreadPool.run(() -> {
            try {
                SearchApi.MediaSearchResult result = SearchApi.searchMedia(requestKeyword, requestedPage, requestType);
                runOnUiThread(() -> {
                    if (!accepts(requestGeneration)) return;
                    int count = cards.size();
                    cards.addAll(result.items);
                    if (!result.items.isEmpty()) adapter.notifyItemRangeInserted(count, result.items.size());
                    bottom = !result.hasMore;
                    showEmptyView(cards.isEmpty());
                    setRefreshing(false);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!accepts(requestGeneration)) return;
                    page = Math.max(1, requestedPage - 1);
                    refreshable = requestedPage == 1;
                    setRefreshing(false);
                    report(error);
                });
            }
        });
    }

    private boolean accepts(int requestGeneration) {
        return generation == requestGeneration && isAdded() && getView() != null;
    }

    @Override public void onDestroyView() {
        generation++;
        cards.clear();
        adapter = null;
        refreshable = true;
        super.onDestroyView();
    }
}
