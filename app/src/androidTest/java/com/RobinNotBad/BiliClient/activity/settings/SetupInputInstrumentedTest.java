package com.RobinNotBad.BiliClient.activity.settings;

import android.app.Instrumentation;
import android.content.*;
import android.os.SystemClock;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.setup.*;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SetupInputInstrumentedTest {
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    final Map<String,Object> saved=new HashMap<>(); SetupUIActivity page;
    @Before public void setup() {
        for(String key:new String[]{"dpi","paddingH_percent","paddingV_percent","player_ui_round"})
            saved.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        SharedPreferencesUtil.getSharedPreferences().edit().putFloat("dpi",1f).putInt("paddingH_percent",0)
                .putInt("paddingV_percent",0).putBoolean("player_ui_round",false).commit();
        page=(SetupUIActivity)ui.startActivitySync(new Intent(ui.getTargetContext(),SetupUIActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    @After public void cleanup() {
        if(page!=null)ui.runOnMainSync(page::finish);
        ui.waitForIdleSync();
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> e:saved.entrySet()) {
            Object v=e.getValue();if(v==null)edit.remove(e.getKey());else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);
            else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else edit.putBoolean(e.getKey(),(Boolean)v);
        }
        edit.commit();
    }
    @Test public void invalidSetupDoesNotOpenPreviewOrContinueAndValidInputCanProceed() {
        Instrumentation.ActivityMonitor preview=ui.addMonitor(UIPreviewActivity.class.getName(),null,true);
        Instrumentation.ActivityMonitor next=ui.addMonitor(IntroductionActivity.class.getName(),null,true);
        try {
            ui.runOnMainSync(()->{
                EditText scale=page.findViewById(R.id.ui_scale_input);
                scale.setText("950");((EditText)page.findViewById(R.id.ui_padding_horizontal)).setText("5");
                page.findViewById(R.id.preview).performClick();page.findViewById(R.id.confirm).performClick();
                assertNotNull(scale.getError());assertEquals(0,preview.getHits());assertEquals(0,next.getHits());
                assertFalse(page.isFinishing());assertEquals(1f,SharedPreferencesUtil.getFloat("dpi",0),0.0001f);
                assertEquals(0,SharedPreferencesUtil.getInt("paddingH_percent",-1));
                scale.setText("1.25");page.findViewById(R.id.preview).performClick();
                assertEquals(1,preview.getHits());assertNull(scale.getError());
                assertEquals(1.25f,SharedPreferencesUtil.getFloat("dpi",0),0.0001f);
                assertEquals(5,SharedPreferencesUtil.getInt("paddingH_percent",-1));
            });
        } finally { ui.removeMonitor(preview);ui.removeMonitor(next); }
    }
}
