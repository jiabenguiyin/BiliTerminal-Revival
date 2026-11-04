package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import androidx.core.net.ConnectivityManagerCompat;

public final class NetworkPlaybackQuality {
    public static final String DEFAULT="play_qn",MOBILE="play_qn_mobile",WIFI="play_qn_wifi";
    public static final String EXTRA_KEY="quality_preference_key";
    private NetworkPlaybackQuality(){}
    public static String preferenceKey(String key){return MOBILE.equals(key)?MOBILE:WIFI.equals(key)?WIFI:DEFAULT;}
    public static int defaultQuality(Context context){return requestedQuality(context,-1);}
    public static int requestedQuality(Context context,int manual) {
        if(SharedPreferencesUtil.getSharedPreferences()==null)return PlaybackQualityPolicy.valid(manual)?manual:0;
        return PlaybackQualityPolicy.request(manual,network(context),SharedPreferencesUtil.getInt(DEFAULT,0),
                SharedPreferencesUtil.getInt(MOBILE,-1),SharedPreferencesUtil.getInt(WIFI,-1));
    }
    public static int network(Context context) {
        if(context==null)return PlaybackQualityPolicy.UNKNOWN;
        try {
            ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(manager==null)return PlaybackQualityPolicy.UNKNOWN;
            NetworkInfo active=manager.getActiveNetworkInfo();
            if(active==null||!active.isConnected())return PlaybackQualityPolicy.UNKNOWN;
            int type=active.getType();
            if(type==ConnectivityManager.TYPE_WIFI||type==ConnectivityManager.TYPE_ETHERNET)return PlaybackQualityPolicy.WIFI;
            if(type==ConnectivityManager.TYPE_MOBILE
                    ||type==ConnectivityManager.TYPE_MOBILE_DUN||type==ConnectivityManager.TYPE_MOBILE_HIPRI
                    ||type==ConnectivityManager.TYPE_MOBILE_MMS||type==ConnectivityManager.TYPE_MOBILE_SUPL
                    ||type==ConnectivityManager.TYPE_WIMAX)return PlaybackQualityPolicy.MOBILE;
            if(type==ConnectivityManager.TYPE_VPN) {
                if(Build.VERSION.SDK_INT>=23) {
                    int underlying=Api23.network(manager);if(underlying!=PlaybackQualityPolicy.UNKNOWN)return underlying;
                }
                return ConnectivityManagerCompat.isActiveNetworkMetered(manager)?PlaybackQualityPolicy.MOBILE:PlaybackQualityPolicy.WIFI;
            }
        }catch(RuntimeException ignored){}
        return PlaybackQualityPolicy.UNKNOWN;
    }
    /** Kept out of the legacy method's verifier path on Android4.x. */
    @android.annotation.TargetApi(23)
    private static final class Api23 {
        static int network(ConnectivityManager manager) {
            android.net.NetworkCapabilities caps=manager.getNetworkCapabilities(manager.getActiveNetwork());
            if(caps!=null) {
                if(caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR))return PlaybackQualityPolicy.MOBILE;
                if(caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                        ||caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))return PlaybackQualityPolicy.WIFI;
            }
            return PlaybackQualityPolicy.UNKNOWN;
        }
    }
}
