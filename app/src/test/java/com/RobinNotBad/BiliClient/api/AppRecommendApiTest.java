package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.VideoCard;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppRecommendApiTest {
    @Test
    public void parsesAidFromPlayerArgsAndSkipsAds() throws Exception {
        JSONArray items = new JSONArray();
        items.put(new JSONObject()
                .put("goto", "av")
                .put("title", "测试视频")
                .put("cover", "https://example.com/cover.jpg")
                .put("args", new JSONObject().put("up_name", "UP"))
                .put("player_args", new JSONObject().put("aid", 123).put("cid", 456)));
        items.put(new JSONObject().put("goto", "ad").put("title", "广告"));

        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(1, AppRecommendApi.parseItems(items, cards));
        assertEquals(123L, cards.get(0).aid);
        assertEquals(456L, cards.get(0).cid);
    }

    @Test
    public void deduplicatesAidAndBvidCards() throws Exception {
        JSONArray items = new JSONArray();
        items.put(new JSONObject().put("goto", "av").put("bvid", "BV1test")
                .put("args", new JSONObject().put("aid", 10)));
        items.put(new JSONObject().put("goto", "av").put("bvid", "BV1test")
                .put("args", new JSONObject().put("aid", 10)));

        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(1, AppRecommendApi.parseItems(items, cards));
        assertEquals(1, cards.size());
    }

    @Test
    public void acceptsListContainerAndParamAid() throws Exception {
        JSONObject response = new JSONObject().put("code", 0).put("data", new JSONObject()
                .put("list", new JSONArray().put(new JSONObject()
                        .put("goto", "av")
                        .put("param", "av987")
                        .put("title", "参数视频"))));
        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(1, AppRecommendApi.parseResponse(response, cards));
        assertEquals(987L, cards.get(0).aid);
    }

    @Test
    public void acceptsLegacyItemContainerAndCardGoto() throws Exception {
        JSONObject response = new JSONObject().put("code", 0).put("data", new JSONObject()
                .put("item", new JSONArray().put(new JSONObject()
                        .put("goto", "av")
                        .put("card_goto", "av")
                        .put("param", "av987")
                        .put("title", "旧版字段视频")
                        .put("cover", "//i0.hdslb.com/video.jpg")
                        .put("desc_button", new JSONObject().put("text", "示例 UP")))));

        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(1, AppRecommendApi.parseResponse(response, cards));
        assertEquals(987L, cards.get(0).aid);
        assertEquals("示例 UP", cards.get(0).upName);
        assertEquals("https://i0.hdslb.com/video.jpg", cards.get(0).cover);
    }

    @Test
    public void parsesCurrentAppFeedItemsShape() throws Exception {
        JSONObject card = new JSONObject()
                .put("goto", "av")
                .put("card_goto", "av")
                .put("param", "117485323945533")
                .put("cover", "http://i0.hdslb.com/bfs/archive/video.jpg")
                .put("title", "现行 App 推荐卡片")
                .put("cover_left_text_1", "232.5万")
                .put("desc_button", new JSONObject().put("text", "示例 UP"))
                .put("args", new JSONObject().put("aid", 117485323945533L)
                        .put("up_name", "示例 UP"))
                .put("player_args", new JSONObject().put("aid", 117485323945533L)
                        .put("cid", 42963044208L));
        JSONObject response = new JSONObject().put("code", 0).put("data",
                new JSONObject().put("items", new JSONArray().put(card)));

        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(1, AppRecommendApi.parseResponse(response, cards));
        assertEquals(117485323945533L, cards.get(0).aid);
        assertEquals(42963044208L, cards.get(0).cid);
        assertEquals("现行 App 推荐卡片", cards.get(0).title);
        assertEquals("https://i0.hdslb.com/bfs/archive/video.jpg", cards.get(0).cover);
    }

    @Test
    public void parsesRealPublicAppFeedCardShape() throws Exception {
        JSONObject card = new JSONObject()
                .put("card_type", "small_cover_v2")
                .put("card_goto", "av")
                .put("goto", "av")
                .put("param", "117484971759480")
                .put("cover", "http://i1.hdslb.com/bfs/archive/example.jpg")
                .put("title", "真实 App 公共推荐卡片")
                .put("args", new JSONObject().put("aid", 117484971759480L))
                .put("player_args", new JSONObject().put("aid", 117484971759480L)
                        .put("cid", 42961864301L));
        JSONObject response = new JSONObject().put("code", 0).put("data",
                new JSONObject().put("items", new JSONArray().put(card)));
        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(1, AppRecommendApi.parseResponse(response, cards));
        assertEquals(117484971759480L, cards.get(0).aid);
        assertEquals(42961864301L, cards.get(0).cid);
    }

    @Test
    public void parsesAndroidFeedShapeWithoutBvid() throws Exception {
        JSONObject card = new JSONObject()
                .put("goto", "av")
                .put("card_goto", "large_cover_v1")
                .put("param", "117488444573443")
                .put("title", "Android 推荐卡片")
                .put("cover", "http://i0.hdslb.com/bfs/archive/video.jpg")
                .put("args", new JSONObject().put("up_name", "示例 UP"))
                .put("player_args", new JSONObject()
                        .put("aid", 117488444573443L)
                        .put("cid", 42970000000L));
        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(1, AppRecommendApi.parseItems(new JSONArray().put(card), cards));
        assertEquals(117488444573443L, cards.get(0).aid);
        assertEquals(42970000000L, cards.get(0).cid);
    }

    @Test
    public void acceptsVideoCardWhenGotoIsEmpty() throws Exception {
        JSONObject card = new JSONObject()
                .put("card_goto", "large_cover_v1")
                .put("param", "987654")
                .put("title", "没有 goto 的 App 推荐卡片")
                .put("cover", "//i0.hdslb.com/bfs/archive/video.jpg");
        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(1, AppRecommendApi.parseItems(new JSONArray().put(card), cards));
        assertEquals(987654L, cards.get(0).aid);
    }

    @Test
    public void rejectsUnknownCardTypeWhenGotoIsEmpty() throws Exception {
        JSONObject card = new JSONObject()
                .put("card_goto", "bangumi")
                .put("param", "987654")
                .put("title", "非视频卡片");
        assertEquals(0, AppRecommendApi.parseItems(new JSONArray().put(card), new ArrayList<>()));
    }

    @Test
    public void acceptsNestedFeedItemsContainer() throws Exception {
        JSONObject card = new JSONObject()
                .put("card_goto", "large_cover_v1")
                .put("param", "123456")
                .put("title", "嵌套推荐卡片");
        JSONObject response = new JSONObject().put("code", 0).put("data",
                new JSONObject().put("feed", new JSONObject()
                        .put("items", new JSONArray().put(card))));
        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(1, AppRecommendApi.parseResponse(response, cards));
        assertEquals(123456L, cards.get(0).aid);
    }

    @Test
    public void acceptsIdAndBvidParamWithoutParsingBvidDigitsAsAid() throws Exception {
        JSONArray items = new JSONArray();
        items.put(new JSONObject().put("goto", "av").put("id", 321)
                .put("title", "使用 id 的视频"));
        items.put(new JSONObject().put("goto", "av").put("param", "BV1xx411c7mD")
                .put("title", "使用 BV 参数的视频"));

        ArrayList<VideoCard> cards = new ArrayList<>();
        assertEquals(2, AppRecommendApi.parseItems(items, cards));
        assertEquals(321L, cards.get(0).aid);
        assertEquals("BV1xx411c7mD", cards.get(1).bvid);
        assertEquals(0L, cards.get(1).aid);
    }

    @Test
    public void skipsAdsMarkedByCardGoto() throws Exception {
        JSONArray items = new JSONArray();
        items.put(new JSONObject().put("goto", "av").put("card_goto", "ad_av")
                .put("param", "av111").put("title", "广告"));

        assertEquals(0, AppRecommendApi.parseItems(items, new ArrayList<>()));
    }

    @Test
    public void buildsOfficialAndroidFeedParameters() {
        Map<String, String> params = AppRecommendApi.buildFeedParams(123456L, "");

        assertEquals("123456", params.get("idx"));
        assertEquals("android", params.get("mobi_app"));
        assertEquals("phone", params.get("device"));
        assertEquals("", params.get("access_key"));
        assertFalse(params.containsKey("accessKey"));
        assertEquals(AppRecommendApi.APP_KEY, params.get("appkey"));
        assertEquals("master", params.get("channel"));
        assertEquals("false", params.get("pull"));
        assertEquals("0", params.get("login_event"));
        assertEquals("", params.get("open_event"));
        assertEquals("8", params.get("flush"));
        assertEquals("0", params.get("interest_id"));
        assertEquals("2", params.get("column"));
        assertEquals("1", params.get("window_size_type"));
        assertEquals("1", params.get("raw_window_size_type"));
        assertFalse(params.containsKey("privacy_disagree_mode"));
        assertTrue(params.containsKey("network"));
        assertTrue(params.containsKey("device_name"));
        assertTrue(params.containsKey("actionKey"));
        assertFalse(params.containsKey("statistics"));
        assertFalse(params.containsKey("device_type"));
    }

    @Test
    public void usesCamelCaseAccessKeyForPersonalizedFeed() {
        Map<String, String> params = AppRecommendApi.buildFeedParams(0L, "bound-token",
                true, 0, "cold");

        assertEquals("bound-token", params.get("access_key"));
        assertFalse(params.containsKey("accessKey"));
    }

    @Test
    public void appHeadersDoNotForwardWebLoginCookies() {
        ArrayList<String> headers = AppRecommendApi.appHeaders();
        for (int i = 0; i + 1 < headers.size(); i += 2) {
            assertFalse("App recommendation headers must not forward SESSDATA",
                    "Cookie".equalsIgnoreCase(headers.get(i)));
        }
    }

    @Test
    public void advancesFeedCursorByResponseItemCount() {
        assertEquals(128L, AppRecommendApi.nextPageIndex(100L, 28));
        assertEquals(101L, AppRecommendApi.nextPageIndex(100L, 0));
    }

    @Test
    public void failedPageReservationCanBeRetriedAndSuccessfulResponseAdvancesCursor() {
        AppRecommendApi.resetPaging();
        long first = AppRecommendApi.reservePageIndex();
        assertEquals(0L, first);
        assertEquals(0L, AppRecommendApi.reservePageIndex());
        AppRecommendApi.completePageIndex(first, 28);
        assertEquals(28L, AppRecommendApi.reservePageIndex());
        assertEquals("mobile", AppRecommendApi.mobileNetworkType(13));
    }

    @Test
    public void usesOfficialRefreshParametersForFirstPage() {
        Map<String, String> params = AppRecommendApi.buildFeedParams(0L, "", true, 0);

        assertEquals("0", params.get("idx"));
        assertEquals("true", params.get("pull"));
        assertEquals("cold", params.get("open_event"));
        assertEquals("0", params.get("flush"));
    }

    @Test
    public void usesOfficialNormalOpenEventValueAfterColdStart() {
        Map<String, String> params = AppRecommendApi.buildFeedParams(0L, "", true, 0, "");

        assertTrue(params.containsKey("open_event"));
        assertEquals("", params.get("open_event"));
    }

    @Test
    public void buildsCompatibleIpadFeedParameters() {
        Map<String, String> params = AppRecommendApi.buildIpadFeedParams(123456L, "");

        assertEquals("ios", params.get("platform"));
        assertEquals("iphone", params.get("mobi_app"));
        assertEquals("pad", params.get("device"));
        assertEquals("90300100", params.get("build"));
        assertEquals("123456", params.get("idx"));
        assertEquals(AppRecommendApi.APP_KEY, params.get("appkey"));
        assertFalse(params.containsKey("access_key"));
        assertFalse(params.containsKey("actionKey"));
    }

    @Test
    public void resetsIpadPagingWithTheAppProfile() {
        AppRecommendApi.resetPaging();
        long beforeFirst = System.currentTimeMillis() / 1000L;
        long firstIndex = AppRecommendApi.nextIpadRequestIndex();
        long secondIndex = AppRecommendApi.nextIpadRequestIndex();
        assertEquals(100L, secondIndex - firstIndex);
        assertTrue(firstIndex >= beforeFirst + 100L);
        assertTrue(firstIndex <= System.currentTimeMillis() / 1000L + 100L);

        AppRecommendApi.resetPaging();
        long beforeReset = System.currentTimeMillis() / 1000L;
        long resetIndex = AppRecommendApi.nextIpadRequestIndex();
        assertTrue(resetIndex >= beforeReset + 100L);
        assertTrue(resetIndex <= System.currentTimeMillis() / 1000L + 100L);
    }

    @Test
    public void sendsColdOpenEventOnlyForInitialFeedLoad() {
        AppRecommendApi.resetPaging(true);
        assertEquals("cold", AppRecommendApi.takeOpenEvent());
        assertEquals("", AppRecommendApi.takeOpenEvent());

        AppRecommendApi.resetPaging(false);
        assertEquals("", AppRecommendApi.takeOpenEvent());
        AppRecommendApi.resetPaging(true);
    }
}
