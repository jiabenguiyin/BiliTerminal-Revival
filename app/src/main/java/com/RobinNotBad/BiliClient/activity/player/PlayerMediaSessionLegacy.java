package com.RobinNotBad.BiliClient.activity.player;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;

/** Android 4.x media-button transport, kept separate from API 21 classes. */
@SuppressWarnings("deprecation")
final class PlayerMediaSessionLegacy implements PlayerMediaSessionController {
    private final AudioManager manager;
    private final ComponentName receiver;
    private final PendingIntent pending;
    private final RemoteControlClient client;
    private final Callback callback;
    private int lastFlags = -1;
    private int lastState = -1;
    PlayerMediaSessionLegacy(Context context, Callback callback) {
        this.callback=callback;
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        receiver=new ComponentName(context,PlayerMediaButtonReceiver.class);
        pending=PendingIntent.getBroadcast(context,0,new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(receiver),PendingIntent.FLAG_UPDATE_CURRENT);
        client=new RemoteControlClient(pending);
        manager.registerMediaButtonEventReceiver(receiver);
        manager.registerRemoteControlClient(client);
        PlayerMediaButtonReceiver.attach(callback);
    }
    @Override public void updateMetadata(String title,long duration) {
        client.editMetadata(true).putString(MediaMetadataRetriever.METADATA_KEY_TITLE,title==null?"":title)
                .putLong(MediaMetadataRetriever.METADATA_KEY_DURATION,Math.max(0,duration)).apply();
    }
    @Override public void updatePlaybackState(boolean playing,long position,boolean next,boolean previous) {
        int flags=RemoteControlClient.FLAG_KEY_MEDIA_PLAY|RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                |RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE;
        if(next)flags|=RemoteControlClient.FLAG_KEY_MEDIA_NEXT;
        if(previous)flags|=RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS;
        if (flags != lastFlags) { client.setTransportControlFlags(flags); lastFlags = flags; }
        int state = playing ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED;
        if (state != lastState) { client.setPlaybackState(state); lastState = state; }
    }
    @Override public void release() {
        manager.unregisterRemoteControlClient(client);
        if(PlayerMediaButtonReceiver.detach(callback)) {
            manager.unregisterMediaButtonEventReceiver(receiver);
            pending.cancel();
        }
    }
}
