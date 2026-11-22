package com.RobinNotBad.BiliClient.api;

import org.json.JSONException;
import org.json.JSONObject;

public final class QrLoginResponse {
    public static final int INVALID_STATUS = Integer.MIN_VALUE;

    public final int statusCode;
    public final int responseCode;
    public final String message;
    public final JSONObject data;

    private QrLoginResponse(int statusCode, int responseCode, String message, JSONObject data) {
        this.statusCode = statusCode;
        this.responseCode = responseCode;
        this.message = message;
        this.data = data;
    }

    public static QrLoginResponse parse(String body) throws JSONException {
        JSONObject response = new JSONObject(body);
        JSONObject data = response.optJSONObject("data");
        int responseCode = response.optInt("code", INVALID_STATUS);
        String message = response.optString("message", response.optString("msg", "")).trim();
        if ("0".equals(message)) message = "";

        if (responseCode != 0 || data == null) {
            return new QrLoginResponse(INVALID_STATUS, responseCode, message, data);
        }

        int statusCode = data.optInt("code", INVALID_STATUS);
        if (statusCode == INVALID_STATUS) {
            String dataMessage = data.optString("message", data.optString("msg", "")).trim();
            if (!dataMessage.isEmpty() && !"0".equals(dataMessage)) message = dataMessage;
        }
        return new QrLoginResponse(statusCode, responseCode, message, data);
    }

    public boolean hasStatus() {
        return responseCode == 0 && statusCode != INVALID_STATUS;
    }

    public String failureSummary() {
        StringBuilder summary = new StringBuilder("扫码接口未返回有效状态");
        if (responseCode != INVALID_STATUS) summary.append("（接口码 ").append(responseCode).append('）');
        if (!message.isEmpty()) {
            String shortMessage = message.replaceAll("\\s+", " ");
            if (shortMessage.length() > 64) shortMessage = shortMessage.substring(0, 64);
            summary.append("：").append(shortMessage);
        }
        return summary.toString();
    }
}
