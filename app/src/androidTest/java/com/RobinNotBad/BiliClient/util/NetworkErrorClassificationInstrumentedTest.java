package com.RobinNotBad.BiliClient.util;

import android.app.Instrumentation;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.api.PrivateMsgApi;
import com.RobinNotBad.BiliClient.event.SnackEvent;

import org.greenrobot.eventbus.EventBus;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NetworkErrorClassificationInstrumentedTest {
    @Test public void privateSessionBusinessErrorIsNotReportedAsNetworkFailure() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Field field = NetWorkUtil.class.getDeclaredField("INSTANCE");
        field.setAccessible(true);
        AtomicReference<OkHttpClient> clients = (AtomicReference<OkHttpClient>) field.get(null);
        OkHttpClient original = clients.get();
        boolean oldSnackbar = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true);
        SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true);
        clients.set(new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"),
                        "{\"code\":-412,\"message\":\"fixture secret\"}"))
                .build()).build());
        try {
            try {
                PrivateMsgApi.getSessionsList(20, 0);
                fail("Expected business failure");
            } catch (PrivateMsgApi.SessionApiException error) {
                assertEquals(-412, error.code);
                instrumentation.runOnMainSync(() -> MsgUtil.err(error));
                instrumentation.waitForIdleSync();
                SnackEvent message = EventBus.getDefault().getStickyEvent(SnackEvent.class);
                assertNotNull(message);
                assertTrue(message.getMessage().contains("-412"));
                assertFalse(message.getMessage().contains("网络错误"));
                assertFalse(message.getMessage().contains("fixture secret"));
            }
            instrumentation.runOnMainSync(() -> MsgUtil.err(new IOException("private diagnostic text")));
            instrumentation.waitForIdleSync();
            SnackEvent message = EventBus.getDefault().getStickyEvent(SnackEvent.class);
            assertNotNull(message);
            assertTrue(message.getMessage().contains("网络错误"));
            assertFalse(message.getMessage().contains("private diagnostic text"));
        } finally {
            EventBus.getDefault().removeStickyEvent(SnackEvent.class);
            clients.set(original);
            SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, oldSnackbar);
        }
    }
}
