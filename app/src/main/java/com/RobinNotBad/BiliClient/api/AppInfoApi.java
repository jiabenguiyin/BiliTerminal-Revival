package com.RobinNotBad.BiliClient.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.BuildConfig;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.update.UpdateInfoActivity;
import com.RobinNotBad.BiliClient.model.Announcement;
import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.HotConfigManager;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Objects;

import okhttp3.Response;

public class AppInfoApi {
    private static final String FEEDBACK_GROUP_NOTICE =
            "\n\n--------------------\n反馈群：1107953621";

    /** Adds the feedback group to update notices without duplicating it. */
    public static String appendFeedbackGroup(String content) {
        if (content == null) content = "";
        return content.contains("1107953621") ? content : content + FEEDBACK_GROUP_NOTICE;
    }
    private static final String TERMINAL_PRIMARY_API_BASE = "https://jp.031030.xyz";
    private static final String ANNOUNCEMENT_CACHE_KEY = "terminal_announcement_cache";
    private static final String[] TERMINAL_API_BASES = new String[]{
            TERMINAL_PRIMARY_API_BASE
    };

    public static void check(Context context) {
        // 讲真这免责声明没啥卵用，写这个也就是半开玩笑的，难道免责声明能挡住律师函吗
        // 而且"fuck_uncle"过分了嗷，咱做第三方软件的真不能这么干……
        if (!SharedPreferencesUtil.getBoolean("disclaimer_shown", false)) {
            MsgUtil.showDialog("免责声明", "使用前请先阅读：\n" + context.getString(R.string.about_to_uncle), 3);
            SharedPreferencesUtil.putBoolean("disclaimer_shown", true);
        }

        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NIGHT_REMINDER_ENABLE, true)) {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            if (hour >= 23 || hour <= 3) {
                MsgUtil.showDialog("温馨提醒", "夜深了，要注意休息呐~", 3);
            }
        }

        try {
            int version = BiliTerminal.getVersion();
            int curr = ConfInfoApi.getDateCurr();

            try {
                checkAnnouncement();
            } catch (Exception announcementError) {
                Log.w("terminal-api", "Announcement check failed", announcementError);
            }

            int last_ver = SharedPreferencesUtil.getInt("app_version_last", 0);
            if (last_ver < version) {
                String update_apk = SharedPreferencesUtil.getString("terminal_update_pkg", "");
                if (!TextUtils.isEmpty(update_apk)) {
                    File file = new File(update_apk);
                    if (file.exists()) {
                        if (file.delete()) {
                            SharedPreferencesUtil.putString("terminal_update_pkg", "");
                            MsgUtil.showMsg("更新包已删除");
                        } else MsgUtil.showMsg("更新包删除失败");
                    } else SharedPreferencesUtil.putString("terminal_update_pkg", "");
                }

                MsgUtil.showDialog("提醒", context.getString(R.string.text_update_success), 5);

                if (last_ver != 0) {
                    if (last_ver < 20240606)
                        MsgUtil.showDialog("部分风控问题已解决", "当前的新版本实现了对抗部分类型的风控，建议您重新登录账号以确保成功使用");

                    if (last_ver < 20250329 && SharedPreferencesUtil.getBoolean("player_ui_round", false)) {
                        SharedPreferencesUtil.putInt("paddingV_percent", 3);
                        SharedPreferencesUtil.putInt("paddingH_percent", 7);
                    }
                }
                MsgUtil.showText("更新公告", appendFeedbackGroup(
                        context.getResources().getString(R.string.update_tip)
                                + "\n\n更新细节：\n" + ToolsUtil.getUpdateLog(context)));
                if (ToolsUtil.isDebugBuild())
                    MsgUtil.showDialog("警告", context.getString(R.string.warning_debug));
                SharedPreferencesUtil.putInt("app_version_last", version);
            }

            if (SharedPreferencesUtil.getInt("app_version_check", 0) < curr) {    //限制一天一次
                Log.e("debug", "检查更新");
                SharedPreferencesUtil.putInt("app_version_check", curr);

                checkUpdate(context, false);
            }
        } catch (Exception e) {
            Log.e("debug-terminal", e.toString());
            // The terminal service is optional. Its outage or an old Android
            // TLS limitation must never be shown as a Bilibili network failure.
            Log.w("debug-terminal", "terminal service unavailable; app continues", e);
        }
    }

    public static final ArrayList<String> customHeaders = new ArrayList<>() {{
        add("User-Agent");
        add(NetWorkUtil.USER_AGENT_WEB);    //防止携带b站cookies导致可能存在的开发者盗号问题（
        add("App-Info");
        try {
            add(new JSONObject()
                    .put("versionName", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .put("isBeta", BuildConfig.BETA)
                    .put("applicationId", BuildConfig.APPLICATION_ID)
                    .put("buildType", BuildConfig.BUILD_TYPE)
                    .put("debugEnabled", BuildConfig.DEBUG)
                    .toString());
        } catch (JSONException e) {
            MsgUtil.showMsg("版本信息json生成出错\n无影响，正常情况下你应该不会遇到");
        }
        add("Device-Info");
        try {
            add(new JSONObject()
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("release", Build.VERSION.RELEASE)
                    .put("product", Build.PRODUCT)
                    .put("brand", Build.BRAND)
                    .put("device", Build.DEVICE)
                    .put("type", Build.TYPE)
                    .put("id", Build.ID)
                    .toString());
        } catch (JSONException e) {
            MsgUtil.showMsg("设备信息json生成出错\n无影响，正常情况下你应该不会遇到");
        }
    }};

    private static String terminalUrl(String base, String pathAndQuery) {
        return base + (pathAndQuery.startsWith("/") ? pathAndQuery : "/" + pathAndQuery);
    }

    private static JSONObject getTerminalJson(String pathAndQuery) throws IOException, JSONException {
        IOException lastIo = null;
        JSONException lastJson = null;
        for (String base : TERMINAL_API_BASES) {
            try {
                return NetWorkUtil.getJson(terminalUrl(base, pathAndQuery), customHeaders);
            } catch (IOException e) {
                lastIo = e;
                Log.e("terminal-api", "GET failed: " + base + " " + e);
            } catch (JSONException e) {
                lastJson = e;
                Log.e("terminal-api", "GET json failed: " + base + " " + e);
            }
        }
        if (lastJson != null) throw lastJson;
        if (lastIo != null) throw lastIo;
        throw new IOException("terminal server unavailable");
    }

    private static JSONObject postTerminalJson(String pathAndQuery, String data) throws IOException, JSONException {
        IOException lastIo = null;
        JSONException lastJson = null;
        for (String base : TERMINAL_API_BASES) {
            int attempts = TERMINAL_PRIMARY_API_BASE.equals(base) ? 2 : 1;
            for (int attempt = 0; attempt < attempts; attempt++) {
                try (Response response = NetWorkUtil.postJson(terminalUrl(base, pathAndQuery), data, customHeaders)) {
                    if (response.body() == null) {
                        throw new IOException("终端接口返回为空");
                    }
                    String responseBody = response.body().string();
                    if (TextUtils.isEmpty(responseBody)) {
                        throw new IOException("终端接口返回为空");
                    }
                    JSONObject result = new JSONObject(responseBody);
                    // Keep structured server errors (including HTTP 429) so callers
                    // can distinguish rejection from a real transport failure.
                    if (!response.isSuccessful() && !result.has("code")) {
                        throw new IOException("终端接口 HTTP " + response.code());
                    }
                    return result;
                } catch (IOException e) {
                    lastIo = e;
                    Log.e("terminal-api", "POST failed: " + base + " attempt=" + (attempt + 1)
                            + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
                } catch (JSONException e) {
                    lastJson = e;
                    Log.e("terminal-api", "POST json failed: " + base + " attempt=" + (attempt + 1));
                }
            }
        }
        if (lastJson != null) throw lastJson;
        if (lastIo != null) throw lastIo;
        throw new IOException("terminal server unavailable");
    }

    private static void checkUpdate(Context context, boolean need_toast, boolean debug_ver) {
        try {
            boolean realIsDebug = ToolsUtil.isDebugBuild();
            String url = "/terminal/version/get_last";
            if (debug_ver) url += BuildConfig.BETA ? "?channel=beta" : "?debug";
            JSONObject result = getTerminalJson(url);

            if (result.getInt("code") != 0) throw new Exception(result.getString("msg"));
            JSONObject data = result.getJSONObject("data");

            String version_name = data.getString("version_name");
            String update_log = data.getString("update_log");
            int latest = data.getInt("version_code");
            long ctime = data.optLong("ctime", -1);
            int can_download = data.optInt("can_download", 0);
            int is_release = data.optInt("is_release", 0);

            String currentVersionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            int version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
            if (isNewerVersion(version_name, latest, currentVersionName, version)) {
                MsgUtil.showMsg(debug_ver ? "发现新的测试版！" : "发现新版本！");
                context.startActivity(new Intent(context, UpdateInfoActivity.class)
                        .putExtra("versionName", version_name)
                        .putExtra("versionCode", latest)
                        .putExtra("updateLog", update_log)
                        .putExtra("ctime", ctime)
                        .putExtra("isRelease", is_release)
                        .putExtra("canDownload", can_download));
                return;
            } else if (need_toast && !(realIsDebug && !debug_ver)) {
                MsgUtil.showMsg(debug_ver ? "没有新的测试版了！" : "当前是最新版本！");
            }
            if (realIsDebug && !debug_ver) {
                checkUpdate(context, need_toast, true);
            }
        } catch (IOException | JSONException e) {
            Log.w("terminal-api", "update check unavailable", e);
            if (need_toast) MsgUtil.showMsg("更新服务暂不可用，不影响应用使用");
        } catch (Exception e) {
            MsgUtil.showMsg(e.getMessage());
        }
    }

    public static void checkUpdate(Context context, boolean need_toast) {
        checkUpdate(context, need_toast, false);
    }

    private static boolean isNewerVersion(String latestName, int latestCode, String currentName, int currentCode) {
        return latestCode > currentCode || compareVersionName(latestName, currentName) > 0;
    }

    private static int compareVersionName(String latestName, String currentName) {
        int[] latest = parseVersionName(latestName);
        int[] current = parseVersionName(currentName);
        int len = Math.max(latest.length, current.length);
        for (int i = 0; i < len; i++) {
            int latestPart = i < latest.length ? latest[i] : 0;
            int currentPart = i < current.length ? current[i] : 0;
            if (latestPart != currentPart) return latestPart - currentPart;
        }
        return 0;
    }

    private static int[] parseVersionName(String versionName) {
        if (TextUtils.isEmpty(versionName)) return new int[]{0};
        String clean = versionName.split("-", 2)[0];
        String[] parts = clean.split("\\.");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {
                values[i] = 0;
            }
        }
        return values;
    }

    public static HotConfigManager.UpdatePlan getUpdatePlan(int versionCode) throws Exception {
        return HotConfigManager.getVerifiedUpdatePlan(versionCode);
    }

    public static String getDownloadUrl(int versionCode) throws Exception {
        return getUpdatePlan(versionCode).fullUrl;
    }

    public static void checkAnnouncement() throws Exception {
        int lastAnnouncement = SharedPreferencesUtil.getInt("app_announcement_last", -1);
        String url = "/terminal/announcement/get_list?from=" + lastAnnouncement;
        JSONObject result = getTerminalJson(url);

        if (result.getInt("code") != 0) throw new Exception("错误：" + result.getString("msg"));
        JSONArray data = result.getJSONArray("data");
        int maxSeenId = lastAnnouncement;
        JSONObject newest = null;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);

            int id = item.getInt("id");
            if (id > maxSeenId) maxSeenId = id;
            if (lastAnnouncement < 0) {
                if (newest == null || id > newest.optInt("id", -1)) newest = item;
                continue;
            }
            if (id <= lastAnnouncement) continue;

            String title = item.getString("title");
            String content = item.getString("content");
            MsgUtil.showText(title, content);
        }
        if (lastAnnouncement < 0 && newest != null) {
            MsgUtil.showText(newest.optString("title", "最新公告"), newest.optString("content", ""));
        }
        if (maxSeenId > lastAnnouncement)
            SharedPreferencesUtil.putInt("app_announcement_last", maxSeenId);
    }

    public static ArrayList<Announcement> getAnnouncementList() throws Exception {
        JSONArray data;
        try {
            JSONObject result = getTerminalJson("/terminal/announcement/get_list");
            if (result.optInt("code", -1) != 0)
                throw new Exception("错误：" + result.optString("msg", "公告接口返回异常"));
            data = result.optJSONArray("data");
            if (data == null) throw new JSONException("公告列表为空");
            SharedPreferencesUtil.putString(ANNOUNCEMENT_CACHE_KEY, data.toString());
        } catch (Exception freshError) {
            String cached = SharedPreferencesUtil.getString(ANNOUNCEMENT_CACHE_KEY, "");
            if (TextUtils.isEmpty(cached)) throw freshError;
            try {
                data = new JSONArray(cached);
                Log.w("terminal-api", "Using cached announcement list", freshError);
            } catch (JSONException cacheError) {
                SharedPreferencesUtil.putString(ANNOUNCEMENT_CACHE_KEY, "");
                throw freshError;
            }
        }

        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        ArrayList<Announcement> list = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject section = data.optJSONObject(i);
            if (section == null) continue;
            Announcement announcement = new Announcement();
            announcement.id = section.optInt("id", -1);
            long timestamp = section.optLong("ctime", 0L);
            if (timestamp > 0L && timestamp < 100000000000L) timestamp *= 1000L;
            announcement.ctime = timestamp > 0L ? sdf.format(timestamp) : "时间未知";
            announcement.title = section.optString("title", "未命名公告");
            announcement.content = section.optString("content", "");
            list.add(announcement);
        }
        Collections.sort(list, (left, right) -> left.id == right.id ? 0 : (left.id < right.id ? 1 : -1));
        return list;
    }

    public static ApiResult uploadStack(String stack, Context context) {
        //上传崩溃堆栈
        try {
            JSONObject post_data = new JSONObject();
            post_data.put("stack", stack);
            post_data.put("client_version", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
            post_data.put("device_sdk", Build.VERSION.SDK_INT);
            post_data.put("device_product", Build.PRODUCT);
            post_data.put("device_brand", Build.BRAND);

            JSONObject res = postTerminalJson("/terminal/upload/stack", post_data.toString());
            String msg = (res.getInt("code") == 200) ? "" : res.getString("msg");
            int id = res.optInt("id", -1);
            return new ApiResult(id, msg);
        } catch (IOException e) {
            return new ApiResult(-1, context.getString(R.string.err_network));
        } catch (JSONException e) {
            return new ApiResult(-514, context.getString(R.string.err_crash_upload_json));
        } catch (PackageManager.NameNotFoundException e) {
            return new ApiResult(-1919, "");
        }
    }

    public static DiagnosticUploadResult uploadDiagnostics(String installId, JSONArray events) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("install_id", installId);
            payload.put("events", events);
            JSONObject result = postTerminalJson("/terminal/upload/diagnostics", payload.toString());
            int code = result.optInt("code", -1);
            if (code == 200) return DiagnosticUploadResult.success();
            String message = result.optString("msg", "服务器未接受日志");
            Log.e("terminal-api", "diagnostics rejected: code=" + code + ", message=" + message);
            return DiagnosticUploadResult.serverRejected(code, message);
        } catch (IOException error) {
            Log.e("terminal-api", "diagnostics network failed: " + error.getClass().getSimpleName());
            return DiagnosticUploadResult.networkError(error.getClass().getSimpleName());
        } catch (JSONException error) {
            Log.e("terminal-api", "diagnostics response invalid: " + error.getClass().getSimpleName());
            return DiagnosticUploadResult.serverRejected(-1, "服务器响应格式异常");
        }
    }

    public static final class DiagnosticUploadResult {
        public enum FailureType {
            NONE,
            NETWORK,
            SERVER_REJECTED
        }

        public final boolean success;
        public final FailureType failureType;
        public final int serverCode;
        public final String message;

        private DiagnosticUploadResult(boolean success, FailureType failureType,
                                       int serverCode, String message) {
            this.success = success;
            this.failureType = failureType;
            this.serverCode = serverCode;
            this.message = message == null ? "" : message;
        }

        static DiagnosticUploadResult success() {
            return new DiagnosticUploadResult(true, FailureType.NONE, 200, "");
        }

        static DiagnosticUploadResult networkError(String message) {
            return new DiagnosticUploadResult(false, FailureType.NETWORK, -1, message);
        }

        static DiagnosticUploadResult serverRejected(int code, String message) {
            return new DiagnosticUploadResult(false, FailureType.SERVER_REJECTED, code, message);
        }
    }

    public static int getSponsors(ArrayList<UserInfo> list, int page) throws Exception {
        String url = "/terminal/afdian/get_sponsor?page=" + page;
        JSONObject result = getTerminalJson(url);

        if (result.getInt("code") != 200) throw new Exception("获取失败");
        JSONArray data = result.getJSONArray("data");

        if (data.length() == 0) return 1;

        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");

        for (int i = 0; i < data.length(); i++) {
            JSONObject sponsor = data.getJSONObject(i);

            UserInfo user = new UserInfo();

            user.name = sponsor.getString("name");
            user.avatar = sponsor.getString("avatar");
            user.sign = "总金额：" + sponsor.getInt("sum_amount") + "r | 捐赠时间：" + sdf.format(sponsor.getLong("last_time") * 1000);
            user.mid = -1;
            user.fans = 0;
            user.followed = true;
            user.level = 6;
            user.notice = "";
            user.official = 0;
            user.officialDesc = "";

            list.add(user);
        }
        return 0;
    }
}
