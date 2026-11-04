package com.RobinNotBad.BiliClient.activity.settings;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.UiConfigurationUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UiSettingsConfirmInstrumentedTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> old=new HashMap<>();
    private SettingUIActivity activity;
    private boolean oldForce;
    @Before public void setup() {
        oldForce=BiliTerminal.DPI_FORCE_CHANGE;
        for(String key:new String[]{"dpi","paddingH_percent","paddingV_percent","density","player_ui_round"})
            old.put(key,SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        SharedPreferencesUtil.getSharedPreferences().edit().putFloat("dpi",1f).putInt("paddingH_percent",0)
                .putInt("paddingV_percent",0).putInt("density",-1).putBoolean("player_ui_round",false).commit();
        open();
    }
    private void open() {
        activity=(SettingUIActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),SettingUIActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline=SystemClock.uptimeMillis()+10000;
        while(activity.findViewById(R.id.confirm_ui_settings)==null&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(40);
        inst.waitForIdleSync();assertNotNull(activity.findViewById(R.id.confirm_ui_settings));
    }
    @After public void close() {
        if(activity!=null) finishActivity();
        android.content.SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object> item:old.entrySet()) {
            Object value=item.getValue();String key=item.getKey();
            if(value==null)edit.remove(key);else if(value instanceof Float)edit.putFloat(key,(Float)value);
            else if(value instanceof Integer)edit.putInt(key,(Integer)value);else edit.putBoolean(key,(Boolean)value);
        }
        edit.commit();BiliTerminal.DPI_FORCE_CHANGE=oldForce;
    }
    private void finishActivity() {
        SettingUIActivity ending=activity;
        inst.runOnMainSync(ending::finish);
        long until=SystemClock.uptimeMillis()+5000;AtomicReference<Stage> stage=new AtomicReference<>();
        do {
            inst.runOnMainSync(()->stage.set(ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(ending)));
            if(stage.get()==Stage.DESTROYED)break;SystemClock.sleep(30);
        }while(SystemClock.uptimeMillis()<until);
        assertEquals("Settings must finish onDestroy before restoring test preferences",Stage.DESTROYED,stage.get());
        inst.waitForIdleSync();activity=null;
    }
    private EditText input(int id){return activity.findViewById(id);}
    private AlertDialog dialog() {
        try{Field f=SettingUIActivity.class.getDeclaredField("confirmationDialog");f.setAccessible(true);return (AlertDialog)f.get(activity);}
        catch(Exception error){throw new AssertionError(error);}
    }
    private void confirm(){activity.findViewById(R.id.confirm_ui_settings).performClick();}

    @Test public void confirmSavesBeforeLeavingAndShowsSuccessAndReopensWithValues()throws Exception {
        inst.runOnMainSync(()->{
            input(R.id.ui_scale_input).setText("0.9");input(R.id.ui_padding_horizontal).setText("5");input(R.id.ui_padding_vertical).setText("3");
            ((ScrollView)activity.findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);confirm();
            assertEquals(0.9f,SharedPreferencesUtil.getFloat("dpi",0),0.001f);
            assertEquals(5,SharedPreferencesUtil.getInt("paddingH_percent",-1));assertEquals(3,SharedPreferencesUtil.getInt("paddingV_percent",-1));
            assertEquals(-1,SharedPreferencesUtil.getInt("density",100));assertTrue(dialog().isShowing());
            TextView message=dialog().findViewById(android.R.id.message);
            assertEquals(activity.getString(R.string.ui_settings_saved_message),message.getText().toString());
        });
        inst.waitForIdleSync();SystemClock.sleep(500);
        // The headless compositor can return an all-black screenshot. Render the
        // measured dialog hierarchy directly for deterministic visual inspection.
        inst.runOnMainSync(()->{
            View decor=dialog().getWindow().getDecorView();
            assertTrue(decor.getWidth()>0&&decor.getHeight()>0);
            Bitmap image=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(image);canvas.drawColor(Color.BLACK);decor.draw(canvas);
            try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"ui-settings-confirmation.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(Exception error){throw new AssertionError(error);}finally{image.recycle();}
        });
        inst.runOnMainSync(()->dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick());finishActivity();
        open();inst.runOnMainSync(()->{
            assertEquals("0.9",input(R.id.ui_scale_input).getText().toString());
            assertEquals("5",input(R.id.ui_padding_horizontal).getText().toString());
            assertEquals("3",input(R.id.ui_padding_vertical).getText().toString());
        });
    }

    @Test public void invalidInputDoesNotShowSuccessOrPartiallySave() {
        inst.runOnMainSync(()->{
            input(R.id.ui_scale_input).setText("0.8");input(R.id.density_input).setText("invalid");confirm();
            assertNull(dialog());assertNotNull(input(R.id.density_input).getError());
            assertEquals(1f,SharedPreferencesUtil.getFloat("dpi",0),0.001f);
            input(R.id.density_input).setText("");confirm();
            assertTrue(dialog().isShowing());assertEquals(0.8f,SharedPreferencesUtil.getFloat("dpi",0),0.001f);
        });
    }

    @Test public void outOfRangeValuesAreRejectedAndNeverPartiallySaved() {
        inst.runOnMainSync(()->{
            input(R.id.ui_scale_input).setText("950");input(R.id.ui_padding_horizontal).setText("5");confirm();
            assertNull(dialog());assertNotNull(input(R.id.ui_scale_input).getError());
            assertEquals(1f,SharedPreferencesUtil.getFloat("dpi",0),0.001f);
            assertEquals(0,SharedPreferencesUtil.getInt("paddingH_percent",-1));
            input(R.id.ui_scale_input).setText("1.25");input(R.id.ui_padding_horizontal).setText("99");confirm();
            assertNull(dialog());assertNotNull(input(R.id.ui_padding_horizontal).getError());
            assertEquals(1f,SharedPreferencesUtil.getFloat("dpi",0),0.001f);
            input(R.id.ui_padding_horizontal).setText("5");confirm();assertTrue(dialog().isShowing());
            assertEquals(1.25f,SharedPreferencesUtil.getFloat("dpi",0),0.001f);
            assertEquals(5,SharedPreferencesUtil.getInt("paddingH_percent",0));
        });
    }

    @Test public void returningWithoutConfirmStillSavesValidSettings() {
        inst.runOnMainSync(()->{input(R.id.ui_scale_input).setText("0.85");input(R.id.ui_padding_horizontal).setText("4");});
        finishActivity();assertEquals(0.85f,SharedPreferencesUtil.getFloat("dpi",0),0.001f);
        assertEquals(4,SharedPreferencesUtil.getInt("paddingH_percent",-1));
    }
}
