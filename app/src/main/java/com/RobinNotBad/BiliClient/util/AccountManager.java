package com.RobinNotBad.BiliClient.util;

import android.content.SharedPreferences;

import com.RobinNotBad.BiliClient.model.UserInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Stores multiple login sessions while keeping the original active credential keys compatible. */
public final class AccountManager {
    static final String ACCOUNTS_KEY = "saved_accounts_v1";
    static final String ACTIVE_MID_KEY = "saved_active_mid";
    static final String LOGGED_OUT_KEY = "account_explicitly_logged_out";
    public static final String PENDING_CONFIRM_KEY = "cookie_pending_confirm";
    private AccountManager() {
    }

    public static final class SavedAccount {
        public long mid;
        public String name = "";
        public String avatar = "";
        String cookies = "";
        String csrf = "";
        String refreshToken = "";
        String pendingConfirm = "";
        long updatedAt;

        public String getDisplayName() {
            return name == null || name.trim().isEmpty() ? "UID " + mid : name;
        }
    }

    public static synchronized void migrateCurrentAccount() {
        if (SharedPreferencesUtil.getBoolean(LOGGED_OUT_KEY, false)) return;
        restoreCurrentAccount();
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (find(loadAccountsInternal(), mid) == null) captureCurrentAccount();
    }

    static long cookieMid(String cookies) {
        try {
            return Long.parseLong(new Cookies(cookies).getOrDefault("DedeUserID", "0"));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    static boolean hasSession(String cookies, long mid) {
        Cookies parsed = new Cookies(cookies);
        return mid > 0 && cookieMid(cookies) == mid
                && !parsed.getOrDefault("SESSDATA", "").isEmpty()
                && !parsed.getOrDefault("bili_jct", "").isEmpty();
    }

    public static boolean sameSession(String first, String second) {
        Cookies left = new Cookies(first);
        Cookies right = new Cookies(second);
        for (String key : new String[]{"DedeUserID", "SESSDATA", "bili_jct"}) {
            if (!left.getOrDefault(key, "").equals(right.getOrDefault(key, ""))) return false;
        }
        return true;
    }

    public static synchronized boolean saveLogin(String cookies, String refreshToken) {
        long mid = cookieMid(cookies);
        if (!hasSession(cookies, mid)) return false;
        List<SavedAccount> accounts = loadAccountsInternal();
        SavedAccount account = find(accounts, mid);
        if (account == null) {
            account = new SavedAccount();
            account.mid = mid;
            accounts.add(account);
        }
        account.cookies = cookies;
        account.csrf = new Cookies(cookies).getOrDefault("bili_jct", "");
        String newRefreshToken = safe(refreshToken);
        if (!newRefreshToken.isEmpty() || account.refreshToken.isEmpty()) {
            account.refreshToken = newRefreshToken;
        }
        account.pendingConfirm = "";
        account.updatedAt = System.currentTimeMillis();
        SharedPreferences.Editor editor = SharedPreferencesUtil.getSharedPreferences().edit()
                .putLong(SharedPreferencesUtil.mid, mid).putLong(ACTIVE_MID_KEY, mid)
                .putBoolean(LOGGED_OUT_KEY, false).putString(SharedPreferencesUtil.cookies, cookies)
                .putString(SharedPreferencesUtil.csrf, account.csrf)
                .putString(SharedPreferencesUtil.refresh_token, account.refreshToken)
                .remove(PENDING_CONFIRM_KEY).putBoolean(SharedPreferencesUtil.cookie_refresh, true)
                .putString(ACCOUNTS_KEY, encode(accounts));
        clearAccountScopedState(editor);
        if (!editor.commit()) return false;
        NetWorkUtil.refreshHeaders();
        return true;
    }

    /**
     * Saves a newly completed web login, including authentication cookies issued
     * by the login response before an active account identity exists locally.
     */
    public static synchronized boolean saveLogin(String existingCookies, List<String> setCookieHeaders,
                                                 String refreshToken) {
        String mergedCookies = CookieMergeUtil.merge(existingCookies, setCookieHeaders, true);
        return saveLogin(mergedCookies, refreshToken);
    }

    /** Repair local metadata first; never select another account after an explicit logout. */
    public static synchronized boolean restoreCurrentAccount() {
        if (SharedPreferencesUtil.getBoolean(LOGGED_OUT_KEY, false)) return false;
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        long remembered = SharedPreferencesUtil.getLong(ACTIVE_MID_KEY, 0);
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        long fromCookie = cookieMid(cookies);
        long target = mid > 0 ? mid : remembered > 0 ? remembered : fromCookie;
        if (hasSession(cookies, target)) {
            SharedPreferences.Editor editor = SharedPreferencesUtil.getSharedPreferences().edit()
                    .putLong(SharedPreferencesUtil.mid, target)
                    .putLong(ACTIVE_MID_KEY, target)
                    .putString(SharedPreferencesUtil.csrf, new Cookies(cookies).getOrDefault("bili_jct", ""))
                    .putBoolean(SharedPreferencesUtil.cookie_refresh, true);
            SavedAccount saved = find(loadAccountsInternal(), target);
            if (saved != null) {
                // A normal Bilibili response can rotate cookies without rotating
                // refresh_token. Keep the token from the saved account even when
                // the active cookie pair was updated separately.
                if (SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "").isEmpty()
                        && !saved.refreshToken.isEmpty())
                    editor.putString(SharedPreferencesUtil.refresh_token, saved.refreshToken);
                if (sameSession(saved.cookies, cookies)
                        && !SharedPreferencesUtil.getSharedPreferences().contains(PENDING_CONFIRM_KEY)
                        && !saved.pendingConfirm.isEmpty())
                    editor.putString(PENDING_CONFIRM_KEY, saved.pendingConfirm);
            }
            editor.commit();
            NetWorkUtil.refreshHeaders();
            return true;
        }
        List<SavedAccount> accounts = loadAccountsInternal();
        SavedAccount saved = find(accounts, target);
        if (target == 0 && accounts.size() == 1) saved = accounts.get(0);
        return saved != null && activate(saved);
    }

    /** Used only after the API rejects the active credentials, without overwriting the backup. */
    public static synchronized boolean restoreRejectedSession(String rejectedCookies) {
        if (SharedPreferencesUtil.getBoolean(LOGGED_OUT_KEY, false)
                || !sameSession(rejectedCookies, SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "")))
            return false;
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        SavedAccount saved = find(loadAccountsInternal(), mid);
        if (saved == null || new Cookies(saved.cookies).getOrDefault("SESSDATA", "").equals(
                new Cookies(rejectedCookies).getOrDefault("SESSDATA", ""))) return false;
        return activate(saved);
    }

