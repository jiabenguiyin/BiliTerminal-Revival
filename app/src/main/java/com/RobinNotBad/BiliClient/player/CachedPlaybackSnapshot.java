package com.RobinNotBad.BiliClient.player;

import android.content.Context;
import android.content.Intent;
import com.RobinNotBad.BiliClient.model.LocalVideo;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.json.JSONArray;
import java.io.*;
import java.util.*;

/** A small Intent token references a private queue file, avoiding Binder-size limits. */
public final class CachedPlaybackSnapshot {
    public static final String TOKEN="cached_playback_queue";
    public static final String INDEX="cached_playback_index";
    public final List<CachedPlaybackItem> items;
    private CachedPlaybackSnapshot(List<CachedPlaybackItem> items){this.items=Collections.unmodifiableList(new ArrayList<>(items));}

    public static String create(Context context,List<LocalVideo> videos,LocalVideo selected,String media)throws Exception {
        CachedPlaybackItem current=null;
        for(CachedPlaybackItem item:selected.playbackItems)if(item.media.equals(media)){current=item;break;}
        if(current==null)throw new IOException("缓存内容已变更");
        boolean all=SharedPreferencesUtil.getInt(SharedPreferencesUtil.PLAYER_CACHED_PLAYBACK_SCOPE,0)==1;
        List<CachedPlaybackItem> entries=select(videos,selected,current,all);
        JSONArray json=new JSONArray();for(CachedPlaybackItem item:entries)json.put(item.toJson());
        byte[] bytes=json.toString().getBytes("UTF-8");
        if(bytes.length>2*1024*1024)throw new IOException("缓存歌单过大，请选择当前合集或分P");
        File dir=new File(context.getCacheDir(),"playback-queues");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法保存缓存歌单");
        File[] old=dir.listFiles((d,name)->name.endsWith(".json"));
        if(old!=null&&old.length>=16){Arrays.sort(old,(a,b)->a.lastModified()<b.lastModified()?-1:a.lastModified()==b.lastModified()?0:1);for(int i=0;i<old.length-15;i++)old[i].delete();}
        String token=UUID.randomUUID().toString();
        try(OutputStream out=new FileOutputStream(new File(dir,token+".json"))){out.write(bytes);}
        return token;
    }

    public static List<CachedPlaybackItem> select(List<LocalVideo> videos,LocalVideo selected,CachedPlaybackItem current,boolean all) {
        ArrayList<CachedPlaybackItem> result=new ArrayList<>();
        if(all||current.collectionId>0&&current.collectionIndex>=0) {
            for(LocalVideo video:videos)for(CachedPlaybackItem item:video.playbackItems)
                if(all||item.collectionId==current.collectionId&&item.collectionIndex>=0)result.add(item);
            if(!all)Collections.sort(result,(a,b)->{
                int order=a.collectionIndex<b.collectionIndex?-1:a.collectionIndex==b.collectionIndex?0:1;
                return order!=0?order:a.partIndex<b.partIndex?-1:a.partIndex==b.partIndex?0:1;
            });
        } else result.addAll(selected.playbackItems);
        LinkedHashMap<String,CachedPlaybackItem> unique=new LinkedHashMap<>();
        for(CachedPlaybackItem item:result)if(CachedPlaybackItem.isLocalReference(item.media))unique.put(item.media,item);
        return new ArrayList<>(unique.values());
    }

    public static CachedPlaybackSnapshot read(Context context,String token)throws Exception {
        if(token==null||!token.matches("[0-9a-fA-F-]{36}"))throw new IOException("缓存歌单标识无效");
        File file=new File(new File(context.getCacheDir(),"playback-queues"),token+".json");
        if(!file.isFile()||file.length()>2*1024*1024)throw new IOException("缓存歌单已过期，请返回缓存列表重新播放");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>2*1024*1024)throw new IOException("缓存歌单过大");bytes.write(b,0,n);}}
        JSONArray array=new JSONArray(bytes.toString("UTF-8"));ArrayList<CachedPlaybackItem> items=new ArrayList<>();
        for(int i=0;i<array.length();i++) {
            CachedPlaybackItem item=CachedPlaybackItem.fromJson(array.getJSONObject(i));
            try { item.load(context); items.add(item); } catch(IOException|SecurityException unavailable) { }
        }
        return new CachedPlaybackSnapshot(items);
    }
    public int indexOf(String media){for(int i=0;i<items.size();i++)if(items.get(i).media.equals(media))return i;return -1;}
    public CollectionPlaybackQueue queue(){long[] ids=new long[items.size()];for(int i=0;i<ids.length;i++)ids[i]=i+1;return new CollectionPlaybackQueue(0,ids);}

    public static void start(Context context,Intent intent,List<LocalVideo> videos,LocalVideo selected,String media) {
        if(!"terminalPlayer".equals(SharedPreferencesUtil.getString("player","null"))) {
            context.startActivity(intent);
            return;
        }
        List<LocalVideo> snapshot = new ArrayList<>(videos);
        com.RobinNotBad.BiliClient.util.CenterThreadPool.run(()->{
            try { intent.putExtra(TOKEN,create(context,snapshot,selected,media)); }
            catch(Exception error) { com.RobinNotBad.BiliClient.util.Logu.w("cache-playlist","歌单不可用："+error.getClass().getSimpleName()); }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                if(context instanceof android.app.Activity&&((android.app.Activity)context).isFinishing())return;
                try { context.startActivity(intent); }
                catch(Exception error){com.RobinNotBad.BiliClient.util.MsgUtil.showMsg("无法打开播放器");}
            });
        });
    }
}
