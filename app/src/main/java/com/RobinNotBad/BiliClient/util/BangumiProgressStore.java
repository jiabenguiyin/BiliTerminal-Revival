package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.model.Bangumi;
import org.json.*;

/** Account/season/episode identity is stored with the position; never borrow another episode's time. */
public final class BangumiProgressStore {
    private static String key(long season) { return "pgc_recent_"+SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)+"_"+season; }
    public static void save(long account,long season,long episode,long cid,long position) {
        if(account!=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)||season<=0||episode<=0||cid<=0||position<0)return;
        try { SharedPreferencesUtil.putString(key(season),new JSONObject().put("ep",episode).put("cid",cid)
                .put("ms",Math.min(position,86400000L)).toString()); } catch(Exception ignored) { }
    }
    public static long episode(long season) { return read(season).optLong("ep",0); }
    public static long position(long season,long episode,long cid) {
        JSONObject x=read(season);
        return x.optLong("ep") == episode && x.optLong("cid")==cid ? Math.max(0,Math.min(86400000L,x.optLong("ms",0))) : 0;
    }
    public static int[] selection(Bangumi bangumi,long episode) {
        if(bangumi!=null&&bangumi.sectionList!=null)for(int s=0;s<bangumi.sectionList.size();s++)
            for(int e=0;e<bangumi.sectionList.get(s).episodeList.size();e++)
                if(bangumi.sectionList.get(s).episodeList.get(e).id==episode)return new int[]{s,e};
        return new int[]{0,0};
    }
    private static JSONObject read(long season) {
        try { return new JSONObject(SharedPreferencesUtil.getString(key(season),"{}")); }
        catch(Exception ignored) { return new JSONObject(); }
    }
}
