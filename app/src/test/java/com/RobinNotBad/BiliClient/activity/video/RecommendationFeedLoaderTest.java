package com.RobinNotBad.BiliClient.activity.video;

import com.RobinNotBad.BiliClient.model.VideoCard;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecommendationFeedLoaderTest {
    @Test
    public void usesPublicAppFeedWhenNoAppCredentialIsBound() throws Exception {
        List<String> calls = new ArrayList<>();
        List<String> fallbacks = new ArrayList<>();
        RecommendationFeedLoader.Result result = RecommendationFeedLoader.load(
                true, false,
                cards -> calls.add("personalized"),
                cards -> {
                    calls.add("public");
                    cards.add(card("app-public"));
                },
                cards -> calls.add("web"),
                (reason, error) -> fallbacks.add(reason));

        assertEquals(Arrays.asList("public"), calls);
        assertEquals(Arrays.asList("public_without_app_key"), fallbacks);
        assertEquals("app-public", result.cards.get(0).bvid);
        assertFalse(result.usedWeb);
    }

    @Test
    public void usesPublicAppFeedWhenPersonalizedFeedIsEmpty() throws Exception {
        List<String> calls = new ArrayList<>();
        RecommendationFeedLoader.Result result = RecommendationFeedLoader.load(
                true, true,
                cards -> calls.add("personalized"),
                cards -> {
                    calls.add("public");
                    cards.add(card("app-public"));
                },
                cards -> calls.add("web"),
                (reason, error) -> { });

        assertEquals(Arrays.asList("personalized", "public"), calls);
        assertEquals("app-public", result.cards.get(0).bvid);
        assertFalse(result.usedWeb);
    }

    @Test
    public void fallsBackToWebWhenBothAppFeedsFailOrAreEmpty() throws Exception {
        List<String> calls = new ArrayList<>();
        List<String> fallbacks = new ArrayList<>();
        RecommendationFeedLoader.Result result = RecommendationFeedLoader.load(
                true, true,
                cards -> {
                    calls.add("personalized");
                    throw new IOException("personalized unavailable");
                },
                cards -> {
                    calls.add("public");
                    throw new IOException("public unavailable");
                },
                cards -> {
                    calls.add("web");
                    cards.add(card("web-fallback"));
                },
                (reason, error) -> fallbacks.add(reason));

        assertEquals(Arrays.asList("personalized", "public", "web"), calls);
        assertEquals(Arrays.asList("request_error", "public_retry", "public_retry_error", "web_retry"), fallbacks);
        assertEquals("web-fallback", result.cards.get(0).bvid);
        assertTrue(result.usedWeb);
    }

    @Test
    public void fallsBackToWebWhenPublicAppFeedIsEmpty() throws Exception {
        List<String> calls = new ArrayList<>();
        List<String> fallbacks = new ArrayList<>();
        RecommendationFeedLoader.Result result = RecommendationFeedLoader.load(
                true, false,
                cards -> calls.add("personalized"),
                cards -> calls.add("public"),
                cards -> {
                    calls.add("web");
                    cards.add(card("web-fallback"));
                },
                (reason, error) -> fallbacks.add(reason));

        assertEquals(Arrays.asList("public", "web"), calls);
        assertEquals(Arrays.asList("public_without_app_key", "web_retry"), fallbacks);
        assertEquals("web-fallback", result.cards.get(0).bvid);
        assertTrue(result.usedWeb);
    }

    @Test
    public void keepsWebModeOnTheExistingWebFeed() throws Exception {
        List<String> calls = new ArrayList<>();
        RecommendationFeedLoader.Result result = RecommendationFeedLoader.load(
                false, false,
                cards -> calls.add("personalized"),
                cards -> calls.add("public"),
                cards -> {
                    calls.add("web");
                    cards.add(card("web-default"));
                },
                (reason, error) -> { });

        assertEquals(Arrays.asList("web"), calls);
        assertEquals("web-default", result.cards.get(0).bvid);
        assertTrue(result.usedWeb);
    }

    @Test
    public void reusesOneAppPageIndexAcrossPersonalizedAndPublicFallback() throws Exception {
        List<Long> pageIndexes = new ArrayList<>();
        RecommendationFeedLoader.Result result = RecommendationFeedLoader.load(
                true, true,
                (cards, pageIndex) -> pageIndexes.add(pageIndex),
                (cards, pageIndex) -> {
                    pageIndexes.add(pageIndex);
                    cards.add(card("app-public"));
                },
                cards -> { },
                12345L,
                (reason, error) -> { });

        assertEquals(Arrays.asList(12345L, 12345L), pageIndexes);
        assertEquals("app-public", result.cards.get(0).bvid);
        assertFalse(result.usedWeb);
    }

    private static VideoCard card(String bvid) {
        return new VideoCard("title", "up", "", "", 1L, bvid);
    }
}
