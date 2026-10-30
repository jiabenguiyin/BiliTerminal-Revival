package com.RobinNotBad.BiliClient.api;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class FavoriteApiTest {
    @Test
    public void deleteFavoriteUsesQueryParameters() {
        String url = FavoriteApi.buildDeleteFavoriteUrl(123L, 456L);

        assertTrue(url.startsWith("https://api.bilibili.com/x/v3/fav/resource/batch-del?"));
        assertTrue(url.contains("resources=123%3A2"));
        assertTrue(url.contains("media_id=4560"));
        assertTrue(url.contains("platform=web"));
    }

    @Test
    public void deleteFavoriteUsesFormBodyWithCsrf() {
        String body = FavoriteApi.buildDeleteFavoriteBody(123L, 456L);

        assertTrue(body.contains("resources=123%3A2"));
        assertTrue(body.contains("media_id=4560"));
        assertTrue(body.contains("platform=web"));
        assertTrue(body.contains("csrf="));
    }

    @Test
    public void deleteFavoriteDoesNotDuplicateExistingMediaSuffix() {
        String body = FavoriteApi.buildDeleteFavoriteBody(123L, 4560L);

        assertTrue(body.contains("media_id=4560"));
        assertTrue(!body.contains("media_id=45600"));
    }

    @Test
    public void addFavoriteUsesOriginalFolderIdAndFormEncoding() {
        String body = FavoriteApi.buildAddFavoriteBody(123L, 456L);

        assertTrue(body.contains("rid=123"));
        assertTrue(body.contains("type=2"));
        assertTrue(body.contains("add_media_ids=456"));
        assertTrue(body.contains("del_media_ids="));
        assertTrue(body.contains("csrf="));
        assertTrue(!body.contains("media_id=456"));
    }
}
