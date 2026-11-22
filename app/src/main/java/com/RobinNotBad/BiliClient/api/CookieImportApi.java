package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.LoginTextFile;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.Cookies;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/** Verify a candidate without activating it or disclosing its contents in errors. */
public final class CookieImportApi {
    private CookieImportApi() { }
    public static final class VerificationFailure extends IOException {
        VerificationFailure(String message){super(message);}
    }
    public static long verify(LoginTextFile.Login login) throws IOException {
        try {
            ArrayList<String> headers=new ArrayList<>(Arrays.asList("Cookie",login.cookies,
                    "User-Agent",NetWorkUtil.USER_AGENT_WEB,"Referer","https://www.bilibili.com/"));
            return verifyResponse(login,NetWorkUtil.getJson("https://api.bilibili.com/x/web-interface/nav",headers));
        }catch(org.json.JSONException error){throw new VerificationFailure("登录验证返回异常，请稍后重试");}
    }
    static long verifyResponse(LoginTextFile.Login login,JSONObject root)throws IOException {
        int code=root.optInt("code",Integer.MIN_VALUE);
        JSONObject data=root.optJSONObject("data");
        if(code==-101||code==0&&data!=null&&!data.optBoolean("isLogin",false))
            throw new VerificationFailure("Cookie 已失效或未登录，请重新导出完整登录信息");
        if(code==-352||code==-412)throw new VerificationFailure("B站要求安全验证，请在官方端完成验证后重新导入");
        if(code!=0||data==null)throw new VerificationFailure("无法验证 Cookie，请稍后重试");
        String cookieMid=new Cookies(login.cookies).getOrDefault("DedeUserID","");
        long mid=data.optLong("mid",0);
        if(mid<=0||!String.valueOf(mid).equals(cookieMid))
            throw new VerificationFailure("Cookie 中的账号信息不一致，请重新导出完整登录信息");
        return mid;
    }
}