    private static boolean activate(SavedAccount account) {
        if (!hasSession(account.cookies, account.mid)) return false;
        SharedPreferences.Editor editor = SharedPreferencesUtil.getSharedPreferences().edit();
        editor.putLong(SharedPreferencesUtil.mid, account.mid);
        editor.putLong(ACTIVE_MID_KEY, account.mid);
        editor.putBoolean(LOGGED_OUT_KEY, false);
        editor.putString(SharedPreferencesUtil.cookies, account.cookies);
        editor.putString(SharedPreferencesUtil.csrf, new Cookies(account.cookies).getOrDefault("bili_jct", ""));
        editor.putString(SharedPreferencesUtil.refresh_token, account.refreshToken);
        editor.putString(PENDING_CONFIRM_KEY, account.pendingConfirm);
        editor.putBoolean(SharedPreferencesUtil.cookie_refresh, true);
        clearAccountScopedState(editor);
        if (!editor.commit()) return false;
        NetWorkUtil.refreshHeaders();
        return true;
    }

    public static synchronized boolean replaceRefreshedSession(String expectedCookies, String expectedToken,
                                                               String newCookies, String newToken) {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (!hasSession(newCookies, mid) || newToken.isEmpty()
                || !sameSession(expectedCookies, SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
                || !expectedToken.equals(SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, ""))
                || SharedPreferencesUtil.getBoolean(LOGGED_OUT_KEY, false)) return false;
        List<SavedAccount> accounts = loadAccountsInternal();
        SavedAccount account = find(accounts, mid);
        if (account == null) {
            account = new SavedAccount();
            account.mid = mid;
            accounts.add(account);
        }
        account.cookies = newCookies;
        account.csrf = new Cookies(newCookies).getOrDefault("bili_jct", "");
        account.refreshToken = newToken;
        account.pendingConfirm = expectedToken;
        account.updatedAt = System.currentTimeMillis();
        boolean saved = SharedPreferencesUtil.getSharedPreferences().edit()
                .putString(SharedPreferencesUtil.cookies, newCookies)
                .putString(SharedPreferencesUtil.csrf, account.csrf)
                .putString(SharedPreferencesUtil.refresh_token, newToken)
                .putString(PENDING_CONFIRM_KEY, expectedToken)
                .putLong(ACTIVE_MID_KEY, mid)
                .putBoolean(SharedPreferencesUtil.cookie_refresh, true)
                .putString(ACCOUNTS_KEY, encode(accounts)).commit();
        NetWorkUtil.refreshHeaders();
        return saved;
    }

