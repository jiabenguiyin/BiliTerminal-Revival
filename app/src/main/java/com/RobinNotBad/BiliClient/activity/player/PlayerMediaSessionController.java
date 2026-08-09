package com.RobinNotBad.BiliClient.activity.player;

interface PlayerMediaSessionController {
    interface Callback {
        void onPlay();

        void onPause();

        void onSkipToNext();

        void onSkipToPrevious();

        void onSeekTo(long position);
    }

    void updateMetadata(String title, long duration);

    void updatePlaybackState(boolean playing, long position, boolean hasNext, boolean hasPrevious);

    void release();
}
