package com.RobinNotBad.BiliClient.api;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import org.json.*;
import java.io.IOException;
import java.net.URI;

/** Cache only an authorized, complete, unencrypted progressive episode stream. */
public final class BangumiDownloadApi {
    private BangumiDownloadApi() { }
    public static void resolve(PlayerData data)throws IOException,JSONException {
        if(data.episodeId<=0||data.aid<=0||data.cid<=0)throw new JSONException("番剧剧集信息不完整");
        data.videoUrl="";data.videoBackupUrls.clear();
        String query=new NetWorkUtil.FormData().setUrlParam(true).put("aid",data.aid).put("cid",data.cid)
                .put("ep_id",data.episodeId).put("qn",data.qn>0?data.qn:32).put("fnval",1)
                .put("fnver",0).put("download",1).put("season_type",Math.max(1,data.seasonType)).put("platform","pc").toString();
        JSONObject body=NetWorkUtil.getJson("https://api.bilibili.com/pgc/player/web/v2/playurl"+query);
        JSONObject result=body.optJSONObject("result");
        JSONObject video=result==null?null:result.optJSONObject("video_info");
        if(video==null)video=result;
        if(video!=null&&(enabled(video,"is_preview")||protectedStream(video)
                ||enabled(result,"is_preview")||protectedStream(result)))
            apply(data,body); // Reject protected/preview streams before any compatibility fallback.
        if(body.optInt("code",-1)==-404||body.optInt("code",-1)==0
                &&(video==null||video.optJSONArray("durl")==null))
            body=NetWorkUtil.getJson("https://api.bilibili.com/pgc/player/web/playurl"+query);
        apply(data,body);
    }
    private static boolean enabled(JSONObject value,String name){
        Object flag=value.opt(name);
        return Boolean.TRUE.equals(flag)||value.optInt(name,0)>0||"true".equalsIgnoreCase(String.valueOf(flag));
    }
    private static boolean protectedStream(JSONObject data){
        if(enabled(data,"is_drm")||enabled(data,"drm_tech_type"))return true;
        JSONObject dash=data.optJSONObject("dash");if(dash==null)return false;
        for(String kind:new String[]{"video","audio"}){
            JSONArray streams=dash.optJSONArray(kind);if(streams==null)continue;
            for(int i=0;i<streams.length();i++){
                JSONObject stream=streams.optJSONObject(i);if(stream==null)continue;
                if(enabled(stream,"drm_tech_type")||!stream.optString("widevine_pssh","").isEmpty()
                        ||!stream.optString("drm_kid","").isEmpty())return true;
            }
        }return false;
    }
    static void apply(PlayerData target,JSONObject body)throws JSONException {
        target.videoUrl="";target.videoBackupUrls.clear();
        int code=body.optInt("code",-1);
        if(code!=0)throw new JSONException("B站未授权缓存该集（"+code+"），请检查登录、会员或购买权限");
        JSONObject result=body.optJSONObject("result");
        JSONObject data=result==null?null:result.optJSONObject("video_info");
        if(data==null)data=result;
        if(data==null)throw new JSONException("番剧接口没有返回缓存数据");
        if(enabled(data,"is_preview")||enabled(result,"is_preview"))throw new JSONException("接口仅返回试看内容，不能作为完整剧集缓存");
        if(protectedStream(data)||protectedStream(result))throw new JSONException("该集为加密内容，暂不支持缓存，请使用官方App");
        JSONArray urls=data.optJSONArray("durl");
        if(urls==null||urls.length()!=1)throw new JSONException("该集暂未提供可完整缓存的单文件视频流，请使用官方App缓存");
        JSONObject stream=urls.getJSONObject(0);
        if(enabled(stream,"drm_tech_type"))throw new JSONException("该集为加密内容，暂不支持缓存");
        String url=stream.optString("url","");
        try{URI parsed=new URI(url);if(parsed.getHost()==null||!("https".equalsIgnoreCase(parsed.getScheme())||"http".equalsIgnoreCase(parsed.getScheme())))throw new Exception();}
        catch(Exception error){throw new JSONException("番剧缓存地址无效");}
        target.videoUrl=url;target.danmakuUrl="https://comment.bilibili.com/"+target.cid+".xml";
        target.qn=data.optInt("quality",target.qn>0?target.qn:32);
        JSONArray values=data.optJSONArray("accept_quality"),labels=data.optJSONArray("accept_description");
        int count=values==null?0:values.length();
        target.qnValueList=new int[count>0?count:1];target.qnStrList=new String[target.qnValueList.length];
        if(count==0){target.qnValueList[0]=target.qn;target.qnStrList[0]="当前可用画质";}
        else for(int i=0;i<count;i++){
            target.qnValueList[i]=values.optInt(i,target.qn);
            target.qnStrList[i]=labels==null?"可用画质 "+(i+1):labels.optString(i,"可用画质 "+(i+1));
        }
    }
}