    public static synchronized void finishRefreshConfirmation(String cookies, String oldToken) {
        if (!sameSession(cookies, SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
                || !oldToken.equals(SharedPreferencesUtil.getString(PENDING_CONFIRM_KEY, ""))) return;
        SharedPreferencesUtil.getSharedPreferences().edit().remove(PENDING_CONFIRM_KEY).commit();
        captureCurrentAccount();
    }

    public static synchronized boolean captureCurrentAccount() {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        if (!hasSession(cookies, mid)) return false;

        List<SavedAccount> accounts = loadAccountsInternal();
        SavedAccount account = find(accounts, mid);
        if (account == null) {
            account = new SavedAccount();
            account.mid = mid;
            accounts.add(account);
        }
        account.cookies = cookies;
        account.csrf = SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
        String refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
        if (!refreshToken.isEmpty()) account.refreshToken = refreshToken;
        account.pendingConfirm = SharedPreferencesUtil.getString(PENDING_CONFIRM_KEY, "");
        account.updatedAt = System.currentTimeMillis();
        return SharedPreferencesUtil.getSharedPreferences().edit()
                .putString(ACCOUNTS_KEY, encode(accounts))
                .putLong(ACTIVE_MID_KEY, mid).putBoolean(LOGGED_OUT_KEY, false).commit();
    }

    /**
     * Persists a server-issued cookie update without changing the selected
     * account or treating an ordinary response as a new login.
     */
    public static synchronized void syncCurrentCookies(String cookies) {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (!hasSession(cookies, mid)
                || SharedPreferencesUtil.getBoolean(LOGGED_OUT_KEY, false)) return;
        SharedPreferences.Editor editor = SharedPreferencesUtil.getSharedPreferences().edit()
                .putString(SharedPreferencesUtil.cookies, cookies)
                .putString(SharedPreferencesUtil.csrf,
                        new Cookies(cookies).getOrDefault("bili_jct", ""))
                .putBoolean(SharedPreferencesUtil.cookie_refresh, true);
        editor.commit();

        List<SavedAccount> accounts = loadAccountsInternal();
        SavedAccount account = find(accounts, mid);
        if (account == null) {
            account = new SavedAccount();
            account.mid = mid;
            accounts.add(account);
        }
        account.cookies = cookies;
        account.csrf = new Cookies(cookies).getOrDefault("bili_jct", "");
        String refreshToken = SharedPreferencesUtil.getString(SharedPreferencesUtil.refresh_token, "");
        if (!refreshToken.isEmpty() || account.refreshToken.isEmpty()) account.refreshToken = refreshToken;
        String pendingConfirm = SharedPreferencesUtil.getString(PENDING_CONFIRM_KEY, "");
        if (!pendingConfirm.isEmpty() || account.pendingConfirm.isEmpty()) account.pendingConfirm = pendingConfirm;
        account.updatedAt = System.currentTimeMillis();
        saveAccountsInternal(accounts);
        NetWorkUtil.refreshHeaders();
    }

    public static synchronized void updateCurrentProfile(UserInfo userInfo) {
        if (userInfo == null || userInfo.mid <= 0) return;
        if (userInfo.mid != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
        captureCurrentAccount();
        List<SavedAccount> accounts = loadAccountsInternal();
        SavedAccount account = find(accounts, userInfo.mid);
        if (account == null) return;
        account.name = safe(userInfo.name);
        account.avatar = safe(userInfo.avatar);
        account.updatedAt = System.currentTimeMillis();
        saveAccountsInternal(accounts);
    }

    public static synchronized List<SavedAccount> getAccounts() {
        List<SavedAccount> accounts = loadAccountsInternal();
        Collections.sort(accounts, new Comparator<SavedAccount>() {
            @Override
            public int compare(SavedAccount left, SavedAccount right) {
                if (right.updatedAt == left.updatedAt) return 0;
                return right.updatedAt > left.updatedAt ? 1 : -1;
            }
        });
        return accounts;
    }

    public static synchronized boolean switchTo(long targetMid) {
        long currentMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (targetMid <= 0 || targetMid == currentMid) return false;
        migrateCurrentAccount();

        SavedAccount target = find(loadAccountsInternal(), targetMid);
        if (target == null || target.cookies.isEmpty()) return false;

        if (!activate(target)) return false;

        target.updatedAt = System.currentTimeMillis();
        List<SavedAccount> accounts = loadAccountsInternal();
        SavedAccount stored = find(accounts, target.mid);
        if (stored != null) stored.updatedAt = target.updatedAt;
        saveAccountsInternal(accounts);
        NetWorkUtil.refreshHeaders();
        return true;
    }

    public static synchronized boolean removeAccount(long targetMid) {
        if (targetMid <= 0 || targetMid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
            return false;
        }
        List<SavedAccount> accounts = loadAccountsInternal();
        boolean removed = false;
        for (int i = accounts.size() - 1; i >= 0; i--) {
            if (accounts.get(i).mid == targetMid) {
                accounts.remove(i);
                removed = true;
            }
        }
        if (removed) saveAccountsInternal(accounts);
        return removed;
    }

    public static synchronized void removeCurrentAccountAndCredentials() {
        long currentMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        List<SavedAccount> accounts = loadAccountsInternal();
        for (int i = accounts.size() - 1; i >= 0; i--) {
            if (accounts.get(i).mid == currentMid) accounts.remove(i);
        }
        SharedPreferences.Editor editor = SharedPreferencesUtil.getSharedPreferences().edit();
        editor.putString(ACCOUNTS_KEY, encode(accounts));
        editor.putBoolean(LOGGED_OUT_KEY, true);
        editor.remove(ACTIVE_MID_KEY);
        editor.remove(PENDING_CONFIRM_KEY);
        editor.remove(SharedPreferencesUtil.cookies);
        editor.remove(SharedPreferencesUtil.mid);
        editor.remove(SharedPreferencesUtil.csrf);
        editor.remove(SharedPreferencesUtil.refresh_token);
        editor.remove(SharedPreferencesUtil.cookie_refresh);
        clearAccountScopedState(editor);
        editor.commit();
        NetWorkUtil.refreshHeaders();
    }

    private static void clearAccountScopedState(SharedPreferences.Editor editor) {
        editor.remove("dynamic_update_baseline");
        editor.putInt(SharedPreferencesUtil.DYNAMIC_UPDATE_NUM, 0);
        editor.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0);
    }

    private static SavedAccount find(List<SavedAccount> accounts, long mid) {
        for (SavedAccount account : accounts) {
            if (account.mid == mid) return account;
        }
        return null;
    }

    static String encode(List<SavedAccount> accounts) {
        JSONArray array = new JSONArray();
        for (SavedAccount account : accounts) {
            if (account == null || account.mid <= 0 || account.cookies.isEmpty()) continue;
            JSONObject object = new JSONObject();
            try {
                object.put("mid", account.mid);
                object.put("name", safe(account.name));
                object.put("avatar", safe(account.avatar));
                object.put("cookies", safe(account.cookies));
                object.put("csrf", safe(account.csrf));
                object.put("refresh_token", safe(account.refreshToken));
                object.put("pending_confirm", safe(account.pendingConfirm));
                object.put("updated_at", account.updatedAt);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    static List<SavedAccount> decode(String encoded) {
        List<SavedAccount> accounts = new ArrayList<>();
        if (encoded == null || encoded.trim().isEmpty()) return accounts;
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                SavedAccount account = new SavedAccount();
                account.mid = object.optLong("mid", 0);
                account.name = object.optString("name", "");
                account.avatar = object.optString("avatar", "");
                account.cookies = object.optString("cookies", "");
                account.csrf = object.optString("csrf", "");
                account.refreshToken = object.optString("refresh_token", "");
                account.pendingConfirm = object.optString("pending_confirm", "");
                account.updatedAt = object.optLong("updated_at", 0);
                if (account.mid > 0 && !account.cookies.isEmpty() && find(accounts, account.mid) == null) {
                    accounts.add(account);
                }
            }
        } catch (JSONException ignored) {
        }
        return accounts;
    }

    private static List<SavedAccount> loadAccountsInternal() {
        return decode(SharedPreferencesUtil.getString(ACCOUNTS_KEY, ""));
    }

    private static void saveAccountsInternal(List<SavedAccount> accounts) {
        SharedPreferencesUtil.putString(ACCOUNTS_KEY, encode(accounts));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
