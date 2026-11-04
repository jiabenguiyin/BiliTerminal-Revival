package com.RobinNotBad.BiliClient.activity;

import android.app.*;
import android.content.Intent;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.settings.SettingMainActivity;
import com.RobinNotBad.BiliClient.activity.settings.SettingUIActivity;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MenuExitInstrumentedTest {
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    final ArrayList<Activity> opened=new ArrayList<>();
    @After public void cleanup(){ui.runOnMainSync(()->{for(Activity a:opened)if(!a.isFinishing())a.finish();});}
    @Test public void exitFinishesNestedTaskAndDoesNotReopenAnyScreen() throws Exception {
        int pid=android.os.Process.myPid();
        for(Class<?> type:new Class<?>[]{SettingMainActivity.class,SettingUIActivity.class,MenuActivity.class}) {
            Instrumentation.ActivityMonitor monitor=ui.addMonitor(type.getName(),null,false);
            try {
                ui.runOnMainSync(()->ui.getTargetContext().startActivity(new Intent(ui.getTargetContext(),type)
                        .putExtra("from","settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                Activity a=monitor.waitForActivityWithTimeout(7000);assertNotNull(a);opened.add(a);
            }finally{ui.removeMonitor(monitor);}
        }
        MenuActivity menu=(MenuActivity)opened.get(opened.size()-1);
        ui.runOnMainSync(()->{
            View exit=find((ViewGroup)menu.findViewById(R.id.menu_layout));assertNotNull(exit);exit.performClick();
        });
        long end=SystemClock.uptimeMillis()+6000;boolean[] alive={true};
        do {
            ui.runOnMainSync(()->{
                alive[0]=false;
                for(Activity a:opened)if(ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(a)!=Stage.DESTROYED)alive[0]=true;
            });
            if(!alive[0])break;SystemClock.sleep(40);
        }while(SystemClock.uptimeMillis()<end);
        assertFalse("An old activity can still be restored",alive[0]);
        SystemClock.sleep(2500);
        ui.runOnMainSync(()->assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).isEmpty()));
        assertEquals("Exit must not SIGKILL the process",pid,android.os.Process.myPid());
    }
    View find(ViewGroup group){
        for(int i=0;i<group.getChildCount();i++){
            View v=group.getChildAt(i);if(v instanceof TextView&&"退出".contentEquals(((TextView)v).getText()))return v;
        }return null;
    }
}
