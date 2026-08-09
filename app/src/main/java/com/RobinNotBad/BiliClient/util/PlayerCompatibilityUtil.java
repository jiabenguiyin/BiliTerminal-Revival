package com.RobinNotBad.BiliClient.util;

import android.os.Build;

import java.util.Locale;

public final class PlayerCompatibilityUtil {
    private PlayerCompatibilityUtil() {
    }

    public static boolean prefersSoftwareCodec() {
        return prefersSoftwareCodec(Build.MANUFACTURER, Build.BRAND, Build.DISPLAY);
    }

    static boolean prefersSoftwareCodec(String manufacturer, String brand, String display) {
        String deviceIdentity = ((manufacturer == null ? "" : manufacturer) + " "
                + (brand == null ? "" : brand) + " "
                + (display == null ? "" : display)).toLowerCase(Locale.US);
        return deviceIdentity.contains("huawei") || deviceIdentity.contains("harmony");
    }
}
