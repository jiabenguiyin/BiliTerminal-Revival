package com.RobinNotBad.BiliClient.activity.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
final class PlayerMediaSessionApi21 implements PlayerMediaSessionController {
    private final MediaSession mediaSession;

    PlayerMediaSessionApi21(Context context, Callback callback) {
        mediaSession = new MediaSession(context, "BiliClientPlayer");
        mediaSession.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                callback.onPlay();
            }

            @Override
            public void onPause() {
                callback.onPause();
            }

            @Override
            public void onSkipToNext() {
                callback.onSkipToNext();
            }

            @Override
            public void onSkipToPrevious() {
                callback.onSkipToPrevious();
            }

            @Override
            public void onSeekTo(long position) {
                callback.onSeekTo(position);
            }
        });
        mediaSession.setActive(true);
    }

    @Override
    public void updateMetadata(String title, long duration) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        if (title != null) {
            builder.putString(MediaMetadata.METADATA_KEY_TITLE, title);
        }
        if (duration > 0) {
            builder.putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
        }
        mediaSession.setMetadata(builder.build());
    }

    @Override
    public void updatePlaybackState(
            boolean playing,
            long position,
            boolean hasNext,
            boolean hasPrevious
    ) {
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_SEEK_TO;
        if (hasNext) {
            actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        }
        if (hasPrevious) {
            actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        }
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        PlaybackState playbackState = new PlaybackState.Builder()
                .setState(state, position, 1.0f)
                .setActions(actions)
                .build();
        mediaSession.setPlaybackState(playbackState);
    }

    @Override
    public void release() {
        mediaSession.release();
    }
}
