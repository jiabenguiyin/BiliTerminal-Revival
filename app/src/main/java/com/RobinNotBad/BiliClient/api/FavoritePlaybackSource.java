package com.RobinNotBad.BiliClient.api;

import android.content.Context;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackQueue;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Paged online queues. Original favorite tokens remain readable; Intents contain only a file token. */
public class FavoritePlaybackSource {
    public static final String TOKEN = "favorite_playback_queue";
    public static final String EXPLICIT = "explicit_online_queue";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    public interface RequestGuard { void check() throws Exception; }

    public static final class Session {
        public long account, owner, folder;
        public boolean all, bottom;
        public String keyword = "";
        public String favoriteOrder = "mtime";
        public String kind = "favorites";
        public boolean uploadOldest;
        public boolean isUploads() { return "uploads".equals(kind); }
        public String label() { return isUploads() ? "投稿" : "收藏"; }
        public int nextPage;
        public ApiResult cursor = new ApiResult();
        public final ArrayList<Long> ids = new ArrayList<>();

        public static Session capture(long account, long owner, long folder, boolean all, String keyword,
                                      int page, ApiResult cursor, boolean bottom, List<VideoCard> cards) throws Exception {
            Session session = new Session();
            session.account = account; session.owner = owner; session.folder = folder; session.all = all;
            session.keyword = keyword == null ? "" : keyword.trim();
            session.nextPage = page + 1; session.cursor = cursor; session.bottom = bottom;
            session.add(cards);
            return fromJson(session.toJson()); // Copy the accepted list cursor before the list can refresh.
        }

        private void add(List<VideoCard> cards) {
            LinkedHashSet<Long> unique = new LinkedHashSet<>(ids);
            for (VideoCard card : cards) if (playableCard(card)) unique.add(card.aid);
            ids.clear(); ids.addAll(unique);
        }

        public static Session uploads(long account, long owner, boolean oldest, int page, boolean bottom,
                                      List<VideoCard> videos) throws Exception {
            Session session = new Session();
            session.kind = "uploads"; session.account = account; session.owner = owner;
            session.uploadOldest = oldest; session.nextPage = page + (oldest ? -1 : 1); session.bottom = bottom;
            ArrayList<VideoCard> ordered = new ArrayList<>(videos);
            if (oldest) Collections.reverse(ordered);
            session.add(ordered);
            return fromJson(session.toJson());
        }

        public CollectionPlaybackQueue queue() {
            long[] values = new long[ids.size()];
            for (int i = 0; i < values.length; i++) values[i] = ids.get(i);
            return new CollectionPlaybackQueue(0, values);
        }

        public JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject().put("schema", 1).put("account", account).put("owner", owner)
                    .put("folder", folder).put("all", all).put("keyword", keyword).put("page", nextPage)
                    .put("bottom", bottom).put("ids", new JSONArray(ids)).put("business", cursor.business)
                    .put("kind", kind).put("upload_oldest", uploadOldest).put("favorite_order",favoriteOrder);
            if (cursor.result instanceof AllFavoriteVideosPager.Cursor)
                json.put("merge", ((AllFavoriteVideosPager.Cursor) cursor.result).toJson());
            return json;
        }

