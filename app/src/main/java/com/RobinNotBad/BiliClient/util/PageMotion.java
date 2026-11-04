package com.RobinNotBad.BiliClient.util;

import android.app.Activity;
import android.view.WindowManager;
import java.util.WeakHashMap;

/** Covers both BaseActivity pages and the standalone legacy player (API14+). */
public final class PageMotion {
    public static final String DISABLED="ui_disable_page_transitions";
    private static final WeakHashMap<Activity,Integer> saved=new WeakHashMap<>();
    public static void apply(Activity activity) {
        if(activity.getWindow()==null)return;
        WindowManager.LayoutParams p=activity.getWindow().getAttributes();
        if(SharedPreferencesUtil.getBoolean(DISABLED,false)) {
            if(!saved.containsKey(activity))saved.put(activity,p.windowAnimations);
            if(p.windowAnimations!=0){p.windowAnimations=0;activity.getWindow().setAttributes(p);}
            activity.overridePendingTransition(0,0);
        } else if(saved.containsKey(activity)) {
            p.windowAnimations=saved.remove(activity);activity.getWindow().setAttributes(p);
        }
    }
    public static void forget(Activity activity) { saved.remove(activity); }
}
