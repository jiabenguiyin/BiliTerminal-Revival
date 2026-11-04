package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.util.*;
import org.json.*;
import okhttp3.*;
import okio.Buffer;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One user-confirmed report, using the platform's current reason IDs. */
public final class VideoReportApi {
    private VideoReportApi() { }
    public static final class Reason {
        public final int id;public final String name,remark;public final boolean officialRequired;
        private Reason(JSONObject row){
            id=row.optInt("tid",0);name=row.optString("name","");remark=row.optString("remark","");
            boolean required=false;JSONArray controls=row.optJSONArray("controls");
            if(controls!=null)for(int i=0;i<controls.length();i++){JSONObject control=controls.optJSONObject(i);if(control!=null&&control.optInt("required",0)==1)required=true;}
            officialRequired=required;
        }
    }
    public static List<Reason> parseReasons(JSONObject response)throws IOException {
        if(response.optInt("code",-1)!=0)throw new IOException("举报原因获取失败");
        JSONArray rows=response.optJSONArray("data");if(rows==null)throw new IOException("举报原因数据不完整");
        ArrayList<Reason> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null||row.optInt("state",1)!=1||row.optInt("business",1)!=1)continue;
            Reason reason=new Reason(row);if(reason.id>0&&!reason.name.trim().isEmpty()&&seen.add(reason.id))result.add(reason);
        }
        if(result.isEmpty())throw new IOException("暂无可用举报原因，请使用官方投诉页");return result;
    }
    public static List<Reason> reasons()throws IOException,JSONException {
        return parseReasons(NetWorkUtil.getJson("https://api.bilibili.com/x/web-interface/archive/appeal/tags"));
    }
    public static boolean validDescription(String text){return text!=null&&!text.trim().isEmpty()&&text.trim().length()<=500;}
    public static String officialUrl(long aid){if(aid<=0)throw new IllegalArgumentException("Invalid video");return "https://www.bilibili.com/appeal/?avid="+aid;}

    public static final class Session {
        public final long aid,account;
        private final String cookie,auth,csrf;
        private final AtomicBoolean submitted=new AtomicBoolean();
        private Session(long account,long aid,String cookie){this.account=account;this.aid=aid;this.cookie=cookie;
            auth=NetWorkUtil.getInfoFromCookie("SESSDATA",cookie);csrf=NetWorkUtil.getInfoFromCookie("bili_jct",cookie);}
        public static Session capture(long account,long aid)throws IOException{
            String cookie=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");Session session=new Session(account,aid,cookie);
            String cookieMid=NetWorkUtil.getInfoFromCookie("DedeUserID",cookie);
            if(account<=0||aid<=0||!session.isCurrent()||session.auth.isEmpty()||session.csrf.isEmpty()
                    ||!cookieMid.isEmpty()&&!cookieMid.equals(String.valueOf(account)))throw new IOException("请先登录当前账号后举报视频");
            return session;
        }
        public boolean isCurrent(){String current=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");return account==SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)
                &&auth.equals(NetWorkUtil.getInfoFromCookie("SESSDATA",current))&&csrf.equals(NetWorkUtil.getInfoFromCookie("bili_jct",current));}
        public ApiResult submit(Reason reason,String description)throws IOException,JSONException{
            if(!isCurrent())throw new IOException("账号已变化，未提交举报");
            if(reason==null||reason.id<=0||reason.officialRequired)throw new IOException("该类型需要补充证明，请使用官方投诉页");
            if(!validDescription(description))throw new IllegalArgumentException("请填写1至500字的详细描述");
            if(!submitted.compareAndSet(false,true))throw new IOException("本次举报已提交，请勿重复发送");
            FormBody form=new FormBody.Builder().add("aid",String.valueOf(aid)).add("tid",String.valueOf(reason.id))
                    .add("desc",description.trim()).add("csrf",csrf).add("block_author","false").build();Buffer body=new Buffer();form.writeTo(body);
            ArrayList<String> headers=new ArrayList<>(Arrays.asList("Cookie",cookie,"User-Agent",NetWorkUtil.USER_AGENT_WEB,
                    "Origin","https://www.bilibili.com","Referer","https://www.bilibili.com/video/av"+aid));
            String buid=NetWorkUtil.getInfoFromCookie("Buid",cookie);if(!buid.isEmpty()){headers.add("buid");headers.add(buid);}
            try(Response response=NetWorkUtil.postOnce("https://api.bilibili.com/x/web-interface/appeal/v2/submit",body.readUtf8(),headers)){
                if(!response.isSuccessful()||response.body()==null)throw new IOException("举报结果未确认");
                JSONObject json=new JSONObject(response.body().string());if(json.optInt("code",Integer.MIN_VALUE)==Integer.MIN_VALUE)throw new JSONException("Missing report result");
                return new ApiResult(json);
            }
        }
    }
}
