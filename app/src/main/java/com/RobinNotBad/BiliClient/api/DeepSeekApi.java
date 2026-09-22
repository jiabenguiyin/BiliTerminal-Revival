package com.RobinNotBad.BiliClient.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public final class DeepSeekApi {
    private DeepSeekApi() {}

    public interface DeltaListener {
        void onDelta(String reasoning, String content);
    }

    public static OkHttpClient.Builder clientBuilder() {
        // Separate from Bilibili credential filtering and cookie interceptors.
        return new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS);
    }

    public static Call newCall(OkHttpClient client, String key, JSONArray messages,
                               boolean pro, boolean thinking) throws JSONException {
        String cleanKey = key == null ? "" : key.trim();
        if (cleanKey.isEmpty() || !cleanKey.matches("[\\x21-\\x7e]+")) {
            throw new IllegalArgumentException("API Key 为空或含有空白、换行等无效字符");
        }
        JSONObject json = new JSONObject().put("model", pro ? "deepseek-v4-pro" : "deepseek-flash")
                .put("messages", messages).put("stream", true).put("max_tokens", 8192)
                .put("thinking", new JSONObject().put("type", thinking ? "enabled" : "disabled"));
        if (thinking) json.put("reasoning_effort", "high");
        Request request = new Request.Builder().url("https://api.deepseek.com/chat/completions")
                .header("Authorization", "Bearer " + cleanKey)
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString()))
                .build();
        return client.newCall(request);
    }

    public static String readReply(Response response, DeltaListener listener) throws IOException {
        if (!response.isSuccessful()) throw new IOException(httpError(response.code()));
        ResponseBody body = response.body();
        if (body == null) throw new IOException("DeepSeek 返回了空响应，请重试");
        BufferedSource source = body.source();
        StringBuilder event = new StringBuilder();
        StringBuilder answer = new StringBuilder();
        int total = 0;
        while (true) {
            String line = source.readUtf8Line();
            if (line == null) throw new IOException("连接提前中断，本次回答未加入上下文，可重试");
            if (line.startsWith("data:")) {
                String value = line.substring(5).trim();
                if (event.length() > 0) event.append('\n');
                event.append(value);
                if (event.length() > 65536) throw new IOException("响应数据过大，已停止接收");
            } else if (line.isEmpty() && event.length() > 0) {
                String data = event.toString();
                event.setLength(0);
                if ("[DONE]".equals(data)) {
                    if (answer.length() == 0) throw new IOException("未收到回答正文，请关闭深度思考后重试");
                    return answer.toString();
                }
                try {
                    JSONObject frame = new JSONObject(data);
                    if (frame.has("error")) throw new IOException("DeepSeek 返回了接口错误，请稍后重试");
                    JSONArray choices = frame.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject choice = choices.getJSONObject(0);
                    if ("length".equals(choice.optString("finish_reason"))) {
                        throw new IOException("回答达到长度上限，本次未加入上下文，请缩短问题或关闭深度思考");
                    }
                    JSONObject delta = choice.optJSONObject("delta");
                    if (delta == null) continue;
                    String reasoning = delta.isNull("reasoning_content") ? "" : delta.optString("reasoning_content");
                    String content = delta.isNull("content") ? "" : delta.optString("content");
                    total += reasoning.length() + content.length();
                    if (total > 65536) throw new IOException("回答过长，已停止接收，本次未加入上下文");
                    answer.append(content);
                    listener.onDelta(reasoning, content);
                } catch (JSONException error) {
                    throw new IOException("DeepSeek 响应格式异常，请重试");
                }
            }
        }
    }

    public static String httpError(int code) {
        switch (code) {
            case 401: return "认证失败（401）：API Key 无效或已被撤销，请更换有效密钥后重试";
            case 402: return "账户余额不足（402），请检查 DeepSeek API 账户余额";
            case 403: return "请求被拒绝（403），请检查账户权限或网络访问限制";
            case 429: return "请求过于频繁（429），请稍后重试";
            case 400:
            case 422: return "请求参数被拒绝（" + code + "），请更换模型或关闭深度思考后重试";
            case 500:
            case 502:
            case 503: return "DeepSeek 服务暂时不可用（" + code + "），请稍后重试";
            default: return "DeepSeek 请求失败（HTTP " + code + "），请稍后重试";
        }
    }

    public static String failureMessage(Exception error, boolean cancelled) {
        if (cancelled) return "已停止，本次回答未加入上下文";
        if (error instanceof SocketTimeoutException) return "请求超时，请检查网络后重试";
        if (error instanceof IOException && error.getMessage() != null
                && error.getMessage().matches(".*[\\u4e00-\\u9fff].*")) return error.getMessage();
        if (error instanceof IllegalArgumentException) return "请检查 API Key，不能包含空白或换行";
        return "连接失败，请检查网络后重试";
    }
}
