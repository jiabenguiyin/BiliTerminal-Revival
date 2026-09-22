package com.RobinNotBad.BiliClient.api;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;

import okhttp3.OkHttpClient;
import okhttp3.Response;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class DeepSeekLiveInstrumentedTest {
    @Test public void flashWithAndWithoutThinkingAuthenticatesAndReplies() throws Exception {
        File keyFile = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getCacheDir(), "deepseek-test-key");
        assumeTrue("Live test requires a user-authorized temporary key", keyFile.isFile());
        OkHttpClient client = NetWorkUtil.setOkHttpSsl(DeepSeekApi.clientBuilder()).build();
        try {
            byte[] data = new byte[256];
            String key;
            try (FileInputStream input = new FileInputStream(keyFile)) {
                int count = input.read(data);
                assertTrue(count > 0 && count < data.length);
                key = new String(data, 0, count, Charset.forName("UTF-8")).trim();
                java.util.Arrays.fill(data, (byte) 0);
            }
            for (boolean thinking : new boolean[]{false, true}) {
                JSONArray messages = new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content",
                                InstrumentationRegistry.getInstrumentation().getTargetContext().getString(R.string.dev_deepseek_prompt)))
                        .put(new JSONObject().put("role", "user").put("content", "Reply only with OK."));
                try (Response response = DeepSeekApi.newCall(client, key, messages, false, thinking).execute()) {
                    assertEquals("DeepSeek status, thinking=" + thinking, 200, response.code());
                    String reply = DeepSeekApi.readReply(response, (reasoning, content) -> {});
                    assertFalse("Expected a nonempty reply", reply.trim().isEmpty());
                }
            }
        } finally {
            assertTrue("Temporary key cleanup", !keyFile.exists() || keyFile.delete());
            client.dispatcher().cancelAll();
            client.connectionPool().evictAll();
        }
    }
}
