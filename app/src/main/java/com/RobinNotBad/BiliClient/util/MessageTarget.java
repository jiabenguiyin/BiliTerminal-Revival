package com.RobinNotBad.BiliClient.util;

import org.json.JSONObject;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Decode notification destinations without treating a comment id as its video's oid. */
public final class MessageTarget {
    public String kind="",bvid="";
    public int type=1;
    public long oid,contentId,root,reply;
    private MessageTarget() { }

    public static long aidForVideoNavigation(long aid,String bvid){
        return bvid!=null&&!bvid.trim().isEmpty()?0:Math.max(0,aid);
    }

    public static MessageTarget like(JSONObject item) {
        MessageTarget target=new MessageTarget();
        target.kind=item.optString("type","");
        target.type=item.optInt("business_id",1);
        target.oid=positive(item.optLong("subject_id",0));
        target.root=positive(item.optLong("root_id",0));
        if("reply".equals(target.kind)||item.optString("business","").contains("评论")){
            target.kind="reply";
            target.reply=first(item.optLong("target_id",0),item.optLong("source_id",0),item.optLong("item_id",0));
        }
        MessageTarget nativeTarget=fromUri(item.optString("native_uri",""));
        MessageTarget webTarget=fromUri(item.optString("uri",""));
        target.merge(nativeTarget);target.mergeMissing(webTarget);
        if(webTarget.reply>0)target.reply=webTarget.reply;
        if(nativeTarget.root<=0&&webTarget.root>0)target.root=webTarget.root;
        if(target.kind.isEmpty())target.kind="video";
        if(target.root>0){target.kind="reply";if(target.reply<=0)target.reply=target.root;}
        if(target.oid<=0&&!"reply".equals(target.kind))target.oid=positive(item.optLong("item_id",0));
        if("reply".equals(target.kind)&&target.reply<=0)
            target.reply=first(item.optLong("target_id",0),item.optLong("source_id",0),item.optLong("item_id",0),target.root);
        if(target.type==0)target.type=1;
        return target;
    }
    private void merge(MessageTarget other){
        if(!other.kind.isEmpty()&&(!"reply".equals(kind)||"reply".equals(other.kind)))kind=other.kind;
        if(!other.bvid.isEmpty())bvid=other.bvid;
        if(other.oid>0){oid=other.oid;type=other.type;}
        if(other.contentId>0)contentId=other.contentId;
        if(other.root>0)root=other.root;
        if(other.reply>0)reply=other.reply;
    }
    private void mergeMissing(MessageTarget other){
        if(kind.isEmpty()||"reply".equals(other.kind))kind=other.kind.isEmpty()?kind:other.kind;
        if(bvid.isEmpty())bvid=other.bvid;
        if(oid<=0&&other.oid>0){oid=other.oid;type=other.type;}
        if(contentId<=0)contentId=other.contentId;
        if(root<=0)root=other.root;
        if(reply<=0)reply=other.reply;
    }
    private static long positive(long value){return Math.max(0,value);}
    private static long number(String value){
        try{return value!=null&&value.matches("[0-9]+")?positive(Long.parseLong(value)):0;}
        catch(Exception ignored){return 0;}
    }
    private static long first(long... values){for(long value:values)if(value>0)return value;return 0;}
    private static Map<String,String> query(URI uri){
        Map<String,String> result=new HashMap<>();
        String raw=uri.getRawQuery();
        if(raw!=null)for(String entry:raw.split("&")){
            int at=entry.indexOf('=');
            if(at>0)try{result.put(URLDecoder.decode(entry.substring(0,at),"UTF-8"),
                    URLDecoder.decode(entry.substring(at+1),"UTF-8"));}catch(Exception ignored){}
        }
        return result;
    }
    public static MessageTarget fromUri(String value){
        MessageTarget target=new MessageTarget();
        try{
            URI uri=new URI(value.trim());
            String scheme=uri.getScheme(),host=uri.getHost();
            if(scheme==null||host==null)return target;
            scheme=scheme.toLowerCase(Locale.ROOT);host=host.toLowerCase(Locale.ROOT);
            boolean nativeLink="bilibili".equals(scheme);
            boolean web=("https".equals(scheme)||"http".equals(scheme))
                    &&("bilibili.com".equals(host)||host.endsWith(".bilibili.com"));
            if(!nativeLink&&!web)return target;
            String path=uri.getPath()==null?"":uri.getPath();
            String[] parts=path.replaceAll("^/+|/+$","").split("/");
            Map<String,String> q=query(uri);
            if(nativeLink&&"comment".equals(host)&&parts.length>=4
                    &&("detail".equals(parts[0])||"msg_fold".equals(parts[0]))){
                target.kind="reply";target.type=(int)number(parts[1]);target.oid=number(parts[2]);target.root=number(parts[3]);
                target.reply=first(number(q.get("anchor")),number(q.get("comment_secondary_id")),parts.length>4?number(parts[4]):0,target.root);
            }else if(web&&path.endsWith("/comment/sub")){
                target.kind="reply";target.type=(int)number(q.get("pageType"));target.oid=number(q.get("oid"));
                target.root=number(q.get("root"));target.reply=first(number(q.get("comment_secondary_id")),target.root);
            }else if(nativeLink&&"video".equals(host)||web&&parts.length>=2&&"video".equals(parts[0])){
                target.kind="video";target.type=1;
                String id=nativeLink?parts[0]:parts[1];
                if(id.matches("(?i)BV[A-Za-z0-9]{10}"))target.bvid="BV"+id.substring(2);
                else target.oid=number(id.replaceFirst("(?i)^av",""));
                if(target.oid==0&&target.bvid.isEmpty())return new MessageTarget();
            }else if(nativeLink&&"following".equals(host)&&parts.length>=2&&"detail".equals(parts[0])
                    ||web&&"t.bilibili.com".equals(host)){
                target.kind="dynamic";target.type=17;target.oid=number(nativeLink?parts[1]:parts[0]);target.contentId=target.oid;
            }else return target;
            target.root=first(target.root,number(q.get("comment_root_id")),number(q.get("root")));
            String fragment=uri.getFragment();
            long fragmentReply=fragment!=null&&fragment.matches("reply[0-9]+")?number(fragment.substring(5)):0;
            target.reply=first(target.reply,number(q.get("comment_secondary_id")),number(q.get("rpid")),fragmentReply,target.root);
            if(target.reply>0)target.kind="reply";
            if(q.containsKey("enterUri")){
                MessageTarget nested=fromUriWithoutNested(q.get("enterUri"));
                if(nested.contentId>0)target.contentId=nested.contentId;
            }
        }catch(Exception ignored){ }
        return target;
    }
    private static MessageTarget fromUriWithoutNested(String value){
        // Only the nested content path is needed; ignore any further navigation parameters.
        if(value==null)return new MessageTarget();
        int end=value.indexOf('?');return fromUri(end<0?value:value.substring(0,end));
    }
}
