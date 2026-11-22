package com.RobinNotBad.BiliClient.util;

import java.util.regex.Pattern;

/** Strict allow-list for values inserted into the local captcha HTML. */
public final class CaptchaParamPolicy {
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{16,64}$");

    private CaptchaParamPolicy() {
    }

    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value).matches();
    }

    public static boolean areValid(String gt, String challenge) {
        return isValid(gt) && isValid(challenge);
    }
}
