package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import okhttp3.Response;

/** Single-user relationship actions bound to the account which opened the menu. */
public final class UserRelationApi {
    private UserRelationApi() {}
    public static final class Session {
        public final long mid;
        private final String cookie,csrf;
        private Session(long mid,String cookie,String csrf){this.mid=mid;this.cookie=cookie;this.csrf=csrf;}
        public boolean isCurrent(){return mid==SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)
                &&cookie.equals(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,""));}
        private void require(long target)throws IOException {
            if(target<=0||target==mid)throw new IOException("不能对自己或无效用户执行此操作");
            if(!isCurrent())throw new IOException("账号已切换，请重新操作");
        }
        private ArrayList<String> headers(long target){return new ArrayList<>(Arrays.asList(
                "Cookie",cookie,"User-Agent",NetWorkUtil.USER_AGENT_WEB,"Origin","https://space.bilibili.com",
                "Referer","https://space.bilibili.com/"+target+"/"));}
    }
    public static Session capture()throws IOException {
        long mid=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        String cookie=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");
        String csrf=NetWorkUtil.getInfoFromCookie("bili_jct",cookie);
        if(mid<=0)throw new IOException("请先登录账号");
        if(cookie.isEmpty()||csrf.isEmpty())throw new IOException("登录信息不完整，请重新登录");
        String cookieMid=NetWorkUtil.getInfoFromCookie("DedeUserID",cookie);
        if(!cookieMid.isEmpty()&&!cookieMid.equals(String.valueOf(mid)))throw new IOException("登录账号信息不一致，请重新登录");
        return new Session(mid,cookie,csrf);
    }
    public static int getAttribute(Session session,long target)throws IOException,JSONException {
        session.require(target);
        JSONObject response=NetWorkUtil.getJson("https://api.bilibili.com/x/relation?fid="+target,session.headers(target));
        int code=response.optInt("code",-1);
        if(code!=0)throw new JSONException("获取用户关系失败（"+code+"）");
        JSONObject data=response.optJSONObject("data");
        if(data==null||!data.has("attribute")||data.optInt("attribute",-1)<0)throw new JSONException("用户关系数据不完整");
        return data.getInt("attribute");
    }
    public static int modify(Session session,long target,int action)throws IOException,JSONException {
        if(action!=1&&action!=2&&action!=4&&action!=5&&action!=6)throw new IllegalArgumentException("Unsupported relationship action");
        session.require(target);
        String body="fid="+target+"&act="+action+"&re_src=11&csrf="+session.csrf;
        try(Response response=NetWorkUtil.post("https://api.bilibili.com/x/relation/modify",body,session.headers(target))) {
            if(response.body()==null)throw new IOException("用户关系操作未返回结果");
            JSONObject result=new JSONObject(response.body().string());
            if(!result.has("code"))throw new JSONException("用户关系操作返回数据不完整");
            return result.getInt("code");
        }
    }
}
