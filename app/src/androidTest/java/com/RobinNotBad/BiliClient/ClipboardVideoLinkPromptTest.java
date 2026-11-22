package com.RobinNotBad.BiliClient;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.activity.settings.TestActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ClipboardVideoLinkPromptTest {
    @Test public void foregroundingWithOfficialShareTextShowsOpenPrompt() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context target = instrumentation.getTargetContext();
        if (Looper.myLooper() == null) Looper.prepare();
        ClipboardManager clipboard = (ClipboardManager) target.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Bilibili", "【测试视频】\nhttps://www.bilibili.com/video/BV1ab411c7mD/?share_source=copy"));

        Activity activity = instrumentation.startActivitySync(new Intent(target, TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            long deadline = SystemClock.uptimeMillis() + 5000L;
            boolean found = false;
            while (SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync();
                AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    found = !root.findAccessibilityNodeInfosByText("发现分享链接").isEmpty()
                            && !root.findAccessibilityNodeInfosByText("立即查看").isEmpty()
                            && !root.findAccessibilityNodeInfosByText("取消").isEmpty();
                    root.recycle();
                }
                if (found) break;
                SystemClock.sleep(100L);
            }
            assertTrue("Opening the app should offer to open the copied Bilibili video", found);
        } finally {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
