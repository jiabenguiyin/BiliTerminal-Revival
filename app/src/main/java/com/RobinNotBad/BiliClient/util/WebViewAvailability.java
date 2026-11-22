package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.webkit.WebView;

/** Runtime probe for ROMs that ship without a usable WebView provider. */
public final class WebViewAvailability {
    private WebViewAvailability() {
    }

    public static boolean isAvailable(Context context) {
        try {
            WebView webView = new WebView(context);
            webView.destroy();
            return true;
        } catch (Throwable error) {
            Logu.w("WebView", "provider unavailable: " + error.getClass().getSimpleName());
            return false;
        }
    }
}
