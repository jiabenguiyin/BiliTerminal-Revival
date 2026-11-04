package com.RobinNotBad.BiliClient.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import com.RobinNotBad.BiliClient.model.PlayerData;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;

/** References to one already-downloaded item; never contains a remote playback URL. */
public final class CachedPlaybackItem {
    public String title="", media="", danmaku="", subtitles="", folder="";
    public long aid, cid, collectionId;
    public int collectionIndex=-1, partIndex=-1;
    public boolean audioOnly;

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("title",title).put("media",media).put("danmaku",danmaku)
                .put("subtitles",subtitles).put("folder",folder).put("audio_only",audioOnly)
                .put("aid",aid).put("cid",cid).put("collection_id",collectionId)
                .put("collection_index",collectionIndex).put("part_index",partIndex);
    }
    public static CachedPlaybackItem fromJson(JSONObject json) throws JSONException {
        CachedPlaybackItem item=new CachedPlaybackItem();
        item.title=json.optString("title","");item.media=json.getString("media");
        item.danmaku=json.optString("danmaku","");item.subtitles=json.optString("subtitles","");
        item.folder=json.optString("folder","");item.audioOnly=json.optBoolean("audio_only");
        item.aid=json.optLong("aid");item.cid=json.optLong("cid");item.collectionId=json.optLong("collection_id");
        item.collectionIndex=json.optInt("collection_index",-1);item.partIndex=json.optInt("part_index",-1);
        if (!isLocalReference(item.media)) throw new JSONException("缓存队列包含非本地资源");
        if (!item.danmaku.isEmpty()&&!isLocalReference(item.danmaku)) throw new JSONException("弹幕不是本地资源");
        return item;
    }
    public static boolean isLocalReference(String reference) {
        return reference!=null&&(reference.startsWith("/")||reference.startsWith("content://")
                ||reference.startsWith("file://"));
    }
    public PlayerData load(Context context) throws IOException {
        if (!isLocalReference(media)) throw new IOException("缓存资源不可用");
        Uri uri=Uri.parse(media);
        if ("content".equals(uri.getScheme())) {
            try (AssetFileDescriptor file=context.getContentResolver().openAssetFileDescriptor(uri,"r")) {
                if(file==null)throw new IOException("缓存目录需要重新授权");
            } catch(SecurityException error) {throw new IOException("缓存目录需要重新授权",error);}
        } else {
            File file=new File("file".equals(uri.getScheme())?uri.getPath():media);
            if(!file.isFile()||!file.canRead())throw new IOException("缓存文件已删除或不可读");
        }
        PlayerData data=new PlayerData(PlayerData.TYPE_LOCAL);
        data.title=title;data.videoUrl=media;data.danmakuUrl=danmaku;data.danmakuCount=-1;
        data.aid=aid;data.cid=cid;
        return data;
    }
}
