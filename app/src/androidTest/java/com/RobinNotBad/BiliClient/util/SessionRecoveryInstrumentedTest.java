package com.RobinNotBad.BiliClient.util;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.user.MySpaceActivity;
import com.RobinNotBad.BiliClient.api.CookieRefreshApi;
import com.RobinNotBad.BiliClient.api.UserInfoApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SessionRecoveryInstrumentedTest {
    private static final String A = "DedeUserID=1001; SESSDATA=session-a; bili_jct=csrf-a";
    private static final String B = "DedeUserID=1002; SESSDATA=session-b; bili_jct=csrf-b";
    private static final String NEW_A = "DedeUserID=1001; SESSDATA=session-new; bili_jct=csrf-new";
    private SharedPreferences originalPreferences;
    private SharedPreferences preferences;
    private AtomicReference<OkHttpClient> clientRef;
    private OkHttpClient originalClient;
    private boolean originalRelay;
    private Instrumentation instrumentation;

    @Before public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        originalPreferences = SharedPreferencesUtil.sharedPreferences;
        preferences = instrumentation.getTargetContext().getSharedPreferences("session-recovery-test", 0);
        preferences.edit().clear().commit();
        SharedPreferencesUtil.sharedPreferences = preferences;
        originalRelay = NetWorkUtil.BILI_RELAY_ENABLE;
        NetWorkUtil.BILI_RELAY_ENABLE = false;
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        clientRef = (AtomicReference<OkHttpClient>) field.get(null);
        originalClient = clientRef.get();
        NetWorkUtil.refreshHeaders();
    }

    @After public void tearDown() {
        instrumentation.waitForIdleSync();
        SharedPreferencesUtil.sharedPreferences = originalPreferences;
        clientRef.set(originalClient);
        NetWorkUtil.BILI_RELAY_ENABLE = originalRelay;
        NetWorkUtil.refreshHeaders();
        preferences.edit().clear().commit();
    }

    @Test public void startupRestoresMissingCookieAndIdentityFromSelectedBackup() {
        assertTrue(AccountManager.saveLogin(B, "refresh-b"));
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        preferences.edit().remove("cookies").remove("mid").remove("csrf").remove("refresh_token").commit();
        AccountManager.migrateCurrentAccount();
        assertEquals(1001L, preferences.getLong("mid", 0));
        assertEquals(A, preferences.getString("cookies", ""));
        assertEquals("csrf-a", preferences.getString("csrf", ""));
        assertEquals("refresh-a", preferences.getString("refresh_token", ""));
        assertEquals(A, NetWorkUtil.webHeaders.get(1));
    }

    @Test public void qrLoginResponseCanBootstrapAccountBeforeMidExists() {
        assertTrue(AccountManager.saveLogin("", Arrays.asList(
                "DedeUserID=1001; Path=/",
                "SESSDATA=session-a; Path=/",
                "bili_jct=csrf-a; Path=/"
        ), "refresh-a"));
        assertEquals(1001L, preferences.getLong("mid", 0));
        assertEquals(A, preferences.getString("cookies", ""));
        assertTrue(AccountManager.restoreCurrentAccount());
        assertEquals(A, AccountManager.getAccounts().get(0).cookies);
    }

    @Test public void validCookieRepairsMissingMetadataAndStaleHeaders() {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        preferences.edit().remove("mid").remove("csrf").remove("refresh_token").commit();
        NetWorkUtil.webHeaders.set(1, "");
        assertTrue(AccountManager.restoreCurrentAccount());
        assertEquals(1001L, preferences.getLong("mid", 0));
        assertEquals("csrf-a", preferences.getString("csrf", ""));
        assertEquals("refresh-a", preferences.getString("refresh_token", ""));
        assertEquals(A, NetWorkUtil.webHeaders.get(1));
    }

    @Test public void partialCredentialsCannotOverwriteBackupAndOtherAccountIsNotSelected() {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        preferences.edit().putString("cookies", "DedeUserID=1001; buvid3=device").commit();
        assertFalse(AccountManager.captureCurrentAccount());
        assertEquals(A, AccountManager.getAccounts().get(0).cookies);
        assertTrue(AccountManager.restoreCurrentAccount());
        preferences.edit().putLong("mid", 9999L).putString("cookies", "").commit();
        assertFalse(AccountManager.restoreCurrentAccount());
        assertEquals(9999L, preferences.getLong("mid", 0));
    }

    @Test public void explicitLogoutDoesNotRestoreAnotherSavedAccount() {
        assertTrue(AccountManager.saveLogin(B, "refresh-b"));
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        AccountManager.removeCurrentAccountAndCredentials();
        AccountManager.migrateCurrentAccount();
        assertFalse(AccountManager.restoreCurrentAccount());
        assertEquals(0L, preferences.getLong("mid", 0));
        assertEquals("", preferences.getString("cookies", ""));
        assertEquals(1, AccountManager.getAccounts().size());
        assertTrue(AccountManager.saveLogin(A, "new-login"));
        assertTrue(AccountManager.restoreCurrentAccount());
    }

    @Test public void rejectedActiveSessionAutomaticallyRecoversValidBackup() throws Exception {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        preferences.edit().putString("cookies", NEW_A).commit();
        AccountManager.migrateCurrentAccount();
        assertEquals(A, AccountManager.getAccounts().get(0).cookies);
        AtomicInteger calls = new AtomicInteger();
        installFake(chain -> {
            calls.incrementAndGet();
            boolean valid = A.equals(chain.request().header("Cookie"));
            return response(chain.request(), valid ? profile() : "{\"code\":-101}");
        });
        assertEquals(1001L, UserInfoApi.getCurrentUserInfo().mid);
        assertEquals(3, calls.get());
        assertEquals(A, preferences.getString("cookies", ""));
    }

    @Test public void networkFailureNeverDeletesSessionOrRefreshesIt() throws Exception {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        AtomicInteger calls = new AtomicInteger();
        installFake(chain -> {
            calls.incrementAndGet();
            throw new IOException("simulated offline");
        });
        try {
            UserInfoApi.getCurrentUserInfo();
            fail("Expected offline failure");
        } catch (IOException expected) {
            assertFalse(expected instanceof UserInfoApi.LoginExpiredException);
        }
        assertEquals(2, calls.get());
        assertEquals(A, preferences.getString("cookies", ""));
        assertEquals(A, AccountManager.getAccounts().get(0).cookies);
    }

    @Test public void confirmationFailureKeepsNewPairAndRetriesAfterRestore() throws Exception {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        AtomicInteger confirmations = new AtomicInteger();
        installFake(chain -> {
            if (chain.request().url().encodedPath().endsWith("/cookie/refresh"))
                return rotatedResponse(chain.request());
            assertEquals(NEW_A, chain.request().header("Cookie"));
            if (confirmations.incrementAndGet() == 1) throw new IOException("confirmation disconnected");
            return response(chain.request(), "{\"code\":0}");
        });
        assertTrue(CookieRefreshApi.refreshCookie("refresh-csrf"));
        assertEquals(NEW_A, preferences.getString("cookies", ""));
        assertEquals("refresh-new", preferences.getString("refresh_token", ""));
        assertEquals("refresh-a", preferences.getString(AccountManager.PENDING_CONFIRM_KEY, ""));
        assertEquals(NEW_A, AccountManager.getAccounts().get(0).cookies);
        preferences.edit().remove("cookies").remove("refresh_token").remove(AccountManager.PENDING_CONFIRM_KEY).commit();
        AccountManager.migrateCurrentAccount();
        assertTrue(CookieRefreshApi.confirmPendingRefresh());
        assertEquals(2, confirmations.get());
        assertEquals("", preferences.getString(AccountManager.PENDING_CONFIRM_KEY, ""));
        assertEquals("", AccountManager.getAccounts().get(0).pendingConfirm);
        assertEquals("refresh-new", preferences.getString("refresh_token", ""));
    }

    @Test public void malformedRefreshDoesNotPersistSetCookieOrNewToken() throws Exception {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        installFake(chain -> response(chain.request(), "{\"code\":-101}").newBuilder()
                .addHeader("Set-Cookie", "SESSDATA=bad; Path=/").build());
        assertFalse(CookieRefreshApi.refreshCookie("refresh-csrf"));
        assertEquals(A, preferences.getString("cookies", ""));
        assertEquals("refresh-a", preferences.getString("refresh_token", ""));
    }

    @Test public void switchingDuringRefreshDiscardsOldAccountResult() throws Exception {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        installFake(chain -> {
            assertTrue(AccountManager.saveLogin(B, "refresh-b"));
            return rotatedResponse(chain.request());
        });
        assertFalse(CookieRefreshApi.refreshCookie("refresh-csrf"));
        assertEquals(B, preferences.getString("cookies", ""));
        assertEquals(1002L, preferences.getLong("mid", 0));
    }

    @Test public void expiredProfileRefreshesOnceAndReturnsNewSessionProfile() throws Exception {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        AtomicInteger rotations = new AtomicInteger();
        installFake(chain -> {
            String path = chain.request().url().encodedPath();
            if (path.endsWith("/cookie/info"))
                return response(chain.request(),
                        "{\"code\":0,\"data\":{\"refresh\":true,\"timestamp\":1789732800000}}");
            if (path.startsWith("/correspond/1/"))
                return response(chain.request(), "<html><div id=\"1-name\">refresh-csrf</div></html>");
            if (path.endsWith("/cookie/refresh")) {
                rotations.incrementAndGet();
                return rotatedResponse(chain.request());
            }
            if (path.endsWith("/confirm/refresh")) return response(chain.request(), "{\"code\":0}");
            return response(chain.request(),
                    NEW_A.equals(chain.request().header("Cookie")) ? profile() : "{\"code\":-101}");
        });
        assertEquals(1001L, UserInfoApi.getCurrentUserInfo().mid);
        assertEquals(1, rotations.get());
        assertEquals(NEW_A, preferences.getString("cookies", ""));
    }

    @Test public void invalidLoginImportLeavesCurrentSessionIntact() {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        assertFalse(AccountManager.saveLogin("DedeUserID=1002; bili_jct=csrf", "other-token"));
        assertEquals(A, preferences.getString("cookies", ""));
        assertEquals("refresh-a", preferences.getString("refresh_token", ""));
    }

    @Test public void stalePassportResponseCannotRestoreLoggedOutSession() throws Exception {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        installFake(chain -> {
            AccountManager.removeCurrentAccountAndCredentials();
            return response(chain.request(), "{\"code\":0}").newBuilder()
                    .addHeader("Set-Cookie", "SESSDATA=stale; Path=/")
                    .addHeader("Set-Cookie", "DedeUserID=1001; Path=/").build();
        });
        try (Response ignored = NetWorkUtil.get("https://passport.bilibili.com/test")) {
            assertEquals("", preferences.getString("cookies", ""));
            assertFalse(AccountManager.restoreCurrentAccount());
        }
    }

    @Test public void relayRefreshAlsoStagesCookiesUntilResponseIsValidated() throws Exception {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        NetWorkUtil.BILI_RELAY_ENABLE = true;
        preferences.edit().putBoolean("relay_always_on", true).commit();
        installFake(chain -> {
            assertEquals("passport.bilibili.com", chain.request().header("X-Relay-Target-Host"));
            return response(chain.request(), "{\"code\":-101}").newBuilder()
                    .addHeader("Set-Cookie", "SESSDATA=bad; Path=/").build();
        });
        assertFalse(CookieRefreshApi.refreshCookie("refresh-csrf"));
        assertEquals(A, preferences.getString("cookies", ""));
    }

    @Test public void profileResponseFromPreviousAccountIsRejected() throws Exception {
        assertTrue(AccountManager.saveLogin(A, "refresh-a"));
        installFake(chain -> {
            AccountManager.saveLogin(B, "refresh-b");
            return response(chain.request(), profile());
        });
        try {
            UserInfoApi.getCurrentUserInfo();
            fail("Old profile should not be displayed for the new account");
        } catch (IOException expected) {
            assertFalse(expected instanceof UserInfoApi.LoginExpiredException);
        }
        assertEquals(B, preferences.getString("cookies", ""));
    }

    @Test public void avatarAlwaysRespondsAndCanRetryRecovery() throws Exception {
        assertTrue(AccountManager.saveLogin(A, ""));
        AtomicInteger valid = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        installFake(chain -> {
            calls.incrementAndGet();
            return response(chain.request(), valid.get() == 0 ? "{\"code\":-101}" : profile());
        });
        MySpaceActivity activity = (MySpaceActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), MySpaceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            awaitName(activity, "登录状态未恢复");
            int before = calls.get();
            instrumentation.runOnMainSync(() -> assertTrue(activity.findViewById(R.id.userAvatar).performClick()));
            instrumentation.waitForIdleSync();
            valid.set(1);
            instrumentation.runOnMainSync(() -> assertTrue(activity.findViewById(R.id.userAvatar).performClick()));
            awaitName(activity, "Recovered");
            assertTrue(calls.get() > before);
            instrumentation.runOnMainSync(() -> {
                assertTrue(activity.findViewById(R.id.userAvatar).hasOnClickListeners());
                assertTrue(activity.findViewById(R.id.myinfo).hasOnClickListeners());
            });
        } finally {
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
        }
    }

    private void awaitName(MySpaceActivity activity, String expected) {
        long deadline = SystemClock.elapsedRealtime() + 15000L;
        AtomicReference<String> name = new AtomicReference<>("");
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync(() -> {
                TextView view = activity.findViewById(R.id.userName);
                if (view != null) name.set(view.getText().toString());
            });
            if (expected.equals(name.get())) return;
            SystemClock.sleep(50);
        }
        assertEquals(expected, name.get());
    }

    private void installFake(Interceptor fake) throws Exception {
        Constructor<?> constructor = Class.forName(
                "com.RobinNotBad.BiliClient.util.NetWorkUtil$CookieSaveInterceptor").getDeclaredConstructor();
        constructor.setAccessible(true);
        clientRef.set(new OkHttpClient.Builder()
                .addInterceptor((Interceptor) constructor.newInstance()).addInterceptor(fake).build());
    }

    private static Response response(Request request, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("test").body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
    }

    private static Response rotatedResponse(Request request) {
        return response(request, "{\"code\":0,\"data\":{\"refresh_token\":\"refresh-new\"}}").newBuilder()
                .addHeader("Set-Cookie", "DedeUserID=1001; Path=/")
                .addHeader("Set-Cookie", "SESSDATA=session-new; Path=/")
                .addHeader("Set-Cookie", "bili_jct=csrf-new; Path=/").build();
    }

    private static String profile() {
        return "{\"code\":0,\"data\":{\"mid\":1001,\"name\":\"Recovered\",\"money\":10}}";
    }
}
