package com.RobinNotBad.BiliClient.activity.base;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Search and paging shared only by the favorites and history video lists. */
public abstract class SearchableVideoListActivity extends RefreshListActivity {
    protected final ArrayList<VideoCard> videoList = new ArrayList<>();
    protected VideoCardAdapter videoCardAdapter;
    private EditText keywordInput;
    private String keyword = "";
    private ApiResult cursor = new ApiResult();
    private int generation;
    private boolean destroyed;
    private boolean loading;

    protected void startVideoList(Bundle savedState, int searchHint) {
        RelativeLayout root = (RelativeLayout) swipeRefreshLayout.getParent();
        View searchBar = LayoutInflater.from(this).inflate(R.layout.view_library_search, root, false);
        RelativeLayout.LayoutParams searchParams = new RelativeLayout.LayoutParams(-1, -2);
        searchParams.addRule(RelativeLayout.BELOW, R.id.top);
        root.addView(searchBar, searchParams);
        RelativeLayout.LayoutParams listParams = (RelativeLayout.LayoutParams) swipeRefreshLayout.getLayoutParams();
        listParams.addRule(RelativeLayout.BELOW, R.id.library_search_bar);
        swipeRefreshLayout.setLayoutParams(listParams);
        RelativeLayout.LayoutParams emptyParams = (RelativeLayout.LayoutParams) emptyView.getLayoutParams();
        emptyParams.addRule(RelativeLayout.BELOW, R.id.library_search_bar);
        emptyView.setLayoutParams(emptyParams);
        keywordInput = searchBar.findViewById(R.id.library_keyword);
        keywordInput.setHint(searchHint);
        keywordInput.setContentDescription(getString(searchHint));
        if (savedState != null) keyword = savedState.getString("library_query", "");
        keywordInput.setText(keyword);
        View clear = searchBar.findViewById(R.id.library_clear);
        clear.setVisibility(keyword.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        keywordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        clear.setOnClickListener(view -> { keywordInput.setText(""); submitSearch(); });
        searchBar.findViewById(R.id.library_search).setOnClickListener(view -> submitSearch());
        keywordInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                submitSearch();
                return true;
            }
            return false;
        });
        searchBar.requestFocus();
        videoCardAdapter = new VideoCardAdapter(this, videoList);
        configureVideoAdapter();
        setAdapter(videoCardAdapter);
        setOnRefreshListener(this::reloadVideos);
        setOnLoadMoreListener(number -> { if (!loading) requestPage(number); });
        emptyView.setOnClickListener(view -> {
            if (!loading && !bottom) requestPage(page + 1);
            else if (!loading) reloadVideos();
        });
        reloadVideos();
    }

    private void submitSearch() {
        keyword = keywordInput.getText().toString().trim();
        keywordInput.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(keywordInput.getWindowToken(), 0);
        reloadVideos();
    }

    protected final void reloadVideos() {
        generation++;
        page = 1;
        cursor = new ApiResult();
        bottom = false;
        videoList.clear();
        videoCardAdapter.notifyDataSetChanged();
        hideEmptyView();
        requestPage(1);
    }

    private void requestPage(int requestedPage) {
        int requestGeneration = generation;
        long account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String query = keyword;
        ApiResult previous = new ApiResult().setOffset(cursor.timestamp, cursor.offset, cursor.business)
                .setResult(cursor.result);
        loading = true;
        setRefreshing(true);
        CenterThreadPool.run(() -> {
            ArrayList<VideoCard> result = new ArrayList<>();
            try {
                ApiResult next = loadVideos(requestedPage, query, previous, result);
                runOnUiThread(() -> {
                    if (destroyed || generation != requestGeneration) return;
                    loading = false;
                    setRefreshing(false);
                    if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
                        emptyView.setText("账号已切换，请返回后重试");
                        bottom = true;
                        showEmptyView();
                        return;
                    }
                    if (next.code != 0) {
                        failPage(requestedPage, next.message + "（" + next.code + "）");
                        return;
                    }
                    cursor = next;
                    page = requestedPage;
                    bottom = next.isBottom;
                    int before = videoList.size();
                    Set<Long> seen = new HashSet<>();
                    if (deduplicateVideos()) for (VideoCard card : videoList) seen.add(card.aid);
                    for (VideoCard card : result) {
                        if (!deduplicateVideos() || seen.add(card.aid)) videoList.add(card);
                    }
                    videoCardAdapter.notifyItemRangeInserted(before, videoList.size() - before);
                    if (videoList.isEmpty()) {
                        emptyView.setText(bottom ? getString(query.isEmpty() ? R.string.library_empty : R.string.library_no_results)
                                : "暂无视频，点击继续加载");
                        showEmptyView();
                    } else hideEmptyView();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (destroyed || generation != requestGeneration) return;
                    Logu.e("library-search", "request failed: " + error.getClass().getSimpleName());
                    failPage(requestedPage, getString(R.string.library_load_failed));
                });
            }
        });
    }

    private void failPage(int requestedPage, String message) {
        loading = false;
        page = Math.max(1, requestedPage - 1);
        setRefreshing(false);
        if (videoList.isEmpty()) {
            bottom = true;
            emptyView.setText(message);
            showEmptyView();
        } else MsgUtil.showMsgLong(message);
    }

    protected void configureVideoAdapter() { }
    protected boolean deduplicateVideos() { return false; }
    protected abstract ApiResult loadVideos(int page, String keyword, ApiResult previous, List<VideoCard> output)
            throws Exception;

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString("library_query", keyword);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        generation++;
        super.onDestroy();
    }
}
