package com.RobinNotBad.BiliClient;

import android.app.Activity;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.activity.browser.BrowserActivity;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class ReleaseSmokeTest {
    @Test public void releaseActivitiesStart() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Activity test = instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            assertNotNull(test.findViewById(R.id.deepseek));
        } finally {
            instrumentation.runOnMainSync(test::finish);
        }

        Activity browser = instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), BrowserActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            assertNotNull(browser.findViewById(R.id.browser_webview));
        } finally {
            instrumentation.runOnMainSync(browser::finish);
        }
    }
}
