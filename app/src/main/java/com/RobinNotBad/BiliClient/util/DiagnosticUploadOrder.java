package com.RobinNotBad.BiliClient.util;

import org.json.JSONObject;
import java.util.*;

/** Reorders only a capped manual upload; every unselected record stays in the queue. */
public final class DiagnosticUploadOrder {
    private DiagnosticUploadOrder() { }
    public static List<String> prioritize(List<String> lines,int limit) {
        if(limit<=0||lines.size()<=limit)return new ArrayList<>(lines);
        boolean[] chosen=new boolean[lines.size()];String[] names=new String[lines.size()];int count=0;
        for(int i=0;i<lines.size();i++)try{names[i]=new JSONObject(lines.get(i)).optString("name");}catch(Exception ignored){names[i]="";}
        // Keep the last measured player geometry even after a long run of unrelated debug output.
        for(String event:new String[]{"player_layout","player_control_touch"})for(int i=names.length-1;i>=0;i--)
            if(event.equals(names[i])){if(!chosen[i]&&count<limit){chosen[i]=true;count++;}break;}
        int reserved=Math.min(limit,Math.min(32,Math.max(2,limit/2)));
        for(int i=names.length-1;i>=0&&count<reserved;i--)if(!chosen[i]&&priority(names[i])){chosen[i]=true;count++;}
        for(int i=names.length-1;i>=0&&count<limit;i--)if(!chosen[i]){chosen[i]=true;count++;}
        ArrayList<String> out=new ArrayList<>(lines.size());
        for(int i=0;i<lines.size();i++)if(chosen[i])out.add(lines.get(i));
        for(int i=0;i<lines.size();i++)if(!chosen[i])out.add(lines.get(i));
        return out;
    }
    private static boolean priority(String name) {
        return "player_layout".equals(name)||"player_control_touch".equals(name)||"player_action".equals(name)
                ||"app_start".equals(name)||"device_profile".equals(name)||"uncaught_exception".equals(name)
                ||"diagnostic_upload_failed".equals(name)||"diagnostic_upload_exception".equals(name);
    }
}