        public static Session fromJson(JSONObject json) throws Exception {
            if (json.getInt("schema") != 1) throw new IOException("收藏歌单版本无效");
            Session session = new Session();
            session.account = json.getLong("account"); session.owner = json.getLong("owner");
            session.folder = json.getLong("folder"); session.all = json.getBoolean("all");
            session.kind = json.optString("kind", "favorites");
            session.uploadOldest = json.optBoolean("upload_oldest", false);
            if (!session.isUploads() && !"favorites".equals(session.kind)) throw new IOException("播放列表类型无效");
            session.keyword = json.getString("keyword"); session.nextPage = json.getInt("page");
            session.favoriteOrder=com.RobinNotBad.BiliClient.util.FavoriteVideoOrder.normalize(json.optString("favorite_order","mtime"));
            session.bottom = json.getBoolean("bottom"); session.cursor.business = json.optString("business", "");
            if (json.has("merge")) session.cursor.result = AllFavoriteVideosPager.Cursor.fromJson(json.getJSONObject("merge"));
            LinkedHashSet<Long> unique = new LinkedHashSet<>();
            JSONArray ids = json.getJSONArray("ids");
            for (int i = 0; i < ids.length(); i++) if (ids.getLong(i) > 0) unique.add(ids.getLong(i));
            session.ids.addAll(unique);
            if (session.owner <= 0 || (session.isUploads() ? session.nextPage < 0 || !session.bottom && session.nextPage < 1
                    : session.nextPage < 2 || !session.all && session.folder <= 0
                    || session.all && session.keyword.isEmpty() && !session.bottom && session.cursor.result == null))
                throw new IOException("播放列表已失效，请从列表重新开始");
            return session;
        }
    }

    public static boolean playableCard(VideoCard card) {
        return card != null && card.aid > 0 && (card.type == null || "video".equals(card.type))
                && !"已失效视频".equals(card.title) && !"视频已失效".equals(card.title);
    }

    protected ApiResult page(Session session, List<VideoCard> output, RequestGuard guard) throws Exception {
        guard.check();
        if (session.isUploads()) {
            UserInfoApi.UserVideoPage result = UserInfoApi.getUserVideoPage(session.owner, session.nextPage, output);
            if (session.uploadOldest) {
                Collections.reverse(output);
                result.isBottom = session.nextPage <= 1;
            }
            return result;
        }
        if (session.all && session.keyword.isEmpty()) {
            return AllFavoriteVideosPager.load((AllFavoriteVideosPager.Cursor) session.cursor.result,
                    new AllFavoriteVideosPager.Source() {
                        private int requests;
                        @Override public List<Long> folders() throws IOException {
                            throw new IOException("收藏合并游标已失效");
                        }
                        @Override public JSONObject page(long folder, int page) throws IOException, org.json.JSONException {
                            try { guard.check(); }
                            catch (IOException error) { throw error; }
                            catch (Exception error) { throw new IOException("收藏加载已取消", error); }
                            if (++requests > 100) throw new IOException("收藏分页未前进，请刷新收藏夹后重试");
                            return com.RobinNotBad.BiliClient.util.NetWorkUtil.getJson(
                                    FavoriteApi.buildFavoriteVideosUrl(folder, page, "", false));
                        }
                    }, output);
        }
        long folder = session.all ? Long.parseLong(session.cursor.business) : session.folder;
        ApiResult result = FavoriteApi.getFavoriteVideos(folder, session.nextPage, session.keyword, session.all, output,session.favoriteOrder);
        result.business = String.valueOf(folder);
        return result;
    }

    /** A failed request never changes the accepted cursor or membership. */
    public Session nextPage(Session accepted) throws Exception {
        return nextPage(accepted, () -> { });
    }

    public Session nextPage(Session accepted, RequestGuard guard) throws Exception {
        Session copy = Session.fromJson(accepted.toJson());
        if (copy.bottom) return copy;
        List<VideoCard> output = new ArrayList<>();
        guard.check();
        ApiResult result = page(copy, output, guard);
        guard.check();
        if (result.code != 0) throw new IOException(copy.label() + "列表加载失败（" + result.code + "）");
        copy.add(output); copy.cursor = result;
        copy.nextPage += copy.isUploads() && copy.uploadOldest ? -1 : 1;
        copy.bottom = result.isBottom;
        return copy;
    }

    private static File file(Context context, String token) throws IOException {
        if (token == null || !token.matches("[0-9a-f-]{36}")) throw new IOException("收藏歌单标识无效");
        return new File(new File(context.getFilesDir(), "favorite-playlists"), token + ".json");
    }

    public static String create(Context context, Session session) throws Exception {
        File dir = new File(context.getFilesDir(), "favorite-playlists");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("无法保存收藏歌单");
        File[] old = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (old != null) {
            Arrays.sort(old, (a, b) -> a.lastModified() < b.lastModified() ? -1 : a.lastModified() == b.lastModified() ? 0 : 1);
            for (int i = 0; i < old.length; i++)
                if (i < old.length - 31 || System.currentTimeMillis() - old[i].lastModified() > 7L * 86400000) old[i].delete();
        }
        String token = UUID.randomUUID().toString(); save(context, token, session); return token;
    }

    public static void save(Context context, String token, Session session) throws Exception {
        byte[] bytes = session.toJson().toString().getBytes("UTF-8");
        if (bytes.length > MAX_BYTES) throw new IOException("收藏歌单过大，请改用单个收藏夹播放");
        File target = file(context, token), temp = new File(target.getPath() + ".tmp-" + UUID.randomUUID());
        try (FileOutputStream out = new FileOutputStream(temp)) { out.write(bytes); out.getFD().sync(); }
        if (!temp.renameTo(target)) { temp.delete(); throw new IOException("无法保存收藏歌单"); }
    }

    public static Session read(Context context, String token, long account) throws Exception {
        File file = file(context, token);
        if (!file.isFile() || file.length() > MAX_BYTES) throw new IOException("收藏歌单已过期，请从收藏夹重新播放");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) >= 0) {
                if (bytes.size() + n > MAX_BYTES) throw new IOException("收藏歌单过大");
                bytes.write(buffer, 0, n);
            }
        }
        Session session = Session.fromJson(new JSONObject(bytes.toString("UTF-8")));
        if (session.account != account) throw new IOException("账号已切换，请从收藏夹重新播放");
        return session;
    }
}
