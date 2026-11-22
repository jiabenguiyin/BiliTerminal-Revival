package com.RobinNotBad.BiliClient;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.View;
import android.widget.TextView;

/** Framework-only smoke probe for minified releases on Dalvik and ART. */
public class MySpaceReleaseProbe extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            String previous = context.getSharedPreferences("default", Context.MODE_PRIVATE).getString("cookies", "");
            Activity sort = open("activity.user.MySpaceSortActivity");
            try {
                check(view(sort, "recyclerView") != null, "Missing order list");
                check(view(sort, "myspace_order_reset").isShown(), "Reset is not visible");
            } finally { runOnMainSync(sort::finish); waitForIdleSync(); }
            Activity login = open("activity.settings.login.SpecialLoginActivity");
            try {
                check("从文本文件导入".equals(((TextView)view(login, "login_file_label")).getText().toString()), "Wrong file action");
                check(view(login, "login_file_action").isShown(), "File action is not visible");
                check(view(login, "copy").getVisibility() == View.GONE, "Import shows empty copy action");
                check(((TextView)view(login, "loginInput")).getText().length() == 0, "Unexpected import contents");
            } finally { runOnMainSync(login::finish); waitForIdleSync(); }
            android.content.SharedPreferences preferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
            String tutorialKey = "tutorial_ver_" + context.getResources().getStringArray(id(context, "tutorial_list", "array"))[5];
            boolean hadTutorial = preferences.contains(tutorialKey);
            int previousTutorial = preferences.getInt(tutorialKey, -1);
            preferences.edit().putInt(tutorialKey, Integer.MAX_VALUE).commit();
            try {
                Activity messages = open("activity.message.MessageActivity");
                try {
                    long deadline = SystemClock.uptimeMillis() + 8000;
                    while ((messages.findViewById(id(messages, "mark_all_read", "id")) == null
                            || !view(messages, "mark_all_read").isShown())
                            && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
                    waitForIdleSync();
                    inspectOnMain(() -> {
                        View action = view(messages, "mark_all_read");
                        check(action != null && action.isShown(), "Missing toolbar read action");
                        View parent = action;
                        while (parent.getId() != id(messages, "top", "id") && parent.getParent() instanceof View)
                            parent = (View)parent.getParent();
                        check(parent == view(messages, "top"), "Read action is outside toolbar");
                        Rect rect = new Rect(), title = new Rect(), time = new Rect();
                        check(action.getGlobalVisibleRect(rect), "Read action clipped");
                        view(messages, "pageName").getGlobalVisibleRect(title);
                        view(messages, "timeText").getGlobalVisibleRect(time);
                        check(!Rect.intersects(rect, title) && !Rect.intersects(rect, time), "Toolbar overlaps");
                        Rect settings = new Rect();
                        check(view(messages, "setting_btn").getGlobalVisibleRect(settings), "Missing settings action");
                        check(!Rect.intersects(settings, rect) && !Rect.intersects(settings, title)
                                && !Rect.intersects(settings, time), "Settings overlaps toolbar");
                    });
                } finally { runOnMainSync(messages::finish); waitForIdleSync(); }
            } finally {
                android.content.SharedPreferences.Editor restore = preferences.edit();
                if (hadTutorial) restore.putInt(tutorialKey, previousTutorial); else restore.remove(tutorialKey);
                restore.commit();
            }
            check(previous.equals(context.getSharedPreferences("default", Context.MODE_PRIVATE).getString("cookies", "")), "Login changed");
            String[] notes = context.getResources().getStringArray(id(context, "update_log_items", "array"));
            check(notes.length == 4 && notes[0].contains("按钮顺序") && notes[0].contains("文本导入导出"), "Missing release notes");
            check("优化 Android 4.x、低内存设备和智能手表的运行稳定性".equals(notes[3]), "Last note changed");
            result.putBoolean("passed", true);
            result.putString("versionName", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            result.putInt("versionCode", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putBoolean("passed", false);
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void inspectOnMain(Runnable inspection) {
        final Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> { try { inspection.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private Activity open(String name) {
        String pkg = getTargetContext().getPackageName();
        Activity activity = startActivitySync(new Intent().setClassName(pkg, pkg + "." + name)
                .putExtra("login", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync(); return activity;
    }
    private View view(Activity activity, String name) { return activity.findViewById(id(activity, name, "id")); }
    private static int id(Context context, String name, String type) {
        int id = context.getResources().getIdentifier(name, type, context.getPackageName());
        check(id != 0, "Missing resource " + name); return id;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
