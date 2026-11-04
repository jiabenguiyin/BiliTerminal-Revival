package com.RobinNotBad.BiliClient.player;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Official PGC clip_info_list fields, in seconds. Field reference:
 * https://github.com/cyq114514/Re-BiliTerminal/blob/main/app/src/main/java/com/RobinNotBad/BiliClient/model/ClipInfo.java
 * Re-BiliTerminal is GPL-3.0; this bounded parser and policy are implemented for Revival. */
public final class PlaybackSegments {
    public static final String MODE = "player_pgc_skip_mode";
    public static final int OFF = 0, MANUAL = 1, AUTO_ONCE = 2;
    public static final class Segment {
        public final boolean opening;
        public final long startMs, endMs;
        public Segment(boolean opening, long start, long end) { this.opening=opening; startMs=start; endMs=end; }
        public String label() { return opening ? "片头" : "片尾"; }
        String key() { return opening + ":" + startMs + ":" + endMs; }
    }
    public static List<Segment> parse(String json) {
        List<Segment> out=new ArrayList<>(); Set<String> unique=new HashSet<>();
        if(json==null || json.length()>16384)return out;
        try {
            JSONArray list=new JSONArray(json);
            for(int i=0;i<Math.min(32,list.length());i++) {
                JSONObject x=list.optJSONObject(i);if(x==null)continue;
                String type=x.optString("clipType");
                if(!"CLIP_TYPE_OP".equals(type)&&!"CLIP_TYPE_ED".equals(type))continue;
                double start=x.optDouble("start",-1),end=x.optDouble("end",-1);
                if(Double.isNaN(start)||Double.isNaN(end)||start<0||end<=start||end>86400)continue;
                Segment segment=new Segment("CLIP_TYPE_OP".equals(type),Math.round(start*1000),Math.round(end*1000));
                if(segment.endMs>segment.startMs&&unique.add(segment.key()))out.add(segment);
            }
        } catch(Exception ignored) { }
        Collections.sort(out,(a,b)->a.startMs<b.startMs?-1:a.startMs==b.startMs?0:1);return out;
    }
    public static String normalize(JSONArray clips) {
        JSONArray out=new JSONArray();
        for(Segment s:parse(clips==null?"[]":clips.toString()))try{
            out.put(new JSONObject().put("clipType",s.opening?"CLIP_TYPE_OP":"CLIP_TYPE_ED")
                    .put("start",s.startMs/1000d).put("end",s.endMs/1000d));
        }catch(Exception ignored) { }
        return out.toString();
    }
    private final List<Segment> segments;
    private final Set<String> attempted=new HashSet<>();
    private long lastAttempt=-1;
    public PlaybackSegments(String json) { segments=parse(json); }
    public void update(String json) { segments.clear(); segments.addAll(parse(json)); }
    public Segment at(long position,long duration) {
        if(duration<=0)return null;
        for(Segment s:segments)if(s.endMs<=duration&&position>=s.startMs&&position<s.endMs)return s;
        return null;
    }
    /** One seek per clip per episode, including failed seeks and decoder position rollbacks. */
    public long automaticTarget(long position,long duration,long elapsed,int mode) {
        if(mode!=AUTO_ONCE)return -1;
        Segment s=at(position,duration);
        if(s==null||s.endMs-position<3000||attempted.contains(s.key())
                ||lastAttempt>=0&&elapsed-lastAttempt<2000)return -1;
        attempted.add(s.key());lastAttempt=elapsed;return s.endMs;
    }
    public static int normalizeMode(int mode) { return mode>=OFF&&mode<=AUTO_ONCE?mode:MANUAL; }
}
