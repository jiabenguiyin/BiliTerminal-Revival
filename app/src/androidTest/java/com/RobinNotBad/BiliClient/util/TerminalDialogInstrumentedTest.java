package com.RobinNotBad.BiliClient.util;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.activity.user.AccountSwitchActivity;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TerminalDialogInstrumentedTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> previous = new HashMap<>();
    private AccountSwitchActivity owner;
    private AlertDialog dialog;

    @Before public void setup() {
        SharedPreferences preferences = inst.getTargetContext().getSharedPreferences("default", 0);
        SharedPreferencesUtil.sharedPreferences = preferences;
        for (String key : new String[]{"dpi", "density", "player_ui_round", AccountManager.ACCOUNTS_KEY,
                AccountManager.ACTIVE_MID_KEY, AccountManager.LOGGED_OUT_KEY})
            previous.put(key, preferences.getAll().get(key));
        preferences.edit().putBoolean(AccountManager.LOGGED_OUT_KEY, true)
                .putBoolean("player_ui_round", false).putFloat("dpi", 1f).putInt("density", -1).commit();
        launch();
    }

    private void launch() {
        owner = (AccountSwitchActivity) inst.startActivitySync(new Intent(inst.getTargetContext(),
                AccountSwitchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        inst.waitForIdleSync();
    }

    @After public void cleanup() {
        inst.runOnMainSync(() -> {
            if (dialog != null) dialog.dismiss();
            if (owner != null) owner.finish();
        });
        inst.waitForIdleSync();
        SharedPreferences.Editor edit = SharedPreferencesUtil.getSharedPreferences().edit();
        for (Map.Entry<String, Object> value : previous.entrySet()) {
            String key = value.getKey(); Object v = value.getValue();
            if (v == null) edit.remove(key);
            else if (v instanceof Float) edit.putFloat(key, (Float) v);
            else if (v instanceof Integer) edit.putInt(key, (Integer) v);
            else if (v instanceof Long) edit.putLong(key, (Long) v);
            else if (v instanceof Boolean) edit.putBoolean(key, (Boolean) v);
            else if (v instanceof String) edit.putString(key, (String) v);
        }
        edit.commit();
    }

    @Test public void accountSwitchUsesRoundedConfirmationAndCancelKeepsTheAccount() throws Exception {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        AccountManager.SavedAccount account = new AccountManager.SavedAccount();
        account.mid = mid == 99123456L ? 99123457L : 99123456L; account.name = "测试账号";
        inst.runOnMainSync(() -> owner.onAccountClick(account));
        inst.waitForIdleSync();
        AccessibilityNodeInfo root = null;
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < deadline) {
            root = inst.getUiAutomation().getRootInActiveWindow();
            if (root != null && !root.findAccessibilityNodeInfosByText("切换到").isEmpty()) break;
            SystemClock.sleep(100);
        }
        assertNotNull(root);
        assertFalse(root.toString(), root.findAccessibilityNodeInfosByText("切换到").isEmpty());
        Bitmap screen = inst.getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(owner.getFilesDir(), "account-switch-confirmation.png"))) {
            assertNotNull(screen); screen.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { if (screen != null) screen.recycle(); }
        assertTrue(root.findAccessibilityNodeInfosByText("取消").get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));
        inst.waitForIdleSync();
        assertEquals(mid, SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
    }

    @Test public void confirmationActionsKeepTheirCallbacksAndTouchAtDrawnCenters() {
        AtomicInteger accepted = new AtomicInteger(), cancelled = new AtomicInteger();
        inst.runOnMainSync(() -> {
            dialog = new AlertDialog.Builder(owner).setTitle("切换账号").setMessage("切换到“测试账号”？")
                    .setNegativeButton("取消", (d, which) -> cancelled.incrementAndGet())
                    .setPositiveButton("切换", (d, which) -> accepted.incrementAndGet()).create();
            MsgUtil.prepareAlertDialog(dialog); dialog.show();
        });
        settled(); verifyBounds(false); capture("confirmation-rounded.png");
        tap(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
        assertEquals(1, cancelled.get()); assertEquals(0, accepted.get()); assertFalse(dialog.isShowing());
        inst.runOnMainSync(dialog::show); settled();
        tap(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        assertEquals(1, accepted.get()); assertFalse(dialog.isShowing());
    }

    @Test public void denseRoundLongMessageScrollsWhileBothActionsRemainVisible() {
        inst.runOnMainSync(owner::finish); inst.waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (!owner.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25);
        assertTrue(owner.isDestroyed());
        SharedPreferencesUtil.putFloat("dpi", 2f); SharedPreferencesUtil.putBoolean("player_ui_round", true); launch();
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < 25; i++) message.append("这是可完整滚动查看的说明内容。\n");
        inst.runOnMainSync(() -> {
            dialog = new AlertDialog.Builder(owner).setTitle("上传诊断日志？").setMessage(message)
                    .setNegativeButton("取消", null).setPositiveButton("确认上传", null).create();
            MsgUtil.prepareAlertDialog(dialog); dialog.show();
        });
        settled(); verifyBounds(true); capture("confirmation-dense-round.png");
        inst.runOnMainSync(() -> {
            androidx.core.widget.NestedScrollView scroll = dialog.findViewById(androidx.appcompat.R.id.scrollView);
            scroll.fullScroll(View.FOCUS_DOWN);
        });
        settled();
        inst.runOnMainSync(() -> assertTrue(((View) dialog.findViewById(android.R.id.message).getParent().getParent()).getScrollY() > 0));
        tap(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)); assertFalse(dialog.isShowing());
    }

    @Test public void listMenuRetainsScrollingAndTheSelectedItem() {
        AtomicInteger selected = new AtomicInteger(-1); String[] items = new String[18];
        for (int i = 0; i < items.length; i++) items[i] = "收藏夹 " + i;
        inst.runOnMainSync(() -> {
            dialog = new AlertDialog.Builder(owner).setTitle("选择收藏夹")
                    .setItems(items, (d, which) -> selected.set(which)).create();
            MsgUtil.prepareAlertDialog(dialog); dialog.show();
        });
        settled(); verifyBounds(false); capture("list-rounded.png");
        inst.runOnMainSync(() -> dialog.getListView().setSelection(17)); settled();
        ListView list = dialog.getListView();
        final View[] row = new View[1];
        inst.runOnMainSync(() -> row[0] = list.getChildAt(17 - list.getFirstVisiblePosition()));
        assertNotNull(row[0]); tap(row[0]); assertEquals(17, selected.get()); assertFalse(dialog.isShowing());
    }

    @Test public void customInputAndThreeActionsRemainUsable() {
        AtomicInteger result = new AtomicInteger(); final EditText[] input = new EditText[1];
        inst.runOnMainSync(() -> {
            input[0] = new EditText(owner); input[0].setSingleLine(true); input[0].setText("原名称");
            dialog = new AlertDialog.Builder(owner).setTitle("编辑收藏夹").setView(input[0])
                    .setNegativeButton("取消", null).setNeutralButton("清空", (d, w) -> result.set(-1))
                    .setPositiveButton("保存", (d, w) -> { assertEquals("新名称", input[0].getText().toString()); result.set(1); }).create();
            MsgUtil.prepareAlertDialog(dialog); dialog.show(); input[0].setText("新名称");
        });
        settled(); verifyBounds(false); capture("input-rounded.png");
        tap(dialog.getButton(AlertDialog.BUTTON_POSITIVE)); assertEquals(1, result.get());
    }

    @Test public void nativeBirthdayPickerUsesTheSameSeparatedActions() {
        final android.app.DatePickerDialog[] picker={null};
        AtomicInteger accepted = new AtomicInteger();
        try {
            inst.runOnMainSync(()->{
                picker[0]=com.RobinNotBad.BiliClient.ui.TerminalDialogStyle.createDatePicker(owner,2024,1,29,
                        (v,y,m,d)->{assertEquals(2025,y);assertEquals(2,m);assertEquals(10,d);accepted.incrementAndGet();});
                picker[0].show();
            });settled();
            capture(picker[0],"birthday-rounded.png");
            inst.runOnMainSync(()->{
                android.app.DatePickerDialog d=picker[0];Rect cancel=new Rect(),confirm=new Rect(),bounds=new Rect();
                assertTrue(d.getButton(-2).getGlobalVisibleRect(cancel));assertTrue(d.getButton(-1).getGlobalVisibleRect(confirm));
                assertTrue(d.getWindow().getDecorView().getGlobalVisibleRect(bounds));
                assertTrue(confirm.left-cancel.right>=12||confirm.top-cancel.bottom>=10);assertTrue(bounds.contains(cancel));assertTrue(bounds.contains(confirm));
                assertTrue(cancel.width()>60);assertTrue(confirm.width()>60);
                assertEquals(2024,d.getDatePicker().getYear());assertEquals(1,d.getDatePicker().getMonth());assertEquals(29,d.getDatePicker().getDayOfMonth());
                d.getDatePicker().updateDate(2025,2,10);
                d.getButton(-2).performClick();
            });
            settled();assertEquals(0,accepted.get());
            inst.runOnMainSync(()->picker[0].show());settled();
            inst.runOnMainSync(()->picker[0].getButton(-1).performClick());settled();assertEquals(1,accepted.get());
        }finally{inst.runOnMainSync(()->{if(picker[0]!=null)picker[0].dismiss();});}
    }

    private void verifyBounds(boolean round) {
        inst.runOnMainSync(() -> {
            View decor = dialog.getWindow().getDecorView(), bounds = owner.findViewById(android.R.id.content);
            assertTrue(decor.getBackground() instanceof GradientDrawable);
            assertTrue(decor.getWidth() <= bounds.getWidth() * (round ? .71f : .91f));
            assertTrue(decor.getHeight() <= bounds.getHeight() * (round ? .71f : .91f));
            Rect viewport = new Rect(); assertTrue(decor.getGlobalVisibleRect(viewport));
            Rect previous = null;
            for (int which : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL}) {
                View button = dialog.getButton(which); if (button == null || button.getVisibility() != View.VISIBLE) continue;
                Rect visible = new Rect(); assertTrue(button.getGlobalVisibleRect(visible));
                assertEquals(button.getWidth(), visible.width()); assertEquals(button.getHeight(), visible.height());
                assertTrue(viewport.contains(visible));
                if (previous != null) assertFalse(Rect.intersects(previous, visible)); previous = visible;
            }
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) assertEquals(Integer.MAX_VALUE, message.getMaxLines());
        });
    }

    private void settled() { inst.waitForIdleSync(); SystemClock.sleep(250); inst.waitForIdleSync(); }
    private void tap(View target) {
        inst.runOnMainSync(() -> {
            int[] xy = new int[2]; target.getLocationInWindow(xy); long now = SystemClock.uptimeMillis();
            for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                MotionEvent e = MotionEvent.obtain(now, now + (action == 0 ? 0 : 50), action,
                        xy[0] + target.getWidth() / 2f, xy[1] + target.getHeight() / 2f, 0);
                dialog.dispatchTouchEvent(e); e.recycle();
            }
        }); inst.waitForIdleSync(); SystemClock.sleep(200); inst.waitForIdleSync();
    }
    private void capture(String name) {
        capture(dialog,name);
    }
    private void capture(android.app.Dialog target, String name) {
        inst.runOnMainSync(() -> {
            View root = target.getWindow().getDecorView();
            Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            try (FileOutputStream out = new FileOutputStream(new File(owner.getFilesDir(), name))) {
                root.draw(new Canvas(image)); image.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception error) { throw new AssertionError(error); }
            finally { image.recycle(); }
        });
    }
}
