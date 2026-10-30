package com.RobinNotBad.BiliClient.activity.video;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.RefreshMainActivity;
import com.RobinNotBad.BiliClient.adapter.video.VideoCardAdapter;
import com.RobinNotBad.BiliClient.api.AppRecommendApi;
import com.RobinNotBad.BiliClient.api.RecommendApi;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//推荐页面
//2023-07-13

public class RecommendActivity extends RefreshMainActivity {

    private List<VideoCard> videoCardList;
    private VideoCardAdapter videoCardAdapter;
    private boolean firstRefresh = true;
    private int nextWebFreshIndex;
    private int refreshGeneration;
    private int requestSequence;
    private int inFlightRequestId;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setMenuClick();
        Log.e("debug", "进入推荐页");

        setOnRefreshListener(this::refreshRecommend);
        setOnLoadMoreListener(page -> addRecommend());

        setPageName("推荐");

        recyclerView.setHasFixedSize(true);

        TutorialHelper.showTutorialList(this, R.array.tutorial_recommend, 0);

        refreshRecommend();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refreshRecommend() {
        Log.e("debug", "刷新");
        refreshGeneration++;
        inFlightRequestId = 0;
        if (firstRefresh) {
            videoCardList = new ArrayList<>();
        } else {
            int last = videoCardList.size();
            videoCardList.clear();
            if (last > 0) videoCardAdapter.notifyItemRangeRemoved(0, last);
        }

        nextWebFreshIndex = 0;
        bottom = false;
        AppRecommendApi.resetPaging(firstRefresh);

        addRecommend();
    }

    private void addRecommend() {
        if (inFlightRequestId != 0) return;
        final int generation = refreshGeneration;
        final int requestId = ++requestSequence;
        final int webFreshIndex = nextWebFreshIndex;
        inFlightRequestId = requestId;
        Log.e("debug", "加载下一页");
        CenterThreadPool.run(() -> {
            try {
                boolean appMode = SharedPreferencesUtil.getBoolean(
                        SharedPreferencesUtil.APP_RECOMMEND_EXPERIMENT, false);
                boolean hasAppAccessKey = appMode && AppRecommendApi.hasAccessKey();
                long appPageIndex = appMode ? AppRecommendApi.reservePageIndex() : 0L;
                recordAppLoader("start", appMode, hasAppAccessKey, 0, false);
                RecommendationFeedLoader.Result feed = RecommendationFeedLoader.load(
                        appMode,
                        hasAppAccessKey,
                        AppRecommendApi::getRecommend,
                        AppRecommendApi::getPublicRecommend,
                        cards -> RecommendApi.getRecommend(cards, webFreshIndex),
                        appPageIndex,
                        this::recordAppFallback);
                final List<VideoCard> list = feed.cards;
                boolean usedWeb = feed.usedWeb;
                final boolean didUseWeb = usedWeb;
                recordAppLoader("result", appMode, hasAppAccessKey, list.size(), usedWeb);
                runOnUiThread(() -> {
                    if (requestId == inFlightRequestId) inFlightRequestId = 0;
                    if (generation != refreshGeneration) return;
                    if (didUseWeb) nextWebFreshIndex = Math.max(nextWebFreshIndex, webFreshIndex + 1);
                    setRefreshing(false);
                    if (list.isEmpty()) {
                        loadFail(new IOException("推荐接口返回空列表"));
                        return;
                    }
                    List<VideoCard> newCards = filterAlreadyLoaded(list);
                    if (newCards.isEmpty()) {
                        setBottom(true);
                        return;
                    }
                    int oldSize = videoCardList.size();
                    videoCardList.addAll(newCards);
                    recordAppLoader("ui_added", appMode, hasAppAccessKey,
                            newCards.size(), didUseWeb);
                    if (firstRefresh) {
                        firstRefresh = false;
                        videoCardAdapter = new VideoCardAdapter(this, videoCardList);
                        setAdapter(videoCardAdapter);
                    } else {
                        videoCardAdapter.notifyItemRangeInserted(oldSize, newCards.size());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestId == inFlightRequestId) inFlightRequestId = 0;
                    if (generation != refreshGeneration) return;
                    setRefreshing(false);
                    loadFail(e);
                });
            }
        });
    }

    private List<VideoCard> filterAlreadyLoaded(List<VideoCard> candidates) {
        Set<String> seen = new HashSet<>();
        for (VideoCard card : videoCardList) addIdentity(seen, card);

        List<VideoCard> newCards = new ArrayList<>();
        for (VideoCard card : candidates) {
            String bvidKey = card.bvid == null || card.bvid.isEmpty() ? "" : "b:" + card.bvid;
            String aidKey = card.aid > 0 ? "a:" + card.aid : "";
            if ((!bvidKey.isEmpty() && seen.contains(bvidKey))
                    || (!aidKey.isEmpty() && seen.contains(aidKey))) continue;
            if (!bvidKey.isEmpty()) seen.add(bvidKey);
            if (!aidKey.isEmpty()) seen.add(aidKey);
            newCards.add(card);
        }
        return newCards;
    }

    private void addIdentity(Set<String> identities, VideoCard card) {
        if (card.bvid != null && !card.bvid.isEmpty()) identities.add("b:" + card.bvid);
        if (card.aid > 0) identities.add("a:" + card.aid);
    }

    private void recordAppFallback(String reason, Exception error) {
        JSONObject details = new JSONObject();
        try {
            details.put("reason", reason);
            if (error != null) details.put("error_type", error.getClass().getSimpleName());
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("app_recommend_fallback", details);
    }

    private void recordAppLoader(String phase, boolean appMode, boolean hasAccessKey,
                                 int cardCount, boolean usedWeb) {
        JSONObject details = new JSONObject();
        try {
            details.put("phase", phase);
            details.put("app_mode", appMode);
            details.put("has_access_key", hasAccessKey);
            details.put("card_count", cardCount);
            details.put("used_web", usedWeb);
        } catch (JSONException ignored) {
        }
        DiagnosticLogManager.record("app_recommend_loader", details);
    }
}
