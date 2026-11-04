package com.RobinNotBad.BiliClient.activity.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import java.lang.ref.WeakReference;

/** Receives legacy system media keys only while an existing player owns them. */
public final class PlayerMediaButtonReceiver extends BroadcastReceiver {
    private static WeakReference<PlayerMediaSessionController.Callback> owner = new WeakReference<>(null);
    static synchronized void attach(PlayerMediaSessionController.Callback callback) { owner = new WeakReference<>(callback); }
    static synchronized boolean detach(PlayerMediaSessionController.Callback callback) {
        if (owner.get() != callback) return false;
        owner.clear();
        return true;
    }
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
        KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        PlayerMediaSessionController.Callback callback = owner.get();
        if (callback == null || event == null || event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_NEXT: callback.onSkipToNext(); break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: callback.onSkipToPrevious(); break;
            case KeyEvent.KEYCODE_MEDIA_PLAY: callback.onPlay(); break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP: callback.onPause(); break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                if (callback instanceof ToggleCallback) ((ToggleCallback) callback).onToggle();
                break;
            default: return;
        }
        if (isOrderedBroadcast()) abortBroadcast();
    }
    interface ToggleCallback extends PlayerMediaSessionController.Callback { void onToggle(); }
}
