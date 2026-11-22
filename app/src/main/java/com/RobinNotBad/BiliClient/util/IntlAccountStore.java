package com.RobinNotBad.BiliClient.util;

/** Keeps BiliBili TV credentials separate from the main-site AccountManager. */
public final class IntlAccountStore {
    private static final String COOKIES = "bstar_intl_cookies_v1";
    private static final String MID = "bstar_intl_mid_v1";

    private IntlAccountStore() {
    }

    public static synchronized boolean save(long mid, String cookies) {
        if (mid <= 0 || cookies == null || cookies.trim().isEmpty()) return false;
        return SharedPreferencesUtil.getSharedPreferences().edit()
                .putLong(MID, mid)
                .putString(COOKIES, cookies)
                .commit();
    }

    public static synchronized long getMid() {
        return SharedPreferencesUtil.getLong(MID, 0);
    }

    public static synchronized String getCookies() {
        return SharedPreferencesUtil.getString(COOKIES, "");
    }

    public static synchronized boolean isLoggedIn() {
        return getMid() > 0 && !getCookies().isEmpty();
    }

    public static synchronized void clear() {
        SharedPreferencesUtil.getSharedPreferences().edit()
                .remove(MID).remove(COOKIES).commit();
    }
}
