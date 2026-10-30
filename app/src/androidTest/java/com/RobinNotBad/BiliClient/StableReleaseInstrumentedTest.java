package com.RobinNotBad.BiliClient;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import static org.junit.Assert.*;

/** Checks the actual signed/minified APK, rather than linking against its implementation classes. */
@RunWith(AndroidJUnit4.class)
public class StableReleaseInstrumentedTest {
    private final Instrumentation ui = InstrumentationRegistry.getInstrumentation();
    private final Context target = ui.getTargetContext();
    private final Bundle args = InstrumentationRegistry.getArguments();

    @Test public void prepareUpgradeMarker() {
        String marker = args.getString("upgradeMarker");
        assertNotNull(marker);
        assertTrue(target.getSharedPreferences("stable_release_probe", Context.MODE_PRIVATE)
                .edit().putString("marker", marker).commit());
    }

    @Test public void installedStableAboutMatchesApprovedReleaseAndPreservesData() throws Exception {
        String version = args.getString("expectedVersion", "1.2.3");
        int code = Integer.parseInt(args.getString("expectedCode", "2026103003"));
        PackageInfo info = target.getPackageManager().getPackageInfo(target.getPackageName(), PackageManager.GET_SIGNATURES);
        assertEquals(version, info.versionName);
        assertEquals(code, info.versionCode);
        assertEquals("Shipped APK is debuggable", 0, info.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE);
        assertEquals("94b1569cdc216f7b19acb1228e56a3dd8782f0adbc4e87e0d945c536e839f561",
                sha(info.signatures[0].toByteArray()));
        String marker = args.getString("upgradeMarker");
        assertNotNull(marker);
        assertEquals("Upgrade lost existing application data", marker,
                target.getSharedPreferences("stable_release_probe", Context.MODE_PRIVATE).getString("marker", null));
        int logId = target.getResources().getIdentifier("update_log_items", "array", target.getPackageName());
        String[] logs = target.getResources().getStringArray(logId);
        assertEquals(5, logs.length);
        assertEquals("优化 Android 4.x、低内存设备和智能手表的运行稳定性", logs[4]);
        StringBuilder joined = new StringBuilder();
        for (String line : logs) { if (joined.length() > 0) joined.append('\n'); joined.append(line); }
        assertEquals("Installed update notes differ from the approved five items",
                args.getString("expectedNotesSha256"), sha(joined.toString().getBytes("UTF-8")));
        Activity about = ui.startActivitySync(new Intent().setClassName(target.getPackageName(),
                "com.RobinNotBad.BiliClient.activity.settings.AboutActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            int versionId = id("app_version"), codeId = id("app_version_code"), notesId = id("updatelog_view");
            long until = SystemClock.uptimeMillis() + 10000;
            while (about.findViewById(versionId) == null && SystemClock.uptimeMillis() < until) SystemClock.sleep(40);
            ui.waitForIdleSync();
            ui.runOnMainSync(() -> {
                assertEquals("版本名\n" + version, ((TextView) about.findViewById(versionId)).getText().toString());
                assertEquals("版本号\n" + code, ((TextView) about.findViewById(codeId)).getText().toString());
                String visibleNotes = ((TextView) about.findViewById(notesId)).getText().toString();
                for (String line : logs) assertTrue(visibleNotes.contains(line));
                assertTrue(visibleNotes.endsWith(logs[4]));
                save(about);
            });
        } finally {
            ui.runOnMainSync(about::finish);
            ui.waitForIdleSync();
            target.getSharedPreferences("stable_release_probe", Context.MODE_PRIVATE).edit().remove("marker").commit();
        }
    }

    private int id(String name) { return target.getResources().getIdentifier(name, "id", target.getPackageName()); }
    private String sha(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder text = new StringBuilder();
        for (byte b : hash) text.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return text.toString();
    }
    private void save(Activity activity) {
        View root = activity.getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        try (FileOutputStream output = new FileOutputStream(new File(target.getFilesDir(), "stable-about.png"))) {
            root.draw(new Canvas(bitmap)); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
