package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class AccountManagerInstrumentedTest {
    @Test
    public void storesSwitchesAndDeletesAccountsAtomically() {
        SharedPreferences preferences = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSharedPreferences("default", 0);
        SharedPreferencesUtil.sharedPreferences = preferences;
        String[] keys = new String[]{
                AccountManager.ACCOUNTS_KEY,
                AccountManager.ACTIVE_MID_KEY,
                AccountManager.LOGGED_OUT_KEY,
                AccountManager.PENDING_CONFIRM_KEY,
                SharedPreferencesUtil.cookies,
                SharedPreferencesUtil.mid,
                SharedPreferencesUtil.csrf,
                SharedPreferencesUtil.refresh_token,
                SharedPreferencesUtil.cookie_refresh,
                "dynamic_update_baseline",
                SharedPreferencesUtil.DYNAMIC_UPDATE_NUM,
                SharedPreferencesUtil.MESSAGE_UPDATE_NUM
        };
        Map<String, Object> backup = snapshot(preferences, keys);

        try {
            preferences.edit().remove(AccountManager.ACCOUNTS_KEY).commit();
            setCurrent(preferences, 1001L, "DedeUserID=1001; SESSDATA=session-a; bili_jct=csrf-a", "csrf-a", "refresh-a");
            assertTrue(AccountManager.captureCurrentAccount());
            setCurrent(preferences, 1002L, "DedeUserID=1002; SESSDATA=session-b; bili_jct=csrf-b", "csrf-b", "refresh-b");
            assertTrue(AccountManager.captureCurrentAccount());

            List<AccountManager.SavedAccount> accounts = AccountManager.getAccounts();
            assertEquals(2, accounts.size());

            assertTrue(AccountManager.switchTo(1001L));
            assertEquals(1001L, preferences.getLong(SharedPreferencesUtil.mid, 0));
            assertEquals("DedeUserID=1001; SESSDATA=session-a; bili_jct=csrf-a",
                    preferences.getString(SharedPreferencesUtil.cookies, ""));
            assertEquals("csrf-a", preferences.getString(SharedPreferencesUtil.csrf, ""));
            assertEquals("refresh-a", preferences.getString(SharedPreferencesUtil.refresh_token, ""));

            assertTrue(AccountManager.removeAccount(1002L));
            assertFalse(AccountManager.removeAccount(1001L));
            assertEquals(1, AccountManager.getAccounts().size());
        } finally {
            restore(preferences, keys, backup);
            NetWorkUtil.refreshHeaders();
        }
    }

    private static void setCurrent(SharedPreferences preferences, long mid, String cookies,
                                   String csrf, String refreshToken) {
        preferences.edit()
                .putLong(SharedPreferencesUtil.mid, mid)
                .putString(SharedPreferencesUtil.cookies, cookies)
                .putString(SharedPreferencesUtil.csrf, csrf)
                .putString(SharedPreferencesUtil.refresh_token, refreshToken)
                .commit();
    }

    private static Map<String, Object> snapshot(SharedPreferences preferences, String[] keys) {
        Map<String, Object> result = new HashMap<>();
        Map<String, ?> all = preferences.getAll();
        for (String key : keys) {
            if (all.containsKey(key)) result.put(key, all.get(key));
        }
        return result;
    }

    private static void restore(SharedPreferences preferences, String[] keys,
                                Map<String, Object> backup) {
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : keys) editor.remove(key);
        for (Map.Entry<String, Object> entry : backup.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
        }
        editor.commit();
    }
}
