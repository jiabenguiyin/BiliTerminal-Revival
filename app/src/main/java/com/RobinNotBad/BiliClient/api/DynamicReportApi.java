package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.util.*;
import org.json.*;
import okhttp3.*;
import okio.Buffer;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dynamic reporting has a separate target, author and reason namespace from video reporting. */
public final class DynamicReportApi {
    private static final int[] IDS={4,8,1,5,3,9,10,12,13,0};
    private static final String[] LABELS={"垃圾广告","引战","色情","人身攻击","违法信息","涉政谣言","涉社会事件谣言","虚假不实信息","违法信息外链","其他"};
    private DynamicReportApi() { }
    public static final class Reason {
        public final int id;public final String name;public final boolean officialRequired=false;
        private Reason(int id,String name){this.id=id;this.name=name;}
    }
    public static List<Reason> reasons(){ArrayList<Reason> result=new ArrayList<>();for(int i=0;i<IDS.length;i++)result.add(new Reason(IDS[i],LABELS[i]));return result;}
    public static boolean validDescription(String text){return text!=null&&!text.trim().isEmpty()&&text.trim().length()<=500;}
    public static String officialUrl(long dynamicId){if(dynamicId<=0)throw new IllegalArgumentException("Invalid dynamic");return "https://t.bilibili.com/"+dynamicId;}
    private static boolean validReason(int reason){for(int id:IDS)if(id==reason)return true;return false;}
    static FormBody form(long dynamicId,long author,int reason,String description,String csrf){
        if(dynamicId<=0||author<=0||!validReason(reason)||!validDescription(description))throw new IllegalArgumentException("动态举报信息不完整");
        return new FormBody.Builder().add("dynamic_id",String.valueOf(dynamicId)).add("accused_uid",String.valueOf(author))
                .add("reason_type",String.valueOf(reason)).add("reason_desc",description.trim()).add("csrf",csrf).build();
    }
    public static final class Session {
        public final long dynamicId,author,account;
        private final String cookie,auth,csrf;private final AtomicBoolean submitted=new AtomicBoolean();
        private Session(long account,long dynamicId,long author,String cookie){this.account=account;this.dynamicId=dynamicId;this.author=author;this.cookie=cookie;
            auth=NetWorkUtil.getInfoFromCookie("SESSDATA",cookie);csrf=NetWorkUtil.getInfoFromCookie("bili_jct",cookie);}
        public static Session capture(long account,long dynamicId,long author)throws IOException{
            String cookie=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");Session session=new Session(account,dynamicId,author,cookie);
            String cookieMid=NetWorkUtil.getInfoFromCookie("DedeUserID",cookie);
            if(account<=0||dynamicId<=0||author<=0||!session.isCurrent()||session.auth.isEmpty()||session.csrf.isEmpty()
                    ||!cookieMid.isEmpty()&&!cookieMid.equals(String.valueOf(account)))throw new IOException("请先登录当前账号后举报动态");
            return session;
        }
        public boolean isCurrent(){String current=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");return account==SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)
                &&auth.equals(NetWorkUtil.getInfoFromCookie("SESSDATA",current))&&csrf.equals(NetWorkUtil.getInfoFromCookie("bili_jct",current));}
        public ApiResult submit(Reason reason,String description)throws IOException,JSONException{
            if(!isCurrent())throw new IOException("账号已变化，未提交举报");
            if(reason==null)throw new IllegalArgumentException("请选择举报原因");
            FormBody form=form(dynamicId,author,reason.id,description,csrf);Buffer body=new Buffer();form.writeTo(body);
            if(!submitted.compareAndSet(false,true))throw new IOException("本次举报已提交，请勿重复发送");
            String url=HttpUrl.parse("https://api.bilibili.com/x/dynamic/feed/dynamic_report/add").newBuilder().addQueryParameter("csrf",csrf).build().toString();
            List<String> headers=Arrays.asList("Cookie",cookie,"User-Agent",NetWorkUtil.USER_AGENT_WEB,"Origin","https://t.bilibili.com","Referer",officialUrl(dynamicId));
            try(Response response=NetWorkUtil.postOnce(url,body.readUtf8(),headers)){
                if(!response.isSuccessful()||response.body()==null)throw new IOException("举报结果未确认");
                JSONObject json=new JSONObject(response.body().string());if(json.optInt("code",Integer.MIN_VALUE)==Integer.MIN_VALUE)throw new JSONException("Missing dynamic report result");
                return new ApiResult(json);
            }
        }
    }
}
