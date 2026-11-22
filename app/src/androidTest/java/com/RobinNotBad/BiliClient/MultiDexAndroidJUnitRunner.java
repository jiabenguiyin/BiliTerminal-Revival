package com.RobinNotBad.BiliClient;

import android.os.Bundle;
import android.os.Build;

import androidx.multidex.MultiDex;
import androidx.test.runner.AndroidJUnitRunner;

public final class MultiDexAndroidJUnitRunner extends AndroidJUnitRunner {
    @Override
    public void onCreate(Bundle arguments) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            MultiDex.installInstrumentation(getContext(), getTargetContext());
        }
        super.onCreate(arguments);
    }
}
