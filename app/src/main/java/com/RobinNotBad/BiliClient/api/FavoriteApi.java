package com.RobinNotBad.BiliClient.api;

import android.util.Log;
import android.util.Pair;

import com.RobinNotBad.BiliClient.model.Collection;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.FavoriteFolder;
import com.RobinNotBad.BiliClient.model.Opus;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.SearchQueryUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import okhttp3.Response;

//收藏API

public class FavoriteApi {
    // TODO 合集收藏

    public static ArrayList<FavoriteFolder> getFavoriteFolders(long mid) throws IOException, JSONException {
        ArrayList<FavoriteFolder> folders = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int page = 1; ; page++) {
            String url = "https://api.bilibili.com/x/v3/fav/folder/created/list"
                    + new NetWorkUtil.FormData()
                    .setUrlParam(true)
                    .put("up_mid", mid)
                    .put("type", 2)
                    .put("pn", page)
                    .put("ps", 20);
            JSONObject result = NetWorkUtil.getJson(url);
            if (result.optInt("code", -1) != 0) {
                throw new JSONException("收藏夹请求失败：" + result.optInt("code", -1)
                        + " " + result.optString("message"));
            }
            JSONObject data = result.optJSONObject("data");
            if (data != null) {
                JSONArray list = data.optJSONArray("list");
                if (list == null || list.length() == 0) return folders;
                int before = folders.size();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject folder = list.getJSONObject(i);

                    FavoriteFolder favoriteFolder = new FavoriteFolder();

                    favoriteFolder.id = folder.optLong("id", folder.optLong("fid"));
                    favoriteFolder.name = folder.optString("title");
                    favoriteFolder.cover = folder.optString("cover");
                    favoriteFolder.videoCount = folder.optInt("media_count");
                    favoriteFolder.maxCount = folder.optInt("max_count", 0);
                    favoriteFolder.ownerMid = folder.optLong("mid", mid);
                    favoriteFolder.attr = folder.optInt("attr", -1);
                    if (seen.add(favoriteFolder.id)) folders.add(favoriteFolder);
                }
                boolean more = data.has("has_more") ? data.optBoolean("has_more")
                        : (data.has("count") ? folders.size() < data.optInt("count") : list.length() >= 20);
                if (!more) return folders;
                if (folders.size() == before) throw new IOException("收藏夹分页未前进，请重试");
            } else return folders;
        }
    }

    /**
     * 获取收藏的合集
     *
     * @param mid            目标用户
     * @param page           页数
     * @param collectionList collection对象List
     * @return 返回码与has_more
     */
    public static Pair<Integer, Boolean> getFavoritedCollections(long mid, int page, List<Collection> collectionList) throws JSONException, IOException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/collected/list" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("platform", "web")
                .put("up_mid", mid)
                .put("pn", page)
                .put("ps", 10);
        JSONObject result = NetWorkUtil.getJson(url);
        JSONObject data = result.optJSONObject("data");
        boolean has_more = false;
        if (data != null) {
            has_more = data.optBoolean("has_more", false);
            JSONArray list = data.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    Collection collection = new Collection();
                    collection.id = item.optInt("id", -1);
                    collection.mid = item.optLong("mid", -1);
                    collection.title = item.optString("title");
                    collection.cover = item.optString("cover");
                    collection.intro = item.optString("intro");
                    collection.view = StringUtil.toWan(item.optInt("view_count", -1));
                    collectionList.add(collection);
                }
            }
        }
        return new Pair<>(result.optInt("code", -1), has_more);
    }

    public static int getFolderVideos(long mid, long fid, int page, ArrayList<VideoCard> videoList) throws IOException, JSONException {
        ApiResult result = getFavoriteVideos(fid, page, "", false, videoList);
        return result.code == 0 ? (result.isBottom ? 1 : 0) : -1;
    }

    static String buildFavoriteVideosUrl(long fid, int page, String keyword, boolean allFolders) {
        return buildFavoriteVideosUrl(fid,page,keyword,allFolders,"mtime");
    }

    static String buildFavoriteVideosUrl(long fid, int page, String keyword, boolean allFolders, String order) {
        String query = keyword == null ? "" : keyword.trim();
        if (allFolders && query.isEmpty()) {
            throw new IllegalArgumentException("全部收藏的浏览必须合并各收藏夹，不能使用空关键词的跨收藏夹搜索");
        }
        return "https://api.bilibili.com/x/v3/fav/resource/list" + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("media_id", fid)
                .put("pn", page)
                .put("ps", 20)
                .put("keyword", query)
                .put("order", com.RobinNotBad.BiliClient.util.FavoriteVideoOrder.normalize(order))
                .put("type", allFolders ? 1 : 0)
                .put("tid", 0)
                .put("platform", "web");
    }

    public static ApiResult getFavoriteVideos(long fid, int page, String keyword, boolean allFolders,
                                               List<VideoCard> videoList) throws IOException, JSONException {
        return getFavoriteVideos(fid,page,keyword,allFolders,videoList,"mtime");
    }

    public static ApiResult getFavoriteVideos(long fid, int page, String keyword, boolean allFolders,
                                               List<VideoCard> videoList,String order) throws IOException, JSONException {
        JSONObject result = NetWorkUtil.getJson(buildFavoriteVideosUrl(fid, page, keyword, allFolders,order));
        return parseFavoriteVideos(result, videoList);
    }

    public static ApiResult getAllFavoriteVideos(long mid, Object previous, List<VideoCard> output)
            throws IOException, JSONException {
        return AllFavoriteVideosPager.load(previous instanceof AllFavoriteVideosPager.Cursor
                ? (AllFavoriteVideosPager.Cursor) previous : null, new AllFavoriteVideosPager.Source() {
            @Override public List<Long> folders() throws IOException, JSONException {
                List<Long> ids = new ArrayList<>();
                for (FavoriteFolder folder : getFavoriteFolders(mid)) ids.add(folder.id);
                return ids;
            }

            @Override public JSONObject page(long mediaId, int page) throws IOException, JSONException {
                return NetWorkUtil.getJson(buildFavoriteVideosUrl(mediaId, page, "", false));
            }
        }, output);
    }

    static ApiResult parseFavoriteVideos(JSONObject result, List<VideoCard> videoList) throws JSONException {
        return parseFavoriteVideos(result, videoList, null);
    }

    static ApiResult parseFavoriteVideos(JSONObject result, List<VideoCard> videoList, List<Long> favoriteTimes)
            throws JSONException {
        ApiResult page = new ApiResult(result);
        if (page.code != 0) return page;

        JSONObject data = result.optJSONObject("data");
        if (data == null) {
            return page.setBottom(true);
        }

        // 尝试多种可能的数组字段名
        JSONArray medias = data.optJSONArray("medias");
        if (medias == null) medias = data.optJSONArray("archives");
        if (medias == null) medias = data.optJSONArray("items");

        if (medias == null || medias.length() == 0) return page.setBottom(true);
        for (int i = 0; i < medias.length(); i++) {
            JSONObject video = medias.optJSONObject(i);
            if (video == null) continue;
            if (video.optInt("type", 2) != 2) continue;

            String title = SearchQueryUtil.plainResultTitle(video.optString("title"));
            String cover = video.optString("cover", video.optString("pic"));
            long aid = video.optLong("id", video.optLong("aid", 0));
            if (aid == 0) continue;

            JSONObject upper = video.optJSONObject("upper");
            if (upper == null) upper = video.optJSONObject("owner");
            String upName = upper == null ? "" : upper.optString("name");

            JSONObject countInfo = video.optJSONObject("cnt_info");
            JSONObject stat = video.optJSONObject("stat");
            long viewCount = countInfo == null
                    ? (stat == null ? 0 : stat.optLong("view", 0))
                    : countInfo.optLong("play", countInfo.optLong("view", 0));
            String view = StringUtil.toWan(viewCount) + "观看";
            videoList.add(new VideoCard(title, upName, view, cover, aid,
                    video.optString("bvid", video.optString("bv_id"))));
            if (favoriteTimes != null) favoriteTimes.add(video.optLong("fav_time", 0));
        }

        return page.setBottom(!data.optBoolean("has_more", medias.length() >= 20));
    }

    public static boolean getFavouriteOpus(ArrayList<Opus> list, int page) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/favlist?page_size=10&page=" + page;
        JSONObject result = NetWorkUtil.getJson(url);
        Log.e("OpusFav", result.toString());
        boolean hasMore = result.getJSONObject("data").getBoolean("has_more");
        if (!hasMore) Log.e("图文", "没有更多啦");
        JSONArray items = result.getJSONObject("data").getJSONArray("items");

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            Opus opus = new Opus();
            opus.content = item.optString("content");
            opus.cover = item.optString("cover");
            opus.title = item.optString("title");
            if (opus.title.isEmpty()) opus.title = opus.content;
            opus.id = Long.parseLong(item.getString("opus_id"));
            opus.pubTime = item.optString("time_text");
            list.add(opus);
        }

        return hasMore;
    }

    public static void getFavoriteState(long aid, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Boolean> stateList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&jsonp=jsonp&rid=" + aid + "&up_mid=" + SharedPreferencesUtil.getLong("mid", 0);
        JSONObject result = NetWorkUtil.getJson(url);
        JSONObject data = result.getJSONObject("data");

        if (data.has("list") && !data.isNull("list")) {
            JSONArray list = data.getJSONArray("list");
            for (int i = 0; i < list.length(); i++) {
                JSONObject folder = list.getJSONObject(i);
                folderList.add(folder.getString("title"));
                fidList.add(folder.getLong("fid"));
                stateList.add(folder.getInt("fav_state") == 1);
            }
        }
    }

    public static FavoriteFolder getFavoriteFolderInfo(long mediaId) throws IOException, JSONException {
        JSONObject response = NetWorkUtil.getJson("https://api.bilibili.com/x/v3/fav/folder/info"
                + new NetWorkUtil.FormData().setUrlParam(true).put("media_id", mediaId));
        if (response.optInt("code", -1) != 0) {
            throw new IOException("收藏夹资料读取失败：" + response.optString("message", "")
                    + "（" + response.optInt("code", -1) + "）");
        }
        FavoriteFolder folder = parseFavoriteFolderInfo(response.getJSONObject("data"));
        if (folder.id != mediaId) throw new IOException("收藏夹资料与请求不符，暂不能修改");
        return folder;
    }

    static FavoriteFolder parseFavoriteFolderInfo(JSONObject data) throws JSONException {
        FavoriteFolder folder = new FavoriteFolder();
        folder.id = data.getLong("id");
        JSONObject upper = data.optJSONObject("upper");
        folder.ownerMid = data.optLong("mid", upper == null ? 0 : upper.optLong("mid", 0));
        folder.name = data.getString("title");
        folder.intro = data.getString("intro");
        folder.cover = data.getString("cover");
        folder.attr = data.getInt("attr");
        folder.videoCount = data.optInt("media_count", 0);
        if (folder.id <= 0 || folder.ownerMid <= 0 || folder.attr < 0) {
            throw new JSONException("收藏夹资料不完整，暂不能修改");
        }
        return folder;
    }

    public static ApiResult editFavoriteFolder(FavoriteFolder folder, String title, String intro,
                                                boolean privateFolder) throws IOException, JSONException {
        requireOwnedFolder(folder);
        title = title.trim();
        if (title.isEmpty() || title.length() > 20 || intro.length() > 200) {
            return new ApiResult(-400, "名称须为1至20字，简介不能超过200字");
        }
        if (folder.isDefault() && (!title.equals(folder.name) || !intro.equals(folder.intro))) {
            return new ApiResult(11009, "默认收藏夹只能修改公开状态");
        }
        return postFolderChange("edit_folder", "/x/v3/fav/folder/edit",
                buildEditFolderBody(folder.id, title, intro, privateFolder));
    }

    public static ApiResult deleteFavoriteFolder(FavoriteFolder folder) throws IOException, JSONException {
        requireOwnedFolder(folder);
        if (folder.isDefault()) return new ApiResult(11003, "默认收藏夹不能删除");
        return postFolderChange("delete_folder", "/x/v3/fav/folder/del", buildDeleteFolderBody(folder.id));
    }

    private static void requireOwnedFolder(FavoriteFolder folder) throws IOException {
        long account = SharedPreferencesUtil.sharedPreferences == null ? 0
                : SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (folder == null || folder.id <= 0 || folder.attr < 0 || account == 0 || folder.ownerMid != account) {
            throw new IOException("账号已切换或不是收藏夹创建者，请返回后重试");
        }
    }

    private static ApiResult postFolderChange(String operation, String path, String body)
            throws IOException, JSONException {
        try (Response response = NetWorkUtil.post("https://api.bilibili.com" + path, body, NetWorkUtil.webHeaders)) {
            if (response.body() == null) throw new IOException("收藏夹接口返回为空");
            ApiResult result = new ApiResult(new JSONObject(response.body().string()));
            JSONObject details = new JSONObject().put("operation", operation).put("code", result.code);
            DiagnosticLogManager.record("favorite_folder_result", details);
            return result;
        }
    }

    static String buildEditFolderBody(long mediaId, String title, String intro, boolean privateFolder) {
        return new NetWorkUtil.FormData().put("media_id", mediaId).put("title", title).put("intro", intro)
                .put("privacy", privateFolder ? 1 : 0).put("csrf", currentCsrf()).toString();
    }

    static String buildDeleteFolderBody(long mediaId) {
        // This is the complete id returned by folder/info; never append an account suffix.
        return new NetWorkUtil.FormData().put("media_ids", mediaId).put("csrf", currentCsrf()).toString();
    }

    public static int addFavorite(long aid, long fid) throws IOException, JSONException {
        String url = "https://api.bilibili.com/medialist/gateway/coll/resource/deal";
        String per = buildAddFavoriteBody(aid, fid);

        JSONObject result = new JSONObject(Objects.requireNonNull(NetWorkUtil.post(url, per, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-添加收藏", result.toString());
        JSONObject details = new JSONObject();
        details.put("operation", "add_favorite");
        details.put("code", result.optInt("code", -1));
        details.put("message", result.optString("message", ""));
        details.put("aid_positive", aid > 0);
        details.put("fid_positive", fid > 0);
        DiagnosticLogManager.record("favorite_add_result", details);
        return result.getInt("code");
    }

    static String buildAddFavoriteBody(long aid, long fid) {
        String mid = currentMid();
        String suffix = mid.length() >= 2 ? mid.substring(mid.length() - 2) : mid;
        return new NetWorkUtil.FormData()
                .put("rid", aid)
                .put("type", 2)
                .put("add_media_ids", String.valueOf(fid) + suffix)
                .put("del_media_ids", "")
                .put("csrf", currentCsrf())
                .toString();
    }


    public static int deleteFavorite(long aid, long fid) throws IOException, JSONException {
        if (aid <= 0 || fid <= 0) return -400;
        String url = "https://api.bilibili.com/x/v3/fav/resource/batch-del";
        String body = buildDeleteFavoriteBody(aid, fid);

        JSONObject result = new JSONObject(Objects.requireNonNull(
                NetWorkUtil.post(url, body, NetWorkUtil.webHeaders).body()).string());
        Log.e("debug-删除收藏", "code=" + result.optInt("code", -1)
                + ", message=" + result.optString("message", ""));
        JSONObject details = new JSONObject();
        details.put("operation", "delete_favorite");
        details.put("code", result.optInt("code", -1));
        details.put("message", result.optString("message", ""));
        DiagnosticLogManager.record("favorite_delete_result", details);
        return result.getInt("code");
    }

    static String buildDeleteFavoriteUrl(long aid, long fid) {
        return "https://api.bilibili.com/x/v3/fav/resource/batch-del"
                + new NetWorkUtil.FormData()
                .setUrlParam(true)
                .put("resources", aid + ":2")
                .put("media_id", favoriteMediaId(fid))
                .put("platform", "web")
                .put("csrf", currentCsrf());
    }

    static String buildDeleteFavoriteBody(long aid, long fid) {
        return new NetWorkUtil.FormData()
                .put("resources", aid + ":2")
                .put("media_id", favoriteMediaId(fid))
                .put("platform", "web")
                .put("csrf", currentCsrf())
                .toString();
    }

    private static String favoriteMediaId(long fid) {
        String value = String.valueOf(fid);
        String mid = currentMid();
        String suffix = mid.length() >= 2 ? mid.substring(mid.length() - 2) : mid;
        // Folder-list responses may already expose the gateway media id with
        // the account suffix. Do not append it a second time.
        if (!suffix.isEmpty() && value.endsWith(suffix)) return value;
        return value + suffix;
    }

    private static String currentCsrf() {
        if (SharedPreferencesUtil.sharedPreferences == null) return "";
        return SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
    }

    private static String currentMid() {
        if (SharedPreferencesUtil.sharedPreferences == null) return "0";
        return String.valueOf(SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
    }

}
