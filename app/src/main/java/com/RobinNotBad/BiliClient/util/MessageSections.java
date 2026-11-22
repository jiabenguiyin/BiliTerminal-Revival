package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.model.MessageCard;
import org.json.*;
import java.util.*;

/** Preserve the server's latest/history boundary; opening a screen is not a local read marker. */
public final class MessageSections {
    private MessageSections() { }
    public static final class Likes {
        public final JSONArray items=new JSONArray();
        public final Set<Long> latestIds=new HashSet<>();
        public final boolean known;
        public final JSONObject cursor;
        private Likes(JSONObject data,boolean first)throws JSONException {
            JSONObject latest=data.optJSONObject("latest"),total=data.optJSONObject("total");
            if(latest==null&&total==null)throw new JSONException("获赞列表数据不完整");
            known=latest!=null;
            Set<Long> seen=new HashSet<>();
            if(first&&latest!=null)add(latest.optJSONArray("items"),true,seen);
            if(total!=null)add(total.optJSONArray("items"),false,seen);
            JSONObject nested=total==null?null:total.optJSONObject("cursor");
            cursor=nested==null?data.optJSONObject("cursor"):nested;
        }
        private void add(JSONArray rows,boolean newest,Set<Long> seen)throws JSONException {
            if(rows==null)return;
            for(int i=0;i<rows.length();i++){
                JSONObject item=rows.getJSONObject(i);long id=item.getLong("id");
                if(id<=0)throw new JSONException("消息 ID 无效");
                if(newest)latestIds.add(id);
                if(seen.add(id))items.put(item);
            }
        }
    }
    public static Likes likes(JSONObject data,boolean first)throws JSONException {return new Likes(data,first);}
    public static void applyBoundary(MessageCard card,JSONObject data){
        if(!data.has("last_view_at")||data.isNull("last_view_at"))return;
        long viewed=data.optLong("last_view_at",-1);
        if(viewed<0)return;
        card.readStateKnown=true;card.isNew=card.timeStamp>viewed;
    }
}
