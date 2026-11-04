package com.RobinNotBad.BiliClient.api;

import org.json.JSONException;
import java.io.IOException;

/** A valid HTTP response can still require verification rather than contain search results. */
public final class SearchException extends IOException {
    public enum Reason { VERIFICATION, LOGIN, SERVICE, CONTENT }
    public final Reason reason;
    public SearchException(Reason reason, String message) { super(message); this.reason = reason; }

    public static String userMessage(Throwable error) {
        if (error instanceof SearchException) return error.getMessage();
        if (error instanceof IOException) return "网络连接失败，请检查网络后重试";
        if (error instanceof JSONException) return "搜索返回内容异常，请稍后重试";
        return "搜索失败，请稍后重试";
    }
}
