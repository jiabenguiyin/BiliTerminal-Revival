package com.RobinNotBad.BiliClient.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.MimeTypes;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import tv.danmaku.ijk.media.player.AbstractMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.MediaInfo;
import tv.danmaku.ijk.media.player.misc.IMediaDataSource;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

/**
 * A single ExoPlayer timeline for Bilibili DASH video and audio URLs.
 *
 * The official player uses the same basic ownership model: one playback clock
 * owns both tracks. This adapter keeps the existing terminal player contract,
 * while avoiding the old two-player drift path.
 */
public final class ExoDashMediaPlayer extends AbstractMediaPlayer implements UnifiedDashPlayer {
    private final Context context;
    private final String audioUrl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile SimpleExoPlayer player;
    private volatile String videoUrl;
    private volatile Map<String, String> headers;
    private volatile String resolvedAudioUrl;
    private volatile Map<String, String> audioHeaders;
    private volatile SurfaceHolder surfaceHolder;
    private volatile Surface surface;
    private boolean prepared;
    private boolean released;
    private boolean buffering;
    private float playbackSpeed = 1.0f;
    private int videoWidth;
    private int videoHeight;
    private Format videoFormat;

    public ExoDashMediaPlayer(Context context, @Nullable String audioUrl) {
        this.context = context.getApplicationContext();
        this.audioUrl = audioUrl == null ? "" : audioUrl;
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    private <T> T callOnMain(Callable<T> action, T fallback) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return action.call();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        FutureTask<T> task = new FutureTask<>(action);
        if (!mainHandler.post(task)) return fallback;
        try {
            return task.get();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private SimpleExoPlayer ensurePlayer() {
        if (player == null) {
            player = new SimpleExoPlayer.Builder(context).build();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (released) return;
                    if (state == Player.STATE_BUFFERING && !buffering) {
                        buffering = true;
                        notifyOnInfo(IMediaPlayer.MEDIA_INFO_BUFFERING_START, 0);
                    } else if (state == Player.STATE_READY) {
                        if (buffering) {
                            buffering = false;
                            notifyOnInfo(IMediaPlayer.MEDIA_INFO_BUFFERING_END, 0);
                        }
                        if (!prepared) {
                            prepared = true;
                            notifyOnPrepared();
                        }
                    } else if (state == Player.STATE_ENDED) {
                        notifyOnCompletion();
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (released) return;
                    buffering = false;
                    int code = IMediaPlayer.MEDIA_ERROR_UNKNOWN;
                    if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                            || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                            || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                        code = IMediaPlayer.MEDIA_ERROR_IO;
                    }
                    notifyOnError(code, error.errorCode);
                }

                @Override
                public void onVideoSizeChanged(com.google.android.exoplayer2.video.VideoSize size) {
                    videoWidth = size.width;
                    videoHeight = size.height;
                    notifyOnVideoSizeChanged(videoWidth, videoHeight, 1, 1);
                }

                @Override
                public void onRenderedFirstFrame() {
                    notifyOnInfo(IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START, 0);
                }

                @Override
                public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                     Player.PositionInfo newPosition,
                                                     int reason) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        notifyOnSeekComplete();
                    }
                }
            });
            attachSurface();
        }
        return player;
    }

    private void attachSurface() {
        if (player == null) return;
        if (surfaceHolder != null) {
            player.setVideoSurfaceHolder(surfaceHolder);
        } else if (surface != null) {
            player.setVideoSurface(surface);
        }
    }

    private DataSource.Factory createDataSourceFactory(@Nullable Map<String, String> requestHeaders) {
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(NetWorkUtil.USER_AGENT_WEB)
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(30_000)
                .setAllowCrossProtocolRedirects(true);
        if (requestHeaders != null && !requestHeaders.isEmpty()) {
            httpFactory.setDefaultRequestProperties(requestHeaders);
        }
        return new DefaultDataSource.Factory(context, httpFactory);
    }

    private MediaSource createSource(String url, DataSource.Factory factory, String mimeType) {
        // Bilibili's DASH representations are standalone fragmented MP4 files and
        // their CDN responses do not always carry a useful Content-Type or file
        // extension. Explicitly identify the track so ExoPlayer does not spend the
        // whole prepare phase guessing the extractor.
        MediaItem item = new MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMimeType(mimeType)
                .build();
        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true);
        return new ProgressiveMediaSource.Factory(factory, extractors)
                .setContinueLoadingCheckIntervalBytes(256 * 1024)
                .createMediaSource(item);
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        surfaceHolder = holder;
        surface = null;
        runOnMain(() -> {
            if (player != null) player.setVideoSurfaceHolder(holder);
        });
    }

    @Override
    public void setSurface(Surface surface) {
        surfaceHolder = null;
        this.surface = surface;
        runOnMain(() -> {
            if (player != null) player.setVideoSurface(surface);
        });
    }

    @Override
    public void setDataSource(Context context, Uri uri) throws IOException {
        setDataSource(context, uri, null);
    }

    @Override
    public void setDataSource(Context context, Uri uri, Map<String, String> headers)
            throws IOException {
        videoUrl = uri.toString();
        this.headers = headers == null ? null : new HashMap<>(headers);
    }

    @Override
    public void setDataSource(FileDescriptor fd) {
        throw new UnsupportedOperationException("file descriptor is not supported");
    }

    @Override
    public void setDataSource(String path) {
        videoUrl = path;
        headers = null;
    }

    @Override
    public String getDataSource() {
        return videoUrl;
    }

    @Override
    public void prepareAsync() {
        runOnMain(this::prepareInternal);
    }

    private void prepareInternal() {
        if (released || videoUrl == null || videoUrl.isEmpty()) {
            notifyOnError(IMediaPlayer.MEDIA_ERROR_MALFORMED, 0);
            return;
        }
        SimpleExoPlayer exo = ensurePlayer();
        DataSource.Factory videoFactory = createDataSourceFactory(headers);
        MediaSource videoSource = createSource(videoUrl, videoFactory, MimeTypes.VIDEO_MP4);
        MediaSource source = videoSource;
        String audio = resolvedAudioUrl == null || resolvedAudioUrl.isEmpty()
                ? audioUrl : resolvedAudioUrl;
        if (!audio.isEmpty()) {
            DataSource.Factory audioFactory = createDataSourceFactory(
                    audioHeaders == null || audioHeaders.isEmpty() ? headers : audioHeaders);
            MediaSource audioSource = createSource(audio, audioFactory, MimeTypes.AUDIO_MP4);
            // The two Bilibili representations can have slightly different edit
            // lists/start timestamps. Adjust offsets and clip the merged timeline
            // to the shorter track so preparation cannot stall on a duration
            // mismatch and playback remains on one ExoPlayer clock.
            source = new MergingMediaSource(true, true, videoSource, audioSource);
        }
        prepared = false;
        buffering = false;
        exo.setPlaybackSpeed(playbackSpeed);
        exo.setMediaSource(source, false);
        exo.prepare();
    }

    @Override
    public void start() {
        runOnMain(() -> {
            if (player != null) player.play();
        });
    }

    @Override
    public void stop() {
        runOnMain(() -> {
            if (player != null) player.stop();
        });
    }

    @Override
    public void pause() {
        runOnMain(() -> {
            if (player != null) player.pause();
        });
    }

    @Override
    public void setScreenOnWhilePlaying(boolean screenOn) {
        if (surfaceHolder != null) surfaceHolder.setKeepScreenOn(screenOn);
    }

    @Override
    public int getVideoWidth() {
        return videoWidth;
    }

    @Override
    public int getVideoHeight() {
        return videoHeight;
    }

    @Override
    public boolean isPlaying() {
        return callOnMain(() -> player != null && player.isPlaying(), false);
    }

    @Override
    public void seekTo(long msec) {
        runOnMain(() -> {
            if (player != null) player.seekTo(Math.max(0L, msec));
        });
    }

    @Override
    public long getCurrentPosition() {
        return callOnMain(() -> player == null ? 0L : Math.max(0L, player.getCurrentPosition()), 0L);
    }

    @Override
    public long getDuration() {
        return callOnMain(() -> player == null ? 0L : Math.max(0L, player.getDuration()), 0L);
    }

    @Override
    public void release() {
        released = true;
        runOnMain(() -> {
            if (player != null) {
                player.release();
                player = null;
            }
            prepared = false;
        });
    }

    @Override
    public void reset() {
        runOnMain(() -> {
            if (player != null) player.stop();
            prepared = false;
            buffering = false;
        });
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        runOnMain(() -> {
            if (player != null) player.setVolume((leftVolume + rightVolume) / 2.0f);
        });
    }

    @Override
    public int getAudioSessionId() {
        return callOnMain(() -> player == null ? C.AUDIO_SESSION_ID_UNSET : player.getAudioSessionId(),
                C.AUDIO_SESSION_ID_UNSET);
    }

    @Override
    public MediaInfo getMediaInfo() {
        MediaInfo info = new MediaInfo();
        info.mMediaPlayerName = "ExoPlayer unified DASH";
        info.mVideoDecoder = videoFormat == null ? "exo" : String.valueOf(videoFormat.sampleMimeType);
        info.mVideoDecoderImpl = "ExoPlayer";
        info.mAudioDecoder = "exo";
        info.mAudioDecoderImpl = "ExoPlayer";
        return info;
    }

    @Override
    public void setLogEnabled(boolean enable) {
    }

    @Override
    public boolean isPlayable() {
        return player != null;
    }

    @Override
    public void setAudioStreamType(int streamtype) {
    }

    @Override
    public void setKeepInBackground(boolean keepInBackground) {
    }

    @Override
    public int getVideoSarNum() {
        return 1;
    }

    @Override
    public int getVideoSarDen() {
        return 1;
    }

    @Override
    public void setWakeMode(Context context, int mode) {
    }

    @Override
    public void setLooping(boolean looping) {
        runOnMain(() -> {
            if (player != null) player.setRepeatMode(looping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        });
    }

    @Override
    public boolean isLooping() {
        return callOnMain(() -> player != null && player.getRepeatMode() == Player.REPEAT_MODE_ALL,
                false);
    }

    @Override
    public ITrackInfo[] getTrackInfo() {
        return null;
    }

    @Override
    public void setDataSource(IMediaDataSource mediaDataSource) {
        throw new UnsupportedOperationException("custom data source is not supported");
    }

    public void setPlaybackSpeed(float speed) {
        playbackSpeed = speed > 0.0f ? speed : 1.0f;
        runOnMain(() -> {
            if (player != null) player.setPlaybackSpeed(playbackSpeed);
        });
    }

    @Override
    public void setAudioSource(String url, Map<String, String> headers) {
        resolvedAudioUrl = url;
        audioHeaders = headers == null ? null : new HashMap<>(headers);
        if ((this.headers == null || this.headers.isEmpty()) && headers != null && !headers.isEmpty()) {
            this.headers = new HashMap<>(headers);
        }
    }
}
