package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.model.DashData;

/** Network defaults are requests; existing availability, account and device policies resolve the stream. */
public final class PlaybackQualityPolicy {
    public static final int UNKNOWN=0,MOBILE=1,WIFI=2,INHERIT=-1;
    private PlaybackQualityPolicy(){}
    public static boolean valid(int quality){return quality==0||DashData.isSupportedQuality(quality);}
    public static int resolve(int network,int global,int mobile,int wifi) {
        int fallback=valid(global)?global:0;
        int choice=network==MOBILE?mobile:network==WIFI?wifi:INHERIT;
        return valid(choice)?choice:fallback;
    }
    public static int request(int manual,int network,int global,int mobile,int wifi) {
        return valid(manual)?manual:resolve(network,global,mobile,wifi);
    }
}
