package com.RobinNotBad.BiliClient;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.activity.user.EditProfileActivity;
import com.RobinNotBad.BiliClient.model.UserInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class EditProfileLifecycleTest {
    @Test public void lateProfileBindingAfterFinishIsIgnored() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context target = instrumentation.getTargetContext();
        SharedPreferences preferences = target.getSharedPreferences("default", Context.MODE_PRIVATE);
        boolean hadMid = preferences.contains("mid");
        long previousMid = preferences.getLong("mid", 0L);
        preferences.edit().putLong("mid", 1L).commit();

        Activity activity = instrumentation.startActivitySync(new Intent(target, EditProfileActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();

            Method bindProfile = EditProfileActivity.class.getDeclaredMethod("bindProfile", UserInfo.class);
            bindProfile.setAccessible(true);
            UserInfo profile = new UserInfo(1L, "test", "https://example.invalid/avatar", "", 0, 0,
                    0, false, "", 0, "", 0);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> {
                try {
                    bindProfile.invoke(activity, profile);
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            assertNull("Late profile callback touched a finished Activity", failure.get());
        } finally {
            instrumentation.runOnMainSync(activity::finish);
            if (hadMid) preferences.edit().putLong("mid", previousMid).commit();
            else preferences.edit().remove("mid").commit();
        }
    }
}
