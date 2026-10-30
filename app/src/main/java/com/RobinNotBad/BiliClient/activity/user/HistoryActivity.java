package com.RobinNotBad.BiliClient.activity.user;

import android.os.Bundle;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.SearchableVideoListActivity;
import com.RobinNotBad.BiliClient.api.HistoryApi;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;

import java.util.List;

public class HistoryActivity extends SearchableVideoListActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageName("历史记录");
        startVideoList(savedInstanceState, R.string.library_search_history);
    }

    @Override protected ApiResult loadVideos(int page, String keyword, ApiResult previous, List<VideoCard> output)
            throws Exception {
        return keyword.isEmpty() ? HistoryApi.getHistory(previous, output)
                : HistoryApi.searchHistory(keyword, page, output);
    }
}
