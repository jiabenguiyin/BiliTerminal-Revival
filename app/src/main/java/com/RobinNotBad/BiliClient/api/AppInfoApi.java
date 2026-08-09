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
import java.util.Objects;

import okhttp3.Response;

public class AppInfoApi {
    private static final String TERMINAL_PRIMARY_API_BASE = "https://jp.031030.xyz";
    private static final String TERMINAL_BACKUP_API_BASE = "http://121.4.26.60:2000";
    private static final String[] TERMINAL_API_BASES = new String[]{
            TERMINAL_PRIMARY_API_BASE,
            TERMINAL_BACKUP_API_BASE
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

            checkAnnouncement();

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
                MsgUtil.showText("更新公告", context.getResources().getString(R.string.update_tip) + "\n\n更新细节：\n" + ToolsUtil.getUpdateLog(context));
                if (ToolsUtil.isDebugBuild())
                    MsgUtil.showDialog("警告", context.getString(R.string.warning_debug));
                SharedPreferencesUtil.putInt("app_version_last", version);
            }

            if (SharedPreferencesUtil.getInt("app_version_check", 0) < curr) {    //限制一天一次
                Log.e("debug", "检查更新");
                SharedPreferencesUtil.putInt("app_version_check", curr);

                checkUpdate(context, false);
            }
        } catch (IOException e) {
            MsgUtil.showMsg("无法连接到终端公告接口\n也许是服务器宕机了？\n（对软件内容无影响）");
        } catch (Exception e) {
            Log.e("debug-terminal", e.toString());
            MsgUtil.err("终端接口出现问题（不影响软件内容）", e);
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
            try (Response response = NetWorkUtil.postJson(terminalUrl(base, pathAndQuery), data, customHeaders)) {
                return new JSONObject(Objects.requireNonNull(response.body()).string());
            } catch (IOException e) {
                lastIo = e;
                Log.e("terminal-api", "POST failed: " + base + " " + e);
            } catch (JSONException e) {
                lastJson = e;
                Log.e("terminal-api", "POST json failed: " + base + " " + e);
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
            MsgUtil.err("检查更新：", e);
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
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);

            int id = item.getInt("id");
            if (id <= lastAnnouncement) {
                if (id > maxSeenId) maxSeenId = id;
                continue;
            }

            if (id > maxSeenId) maxSeenId = id;
            String title = item.getString("title");
            String content = item.getString("content");
            MsgUtil.showText(title, content);
        }
        if (maxSeenId > lastAnnouncement)
            SharedPreferencesUtil.putInt("app_announcement_last", maxSeenId);
    }

    public static ArrayList<Announcement> getAnnouncementList() throws Exception {
        String url = "/terminal/announcement/get_list";
        JSONObject result = getTerminalJson(url);

        if (result.getInt("code") != 0) throw new Exception("错误：" + result.getString("msg"));
        JSONArray data = result.getJSONArray("data");

        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        ArrayList<Announcement> list = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject section = data.getJSONObject(i);
            Announcement announcement = new Announcement();
            announcement.id = section.getInt("id");
            announcement.ctime = sdf.format(section.getLong("ctime") * 1000);
            announcement.title = section.getString("title");
            announcement.content = section.getString("content");
            list.add(announcement);
        }
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

    public static boolean uploadDiagnostics(String installId, JSONArray events) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("install_id", installId);
            payload.put("events", events);
            JSONObject result = postTerminalJson("/terminal/upload/diagnostics", payload.toString());
            return result.optInt("code", -1) == 200;
        } catch (IOException | JSONException error) {
            return false;
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
