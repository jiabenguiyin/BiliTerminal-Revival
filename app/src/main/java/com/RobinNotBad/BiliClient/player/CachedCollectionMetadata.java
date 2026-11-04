package com.RobinNotBad.BiliClient.player;

import android.content.Context;
import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.model.Collection;
import com.RobinNotBad.BiliClient.model.DownloadSection;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.LinkedHashSet;

/** Minimal offline identity/order metadata, written alongside completed cache files. */
public final class CachedCollectionMetadata {
    public static final String FILE_NAME=".playback_metadata.json";
    private CachedCollectionMetadata() { }

    public static void remember(VideoInfo info) {
        if(info==null||info.aid<=0)return;
        try {
            JSONObject data=new JSONObject().put("aid",info.aid);
            long collectionId=0;int collectionIndex=-1;
            if(info.collection!=null) {
                LinkedHashSet<Long> ids=new LinkedHashSet<>();
                if(info.collection.sections!=null)for(Collection.Section section:info.collection.sections) {
                    if(section==null||section.episodes==null)continue;
                    for(Collection.Episode episode:section.episodes)if(episode!=null) {
                        long aid=episode.aid>0?episode.aid:episode.arc==null?0:episode.arc.aid;
                        if(aid>0)ids.add(aid);
                    }
                }
                if(ids.isEmpty()&&info.collection.cards!=null)
                    for(com.RobinNotBad.BiliClient.model.VideoCard card:info.collection.cards)
                        if(card!=null&&card.aid>0)ids.add(card.aid);
                int i=0;for(long aid:ids){if(aid==info.aid){collectionIndex=i;break;}i++;}
                if(collectionIndex>=0)collectionId=info.collection.id;
            }
            data.put("collection_id",collectionId).put("collection_index",collectionIndex);
            JSONArray cids=new JSONArray();if(info.cids!=null)for(long cid:info.cids)cids.put(cid);
            data.put("cids",cids);
            File dir=new File(BiliTerminal.context.getFilesDir(),"download-identities");
            if(!dir.isDirectory()&&!dir.mkdirs())return;
            try(OutputStream out=new FileOutputStream(new File(dir,info.aid+".json"))) {
                out.write(data.toString().getBytes("UTF-8"));
            }
        } catch(Exception error){Logu.w("cache-playlist","保存缓存顺序失败："+error.getClass().getSimpleName());}
    }

    public static void writeCompleted(Context context,DownloadSection task,VideoStorageUtil.Node folder) {
        try {
            JSONObject data=new JSONObject();
            File source=new File(new File(context.getFilesDir(),"download-identities"),task.aid+".json");
            if(source.isFile())try(InputStream in=new FileInputStream(source)){data=readJson(in);}
            int page=-1;JSONArray cids=data.optJSONArray("cids");
            if(cids!=null)for(int i=0;i<cids.length();i++)if(cids.optLong(i)==task.cid){page=i;break;}
            data.remove("cids");data.put("aid",task.aid).put("cid",task.cid).put("part_index",page);
            try(OutputStream out=folder.getOrCreateFile(FILE_NAME,"application/json").openOutput(false)) {
                out.write(data.toString().getBytes("UTF-8"));
            }
        } catch(Exception error){Logu.w("cache-playlist","缓存顺序信息不可用："+error.getClass().getSimpleName());}
    }

    public static void readInto(VideoStorageUtil.Node folder,CachedPlaybackItem item) {
        try {
            VideoStorageUtil.Node file=folder.find(FILE_NAME);if(file==null)return;
            JSONObject data;try(InputStream in=file.openInput()){data=readJson(in);}
            item.aid=data.optLong("aid");item.cid=data.optLong("cid");
            item.collectionId=data.optLong("collection_id");item.collectionIndex=data.optInt("collection_index",-1);
            item.partIndex=data.optInt("part_index",-1);
        } catch(Exception ignored) { }
    }
    private static JSONObject readJson(InputStream input)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[2048];int n;
        while((n=input.read(block))!=-1){if(bytes.size()+n>65536)throw new IOException("缓存信息过大");bytes.write(block,0,n);}
        return new JSONObject(bytes.toString("UTF-8"));
    }
}
