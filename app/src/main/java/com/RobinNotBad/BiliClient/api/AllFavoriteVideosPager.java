package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Merges paged folder streams; type=1 only works with a nonempty search keyword. */
final class AllFavoriteVideosPager {
    static final int PAGE_SIZE = 20;

    interface Source {
        List<Long> folders() throws IOException, JSONException;
        JSONObject page(long mediaId, int page) throws IOException, JSONException;
    }

    private static final class Entry {
        final VideoCard video;
        final long time;

        Entry(VideoCard video, long time) { this.video = video; this.time = time; }
    }

    private static final class Folder {
        final long id;
        int nextPage = 1;
        List<Entry> entries = new ArrayList<>();
        int index;
        boolean exhausted;

        Folder(long id) { this.id = id; }

        Folder(Folder other) {
            id = other.id;
            nextPage = other.nextPage;
            entries = other.entries;
            index = other.index;
            exhausted = other.exhausted;
        }
    }

    static final class Cursor {
        final List<Folder> folders = new ArrayList<>();
        final Set<Long> emitted = new HashSet<>();

        Cursor() { }

        Cursor(Cursor other) {
            for (Folder folder : other.folders) folders.add(new Folder(folder));
            emitted.addAll(other.emitted);
        }

        // Private playback snapshots need only IDs and merge timestamps, not card images or text.
        JSONObject toJson() throws JSONException {
            JSONArray streams = new JSONArray();
            for (Folder folder : folders) {
                JSONArray pending = new JSONArray();
                for (int i = folder.index; i < folder.entries.size(); i++) {
                    Entry entry = folder.entries.get(i);
                    pending.put(new JSONArray().put(entry.video.aid).put(entry.time)
                            .put(FavoritePlaybackSource.playableCard(entry.video)));
                }
                streams.put(new JSONObject().put("id", folder.id).put("page", folder.nextPage)
                        .put("end", folder.exhausted).put("pending", pending));
            }
            return new JSONObject().put("folders", streams).put("seen", new JSONArray(emitted));
        }

        static Cursor fromJson(JSONObject json) throws JSONException {
            Cursor cursor = new Cursor();
            JSONArray streams = json.getJSONArray("folders"), seen = json.getJSONArray("seen");
            for (int i = 0; i < streams.length(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                Folder folder = new Folder(stream.getLong("id"));
                folder.nextPage = stream.getInt("page");
                folder.exhausted = stream.getBoolean("end");
                JSONArray pending = stream.getJSONArray("pending");
                for (int j = 0; j < pending.length(); j++) {
                    JSONArray entry = pending.getJSONArray(j);
                    VideoCard card = new VideoCard();
                    card.aid = entry.getLong(0);
                    if (!entry.optBoolean(2, true)) card.title = "已失效视频";
                    folder.entries.add(new Entry(card, entry.getLong(1)));
                }
                cursor.folders.add(folder);
            }
            for (int i = 0; i < seen.length(); i++) cursor.emitted.add(seen.getLong(i));
            return cursor;
        }
    }

    static ApiResult load(Cursor previous, Source source, List<VideoCard> output)
            throws IOException, JSONException {
        // Work on a copy: failed/retried or superseded requests cannot advance the accepted cursor.
        Cursor cursor = previous == null ? new Cursor() : new Cursor(previous);
        if (previous == null) {
            for (long id : new LinkedHashSet<>(source.folders())) {
                if (id <= 0) throw new JSONException("收藏夹 ID 无效");
                cursor.folders.add(new Folder(id));
            }
        }

        List<VideoCard> page = new ArrayList<>();
        while (page.size() < PAGE_SIZE) {
            Folder newest = null;
            for (Folder folder : cursor.folders) {
                while (folder.index >= folder.entries.size() && !folder.exhausted) {
                    JSONObject response = source.page(folder.id, folder.nextPage);
                    List<VideoCard> cards = new ArrayList<>();
                    List<Long> times = new ArrayList<>();
                    ApiResult result = FavoriteApi.parseFavoriteVideos(response, cards, times);
                    if (result.code != 0) return result;
                    List<Entry> entries = new ArrayList<>();
                    for (int i = 0; i < cards.size(); i++) entries.add(new Entry(cards.get(i), times.get(i)));
                    folder.entries = entries;
                    folder.index = 0;
                    folder.nextPage++;
                    folder.exhausted = result.isBottom;
                }
                if (folder.index < folder.entries.size() && (newest == null
                        || folder.entries.get(folder.index).time > newest.entries.get(newest.index).time)) {
                    newest = folder;
                }
            }
            if (newest == null) break;
            Entry entry = newest.entries.get(newest.index++);
            if (cursor.emitted.add(entry.video.aid)) page.add(entry.video);
        }

        boolean bottom = true;
        for (Folder folder : cursor.folders) {
            if (!folder.exhausted || folder.index < folder.entries.size()) { bottom = false; break; }
        }
        output.addAll(page);
        return new ApiResult(0, "").setBottom(bottom).setResult(cursor);
    }
}
