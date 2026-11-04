package com.RobinNotBad.BiliClient.activity.search;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.api.ConfInfoApi;
import com.RobinNotBad.BiliClient.util.*;
import com.google.android.material.tabs.TabLayout;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SearchFailureInstrumentedTest {
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    final Map<String,Object> saved=new HashMap<>();SearchActivity page;
    AtomicReference<OkHttpClient> clients;OkHttpClient original;volatile String mode="verify";
    @Before public void setup()throws Exception {
        SharedPreferences prefs=SharedPreferencesUtil.getSharedPreferences();
        for(String k:new String[]{"tutorial_ver_search","tutorial_pager_SearchActivity","search_suggestions_enable",
                SharedPreferencesUtil.SEARCH_DEFAULT_CONTENT_ENABLE,SharedPreferencesUtil.SEARCH_SHOW_CATEGORY_TABS,"wbi_mixin_key","last_wbi","dpi","density"})saved.put(k,prefs.getAll().get(k));
        prefs.edit().putInt("tutorial_ver_search",99).putBoolean("tutorial_pager_SearchActivity",false)
                .putBoolean("search_suggestions_enable",false).putBoolean(SharedPreferencesUtil.SEARCH_DEFAULT_CONTENT_ENABLE,false)
                .putBoolean(SharedPreferencesUtil.SEARCH_SHOW_CATEGORY_TABS,true).putFloat("dpi",1f).putInt("density",-1)
                .putString("wbi_mixin_key","0123456789abcdef0123456789abcdef").putInt("last_wbi",ConfInfoApi.getDateCurr()).commit();
        Field f=NetWorkUtil.class.getDeclaredField("INSTANCE");f.setAccessible(true);clients=(AtomicReference<OkHttpClient>)f.get(null);original=clients.get();
        clients.set(new OkHttpClient.Builder().addInterceptor(chain->{
            if("network".equals(mode))throw new IOException("fixture offline");
            try {
                JSONObject data="verify".equals(mode)?new JSONObject().put("v_voucher","fixture"):
                        new JSONObject().put("result",new JSONArray()).put("numResults",0).put("numPages",0);
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"),new JSONObject().put("code",0).put("data",data).toString())).build();
            }catch(JSONException e){throw new IOException(e);}
        }).build());
        page=(SearchActivity)ui.startActivitySync(new Intent(ui.getTargetContext(),SearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->page.viewPager!=null&&page.viewPager.getAdapter()!=null);
    }
    @After public void cleanup(){
        if(page!=null)ui.runOnMainSync(page::finish);ui.waitForIdleSync();if(clients!=null)clients.set(original);
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object>e:saved.entrySet()){Object v=e.getValue();if(v==null)edit.remove(e.getKey());
            else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);
            else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);else edit.putString(e.getKey(),(String)v);}edit.commit();
    }
    @Test public void eachCategoryShowsVerificationAndRetryRecoversWithoutSeid()throws Exception {
        for(int i=0;i<6;i++) {
            final int category=i;mode="verify";
            ui.runOnMainSync(()->{((TabLayout)page.findViewById(R.id.search_categories)).getTabAt(category).select();page.searchKeyword("qa-category-"+category);});
            await(()->fragment(category)!=null&&!fragment(category).isRefreshing()&&fragment(category).emptyView.getVisibility()==View.VISIBLE
                    &&fragment(category).emptyView.getText().toString().contains("安全验证"));
            if(i==3)capture("qa-search-verification.png");
            mode="empty";
            ui.runOnMainSync(()->fragment(category).emptyView.performClick());
            await(()->!fragment(category).isRefreshing()&&"暂无搜索结果".contentEquals(fragment(category).emptyView.getText()));
            ui.runOnMainSync(()->assertTrue(fragment(category).page>=1));
        }
    }
    @Test public void genuineNetworkFailureGetsNetworkMessageRatherThanVerification() {
        mode="network";ui.runOnMainSync(()->page.searchKeyword("qa-offline"));
        await(()->fragment(0)!=null&&!fragment(0).isRefreshing()&&fragment(0).emptyView.getText().toString().contains("网络连接失败"));
        ui.runOnMainSync(()->assertFalse(fragment(0).emptyView.getText().toString().contains("安全验证")));
    }
    SearchFragment fragment(int i){return (SearchFragment)page.getSupportFragmentManager().findFragmentByTag("f"+i);}
    interface Check{boolean get();}
    void await(Check c){long end=SystemClock.uptimeMillis()+12000;boolean[]ok={false};do{ui.runOnMainSync(()->ok[0]=c.get());if(ok[0])return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<end);fail("Search state did not settle");}
    void capture(String filename)throws Exception{ui.waitForIdleSync();ui.runOnMainSync(()->{View v=page.findViewById(android.R.id.content);Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));try(FileOutputStream out=page.openFileOutput(filename,0)){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}});}
}
