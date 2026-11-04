package com.RobinNotBad.BiliClient.util;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.api.MessageApi;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Account-scoped counts; only foreground menu pages poll, never a background service. */
public final class MessageUnreadCounter {
    private static final MessageUnreadState STATE=new MessageUnreadState();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Set<Runnable> OBSERVERS=new HashSet<>();
    private static final long POLL_MS=60000L, MIN_REFRESH_MS=5000L;
    private static boolean checking, refreshAgain;
    private static long lastAttempt, lastAccount;
    private static android.content.SharedPreferences watchedPreferences;
    private static final android.content.SharedPreferences.OnSharedPreferenceChangeListener PREFERENCES=(preferences,key)->{
        if(SharedPreferencesUtil.MESSAGE_MENU_ARROW_DOT_ENABLE.equals(key)) {
            notifyObservers();
            return;
        }
        if(SharedPreferencesUtil.mid.equals(key)||SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE.equals(key)) {
            synchronized(MessageUnreadCounter.class){STATE.reset(account());lastAccount=0;}
            SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM,0);
            notifyObservers();
            synchronized(MessageUnreadCounter.class){if(!OBSERVERS.isEmpty())MAIN.post(()->refresh(true));}
        }
    };
    private static final Runnable POLL=new Runnable(){public void run(){
        synchronized(MessageUnreadCounter.class){if(OBSERVERS.isEmpty())return;}
        refresh(false);
        synchronized(MessageUnreadCounter.class){if(!OBSERVERS.isEmpty())MAIN.postDelayed(this,POLL_MS);}
    }};
    private MessageUnreadCounter() {}
    private static long account(){return SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);}
    private static boolean enabled(){return SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE,true);}
    public static synchronized void initialize() {
        if(watchedPreferences!=null)watchedPreferences.unregisterOnSharedPreferenceChangeListener(PREFERENCES);
        watchedPreferences=SharedPreferencesUtil.getSharedPreferences();
        watchedPreferences.registerOnSharedPreferenceChangeListener(PREFERENCES);
        STATE.reset(account());lastAttempt=lastAccount=0;
        SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM,0);
    }
    public static synchronized void invalidate(){STATE.reset(0);lastAccount=0;notifyObservers();}
    public static synchronized MessageUnreadState.Check beginCheck(){return enabled()?STATE.begin(account()):null;}
    public static synchronized int getCount(){return enabled()?STATE.count(account()):0;}
    static synchronized boolean isChecking(){return checking;}
    public static synchronized boolean complete(MessageUnreadState.Check check,int interactions,int privateMessages) {
        return complete(check,Integer.valueOf(interactions),Integer.valueOf(privateMessages));
    }
    public static synchronized boolean complete(MessageUnreadState.Check check,Integer interactions,Integer privateMessages) {
        boolean accepted=enabled()&&STATE.complete(check,account(),interactions,privateMessages);
        if(accepted){SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM,getCount());notifyObservers();}
        record(accepted?"applied":"discarded",interactions,privateMessages);
        return accepted;
    }
    public static synchronized void fail(MessageUnreadState.Check check) {
        boolean accepted=STATE.fail(check,account());
        // A timeout does not prove that previously confirmed messages have been read.
        if(accepted)notifyObservers();
        record(accepted?"failed_kept_last_confirmed":"discarded_failure",null,null);
    }
    public static synchronized void observe(Runnable observer) {
        OBSERVERS.add(observer);MAIN.removeCallbacks(POLL);MAIN.post(POLL);
        MAIN.post(()->{synchronized(MessageUnreadCounter.class){if(!OBSERVERS.contains(observer))return;}observer.run();});
    }
    public static synchronized void unobserve(Runnable observer) {
        OBSERVERS.remove(observer);if(OBSERVERS.isEmpty())MAIN.removeCallbacks(POLL);
    }
    private static synchronized void notifyObservers() {
        for(Runnable observer:new HashSet<>(OBSERVERS))MAIN.post(()->{
            synchronized(MessageUnreadCounter.class){if(!OBSERVERS.contains(observer))return;}
            observer.run();
        });
    }
    public static synchronized void refresh(boolean force) {
        long mid=account(),now=SystemClock.elapsedRealtime();
        if(!enabled()||mid<=0){notifyObservers();return;}
        if(checking){refreshAgain|=force;return;}
        if(!force&&mid==lastAccount&&now-lastAttempt<MIN_REFRESH_MS)return;
        final MessageUnreadState.Check check=beginCheck();
        checking=true;lastAttempt=now;lastAccount=mid;
        BiliTerminal.runBackgroundCompat(()->{
            Integer interactions=null,privateMessages=null;
            try {
                try{interactions=MessageApi.countInteractionUnread(MessageApi.getUnread());}catch(Exception ignored){}
                try{privateMessages=MessageApi.checkPrivateMsgUnread();}catch(Exception ignored){}
                if(interactions==null&&privateMessages==null)fail(check);
                else complete(check,interactions,privateMessages);
            }finally{
                boolean again;
                synchronized(MessageUnreadCounter.class){checking=false;again=refreshAgain||mid!=account();refreshAgain=false;}
                if(again)MAIN.post(()->refresh(true));
            }
        });
    }
    private static void record(String result,Integer interactions,Integer privateMessages) {
        try {
            DiagnosticLogManager.record("message_unread_result",new JSONObject().put("result",result)
                    .put("interactions",interactions==null?JSONObject.NULL:interactions)
                    .put("private_messages",privateMessages==null?JSONObject.NULL:privateMessages).put("total",getCount()));
        }catch(Exception ignored){}
    }
}
