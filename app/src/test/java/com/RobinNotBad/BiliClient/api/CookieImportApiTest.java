package com.RobinNotBad.BiliClient.api;
import com.RobinNotBad.BiliClient.util.LoginTextFile;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class CookieImportApiTest {
    private LoginTextFile.Login login()throws Exception{return LoginTextFile.parse("DedeUserID=42; SESSDATA=a%2Cb+z==; bili_jct=c");}
    @Test public void verifiesTheActualLoggedInAccount()throws Exception {
        assertEquals(42,CookieImportApi.verifyResponse(login(),new JSONObject("{\"code\":0,\"data\":{\"isLogin\":true,\"mid\":42}}")));
    }
    @Test public void rejectsExpiredMismatchedOrIncompleteIdentityWithoutLeakingTokens()throws Exception {
        for(String body:new String[]{"{\"code\":-101}","{\"code\":0,\"data\":{\"isLogin\":false}}",
                "{\"code\":0,\"data\":{\"isLogin\":true,\"mid\":99}}","{}","{\"code\":-352}"}){
            try{CookieImportApi.verifyResponse(login(),new JSONObject(body));fail();}
            catch(CookieImportApi.VerificationFailure e){assertFalse(e.getMessage().contains("a%2Cb"));}
        }
    }
}
