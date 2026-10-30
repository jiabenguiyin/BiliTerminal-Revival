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
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/** Framework-only probe for minified releases on pre-ART Android; no AndroidX test linkage. */
public class StableReleaseProbe extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            String marker = args.getString("upgradeMarker");
            check(marker != null, "Missing upgrade marker");
            PackageInfo info = target.getPackageManager().getPackageInfo(target.getPackageName(), PackageManager.GET_SIGNATURES);
            if ("prepare".equals(args.getString("mode"))) {
                check(info.versionCode == 2026103002 && "1.2.3-BETA".equals(info.versionName), "Expected previous Beta APK");
                check(target.getSharedPreferences("stable_release_probe", Context.MODE_PRIVATE).edit()
                        .putString("marker", marker).commit(), "Cannot write upgrade marker");
            } else {
                check(info.versionCode == 2026103003 && "1.2.3".equals(info.versionName), "Wrong stable package identity");
                check((info.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0, "Shipped APK is debuggable");
                check("94b1569cdc216f7b19acb1228e56a3dd8782f0adbc4e87e0d945c536e839f561"
                        .equals(sha(info.signatures[0].toByteArray())), "Signing certificate changed");
                check(marker.equals(target.getSharedPreferences("stable_release_probe", Context.MODE_PRIVATE)
                        .getString("marker", null)), "Existing data was lost on upgrade");
                String[] notes = target.getResources().getStringArray(id(target, "update_log_items", "array"));
                check(notes.length == 5, "Wrong note count");
                check("优化 Android 4.x、低内存设备和智能手表的运行稳定性".equals(notes[4]), "Last note changed");
                StringBuilder text = new StringBuilder();
                for (String note : notes) { if (text.length() > 0) text.append('\n'); text.append(note); }
                check(args.getString("expectedNotesSha256").equals(sha(text.toString().getBytes("UTF-8"))), "Approved notes differ");
                Activity about = startActivitySync(new Intent().setClassName(target.getPackageName(),
                        "com.RobinNotBad.BiliClient.activity.settings.AboutActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                try {
                    int versionId = id(target, "app_version", "id"), codeId = id(target, "app_version_code", "id");
                    int notesId = id(target, "updatelog_view", "id");
                    long deadline = SystemClock.uptimeMillis() + 10000;
                    while (about.findViewById(versionId) == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
                    waitForIdleSync();
                    runOnMainSync(() -> {
                        check("版本名\n1.2.3".equals(((TextView) about.findViewById(versionId)).getText().toString()), "About version differs");
                        check("版本号\n2026103003".equals(((TextView) about.findViewById(codeId)).getText().toString()), "About code differs");
                        String visible = ((TextView) about.findViewById(notesId)).getText().toString();
                        for (String note : notes) check(visible.contains(note), "About note missing");
                        check(visible.endsWith(notes[4]), "About last note differs");
                        save(target, about);
                    });
                } finally { runOnMainSync(about::finish); waitForIdleSync(); }
                target.getSharedPreferences("stable_release_probe", Context.MODE_PRIVATE).edit().remove("marker").commit();
            }
            result.putBoolean("passed", true);
            result.putString("versionName", info.versionName);
            result.putInt("versionCode", info.versionCode);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putBoolean("passed", false);
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static int id(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static String sha(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            text.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return text.toString();
    }
    private static void save(Context context, Activity activity) {
        View root = activity.getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), "stable-about.png"))) {
            root.draw(new Canvas(bitmap)); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
