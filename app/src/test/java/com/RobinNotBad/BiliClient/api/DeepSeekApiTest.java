package com.RobinNotBad.BiliClient.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.junit.Assert.*;

public class DeepSeekApiTest {
    private static Response response(Request request, int status, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("test").body(ResponseBody.create(MediaType.parse("text/event-stream"), body)).build();
    }

    @Test public void bearerHeaderSurvivesAndBilibiliCredentialsAreAbsent() throws Exception {
        AtomicReference<Request> sent = new AtomicReference<>();
        OkHttpClient client = DeepSeekApi.clientBuilder().addInterceptor(chain -> {
            sent.set(chain.request());
            return response(chain.request(), 200, "");
        }).build();
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", "hello"));
        try (Response ignored = DeepSeekApi.newCall(client, "  sk-fake-unit-test  ", messages, false, false).execute()) {
            Request request = sent.get();
            assertEquals("https://api.deepseek.com/chat/completions", request.url().toString());
            assertEquals("Bearer sk-fake-unit-test", request.header("Authorization"));
            assertNull(request.header("Cookie"));
            assertNull(request.header("X-Relay-Token"));
            Buffer body = new Buffer();
            request.body().writeTo(body);
            JSONObject json = new JSONObject(body.readUtf8());
            assertEquals("deepseek-flash", json.getString("model"));
            assertEquals("disabled", json.getJSONObject("thinking").getString("type"));
            assertFalse(json.has("reasoning_effort"));
            assertFalse(client.followRedirects());
            assertFalse(client.followSslRedirects());
        }
    }

    @Test public void proThinkingParametersAreIndependent() throws Exception {
        Request request = DeepSeekApi.newCall(DeepSeekApi.clientBuilder().build(),
                "sk-test", new JSONArray(), true, true).request();
        Buffer body = new Buffer();
        request.body().writeTo(body);
        JSONObject json = new JSONObject(body.readUtf8());
        assertEquals("deepseek-v4-pro", json.getString("model"));
        assertEquals("enabled", json.getJSONObject("thinking").getString("type"));
        assertEquals("high", json.getString("reasoning_effort"));
    }

    @Test public void multilineEventsHeartbeatsEmptyChoicesAndCombinedDeltaAreHandled() throws Exception {
        String stream = ": keepalive\n\ndata: {\"choices\":[]}\n\n"
                + "data:{\"choices\":[{\"delta\":\n"
                + "data: {\"reasoning_content\":\"think\",\"content\":\"hello\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\" world\",\"reasoning_content\":null}}]}\n\n"
                + "data: [DONE]\n\n";
        StringBuilder thought = new StringBuilder();
        try (Response response = response(new Request.Builder().url("https://api.deepseek.com/").build(), 200, stream)) {
            assertEquals("hello world", DeepSeekApi.readReply(response, (r, c) -> thought.append(r)));
            assertEquals("think", thought.toString());
        }
    }

    @Test public void failedAndTruncatedRequestsCannotLookSuccessful() throws Exception {
        for (String stream : new String[]{"data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n",
                "data: {\"error\":{\"message\":\"secret\"}}\n\n",
                "data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\n"}) {
            try (Response response = response(new Request.Builder().url("https://api.deepseek.com/").build(), 200, stream)) {
                try {
                    DeepSeekApi.readReply(response, (r, c) -> {});
                    fail("Must reject incomplete/error response");
                } catch (IOException expected) {
                    assertFalse(expected.getMessage().contains("secret"));
                }
            }
        }
    }

    @Test public void authenticationFailureDoesNotEchoSensitiveServerBody() throws Exception {
        try (Response response = response(new Request.Builder().url("https://api.deepseek.com/").build(),
                401, "{\"error\":\"sk-sensitive-data\"}")) {
            try {
                DeepSeekApi.readReply(response, (r, c) -> {});
                fail("Expected authentication error");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("401"));
                assertFalse(expected.getMessage().contains("sensitive"));
            }
        }
    }

    @Test public void invalidKeyIsRejectedBeforeNetwork() throws Exception {
        for (String key : new String[]{"", "sk-test\ninjected", "sk test", "密钥"}) {
            try {
                DeepSeekApi.newCall(DeepSeekApi.clientBuilder().build(), key, new JSONArray(), false, false);
                fail("Invalid key accepted");
            } catch (IllegalArgumentException expected) {}
        }
    }
}
