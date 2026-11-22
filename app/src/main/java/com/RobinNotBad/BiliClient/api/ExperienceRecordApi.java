package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExperienceRecordApi {
    private ExperienceRecordApi() { }

    public static final class Record {
        public final long delta;
        public final String time;
        public final String reason;

        private Record(long delta, String time, String reason) {
            this.delta = delta;
            this.time = time;
            this.reason = reason;
        }
    }

    public static final class Failure extends IOException {
        private Failure(String message) { super(message); }
    }

    public static boolean isCurrent(long account, String cookie) {
        return account == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                && AccountManager.sameSession(cookie,
                        SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
    }

    /** Returns true only when the refreshed list contains a new +5 share record. */
    public static boolean hasNewShareReward(List<Record> before, List<Record> after) {
        if (before == null || after == null) return false;
        Set<String> previous = new HashSet<>();
        for (Record record : before) previous.add(recordKey(record));
        for (Record record : after) {
            if (record.delta == 5
                    && record.reason != null
                    && record.reason.contains("分享")
                    && !previous.contains(recordKey(record))) return true;
        }
        return false;
    }

    private static String recordKey(Record record) {
        return record.delta + "\u0000" + record.time + "\u0000" + record.reason;
    }

    public static ArrayList<Record> getRecords(long account, String cookie)
            throws IOException, JSONException {
        String cookieAccount = NetWorkUtil.getInfoFromCookie("DedeUserID", cookie);
        if (account <= 0 || NetWorkUtil.getInfoFromCookie("SESSDATA", cookie).isEmpty()
                || (!cookieAccount.isEmpty() && !cookieAccount.equals(Long.toString(account)))) {
            throw new Failure("请先登录后查看经验记录");
        }
        requireCurrent(account, cookie);
        ArrayList<String> headers = new ArrayList<>(Arrays.asList(
                "Cookie", cookie, "User-Agent", NetWorkUtil.USER_AGENT_WEB,
                "Referer", "https://account.bilibili.com/account/record"));
        JSONObject response = NetWorkUtil.getJson(
                "https://api.bilibili.com/x/member/web/exp/log?jsonp=jsonp&web_location=333.33", headers);
        requireCurrent(account, cookie);
        return parseRecords(response);
    }

    private static void requireCurrent(long account, String cookie) throws Failure {
        if (!isCurrent(account, cookie)) throw new Failure("账号已切换，请刷新后重试");
    }

    static ArrayList<Record> parseRecords(JSONObject response) throws IOException, JSONException {
        int code = response.getInt("code");
        if (code == -101 || code == -111) throw new Failure("登录信息已失效，请重新登录");
        if (code == -352 || code == -412) throw new Failure("B 站要求安全验证，请稍后重试");
        if (code != 0) throw new Failure("经验记录获取失败（" + code + "）");
        JSONObject data = response.getJSONObject("data");
        JSONArray list = data.optJSONArray("list");
        ArrayList<Record> records = new ArrayList<>();
        if (list == null) {
            if (data.has("count") && data.getLong("count") == 0) return records;
            throw new JSONException("经验记录列表缺失");
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            records.add(new Record(item.getLong("delta"), item.getString("time"), item.getString("reason")));
        }
        return records;
    }
}
