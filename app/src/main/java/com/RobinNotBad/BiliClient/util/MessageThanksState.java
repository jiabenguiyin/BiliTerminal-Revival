package com.RobinNotBad.BiliClient.util;
import org.json.JSONArray;
import java.util.LinkedHashSet;
/** Local confirmation of manually sent messages, isolated by account. */
public final class MessageThanksState {
    private MessageThanksState() { }
    private static String key(long account){return "message_thanks_v1_"+account;}
    private static LinkedHashSet<String> read(long account){
        LinkedHashSet<String> result=new LinkedHashSet<>();
        try{JSONArray array=new JSONArray(SharedPreferencesUtil.getString(key(account),"[]"));
            for(int i=0;i<array.length();i++)result.add(array.getString(i));
        }catch(Exception ignored){}return result;
    }
    public static synchronized boolean sent(long account,long notice,long recipient){
        return account>0&&notice>0&&recipient>0&&read(account).contains(notice+":"+recipient);
    }
    public static synchronized void confirmSent(long account,long notice,long recipient){
        if(account<=0||notice<=0||recipient<=0||account!=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0))return;
        LinkedHashSet<String> rows=read(account);rows.add(notice+":"+recipient);
        while(rows.size()>512)rows.remove(rows.iterator().next());
        JSONArray array=new JSONArray();for(String row:rows)array.put(row);
        SharedPreferencesUtil.putString(key(account),array.toString());
    }
}
