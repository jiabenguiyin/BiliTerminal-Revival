package com.RobinNotBad.BiliClient.activity.video;

import com.RobinNotBad.BiliClient.model.VideoCard;

import java.util.ArrayList;
import java.util.List;

final class RecommendationFeedLoader {
    interface FeedRequest {
        void load(List<VideoCard> cards) throws Exception;
    }

    interface IndexedFeedRequest {
        void load(List<VideoCard> cards, long pageIndex) throws Exception;
    }

    interface FallbackRecorder {
        void record(String reason, Exception error);
    }

    static final class Result {
        final List<VideoCard> cards;
        final boolean usedWeb;

        Result(List<VideoCard> cards, boolean usedWeb) {
            this.cards = cards;
            this.usedWeb = usedWeb;
        }
    }

    private RecommendationFeedLoader() {
    }

    static Result load(boolean appMode, boolean hasAppAccessKey,
                       FeedRequest personalizedApp, FeedRequest publicApp,
                       FeedRequest web, FallbackRecorder fallbackRecorder) throws Exception {
        List<VideoCard> cards = new ArrayList<>();
        if (!appMode) {
            web.load(cards);
            return new Result(cards, true);
        }

        if (hasAppAccessKey) {
            try {
                personalizedApp.load(cards);
            } catch (Exception error) {
                fallbackRecorder.record("request_error", error);
            }
        }

        if (cards.isEmpty()) {
            fallbackRecorder.record(hasAppAccessKey ? "public_retry" : "public_without_app_key", null);
            try {
                publicApp.load(cards);
            } catch (Exception error) {
                fallbackRecorder.record("public_retry_error", error);
            }
        }

        if (cards.isEmpty()) {
            fallbackRecorder.record("web_retry", null);
            web.load(cards);
            return new Result(cards, true);
        }
        return new Result(cards, false);
    }

    static Result load(boolean appMode, boolean hasAppAccessKey,
                       IndexedFeedRequest personalizedApp, IndexedFeedRequest publicApp,
                       FeedRequest web, long appPageIndex,
                       FallbackRecorder fallbackRecorder) throws Exception {
        return load(
                appMode,
                hasAppAccessKey,
                cards -> personalizedApp.load(cards, appPageIndex),
                cards -> publicApp.load(cards, appPageIndex),
                web,
                fallbackRecorder);
    }
}
