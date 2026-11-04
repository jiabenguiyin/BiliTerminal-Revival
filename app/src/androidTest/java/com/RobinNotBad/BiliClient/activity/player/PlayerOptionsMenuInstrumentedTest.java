package com.RobinNotBad.BiliClient.activity.player;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.activity.settings.UIPreviewActivity;
import com.RobinNotBad.BiliClient.player.PlayerOptionsMenu;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayerOptionsMenuInstrumentedTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> old = new HashMap<>();
    private Activity host;
    private PlayerOptionsMenu menu;

    @Before public void before() {
        for (String key : new String[]{"dpi", "density", "paddingH_percent", "paddingV_percent", "player_ui_round"})
            old.put(key, SharedPreferencesUtil.getSharedPreferences().getAll().get(key));
        SharedPreferencesUtil.putFloat("dpi", 1f); SharedPreferencesUtil.putInt("density", -1);
        SharedPreferencesUtil.putInt("paddingH_percent", 0); SharedPreferencesUtil.putInt("paddingV_percent", 0);
        SharedPreferencesUtil.putBoolean("player_ui_round", false);
        host = inst.startActivitySync(new Intent(inst.getTargetContext(), UIPreviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        inst.runOnMainSync(() -> host.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY));
        inst.waitForIdleSync();
        long until = SystemClock.uptimeMillis() + 5000;
        while (host.findViewById(android.R.id.content).getWidth() < host.getResources().getDisplayMetrics().widthPixels
                && SystemClock.uptimeMillis() < until) SystemClock.sleep(30);
    }

    @After public void after() {
        inst.runOnMainSync(() -> { if (menu != null) menu.dismiss(); if (host != null) host.finish(); });
        inst.waitForIdleSync();
        for (Map.Entry<String,Object> entry : old.entrySet()) {
            if (entry.getValue() == null) SharedPreferencesUtil.removeValue(entry.getKey());
            else if (entry.getValue() instanceof Float) SharedPreferencesUtil.putFloat(entry.getKey(), (Float) entry.getValue());
            else if (entry.getValue() instanceof Integer) SharedPreferencesUtil.putInt(entry.getKey(), (Integer) entry.getValue());
            else SharedPreferencesUtil.putBoolean(entry.getKey(), (Boolean) entry.getValue());
        }
    }

    @Test public void largeTextMenuStaysInsideScreenAndUsesADarkSurface() throws Exception {
        SharedPreferencesUtil.putFloat("dpi", 1.5f);
        show(2);
        bounds();
        inst.runOnMainSync(() -> {
            View decor = menu.getDialog().getWindow().getDecorView();
            System.out.println("MENU_DIMENSIONS owner=" + host.findViewById(android.R.id.content).getWidth()
                    + "x" + host.findViewById(android.R.id.content).getHeight() + " dialog="
                    + decor.getWidth() + "x" + decor.getHeight() + " density="
                    + host.getResources().getDisplayMetrics().density);
            Bitmap image = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(image));
            int white = 0, dark = 0;
            for (int y = 0; y < image.getHeight(); y += 3) for (int x = 0; x < image.getWidth(); x += 3) {
                int value = image.getPixel(x,y);
                if (Color.alpha(value) < 200) continue;
                if (Color.red(value) > 220 && Color.green(value) > 220 && Color.blue(value) > 220) white++;
                if (Color.red(value) < 80 && Color.green(value) < 80 && Color.blue(value) < 80) dark++;
            }
            assertTrue("Menu retained a white system surface", dark > white * 3);
            try (FileOutputStream out = new FileOutputStream(new File(host.getFilesDir(), "player-options-dark.png"))) {
                image.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (IOException error) { throw new AssertionError(error); }
            image.recycle();
        });
    }

    @Test public void menuTextFollowsSavedUiScaleOnEveryOpen() throws Exception {
        float[] size = new float[2];
        for (int i = 0; i < 2; i++) {
            SharedPreferencesUtil.putFloat("dpi", i == 0 ? 0.75f : 1.25f);
            show(2); final int index = i;
            inst.runOnMainSync(() -> {
                LinearLayout row = menu.getDialog().findViewById(android.R.id.content).findViewWithTag(Integer.valueOf(1));
                size[index] = ((TextView) row.getChildAt(0)).getTextSize(); menu.dismiss();
            });
        }
        assertTrue("Player menu ignored interface scale", size[1] > size[0] * 1.25f);
    }

    @Test public void longMenuScrollsAndLastActionReceivesATouchAtItsDrawnPosition() throws Exception {
        AtomicInteger chosen = new AtomicInteger();
        show(12);
        inst.runOnMainSync(() -> menu.setOnMenuItemClickListener(item -> { chosen.set(item.getItemId()); return true; }));
        bounds();
        inst.runOnMainSync(() -> {
            View row = menu.getDialog().findViewById(android.R.id.content).findViewWithTag(Integer.valueOf(12));
            ScrollView scroll = (ScrollView) row.getParent().getParent();
            scroll.setSmoothScrollingEnabled(false); scroll.fullScroll(View.FOCUS_DOWN);
        });
        inst.waitForIdleSync();
        inst.runOnMainSync(() -> {
            Dialog current = menu.getDialog();
            View row = menu.getDialog().findViewById(android.R.id.content).findViewWithTag(Integer.valueOf(12));
            Rect rect = new Rect();
            assertTrue(row.getGlobalVisibleRect(rect));
            int[] point = new int[2]; row.getLocationInWindow(point);
            long now = SystemClock.uptimeMillis();
            float x = point[0] + row.getWidth() / 2f, y = point[1] + row.getHeight() / 2f;
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
            current.dispatchTouchEvent(down); down.recycle();
            MotionEvent up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, x, y, 0);
            current.dispatchTouchEvent(up); up.recycle();
        });
        inst.waitForIdleSync(); assertEquals(12, chosen.get()); assertFalse(menu.isShowing());
    }

    private void show(int count) throws Exception {
        inst.runOnMainSync(() -> {
            if (menu != null) menu.dismiss();
            menu = new PlayerOptionsMenu(host, host.findViewById(android.R.id.content));
            for (int i = 1; i <= count; i++) menu.getMenu().add(0, i, i, i == 1 ? "清晰度" : "播放合集").setCheckable(i == 2).setChecked(i == 2);
            menu.setOnMenuItemClickListener(item -> true); menu.show();
        });
        inst.waitForIdleSync();
        long until = SystemClock.uptimeMillis()+5000;
        boolean[] ready = {false};
        do {
            inst.runOnMainSync(() -> {
                Window window = menu.getDialog().getWindow();
                ready[0] = window.getDecorView().getWidth() == window.getAttributes().width
                        && window.getDecorView().getHeight() == window.getAttributes().height;
            });
            if (ready[0]) break;
            SystemClock.sleep(30);
        } while (SystemClock.uptimeMillis() < until);
        assertTrue("Dialog did not finish resizing", ready[0]);
    }

    @Test public void checkedAndUncheckedUseTheSameRingWithAnOptionalSeparatedDot() throws Exception {
        show(2);
        for (boolean checked : new boolean[]{true, false}) {
            inst.runOnMainSync(() -> {
                menu.dismiss(); menu.getMenu().findItem(2).setChecked(checked); menu.show();
            });
            inst.waitForIdleSync();
            inst.runOnMainSync(() -> {
                LinearLayout row = menu.getDialog().findViewById(android.R.id.content).findViewWithTag(Integer.valueOf(2));
                assertTrue(row.getChildAt(1) instanceof ImageView);
                android.graphics.drawable.Drawable drawable = ((ImageView) row.getChildAt(1)).getDrawable();
                Rect originalBounds=new Rect(drawable.getBounds());
                Bitmap bitmap = Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
                drawable.setBounds(0,0,48,48); drawable.draw(new Canvas(bitmap));
                assertEquals(checked, Color.alpha(bitmap.getPixel(24,24)) > 200);
                assertEquals("The dot must not touch its ring", 0, Color.alpha(bitmap.getPixel(36,24)));
                assertTrue("The ring must remain visible in both states", Color.alpha(bitmap.getPixel(42,24)) > 100);
                drawable.setBounds(originalBounds);
                bitmap.recycle();
                View decor=menu.getDialog().getWindow().getDecorView();
                Bitmap picture=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);decor.draw(new Canvas(picture));
                try(FileOutputStream out=host.openFileOutput(checked?"menu-radio-on.png":"menu-radio-off.png",0)){picture.compress(Bitmap.CompressFormat.PNG,100,out);}
                catch(IOException error){throw new AssertionError(error);}finally{picture.recycle();}
            });
        }
    }

    private void bounds() {
        inst.runOnMainSync(() -> {
            View window = menu.getDialog().getWindow().getDecorView();
            View root = host.findViewById(android.R.id.content);
            assertTrue(window.getWidth() > 0 && window.getHeight() > 0);
            assertTrue(window.getWidth() <= root.getWidth()); assertTrue(window.getHeight() <= root.getHeight());
        });
    }
}
