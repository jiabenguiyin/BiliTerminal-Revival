package com.RobinNotBad.BiliClient.api;

import org.json.JSONException;
import org.json.JSONObject;

public final class QrLoginCode {
    public final String url;
    public final String key;

    private QrLoginCode(String url, String key) {
        this.url = url;
        this.key = key;
    }

    public static QrLoginCode parse(String body) throws JSONException {
        JSONObject response = new JSONObject(body);
        int code = response.optInt("code", Integer.MIN_VALUE);
        JSONObject data = response.optJSONObject("data");
        String message = response.optString("message", response.optString("msg", "")).trim();
        if ("0".equals(message)) message = "";

        if (code != 0) throw new JSONException(errorMessage(code, message));
        if (data == null) throw new JSONException(errorMessage(code, message));

        String url = data.optString("url", "").trim();
        String key = data.optString("qrcode_key", "").trim();
        if (url.isEmpty() || key.isEmpty()) {
            throw new JSONException("二维码接口返回的数据不完整");
        }
        return new QrLoginCode(url, key);
    }

    private static String errorMessage(int code, String message) {
        String summary = code == 0 ? "二维码接口未返回有效数据" : "二维码接口返回异常";
        if (code != Integer.MIN_VALUE && code != 0) summary += "（接口码 " + code + "）";
        if (!message.isEmpty()) {
            String safeMessage = message.replaceAll("\\s+", " ");
            if (safeMessage.length() > 64) safeMessage = safeMessage.substring(0, 64);
            summary += "：" + safeMessage;
        }
        return summary;
    }
}
