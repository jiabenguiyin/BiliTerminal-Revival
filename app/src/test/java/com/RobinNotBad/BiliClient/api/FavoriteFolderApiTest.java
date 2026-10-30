package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.FavoriteFolder;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import okhttp3.HttpUrl;
import static org.junit.Assert.*;

public class FavoriteFolderApiTest {
    @Test public void folderMetadataKeepsFullLongIdAndPrivateFlag() throws Exception {
        FavoriteFolder folder = FavoriteApi.parseFavoriteFolderInfo(metadata(3));
        assertEquals(12345678901242L, folder.id);
        assertEquals(42L, folder.ownerMid);
        assertEquals("Do not change & 中文", folder.intro);
        assertTrue(folder.isPrivate());
        assertFalse(folder.isDefault());
    }

    @Test public void defaultAndPublicFlagsUseDifferentBits() throws Exception {
        assertTrue(FavoriteApi.parseFavoriteFolderInfo(metadata(0)).isDefault());
        assertFalse(FavoriteApi.parseFavoriteFolderInfo(metadata(0)).isPrivate());
        assertTrue(FavoriteApi.parseFavoriteFolderInfo(metadata(1)).isDefault());
        assertTrue(FavoriteApi.parseFavoriteFolderInfo(metadata(1)).isPrivate());
        assertFalse(FavoriteApi.parseFavoriteFolderInfo(metadata(54)).isDefault());
        assertFalse(FavoriteApi.parseFavoriteFolderInfo(metadata(54)).isPrivate());
    }

    @Test public void editBodyEncodesUserTextAndNeverSubmitsCover() {
        HttpUrl body = HttpUrl.parse("https://example.invalid/?" + FavoriteApi.buildEditFolderBody(
                12345678901242L, "Work & 学习+", "Do not change & 中文", true));
        assertEquals("12345678901242", body.queryParameter("media_id"));
        assertEquals("Work & 学习+", body.queryParameter("title"));
        assertEquals("Do not change & 中文", body.queryParameter("intro"));
        assertNull(body.queryParameter("cover"));
        assertEquals("1", body.queryParameter("privacy"));
        assertNotNull(body.queryParameter("csrf"));
    }

    @Test public void deleteFolderUsesOnlyTheExactFullId() {
        HttpUrl body = HttpUrl.parse("https://example.invalid/?" + FavoriteApi.buildDeleteFolderBody(12345678901242L));
        assertEquals("12345678901242", body.queryParameter("media_ids"));
        assertNull(body.queryParameter("media_id"));
        assertNull(body.queryParameter("resources"));
    }

    @Test(expected = JSONException.class) public void missingPrivacyCannotSilentlyBecomePublic() throws Exception {
        JSONObject json = metadata(3);
        json.remove("attr");
        FavoriteApi.parseFavoriteFolderInfo(json);
    }

    private static JSONObject metadata(int attr) throws Exception {
        return new JSONObject().put("id", 12345678901242L).put("fid", 123456789012L).put("mid", 42)
                .put("title", "Work").put("cover", "https://i0.hdslb.com/bfs/medialist/old.png")
                .put("intro", "Do not change & 中文").put("attr", attr).put("media_count", 13);
    }
}
