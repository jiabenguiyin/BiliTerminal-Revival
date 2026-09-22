package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class AccountManagerTest {
    @Test public void onlyCompleteIdentityMatchedSessionsAreRecoverable() {
        assertTrue(AccountManager.hasSession("DedeUserID=7; SESSDATA=a; bili_jct=b", 7));
        assertFalse(AccountManager.hasSession("DedeUserID=7; bili_jct=b", 7));
        assertFalse(AccountManager.hasSession("DedeUserID=7; SESSDATA=a; bili_jct=b", 8));
        assertFalse(AccountManager.hasSession("DedeUserID=oops; SESSDATA=a; bili_jct=b", 7));
    }

    @Test public void sessionComparisonIgnoresOnlyNonAuthenticationCookies() {
        String original = "DedeUserID=7; SESSDATA=a; bili_jct=b";
        assertTrue(AccountManager.sameSession(original, original + "; buvid3=updated"));
        assertFalse(AccountManager.sameSession(original, "DedeUserID=7; SESSDATA=c; bili_jct=b"));
    }
    @Test
    public void accountDataRoundTripsWithoutLosingCredentials() {
        AccountManager.SavedAccount source = new AccountManager.SavedAccount();
        source.mid = 123456L;
        source.name = "测试账号";
        source.avatar = "https://example.com/avatar.jpg";
        source.cookies = "DedeUserID=123456; SESSDATA=session; bili_jct=csrf";
        source.csrf = "csrf";
        source.refreshToken = "refresh";
        source.pendingConfirm = "old-refresh";
        source.updatedAt = 42L;
        List<AccountManager.SavedAccount> accounts = new ArrayList<>();
        accounts.add(source);

        List<AccountManager.SavedAccount> restored = AccountManager.decode(
                AccountManager.encode(accounts));

        assertEquals(1, restored.size());
        AccountManager.SavedAccount account = restored.get(0);
        assertEquals(source.mid, account.mid);
        assertEquals(source.name, account.name);
        assertEquals(source.avatar, account.avatar);
        assertEquals(source.cookies, account.cookies);
        assertEquals(source.csrf, account.csrf);
        assertEquals(source.refreshToken, account.refreshToken);
        assertEquals(source.pendingConfirm, account.pendingConfirm);
        assertEquals(source.updatedAt, account.updatedAt);
    }

    @Test
    public void duplicateAndIncompleteAccountsAreIgnored() {
        String encoded = "["
                + "{\"mid\":7,\"cookies\":\"SESSDATA=first\"},"
                + "{\"mid\":7,\"cookies\":\"SESSDATA=duplicate\"},"
                + "{\"mid\":8,\"cookies\":\"\"},"
                + "{\"mid\":0,\"cookies\":\"SESSDATA=invalid\"}"
                + "]";

        List<AccountManager.SavedAccount> restored = AccountManager.decode(encoded);

        assertEquals(1, restored.size());
        assertEquals(7L, restored.get(0).mid);
        assertEquals("SESSDATA=first", restored.get(0).cookies);
    }

    @Test
    public void savedAccountKeepsRefreshTokenWhenCookieSyncHasNoToken() {
        AccountManager.SavedAccount source = new AccountManager.SavedAccount();
        source.mid = 7;
        source.cookies = "DedeUserID=7; SESSDATA=session; bili_jct=csrf";
        source.refreshToken = "refresh";

        List<AccountManager.SavedAccount> restored = AccountManager.decode(
                AccountManager.encode(Collections.singletonList(source)));

        assertEquals("refresh", restored.get(0).refreshToken);
    }

}
