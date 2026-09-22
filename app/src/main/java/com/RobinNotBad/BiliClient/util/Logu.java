package com.RobinNotBad.BiliClient.util;

import android.util.Log;

import java.util.Locale;

public final class Logu {
    public static boolean LOGV_ENABLED = false;
    public static boolean LOGD_ENABLED = false;
    public static boolean LOGI_ENABLED = false;

    private Logu() {
    }

    public static void v(String info) {
        write(Log.VERBOSE, "V", null, info, LOGV_ENABLED);
    }

    public static void i(String info) {
        write(Log.INFO, "I", null, info, LOGI_ENABLED);
    }

    public static void d(String info) {
        write(Log.DEBUG, "D", null, info, LOGD_ENABLED);
    }

    public static void w(String info) {
        write(Log.WARN, "W", null, info, true);
    }

    public static void e(String info) {
        write(Log.ERROR, "E", null, info, true);
    }

    public static void wtf(String info) {
        write(Log.ASSERT, "WTF", null, info, true);
    }

    public static void v(String tag, String info) {
        write(Log.VERBOSE, "V", tag, info, LOGV_ENABLED);
    }

    public static void i(String tag, String info) {
        write(Log.INFO, "I", tag, info, LOGI_ENABLED);
    }

    public static void d(String tag, String info) {
        write(Log.DEBUG, "D", tag, info, LOGD_ENABLED);
    }

    public static void w(String tag, String info) {
        write(Log.WARN, "W", tag, info, true);
    }

    public static void e(String tag, String info) {
        write(Log.ERROR, "E", tag, info, true);
    }

    public static void wtf(String tag, String info) {
        write(Log.ASSERT, "WTF", tag, info, true);
    }

    private static void write(int priority, String level, String explicitTag, String info, boolean logcatEnabled) {
        String caller = getCaller();
        String tag = explicitTag == null || explicitTag.isEmpty() ? caller : caller + "/" + explicitTag;
        String safeInfo = isPrivateTag(explicitTag)
                ? "[private content omitted]"
                : DiagnosticLogManager.sanitizeTextForDiagnostics(info);
        if (logcatEnabled) {
            Log.println(priority, caller, explicitTag == null ? safeInfo : explicitTag + ">" + safeInfo);
        }
        DiagnosticLogManager.recordLog(level, tag, safeInfo);
    }

    private static boolean isPrivateTag(String tag) {
        if (tag == null) return false;
        String normalized = tag.toLowerCase(Locale.US);
        return normalized.contains("cookie")
                || normalized.contains("csrf")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("sessdata")
                || normalized.contains("authorization")
                || normalized.contains("login")
                || normalized.contains("title")
                || normalized.contains("subtitle")
                || normalized.contains("message")
                || normalized.contains("comment")
                || normalized.contains("content")
                || normalized.contains("keyword")
                || normalized.contains("search")
                || normalized.contains("query")
                || normalized.contains("name")
                || normalized.contains("sender")
                || normalized.contains("description")
                || normalized.contains("profile")
                || normalized.contains("标题")
                || normalized.contains("字幕")
                || normalized.contains("私信")
                || normalized.contains("评论")
                || normalized.contains("正文")
                || normalized.contains("搜索")
                || normalized.contains("昵称")
                || normalized.contains("简介")
                || normalized.contains("签名");
    }

    private static String getCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.equals(Logu.class.getName())
                    || className.equals(Thread.class.getName())
                    || className.equals("dalvik.system.VMStack")) continue;
            int index = className.lastIndexOf('.');
            String simpleName = index >= 0 ? className.substring(index + 1) : className;
            return simpleName + ">" + element.getMethodName();
        }
        return "unknown";
    }
}
