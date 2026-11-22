package com.RobinNotBad.BiliClient.activity.player;

import static android.media.AudioManager.STREAM_MUSIC;
import static com.RobinNotBad.BiliClient.util.NetWorkUtil.USER_AGENT_WEB;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.InteractionDebugActivity;
import com.RobinNotBad.BiliClient.activity.browser.BrowserRelayProxy;
import com.RobinNotBad.BiliClient.adapter.QualitySelectorAdapter;
import com.RobinNotBad.BiliClient.adapter.ViewPointAdapter;
import com.RobinNotBad.BiliClient.api.ConfInfoApi;
import com.RobinNotBad.BiliClient.api.DanmakuApi;
import com.RobinNotBad.BiliClient.api.InteractionVideoApi;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.api.VideoInfoApi;
import com.RobinNotBad.BiliClient.event.SnackEvent;
import com.RobinNotBad.BiliClient.model.DanmakuElem;
import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.model.DashData;
import com.RobinNotBad.BiliClient.model.HighEnergyData;
import com.RobinNotBad.BiliClient.model.InteractionVideoData;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.Subtitle;
import com.RobinNotBad.BiliClient.model.SubtitleLink;
import com.RobinNotBad.BiliClient.model.ViewPoint;
import com.RobinNotBad.BiliClient.player.DanmakuWindowLoader;
import com.RobinNotBad.BiliClient.player.PlayerInfoOverlay;
import com.RobinNotBad.BiliClient.player.DanmakuLoadPolicy;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackQueue;
import com.RobinNotBad.BiliClient.player.CollectionPlaybackSource;
import com.RobinNotBad.BiliClient.player.CachedPlaybackSnapshot;
import com.RobinNotBad.BiliClient.player.CachedPlaybackItem;
import com.RobinNotBad.BiliClient.util.DanmakuXmlCounter;
import com.RobinNotBad.BiliClient.util.RequestCancellation;
import com.RobinNotBad.BiliClient.service.PlaybackKeepAliveService;
import com.RobinNotBad.BiliClient.ui.widget.BatteryView;
import com.RobinNotBad.BiliClient.ui.widget.HighEnergyProgressBar;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.BackgroundPlaybackPolicy;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.DeviceProfile;
import com.RobinNotBad.BiliClient.util.DanmakuClock;
import com.RobinNotBad.BiliClient.util.ForegroundRestorePolicy;
import com.RobinNotBad.BiliClient.util.HighEnergyDataBuilder;
import com.RobinNotBad.BiliClient.util.InteractionProgressStore;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.PlaybackProgressGuard;
import com.RobinNotBad.BiliClient.util.PlayerCompatibilityUtil;
import com.RobinNotBad.BiliClient.util.PlayerSettingsUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.SwipeSeekUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.Inflater;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.IDanmakuIterator;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.IDataSource;
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser;
import master.flame.danmaku.danmaku.parser.android.BiliProtobufDanmakuParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.BufferedSink;
import okio.Okio;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import com.RobinNotBad.BiliClient.player.UnifiedDashPlayer;
import com.RobinNotBad.BiliClient.player.UnifiedDashPlayerFactory;

public class PlayerActivity extends Activity implements IMediaPlayer.OnPreparedListener {
    private static final long PLAYBACK_STALL_TIMEOUT_MS = 6000L;
    private static final long PLAYBACK_STALL_CHECK_INTERVAL_MS = 1000L;
    private static final long PLAYBACK_PRIMARY_TIMEOUT_MS = 20_000L;
    private static final long PLAYBACK_FINAL_TIMEOUT_MS = 45_000L;
    private static final long DASH_START_HEADSTART_MS = 180L;
    private static final long DASH_DRIFT_CORRECTION_THRESHOLD_MS = 350L;
    private static final long DASH_DRIFT_CHECK_INTERVAL_MS = 2500L;
    /** DASH can remain in BUFFERING without reporting an error when a CDN/relay stalls. */
    private static final long UNIFIED_BUFFERING_FALLBACK_DELAY_MS = 8000L;
    /** 1.1.6: 音频轨运行时网络恢复窗口（后台播放/听视频载入失败修复）。 */
    private static final long AUDIO_RUNTIME_RETRY_DELAY_MS = 2500L;
    private static final int AUDIO_RUNTIME_MAX_RETRIES = 2;
    private volatile boolean destroyed = false;

    private IMediaPlayer ijkPlayer;
    private IMediaPlayer dashAudioPlayer;
    private IDanmakuView mDanmakuView;
    private DanmakuContext mContext;

    private SurfaceView surfaceView;
    private TextureView textureView;
    private SurfaceTexture mSurfaceTexture;
    private Surface textureSurface;
    private boolean videoOutputDetached = false;
    private boolean restoreFrameOnNextSurface = false;

    private SubtitleLink[] subtitleLinks = null;
    private Subtitle[] subtitles = null;
    private int subtitle_curr_index, subtitle_count;
    private float subtitle_delta;

    private RelativeLayout layout_control, layout_top, layout_video, layout_card_bg, layout_audio_only;
    private LinearLayout layout_speed, right_control, loading_info;
    private RelativeLayout bottom_buttons;
    private HorizontalScrollView right_second;
    private LinearLayout card_subtitle, card_danmaku_send, card_page_selector, card_quality_selector, card_viewpoint_selector;

    private ImageView img_loading;
    private AnimationDrawable anim_loading;
    private ImageButton btn_control, btn_danmaku, btn_loop, btn_rotate, btn_menu, btn_subtitle, btn_danmaku_send,
            btn_audio_only, btn_page_selector, btn_auto_next, btn_quality, btn_viewpoint, btn_debug, btn_lock;
    private HighEnergyProgressBar seekbar_progress;
    private SeekBar seekbar_speed;
    private TextView text_progress, text_online, text_volume, loading_text0, loading_text1, text_speed, text_newspeed, swipe_seek_hint;
    public TextView text_title, text_subtitle, text_audio_title, text_audio_subtitle;

    private Timer progressTimer, speedTimer, loadingTimer, onlineTimer, surfaceTimer;
    private Handler mainHandler;
    private Runnable danmakuSyncRunnable;
    private Runnable bufferingUiRunnable;
    private Runnable unifiedBufferingFallbackRunnable;
    private boolean bufferingUiShown = false;
    /** Keeps danmaku time moving without querying the media layer on every draw frame. */
    private final DanmakuClock danmakuClock = new DanmakuClock();
    private long lastDanmakuClockSyncAt = -1L;
    /** 1.1.6: 音频轨运行时恢复重试计数（新视频加载时清零）。 */
    private int audioRuntimeRetryCount = 0;
    /** 1.1.6: 本地缓存视频同源重试标记（每个视频一次）。 */
    private boolean localSourceRetried = false;
    private String video_url, audio_url, danmaku_url;
    private final ArrayList<String> playbackUrls = new ArrayList<>();
    private final ArrayList<String> audioPlaybackUrls = new ArrayList<>();
    private int playbackUrlIndex = 0;
    private int audioPlaybackUrlIndex = 0;
    private int audioPrepareAttempt = 0;
    private boolean videoTrackPrepared = false;
    private boolean audioTrackPrepared = false;
    private boolean preparationFinalized = false;
    private boolean resumeAfterPrepare = true;
    private long lastDashAudioSyncAt = 0L;
    private long suppressDashDriftUntil = 0L;
    private boolean audioRenderingStarted = false;
    private boolean videoRenderingStarted = false;
    private boolean dashVideoStarted = false;
    private boolean dashPlaybackStartedOnce = false;
    private boolean videoTrackBuffering = false;
    private boolean audioTrackBuffering = false;
    private long videoBufferingStartedAt = -1L;
    private boolean videoSeekPending = false;
    private boolean audioSeekPending = false;
    private long pendingVideoSeekPosition = -1L;
    private long pendingAudioSeekPosition = -1L;
    private long queuedSynchronizedSeekPosition = -1L;
    private boolean audioOnlySynchronizedSeek = false;
    private boolean resumeAfterSynchronizedSeek = false;
    private int synchronizedSeekGeneration = 0;
    private int dashStartGeneration = 0;
    private float currentPlaybackSpeed = 1.0F;
    private Runnable dashBufferingPauseRunnable;
    private boolean dashBufferingPauseApplied = false;
    private boolean dashAudioRecoveryPending = false;
    private String localSubtitleRoot = "";
    private PlayerMediaSessionController mediaSession;

    private boolean isPlaying, isPrepared, hasDanmaku,
            isOnlineVideo, isLiveMode, isSeeking;
    private volatile boolean isDanmakuVisible;
    private boolean playbackCompleted;
    private boolean danmakuLoadStarted;
    private DanmakuLoadPolicy danmakuLoadPolicy;
    private long knownDanmakuCount = -1;
    private RequestCancellation danmakuCountRequest, danmakuXmlRequest;
    private boolean danmakuProtectionHintShown;
    private volatile int danmakuLoadGeneration;
    private volatile DanmakuWindowLoader danmakuWindowLoader;
    private Map<Integer, DmSegMobileReply> pendingDanmakuWindow;
    private final HashSet<Integer> displayedDanmakuSegments = new HashSet<>();
    private BiliProtobufDanmakuParser windowDanmakuParser;
    private boolean unifiedPlayerFallbackAttempted = false;
    private boolean forceCompatibilityPlayer = false;
    private boolean menu_opened = false;
    private boolean screenLocked = false;
    private boolean lockedTapCandidate;
    private float lockedTouchDownX, lockedTouchDownY;
    private boolean lockUiStateSaved = false;
    private int lockedDanmakuVisibility = View.VISIBLE;
    private int lockedRotateVisibility = View.VISIBLE;
    private int lockedMenuVisibility = View.VISIBLE;
    private int lockedRightSecondVisibility = View.GONE;
    private int lockedTopVisibility = View.VISIBLE;
    private int lockedBottomVisibility = View.VISIBLE;
    private int lockedProgressVisibility = View.VISIBLE;
    private int lockedSpeedVisibility = View.GONE;
    private int lockedDebugVisibility = View.GONE;
    private int playbackPrepareAttempt = 0;
    private boolean isAudioOnlyMode = false;
    private boolean isLocalAudioFile = false; // 标记是否为本地音频文件
    private boolean isActivityVisible = false;
    private boolean backgroundLeaveRequested = false;
    private boolean backgroundPlaybackActive = false;
    private boolean backgroundTransitionSuppressed = false;
    private boolean wasPlayingBeforePause = false;
    private long foregroundRestorePosition = -1L;
    private int foregroundRestoreGeneration = 0;
    private boolean foregroundRestoreScheduled = false;
    private boolean hardwareCodecInUse = false;
    /** 1.1.6: 硬解失败后置位，强制同 URL 软解重试（避免 -10000 后只能换源碰运气）。 */
    private boolean forceSoftwareDecode = false;
    private boolean progressAnomalyReported = false;

    private int video_all, video_now, video_now_last;
    private long progress_history;
    private final com.RobinNotBad.BiliClient.player.PlaybackResumePoints playlistResumePoints =
            new com.RobinNotBad.BiliClient.player.PlaybackResumePoints();
    private String localProgressFolderLocator = "";
    private VideoStorageUtil.Node localProgressFile;
    private float swipeDownX, swipeDownY;
    private boolean swipeSeeking;
    private float verticalGestureStartX, verticalGestureStartY, verticalGestureLastY;
    private float verticalGestureStartBrightness;
    private boolean verticalAdjusting;
    private boolean brightnessAdjusting;
    private long swipeSeekStartPosition;
    private final PlaybackProgressGuard progressGuard = new PlaybackProgressGuard();
    private final HighEnergyDataBuilder highEnergyDataBuilder = new HighEnergyDataBuilder();
    private final HashSet<Integer> highEnergySegments = new HashSet<>();
    private boolean highEnergyEnabled;
    private String progress_str;

    private int screen_width, screen_height;
    private int playerSafeLeft, playerSafeTop, playerSafeRight, playerSafeBottom;
    private WindowInsetsCompat playerWindowInsets;
    private final Map<View, Rect> playerBaseMargins = new HashMap<>();
    private final Map<View, Rect> playerBasePaddings = new HashMap<>();
    private final PlayerControlTouchDiagnostics controlTouchDiagnostics = new PlayerControlTouchDiagnostics();
    private int video_width, video_height;

    private AudioManager audioManager;
    private int heldVolumeKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private final Runnable hardwareVolumeRepeat = new Runnable() {
        @Override
        public void run() {
            if (destroyed || mainHandler == null || !isPrepared
                    || (heldVolumeKeyCode != KeyEvent.KEYCODE_VOLUME_UP
                    && heldVolumeKeyCode != KeyEvent.KEYCODE_VOLUME_DOWN)) {
                return;
            }
            changeVolume(heldVolumeKeyCode == KeyEvent.KEYCODE_VOLUME_UP);
            mainHandler.postDelayed(this, 120L);
        }
    };

    private ScaleGestureDetector scaleGestureDetector;
    private ViewScaleGestureListener scaleGestureListener;
    private float previousX, previousY;
    private boolean gesture_moved, gesture_scaled, gesture_click_disabled;
    private float surfaceDownX, surfaceDownY, surfaceTapSlopSquared;
    private int surfacePointerId;
    private boolean surfaceTapCandidate, surfaceClickCancelled;
    /** Optional single-finger seek mode while the zoomed video's controls are shown. */
    private boolean zoomedSingleFingerSeekMode;
    /** Once a second pointer appears, this gesture stays in two-finger mode until it ends. */
    private boolean multiTouchGesture;
    private float video_origX, video_origY;
    private int videoLayoutGeneration,videoPanGeneration;
    private long timestamp_click;
    private boolean controlsVisibleBeforeTap;
    private boolean singleTapChangedControls;
    private volatile boolean onLongClick = false;
    private long longPressStartedAt, lastLongPressSampleAt, lastLongPressSamplePosition;
    private int longPressMemoryClass;
    private final com.RobinNotBad.BiliClient.player.LongPressSwitchGesture longPressSwitch = new com.RobinNotBad.BiliClient.player.LongPressSwitchGesture();
    private boolean longPressSwitchSequence;
    private boolean longPressPanSequence;
    private float lastSurfaceX, lastSurfaceY;
    private boolean danmakuPausedByBuffering = false;
    private int localRuntimeRetryCount = 0;
    private Runnable playbackStallWatchdog;
    private long playbackStallLastPosition = -1L;
    private long playbackStallLastSampleAt = -1L;
    private int playbackStallRecoveryCount = 0;
    private boolean playbackStallRecoveryInFlight = false;

    private final float[] speed_values = {0.5F, 0.75F, 1.0F, 1.25F, 1.5F, 1.75F, 2.0F, 3.0F};
    private final String[] speed_strs = {"x 0.5", "x 0.75", "x 1.0", "x 1.25", "x 1.5", "x 1.75", "x 2.0", "x 3.0"};

    private boolean finishWatching = false;
    private boolean loop_enabled;
    private boolean auto_next_enabled = false;
    private ImageButton btn_collection_order;
    private CollectionPlaybackQueue collectionQueue;
    private String favoritePlaybackToken;
    private boolean explicitOnlineQueue, explicitOnlineEnabled = true;
    private com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.Session favoriteSession;
    private com.RobinNotBad.BiliClient.api.FavoritePlaybackSource favoritePlaybackSource =
            new com.RobinNotBad.BiliClient.api.FavoritePlaybackSource();
    private volatile int favoriteGeneration;
    private boolean favoritePaging, favoritePageFailed, favoritePendingCompletion;
    private int favoriteRetryAction;
    private CachedPlaybackSnapshot cachedPlaybackSnapshot;
    private String cachedPlaybackToken;
    private int currentCachedIndex = -1;
    private boolean cachedUserAudioOnlyMode;
    private Boolean pendingMediaPlaybackRequest;
    private CollectionPlaybackSource collectionPlaybackSource = new CollectionPlaybackSource();
    private boolean showCollectionPlayback, collectionPlaybackEnabled = true;
    private boolean collectionQueueLoading, collectionQueueFailed;
    private long collectionQueueLoadedForAid = -1;
    private int collectionPlaybackOrder = CollectionPlaybackQueue.SEQUENTIAL;
    private int playbackSwitchGeneration;
    private boolean playbackSwitchInFlight, collectionSwitchInFlight;
    private com.RobinNotBad.BiliClient.player.PlayerOptionsMenu playbackOptionsMenu;
    private TextView longPressSpeedHint;
    private int manualPlaybackQuality = -1;
    private boolean bangumiPlayback;
    private long pgcSeason, pgcEpisode, pgcAccount, lastPgcSave = -1;
    private int pgcType = 1;
    private com.RobinNotBad.BiliClient.player.PlaybackSegments segmentPolicy;
    private TextView segmentSkipButton;
    private String lastPlayerGeometry = "";
    private int playerCornerLeft, playerCornerRight;

    private BatteryView batteryView;
    private BatteryManager batteryManager;

    private File danmakuFile;

    private boolean screen_landscape, screen_round;

    public String online_number = "0";

    private long aid, cid, mid;

    private ArrayList<String> pagenames;
    private ArrayList<Long> cids;
    private int currentPageIndex = 0;
    private String videoTitle;

    private String[] qnStrList;
    private int[] qnValueList;
    private int currentQuality = 0;

    private InteractionVideoData interactionData;
    private long interactionGraphVersion = 0;
    private long currentEdgeId = 0;
    private long initialEdgeId = 0;
    private InteractionVideoData.InteractionQuestion currentQuestion = null;
    private boolean questionShown = false;
    private LinearLayout interactionChoiceLayout;
    private InteractionProgressStore.Snapshot interactionRestoreSnapshot;
    private long lastInteractionSaveAt = 0L;
    private PlayerInfoOverlay infoOverlay;
    private com.RobinNotBad.BiliClient.player.AudioPlaybackPanel audioPanel;
    private final Runnable toolbarBoundsUpdate=this::applyRightToolbarBounds;
    private boolean roundSideControlsPositioned;
    private final Runnable expandedMenuBoundsUpdate=this::applyExpandedMenuBounds;
    private long audioSleepDeadline;
    private final Runnable audioSleepCheck=()->{
        if(destroyed||audioSleepDeadline<=0)return;
        long remaining=audioSleepDeadline-SystemClock.elapsedRealtime();
        if(remaining>0){mainHandler.postDelayed(this.audioSleepCheck,remaining);return;}
        audioSleepDeadline=0;setMediaPlaying(false);if(isPlaying)playerPause();updateAudioDashboard();
        MsgUtil.showMsg("已到定时停止时间");
    };

    @Override
    public void onBackPressed() {
        if (!SharedPreferencesUtil.getBoolean("back_disable", false))
            super.onBackPressed();
    }

    @Override public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !SharedPreferencesUtil.getBoolean("back_disable", false)) {
            BiliTerminal.returnHome(this);
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(BiliTerminal.getFitDisplayContext(newBase));
    }

    private IMediaPlayer createMediaPlayer() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            return new AndroidMediaPlayer();
        }
        if (PlayerCompatibilityUtil.shouldUsePlatformMediaPlayer()) {
            Logu.w("player", "native decoder ABI unavailable, using platform player");
            return new AndroidMediaPlayer();
        }
        boolean useUnified = DeviceProfile.useUnifiedDashPlayer()
                && !forceCompatibilityPlayer
                && isOnlineVideo
                && !isAudioOnlyMode
                && audio_url != null && !audio_url.isEmpty();
        Logu.v("player", "create=" + (useUnified ? "unified" : "compatibility")
                + ", profile=" + DeviceProfile.get().name()
                + ", audio=" + (audio_url == null ? 0 : audio_url.length())
                + ", forcedCompat=" + forceCompatibilityPlayer);
        if (useUnified) {
            IMediaPlayer unifiedPlayer = UnifiedDashPlayerFactory.create(this, audio_url);
            if (unifiedPlayer != null) return unifiedPlayer;
            Logu.e("player", "unified player creation failed, using compatibility player");
        }
        try {
            IjkMediaPlayer.loadLibrariesOnce(null);
            // Native IJK info logs print request headers, including cookies and relay tokens.
            IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_WARN);
            return new IjkMediaPlayer();
        } catch (UnsatisfiedLinkError | SecurityException error) {
            Logu.e("player", "native decoder unavailable, using platform player: "
                    + error.getClass().getSimpleName());
            return new AndroidMediaPlayer();
        }
    }

    private void setPlaybackSpeed(float speed) {
        if (destroyed) return;
        boolean changed = Math.abs(currentPlaybackSpeed - speed) > 0.01F;
        currentPlaybackSpeed = speed;
        danmakuClock.setSpeed(speed, SystemClock.elapsedRealtime());
        setPlayerSpeed(ijkPlayer, speed);
        setPlayerSpeed(dashAudioPlayer, speed);
        setDanmakuSpeed(speed);
        if (changed && hasDashAudio() && isPrepared) {
            suppressDashDriftUntil = SystemClock.elapsedRealtime() + 2500L;
            final int generation = synchronizedSeekGeneration;
            mainHandler.postDelayed(() -> {
                if (destroyed || generation != synchronizedSeekGeneration || isSeeking
                        || videoTrackBuffering || audioTrackBuffering) return;
                alignDashAudioToVideo("speed_change", 900L);
            }, 600L);
        }
    }

    private void setDanmakuSpeed(float speed) {
        if (!hasDanmaku || mDanmakuView == null) return;
        try {
            // PlayerActivity's callback supplies the real video clock. Applying
            // another speed factor inside DanmakuFlameMaster made speed changes
            // compound and caused long gaps/disappearing danmaku on watches.
            mDanmakuView.setSpeed(1.0F);
        } catch (RuntimeException error) {
            Logu.e("danmaku-speed", "同步弹幕倍速失败: " + error);
        }
    }

    private void setPlayerSpeed(IMediaPlayer player, float speed) {
        if (player instanceof UnifiedDashPlayer) {
            ((UnifiedDashPlayer) player).setPlaybackSpeed(speed);
            return;
        }
        if (!(player instanceof IjkMediaPlayer)) return;
        try {
            ((IjkMediaPlayer) player).setSpeed(speed);
        } catch (RuntimeException error) {
            Logu.e("player-speed", "切换倍速失败: " + error);
        }
    }

    private int getPlayerMemoryClass() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        return manager != null ? manager.getMemoryClass() : 0;
    }

    private void startLongPressSpeed() {
        float speed = PlayerCompatibilityUtil.longPressSpeed(Build.VERSION.SDK_INT, getPlayerMemoryClass());
        setPlaybackSpeed(speed);
        text_speed.setText(String.format(Locale.CHINA, "x %.1f", speed));
        longPressStartedAt = lastLongPressSampleAt = SystemClock.elapsedRealtime();
        lastLongPressSamplePosition = video_now;
        longPressMemoryClass = getPlayerMemoryClass();
        onLongClick = true;
        recordLongPressPerformance("start", video_now, longPressStartedAt);
        longPressPanSequence=isVideoZoomed()&&!multiTouchGesture
                &&SharedPreferencesUtil.getBoolean("player_doublemove",true)
                &&SharedPreferencesUtil.getBoolean("player_singlemove",true);
        if(longPressPanSequence){previousX=lastSurfaceX;previousY=lastSurfaceY;longPressSwitch.cancel();}
        longPressSwitchSequence = !longPressPanSequence&&SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_LONGPRESS_SWITCH, true)
                && !multiTouchGesture;
        if (longPressSwitchSequence) longPressSwitch.arm(lastSurfaceX, lastSurfaceY,
                layout_control.getHeight(), getResources().getDisplayMetrics().density,
                ViewConfiguration.get(this).getScaledTouchSlop());
        if (longPressSpeedHint != null) {
            longPressSpeedHint.setText(getString(R.string.player_longpress_speed_hint, speed));
            updateLongPressSpeedHint();
        }
    }

    private void updateLongPressSpeedHint() {
        if(!com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.getBoolean(
                com.RobinNotBad.BiliClient.util.PlayerSettingsUtil.LONGPRESS_HINT_ENABLED,true)) {
            longPressSpeedHint.setVisibility(View.GONE);return;
        }
        float scale=com.RobinNotBad.BiliClient.util.PlayerSettingsUtil.getLongPressHintScale();
        int width=layout_control.getWidth()>0?layout_control.getWidth():getResources().getDisplayMetrics().widthPixels;
        int height=layout_control.getHeight()>0?layout_control.getHeight():getResources().getDisplayMetrics().heightPixels;
        float density=Math.min(getResources().getDisplayMetrics().density,Math.min(width,height)/200f);
        int padding=Math.max(1,Math.round(4*density*scale));
        longPressSpeedHint.setPadding(padding,padding,padding,padding);
        int maxWidth=Math.max(1,Math.round((width-playerSafeLeft-playerSafeRight)*.72f));
        android.graphics.Paint measure=new android.graphics.Paint(longPressSpeedHint.getPaint());
        measure.setTextSize(100f);
        float units=measure.measureText(longPressSpeedHint.getText().toString())/100f;
        // Reserve room at the largest supported scale, so increasing the setting
        // never makes the glyphs smaller when a very narrow watch reaches the cap.
        int maxPadding=Math.max(1,Math.round(4*density*1.25f));
        float base=Math.min(13*density*getResources().getConfiguration().fontScale,
                Math.max(1,maxWidth-2*maxPadding-4)/Math.max(1,units)/1.25f);
        longPressSpeedHint.setTextSize(TypedValue.COMPLEX_UNIT_PX,Math.max(1,base*scale));
        longPressSpeedHint.setEllipsize(null);
        longPressSpeedHint.setMaxWidth(maxWidth);
        android.widget.RelativeLayout.LayoutParams params=(android.widget.RelativeLayout.LayoutParams)longPressSpeedHint.getLayoutParams();
        params.topMargin=playerSafeTop+Math.round(4*density);longPressSpeedHint.setLayoutParams(params);
        longPressSpeedHint.setVisibility(View.VISIBLE);
    }

    private void stopLongPressSpeed() {
        longPressSwitch.cancel();
        if (longPressSpeedHint != null) longPressSpeedHint.setVisibility(View.GONE);
        if (!onLongClick) return;
        recordLongPressPerformance("end", video_now, SystemClock.elapsedRealtime());
        onLongClick = false;
        int speedPosition = seekbar_speed.getProgress();
        float normalSpeed = speed_values[speedPosition];
        setPlaybackSpeed(normalSpeed);
        text_speed.setText(speed_strs[speedPosition]);
    }

    private void recordLongPressPerformance(String phase, long position, long now) {
        try {
            JSONObject details = new JSONObject().put("phase", phase).put("sdk", Build.VERSION.SDK_INT)
                    .put("memory_class_mb", longPressMemoryClass).put("requested_speed", currentPlaybackSpeed)
                    .put("held_ms", Math.max(0L, now - longPressStartedAt)).put("position_ms", position)
                    .put("quality", currentQuality).put("video_buffering", videoTrackBuffering)
                    .put("audio_buffering", audioTrackBuffering).put("danmaku", isDanmakuVisible)
                    .put("player", ijkPlayer == null ? "none" : ijkPlayer.getClass().getSimpleName());
            long elapsed = now - lastLongPressSampleAt;
            if (elapsed > 0 && position >= lastLongPressSamplePosition) {
                details.put("sample_ms", elapsed).put("advance_ms", position - lastLongPressSamplePosition)
                        .put("observed_speed", (position - lastLongPressSamplePosition) / (double) elapsed);
            }
            DiagnosticLogManager.record("player_longpress", details);
        } catch (Exception ignored) { }
        lastLongPressSampleAt = now;
        lastLongPressSamplePosition = position;
    }

    private boolean getExtras() {
        Intent intent = getIntent();
        if (intent == null)
            return false;
        bangumiPlayback = intent.getIntExtra("content_type", 0) == PlayerData.TYPE_BANGUMI;
        pgcSeason = intent.getLongExtra("pgc_season", 0); pgcEpisode = intent.getLongExtra("pgc_episode", 0);
        pgcType = intent.getIntExtra("pgc_type", 1);
        pgcAccount = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        segmentPolicy = new com.RobinNotBad.BiliClient.player.PlaybackSegments(intent.getStringExtra("clip_segments"));

        video_url = intent.getStringExtra("url");
        ArrayList<String> backupUrls = intent.getStringArrayListExtra("backup_urls");
        if (backupUrls == null) {
            String[] backupUrlArray = intent.getStringArrayExtra("backup_urls");
            if (backupUrlArray != null) backupUrls = new ArrayList<>(Arrays.asList(backupUrlArray));
        }
        if (backupUrls == null) {
            String backupUrl = intent.getStringExtra("backup_url");
            if (backupUrl != null && !backupUrl.isEmpty()) {
                backupUrls = new ArrayList<>();
                backupUrls.add(backupUrl);
            }
        }
        setPlaybackUrls(video_url, backupUrls);
        audio_url = intent.getStringExtra("audio_url");
        ArrayList<String> audioBackupUrls = intent.getStringArrayListExtra("audio_backup_urls");
        setDashAudioUrls(audio_url, audioBackupUrls);
        danmaku_url = intent.getStringExtra("danmaku");
        cachedPlaybackToken = intent.getStringExtra(CachedPlaybackSnapshot.TOKEN);
        favoritePlaybackToken = intent.getStringExtra(com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.TOKEN);
        explicitOnlineQueue = favoritePlaybackToken != null && intent.getBooleanExtra(
                com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.EXPLICIT, false);
        localSubtitleRoot = intent.getStringExtra("local_subtitle_root");
        if (localSubtitleRoot == null) localSubtitleRoot = "";
        localProgressFolderLocator = intent.getStringExtra("local_progress_folder");
        if (localProgressFolderLocator == null) localProgressFolderLocator = "";
        String title = intent.getStringExtra("title");

        if (video_url == null)
            return false;
        if (danmaku_url != null)
            Logu.v("弹幕", danmaku_url);
        Logu.v("视频", video_url);
        Logu.v("标题", title);
        text_title.setText(title);
        videoTitle = title;

        aid = intent.getLongExtra("aid", 0);
        cid = intent.getLongExtra("cid", 0);
        mid = intent.getLongExtra("mid", 0);

        progress_history = intent.getIntExtra("progress", 0);
        interactionRestoreSnapshot = InteractionProgressStore.load(aid);
        Logu.d("history", String.valueOf(progress_history));

        isLiveMode = intent.getBooleanExtra("live_mode", false);
        isOnlineVideo = video_url.startsWith("http://") || video_url.startsWith("https://");
        hasDanmaku = danmaku_url != null && !danmaku_url.equals("");
        resetDanmakuSource(intent.getLongExtra("danmaku_count", -1));
        // 本地缓存的进度文件是最终状态，优先于列表传入的旧进度。
        loadLocalPlaybackProgress();

        if (intent.hasExtra("pagenames") && intent.hasExtra("cids")) {
            pagenames = intent.getStringArrayListExtra("pagenames");
            ArrayList<Long> cidList = new ArrayList<>();
            long[] cidArray = intent.getLongArrayExtra("cids");
            if (cidArray != null) {
                for (long c : cidArray) {
                    cidList.add(c);
                }
            }
            cids = cidList;
            currentPageIndex = intent.getIntExtra("currentPageIndex", 0);
        }

        initialEdgeId = intent.getLongExtra("edgeId", 0);
        if (initialEdgeId > 0) {
            currentEdgeId = initialEdgeId;
        }

        if (intent.hasExtra("qnStrList") && intent.hasExtra("qnValueList")) {
            qnStrList = intent.getStringArrayExtra("qnStrList");
            qnValueList = intent.getIntArrayExtra("qnValueList");
            currentQuality = intent.getIntExtra("currentQuality", com.RobinNotBad.BiliClient.util.NetworkPlaybackQuality.defaultQuality(this));
        }

        return true;
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logu.v("加载", "加载");
        super.onCreate(savedInstanceState);

        screen_landscape = SharedPreferencesUtil.getBoolean("player_autolandscape", false)
                || SharedPreferencesUtil.getBoolean("ui_landscape", false);
        if (useSoftwarePlayerRotation()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(screen_landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        setContentView(R.layout.activity_player);
        findview();
        if (!getExtras()) {
            finish();
            return;
        }

        initUI();
        if (screen_landscape && useSoftwarePlayerRotation()) softwareRotate();

        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_MEDIA_SESSION_ENABLE, false)) {
            initMediaSession();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
            batteryView.setPower(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        } else
            batteryView.setVisibility(View.GONE);

        loop_enabled = SharedPreferencesUtil.getBoolean("player_loop", false);
        if (savedInstanceState != null) {
            int savedManual=savedInstanceState.getInt("manual_playback_quality",-1);
            if(com.RobinNotBad.BiliClient.util.PlaybackQualityPolicy.valid(savedManual))manualPlaybackQuality=savedManual;
            if (savedInstanceState.getLong("playlist_resume_account", -1) ==
                    SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
                playlistResumePoints.restore(savedInstanceState.getStringArray("playlist_resume_keys"),
                        savedInstanceState.getLongArray("playlist_resume_values"));
                if (isOnlineVideo && !isLiveMode && !bangumiPlayback)
                    progress_history = playlistResumePoints.position(aid, cid, cid, progress_history);
            }
            explicitOnlineEnabled = savedInstanceState.getBoolean("explicit_online_enabled", true);
            collectionPlaybackOrder = Math.max(0, Math.min(3, savedInstanceState.getInt("collection_order", 0)));
            long[] savedQueue = savedInstanceState.getLongArray("collection_aids");
            if (savedQueue != null && favoritePlaybackToken == null) collectionQueue = new CollectionPlaybackQueue(
                    savedInstanceState.getLong("collection_id"), savedQueue);
        }
        // 从设置读取听视频模式的默认值
        isAudioOnlyMode = SharedPreferencesUtil.getBoolean("player_audio_only", false);
        cachedUserAudioOnlyMode = savedInstanceState == null ? isAudioOnlyMode
                : savedInstanceState.getBoolean("cached_user_audio_only", isAudioOnlyMode);
        // 从Intent读取是否为仅音频模式（用于播放本地音频文件）
        isLocalAudioFile = getIntent().getBooleanExtra("audio_only", false);
        if (isLocalAudioFile) {
            isAudioOnlyMode = true;
        }
        ijkPlayer = createMediaPlayer();
        img_loading.setImageResource(R.drawable.loading_tv_shaking);
        anim_loading = (AnimationDrawable) img_loading.getDrawable();
        anim_loading.start();

        File cachepath = getCacheDir();
        if (!cachepath.exists())
            cachepath.mkdirs();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        if(savedInstanceState!=null){
            audioSleepDeadline=savedInstanceState.getLong("audio_sleep_deadline",0);
            if(audioSleepDeadline>0)mainHandler.post(audioSleepCheck);
        }

        setVideoGestures();
        autohideReset();

        initSeekbars();

        if (isLiveMode) {
            btn_control.setVisibility(View.GONE); // 直播模式隐藏暂停按钮，使用GONE而不是INVISIBLE以保持UI布局正确
            seekbar_progress.setVisibility(View.GONE);
            seekbar_progress.setEnabled(false);
            streamDanmaku(null); // 用来初始化一下弹幕层
        }

        layout_control.postDelayed(() -> CenterThreadPool.run(() -> { // 等界面加载完成
            if (isLiveMode) {
                runOnUiThread(() -> {
                    btn_menu.setVisibility(View.VISIBLE);
                    btn_danmaku_send.setVisibility(View.VISIBLE);
                    btn_subtitle.setVisibility(View.GONE);
                    // 直播模式隐藏清晰度按钮
                    btn_quality.setVisibility(View.GONE);
                    // 直播模式隐藏循环按钮
                    btn_loop.setVisibility(View.GONE);
                    updateAudioOnlyButton();
                    btn_audio_only.setOnClickListener(view -> toggleAudioOnlyMode());
                    btn_audio_only.setVisibility(View.VISIBLE);
                    // 直播模式隐藏自动下一个按钮
                    btn_auto_next.setVisibility(View.GONE);
                    // 直播模式隐藏分P选择器按钮
                    btn_page_selector.setVisibility(View.GONE);
                });
                setDisplay();
                return;
            }

            runOnUiThread(() -> {
                loading_text0.setText("装填弹幕中");
                loading_text1.setText("(≧∇≦)");
            });
            if (isOnlineVideo) {
                danmakuFile = new File(cachepath, "danmaku.xml");
                downdanmu();
            } else {
                runOnUiThread(() -> btn_danmaku_send.setVisibility(View.GONE));
                // Subtitle discovery may use this directory even while danmaku is off.
                if (danmaku_url != null && !danmaku_url.isEmpty() && !danmaku_url.startsWith("content://"))
                    danmakuFile = new File(danmaku_url);
                downdanmu();
            }

            if (!destroyed && SharedPreferencesUtil.getBoolean("player_subtitle_autoshow", true))
                downSubtitle(false);

            // 加载高能进度条数据
            if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                loadHighEnergyData();
            }

            if (!destroyed && isOnlineVideo && aid > 0 && cid > 0 && SharedPreferencesUtil.getBoolean("player_show_viewpoints", true)) {
                loadViewPoints();
            }

            if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                loadInteractionVideo();
            }

            if (!destroyed)
                setDisplay();
        }), 60);
    }

    private void findview() {
        layout_control = findViewById(R.id.control_layout);
        layout_top = findViewById(R.id.top);
        infoOverlay = new PlayerInfoOverlay(findViewById(R.id.interaction_values_scroll),
                findViewById(R.id.interaction_values), findViewById(R.id.player_chapter_list), position -> {
                    if (destroyed || screenLocked || isLiveMode) return;
                    seekToPosition(position);
                    autohideReset();
                });
        right_control = findViewById(R.id.right_control);
        applyPlayerMargins(right_control,0,0,playerDp(4),0);
        right_second = findViewById(R.id.right_second);
        layout_card_bg = findViewById(R.id.card_bg);
        card_subtitle = findViewById(R.id.subtitle_card);
        card_danmaku_send = findViewById(R.id.danmaku_send_card);
        card_page_selector = findViewById(R.id.page_selector_card);
        card_quality_selector = findViewById(R.id.quality_selector_card);
        card_viewpoint_selector = findViewById(R.id.viewpoint_selector_card);
        layout_audio_only = findViewById(R.id.audio_only_layout);

        loading_info = findViewById(R.id.loading_info);

        img_loading = findViewById(R.id.circle);
        text_progress = findViewById(R.id.text_progress);
        text_online = findViewById(R.id.text_online);
        btn_danmaku = findViewById(R.id.danmaku_btn);
        btn_loop = findViewById(R.id.loop_btn);
        btn_rotate = findViewById(R.id.rotate_btn);
        btn_menu = findViewById(R.id.menu_btn);
        btn_danmaku_send = findViewById(R.id.danmaku_send_btn);
        btn_subtitle = findViewById(R.id.subtitle_btn);
        btn_audio_only = findViewById(R.id.audio_only_btn);
        btn_control = findViewById(R.id.button_video);
        btn_page_selector = findViewById(R.id.button_page_selector);
        btn_auto_next = findViewById(R.id.auto_next_btn);
        btn_quality = findViewById(R.id.button_quality);
        btn_collection_order = findViewById(R.id.collection_order_btn);
        btn_viewpoint = findViewById(R.id.viewpoint_btn);
        btn_lock = findViewById(R.id.screen_lock_btn);
        seekbar_progress = findViewById(R.id.videoprogress);
        loading_text0 = findViewById(R.id.loading_text0);
        loading_text1 = findViewById(R.id.loading_text1);
        text_title = findViewById(R.id.text_title);
        text_volume = findViewById(R.id.showsound);
        layout_video = findViewById(R.id.videoArea);
        mDanmakuView = findViewById(R.id.sv_danmaku);
        batteryView = findViewById(R.id.battery);

        text_speed = findViewById(R.id.text_speed);
        longPressSpeedHint = findViewById(R.id.long_press_speed_hint);
        layout_control.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                {updateRightToolbarBounds();updateExpandedMenuBounds();updateAudioDashboard();});
        findViewById(R.id.bottom_control).addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateRightToolbarBounds());
        layout_top.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{updateRightToolbarBounds();updateAudioDashboard();});
        text_speed.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateRightToolbarBounds());
        btn_lock.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateRightToolbarBounds());
        btn_menu.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateExpandedMenuBounds());
        findViewById(R.id.right_toolbar_scroll).getViewTreeObserver().addOnScrollChangedListener(this::updateExpandedMenuBounds);
        layout_speed = findViewById(R.id.layout_speed);
        seekbar_speed = findViewById(R.id.seekbar_speed);
        text_newspeed = findViewById(R.id.text_newspeed);
        bottom_buttons = findViewById(R.id.bottom_buttons);
        btn_debug = findViewById(R.id.btn_debug);

        text_subtitle = findViewById(R.id.text_subtitle);
        text_audio_title = findViewById(R.id.audio_title);
        text_audio_subtitle = findViewById(R.id.audio_subtitle);
        audioPanel=findViewById(R.id.audio_panel);
        findViewById(R.id.round_menu_btn).setOnClickListener(v->showCompactPlayerTools());
        audioPanel.setActions(new com.RobinNotBad.BiliClient.player.AudioPlaybackPanel.Actions(){
            public void play(){if(!screenLocked&&isPrepared&&!playbackSwitchInFlight)setMediaPlaying(!desiredMediaPlaying());}
            public void seek(long position){if(!screenLocked&&isPrepared&&!playbackSwitchInFlight)seekToPosition(position);}
            public void skip(boolean next){if(!screenLocked)skipPlaybackItem(next);}
            public void more(){showAudioOptions();}
            public void controls(){if(screenLocked)return;if(layout_top.getVisibility()==View.VISIBLE)hidecon.run();else showcon();}
            public boolean hold(float x,float y){
                if(screenLocked||!isPrepared||!isPlaying||playbackSwitchInFlight)return false;
                lastSurfaceX=x;lastSurfaceY=y;gesture_click_disabled=false;surfaceClickCancelled=false;multiTouchGesture=false;
                return layout_control.performLongClick();
            }
            public void holdTouch(MotionEvent event){handleLongPressSwitch(event);}
            public void releaseHold(){stopLongPressSpeed();longPressSwitchSequence=false;gesture_click_disabled=false;}
        });
        swipe_seek_hint = findViewById(R.id.swipe_seek_hint);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setVideoGestures() {
        if (SharedPreferencesUtil.getBoolean("player_scale", true)) {
            scaleGestureListener = new ViewScaleGestureListener(layout_video);
            scaleGestureDetector = new ScaleGestureDetector(this, scaleGestureListener);

            boolean move_enabled = SharedPreferencesUtil.getBoolean("player_doublemove", true);
            boolean single_move_enabled = SharedPreferencesUtil.getBoolean("player_singlemove", true);
            boolean antiMistouchEnabled = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_ANTI_MISTOUCH, true);

            layout_control.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {gesture_click_disabled = false;longPressPanSequence=false;}
                if (screenLocked) return handleLockedPlayerTouch(event);
                if (handleLongPressPan(event)) return true;
                if (handleLongPressSwitch(event)) return true;
                boolean consumeSurfaceTouch = trackSurfaceTap(v, event);
                int pointerCount = event.getPointerCount();
                boolean singleTouch = pointerCount == 1;
                boolean doubleTouch = pointerCount == 2;
                boolean swipeSeekEnabled = SharedPreferencesUtil.getBoolean(
                        SharedPreferencesUtil.PLAYER_SWIPE_SEEK, false);
                boolean zoomedVideo = isVideoZoomed();

                // Logu.v("gesture", event.getEventTime() + "");
                scaleGestureDetector.onTouchEvent(event);
                boolean gesture_scaling = scaleGestureListener.scaling;

                if (!gesture_scaled && gesture_scaling)
                    gesture_scaled = true;

                boolean zoomedSeekMode = zoomedVideo && swipeSeekEnabled
                        && zoomedSingleFingerSeekMode && single_move_enabled
                        && move_enabled && !isLiveMode;
                boolean seekGestureAllowed = swipeSeekEnabled && !isLiveMode
                        && (!zoomedVideo || !move_enabled || !single_move_enabled || zoomedSeekMode);

                // Logu.v("gesture", (scaling ? "scaled-yes" : "scaled-no"));
                boolean verticalGestureActive = handleVerticalGesture(event, zoomedVideo, gesture_scaling);
                if (verticalGestureActive) gesture_moved = true;

                switch (action) {
                    case MotionEvent.ACTION_MOVE:
                        if (singleTouch) {
                            if (gesture_scaling) {
                                videoMoveBy(0, 0); // 防止单指缩放出框
                            } else if (move_enabled && single_move_enabled
                                    && zoomedVideo
                                    && (!swipeSeekEnabled || !zoomedSeekMode)
                                    && !verticalGestureActive
                                    && !multiTouchGesture) {
                                // A zoomed video pans by default. When the user
                                // taps to enter seek mode, the seek handler below
                                // owns the horizontal movement instead.
                                float currentX = event.getX(0);
                                float currentY = event.getY(0);
                                float deltaX = currentX - previousX;
                                float deltaY = currentY - previousY;
                                // 增加移动阈值，避免微小移动触发防误触
                                float minMoveDelta = antiMistouchEnabled ? ToolsUtil.dp2px(8) : ToolsUtil.dp2px(3);
                                if (Math.abs(deltaX) > minMoveDelta || Math.abs(deltaY) > minMoveDelta) {
                                    videoMoveBy(deltaX, deltaY);
                                    previousX = currentX;
                                    previousY = currentY;
                                    gesture_moved = true;
                                }
                            } else if (move_enabled && !single_move_enabled
                                    && !swipeSeekEnabled
                                    && !zoomedVideo
                                    && !verticalGestureActive
                                    && !multiTouchGesture) {
                                // Preserve the original single-finger pan
                                // behavior when swipe seeking is disabled.
                                float currentX = event.getX(0);
                                float currentY = event.getY(0);
                                float deltaX = currentX - previousX;
                                float deltaY = currentY - previousY;
                                // 增加移动阈值
                                float minMoveDelta = antiMistouchEnabled ? ToolsUtil.dp2px(8) : ToolsUtil.dp2px(3);
                                if (Math.abs(deltaX) > minMoveDelta || Math.abs(deltaY) > minMoveDelta) {
                                    videoMoveBy(deltaX, deltaY);
                                    previousX = currentX;
                                    previousY = currentY;
                                    gesture_moved = true;
                                }
                            }
                        }
                        if (doubleTouch && move_enabled && !single_move_enabled) {
                            float currentX = (event.getX(0) + event.getX(1)) / 2;
                            float currentY = (event.getY(0) + event.getY(1)) / 2;
                            float deltaX = currentX - previousX;
                            float deltaY = currentY - previousY;
                            if (deltaX != 0f || deltaY != 0f) {
                                videoMoveBy(deltaX, deltaY);
                                previousX = currentX;
                                previousY = currentY;
                            }
                        }
                        break;

                    case MotionEvent.ACTION_DOWN:
                        multiTouchGesture = false;
                        if (singleTouch) { // 如果是单指按下，设置起始位置为当前手指位置
                            previousX = event.getX(0);
                            previousY = event.getY(0);
                            // Logu.v("gesture", "touch_start:" + previousX + "," + previousY);
                        }
                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        multiTouchGesture = true;
                        cancelPendingSingleTap();
                        gesture_click_disabled = true;
                        zoomedSingleFingerSeekMode = false;
                        swipeSeeking = false;
                        if (swipe_seek_hint != null) swipe_seek_hint.setVisibility(View.GONE);
                        if (doubleTouch) { // 如果是双指按下，设置起始位置为两指连线的中心点
                            previousX = (event.getX(0) + event.getX(1)) / 2;
                            previousY = (event.getY(0) + event.getY(1)) / 2;
                            // Logu.v("gesture","double_touch");
                        }
                        break;

                    case MotionEvent.ACTION_POINTER_UP:
                        if (doubleTouch) {
                            int index = event.getActionIndex(); // actionIndex是抬起来的手指位置
                            previousX = event.getX((index == 0 ? 1 : 0));
                            previousY = event.getY((index == 0 ? 1 : 0));
                            // Logu.v("gesture","single_touch");
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (action == MotionEvent.ACTION_UP && swipeSeeking && !multiTouchGesture
                                && !isLiveMode) {
                            long target = swipeSeekTarget(event.getX(0));
                            seekToPosition(target);
                            MsgUtil.showMsg("已调整到 " + StringUtil.toTime((int) (target / 1000)));
                        }
                        if (swipe_seek_hint != null) swipe_seek_hint.setVisibility(View.GONE);
                        swipeSeeking = false;
                        stopLongPressSpeed();
                        if (gesture_moved)
                            gesture_moved = false;
                        if (gesture_scaled)
                            gesture_scaled = false;
                        if (!isVideoZoomed() || layout_top.getVisibility() != View.VISIBLE)
                            zoomedSingleFingerSeekMode = false;
                        multiTouchGesture = false;
                        break;
                }

                if (action == MotionEvent.ACTION_DOWN && singleTouch
                        && seekGestureAllowed) {
                    swipeDownX = event.getX(0);
                    swipeDownY = event.getY(0);
                    swipeSeeking = false;
                    swipeSeekStartPosition = ijkPlayer == null ? progress_history : Math.max(0L, ijkPlayer.getCurrentPosition());
                } else if (action == MotionEvent.ACTION_MOVE && singleTouch
                        && seekGestureAllowed
                        && !swipeSeeking
                        && !verticalGestureActive
                        && !gesture_scaling && !multiTouchGesture) {
                    float dx = event.getX(0) - swipeDownX;
                    float dy = event.getY(0) - swipeDownY;
                    if (Math.abs(dx) > ToolsUtil.dp2px(24) && Math.abs(dx) > Math.abs(dy) * 1.35f) {
                        swipeSeeking = true;
                        gesture_moved = true;
                        // Do not let the movement needed to recognize a horizontal
                        // gesture become an accidental seek jump.
                        swipeDownX = event.getX(0);
                        swipeSeekStartPosition = ijkPlayer == null
                                ? progress_history
                                : Math.max(0L, ijkPlayer.getCurrentPosition());
                    }
                }

                if (action == MotionEvent.ACTION_MOVE && swipeSeeking
                        && !multiTouchGesture && !gesture_scaling && !verticalGestureActive) {
                    updateSwipeSeekHint(event.getX(0));
                }

                // 只有在启用防误触且有明显移动/缩放时才禁用点击
                if (!gesture_click_disabled && antiMistouchEnabled && (gesture_moved || gesture_scaled)) {
                    cancelPendingSingleTap();
                    gesture_click_disabled = true;
                    hidecon.run();
                }
                if (action == MotionEvent.ACTION_CANCEL) cancelPendingSingleTap();

                return finishSurfaceTouch(event, consumeSurfaceTouch);
            });
        } else {
            boolean antiMistouchEnabled = SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_ANTI_MISTOUCH, true);

            layout_control.setOnTouchListener((view, motionEvent) -> {
                int action = motionEvent.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    gesture_click_disabled = false;
                    gesture_moved = false;
                    multiTouchGesture = false;
                }
                if (screenLocked) return handleLockedPlayerTouch(motionEvent);
                if (handleLongPressSwitch(motionEvent)) return true;
                boolean consumeSurfaceTouch = trackSurfaceTap(view, motionEvent);
                if (motionEvent.getPointerCount() > 1) {
                    multiTouchGesture = true;
                    cancelPendingSingleTap();
                    gesture_click_disabled = true;
                    swipeSeeking = false;
                    if (swipe_seek_hint != null) swipe_seek_hint.setVisibility(View.GONE);
                }
                boolean verticalGestureActive = handleVerticalGesture(motionEvent, false, false);
                if (verticalGestureActive) gesture_moved = true;
                if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_SWIPE_SEEK, false)
                        && !isLiveMode) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        multiTouchGesture = false;
                        swipeDownX = motionEvent.getX();
                        swipeDownY = motionEvent.getY();
                        swipeSeekStartPosition = ijkPlayer == null ? progress_history : Math.max(0L, ijkPlayer.getCurrentPosition());
                        swipeSeeking = false;
                    } else if (action == MotionEvent.ACTION_MOVE && !swipeSeeking
                            && !verticalGestureActive && !multiTouchGesture) {
                        float dx = motionEvent.getX() - swipeDownX;
                        float dy = motionEvent.getY() - swipeDownY;
                        if (Math.abs(dx) > ToolsUtil.dp2px(24) && Math.abs(dx) > Math.abs(dy) * 1.35f) {
                            swipeSeeking = true;
                            // The activation threshold is for gesture detection only;
                            // it must not be counted as playback movement.
                            swipeDownX = motionEvent.getX();
                            swipeSeekStartPosition = ijkPlayer == null
                                    ? progress_history
                                    : Math.max(0L, ijkPlayer.getCurrentPosition());
                        }
                    }
                    if (action == MotionEvent.ACTION_MOVE && swipeSeeking && !verticalGestureActive) {
                        gesture_moved = true;
                        updateSwipeSeekHint(motionEvent.getX());
                    } else if (action == MotionEvent.ACTION_UP && swipeSeeking && !multiTouchGesture) {
                        long target = swipeSeekTarget(motionEvent.getX());
                        seekToPosition(target);
                        MsgUtil.showMsg("已调整到 " + StringUtil.toTime((int) (target / 1000)));
                    }
                    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && swipe_seek_hint != null) swipe_seek_hint.setVisibility(View.GONE);
                }
                // 只有在启用防误触且有明显移动时才禁用点击
                if (!gesture_click_disabled && antiMistouchEnabled && gesture_moved) {
                    cancelPendingSingleTap();
                    gesture_click_disabled = true;
                    hidecon.run();
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    stopLongPressSpeed();
                    swipeSeeking = false;
                    gesture_moved = false;
                    multiTouchGesture = false;
                }
                if (action == MotionEvent.ACTION_CANCEL) cancelPendingSingleTap();
                return finishSurfaceTouch(motionEvent, consumeSurfaceTouch);
            });
        }

        // 这个管普通点击
        layout_control.setOnClickListener(view -> {
            if (screenLocked) {
                btn_lock.setVisibility(btn_lock.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                return;
            }
            if (gesture_click_disabled)
                gesture_click_disabled = false;
            else
                clickUI();
        });
        // 这个管长按开始
        layout_control.setOnLongClickListener(view -> {
            if (screenLocked) return true;
            cancelPendingSingleTap();
            if (SharedPreferencesUtil.getBoolean("player_longclick", true) && ijkPlayer != null && isPlaying
                    && !isLiveMode) {
                if (!onLongClick && !gesture_click_disabled) {
                    hidecon.run();
                    startLongPressSpeed();
                    Logu.v("gesture", "longclick_down");
                    return true;
                }
                return false;
            }
            return false;
        });

    }

    /** On a zoomed picture, dragging while holding speed belongs to pan, not queue switching. */
    private boolean handleLongPressPan(MotionEvent event){
        if(!longPressPanSequence)return false;
        int action=event.getActionMasked();cancelPendingSingleTap();surfaceTapCandidate=false;gesture_click_disabled=true;
        if(!surfaceClickCancelled){
            MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
            layout_control.onTouchEvent(cancel);cancel.recycle();surfaceClickCancelled=true;
        }
        if(event.getPointerCount()!=1||action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP){
            multiTouchGesture=true;stopLongPressSpeed();
        }else if(action==MotionEvent.ACTION_MOVE&&onLongClick&&!multiTouchGesture){
            float x=event.getX(0),y=event.getY(0),dx=x-previousX,dy=y-previousY;
            float threshold=ToolsUtil.dp2px(SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_ANTI_MISTOUCH,true)?8:3);
            if(Math.abs(dx)>threshold||Math.abs(dy)>threshold){videoMoveBy(dx,dy);previousX=x;previousY=y;gesture_moved=true;}
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            stopLongPressSpeed();longPressPanSequence=false;longPressSwitchSequence=false;
            swipeSeeking=false;gesture_moved=false;gesture_scaled=false;multiTouchGesture=false;gesture_click_disabled=false;
        }
        return true;
    }

    private boolean handleLongPressSwitch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            longPressSwitchSequence = false; longPressSwitch.cancel();
        }
        if (!longPressSwitchSequence) {
            lastSurfaceX = event.getX(0); lastSurfaceY = event.getY(0);
            return false;
        }
        cancelPendingSingleTap();
        surfaceTapCandidate = false;
        gesture_click_disabled = true;
        if (!surfaceClickCancelled) {
            MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
            layout_control.onTouchEvent(cancel); cancel.recycle(); surfaceClickCancelled = true;
        }
        int direction = 0;
        if (event.getPointerCount() != 1 || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL
                || !SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_LONGPRESS_SWITCH, true)) {
            stopLongPressSpeed();
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            for (int i = 0; i < event.getHistorySize() && direction == 0; i++)
                direction = longPressSwitch.update(event.getHistoricalX(0, i), event.getHistoricalY(0, i), event.getHistoricalEventTime(i));
            if (direction == 0) direction = longPressSwitch.update(event.getX(0), event.getY(0), event.getEventTime());
        }
        if (direction != 0) {
            // Restore the user's speed before the queue loads another item. The rest
            // of this physical touch still belongs to this gesture, not the new video.
            stopLongPressSpeed();
            boolean moved = skipPlaybackItem(direction > 0);
            MsgUtil.showMsg(getString(moved ? direction > 0 ? R.string.player_gesture_next
                    : R.string.player_gesture_previous : R.string.player_gesture_no_queue));
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            stopLongPressSpeed(); longPressSwitchSequence = false;
            swipeSeeking = false; gesture_moved = false; gesture_scaled = false;
            multiTouchGesture = false; gesture_click_disabled = false;
        }
        return true;
    }

    /** Tap eligibility follows the whole touch stream, independently of pan/seek recognition.
     * Fast input may deliver its first substantial displacement on UP, or batch an out-and-back
     * movement into one event. Neither may become View's click on release. */
    private boolean trackSurfaceTap(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            surfaceTapCandidate = event.getPointerCount() == 1;
            surfaceClickCancelled = false;
            surfacePointerId = event.getPointerId(0);
            surfaceDownX = event.getX(0);
            surfaceDownY = event.getY(0);
            float slop = ViewConfiguration.get(this).getScaledTouchSlop();
            surfaceTapSlopSquared = slop * slop;
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP || event.getPointerCount() != 1) {
            surfaceTapCandidate = false;
        } else if (surfaceTapCandidate && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)) {
            int pointer = event.findPointerIndex(surfacePointerId);
            if (pointer < 0) surfaceTapCandidate = false;
            else {
                for (int i = 0; i < event.getHistorySize() && surfaceTapCandidate; i++)
                    if (surfaceMovedBeyondTap(event.getHistoricalX(pointer, i), event.getHistoricalY(pointer, i)))
                        surfaceTapCandidate = false;
                if (surfaceMovedBeyondTap(event.getX(pointer), event.getY(pointer))) surfaceTapCandidate = false;
            }
        }
        if (!surfaceTapCandidate) {
            cancelPendingSingleTap();
            gesture_click_disabled = true;
            if (!surfaceClickCancelled) {
                // Cancel only this surface View's pressed/long-click/click state. Gesture handlers
                // still receive every real event, and child playback buttons keep their own taps.
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                view.onTouchEvent(cancel);
                cancel.recycle();
                surfaceClickCancelled = true;
            }
        }
        return !surfaceTapCandidate;
    }

    private boolean surfaceMovedBeyondTap(float x, float y) {
        float dx = x - surfaceDownX, dy = y - surfaceDownY;
        return dx * dx + dy * dy > surfaceTapSlopSquared;
    }

    private boolean finishSurfaceTouch(MotionEvent event, boolean consumed) {
        if (consumed && (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            // View already received CANCEL, so this UP cannot click. Keep future accessibility
            // performClick actions available even when no subsequent physical DOWN is delivered.
            gesture_click_disabled = false;
        }
        return consumed;
    }

    private boolean handleLockedPlayerTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            lockedTapCandidate = event.getPointerCount() == 1;
            lockedTouchDownX = event.getX();
            lockedTouchDownY = event.getY();
        } else if (action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP || event.getPointerCount() != 1) {
            lockedTapCandidate = false;
        }
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            float dx = event.getX() - lockedTouchDownX;
            float dy = event.getY() - lockedTouchDownY;
            int slop = ViewConfiguration.get(this).getScaledTouchSlop();
            if (dx * dx + dy * dy > slop * slop) lockedTapCandidate = false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean tap = action == MotionEvent.ACTION_UP && lockedTapCandidate
                    && event.getEventTime() - event.getDownTime() < ViewConfiguration.getLongPressTimeout();
            lockedTapCandidate = false;
            // Use the normal click path for accessibility, without entering playback gestures.
            if (tap) layout_control.performClick();
            stopLongPressSpeed();
            if (swipe_seek_hint != null) swipe_seek_hint.setVisibility(View.GONE);
            gesture_moved = false;
            gesture_scaled = false;
            multiTouchGesture = false;
        }
        return true;
    }

    private boolean isVideoZoomed() {
        return !isAudioOnlyMode && scaleGestureListener != null
                && (scaleGestureListener.can_reset
                || layout_video != null && Math.abs(layout_video.getScaleX() - 1f) > 0.01f);
    }

    private void autohideReset() {
        if (screenLocked || destroyed) return;
        layout_control.removeCallbacks(hidecon);
        layout_control.postDelayed(hidecon, 4000);
    }

    private void positionPlaybackSpeedLabel() {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) text_speed.getLayoutParams();
        boolean showLock = btn_lock.getVisibility() == View.VISIBLE;
        int above = showLock ? R.id.screen_lock_btn : 0;
        int centered = showLock ? 0 : RelativeLayout.TRUE;
        if (params.getRules()[RelativeLayout.ABOVE] == above
                && params.getRules()[RelativeLayout.CENTER_VERTICAL] == centered) return;
        params.addRule(RelativeLayout.ABOVE, above);
        params.addRule(RelativeLayout.CENTER_VERTICAL, centered);
        text_speed.setLayoutParams(params);
    }

    private void setScreenLocked(boolean locked) {
        if (btn_lock == null) return;
        if (locked == screenLocked && (!locked || lockUiStateSaved)) return;
        lockedTapCandidate = false;

        if (locked) {
            cancelPendingSingleTap();
            zoomedSingleFingerSeekMode = false;
            lockUiStateSaved = true;
            lockedDanmakuVisibility = btn_danmaku.getVisibility();
            lockedRotateVisibility = btn_rotate.getVisibility();
            lockedMenuVisibility = btn_menu.getVisibility();
            lockedRightSecondVisibility = right_second.getVisibility();
            lockedTopVisibility = layout_top.getVisibility();
            lockedBottomVisibility = bottom_buttons.getVisibility();
            lockedProgressVisibility = seekbar_progress.getVisibility();
            lockedSpeedVisibility = text_speed.getVisibility();
            lockedDebugVisibility = btn_debug.getVisibility();

            screenLocked = true;
            updatePlayerInfoOverlay();
            if (segmentSkipButton != null) segmentSkipButton.setVisibility(View.GONE);
            layout_control.removeCallbacks(hidecon);
            layout_card_bg.setVisibility(View.GONE);
            card_subtitle.setVisibility(View.GONE);
            card_danmaku_send.setVisibility(View.GONE);
            card_page_selector.setVisibility(View.GONE);
            card_quality_selector.setVisibility(View.GONE);
            card_viewpoint_selector.setVisibility(View.GONE);
            if (playbackOptionsMenu != null) playbackOptionsMenu.dismiss();
            btn_collection_order.setVisibility(View.GONE);
            right_second.setVisibility(View.GONE);
            layout_top.setVisibility(View.GONE);
            bottom_buttons.setVisibility(View.GONE);
            seekbar_progress.setVisibility(View.GONE);
            text_speed.setVisibility(View.GONE);
            btn_debug.setVisibility(View.GONE);
            btn_danmaku.setVisibility(View.GONE);
            btn_rotate.setVisibility(View.GONE);
            btn_menu.setVisibility(View.GONE);
            right_control.setVisibility(View.GONE);
            btn_lock.setVisibility(View.VISIBLE);
            btn_lock.setImageResource(R.drawable.icon_lock);
            btn_lock.setContentDescription("已锁定，点击解锁播放器");
            stopLongPressSpeed();
            if (swipe_seek_hint != null) swipe_seek_hint.setVisibility(View.GONE);
            swipeSeeking = false;
            gesture_moved = false;
            gesture_scaled = false;
            multiTouchGesture = false;
        } else {
            screenLocked = false;
            if (lockUiStateSaved) {
                btn_danmaku.setVisibility(lockedDanmakuVisibility);
                btn_rotate.setVisibility(lockedRotateVisibility);
                btn_menu.setVisibility(lockedMenuVisibility);
                right_second.setVisibility(View.GONE);
                layout_top.setVisibility(lockedTopVisibility);
                bottom_buttons.setVisibility(lockedBottomVisibility);
                seekbar_progress.setVisibility(lockedProgressVisibility);
                text_speed.setVisibility(lockedSpeedVisibility);
                btn_debug.setVisibility(lockedDebugVisibility);
            }
            menu_opened = false;
            btn_menu.setImageResource(R.mipmap.morehide);
            btn_lock.setVisibility(View.VISIBLE);
            btn_lock.setImageResource(R.drawable.icon_unlock);
            btn_lock.setContentDescription("未锁定，点击锁定播放器");
            lockUiStateSaved = false;
            updateCollectionPlaybackControls();
            showcon();
        }
    }

    private void clickUI() {
        if (screenLocked) return;
        long now_timestamp = SystemClock.uptimeMillis();
        if (timestamp_click != 0L
                && now_timestamp - timestamp_click < ViewConfiguration.getDoubleTapTimeout()) {
            boolean restoreTapState = singleTapChangedControls;
            boolean wasControlsVisible = controlsVisibleBeforeTap;
            cancelPendingSingleTap();
            // The first tap is applied immediately for responsive controls. If this
            // becomes a double tap, restore the prior control state before applying
            // the configured double-tap action.
            if (restoreTapState) {
                if (wasControlsVisible) showcon();
                else hidecon.run();
            }
            int mode = PlayerSettingsUtil.getDoubleTapMode();
            if (mode == 0) {
                if (!isLiveMode) togglePlayback();
            } else if (mode == 1) {
                resetVideoTransform();
            } else if (!resetVideoTransform() && !isLiveMode) {
                togglePlayback();
            }
        } else {
            cancelPendingSingleTap();
            timestamp_click = now_timestamp;
            controlsVisibleBeforeTap = layout_top.getVisibility() == View.VISIBLE;
            singleClickUI();
            singleTapChangedControls = controlsVisibleBeforeTap
                    != (layout_top.getVisibility() == View.VISIBLE);
        }
    }

    private void cancelPendingSingleTap() {
        timestamp_click = 0L;
        singleTapChangedControls = false;
    }

    private void singleClickUI() {
        boolean showControls = layout_top.getVisibility() != View.VISIBLE;
        zoomedSingleFingerSeekMode = showControls && isVideoZoomed() && !isLiveMode
                && SharedPreferencesUtil.getBoolean("player_doublemove", true)
                && SharedPreferencesUtil.getBoolean("player_singlemove", true)
                && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_SWIPE_SEEK, false)
                && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_ZOOM_TAP_SEEK, false);
        if (showControls) showcon();
        else hidecon.run();
    }

    private boolean resetVideoTransform() {
        if (!SharedPreferencesUtil.getBoolean("player_scale", true)
                || scaleGestureListener == null || !scaleGestureListener.can_reset)
            return false;

        scaleGestureListener.can_reset = false;
        videoPanGeneration++;
        zoomedSingleFingerSeekMode = false;
        layout_video.setX(video_origX);
        layout_video.setY(video_origY);
        layout_video.setScaleX(1.0f);
        layout_video.setScaleY(1.0f);
        return true;
    }

    private void togglePlayback() {
        if (isLiveMode)
            return;
        if (isPlaying)
            playerPause();
        else
            playerResume();
        showcon();
    }

    private synchronized void setPlaybackUrls(String primaryUrl, List<String> backupUrls) {
        playbackUrls.clear();
        playbackUrlIndex = 0;
        if (primaryUrl != null && !primaryUrl.isEmpty()) playbackUrls.add(primaryUrl);
        if (backupUrls != null) {
            for (String backupUrl : backupUrls) {
                if (backupUrl != null && !backupUrl.isEmpty() && !playbackUrls.contains(backupUrl)) {
                    playbackUrls.add(backupUrl);
                }
            }
        }
        video_url = primaryUrl;
        localRuntimeRetryCount = 0;
        danmakuPausedByBuffering = false;
        unifiedPlayerFallbackAttempted = false;
    }

    private long swipeSeekTarget(float x) {
        return SwipeSeekUtil.calculateTarget(
                swipeSeekStartPosition,
                x - swipeDownX,
                layout_control.getWidth(),
                Math.max(0L, video_all));
    }

    /** Handles the conventional player split: left side brightness, right side volume. */
    private boolean handleVerticalGesture(MotionEvent event, boolean zoomedVideo,
                                          boolean gestureScaling) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            verticalGestureStartX = event.getX(0);
            verticalGestureStartY = event.getY(0);
            verticalGestureLastY = verticalGestureStartY;
            verticalAdjusting = false;
            brightnessAdjusting = false;
            verticalGestureStartBrightness = getWindowBrightness();
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (onLongClick) {
                verticalAdjusting = false; brightnessAdjusting = false;
                return true;
            }
            if (event.getPointerCount() != 1 || multiTouchGesture || gestureScaling) {
                return verticalAdjusting || brightnessAdjusting;
            }

            float dx = event.getX(0) - verticalGestureStartX;
            float dy = event.getY(0) - verticalGestureStartY;
            if (!verticalAdjusting && !brightnessAdjusting && !zoomedVideo
                    && Math.abs(dy) > ToolsUtil.dp2px(24)
                    && Math.abs(dy) > Math.abs(dx) * 1.35f) {
                boolean verticalGestureEnabled = SharedPreferencesUtil.getBoolean(
                        SharedPreferencesUtil.PLAYER_BRIGHTNESS_GESTURE, true);
                if (verticalGestureEnabled
                        && verticalGestureStartX <= layout_control.getWidth() / 2f) {
                    brightnessAdjusting = true;
                    showPlaybackAdjustment("亮度：" + toPercent(verticalGestureStartBrightness) + "%");
                } else if (verticalGestureEnabled
                        && verticalGestureStartX > layout_control.getWidth() / 2f) {
                    verticalAdjusting = true;
                    showPlaybackAdjustment("音量：" + getVolumePercent() + "%");
                }
            }

            if (brightnessAdjusting) {
                float height = Math.max(1, layout_control.getHeight());
                float brightness = verticalGestureStartBrightness
                        + (verticalGestureStartY - event.getY(0)) / height;
                setWindowBrightness(brightness);
                verticalGestureLastY = event.getY(0);
                showPlaybackAdjustment("亮度：" + toPercent(getWindowBrightness()) + "%");
                return true;
            }
            if (verticalAdjusting) {
                float stepDistance = Math.max(1f, ToolsUtil.dp2px(24));
                float deltaY = verticalGestureLastY - event.getY(0);
                if (Math.abs(deltaY) >= stepDistance) {
                    int steps = (int) (Math.abs(deltaY) / stepDistance);
                    boolean increase = deltaY > 0;
                    for (int i = 0; i < steps; i++) changeVolume(increase);
                    verticalGestureLastY += (increase ? -1 : 1) * steps * stepDistance;
                }
                return true;
            }
            return false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean handled = verticalAdjusting || brightnessAdjusting;
            verticalAdjusting = false;
            brightnessAdjusting = false;
            return handled;
        }
        return false;
    }

    private int getVolumePercent() {
        if (audioManager == null) return 0;
        int max = Math.max(1, audioManager.getStreamMaxVolume(STREAM_MUSIC));
        return Math.round(audioManager.getStreamVolume(STREAM_MUSIC) * 100f / max);
    }

    private int toPercent(float value) {
        return Math.round(Math.max(0f, Math.min(1f, value)) * 100f);
    }

    private float getWindowBrightness() {
        float value = getWindow().getAttributes().screenBrightness;
        if (value >= 0f) return Math.max(0f, Math.min(1f, value));
        try {
            int systemBrightness = Settings.System.getInt(
                    getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            return Math.max(0f, Math.min(1f, systemBrightness / 255f));
        } catch (Exception ignored) {
            return 0.5f;
        }
    }

    private void setWindowBrightness(float value) {
        android.view.WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = Math.max(0.01f, Math.min(1f, value));
        getWindow().setAttributes(attributes);
    }

    @SuppressLint("SetTextI18n")
    private void showPlaybackAdjustment(String text) {
        if (text_volume == null) return;
        text_volume.setVisibility(View.VISIBLE);
        text_volume.setText(text);
        text_volume.removeCallbacks(hideVolume);
        text_volume.postDelayed(hideVolume, 1800);
    }

    private void updateSwipeSeekHint(float x) {
        if (swipe_seek_hint == null) return;
        long target = swipeSeekTarget(x);
        long delta = target - swipeSeekStartPosition;
        swipe_seek_hint.setText(getString(R.string.player_swipe_seek_hint,
                SwipeSeekUtil.formatDelta(delta), StringUtil.toTime((int) (target / 1000))));
        swipe_seek_hint.setVisibility(View.VISIBLE);
    }

    private synchronized void setDashAudioUrls(String primaryUrl, List<String> backupUrls) {
        audioPlaybackUrls.clear();
        audioPlaybackUrlIndex = 0;
        if (primaryUrl != null && !primaryUrl.isEmpty()) audioPlaybackUrls.add(primaryUrl);
        if (backupUrls != null) {
            for (String backupUrl : backupUrls) {
                if (backupUrl != null && !backupUrl.isEmpty() && !audioPlaybackUrls.contains(backupUrl)) {
                    audioPlaybackUrls.add(backupUrl);
                }
            }
        }
        audio_url = primaryUrl == null ? "" : primaryUrl;
        // 1.1.6: 新视频加载时清零音频恢复重试计数、软解强制标志与本地源重试标记
        audioRuntimeRetryCount = 0;
        forceSoftwareDecode = false;
        localSourceRetried = false;
        playbackStallRecoveryCount = 0;
        playbackStallRecoveryInFlight = false;
    }

    private boolean hasDashAudio() {
        // 1.1.6: 兼容档关闭 DASH 双播放器（视频+音频分离），走 durl 单播放器，
        // 避免老设备双解码器/双缓冲的内存与 CPU 负担，以及双播放器同步问题
        return DeviceProfile.useDashDualPlayer() && audio_url != null && !audio_url.isEmpty();
    }

    private boolean retryPlaybackWithBackup(String failedUrl, String reason) {
        final String nextUrl;
        synchronized (this) {
            if (!(isOnlineVideo || isLiveMode) || playbackUrls.isEmpty()) return false;
            String currentUrl = NetWorkUtil.routeUrlForRelay(playbackUrls.get(playbackUrlIndex));
            if (!failedUrl.equals(currentUrl)) return true;
            if (playbackUrlIndex + 1 >= playbackUrls.size()) return false;
            playbackUrlIndex++;
            nextUrl = playbackUrls.get(playbackUrlIndex);
            video_url = nextUrl;
            playbackPrepareAttempt++;
        }

        Logu.e("player", "switch to backup media url " + playbackUrlIndex + ": " + reason);
        recordPlayerAction("backup_source", reason, currentQuality);
        runOnUiThread(() -> {
            if (destroyed) return;
            isPrepared = false;
            isPlaying = false;
            loading_info.setVisibility(View.VISIBLE);
            loading_text0.setText("尝试备用线路");
            loading_text1.setText("");
            anim_loading.start();

            releasePlaybackPlayers();
            ijkPlayer = createMediaPlayer();
            setDisplay();
        });
        return true;
    }

    /** 1.1.6: 用同一 URL 重建播放器重试（硬解 → 软解切换），不更换播放源。 */
    private void retryPlaybackWithSameSource() {
        runOnUiThread(() -> {
            if (destroyed) return;
            if (isPrepared && ijkPlayer != null && !isLiveMode) {
                try {
                    foregroundRestorePosition = Math.max(0L, ijkPlayer.getCurrentPosition());
                } catch (RuntimeException ignored) {
                }
            }
            resumeAfterPrepare = isPlaying;
            danmakuPausedByBuffering = false;
            isPrepared = false;
            isPlaying = false;
            loading_info.setVisibility(View.VISIBLE);
            loading_text0.setText("切换解码方式重试");
            loading_text1.setText("");
            anim_loading.start();

            releasePlaybackPlayers();
            ijkPlayer = createMediaPlayer();
            setDisplay();
        });
    }

    private void showPlaybackLoadFailure() {
        recordPlayerAction("load_failed", null, currentQuality);
        runOnUiThread(() -> {
            if (destroyed) return;
            releasePlaybackPlayers();
            isPrepared = false;
            isPlaying = false;
            if (loadingTimer != null) {
                loadingTimer.cancel();
                loadingTimer = null;
            }
            loading_info.setVisibility(View.VISIBLE);
            loading_text0.setText("视频载入失败");
            loading_text1.setText("请返回后重试");
            anim_loading.stop();
            btn_control.setImageResource(R.drawable.btn_player_play);
        });
    }

    private void showPlaybackRuntimeFailure() {
        recordPlayerAction("playback_interrupted", null, currentQuality);
        runOnUiThread(() -> {
            if (destroyed) return;
            pausePlaybackPlayers();
            isPlaying = false;
            if (loadingTimer != null) {
                loadingTimer.cancel();
                loadingTimer = null;
            }
            loading_info.setVisibility(View.VISIBLE);
            loading_text0.setText("播放中断");
            loading_text1.setText("请返回后重试");
            anim_loading.stop();
            btn_control.setImageResource(R.drawable.btn_player_play);
        });
    }

    /**
     * The unified player is preferred for DASH, but a CDN can still return a
     * stream that the device-side ExoPlayer cannot open. Re-fetch a muxed
     * progressive stream and let the stable compatibility player take over.
     */
    private boolean retryUnifiedPlayerWithProgressiveSource(String reason) {
        if (!(ijkPlayer instanceof UnifiedDashPlayer) || !isOnlineVideo
                || unifiedPlayerFallbackAttempted || aid <= 0 || cid <= 0) return false;
        unifiedPlayerFallbackAttempted = true;
        final long restorePosition = isPrepared && ijkPlayer != null
                ? Math.max(0L, ijkPlayer.getCurrentPosition()) : Math.max(0L, progress_history);
        final boolean shouldResume = isPlaying || resumeAfterPrepare;
        recordPlayerAction("unified_player_fallback", reason, currentQuality);
        runOnUiThread(() -> {
            if (destroyed) return;
            isPrepared = false;
            isPlaying = false;
            loading_info.setVisibility(View.VISIBLE);
            loading_text0.setText("切换兼容播放方式");
            loading_text1.setText("");
            anim_loading.start();
        });
        CenterThreadPool.run(() -> {
            PlayerData fallback = new PlayerData();
            fallback.aid = aid;
            fallback.cid = cid;
            fallback.mid = mid;
            fallback.title = videoTitle == null ? "" : videoTitle;
            int[] fallbackQualities = {DashData.QN_1080P, DashData.QN_720P,
                    DashData.QN_480P, DashData.QN_360P};
            Exception lastError = null;
            for (int quality : fallbackQualities) {
                try {
                    fallback.qn = quality;
                    PlayerApi.getVideo(fallback, false);
                    if (fallback.videoUrl != null && !fallback.videoUrl.isEmpty()) break;
                } catch (Exception error) {
                    lastError = error;
                    fallback.videoUrl = "";
                }
            }
            if (fallback.videoUrl == null || fallback.videoUrl.isEmpty()) {
            recordPlayerAction("unified_fallback_failed",
                        lastError == null ? "empty progressive source" : lastError.getClass().getSimpleName(),
                        currentQuality);
                showPlaybackLoadFailure();
                return;
            }
            final long finalPosition = restorePosition;
            runOnUiThread(() -> {
                if (destroyed) return;
                forceCompatibilityPlayer = true;
                setPlaybackUrls(fallback.videoUrl, fallback.videoBackupUrls);
                setDashAudioUrls("", null);
                currentQuality = fallback.qn;
                progress_history = finalPosition;
                resumeAfterPrepare = shouldResume;
                releasePlaybackPlayers();
                ijkPlayer = createMediaPlayer();
                setDisplay();
                recordPlayerAction("unified_fallback_ready", null, currentQuality);
            });
        });
        return true;
    }

    private void schedulePlaybackPrepareTimeout(String sourceUrl) {
        final int attempt = ++playbackPrepareAttempt;
        final long timeout;
        synchronized (this) {
            // 1.1.6: 超时按设备档位 —— 兼容档 30s / 其余 20s；最后一个备用源再放宽
            long base = DeviceProfile.prepareTimeoutMs();
            timeout = playbackUrlIndex + 1 < playbackUrls.size()
                    ? base : base + PLAYBACK_FINAL_TIMEOUT_MS - PLAYBACK_PRIMARY_TIMEOUT_MS;
        }
        mainHandler.postDelayed(() -> {
            if (destroyed || isPrepared || attempt != playbackPrepareAttempt) return;
            Logu.e("player", "prepare timeout: " + sourceUrl);
            recordPlayerAction("prepare_timeout", null, currentQuality);
            if (retryPlaybackWithBackup(sourceUrl, "prepare timeout")) return;
            showPlaybackLoadFailure();
        }, timeout);
    }

    @SuppressLint("SetTextI18n")
    private void showcon() {
        if (screenLocked) {
            right_control.setVisibility(View.GONE);
            btn_lock.setVisibility(View.VISIBLE);
            return;
        }
        right_control.setVisibility(View.VISIBLE);
        btn_lock.setVisibility(SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.PLAYER_SCREEN_LOCK, true) ? View.VISIBLE : View.GONE);
        positionPlaybackSpeedLabel();
        layout_top.setVisibility(View.VISIBLE);
        if(isAudioOnlyMode){
            right_control.setVisibility(View.GONE);bottom_buttons.setVisibility(View.GONE);
            seekbar_progress.setVisibility(View.GONE);text_progress.setVisibility(View.GONE);text_online.setVisibility(View.GONE);
            updateAudioDashboard();autohideReset();return;
        }
        text_progress.setVisibility(View.VISIBLE);
        bottom_buttons.setVisibility(View.VISIBLE);
        seekbar_progress.setVisibility(View.VISIBLE);
        seekbar_progress.setEnabled(false);
        seekbar_progress.postDelayed(progressbarEnable, 200);
        if (isPrepared && (!isLiveMode) && (!isAudioOnlyMode)) {
            text_speed.setVisibility(View.VISIBLE);
            updateDebugButtonVisibility();
        }
        if(isAudioOnlyMode)text_speed.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryView.setPower(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
        }
        if (screen_round) {
            text_progress.setGravity(Gravity.NO_GRAVITY);
            text_progress.setPadding(ToolsUtil.dp2px(24f), 0, 0, 0);
            if (onlineTimer != null)
                text_online.setVisibility(View.VISIBLE);
        }

        autohideReset();
        refreshSegmentSkipPosition();
        updatePlayerInfoOverlay();
    }

    private final Runnable progressbarEnable = () -> seekbar_progress.setEnabled(true);

    private final Runnable hidecon = () -> {
        if (screenLocked) return;
        if (!swipeSeeking) zoomedSingleFingerSeekMode = false;
        btn_lock.setVisibility(View.GONE);
        right_control.setVisibility(View.GONE);
        layout_top.setVisibility(View.GONE);
        updateAudioDashboard();
        updatePlayerInfoOverlay();
        bottom_buttons.setVisibility(View.GONE);
        seekbar_progress.setVisibility(View.GONE);
        if (isPrepared && (!isAudioOnlyMode)) {
            text_speed.setVisibility(View.GONE);
            btn_debug.setVisibility(View.GONE);
        }
        if(isAudioOnlyMode)text_speed.setVisibility(View.GONE);
        if (screen_round) {
            text_progress.setGravity(Gravity.CENTER);
            text_progress.setPadding(0, 0, 0, ToolsUtil.dp2px(8f));
            if (onlineTimer != null)
                text_online.setVisibility(View.GONE);
        }
        if (menu_opened)
            btn_menu.performClick();
    };

    private void setDisplay() {
        if (destroyed || ijkPlayer == null) return;
        if (surfaceTimer != null) { surfaceTimer.cancel(); surfaceTimer = null; }
        Logu.v("创建播放器");
        Logu.v("url", video_url);

        runOnUiThread(() -> loading_text0.setText("初始化播放"));

        if (ijkPlayer instanceof IjkMediaPlayer) {
            IjkMediaPlayer nativePlayer = (IjkMediaPlayer) ijkPlayer;
            // 1.1.6: KitKat(Android 4.x) 全路径强制软解（durl 也生效）—— 修复老设备
            // 硬解 mp4/dash 首源打开失败(-10000)的问题；硬解运行中失败时
            // forceSoftwareDecode 置位，用同一 URL 走软解重试。
            boolean legacyDashSoftwareCodec = Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT;
            boolean hardwareCodecEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                    && !legacyDashSoftwareCodec
                    && !forceSoftwareDecode
                    && !DeviceProfile.defaultSoftwareCodec()
                    && SharedPreferencesUtil.getBoolean("player_codec",
                    !PlayerCompatibilityUtil.prefersSoftwareCodec());
            hardwareCodecInUse = hardwareCodecEnabled;
            if (isAudioOnlyMode && !hasDashAudio()) {
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1); // 禁用视频
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 48); // 跳过所有视频帧
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
            } else {
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec",
                        hardwareCodecEnabled ? 1 : 0);
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
                if (legacyDashSoftwareCodec) {
                    nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format",
                            IjkMediaPlayer.SDL_FCC_RV16);
                }
            }

            nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles",
                    (SharedPreferencesUtil.getBoolean("player_audio", false) ? 1 : 0));
            if (hardwareCodecEnabled) {
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);
            }
            nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 4);
            nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", hasDashAudio() ? 0 : 1);
            // Keep the original lightweight probe. A forced 1 MB probe delays
            // startup badly on watches and low-bandwidth IoT connections.
            nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 100);
            nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1);
            nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);

            nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "flush_packets");
            nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
            // 1.1.6: 首源连接超时 —— 低端设备 5s 快速失败切备用源，避免长时间黑屏等待
            nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout",
                    DeviceProfile.connectTimeoutSec() * 1000000L);

            // IJK has its own user agent; Bilibili media URLs require the browser user agent here.
            if (isOnlineVideo) {
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size",
                        DeviceProfile.maxBufferBytes());
                if (!isLiveMode) {
                    int cachedDuration = legacyDashSoftwareCodec
                            ? 3000 : DeviceProfile.maxCachedDurationMs();
                    int minFrames = legacyDashSoftwareCodec
                            ? 3 : DeviceProfile.minBufferedFrames();
                    nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                            "max_cached_duration", cachedDuration);
                    nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                            "min-frames", minFrames);
                }
                nativePlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", USER_AGENT_WEB);
                Logu.v("设置ua");
            }
        }

        if (isAudioOnlyMode || !isActivityVisible && backgroundPlaybackActive) {
            IMediaPlayer audioTarget = ijkPlayer;
            String audioSource = video_url;
            videoOutputDetached = true;
            releaseTextureSurface();
            audioTarget.setDisplay(null);
            CenterThreadPool.run(() -> MPPrepare(audioTarget, audioSource));
            return;
        }

        Logu.v("准备设置显示");
        if (SharedPreferencesUtil.getBoolean("player_display", Build.VERSION.SDK_INT < 26)) { // Texture
            Logu.v("使用texture模式");
            surfaceTimer = new Timer();
            surfaceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Logu.v("循环检测");
                    if (mSurfaceTexture != null) {
                        IMediaPlayer player = ijkPlayer;
                        if (destroyed || player == null) {
                            cancel();
                            return;
                        }
                        this.cancel();
                        bindTextureSurface(mSurfaceTexture);
                        MPPrepare(video_url);
                        Logu.v("设置surfaceTexture成功！");
                    }
                }
            }, 0, 200);
        } else {
            Logu.v("使用surface模式");
            SurfaceHolder surfaceHolder = surfaceView.getHolder(); // Surface
            Logu.v("获取surfaceHolder成功！");
            surfaceTimer = new Timer();
            surfaceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Logu.v("循环检测");
                    if (!surfaceHolder.isCreating()) {
                        IMediaPlayer player = ijkPlayer;
                        if (destroyed || player == null) {
                            cancel();
                            return;
                        }
                        this.cancel();
                        Logu.v("定时器结束！");
                        player.setDisplay(surfaceHolder);
                        videoOutputDetached = false;
                        Logu.v("设置surfaceHolder成功！");
                        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                                IMediaPlayer currentPlayer = ijkPlayer;
                                if (!destroyed && currentPlayer != null) {
                                    Logu.v("surface", "重新设置Holder");
                                    currentPlayer.setDisplay(surfaceHolder);
                                    videoOutputDetached = false;
                                    restoreVideoFrameIfNeeded(currentPlayer);
                                }
                            }

                            @Override
                            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                            }

                            @Override
                            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                                Logu.v("surface", "Holder没了");
                                IMediaPlayer currentPlayer = ijkPlayer;
                                videoOutputDetached = true;
                                restoreFrameOnNextSurface = true;
                                if (isPrepared && !destroyed && currentPlayer != null)
                                    currentPlayer.setDisplay(null);
                            }
                        });
                        Logu.v("添加callback成功！");
                        MPPrepare(video_url);
                    }
                }
            }, 0, 200);
        }
    }

    private void bindTextureSurface(@NonNull SurfaceTexture surfaceTexture) {
        IMediaPlayer player = ijkPlayer;
        if (destroyed || player == null) return;
        releaseTextureSurface();
        textureSurface = new Surface(surfaceTexture);
        player.setSurface(textureSurface);
        videoOutputDetached = false;
        restoreVideoFrameIfNeeded(player);
    }

    private void releaseTextureSurface() {
        if (textureSurface != null) {
            textureSurface.release();
            textureSurface = null;
        }
    }

    private void restoreVideoOutputIfAvailable() {
        IMediaPlayer player = ijkPlayer;
        if (destroyed || player == null || isAudioOnlyMode) return;
        if (!videoOutputDetached && !restoreFrameOnNextSurface) return;
        try {
            if (textureView != null && textureView.isAvailable()) {
                SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                if (surfaceTexture != null) {
                    mSurfaceTexture = surfaceTexture;
                    bindTextureSurface(surfaceTexture);
                    return;
                }
            }
            if (surfaceView != null) {
                SurfaceHolder holder = surfaceView.getHolder();
                Surface surface = holder.getSurface();
                if (surface != null && surface.isValid()) {
                    player.setDisplay(holder);
                    videoOutputDetached = false;
                    restoreVideoFrameIfNeeded(player);
                }
            }
        } catch (RuntimeException error) {
            Logu.e("player-surface", "恢复视频画面失败: " + error);
        }
    }

    private void restoreVideoFrameIfNeeded(IMediaPlayer player) {
        if (!restoreFrameOnNextSurface || !isActivityVisible || !isPrepared || isAudioOnlyMode) {
            return;
        }
        restoreFrameOnNextSurface = false;
        try {
            if (!isLiveMode) {
                long currentPosition = Math.max(0L, player.getCurrentPosition());
                long position = ForegroundRestorePolicy.choosePosition(
                        currentPosition, foregroundRestorePosition, video_all);
                boolean shouldSeek = ForegroundRestorePolicy.needsSeek(
                        currentPosition, foregroundRestorePosition);
                if (shouldSeek) {
                    progressGuard.recordSeek(position, video_all, SystemClock.elapsedRealtime());
                    seekPlaybackPlayers(position);
                    if (isDanmakuRenderingEnabled()) {
                        mDanmakuView.seekTo(position);
                    }
                }
                foregroundRestorePosition = -1L;
            }
            if (isPlaying) {
                startPlaybackPlayers();
            } else {
                // IJK does not render a seeked frame on a newly attached surface while paused.
                // Prime one silent frame, then restore the paused state.
                player.setVolume(0f, 0f);
                player.start();
                if (layout_video != null) {
                    layout_video.postDelayed(() -> {
                        IMediaPlayer currentPlayer = ijkPlayer;
                        if (destroyed || currentPlayer == null || currentPlayer != player) return;
                        try {
                            if (!isPlaying) currentPlayer.pause();
                            applyPlaybackGain();
                        } catch (RuntimeException error) {
                            Logu.e("player-surface", "恢复暂停状态失败: " + error);
                        }
                    }, 180L);
                }
            }
            Logu.v("player-surface", "已恢复前台视频画面");
        } catch (RuntimeException error) {
            restoreFrameOnNextSurface = true;
            Logu.e("player-surface", "请求视频帧失败: " + error);
        }
    }

    /**
     * Old TextureView/IJK combinations can keep accepting frames after a
     * background transition while the consumer surface remains black. A
     * fresh player is the reliable recovery path on those devices; playback
     * position and the user's play/pause state are restored after prepare.
     */
    private void recreatePlayerForForeground() {
        if (destroyed || isAudioOnlyMode || isLiveMode || !isPrepared
                || ijkPlayer == null) return;
        long position = 0L;
        try {
            long currentPosition = Math.max(0L, ijkPlayer.getCurrentPosition());
            position = ForegroundRestorePolicy.choosePosition(
                    currentPosition, foregroundRestorePosition, video_all);
        } catch (RuntimeException ignored) {
        }
        foregroundRestorePosition = position;
        resumeAfterPrepare = wasPlayingBeforePause;
        recordPlayerAction("foreground_player_recreate", null, currentQuality);
        isPrepared = false;
        isPlaying = false;
        videoOutputDetached = false;
        restoreFrameOnNextSurface = false;
        loading_info.setVisibility(View.VISIBLE);
        loading_text0.setText("恢复视频画面");
        loading_text1.setText("");
        anim_loading.start();
        releasePlaybackPlayers();
        ijkPlayer = createMediaPlayer();
        setDisplay();
    }

    private String setPlayerDataSource(IMediaPlayer player, String logicalUrl) throws IOException {
        String sourceUrl = NetWorkUtil.routeUrlForRelay(logicalUrl);
        if (isOnlineVideo) {
            Map<String, String> headers = new HashMap<>();
            if (NetWorkUtil.shouldAttachBiliCredentials(sourceUrl)) {
                headers.put("Referer", "https://www.bilibili.com/");
                // Member-only streams and some signed media hosts require the
                // current session cookie during the media request as well.
                headers.put("Cookie", SharedPreferencesUtil.getString(
                        SharedPreferencesUtil.cookies, ""));
            }
            if (sourceUrl.startsWith(NetWorkUtil.BILI_RELAY_BASE + "/")) {
                headers.put("X-Relay-Token", NetWorkUtil.BILI_RELAY_TOKEN);
            }
            if (player instanceof IjkMediaPlayer) {
                ((IjkMediaPlayer) player).setDataSource(sourceUrl, headers);
            } else if (player instanceof UnifiedDashPlayer) {
                ((UnifiedDashPlayer) player).setAudioSource(
                        NetWorkUtil.routeUrlForRelay(audio_url), headers);
                player.setDataSource(this, Uri.parse(sourceUrl), headers);
            } else {
                Uri platformUri = Uri.parse(sourceUrl);
                if ("https".equalsIgnoreCase(platformUri.getScheme())
                        && NetWorkUtil.shouldAttachBiliCredentials(sourceUrl)) {
                    platformUri = platformUri.buildUpon().scheme("http").build();
                }
                player.setDataSource(this, platformUri, headers);
            }
        } else if (sourceUrl.startsWith("content://")) {
            player.setDataSource(this, Uri.parse(sourceUrl), new HashMap<>());
        } else {
            player.setDataSource(sourceUrl);
        }
        return sourceUrl;
    }

    private void prepareDashAudioTrack() {
        releaseDashAudioPlayer();
        audioTrackPrepared = !hasDashAudio();
        audioTrackBuffering = false;
        audioRenderingStarted = false;
        videoRenderingStarted = false;
        dashVideoStarted = false;
        if (!hasDashAudio() || destroyed) return;

        IMediaPlayer audioPlayer = createMediaPlayer();
        dashAudioPlayer = audioPlayer;
        final int attempt = ++audioPrepareAttempt;
        if (audioPlayer instanceof IjkMediaPlayer) {
            IjkMediaPlayer nativeAudio = (IjkMediaPlayer) audioPlayer;
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1);
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
            // Long DASH tracks need a real packet buffer. Disabling it causes repeated
            // audio starvation on longer videos and after seeking or changing speed.
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles",
                    SharedPreferencesUtil.getBoolean("player_audio", false) ? 1 : 0);
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size",
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ? 3 * 1024 * 1024 : 12 * 1024 * 1024);
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration",
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ? 3000 : 12000);
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 5);
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1);
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", USER_AGENT_WEB);
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
            nativeAudio.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
        }
        audioPlayer.setOnPreparedListener(player -> {
            if (destroyed || player != dashAudioPlayer || attempt != audioPrepareAttempt) return;
            audioTrackPrepared = true;
            setPlayerSpeed(player, currentPlaybackSpeed);
            recordPlayerAction("dash_audio_prepared", null, currentQuality);
            if (preparationFinalized && dashAudioRecoveryPending) {
                runOnUiThread(() -> finishDashAudioRecovery(player, attempt));
            } else {
                runOnUiThread(this::finishPreparationIfReady);
            }
        });
        audioPlayer.setOnInfoListener((player, what, extra) -> {
            if (destroyed || player != dashAudioPlayer) return false;
            if (what == IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START
                    || what == IMediaPlayer.MEDIA_INFO_AUDIO_DECODED_START) {
                audioRenderingStarted = true;
                recordPlayerAction("dash_audio_rendering_start", String.valueOf(what), currentQuality);
                runOnUiThread(this::startDashVideoAfterAudio);
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                audioTrackBuffering = true;
                recordPlayerAction("dash_audio_buffering_start", null, currentQuality);
                scheduleDashBufferingPause();
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                audioTrackBuffering = false;
                recordPlayerAction("dash_audio_buffering_end", null, currentQuality);
                resumeAfterDashBuffering();
            }
            return false;
        });
        audioPlayer.setOnSeekCompleteListener(player -> {
            if (destroyed || player != dashAudioPlayer) return;
            if (audioSeekPending && pendingAudioSeekPosition >= 0L) {
                try {
                    if (Math.abs(player.getCurrentPosition() - pendingAudioSeekPosition) > 750L) return;
                } catch (RuntimeException ignored) {
                    return;
                }
            }
            audioSeekPending = false;
            pendingAudioSeekPosition = -1L;
            finishSynchronizedSeekIfReady(synchronizedSeekGeneration);
        });
        audioPlayer.setOnErrorListener((player, what, extra) -> {
            if (player != dashAudioPlayer) return true;
            String reason = "audio error " + what + "/" + extra;
            recordPlayerAction("dash_audio_error", reason, currentQuality);
            // 1.1.6: 运行时（后台播放/听视频）先给短暂网络恢复窗口，同一音频轨延迟重试，
            // 避免网络一抖就切备用源/直接判"载入失败"
            if (preparationFinalized && isPrepared && audioRuntimeRetryCount < AUDIO_RUNTIME_MAX_RETRIES) {
                audioRuntimeRetryCount++;
                recordPlayerAction("dash_audio_recovery_wait", reason, currentQuality);
                mainHandler.postDelayed(() -> {
                    if (destroyed || !isPrepared) return;
                    prepareDashAudioTrack();
                }, AUDIO_RUNTIME_RETRY_DELAY_MS);
                return true;
            }
            if (!retryDashAudioWithBackup(reason)) {
                if (preparationFinalized && isPrepared) showPlaybackRuntimeFailure();
                else showPlaybackLoadFailure();
            }
            return true;
        });
        try {
            setPlayerDataSource(audioPlayer, audio_url);
            audioPlayer.prepareAsync();
            scheduleDashAudioPrepareTimeout(audioPlayer, attempt);
        } catch (IOException | RuntimeException error) {
            if (!retryDashAudioWithBackup(error.getClass().getSimpleName())) showPlaybackLoadFailure();
        }
    }

    private void scheduleDashAudioPrepareTimeout(IMediaPlayer player, int attempt) {
        long timeout = audioPlaybackUrlIndex + 1 < audioPlaybackUrls.size()
                ? PLAYBACK_PRIMARY_TIMEOUT_MS : PLAYBACK_FINAL_TIMEOUT_MS;
        mainHandler.postDelayed(() -> {
            if (destroyed || audioTrackPrepared || player != dashAudioPlayer
                    || attempt != audioPrepareAttempt) return;
            recordPlayerAction("dash_audio_timeout", null, currentQuality);
            if (!retryDashAudioWithBackup("prepare timeout")) {
                if (preparationFinalized && isPrepared) showPlaybackRuntimeFailure();
                else showPlaybackLoadFailure();
            }
        }, timeout);
    }

    private synchronized boolean retryDashAudioWithBackup(String reason) {
        if (audioPlaybackUrlIndex + 1 >= audioPlaybackUrls.size()) return false;
        boolean runtimeRecovery = preparationFinalized && isPrepared;
        audioPlaybackUrlIndex++;
        audio_url = audioPlaybackUrls.get(audioPlaybackUrlIndex);
        dashAudioRecoveryPending = runtimeRecovery;
        recordPlayerAction("dash_audio_backup", reason, currentQuality);
        runOnUiThread(() -> {
            if (runtimeRecovery) {
                dashStartGeneration++;
                dashVideoStarted = false;
                try {
                    if (ijkPlayer != null) ijkPlayer.pause();
                } catch (RuntimeException ignored) {
                }
                loading_info.setVisibility(View.VISIBLE);
                loading_text0.setText("正在恢复音频");
                loading_text1.setText("");
                anim_loading.start();
            }
            prepareDashAudioTrack();
        });
        return true;
    }

    private void finishDashAudioRecovery(IMediaPlayer player, int attempt) {
        if (destroyed || player != dashAudioPlayer || attempt != audioPrepareAttempt) return;
        dashAudioRecoveryPending = false;
        boolean aligning = alignDashAudioToVideo("recovery", 0L);
        if (!aligning && isPlaying) startPlaybackPlayers();
        loading_info.setVisibility(View.GONE);
        anim_loading.stop();
        recordPlayerAction("dash_audio_recovered", null, currentQuality);
    }

    private void releaseDashAudioPlayer() {
        audioPrepareAttempt++;
        IMediaPlayer player = dashAudioPlayer;
        dashAudioPlayer = null;
        if (player == null) return;
        releaseMediaPlayerAsync(player);
    }

    private void releaseMainPlayer() {
        IMediaPlayer player = ijkPlayer;
        ijkPlayer = null;
        if (player == null) return;
        try {
            player.setDisplay(null);
        } catch (RuntimeException ignored) {
        }
        releaseMediaPlayerAsync(player);
    }

    private void releaseMediaPlayerAsync(IMediaPlayer player) {
        CenterThreadPool.run(() -> {
            try {
                player.stop();
            } catch (RuntimeException ignored) {
            }
            try {
                player.release();
            } catch (RuntimeException ignored) {
            }
        });
    }

    private void releasePlaybackPlayers() {
        cancelPlaybackStallWatchdog();
        dashStartGeneration++;
        releaseMainPlayer();
        releaseDashAudioPlayer();
        videoTrackPrepared = false;
        audioTrackPrepared = false;
        preparationFinalized = false;
        audioRenderingStarted = false;
        dashVideoStarted = false;
        dashPlaybackStartedOnce = false;
        videoTrackBuffering = false;
        audioTrackBuffering = false;
        videoBufferingStartedAt = -1L;
        videoSeekPending = false;
        audioSeekPending = false;
        pendingVideoSeekPosition = -1L;
        pendingAudioSeekPosition = -1L;
        queuedSynchronizedSeekPosition = -1L;
        audioOnlySynchronizedSeek = false;
        resumeAfterSynchronizedSeek = false;
        dashAudioRecoveryPending = false;
        cancelDashBufferingPause();
        dashBufferingPauseApplied = false;
        synchronizedSeekGeneration++;
    }

    private void MPPrepare(String nowurl) {
        MPPrepare(ijkPlayer, nowurl);
    }

    private void MPPrepare(IMediaPlayer player, String nowurl) {
        if (destroyed || player == null || player != ijkPlayer) return;
        playbackCompleted = false;
        // Video, API, image and danmaku traffic all follow the same manual relay mode.
        videoTrackPrepared = false;
        audioTrackPrepared = !hasDashAudio();
        preparationFinalized = false;
        audioRenderingStarted = false;
        dashVideoStarted = false;
        prepareDashAudioTrack();
        String sourceUrl = NetWorkUtil.routeUrlForRelay(nowurl);
        player.setOnPreparedListener(this);
        player.setOnSeekCompleteListener(mediaPlayer -> {
            if (destroyed || mediaPlayer != ijkPlayer) return;
            if (videoSeekPending && pendingVideoSeekPosition >= 0L) {
                try {
                    if (Math.abs(mediaPlayer.getCurrentPosition() - pendingVideoSeekPosition) > 750L) return;
                } catch (RuntimeException ignored) {
                    return;
                }
            }
            videoSeekPending = false;
            pendingVideoSeekPosition = -1L;
            finishSynchronizedSeekIfReady(synchronizedSeekGeneration);
        });

        if (isLiveMode) {
            runOnUiThread(() -> loading_text0.setText("载入直播中"));
        } else
            runOnUiThread(() -> loading_text0.setText("载入视频中"));
        try {
            setPlayerDataSource(player, nowurl);
        } catch (IOException e) {
            e.printStackTrace();
            if (retryPlaybackWithBackup(sourceUrl, e.toString())) return;
            showPlaybackLoadFailure();
            return;
        }

        player.setOnCompletionListener(iMediaPlayer -> runOnUiThread(() -> {
            if (destroyed || iMediaPlayer != ijkPlayer || playbackCompleted || playbackSwitchInFlight) return;
            finishWatching = true;
            
            if (interactionData != null && interactionData.edges != null && 
                interactionData.edges.questions != null && !questionShown) {
                checkEndInteractionQuestions();
                    if (questionShown) {
                        playbackCompleted = false;
                        isPlaying = false;
                        pausePlaybackPlayers();
                        stopBackgroundPlaybackService();
                    if (isDanmakuRenderingEnabled()) {
                        mDanmakuView.pause();
                    }
                    btn_control.setImageResource(R.drawable.btn_player_play);
                    return;
                }
            }
            
            boolean collectionEnded = false;
            if (favoritePlaybackToken != null && showCollectionPlayback && collectionPlaybackEnabled) {
                if (favoriteSession == null && collectionQueueLoading) {
                    favoritePendingCompletion = true;
                    return;
                }
                if (!collectionPlaybackActive()) {
                    stopFavoritePlayback();
                    MsgUtil.showMsg(onlineQueueLabel() + "连播不可用，请从列表重新播放或重试加载");
                    return;
                }
            }
            if (collectionPlaybackActive()) {
                if (isOnlineVideo && hasMultiplePages() && currentPageIndex + 1 < cids.size()) {
                    switchPlaybackItem(aid, currentPageIndex + 1, true);
                    return;
                }
                if (favoriteSession != null) {
                    requestFavoriteAdvance(1);
                    return;
                }
                long nextAid = collectionQueue.next(currentQueueKey(), collectionPlaybackOrder, new java.util.Random());
                if (nextAid == currentQueueKey() && (cachedPlaybackSnapshot != null || currentPageIndex == 0 && !hasMultiplePages())) {
                    playbackCompleted = false;
                    resetPlaybackTimelineToStart("collection_repeat");
                    isPlaying = true;
                    startPlaybackPlayers();
                    return;
                }
                if (nextAid > 0) {
                    switchPlaybackItem(nextAid, 0, true);
                    return;
                }
                collectionEnded = true;
                MsgUtil.showMsg(getString(R.string.collection_playback_finished));
            }
            if (!collectionEnded && loop_enabled) {
                playbackCompleted = false;
                resetPlaybackTimelineToStart("loop_replay");
                isPlaying = true;
                startPlaybackPlayers();
            } else if (!collectionEnded && auto_next_enabled && hasMultiplePages() && currentPageIndex < pagenames.size() - 1) {
                playbackCompleted = false;
                switchToPage(currentPageIndex + 1);
            } else {
                playbackCompleted = true;
                isPlaying = false;
                pausePlaybackPlayers();
                stopBackgroundPlaybackService();
                if (isDanmakuRenderingEnabled()) {
                    mDanmakuView.pause();
                }
                btn_control.setImageResource(R.drawable.btn_player_play);
                if (mediaSession != null) {
                    updateMediaSessionPlaybackState();
                }
            }
        }));

        player.setOnErrorListener((iMediaPlayer, what, extra) -> {
            if (destroyed || iMediaPlayer != ijkPlayer) {
                // A released IJK instance can report its final error asynchronously.
                // It must never replace the state of the current player.
                return true;
            }
            String EReport = "播放器可能遇到错误！\n错误码：" + what + "\n附加：" + extra;
            Logu.e("ijk-err", EReport);
            JSONObject details = new JSONObject();
            try {
                details.put("what", what);
                details.put("extra", extra);
                details.put("hardware_codec", hardwareCodecInUse);
                details.put("device_profile", DeviceProfile.get().name());
                details.put("live", isLiveMode);
                details.put("relay", NetWorkUtil.isRelayActive());
            } catch (Exception ignored) {
            }
            DiagnosticLogManager.record("player_error", details);
            if (iMediaPlayer instanceof UnifiedDashPlayer
                    && retryUnifiedPlayerWithProgressiveSource(EReport)) {
                return true;
            }
            // 1.1.6: 硬解失败 → 先同 URL 软解重试（避免 -10000 后只能换源碰运气），
            // 软解也失败再走切源/报失败
            if (hardwareCodecInUse && !forceSoftwareDecode && !isAudioOnlyMode) {
                forceSoftwareDecode = true;
                Logu.e("ijk-err", "硬件解码失败，切换软件解码重试: " + EReport);
                recordPlayerAction("software_codec_retry", EReport, currentQuality);
                retryPlaybackWithSameSource();
                return true;
            }
            // Errors after prepare are playback interruptions, not load failures. Local cache
            // has no backup URL, so rebuild the same source at the current position first.
            if (preparationFinalized && isPrepared) {
                if (!isOnlineVideo && !isLiveMode && localRuntimeRetryCount < 2) {
                    localRuntimeRetryCount++;
                    Logu.e("ijk-err", "本地视频播放异常，同源恢复: " + EReport);
                    recordPlayerAction("local_runtime_retry", EReport, currentQuality);
                    retryPlaybackWithSameSource();
                    return true;
                }
                if (retryPlaybackWithBackup(sourceUrl, EReport)) return true;
                playbackPrepareAttempt++;
                stopBackgroundPlaybackService();
                showPlaybackRuntimeFailure();
                return true;
            }
            if (retryPlaybackWithBackup(sourceUrl, EReport)) return true;
            playbackPrepareAttempt++;
            stopBackgroundPlaybackService();
            showPlaybackLoadFailure();
            return true;
        });

        ijkPlayer.setOnBufferingUpdateListener(
                (mp, percent) -> seekbar_progress.setSecondaryProgress(percent * video_all / 100));

        ijkPlayer.setOnInfoListener((mp, what, extra) -> {
                if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    videoTrackBuffering = true;
                    if (videoBufferingStartedAt < 0L) {
                        videoBufferingStartedAt = SystemClock.elapsedRealtime();
                    }
                    recordPlayerAction("buffering_start", null, currentQuality);
                    scheduleDashBufferingPause();
                    scheduleBufferingUi();
                } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    videoTrackBuffering = false;
                    long bufferingDuration = videoBufferingStartedAt < 0L ? -1L
                            : SystemClock.elapsedRealtime() - videoBufferingStartedAt;
                    videoBufferingStartedAt = -1L;
                    recordPlayerAction("buffering_end", null, currentQuality, bufferingDuration);
                    resumeAfterDashBuffering();
                    cancelBufferingUi();
                } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    videoRenderingStarted = true;
                    recordPlayerAction("video_rendering_start", null, currentQuality);
                }

                return false;
            });

        player.setScreenOnWhilePlaying(true);
        if (destroyed || player != ijkPlayer) return;
        try {
            player.prepareAsync();
        } catch (RuntimeException error) {
            if (!destroyed && player == ijkPlayer) throw error;
            Logu.e("player-prepare", "播放器已释放，取消本次准备");
        }
        schedulePlaybackPrepareTimeout(sourceUrl);
        Logu.v("开始准备");
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onPrepared(IMediaPlayer mediaPlayer) {
        if (mediaPlayer != ijkPlayer) return;
        playbackPrepareAttempt++;
        videoTrackPrepared = true;
        finishPreparationIfReady();
    }

    private void finishPreparationIfReady() {
        if (preparationFinalized || !videoTrackPrepared || (hasDashAudio() && !audioTrackPrepared)) return;
        preparationFinalized = true;
        if (destroyed) {
            releasePlaybackPlayers();
            return;
        }

        isPrepared = true;
        applyPlaybackGain();
        playbackStallRecoveryInFlight = false;
        recordPlayerAction("prepared", null, currentQuality);
        video_all = (int) ijkPlayer.getDuration();
        progressGuard.reset();
        danmakuClock.reset(Math.max(0L, ijkPlayer.getCurrentPosition()),
                SystemClock.elapsedRealtime(), false);
        lastDanmakuClockSyncAt = -1L;

        // The protobuf endpoint is segmented. Start it only after the player has
        // reported its real duration; before preparation old devices commonly
        // report 0 and long videos then load only the first hour of segments.
        if (!isLiveMode && hasDanmaku) downdanmu();

        changeVideoSize();

        if ((isLiveMode || (hasDanmaku && isDanmakuVisible)) && mDanmakuView != null) {
            // Let the media decoder establish a stable clock before the
            // danmaku renderer starts competing for CPU on low-end watches.
            final IDanmakuView preparedDanmakuView = mDanmakuView;
            layout_control.postDelayed(() -> {
                if (!destroyed && preparedDanmakuView == mDanmakuView
                        && preparedDanmakuView.isPrepared()
                        && (isLiveMode || isDanmakuVisible) && isPrepared && (isPlaying || isLiveMode)) {
                    preparedDanmakuView.start(Math.max(0L, ijkPlayer.getCurrentPosition()));
                    syncDanmakuClockToPlayer(isPlaying);
                }
            }, 1200L);
        }
        btn_danmaku.setOnClickListener(view -> toggleDanmakuByUser());
        updateDanmakuButton();
        // 原作者居然把旋转按钮命名为danmaku_btn，也是没谁了...我改过来了 ----RobinNotBad
        // 他大抵是觉得能用就行

        if (!isLiveMode) {
            if (loop_enabled)
                btn_loop.setImageResource(R.mipmap.loopon);
            else
                btn_loop.setImageResource(R.mipmap.loopoff);
            btn_loop.setOnClickListener(view -> {
                btn_loop.setImageResource((loop_enabled ? R.mipmap.loopoff : R.mipmap.loopon));
                loop_enabled = !loop_enabled;
            });
            btn_loop.setVisibility(View.VISIBLE);

            // 听视频模式按钮
            // 如果是本地音频文件，隐藏听视频开关（因为已经是纯音频了）
            if (isLocalAudioFile) {
                btn_audio_only.setVisibility(View.GONE);
            } else {
                updateAudioOnlyButton();
                btn_audio_only.setOnClickListener(view -> toggleAudioOnlyMode());
                btn_audio_only.setVisibility(View.VISIBLE);
            }

            if (hasMultiplePages()) {
                btn_page_selector.setVisibility(View.VISIBLE);
                btn_page_selector.setOnClickListener(view -> showPageSelectorCard());
                btn_auto_next.setVisibility(View.VISIBLE);
                updateAutoNextButton();
                btn_auto_next.setOnClickListener(view -> toggleAutoNext());
            } else {
                btn_page_selector.setVisibility(View.GONE);
                btn_auto_next.setVisibility(View.GONE);
            }

            if (SharedPreferencesUtil.getBoolean("player_ui_showQualityBtn", true) && isOnlineVideo) {
                btn_quality.setVisibility(View.VISIBLE);
                btn_quality.setOnClickListener(view -> showQualitySelectorCard());
            } else {
                btn_quality.setVisibility(View.GONE);
            }

            if (!SharedPreferencesUtil.getBoolean("player_ui_showPageBtn", true))
                btn_page_selector.setVisibility(View.GONE);
        } else {
            // 直播模式保留弹幕发送和听直播，隐藏无关按钮
            btn_loop.setVisibility(View.GONE);
            btn_subtitle.setVisibility(View.GONE);
            btn_danmaku_send.setVisibility(View.VISIBLE);
            updateAudioOnlyButton();
            btn_audio_only.setOnClickListener(view -> toggleAudioOnlyMode());
            btn_audio_only.setVisibility(View.VISIBLE);
            btn_page_selector.setVisibility(View.GONE);
            btn_auto_next.setVisibility(View.GONE);
            btn_quality.setVisibility(View.GONE);
        }

        updateCollectionPlaybackControls();

        seekbar_progress.setMax(video_all);
        if (highEnergyEnabled) {
            highEnergyDataBuilder.reset(video_all);
            highEnergySegments.clear();
            seekbar_progress.clearHighEnergyData();
        }
        progress_str = StringUtil.toTime(video_all / 1000);

        if (isAudioOnlyMode) {
            updateAudioOnlyUI();
        }

        if (!isOnlineVideo && video_all > 0 && progress_history >= video_all - 250L)
            progress_history = 0; // Keep the completion checkpoint on disk, but replay completed cache from zero.
        if (SharedPreferencesUtil.getBoolean("player_from_last", true) && !isLiveMode) {
            if (progress_history > 5) {
                progressGuard.recordSeek(progress_history, video_all, SystemClock.elapsedRealtime());
                seekPlaybackPlayers(progress_history);
                if (isDanmakuRenderingEnabled()) {
                    mDanmakuView.seekTo(progress_history);
                }
                Logu.d("进度跳转", String.valueOf(progress_history));
                runOnUiThread(() -> MsgUtil.showMsg("已从上次的位置播放"));
            }
        }

        if (foregroundRestorePosition >= 0L && !isLiveMode) {
            long currentPosition = Math.max(0L, ijkPlayer.getCurrentPosition());
            if (ForegroundRestorePolicy.needsSeek(currentPosition, foregroundRestorePosition)) {
                long restorePosition = ForegroundRestorePolicy.choosePosition(
                        currentPosition, foregroundRestorePosition, video_all);
                progressGuard.recordSeek(restorePosition, video_all, SystemClock.elapsedRealtime());
                seekPlaybackPlayers(restorePosition);
                if (isDanmakuRenderingEnabled()) {
                    mDanmakuView.seekTo(restorePosition);
                }
                recordPlayerAction("foreground_position_restored", null, currentQuality);
            } else {
                recordPlayerAction("foreground_position_kept", null, currentQuality);
            }
            foregroundRestorePosition = -1L;
        }

        loading_info.setVisibility(View.GONE);
        anim_loading.stop();
        isPlaying = resumeAfterPrepare;
        danmakuClock.setPlaying(isPlaying && !videoSeekPending && !audioSeekPending,
                SystemClock.elapsedRealtime());
        btn_control.setImageResource(isPlaying
                ? R.drawable.btn_player_pause : R.drawable.btn_player_play);

        text_speed.setVisibility(layout_top.getVisibility());
        if (isLiveMode)
            text_speed.setVisibility(View.GONE);
        text_speed.setOnClickListener(view -> layout_speed.setVisibility(View.VISIBLE));
        layout_speed.setOnClickListener(view -> layout_speed.setVisibility(View.GONE));

        btn_debug.setOnClickListener(view -> showInteractionDebugDialog());
        updateDebugButtonVisibility();

        progressChange();
        onlineChange();

        setPlaybackSpeed(speed_values[seekbar_speed.getProgress()]);
        if (isPlaying) {
            startPlaybackPlayers();
            // Listening mode intentionally has no video track, so there can be no
            // video-rendering callback or first frame to wait for.
            if (!isAudioOnlyMode) {
                scheduleFirstFrameTimeout(ijkPlayer, playbackUrlIndex);
            }
            if (videoTrackBuffering) {
                scheduleBufferingUi();
            }
        }
        else pausePlaybackPlayers();
        schedulePlaybackStallWatchdog();
        if (!isActivityVisible && backgroundPlaybackActive) {
            startBackgroundPlaybackService();
        }

        if (mediaSession != null) {
            updateMediaSessionMetadata();
            updateMediaSessionPlaybackState();
        }

        btn_control.setOnClickListener(view -> controlVideo());
        btn_subtitle.setOnClickListener(view -> CenterThreadPool.run(() -> downSubtitle(true)));
    }

    private void showLoadingSpeed() {
        if (!(ijkPlayer instanceof IjkMediaPlayer)) return;
        if (loadingTimer != null) loadingTimer.cancel();
        loadingTimer = new Timer();
        loadingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                IjkMediaPlayer player = (IjkMediaPlayer) ijkPlayer;
                if (destroyed || player == null) {
                    cancel();
                    return;
                }
                String text = String.format(Locale.CHINA, "%.1f", player.getTcpSpeed() / 1024f) + "KB/s";
                runOnUiThread(() -> {
                    if (!destroyed && loading_text1 != null) loading_text1.setText(text);
                });
            }
        }, 0, 500);
    }

    private void scheduleBufferingUi() {
        if (mainHandler == null) return;
        cancelBufferingUi();
        bufferingUiRunnable = () -> {
            bufferingUiRunnable = null;
            if (destroyed || !videoTrackBuffering || !isPrepared || !isPlaying) return;
            bufferingUiShown = true;
            loading_info.setVisibility(View.VISIBLE);
            anim_loading.start();
            loading_text0.setText("正在缓冲");
            showLoadingSpeed();
        };
        mainHandler.postDelayed(bufferingUiRunnable, 500L);
        scheduleUnifiedBufferingFallback();
    }

    private void scheduleUnifiedBufferingFallback() {
        cancelUnifiedBufferingFallback();
        if (mainHandler == null || !(ijkPlayer instanceof UnifiedDashPlayer)
                || !isPrepared || !isPlaying) return;
        final IMediaPlayer player = ijkPlayer;
        unifiedBufferingFallbackRunnable = () -> {
            unifiedBufferingFallbackRunnable = null;
            if (destroyed || player != ijkPlayer || !isPrepared || !isPlaying
                    || !videoTrackBuffering) return;
            recordPlayerAction("unified_buffering_timeout", null, currentQuality);
            retryUnifiedPlayerWithProgressiveSource("buffering timeout");
        };
        mainHandler.postDelayed(unifiedBufferingFallbackRunnable,
                UNIFIED_BUFFERING_FALLBACK_DELAY_MS);
    }

    private void cancelUnifiedBufferingFallback() {
        if (mainHandler != null && unifiedBufferingFallbackRunnable != null) {
            mainHandler.removeCallbacks(unifiedBufferingFallbackRunnable);
        }
        unifiedBufferingFallbackRunnable = null;
    }

    private void cancelBufferingUi() {
        cancelUnifiedBufferingFallback();
        if (mainHandler != null && bufferingUiRunnable != null) {
            mainHandler.removeCallbacks(bufferingUiRunnable);
        }
        bufferingUiRunnable = null;
        if (!bufferingUiShown) return;
        bufferingUiShown = false;
        if (loadingTimer != null) {
            loadingTimer.cancel();
            loadingTimer = null;
        }
        if (loading_info != null) loading_info.setVisibility(View.GONE);
        if (anim_loading != null) anim_loading.stop();
    }

    private void schedulePlaybackStallWatchdog() {
        cancelPlaybackStallWatchdog();
        if (mainHandler == null) return;
        playbackStallLastSampleAt = SystemClock.elapsedRealtime();
        playbackStallWatchdog = new Runnable() {
            @Override
            public void run() {
                if (destroyed) return;
                IMediaPlayer player = ijkPlayer;
                long now = SystemClock.elapsedRealtime();
                if (!isPrepared || !isPlaying || isSeeking || videoSeekPending || audioSeekPending
                        || isAudioOnlyMode || player == null) {
                    playbackStallLastPosition = -1L;
                    playbackStallLastSampleAt = now;
                } else {
                    try {
                        long position = Math.max(0L, player.getCurrentPosition());
                        if (playbackStallLastPosition < 0L || position != playbackStallLastPosition) {
                            playbackStallLastPosition = position;
                            playbackStallLastSampleAt = now;
                            playbackStallRecoveryCount = 0;
                            playbackStallRecoveryInFlight = false;
                        } else if (!videoTrackBuffering && !audioTrackBuffering
                                && !playbackStallRecoveryInFlight
                                && now - playbackStallLastSampleAt >= PLAYBACK_STALL_TIMEOUT_MS) {
                            recoverFromPlaybackStall(player, position);
                            playbackStallLastSampleAt = now;
                        }
                    } catch (RuntimeException ignored) {
                        playbackStallLastSampleAt = now;
                    }
                }
                mainHandler.postDelayed(this, PLAYBACK_STALL_CHECK_INTERVAL_MS);
            }
        };
        mainHandler.postDelayed(playbackStallWatchdog, PLAYBACK_STALL_CHECK_INTERVAL_MS);
    }

    private void cancelPlaybackStallWatchdog() {
        if (mainHandler != null && playbackStallWatchdog != null) {
            mainHandler.removeCallbacks(playbackStallWatchdog);
        }
        playbackStallWatchdog = null;
        playbackStallLastPosition = -1L;
        playbackStallLastSampleAt = -1L;
        playbackStallRecoveryInFlight = false;
    }

    private void recoverFromPlaybackStall(IMediaPlayer player, long position) {
        if (playbackStallRecoveryCount >= 2) {
            recordPlayerAction("playback_stall_limit", String.valueOf(position), currentQuality);
            return;
        }
        playbackStallRecoveryCount++;
        playbackStallRecoveryInFlight = true;
        recordPlayerAction("playback_stall_detected", String.valueOf(position), currentQuality);
        if (player instanceof UnifiedDashPlayer
                && retryUnifiedPlayerWithProgressiveSource("playback stalled")) {
            return;
        }
        retryPlaybackWithSameSource();
    }

    private void scheduleFirstFrameTimeout(IMediaPlayer player, int sourceIndex) {
        mainHandler.postDelayed(() -> {
            if (destroyed || player != ijkPlayer || sourceIndex != playbackUrlIndex
                    || !isPrepared || !isPlaying || isAudioOnlyMode || videoRenderingStarted
                    || !isActivityVisible && backgroundPlaybackActive) return;
            // Listening mode intentionally disables video rendering. It must never be
            // treated as a missing first video frame after a background transition.
            recordPlayerAction("first_frame_timeout", null, currentQuality);
            String failedUrl = playbackUrls.isEmpty()
                    ? video_url : NetWorkUtil.routeUrlForRelay(playbackUrls.get(sourceIndex));
            if (!retryPlaybackWithBackup(failedUrl, "first frame timeout")) {
                showPlaybackLoadFailure();
            }
        }, 15_000L);
    }

    private void changeVideoSize() {
        if (!isPrepared || ijkPlayer == null)
            return;
        int width = ijkPlayer.getVideoWidth();
        int height = ijkPlayer.getVideoHeight();
        Logu.v("screen", screen_width + "x" + screen_height);
        Logu.v("video", width + "x" + height);

        // 在听视频模式下，视频宽高可能为0，跳过尺寸调整
        if (width == 0 || height == 0) {
            Logu.v("视频尺寸", "视频宽高为0，跳过尺寸调整（可能处于听视频模式）");
            return;
        }

        if (SharedPreferencesUtil.getBoolean("player_ui_round", false)) {
            float video_mul = (float) height / (float) width;
            double sqrt = Math.sqrt(screen_width * screen_width / ((double) (height * height) / (width * width) + 1));
            video_height = (int) (sqrt * video_mul + 0.5);
            video_width = (int) (sqrt + 0.5);
        } else {
            int width_case1 = width * screen_height / height;
            int height_case2 = height * screen_width / width;

            if (width_case1 <= screen_width) {
                video_width = width_case1;
                video_height = screen_height;
            } else {
                video_width = screen_width;
                video_height = height_case2;
            }
        }

        runOnUiThread(() -> {
            final int layoutRequest=++videoLayoutGeneration;
            final int panBeforeLayout=videoPanGeneration;
            final float offsetX=layout_video.getX()-video_origX,offsetY=layout_video.getY()-video_origY;
            layout_video.setLayoutParams(new RelativeLayout.LayoutParams(video_width, video_height));
            Logu.v("改变视频区域大小", video_width + "x" + video_height);
            video_origX = (screen_width - video_width) / 2f;
            video_origY = (screen_height - video_height) / 2f;

            layout_video.postDelayed(() -> {
                if(destroyed||isFinishing()||layoutRequest!=videoLayoutGeneration)return;
                if(isVideoZoomed()){
                    if(panBeforeLayout==videoPanGeneration){layout_video.setX(video_origX+offsetX);layout_video.setY(video_origY+offsetY);}
                    boolean touchHadMoved=gesture_moved;
                    videoMoveBy(0,0);
                    gesture_moved=touchHadMoved;
                }else if(panBeforeLayout==videoPanGeneration){
                    layout_video.setX(video_origX);layout_video.setY(video_origY);
                }
                Logu.v("改变视频位置", ((screen_width - video_width) / 2) + "," + ((screen_height - video_height) / 2));
            }, 60); // 别问为什么，问就是必须这么写，要等上面的绘制完成
        });
    }

    private void progressChange() {
        if (progressTimer != null) progressTimer.cancel();
        progressTimer = new Timer();
        TimerTask task = new TimerTask() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                IMediaPlayer player = ijkPlayer;
                if (destroyed || player == null) {
                    cancel();
                    return;
                }
                if (isPrepared && isPlaying && !isSeeking
                        && !videoSeekPending && !audioSeekPending) {
                    long currentPosition;
                    try {
                        currentPosition = player.getCurrentPosition();
                    } catch (RuntimeException error) {
                        if (destroyed || ijkPlayer == null) cancel();
                        return;
                    }
                    long now = SystemClock.elapsedRealtime();
                    if (onLongClick && now - lastLongPressSampleAt >= 5000L)
                        recordLongPressPerformance("sample", currentPosition, now);
                    long acceptedPosition = progressGuard.accept(currentPosition, video_all, now);
                    if (acceptedPosition < 0L) {
                        Logu.e("player-progress", "忽略异常播放进度：" + currentPosition);
                        if (!progressAnomalyReported) {
                            progressAnomalyReported = true;
                            JSONObject details = new JSONObject();
                            try {
                                details.put("position", currentPosition);
                                details.put("duration", video_all);
                                details.put("hardware_codec", hardwareCodecInUse);
                            } catch (Exception ignored) {
                            }
                            DiagnosticLogManager.record("player_progress_anomaly", details);
                        }
                        return;
                    }
                    reconcileDanmakuClock(currentPosition, now);
                    DanmakuWindowLoader windowLoader = danmakuWindowLoader;
                    if (windowLoader != null) windowLoader.update(acceptedPosition, video_all);
                    correctDashAudioDrift(acceptedPosition);
                    video_now = (int) acceptedPosition;
                    final long tickCid = cid;
                    runOnUiThread(() -> {
                        if (destroyed || tickCid != cid) return;
                        updateSegmentSkip(acceptedPosition);
                        updateAudioDashboard();
                        if (lastPgcSave < 0 || now - lastPgcSave >= 5000) {
                            savePgcProgress(acceptedPosition); lastPgcSave = now;
                        }
                    });
                    if (video_now_last != video_now) { // 检测进度是否在变动
                        video_now_last = video_now;
                        saveInteractionProgress(false);
                        float curr_sec = video_now / 1000f;
                        runOnUiThread(() -> {
                            if (isLiveMode) {
                                text_progress.setText(StringUtil.toTime((int) curr_sec));
                                text_online.setText(online_number);
                            } else {
                                seekbar_progress.setProgress(video_now);
                                // progressBar上有一个onProgressChange的监听器，文字更改在那里
                            }
                        });
                        if (subtitles != null)
                            showSubtitle(curr_sec + subtitle_delta);
                        else
                            runOnUiThread(() -> text_subtitle.setVisibility(View.GONE));
                        
                        if (viewPointAdapter != null && viewPoints != null && !viewPoints.isEmpty()) {
                            viewPointAdapter.updateCurrentPosition((int) curr_sec);
                        }

                        if (mediaSession != null) {
                            updateMediaSessionPlaybackState();
                        }
                    }
                }
            }
        };
        progressTimer.schedule(task, 0, 250);
    }

    private void onlineChange() {
        if (onlineTimer != null) {
            onlineTimer.cancel();
            onlineTimer = null;
        }
        if (!SharedPreferencesUtil.getBoolean("player_show_online", false) || !isOnlineVideo || isLiveMode || aid == 0 || cid == 0)
            return;

        onlineTimer = new Timer();
        TimerTask task = new TimerTask() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                if (ijkPlayer != null) {
                    try {
                        online_number = VideoInfoApi.getWatching(aid, cid);
                        runOnUiThread(() -> {
                            if (!online_number.isEmpty())
                                text_online.setText(online_number + "人在看");
                            else
                                text_online.setText("");
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            MsgUtil.err(e);
                            text_online.setVisibility(View.GONE);
                        });
                        this.cancel();
                    }
                }
            }
        };
        onlineTimer.schedule(task, 0, 5000);
    }

    private void getSubtitle(String subtitle_url) {
        if (subtitle_url == null || subtitle_url.isEmpty())
            return;
        try {
            if (isOnlineVideo)
                subtitles = PlayerApi.getSubtitle(subtitle_url);
            else
                subtitles = PlayerApi.getSubtitle(this, subtitle_url);

            if (subtitles == null)
                return;

            subtitle_count = subtitles.length;
            subtitle_curr_index = 0;
            runOnUiThread(() -> btn_subtitle.setImageResource(R.mipmap.subtitle_on));
        } catch (Exception e) {
            MsgUtil.err(e);
        }
    }

    private void showSubtitle(float curr_sec) {
        if (subtitles == null || subtitle_count == 0) {
            runOnUiThread(() -> text_subtitle.setVisibility(View.GONE));
            return;
        }
        
        Subtitle subtitle_curr = subtitles[subtitle_curr_index];

        boolean need_adjust = true;
        boolean need_show = true;

        while (need_adjust) {
            if (curr_sec < subtitle_curr.from) { // 进度在当前字幕的起始位置之前
                // 如果不是第一条字幕，且进度在上一条字幕的结束位置之前，那么字幕前移一位
                // 否则字幕不显示且退出校准（当前进度在两条字幕之间）
                if (subtitle_curr_index != 0 && curr_sec < subtitles[subtitle_curr_index - 1].to) {
                    subtitle_curr_index--;
                } else {
                    need_adjust = false;
                    need_show = false;
                }
            } else if (curr_sec > subtitle_curr.to) { // 在当前字幕的结束位置之后
                // 如果不是最后一条字幕，且进度在下一条字幕的开始位置之后，那么字幕后移一位
                // 否则字幕不显示且退出校准（当前进度在两条字幕之间）
                if (subtitle_curr_index + 1 < subtitle_count && curr_sec > subtitles[subtitle_curr_index + 1].from) {
                    subtitle_curr_index++;
                } else {
                    need_adjust = false;
                    need_show = false;
                }
            } else
                need_adjust = false; // 在当前字幕的时间段内，则退出校准
        }

        if (need_show)
            runOnUiThread(() -> {
                text_subtitle.setText(subtitles[subtitle_curr_index].content);
                text_subtitle.setVisibility(View.VISIBLE);
            });
        else
            runOnUiThread(() -> text_subtitle.setVisibility(View.GONE));
    }

    private int subtitle_selected = -1;

    private void downSubtitle(boolean from_btn) {
        try {
            if (subtitleLinks == null) { // 首次运行，获取字幕
                if (isOnlineVideo) {
                    subtitleLinks = PlayerApi.getSubtitleLinks(aid, cid);
                } else if (localSubtitleRoot != null && !localSubtitleRoot.isEmpty()) {
                    subtitleLinks = PlayerApi.getSubtitleLinks(this, localSubtitleRoot);
                } else if (danmakuFile != null && danmakuFile.getParentFile() != null) {
                    subtitleLinks = PlayerApi.getSubtitleLinks(new File(danmakuFile.getParentFile(), "subtitles"));
                } else {
                    subtitleLinks = new SubtitleLink[]{
                            new SubtitleLink(-1, "不显示字幕", "null", false)};
                }
            }

            if (subtitleLinks.length == 1) {
                if (from_btn)
                    MsgUtil.showMsg("本视频无字幕");
                return;
            }

            subtitle_delta = SharedPreferencesUtil.getFloat("player_subtitle_delta", 0.3f);

            boolean ai_not_only = (subtitleLinks.length > 2 || (subtitleLinks.length == 2 && !subtitleLinks[0].isAI));
            boolean ai_allowed = (from_btn || SharedPreferencesUtil.getBoolean("player_subtitle_ai_allowed", false));

            if (ai_not_only || ai_allowed) {
                if (subtitle_selected == -1)
                    subtitle_selected = subtitleLinks.length;

                runOnUiThread(() -> {
                    RecyclerView subtitleRecycler = findViewById(R.id.subtitle_list);
                    SubtitleAdapter adapter = new SubtitleAdapter();
                    adapter.setData(subtitleLinks);
                    adapter.setSelectedItemIndex(subtitle_selected);
                    adapter.setOnItemClickListener(index -> {
                        layout_card_bg.setVisibility(View.GONE);
                        card_subtitle.setVisibility(View.GONE);
                        subtitle_selected = index;

                        if (subtitleLinks[index].id == -1) {
                            subtitles = null;
                            btn_subtitle.setImageResource(R.mipmap.subtitle_off);
                        } else
                            CenterThreadPool.run(() -> getSubtitle(subtitleLinks[index].url));
                    });
                    subtitleRecycler
                            .setLayoutManager(new CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false));
                    subtitleRecycler.setHasFixedSize(true);
                    subtitleRecycler.setAdapter(adapter);
                    layout_card_bg.setVisibility(View.VISIBLE);
                    card_subtitle.setVisibility(View.VISIBLE);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            MsgUtil.err(e);
        }
    }

    private void downdanmu() {
        runOnUiThread(() -> {
            if (destroyed || isLiveMode || !hasDanmaku || !isPrepared || ijkPlayer == null
                    || isAudioOnlyMode || danmakuLoadPolicy == null) return;
            if (danmakuLoadPolicy.needsCount()) {
                checkDanmakuCount();
                return;
            }
            isDanmakuVisible = danmakuLoadPolicy.shouldLoad();
            updateDanmakuButton();
            if (!isDanmakuVisible) {
                if (danmakuLoadPolicy.isSuppressed() && !danmakuProtectionHintShown) {
                    danmakuProtectionHintShown = true;
                    MsgUtil.showMsg(knownDanmakuCount > DanmakuLoadPolicy.AUTO_LOAD_LIMIT
                            ? "弹幕超过5000条，已停止自动加载；可手动打开"
                            : "暂未获取弹幕数量，已暂停自动加载；可手动打开");
                }
                return;
            }
            if (danmakuLoadStarted) return;
            boolean useNewApi = isOnlineVideo
                    && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.NEW_DANMAKU_API, true);
            if (useNewApi) {
                long duration = ijkPlayer.getDuration();
                if (duration <= 0) return;
                danmakuLoadStarted = true;
                final int generation = danmakuLoadGeneration;
                final long requestAid = aid;
                final long requestCid = cid;
                danmakuWindowLoader = new DanmakuWindowLoader(CenterThreadPool::run,
                        (index, cancellation) -> {
                            try {
                                return DanmakuApi.getVideoDanmakuSegment(
                                        requestAid, requestCid, index, cancellation);
                            } catch (Exception error) {
                                if (!cancellation.isCancelled())
                                    Logu.e("danmaku-window", "segment=" + index + ": " + error);
                                throw error;
                            }
                        },
                        (revision, segments) -> mainHandler.post(() -> {
                            DanmakuWindowLoader loader = danmakuWindowLoader;
                            if (destroyed || !isDanmakuVisible || generation != danmakuLoadGeneration
                                    || loader == null || !loader.isCurrent(revision)) return;
                            pendingDanmakuWindow = segments;
                            applyDanmakuWindow();
                        }), SystemClock::elapsedRealtime,
                        DeviceProfile.danmakuWindowRadius());
                streamDanmaku(null, new ArrayList<>());
                // History restoration happens later in preparation. Request its destination first.
                danmakuWindowLoader.update(Math.max(progress_history,
                        Math.max(0L, ijkPlayer.getCurrentPosition())), duration);
            } else {
                danmakuLoadStarted = true;
                downdanmuOld();
            }
        });
    }

    private void downdanmuOld() {
        final int generation = danmakuLoadGeneration;
        final String url = danmaku_url;
        final File file = new File(getCacheDir(), "danmaku-" + cid + "-" + generation + ".xml");
        final boolean online = isOnlineVideo;
        final RequestCancellation cancellation = new RequestCancellation();
        danmakuXmlRequest = cancellation;
        CenterThreadPool.run(() -> {
            try {
                File loaded;
                if (online) {
                    try (Response response = NetWorkUtil.getCancellable(url, NetWorkUtil.webHeaders, cancellation)) {
                        if (!response.isSuccessful() || response.body() == null)
                            throw new IOException("Danmaku HTTP " + response.code());
                        byte[] bytes = response.body().bytes();
                        if (cancellation.isCancelled()) return;
                        try (BufferedSink sink = Okio.buffer(Okio.sink(file))) { sink.write(decompress(bytes)); }
                    }
                    loaded = file;
                } else loaded = localDanmakuFile(url, generation);
                runOnUiThread(() -> {
                    if (destroyed || cancellation.isCancelled() || !isDanmakuVisible
                            || generation != danmakuLoadGeneration) {
                        if (online) file.delete();
                        return;
                    }
                    danmakuXmlRequest = null;
                    danmakuFile = loaded;
                    streamDanmaku(loaded.toString(), null);
                });
            } catch (Exception error) {
                if (online) file.delete();
                runOnUiThread(() -> {
                    if (destroyed || cancellation.isCancelled() || generation != danmakuLoadGeneration) return;
                    danmakuXmlRequest = null;
                    danmakuLoadStarted = false;
                    Logu.e("danmaku-load", error.getClass().getSimpleName());
                    MsgUtil.showMsg("弹幕加载失败，可关闭后重试");
                });
            }
        });
    }

    private File localDanmakuFile(String url, int generation) throws IOException {
        if (url == null || url.isEmpty()) throw new IOException("No local danmaku");
        if (url.startsWith("content://")) return VideoStorageUtil.copyContentToCache(
                this, url, "local-danmaku-" + cid + "-" + generation + ".xml");
        File file = new File(url);
        if (!file.isFile()) throw new IOException("No local danmaku file");
        return file;
    }

    private void resetDanmakuSource(long count) {
        stopDanmakuLoading();
        knownDanmakuCount = count;
        danmakuProtectionHintShown = false;
        hasDanmaku = danmaku_url != null && !danmaku_url.isEmpty();
        danmakuLoadPolicy = new DanmakuLoadPolicy(SharedPreferencesUtil.getBoolean("pref_switch_danmaku", true),
                !isLiveMode && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PLAYER_LARGE_DANMAKU_GUARD, false), count);
        isDanmakuVisible = danmakuLoadPolicy.shouldLoad();
        updateDanmakuButton();
    }

    private void checkDanmakuCount() {
        if (danmakuCountRequest != null) return;
        final int generation = danmakuLoadGeneration;
        final long requestAid = aid;
        final boolean online = isOnlineVideo;
        final String localUrl = danmaku_url;
        final RequestCancellation cancellation = new RequestCancellation();
        danmakuCountRequest = cancellation;
        CenterThreadPool.run(() -> {
            long count = -1;
            try {
                count = online ? DanmakuApi.getVideoDanmakuCount(requestAid, cancellation)
                        : DanmakuXmlCounter.count(localDanmakuFile(localUrl, generation),
                                DanmakuLoadPolicy.AUTO_LOAD_LIMIT + 1, cancellation);
            } catch (Exception error) {
                if (!cancellation.isCancelled()) Logu.e("danmaku-count", error.getClass().getSimpleName());
            }
            final long result = count;
            runOnUiThread(() -> {
                if (destroyed || cancellation.isCancelled() || generation != danmakuLoadGeneration) return;
                danmakuCountRequest = null;
                knownDanmakuCount = result;
                danmakuLoadPolicy.setCount(result);
                downdanmu();
            });
        });
    }

    private void updateDanmakuButton() {
        if (btn_danmaku == null) return;
        btn_danmaku.setImageResource(isDanmakuVisible ? R.mipmap.danmakuon : R.mipmap.danmakuoff);
        btn_danmaku.setContentDescription(isDanmakuVisible ? "关闭弹幕并释放缓存" : "打开弹幕，手动加载");
        boolean allowManualOverride = danmakuLoadPolicy != null
                && (danmakuLoadPolicy.isSuppressed() || danmakuLoadPolicy.needsCount());
        btn_danmaku.setVisibility(!isAudioOnlyMode && (allowManualOverride
                || SharedPreferencesUtil.getBoolean("player_ui_showDanmakuBtn", true)) ? View.VISIBLE : View.GONE);
        if (mDanmakuView != null) {
            mDanmakuView.setVisibility(isDanmakuVisible && !isAudioOnlyMode ? View.VISIBLE : View.GONE);
        }
    }

    private void toggleDanmakuByUser() {
        boolean enable = !isDanmakuVisible;
        SharedPreferencesUtil.putBoolean("pref_switch_danmaku", enable);
        if (isLiveMode) {
            isDanmakuVisible = enable;
            if (mDanmakuView != null) {
                if (enable) mDanmakuView.show();
                else mDanmakuView.hide();
            }
        } else {
            stopDanmakuLoading();
            danmakuLoadPolicy.setEnabledByUser(enable);
            isDanmakuVisible = enable;
            if (enable) downdanmu();
        }
        updateDanmakuButton();
    }

    private void stopDanmakuLoading() {
        danmakuLoadGeneration++;
        if (danmakuCountRequest != null) { danmakuCountRequest.cancel(); danmakuCountRequest = null; }
        if (danmakuXmlRequest != null) { danmakuXmlRequest.cancel(); danmakuXmlRequest = null; }
        stopDanmakuWindow();
        danmakuLoadStarted = false;
        if (mDanmakuView != null && !isLiveMode) {
            mDanmakuView.setCallback(null);
            mDanmakuView.release();
            mDanmakuView.enableDanmakuDrawingCache(false);
            mDanmakuView.setVisibility(View.GONE);
        }
        mContext = null;
    }

    private boolean isDanmakuRenderingEnabled() {
        return hasDanmaku && mDanmakuView != null && (isLiveMode || isDanmakuVisible);
    }

    private void stopDanmakuWindow() {
        DanmakuWindowLoader loader = danmakuWindowLoader;
        danmakuWindowLoader = null;
        if (loader != null) loader.close();
        pendingDanmakuWindow = null;
        displayedDanmakuSegments.clear();
        windowDanmakuParser = null;
    }

    private void applyDanmakuWindow() {
        if (destroyed || !isDanmakuVisible || pendingDanmakuWindow == null || windowDanmakuParser == null
                || mDanmakuView == null || !mDanmakuView.isPrepared()) return;
        if (!pendingDanmakuWindow.keySet().containsAll(displayedDanmakuSegments)) {
            // Drop expired segments from the renderer as well as the network cache.
            mDanmakuView.removeAllDanmakus(true);
            mDanmakuView.clearDanmakusOnScreen();
            displayedDanmakuSegments.clear();
        }
        for (Map.Entry<Integer, DmSegMobileReply> entry : pendingDanmakuWindow.entrySet()) {
            updateHighEnergyData(entry.getKey(), entry.getValue());
            if (displayedDanmakuSegments.contains(entry.getKey())) continue;
            DmSegMobileReply displaySegment = limitDanmakuSegment(entry.getValue(),
                    DeviceProfile.maxDanmakuPerSegment());
            windowDanmakuParser.setDanmakuSegments(Collections.singletonList(displaySegment));
            IDanmakuIterator items = windowDanmakuParser.parse().iterator();
            int renderedCount = 0;
            while (items.hasNext()) {
                mDanmakuView.addDanmaku(items.next());
                renderedCount++;
            }
            displayedDanmakuSegments.add(entry.getKey());
            Logu.d("danmaku-window", "shown segment=" + entry.getKey()
                    + ", fetched=" + (entry.getValue().elems == null ? 0 : entry.getValue().elems.size())
                    + ", rendered=" + renderedCount);
        }
        windowDanmakuParser.setDanmakuSegments(null);
    }

    private DmSegMobileReply limitDanmakuSegment(DmSegMobileReply segment, int maxCount) {
        if (segment == null || segment.elems == null || maxCount <= 0
                || segment.elems.size() <= maxCount) return segment;

        ArrayList<DanmakuElem> sorted = new ArrayList<>(segment.elems);
        Collections.sort(sorted, new Comparator<DanmakuElem>() {
            @Override
            public int compare(DanmakuElem left, DanmakuElem right) {
                return Integer.compare(left.progress, right.progress);
            }
        });
        ArrayList<DanmakuElem> sampled = new ArrayList<>(maxCount);
        for (int i = 0; i < maxCount; i++) {
            int index = (int) (((long) i * sorted.size()) / maxCount);
            sampled.add(sorted.get(Math.min(index, sorted.size() - 1)));
        }
        return new DmSegMobileReply(sampled);
    }

    private void updateHighEnergyData(int segmentIndex, DmSegMobileReply segment) {
        if (!highEnergyEnabled || segment == null || video_all <= 0) return;
        if (!highEnergySegments.add(segmentIndex)) return;
        if (highEnergyDataBuilder.addSegment(segment)) {
            HighEnergyData data = highEnergyDataBuilder.snapshot();
            if (!destroyed && seekbar_progress != null) {
                seekbar_progress.setHighEnergyData(data.events, data.stepSec);
            }
        }
    }

    private BaseDanmakuParser createParser(String stream) {
        return createParser(stream, null);
    }

    private BaseDanmakuParser createParser(String stream, java.util.List<DmSegMobileReply> protobufSegments) {
        if (protobufSegments != null) {
            BiliProtobufDanmakuParser parser = new BiliProtobufDanmakuParser();
            parser.sharedPreferences = SharedPreferencesUtil.getSharedPreferences();
            parser.setOwnUserHash(master.flame.danmaku.danmaku.util.SelfDanmakuStyle.senderHash(
                    SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)));
            parser.setDanmakuSegments(protobufSegments);
            return parser;
        }

        // 兼容性回退
        if (stream == null) {
            return new BaseDanmakuParser() {
                @Override
                protected Danmakus parse() {
                    return new Danmakus();
                }
            };
        }

        ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);

        assert loader != null;
        loader.load(stream);
        BaseDanmakuParser parser = new BiliDanmukuParser();
        parser.sharedPreferences = SharedPreferencesUtil.getSharedPreferences();
        parser.setOwnUserHash(master.flame.danmaku.danmaku.util.SelfDanmakuStyle.senderHash(
                SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)));
        IDataSource<?> dataSource = loader.getDataSource();
        parser.load(dataSource);
        return parser;
    }

    private void streamDanmaku(String danmakuFile) {
        streamDanmaku(danmakuFile, null);
    }

    private void streamDanmaku(String danmakuFile, java.util.List<DmSegMobileReply> protobufSegments) {
        if (destroyed || (!isLiveMode && !isDanmakuVisible) || mDanmakuView == null) return;
        Logu.v("danmaku", "stream");

        mContext = DanmakuContext.create();
        HashMap<Integer, Integer> maxLinesPair = new HashMap<>();
        maxLinesPair.put(BaseDanmaku.TYPE_SCROLL_RL, PlayerSettingsUtil.getDanmakuMaxLine());
        HashMap<Integer, Boolean> overlap = new HashMap<>();
        overlap.put(BaseDanmaku.TYPE_SCROLL_LR, SharedPreferencesUtil.getBoolean("player_danmaku_allowoverlap", true));
        overlap.put(BaseDanmaku.TYPE_FIX_BOTTOM, SharedPreferencesUtil.getBoolean("player_danmaku_allowoverlap", true));
        mContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 1)
                .setDuplicateMergingEnabled(SharedPreferencesUtil.getBoolean("player_danmaku_mergeduplicate", false))
                .setScrollSpeedFactor(PlayerSettingsUtil.getDanmakuDurationFactor(isLiveMode))
                .setScaleTextSize(PlayerSettingsUtil.getDanmakuSize())// 缩放值
                .setMaximumLines(maxLinesPair)
                .setDanmakuTransparency(PlayerSettingsUtil.getDanmakuTransparency())
                .preventOverlapping(overlap);

        BaseDanmakuParser mParser = createParser(danmakuFile, protobufSegments);
        windowDanmakuParser = danmakuWindowLoader != null
                && mParser instanceof BiliProtobufDanmakuParser
                ? (BiliProtobufDanmakuParser) mParser : null;

        final IDanmakuView targetView = mDanmakuView;
        final int generation = danmakuLoadGeneration;
        mDanmakuView.setCallback(new DrawHandler.Callback() {
            @Override
            public void prepared() {
                Logu.v("danmaku", "prepared");
                String msg = protobufSegments != null
                        ? "弹幕君准备完毕～(是新来的哦～)"
                        : "弹幕君准备完毕～(*≧ω≦)";
                runOnUiThread(() -> {
                    if (destroyed || (!isLiveMode && !isDanmakuVisible)
                            || generation != danmakuLoadGeneration || targetView != mDanmakuView) return;
                    applyDanmakuWindow();
                    if (isPrepared && ijkPlayer != null) {
                        targetView.start(Math.max(0L, ijkPlayer.getCurrentPosition()));
                        syncDanmakuClockToPlayer(isPlaying);
                        if (!isPlaying) targetView.pause();
                        if (!isDanmakuVisible) targetView.hide();
                    }
                    if (isLiveMode && liveWebSocket == null) {
                        danmuSocketConnect();
                    }
                    addDanmaku(msg, Color.WHITE);
                });
            }

            @Override
            public void updateTimer(DanmakuTimer timer) {
                if (ijkPlayer != null && isPrepared) {
                    // The media position is sampled by progressChange() at a low
                    // frequency; do not cross the JNI/media boundary for every
                    // danmaku draw callback on old watches.
                    timer.update(danmakuClock.positionAt(SystemClock.elapsedRealtime()));
                }
            }

            @Override
            public void danmakuShown(BaseDanmaku danmaku) {
            }

            @Override
            public void drawingFinished() {
            }
        });
        mDanmakuView.enableDanmakuDrawingCache(true);
        mDanmakuView.prepare(mParser, mContext);
    }

    public void addDanmaku(String text, int color) {
        addDanmaku(text, color, 25, 1, 0);
    }

    public void addDanmaku(String text, int color, int textSize, int type, int backgroundColor) {
        addDanmaku(text, color, textSize, type, backgroundColor, false);
    }

    public void addOwnDanmaku(String text, int color) {
        addDanmaku(text, color, 25, 1, 0, true);
    }

    public void addLiveChatDanmaku(String nickname, String content, long senderMid) {
        long ownMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String text = SharedPreferencesUtil.getBoolean("player_danmaku_showsender", true)
                && nickname != null && !nickname.isEmpty() ? nickname + "：" + content : content;
        addDanmaku(text, Color.WHITE, 25, 1, 0, ownMid > 0 && ownMid == senderMid);
    }

    private void addDanmaku(String text, int color, int textSize, int type, int backgroundColor, boolean own) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> addDanmaku(text, color, textSize, type, backgroundColor, own));
            return;
        }
        if ((!isLiveMode && !isDanmakuVisible) || mContext == null || mDanmakuView == null) return;
        BaseDanmaku danmaku = mContext.mDanmakuFactory.createDanmaku(type);
        if (text == null || danmaku == null || ijkPlayer == null)
            return;
        danmaku.text = text;
        danmaku.padding = 5;
        danmaku.priority = 1;
        danmaku.textColor = color;
        danmaku.backgroundColor = backgroundColor;
        if (own) master.flame.danmaku.danmaku.util.SelfDanmakuStyle.apply(danmaku);
        danmaku.textSize = textSize * (mContext.getDisplayer().getDensity() - 0.6f);
        danmaku.time = mDanmakuView.getCurrentTime() + 100;
        mDanmakuView.addDanmaku(danmaku);
    }

    public static byte[] decompress(byte[] data) {
        byte[] output;
        Inflater decompresser = new Inflater(true);// 这个true是关键
        decompresser.reset();
        decompresser.setInput(data);
        ByteArrayOutputStream o = new ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[2048];
            while (!decompresser.finished()) {
                int i = decompresser.inflate(buf);
                if (i == 0 && !decompresser.finished()) {
                    throw new IOException("Incomplete danmaku compression stream");
                }
                o.write(buf, 0, i);
            }
            output = o.toByteArray();
        } catch (Exception e) {
            output = data;
            e.printStackTrace();
        } finally {
            try {
                o.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        decompresser.end();
        return output;
    }

    public void controlVideo() {
        if (isPlaying) {
            playerPause();
        } else {
            if (video_all > 0 && video_now > 0 && video_now >= video_all - 250)
                playbackCompleted = true;
            playerResume();
        }
        autohideReset();
    }

    @SuppressLint("SetTextI18n")
    public void changeVolume(Boolean add_or_cut) {
        if (audioManager == null) return;
        if (SharedPreferencesUtil.getBoolean(com.RobinNotBad.BiliClient.player.PlaybackVolume.ENABLED, false)) {
            int value = com.RobinNotBad.BiliClient.player.PlaybackVolume.adjust(
                    SharedPreferencesUtil.getInt(com.RobinNotBad.BiliClient.player.PlaybackVolume.GAIN, 100),
                    PlayerSettingsUtil.getVolumeStep(), add_or_cut);
            SharedPreferencesUtil.putInt(com.RobinNotBad.BiliClient.player.PlaybackVolume.GAIN, value);
            applyPlaybackGain();
            showPlaybackAdjustment("播放器音量：" + value + "%（系统" + getVolumePercent() + "%）");
            autohideReset(); return;
        }
        int volumeNow = audioManager.getStreamVolume(STREAM_MUSIC);
        int volumeMax = audioManager.getStreamMaxVolume(STREAM_MUSIC);
        int stepPercent = PlayerSettingsUtil.getVolumeStep();
        int volumeStep = Math.max(1, Math.round(volumeMax * stepPercent / 100f));
        int volumeNew = volumeNow + (add_or_cut ? volumeStep : -volumeStep);
        if (volumeNew >= 0 && volumeNew <= volumeMax) {
            audioManager.setStreamVolume(STREAM_MUSIC, volumeNew, 0);
            volumeNow = volumeNew;
        }
        int show = (int) ((float) volumeNow / (float) volumeMax * 100);
        showPlaybackAdjustment("音量：" + show + "%");
        autohideReset();
    }

    private void applyPlaybackGain() {
        float gain = com.RobinNotBad.BiliClient.player.PlaybackVolume.gain(
                SharedPreferencesUtil.getBoolean(com.RobinNotBad.BiliClient.player.PlaybackVolume.ENABLED, false),
                SharedPreferencesUtil.getInt(com.RobinNotBad.BiliClient.player.PlaybackVolume.GAIN, 100));
        try { if (ijkPlayer != null) ijkPlayer.setVolume(gain, gain); } catch (RuntimeException ignored) { }
        try { if (dashAudioPlayer != null) dashAudioPlayer.setVolume(gain, gain); } catch (RuntimeException ignored) { }
    }

    private void updateSeekButtons() {
        if (findViewById(R.id.seek_back_btn) == null) return;
        boolean show = !isLiveMode && SharedPreferencesUtil.getBoolean(
                com.RobinNotBad.BiliClient.player.PlaybackVolume.SEEK_BUTTONS, false);
        int seconds = com.RobinNotBad.BiliClient.player.PlaybackVolume.seekSeconds(SharedPreferencesUtil.getInt(
                com.RobinNotBad.BiliClient.player.PlaybackVolume.SEEK_SECONDS, 10));
        int[] ids = {R.id.seek_back_btn, R.id.seek_forward_btn};
        for (int i = 0; i < ids.length; i++) {
            final boolean forward = i == 1;
            TextView button = findViewById(ids[i]); button.setVisibility(show ? View.VISIBLE : View.GONE);
            button.setText((forward ? "+" : "−") + seconds + "s");
            button.setContentDescription((forward ? "快进" : "快退") + seconds + "秒");
            button.setOnClickListener(view -> {
                if (!screenLocked && !isLiveMode && isPrepared && ijkPlayer != null)
                    seekToPosition(ijkPlayer.getCurrentPosition() + (forward ? 1 : -1) * seconds * 1000L);
            });
        }
        if (menu_opened) updateExpandedMenuBounds();
    }

    private void savePgcProgress(long position) {
        if (bangumiPlayback) com.RobinNotBad.BiliClient.util.BangumiProgressStore.save(
                pgcAccount, pgcSeason, pgcEpisode, cid, position);
    }

    private void refreshSegmentSkipPosition() {
        if (!bangumiPlayback || !isPrepared || ijkPlayer == null) return;
        try { updateSegmentSkip(Math.max(0, ijkPlayer.getCurrentPosition())); }
        catch (RuntimeException ignored) { if (segmentSkipButton != null) segmentSkipButton.setVisibility(View.GONE); }
    }

    private void updateSegmentSkip(long position) {
        if (segmentSkipButton == null || segmentPolicy == null) return;
        int mode = com.RobinNotBad.BiliClient.player.PlaybackSegments.normalizeMode(SharedPreferencesUtil.getInt(
                com.RobinNotBad.BiliClient.player.PlaybackSegments.MODE, 1));
        com.RobinNotBad.BiliClient.player.PlaybackSegments.Segment segment =
                bangumiPlayback && isPrepared && !isLiveMode ? segmentPolicy.at(position, video_all) : null;
        segmentSkipButton.setVisibility(segment != null && mode == 1 && !screenLocked ? View.VISIBLE : View.GONE);
        if (segment != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) segmentSkipButton.getLayoutParams();
            int available = layout_control.getWidth() - params.leftMargin - params.rightMargin;
            String label = available < playerDp(76) ? "跳过" : "跳过" + segment.label();
            if (!label.contentEquals(segmentSkipButton.getText())) segmentSkipButton.setText(label);
            segmentSkipButton.setContentDescription("跳过" + segment.label());
        }
        if (segment == null || !isPlaying || isSeeking || videoSeekPending || audioSeekPending) return;
        long target = segmentPolicy.automaticTarget(position, video_all, SystemClock.elapsedRealtime(), mode);
        if (target >= 0) { seekToPosition(target); MsgUtil.showMsg("已跳过" + segment.label()); }
    }

    private final Runnable hideVolume = () -> text_volume.setVisibility(View.GONE);

    /**
     * 软件旋屏，给某些特殊设备用的。
     * 终端屎山又增高啦
     */
    private void softwareRotate() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screen_width = screen_landscape ? displayMetrics.heightPixels : displayMetrics.widthPixels;
        screen_height = screen_landscape ? displayMetrics.widthPixels : displayMetrics.heightPixels;

        ViewGroup root_layout = findViewById(R.id.root_layout);
        ViewGroup.LayoutParams params = root_layout.getLayoutParams();
        params.width = screen_width;
        params.height = screen_height;

        if (!destroyed)
            runOnUiThread(() -> {
                root_layout.setLayoutParams(params);
                root_layout.setPivotX(0);
                root_layout.setPivotY(0);
                root_layout.setX(screen_landscape ? screen_height : 0);
                root_layout.setRotation(screen_landscape ? 90 : 0);
                if (!SharedPreferencesUtil.getBoolean("player_display", Build.VERSION.SDK_INT < 26)
                        && isPrepared) {
                    MsgUtil.showMsg("请切换为TextureView才能支持软件旋屏！");
                }
            });
        changeVideoSize();
    }

    private boolean useSoftwarePlayerRotation() {
        return SharedPreferencesUtil.getBoolean("dev_player_rotate_software", false)
                || DeviceProfile.requiresSoftwarePlayerRotation();
    }

    private void videoMoveBy(float dx, float dy) {
        float x = dx + layout_video.getX();
        float y = dy + layout_video.getY();

        float width_delta = 0.5f * video_width * (layout_video.getScaleX() - 1f);
        float height_delta = 0.5f * video_height * (layout_video.getScaleY() - 1f);
        float video_x_min = video_origX - width_delta;
        float video_x_max = video_origX + width_delta;
        float video_y_min = video_origY - height_delta;
        float video_y_max = video_origY + height_delta;

        if (x < video_x_min)
            x = video_x_min;
        if (x > video_x_max)
            x = video_x_max;
        if (y < video_y_min)
            y = video_y_min;
        if (y > video_y_max)
            y = video_y_max;

        if (layout_video.getX() != x || layout_video.getY() != y) {
            videoPanGeneration++;
            // Logu.v("gesture","moveto:" + x + "," + y);
            layout_video.setX(x);
            layout_video.setY(y);
            if (!gesture_moved && (Math.abs(video_origX - x) > 5f || Math.abs(video_origY - y) > 5f)) {
                gesture_moved = true;
            }
        }
    }

    private void playerPause() {
        if (ijkPlayer != null && isPrepared) syncDanmakuClockToPlayer(false);
        isPlaying = false;
        recordPlayerAction("pause", null, currentQuality);
        stopBackgroundPlaybackService();
        if (ijkPlayer != null && isPrepared) {
            pausePlaybackPlayers();
            if (isDanmakuRenderingEnabled()) {
                mDanmakuView.pause();
            }
        }
        if (btn_control != null)
            btn_control.setImageResource(R.drawable.btn_player_play);
        updateAudioDashboard();
        if (mediaSession != null) {
            updateMediaSessionPlaybackState();
        }
    }

    private void playerResume() {
        boolean replaying = playbackCompleted;
        if (replaying) {
            playbackCompleted = false;
            resetPlaybackTimelineToStart("replay_from_completion");
        } else if (ijkPlayer != null && isPrepared) {
            syncDanmakuClockToPlayer(false);
        }
        isPlaying = true;
        recordPlayerAction("resume", null, currentQuality);
        if (ijkPlayer != null && isPrepared) {
            startPlaybackPlayers();
            if (isDanmakuRenderingEnabled()) {
                mDanmakuView.resume();
            }
        }
        if (btn_control != null)
            btn_control.setImageResource(R.drawable.btn_player_pause);
        updateAudioDashboard();
        if (mediaSession != null) {
            updateMediaSessionPlaybackState();
        }
        if (!isActivityVisible && backgroundPlaybackActive) {
            startBackgroundPlaybackService();
        }
    }

    private void resetPlaybackTimelineToStart(String action) {
        if (ijkPlayer == null || !isPrepared || isLiveMode) return;
        long now = SystemClock.elapsedRealtime();
        progressGuard.recordSeek(0L, video_all, now);
        video_now = 0;
        video_now_last = 0;
        progressAnomalyReported = false;
        seekPlaybackPlayers(0L);
        if (isDanmakuRenderingEnabled()) {
            mDanmakuView.seekTo(0L);
        }
        if (seekbar_progress != null) seekbar_progress.setProgress(0);
        saveLocalPlaybackProgress(0L);
        recordPlayerAction(action, null, currentQuality);
    }

    private void pausePlaybackPlayers() {
        danmakuClock.setPlaying(false, SystemClock.elapsedRealtime());
        dashStartGeneration++;
        dashVideoStarted = false;
        try {
            if (ijkPlayer != null) ijkPlayer.pause();
        } catch (RuntimeException ignored) {
        }
        try {
            if (dashAudioPlayer != null) dashAudioPlayer.pause();
        } catch (RuntimeException ignored) {
        }
    }

    private void startPlaybackPlayers() {
        try {
            applyPlaybackGain();
            if (hasDashAudio() && dashAudioPlayer != null && ijkPlayer != null) {
                if (videoSeekPending || audioSeekPending) return;
                if (dashPlaybackStartedOnce && (videoTrackBuffering || audioTrackBuffering)) return;
                long videoPosition = Math.max(0L, ijkPlayer.getCurrentPosition());
                long audioPosition = Math.max(0L, dashAudioPlayer.getCurrentPosition());
                if (SystemClock.elapsedRealtime() >= suppressDashDriftUntil
                        && Math.abs(audioPosition - videoPosition) > 1000L) {
                    alignDashAudioToVideo("resume", 1000L);
                    return;
                }
                dashAudioPlayer.start();
                int generation = ++dashStartGeneration;
                if (audioRenderingStarted) {
                    startDashVideoAfterAudio();
                } else {
                    // Some old IJK builds do not emit AUDIO_RENDERING_START. Keep only a
                    // short head start; a long delay lets the independent audio clock drift
                    // close to a second before the first video frame is shown.
                    mainHandler.postDelayed(() -> {
                        if (generation == dashStartGeneration && isPlaying && !dashVideoStarted) {
                            startDashVideoAfterAudio();
                        }
                    }, DASH_START_HEADSTART_MS);
                }
                return;
            }
            if (ijkPlayer != null) {
                ijkPlayer.start();
                danmakuClock.setPlaying(isPlaying, SystemClock.elapsedRealtime());
            }
        } catch (RuntimeException error) {
            recordPlayerAction("dash_start_failed", error.getClass().getSimpleName(), currentQuality);
        }
    }

    private void kickPlaybackPlayersAfterSeek(boolean audioOnlySeek) {
        try {
            if (hasDashAudio() && dashAudioPlayer != null && ijkPlayer != null) {
                dashAudioPlayer.start();
                if (audioOnlySeek) {
                    dashVideoStarted = false;
                    recordPlayerAction("dash_audio_seek_buffering_kick", null, currentQuality);
                    return;
                }
                // Keep the same start order as normal playback. Starting both
                // players in the same call lets the video decoder become the
                // late side on older IJK builds and produces an audible offset
                // after a seek, especially on long 60fps streams.
                int generation = ++dashStartGeneration;
                if (audioRenderingStarted) {
                    startDashVideoAfterAudio();
                } else {
                    mainHandler.postDelayed(() -> {
                        if (generation == dashStartGeneration && isPlaying && !dashVideoStarted) {
                            startDashVideoAfterAudio();
                        }
                    }, DASH_START_HEADSTART_MS);
                }
                recordPlayerAction("dash_seek_buffering_kick", null, currentQuality);
                return;
            }
            if (ijkPlayer != null) ijkPlayer.start();
        } catch (RuntimeException error) {
            recordPlayerAction("dash_seek_buffering_kick_failed",
                    error.getClass().getSimpleName(), currentQuality);
        }
    }

    private void startDashVideoAfterAudio() {
        if (destroyed || !isPlaying || !isPrepared || !hasDashAudio()
                || dashAudioPlayer == null || ijkPlayer == null || dashVideoStarted
                || videoSeekPending || audioSeekPending
                || (dashPlaybackStartedOnce && (videoTrackBuffering || audioTrackBuffering))) return;
        try {
            dashVideoStarted = true;
            ijkPlayer.start();
            danmakuClock.setPlaying(true, SystemClock.elapsedRealtime());
            dashPlaybackStartedOnce = true;
            recordPlayerAction("dash_video_started_after_audio", null, currentQuality);
        } catch (RuntimeException error) {
            dashVideoStarted = false;
            recordPlayerAction("dash_video_start_failed", error.getClass().getSimpleName(), currentQuality);
        }
    }

    private void pauseAudioForDashBuffering() {
        if (!hasDashAudio() || dashAudioPlayer == null) return;
        try {
            dashStartGeneration++;
            dashVideoStarted = false;
            dashAudioPlayer.pause();
        } catch (RuntimeException ignored) {
        }
    }

    private void pauseVideoForDashBuffering() {
        if (ijkPlayer == null) return;
        try {
            dashStartGeneration++;
            dashVideoStarted = false;
            ijkPlayer.pause();
        } catch (RuntimeException ignored) {
        }
    }

    private void scheduleDashBufferingPause() {
        // Single-player playback already keeps audio and video on one clock.
        // Applying the DASH coordination pause there creates visible stalls on
        // short network hiccups, especially on Android 8.1 watches.
        if (!hasDashAudio()) return;
        runOnUiThread(() -> {
            if (destroyed || dashBufferingPauseApplied || dashBufferingPauseRunnable != null)
                return;
            dashBufferingPauseRunnable = () -> {
                dashBufferingPauseRunnable = null;
                if (destroyed || !isPlaying || videoSeekPending || audioSeekPending
                        || (!videoTrackBuffering && !audioTrackBuffering)) return;
                dashBufferingPauseApplied = true;
                pausePlaybackPlayers();
                recordPlayerAction("dash_coordinated_buffering_pause", null, currentQuality);
            };
            mainHandler.postDelayed(dashBufferingPauseRunnable, 350L);
        });
    }

    private void cancelDashBufferingPause() {
        if (dashBufferingPauseRunnable == null || mainHandler == null) return;
        mainHandler.removeCallbacks(dashBufferingPauseRunnable);
        dashBufferingPauseRunnable = null;
    }

    private void resumeAfterDashBuffering() {
        if (!hasDashAudio()) return;
        runOnUiThread(() -> {
            if (destroyed || !isPlaying || videoTrackBuffering || audioTrackBuffering
                    || videoSeekPending || audioSeekPending) return;
            cancelDashBufferingPause();
            boolean needsCoordinatedResume = dashBufferingPauseApplied || !dashVideoStarted;
            dashBufferingPauseApplied = false;
            if (!needsCoordinatedResume) return;
            if (SystemClock.elapsedRealtime() < suppressDashDriftUntil) {
                startPlaybackPlayers();
                return;
            }
            if (!alignDashAudioToVideo("buffering_end", 1000L)) {
                startPlaybackPlayers();
            }
        });
    }

    private void seekPlaybackPlayers(long position) {
        long boundedPosition = Math.max(0L,
                video_all > 0 ? Math.min(position, video_all) : position);
        DanmakuWindowLoader windowLoader = danmakuWindowLoader;
        if (windowLoader != null) windowLoader.update(boundedPosition, video_all);
        danmakuClock.reset(boundedPosition, SystemClock.elapsedRealtime(), false);
        lastDanmakuClockSyncAt = -1L;
        if (videoSeekPending || audioSeekPending) {
            // IJK can report the previous seek as complete after a newer seekTo call.
            // Serialize seeks and keep only the user's latest destination.
            queuedSynchronizedSeekPosition = boundedPosition;
            resumeAfterSynchronizedSeek = resumeAfterSynchronizedSeek
                    || (isPlaying && isPrepared);
            suppressDashDriftUntil = SystemClock.elapsedRealtime() + 3500L;
            recordPlayerAction("seek_queued", String.valueOf(boundedPosition), currentQuality);
            return;
        }
        beginSynchronizedSeek(boundedPosition);
    }

    private void beginSynchronizedSeek(long position) {
        final int generation = ++synchronizedSeekGeneration;
        danmakuClock.reset(position, SystemClock.elapsedRealtime(), false);
        audioOnlySynchronizedSeek = false;
        resumeAfterSynchronizedSeek = resumeAfterSynchronizedSeek
                || (isPlaying && isPrepared);
        videoSeekPending = ijkPlayer != null && videoTrackPrepared;
        audioSeekPending = hasDashAudio() && dashAudioPlayer != null && audioTrackPrepared;
        pendingVideoSeekPosition = videoSeekPending ? position : -1L;
        pendingAudioSeekPosition = audioSeekPending ? position : -1L;
        suppressDashDriftUntil = SystemClock.elapsedRealtime() + 3500L;
        if (resumeAfterSynchronizedSeek) pausePlaybackPlayers();

        try {
            if (ijkPlayer != null) ijkPlayer.seekTo(position);
        } catch (RuntimeException error) {
            videoSeekPending = false;
            pendingVideoSeekPosition = -1L;
            recordPlayerAction("video_seek_failed", error.getClass().getSimpleName(), currentQuality);
        }
        if (dashAudioPlayer != null) {
            try {
                dashAudioPlayer.seekTo(position);
            } catch (RuntimeException error) {
                audioSeekPending = false;
                pendingAudioSeekPosition = -1L;
                recordPlayerAction("dash_audio_seek_failed", error.getClass().getSimpleName(), currentQuality);
            }
        }
        finishSynchronizedSeekIfReady(generation);
        mainHandler.postDelayed(() -> {
            if (destroyed || generation != synchronizedSeekGeneration
                    || (!videoSeekPending && !audioSeekPending)) return;
            videoSeekPending = false;
            audioSeekPending = false;
            pendingVideoSeekPosition = -1L;
            pendingAudioSeekPosition = -1L;
            recordPlayerAction("dash_seek_timeout", null, currentQuality);
            finishSynchronizedSeekIfReady(generation);
        }, 3000L);
    }

    private boolean alignDashAudioToVideo(String reason, long thresholdMs) {
        if (!hasDashAudio() || dashAudioPlayer == null || ijkPlayer == null
                || !audioTrackPrepared || !videoTrackPrepared) return false;
        try {
            long videoPosition = Math.max(0L, ijkPlayer.getCurrentPosition());
            long audioPosition = Math.max(0L, dashAudioPlayer.getCurrentPosition());
            long drift = audioPosition - videoPosition;
            if (Math.abs(drift) <= thresholdMs) return false;

            final int generation = ++synchronizedSeekGeneration;
            audioOnlySynchronizedSeek = true;
            resumeAfterSynchronizedSeek = isPlaying && isPrepared;
            videoSeekPending = false;
            audioSeekPending = true;
            pendingVideoSeekPosition = -1L;
            pendingAudioSeekPosition = videoPosition;
            suppressDashDriftUntil = SystemClock.elapsedRealtime() + 3500L;
            if (resumeAfterSynchronizedSeek) pausePlaybackPlayers();
            dashAudioPlayer.seekTo(videoPosition);
            recordPlayerAction("dash_audio_align_" + reason, String.valueOf(drift), currentQuality);
            mainHandler.postDelayed(() -> {
                if (destroyed || generation != synchronizedSeekGeneration || !audioSeekPending) return;
                audioSeekPending = false;
                pendingAudioSeekPosition = -1L;
                recordPlayerAction("dash_audio_align_timeout", reason, currentQuality);
                finishSynchronizedSeekIfReady(generation);
            }, 2500L);
            return true;
        } catch (RuntimeException error) {
            videoSeekPending = false;
            audioSeekPending = false;
            pendingVideoSeekPosition = -1L;
            pendingAudioSeekPosition = -1L;
            resumeAfterSynchronizedSeek = false;
            recordPlayerAction("dash_audio_align_failed", error.getClass().getSimpleName(), currentQuality);
            return false;
        }
    }

    private void finishSynchronizedSeekIfReady(int generation) {
        if (destroyed || generation != synchronizedSeekGeneration
                || videoSeekPending || audioSeekPending) return;
        if (queuedSynchronizedSeekPosition >= 0L) {
            long nextPosition = queuedSynchronizedSeekPosition;
            queuedSynchronizedSeekPosition = -1L;
            beginSynchronizedSeek(nextPosition);
            return;
        }
        // During initial preparation a history-position seek can begin just before
        // finishPreparationIfReady marks the session as playing.
        boolean shouldResume = resumeAfterSynchronizedSeek || isPlaying;
        boolean audioOnlySeek = audioOnlySynchronizedSeek;
        audioOnlySynchronizedSeek = false;
        resumeAfterSynchronizedSeek = false;
        suppressDashDriftUntil = SystemClock.elapsedRealtime() + 2500L;
        danmakuClock.setPlaying(isPlaying, SystemClock.elapsedRealtime());
        if (shouldResume && isPlaying) {
            if (videoTrackBuffering || audioTrackBuffering) {
                kickPlaybackPlayersAfterSeek(audioOnlySeek);
            } else {
                startPlaybackPlayers();
            }
        }
    }

    private void correctDashAudioDrift(long videoPosition) {
        if (!hasDashAudio() || dashAudioPlayer == null || !audioTrackPrepared || isSeeking
                || videoTrackBuffering || audioTrackBuffering
                || videoSeekPending || audioSeekPending) return;
        long now = SystemClock.elapsedRealtime();
        if (now < suppressDashDriftUntil
                || now - lastDashAudioSyncAt < DASH_DRIFT_CHECK_INTERVAL_MS) return;
        lastDashAudioSyncAt = now;
        try {
            long audioPosition = dashAudioPlayer.getCurrentPosition();
            long drift = audioPosition - videoPosition;
            if (Math.abs(drift) > DASH_DRIFT_CORRECTION_THRESHOLD_MS) {
                runOnUiThread(() -> alignDashAudioToVideo(
                        Math.abs(drift) >= 1000L ? "large_drift" : "drift",
                        DASH_DRIFT_CORRECTION_THRESHOLD_MS));
            }
        } catch (RuntimeException error) {
            recordPlayerAction("dash_audio_sync_failed", error.getClass().getSimpleName(), currentQuality);
        }
    }

    private void reconcileDanmakuClock(long playerPosition, long now) {
        if (!hasDanmaku || mDanmakuView == null || !isPlaying
                || isSeeking || videoSeekPending || audioSeekPending) return;
        if (lastDanmakuClockSyncAt >= 0L && now - lastDanmakuClockSyncAt < 750L) return;
        danmakuClock.reconcile(playerPosition, now, 800L);
        lastDanmakuClockSyncAt = now;
    }

    private void syncDanmakuClockToPlayer(boolean playing) {
        if (!hasDanmaku || mDanmakuView == null || ijkPlayer == null || !isPrepared) {
            danmakuClock.setPlaying(playing, SystemClock.elapsedRealtime());
            return;
        }
        try {
            danmakuClock.reset(Math.max(0L, ijkPlayer.getCurrentPosition()),
                    SystemClock.elapsedRealtime(), playing);
            lastDanmakuClockSyncAt = -1L;
        } catch (RuntimeException ignored) {
            danmakuClock.setPlaying(playing, SystemClock.elapsedRealtime());
        }
    }

    private void recordPlayerAction(String action, String error, int quality) {
        recordPlayerAction(action, error, quality, -1L);
    }

    private void recordPlayerAction(String action, String error, int quality,
                                    long bufferingDurationMs) {
        JSONObject details = new JSONObject();
        try {
            details.put("action", action);
            details.put("quality", quality);
            details.put("prepared", isPrepared);
            details.put("playing", isPlaying);
            details.put("playback_speed", currentPlaybackSpeed);
            details.put("longpress", onLongClick);
            if (onLongClick) details.put("held_ms", Math.max(0L, SystemClock.elapsedRealtime() - longPressStartedAt));
            details.put("live", isLiveMode);
            details.put("online", isOnlineVideo);
            details.put("relay", NetWorkUtil.isRelayActive());
            details.put("device_profile", DeviceProfile.get().name());
            details.put("player", ijkPlayer instanceof UnifiedDashPlayer
                    ? "unified" : ijkPlayer instanceof IjkMediaPlayer ? "ijk" : "platform");
            details.put("source_index", playbackUrlIndex);
            if (bufferingDurationMs >= 0L) {
                details.put("buffering_duration_ms", bufferingDurationMs);
            }
            if (error != null && !error.isEmpty()) details.put("error", error);
        } catch (Exception ignored) {
        }
        DiagnosticLogManager.record("player_interaction", details);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Logu.v("开始旋转屏幕");

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screen_width = displayMetrics.widthPixels;// 获取屏宽
        screen_height = displayMetrics.heightPixels;// 获取屏高
        changeVideoSize();

        Logu.v("旋转屏幕结束");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Logu.v("onNewIntent");
        if (intent != null && PlaybackKeepAliveService.ACTION_RETURN_TO_PLAYER.equals(intent.getAction())) {
            return;
        }
        finish();
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!backgroundTransitionSuppressed) {
            backgroundLeaveRequested = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { if (isPrepared && ijkPlayer != null) savePgcProgress(Math.max(0, ijkPlayer.getCurrentPosition())); }
        catch (RuntimeException ignored) { }
        stopLongPressSpeed();
        cancelPendingSingleTap();
        Logu.v("onPause");
        stopHardwareVolumeRepeat();
        saveInteractionProgress(true);
        wasPlayingBeforePause = isPlaying;
        foregroundRestoreGeneration++;
        foregroundRestoreScheduled = false;
        if (!isLiveMode && isPrepared && ijkPlayer != null) {
            try {
                foregroundRestorePosition = Math.max(0L, ijkPlayer.getCurrentPosition());
            } catch (RuntimeException ignored) {
            }
        }
        isActivityVisible = false;
        backgroundPlaybackActive = BackgroundPlaybackPolicy.shouldContinue(
                ordinaryBackgroundPlaybackEnabled(),
                isAudioOnlyMode,
                isPlaying,
                backgroundLeaveRequested,
                isScreenOff()
        );
        backgroundLeaveRequested = false;
        backgroundTransitionSuppressed = false;
        if (!backgroundPlaybackActive) {
            playerPause();
        } else {
            recordPlayerAction("background_playback", null, currentQuality);
            startBackgroundPlaybackService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePlayerInfoOverlay();
        applyPlaybackGain();
        updateSeekButtons();
        refreshSegmentSkipPosition();
        Logu.v("onResume");
        refreshCollectionPlaybackSettings();
        boolean returningFromBackgroundPlayback = backgroundPlaybackActive;
        isActivityVisible = true;
        backgroundLeaveRequested = false;
        backgroundPlaybackActive = false;
        backgroundTransitionSuppressed = false;
        stopBackgroundPlaybackService();
        if (returningFromBackgroundPlayback || videoOutputDetached) {
            recordPlayerAction("foreground_restore", null, currentQuality);
            restoreFrameOnNextSurface = true;
            final int generation = ++foregroundRestoreGeneration;
            if (foregroundRestoreScheduled) return;
            foregroundRestoreScheduled = true;
            if (layout_video != null) {
                layout_video.postDelayed(() -> {
                    if (destroyed || generation != foregroundRestoreGeneration || !isActivityVisible) return;
                    restoreVideoOutputIfAvailable();
                    // Give SurfaceView/TextureView callbacks time to rebind the existing
                    // decoder. Rebuilding immediately caused a visible position rollback.
                    layout_video.postDelayed(() -> {
                        if (destroyed || generation != foregroundRestoreGeneration
                                || !isActivityVisible) return;
                        foregroundRestoreScheduled = false;
                        if (isPrepared && !isAudioOnlyMode && !isLiveMode
                                && (videoOutputDetached || DeviceProfile.useUnifiedDashPlayer())) {
                            recreatePlayerForForeground();
                        }
                    }, 420L);
                }, 80L);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logu.v("onStop");
    }

    WebSocket liveWebSocket = null;
    private BrowserRelayProxy liveRelayProxy;

    @Override
    protected void onDestroy() {
        cancelPendingSingleTap();
        if(layout_control!=null)layout_control.removeCallbacks(toolbarBoundsUpdate);
        if(layout_control!=null)layout_control.removeCallbacks(expandedMenuBoundsUpdate);
        Logu.v("结束");
        stopHardwareVolumeRepeat();
        if (eventBusInit) {
            EventBus.getDefault().unregister(this);
            eventBusInit = false;
        }
        destroyed = true;
        favoriteGeneration++;
        playbackSwitchGeneration++;
        if (playbackOptionsMenu != null) playbackOptionsMenu.dismiss();
        if(mainHandler!=null)mainHandler.removeCallbacks(audioSleepCheck);
        stopDanmakuLoading();
        onLongClick = false;
        danmakuPausedByBuffering = false;
        backgroundLeaveRequested = false;
        backgroundPlaybackActive = false;
        stopBackgroundPlaybackService();

        cancelAllTimers();

        if (mDanmakuView != null) {
            mDanmakuView.release();
            mDanmakuView = null;
        }
        releasePlaybackPlayers();
        releaseTextureSurface();

        if (isOnlineVideo && danmakuFile != null && danmakuFile.exists())
            danmakuFile.delete();

        if (liveWebSocket != null) {
            liveWebSocket.close(1000, "");
            liveWebSocket = null;
        }
        if (liveRelayProxy != null) {
            liveRelayProxy.close();
            liveRelayProxy = null;
        }

        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }

        setRequestedOrientation(SharedPreferencesUtil.getBoolean("ui_landscape", false)
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        super.onDestroy();
    }

    private void cancelAllTimers() {
        if (surfaceTimer != null) {
            surfaceTimer.cancel();
            surfaceTimer = null;
        }
        if (progressTimer != null) {
            progressTimer.cancel();
            progressTimer = null;
        }
        if (onlineTimer != null) {
            onlineTimer.cancel();
            onlineTimer = null;
        }
        if (loadingTimer != null) {
            loadingTimer.cancel();
            loadingTimer = null;
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        bufferingUiRunnable = null;
        bufferingUiShown = false;
        layout_control.removeCallbacks(hidecon);
        text_volume.removeCallbacks(hideVolume);
        seekbar_progress.removeCallbacks(progressbarEnable);
    }

    private boolean ordinaryBackgroundPlaybackEnabled() {
        return SharedPreferencesUtil.getBoolean("player_background", false);
    }

    private boolean backgroundPlaybackAllowed() {
        return isAudioOnlyMode || ordinaryBackgroundPlaybackEnabled();
    }

    @SuppressWarnings("deprecation")
    private boolean isScreenOff() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return !powerManager.isInteractive();
        }
        return !powerManager.isScreenOn();
    }

    private void startBackgroundPlaybackService() {
        if (!backgroundPlaybackActive || !backgroundPlaybackAllowed() || !isPlaying) {
            stopBackgroundPlaybackService();
            return;
        }
        PlaybackKeepAliveService.start(getApplicationContext(), videoTitle, true);
    }

    private void stopBackgroundPlaybackService() {
        PlaybackKeepAliveService.stop(getApplicationContext());
    }

    OkHttpClient okHttpClient;

    private void danmuSocketConnect() {
        CenterThreadPool.run(() -> {
            try {
                if (liveWebSocket != null) {
                    liveWebSocket.close(1000, "reconnect");
                    liveWebSocket = null;
                }
                if (liveRelayProxy != null) {
                    liveRelayProxy.close();
                    liveRelayProxy = null;
                }
                String url = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?type=0&id=" + aid;
                ArrayList<String> mHeaders = new ArrayList<>() {
                    {
                        add("Cookie");
                        add(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
                        add("Referer");
                        add("https://live.bilibili.com/" + aid);
                        add("Origin");
                        add("https://live.bilibili.com");
                        add("User-Agent");
                        add(USER_AGENT_WEB);
                    }
                };
                Response response = NetWorkUtil.get(ConfInfoApi.signWBI(url), mHeaders);
                JSONObject data = new JSONObject(Objects.requireNonNull(response.body()).string())
                        .getJSONObject("data");
                JSONObject host = data.getJSONArray("host_list").getJSONObject(0);

                url = "wss://" + host.getString("host") + ":" + host.getInt("wss_port") + "/sub";
                Logu.v("连接WebSocket", url);

                OkHttpClient.Builder clientBuilder = NetWorkUtil.setOkHttpSsl(new OkHttpClient.Builder())
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(0, TimeUnit.SECONDS)
                        .writeTimeout(15, TimeUnit.SECONDS)
                        .pingInterval(30, TimeUnit.SECONDS);
                if (NetWorkUtil.isRelayActive()) {
                    liveRelayProxy = new BrowserRelayProxy(() ->
                            Logu.e("live-danmaku", "直播弹幕中继隧道断开"));
                    clientBuilder.proxy(new Proxy(Proxy.Type.HTTP,
                            new InetSocketAddress("127.0.0.1", liveRelayProxy.getPort())));
                }
                okHttpClient = clientBuilder.build();
                Request request = new Request.Builder()
                        .url(url)
                        .header("Cookie", SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))
                        .header("Origin", "https://live.bilibili.com")
                        .header("User-Agent", USER_AGENT_WEB)
                        .build();

                PlayerDanmuClientListener listener = new PlayerDanmuClientListener();
                listener.mid = mid;
                listener.roomid = aid;
                listener.key = data.getString("token");
                listener.playerActivity = this;

                liveWebSocket = okHttpClient.newWebSocket(request, listener);
                // okHttpClient.dispatcher().executorService().shutdown();
            } catch (Exception e) {
                MsgUtil.showMsg("直播弹幕连接失败");
                e.printStackTrace();
            }
        });
    }

    private void initMediaSession() {
        if (mediaSession != null) return;
        PlayerMediaSessionController.Callback callback = new PlayerMediaButtonReceiver.ToggleCallback() {
            @Override public void onPlay() { runOnUiThread(() -> setMediaPlaying(true)); }
            @Override public void onPause() { runOnUiThread(() -> setMediaPlaying(false)); }
            @Override public void onToggle() { runOnUiThread(() -> setMediaPlaying(!desiredMediaPlaying())); }
            @Override public void onSkipToNext() { runOnUiThread(() -> skipPlaybackItem(true)); }
            @Override public void onSkipToPrevious() { runOnUiThread(() -> skipPlaybackItem(false)); }
            @Override public void onSeekTo(long pos) {
                runOnUiThread(() -> { if (isPrepared) seekToPosition(pos); updateMediaSessionPlaybackState(); });
            }
        };
        try {
            mediaSession = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    ? new PlayerMediaSessionApi21(this, callback) : new PlayerMediaSessionLegacy(this, callback);
        } catch (RuntimeException error) {
            Logu.w("media-buttons", "系统媒体控制不可用：" + error.getClass().getSimpleName());
        }
    }

    private void setMediaPlaying(boolean playing) {
        if (destroyed) return;
        if (!isPrepared || playbackSwitchInFlight) {
            if (playbackSwitchInFlight) pendingMediaPlaybackRequest = playing;
            resumeAfterPrepare = playing; return;
        }
        if (playing && !isPlaying) playerResume();
        else if (!playing && isPlaying) playerPause();
        updateMediaSessionPlaybackState();
    }

    private boolean desiredMediaPlaying() {
        return !isPrepared || playbackSwitchInFlight ? resumeAfterPrepare : isPlaying;
    }

    private boolean handleMediaKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_MEDIA_NEXT: skipPlaybackItem(true); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: skipPlaybackItem(false); return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY: setMediaPlaying(true); return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP: setMediaPlaying(false); return true;
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: setMediaPlaying(!desiredMediaPlaying()); return true;
            default: return false;
        }
    }

    private boolean isMediaKey(int key) {
        return key == KeyEvent.KEYCODE_MEDIA_NEXT || key == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || key == KeyEvent.KEYCODE_MEDIA_PLAY || key == KeyEvent.KEYCODE_MEDIA_PAUSE
                || key == KeyEvent.KEYCODE_MEDIA_STOP || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || key == KeyEvent.KEYCODE_HEADSETHOOK;
    }

    private boolean skipPlaybackItem(boolean next) {
        if (destroyed || !isPrepared || playbackSwitchInFlight || favoritePaging || isLiveMode) return false;
        if (hasMultiplePages() && (next ? currentPageIndex + 1 < cids.size() : currentPageIndex > 0)) {
            switchToPage(currentPageIndex + (next ? 1 : -1));
            return true;
        }
        if (!collectionPlaybackActive()) return false;
        if (favoriteSession != null) {
            requestFavoriteAdvance(next ? 2 : 3);
            return true;
        }
        long current = currentQueueKey();
        long target = next ? collectionQueue.nextForUser(current, collectionPlaybackOrder, new java.util.Random())
                : collectionQueue.previousForUser(current, collectionPlaybackOrder);
        if (target > 0 && target != current) { switchPlaybackItem(target, 0, true, !next); return true; }
        return false;
    }

    private void refreshMediaControls() {
        boolean wanted = collectionPlaybackActive() || SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.PLAYER_MEDIA_SESSION_ENABLE, false);
        if (wanted && mediaSession == null) initMediaSession();
        if (!wanted && mediaSession != null) { mediaSession.release(); mediaSession = null; }
        updateMediaSessionMetadata();
        updateMediaSessionPlaybackState();
    }

    private void updateMediaSessionMetadata() {
        if (mediaSession == null) {
            return;
        }
        mediaSession.updateMetadata(videoTitle, video_all);
    }

    private void updateMediaSessionPlaybackState() {
        updateAudioDashboard();
        if (mediaSession == null) {
            return;
        }
        long position = isPrepared && ijkPlayer != null ? ijkPlayer.getCurrentPosition() : 0;
        boolean hasNext = hasMultiplePages() && currentPageIndex < pagenames.size() - 1;
        boolean hasPrevious = hasMultiplePages() && currentPageIndex > 0;
        if (collectionPlaybackActive()) {
            hasNext |= collectionQueue.canSkipNext(currentQueueKey(), collectionPlaybackOrder);
            hasNext |= favoriteSession != null && !favoriteSession.bottom
                    && collectionPlaybackOrder != CollectionPlaybackQueue.REVERSE;
            hasPrevious |= collectionQueue.previousForUser(currentQueueKey(), collectionPlaybackOrder) > 0;
            hasPrevious |= favoriteSession != null && !favoriteSession.bottom
                    && collectionPlaybackOrder == CollectionPlaybackQueue.REVERSE;
        }
        mediaSession.updatePlaybackState(isPlaying, position, hasNext, hasPrevious);
    }

    private int playerDp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @SuppressLint("NewApi")
    private void applyPlayerSafeArea() {
        // Before Android 9 there are no DisplayCutout insets to apply here.
        // Keep the 1.2.1 control geometry on legacy watch ROMs while checking
        // the 1.2.2 dynamic-inset regression on the affected hardware.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        final View root = findViewById(R.id.root_layout);
        if (root == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            playerWindowInsets = insets;
            root.post(this::updatePlayerSafeArea);
            return insets;
        });
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom)
                updatePlayerSafeArea();
        });
        root.post(() -> {
            ViewCompat.requestApplyInsets(root);
            updatePlayerSafeArea();
        });
    }

    @SuppressLint("NewApi")
    private void updatePlayerSafeArea() {
        if (destroyed) return;
        View root = findViewById(R.id.root_layout);
        if (root == null || root.getWidth() == 0 || root.getHeight() == 0) return;
        WindowInsetsCompat insets = playerWindowInsets;
        if (insets == null) insets = ViewCompat.getRootWindowInsets(root);
        Insets safe = insets == null ? Insets.NONE : insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        int[] location = new int[2];
        root.getLocationOnScreen(location);
        DisplayMetrics display = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(display);
        // The window can already exclude a notch or navigation bar. Only reserve
        // the part still overlapping our content, and recalculate after rotation.
        boolean unrotated = root.getRotation() == 0f;
        int left = Math.max(0, safe.left - (unrotated ? Math.max(0, location[0]) : 0));
        int top = Math.max(0, safe.top - (unrotated ? Math.max(0, location[1]) : 0));
        int right = Math.max(0, safe.right - (unrotated
                ? Math.max(0, display.widthPixels - location[0] - root.getWidth()) : 0));
        int bottom = Math.max(0, safe.bottom - (unrotated
                ? Math.max(0, display.heightPixels - location[1] - root.getHeight()) : 0));
        int radiusLeft=0, radiusRight=0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && insets != null
                && insets.toWindowInsets() != null) {
            android.view.WindowInsets platform = insets.toWindowInsets();
            for (int position : new int[]{android.view.RoundedCorner.POSITION_TOP_LEFT,
                    android.view.RoundedCorner.POSITION_BOTTOM_LEFT}) {
                android.view.RoundedCorner corner = platform.getRoundedCorner(position);
                if (corner != null) radiusLeft = Math.max(radiusLeft, corner.getRadius());
            }
            for (int position : new int[]{android.view.RoundedCorner.POSITION_TOP_RIGHT,
                    android.view.RoundedCorner.POSITION_BOTTOM_RIGHT}) {
                android.view.RoundedCorner corner = platform.getRoundedCorner(position);
                if (corner != null) radiusRight = Math.max(radiusRight, corner.getRadius());
            }
            // Rounded corners need horizontal clearance for the header controls.
            // Treating their radius as a top inset moved the whole header down.
        }
        applyPlayerSafeArea(left, top, right, bottom, radiusLeft, radiusRight,
                screen_round || Build.VERSION.SDK_INT>=23 && getResources().getConfiguration().isScreenRound());
    }

    private void applyPlayerSafeArea(int left,int top,int right,int bottom,int radiusLeft,int radiusRight,boolean round) {
        View root=findViewById(R.id.root_layout);if(root==null)return;
        playerCornerLeft=radiusLeft;playerCornerRight=radiusRight;
        int limit=Math.round(16 * getResources().getDisplayMetrics().density);
        left=Math.max(left,com.RobinNotBad.BiliClient.player.PlayerSafeAreaPolicy.cornerInset(radiusLeft,root.getWidth(),root.getHeight(),round,limit));
        right=Math.max(right,com.RobinNotBad.BiliClient.player.PlayerSafeAreaPolicy.cornerInset(radiusRight,root.getWidth(),root.getHeight(),round,limit));
        applyPlayerSafeArea(left,top,right,bottom);
        ViewCompat.postOnAnimation(root, this::recordPlayerGeometry);
    }

    private void applyPlayerSafeArea(int insetLeft, int insetTop, int insetRight, int insetBottom) {
        int minimum = playerDp(4);
        playerSafeLeft = Math.max(minimum, insetLeft);
        playerSafeTop = Math.max(minimum, insetTop);
        playerSafeRight = Math.max(minimum, insetRight);
        playerSafeBottom = Math.max(minimum, insetBottom);

        applyPlayerMargins(layout_top, playerSafeLeft, Math.max(0, insetTop), playerSafeRight, 0);
        View bottomControl = findViewById(R.id.bottom_control);
        applyPlayerMargins(bottomControl, playerSafeLeft, 0, playerSafeRight, playerSafeBottom);
        applyPlayerMargins(right_control, 0, 0, playerSafeRight, 0);
        applyPlayerMargins(btn_lock, playerSafeLeft, 0, 0, 0);
        applyPlayerMargins(findViewById(R.id.sv_danmaku), playerSafeLeft, playerSafeTop, playerSafeRight, playerSafeBottom);
        applyPlayerPadding(text_progress, playerSafeLeft, 0, 0, 0);
        applyPlayerPadding(text_online, 0, 0, playerSafeRight, 0);
        // bottom_control already carries the safe margins; applying them again
        // inside the row squeezes and shifts every playback/volume hit target.
        applyPlayerPadding(bottom_buttons, 0, 0, 0, 0);
        applyPlayerPadding(layout_card_bg, playerSafeLeft, playerSafeTop, playerSafeRight, playerSafeBottom);
        updateRightToolbarBounds();
        updateExpandedMenuBounds();
    }

    private void recordPlayerGeometry() {
        if (destroyed) return;
        View root=findViewById(R.id.root_layout),bottom=findViewById(R.id.bottom_control);
        if(root==null||bottom==null||root.getWidth()<=0)return;
        try {
            JSONObject d=new JSONObject();
            d.put("viewport",root.getWidth()+"x"+root.getHeight());d.put("rotation",root.getRotation());
            d.put("density",getResources().getDisplayMetrics().density);d.put("font_scale",getResources().getConfiguration().fontScale);
            d.put("round_setting",screen_round);d.put("round_display",Build.VERSION.SDK_INT>=23&&getResources().getConfiguration().isScreenRound());
            d.put("corner_left",playerCornerLeft);d.put("corner_right",playerCornerRight);
            d.put("safe",playerSafeLeft+","+playerSafeTop+","+playerSafeRight+","+playerSafeBottom);
            d.put("bottom_size",bottom.getWidth()+"x"+bottom.getHeight());
            d.put("row_padding",bottom_buttons.getPaddingLeft()+","+bottom_buttons.getPaddingRight()+","+bottom_buttons.getPaddingBottom());
            if(Build.VERSION.SDK_INT>=31&&playerWindowInsets!=null&&playerWindowInsets.toWindowInsets()!=null){
                StringBuilder radii=new StringBuilder();
                for(int pos=0;pos<4;pos++){
                    android.view.RoundedCorner corner=playerWindowInsets.toWindowInsets().getRoundedCorner(pos);
                    if(pos>0)radii.append(',');radii.append(corner==null?0:corner.getRadius());
                }
                d.put("corner_radii",radii.toString());
            }
            String signature=d.toString();if(signature.equals(lastPlayerGeometry))return;
            lastPlayerGeometry=signature;DiagnosticLogManager.record("player_layout",d);
        }catch(Exception ignored){ }
    }

    private void applyPlayerMargins(View view, int left, int top, int right, int bottom) {
        if (view == null) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        Rect base = playerBaseMargins.get(view);
        if (base == null) {
            base = new Rect(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin);
            playerBaseMargins.put(view, base);
        }
        left = Math.max(base.left, left);
        top = Math.max(base.top, top);
        right = Math.max(base.right, right);
        bottom = Math.max(base.bottom, bottom);
        if (params.leftMargin == left && params.topMargin == top
                && params.rightMargin == right && params.bottomMargin == bottom) return;
        params.setMargins(left, top, right, bottom);
        if (Build.VERSION.SDK_INT >= 17) {
            boolean rtl = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            params.setMarginStart(rtl ? right : left);
            params.setMarginEnd(rtl ? left : right);
        }
        view.setLayoutParams(params);
    }

    private void applyPlayerPadding(View view, int left, int top, int right, int bottom) {
        if (view == null) return;
        Rect base = playerBasePaddings.get(view);
        if (base == null) {
            base = new Rect(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
            playerBasePaddings.put(view, base);
        }
        left = Math.max(base.left, left);
        top = Math.max(base.top, top);
        right = Math.max(base.right, right);
        bottom = Math.max(base.bottom, bottom);
        if (view.getPaddingLeft() != left || view.getPaddingTop() != top
                || view.getPaddingRight() != right || view.getPaddingBottom() != bottom)
            view.setPadding(left, top, right, bottom);
    }

    private void initUI() {
        segmentSkipButton = findViewById(R.id.segment_skip_btn);
        segmentSkipButton.setOnClickListener(view -> {
            if (screenLocked || !bangumiPlayback || !isPrepared || ijkPlayer == null || segmentPolicy == null) return;
            if (com.RobinNotBad.BiliClient.player.PlaybackSegments.normalizeMode(SharedPreferencesUtil.getInt(
                    com.RobinNotBad.BiliClient.player.PlaybackSegments.MODE, 1)) != 1) return;
            com.RobinNotBad.BiliClient.player.PlaybackSegments.Segment segment = segmentPolicy.at(ijkPlayer.getCurrentPosition(), video_all);
            if (segment != null) { seekToPosition(segment.endMs); segmentSkipButton.setVisibility(View.GONE); }
        });
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screen_width = displayMetrics.widthPixels;// 获取屏宽
        screen_height = displayMetrics.heightPixels;// 获取屏高

        if (SharedPreferencesUtil.getBoolean("player_ui_showRotateBtn", true))
            btn_rotate.setVisibility(View.VISIBLE);
        else
            btn_rotate.setVisibility(View.GONE);

        screen_round = SharedPreferencesUtil.getBoolean("player_ui_round", false);
        if (screen_round) {
            int padding = (int) (screen_width * 0.03);

            LinearLayout.LayoutParams progressParams = (LinearLayout.LayoutParams) seekbar_progress.getLayoutParams();
            progressParams.leftMargin = padding * 4;
            progressParams.rightMargin = padding * 4;
            seekbar_progress.setLayoutParams(progressParams);

            text_online.setPadding(0, 0, padding * 3, 0);
            text_progress.setPadding(padding * 3, 0, 0, 0);

            bottom_buttons.setPadding(padding, 0, padding, padding);

            right_control.setPadding(0, 0, padding, 0);

            RelativeLayout.LayoutParams danmakuParams = (RelativeLayout.LayoutParams) mDanmakuView.getLayoutParams();
            danmakuParams.setMargins(0, padding * 3, 0, padding * 3);
            mDanmakuView.setLayoutParams(danmakuParams);

            text_subtitle.setMaxWidth((int) (screen_width * 0.65));

            layout_top.setPadding(padding * 7, padding, padding * 7, 0);

            LinearLayout clockLayout = findViewById(R.id.clock_layout);
            clockLayout.setOrientation(LinearLayout.HORIZONTAL);
            RelativeLayout.LayoutParams clockLayoutParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clockLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
            clockLayout.setLayoutParams(clockLayoutParams);

            RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.addRule(RelativeLayout.BELOW, R.id.clock_layout);
            titleParams.topMargin = padding / 2;
            text_title.setLayoutParams(titleParams);
            text_title.setGravity(Gravity.CENTER);

            TextView textClock = findViewById(R.id.clock);
            LinearLayout.LayoutParams textClockParams = (LinearLayout.LayoutParams) textClock.getLayoutParams();
            textClockParams.leftMargin = padding / 2;
            textClockParams.topMargin = padding / 4;
            textClock.setLayoutParams(textClockParams);
        }

        applyPlayerSafeArea();

        if ((!SharedPreferencesUtil.getBoolean("player_show_online", false)) || aid == 0 || cid == 0)
            text_online.setVisibility(View.GONE);

        layout_top.setOnClickListener(view -> finish());
        layout_top.setOnLongClickListener(view -> { BiliTerminal.returnHome(this); return true; });

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        if (SharedPreferencesUtil.getBoolean("player_display", Build.VERSION.SDK_INT < 26)) {
            textureView = new TextureView(this);
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                    Logu.v("surfacetexture", "available");
                    mSurfaceTexture = surfaceTexture;
                    bindTextureSurface(surfaceTexture);
                }

                @Override
                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                    Logu.v("surfacetexture", "sizechanged");
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                    Logu.v("surfacetexture", "destroyed");
                    mSurfaceTexture = null;
                    videoOutputDetached = true;
                    restoreFrameOnNextSurface = true;
                    if (ijkPlayer != null)
                        ijkPlayer.setSurface(null);
                    releaseTextureSurface();
                    // Retain the SurfaceTexture on Android 4.x so returning
                    // from Home does not leave IJK attached to a dead buffer.
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
                }
            });
            layout_video.addView(textureView, params);
        } else {
            surfaceView = new SurfaceView(this);
            layout_video.addView(surfaceView, params);
        }

        btn_rotate.setOnClickListener(view -> {
            if (screenLocked) return;
            Logu.v("点击旋转按钮");
            screen_landscape = !screen_landscape;
            if (useSoftwarePlayerRotation())
                softwareRotate();
            else
                setRequestedOrientation(screen_landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        });

        setupVolumeButton(findViewById(R.id.button_sound_add), true);
        setupVolumeButton(findViewById(R.id.button_sound_cut), false);

        btn_menu.setOnClickListener(view -> {
            if (screenLocked) return;
            if (menu_opened) {
                right_second.setVisibility(View.GONE);
                btn_menu.setImageResource(R.mipmap.morehide);
            } else {
                if (expandedMenuAvailableWidth() < playerDp(32)||compactPlayerToolsNeeded()) {
                    showCompactPlayerTools();
                    return;
                }
                right_second.setVisibility(View.VISIBLE);
                btn_menu.setImageResource(R.mipmap.moreshow);
            }
            menu_opened = !menu_opened;
            updateExpandedMenuBounds();
        });

        btn_lock.setVisibility(SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.PLAYER_SCREEN_LOCK, true) ? View.VISIBLE : View.GONE);
        positionPlaybackSpeedLabel();
        btn_lock.setOnClickListener(view -> setScreenLocked(!screenLocked));

        layout_card_bg.setOnClickListener(view -> {
            layout_card_bg.setVisibility(View.GONE);
            card_subtitle.setVisibility(View.GONE);
            card_danmaku_send.setVisibility(View.GONE);
            card_page_selector.setVisibility(View.GONE);
            card_quality_selector.setVisibility(View.GONE);
            card_viewpoint_selector.setVisibility(View.GONE);
        });
        btn_danmaku_send.setOnClickListener(view -> {
            layout_card_bg.setVisibility(View.VISIBLE);
            card_danmaku_send.setVisibility(View.VISIBLE);
        });
        findViewById(R.id.danmaku_send).setOnClickListener(view1 -> {
            EditText editText = findViewById(R.id.danmaku_send_edit);
            String danmakuText = editText.getText().toString().trim();
            if (danmakuText.isEmpty()) {
                MsgUtil.showMsg("不能发送空弹幕喵");
            } else if (isLiveMode && (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) <= 0
                    || SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "").isEmpty())) {
                MsgUtil.showMsg("请先登录后发送直播弹幕");
            } else {
                layout_card_bg.setVisibility(View.GONE);
                card_danmaku_send.setVisibility(View.GONE);
                view1.setEnabled(false);
                final long sendAid = aid, sendCid = cid, sendPosition = video_now;
                final long sendMid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
                final boolean sendLive = isLiveMode;

                CenterThreadPool.run(() -> {
                    try {
                        MsgUtil.showMsg("正在发送~");

                        if (sendLive) {
                            JSONObject result = DanmakuApi.sendLiveDanmaku(
                                    sendAid, danmakuText, ToolsUtil.getRgb888(Color.WHITE));
                            if (result.optInt("code", -1) == 0) {
                                MsgUtil.showMsg("发送成功喵~");
                                runOnUiThread(() -> {
                                    if (!destroyed && isLiveMode && aid == sendAid
                                            && sendMid == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                                            && editText.getText().toString().trim().equals(danmakuText)) editText.setText("");
                                });
                            } else {
                                String message = result.optString(
                                        "message", result.optString("msg", "未知错误"));
                                MsgUtil.showMsg("发送失败：" + message);
                            }
                        } else {
                            int result = DanmakuApi.sendVideoDanmakuByAid(
                                    sendCid, danmakuText, sendAid, sendPosition,
                                    ToolsUtil.getRgb888(Color.WHITE), 1);

                            if (result == 0) {
                                MsgUtil.showMsg("发送成功喵~");
                                runOnUiThread(() -> {
                                    if (destroyed || isLiveMode || aid != sendAid || cid != sendCid
                                            || sendMid != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
                                    addOwnDanmaku(danmakuText, Color.WHITE);
                                    if (editText.getText().toString().trim().equals(danmakuText)) editText.setText("");
                                });
                            } else {
                                MsgUtil.showMsg("发送失败：" + result);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        MsgUtil.err(e);
                    } finally {
                        runOnUiThread(() -> view1.setEnabled(true));
                    }
                });
            }
        });
    }

    private void initSeekbars() {
        seekbar_progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onProgressChanged(SeekBar seekBar, int position, boolean fromUser) {
                runOnUiThread(() -> {
                    if (!isLiveMode)
                        text_progress.setText(StringUtil.toTime(position / 1000) + "/" + progress_str);
                });
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isPrepared && !destroyed) {
                    int seekPos = seekbar_progress.getProgress();
                    progressGuard.recordSeek(seekPos, video_all, SystemClock.elapsedRealtime());
                    video_now = seekPos;
                    video_now_last = seekPos;
                    seekPlaybackPlayers(seekPos);
                    updateSegmentSkip(seekPos);
                    if (isDanmakuRenderingEnabled()) {
                        mDanmakuView.seekTo((long) seekPos);
                    }
                    autohideReset();
                }
                isSeeking = false;
            }
        });

        seekbar_speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int position, boolean fromUser) {
                if (fromUser) {
                    text_newspeed.setText(speed_strs[position]);
                    text_speed.setText(speed_strs[position]);
                    if (ijkPlayer != null)
                        setPlaybackSpeed(speed_values[position]);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (speedTimer != null)
                    speedTimer.cancel();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                speedTimer = new Timer();
                TimerTask timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> layout_speed.setVisibility(View.GONE));
                    }
                };
                speedTimer.schedule(timerTask, 200);
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        DiagnosticLogManager.recordKey(this, keyCode);
        if (isMediaKey(keyCode)) { if (event.getRepeatCount() == 0) handleMediaKey(keyCode); return true; }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!isPrepared) return super.onKeyDown(keyCode, event);
            if (heldVolumeKeyCode != keyCode) {
                stopHardwareVolumeRepeat();
                heldVolumeKeyCode = keyCode;
                changeVolume(keyCode == KeyEvent.KEYCODE_VOLUME_UP);
                if (mainHandler != null) mainHandler.postDelayed(hardwareVolumeRepeat, 450L);
            }
            return true;
        }
        if (isPrepared)
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    controlVideo();
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    seekToPosition(ijkPlayer.getCurrentPosition() - 10000L);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    seekToPosition(ijkPlayer.getCurrentPosition() + 10000L);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    changeVolume(true);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    changeVolume(false);
                    break;
            }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        DiagnosticLogManager.recordKey(this, keyCode);
        if (isMediaKey(keyCode)) return true;
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (heldVolumeKeyCode == keyCode) stopHardwareVolumeRepeat();
            return isPrepared;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void stopHardwareVolumeRepeat() {
        heldVolumeKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        if (mainHandler != null) mainHandler.removeCallbacks(hardwareVolumeRepeat);
    }

    private void setupVolumeButton(View button, boolean increase) {
        if (button == null) return;
        button.setOnTouchListener(new View.OnTouchListener() {
            private boolean repeating;
            private final Runnable repeat = new Runnable() {
                @Override
                public void run() {
                    if (!repeating || destroyed || mainHandler == null) return;
                    changeVolume(increase);
                    mainHandler.postDelayed(this, 120L);
                }
            };

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        repeating = true;
                        changeVolume(increase);
                        if (mainHandler != null) mainHandler.postDelayed(repeat, 450L);
                        view.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        repeating = false;
                        if (mainHandler != null) mainHandler.removeCallbacks(repeat);
                        view.setPressed(false);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        controlTouchDiagnostics.beforeDispatch(this, event);
        boolean handled = super.dispatchTouchEvent(event);
        controlTouchDiagnostics.afterDispatch(this, event, handled);
        DiagnosticLogManager.recordTouch(this, event);
        return handled;
    }

    private void seekToPosition(long position) {
        if (ijkPlayer != null && isPrepared) {
            long boundedPosition = Math.max(0L, video_all > 0 ? Math.min(position, video_all) : position);
            progressGuard.recordSeek(boundedPosition, video_all, SystemClock.elapsedRealtime());
            video_now = (int) boundedPosition;
            video_now_last = video_now;
            seekPlaybackPlayers(boundedPosition);
            if (isDanmakuRenderingEnabled()) {
                mDanmakuView.seekTo(boundedPosition);
            }
            saveLocalPlaybackProgress(boundedPosition);
            savePgcProgress(boundedPosition);
            updateSegmentSkip(boundedPosition);
            updateAudioDashboard();
        }
    }

    private void loadLocalPlaybackProgress() {
        if (isOnlineVideo || localProgressFolderLocator.isEmpty()) return;
        try {
            VideoStorageUtil.Node folder = VideoStorageUtil.fromLocator(this, localProgressFolderLocator);
            if (folder == null) return;
            localProgressFile = folder.find(".playback_progress");
            if (localProgressFile != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        localProgressFile.openInput()))) {
                    progress_history = Math.max(0L, Long.parseLong(reader.readLine().trim()));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveLocalPlaybackProgress(long position) {
        if (isOnlineVideo || localProgressFolderLocator.isEmpty()) return;
        try {
            VideoStorageUtil.Node folder = VideoStorageUtil.fromLocator(this, localProgressFolderLocator);
            if (folder == null) return;
            if (localProgressFile == null)
                localProgressFile = folder.getOrCreateFile(".playback_progress", "text/plain");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    localProgressFile.openOutput(false)))) {
                writer.write(String.valueOf(Math.max(0L, position)));
            }
        } catch (Exception ignored) {
        }
    }

    private void toggleAudioOnlyMode() {
        boolean oldMode = isAudioOnlyMode;
        isAudioOnlyMode = !isAudioOnlyMode;
        if (!isLocalAudioFile) cachedUserAudioOnlyMode = isAudioOnlyMode;
        // 不保存状态，仅在当前播放会话中切换

        if (hasDashAudio()) {
            updateAudioOnlyButton();
            updateAudioOnlyUI();
            MsgUtil.showMsg(isAudioOnlyMode
                    ? "已切换到" + getAudioOnlyModeName()
                    : "已恢复画面");
            recordPlayerAction(isAudioOnlyMode ? "audio_only_on" : "audio_only_off", null, currentQuality);
            return;
        }

        if (isPrepared && ijkPlayer != null) {
            final long currentPosition = ijkPlayer.getCurrentPosition();
            final boolean wasPlaying = isPlaying;

            MsgUtil.showMsg(isAudioOnlyMode
                    ? "正在切换到" + getAudioOnlyModeName() + "..."
                    : "正在恢复画面...");

            CenterThreadPool.run(() -> {
                try {
                    runOnUiThread(() -> {
                        if (isDanmakuRenderingEnabled()) {
                            mDanmakuView.pause();
                        }
                        releasePlaybackPlayers();

                        loading_info.setVisibility(View.VISIBLE);
                        anim_loading.start();
                        loading_text0.setText(isAudioOnlyMode
                                ? "切换到" + getAudioOnlyModeName()
                                : "恢复画面");
                        isPrepared = false;
                        isPlaying = false;
                        resumeAfterPrepare = wasPlaying;

                        updateAudioOnlyButton();
                        updateAudioOnlyUI();
                    });

                    Thread.sleep(100);

                    runOnUiThread(() -> {
                        ijkPlayer = createMediaPlayer();
                        progress_history = currentPosition;

                        setDisplay();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        MsgUtil.showMsg("切换失败，请重试");
                        isAudioOnlyMode = oldMode;
                        // 不保存状态
                        updateAudioOnlyButton();
                        updateAudioOnlyUI();
                        loading_info.setVisibility(View.GONE);
                        anim_loading.stop();
                    });
                }
            });
        } else {
            updateAudioOnlyButton();
            updateAudioOnlyUI();
            MsgUtil.showMsg(isAudioOnlyMode
                    ? "已切换到" + getAudioOnlyModeName()
                    : "已恢复画面");
        }
    }

    private String getAudioOnlyModeName() {
        return isLiveMode ? "听直播模式" : "听视频模式";
    }

    private void updateAudioOnlyButton() {
        if (btn_audio_only != null) {
            btn_audio_only
                    .setImageResource(isAudioOnlyMode ? R.drawable.icon_audio_only_on : R.drawable.icon_audio_only_off);
        }
    }

    private boolean roundPlayerDisplay(){
        return screen_round||Build.VERSION.SDK_INT>=23&&getResources().getConfiguration().isScreenRound();
    }

    private void updateAudioDashboard(){
        if(audioPanel==null||!isAudioOnlyMode||destroyed)return;
        if(Looper.myLooper()!=Looper.getMainLooper()){
            if(mainHandler!=null)mainHandler.post(this::updateAudioDashboard);
            return;
        }
        if(!screenLocked)btn_lock.setVisibility(View.GONE);
        boolean previous=hasMultiplePages()&&currentPageIndex>0;
        boolean next=hasMultiplePages()&&currentPageIndex+1<pagenames.size();
        String queue="";
        if(hasMultiplePages())queue=" · P"+(currentPageIndex+1)+"/"+pagenames.size();
        if(collectionPlaybackActive()){
            long key=currentQueueKey();
            queue+=" · "+(cachedPlaybackSnapshot!=null?"歌单":"列表")+(collectionQueue.indexOf(key)+1)+"/"+collectionQueue.size()
                    +(favoriteSession!=null&&!favoriteSession.bottom?"+":"");
            previous|=collectionQueue.previousForUser(key,collectionPlaybackOrder)>0
                    ||favoriteSession!=null&&!favoriteSession.bottom&&collectionPlaybackOrder==CollectionPlaybackQueue.REVERSE;
            next|=collectionQueue.canSkipNext(key,collectionPlaybackOrder)
                    ||favoriteSession!=null&&!favoriteSession.bottom&&collectionPlaybackOrder!=CollectionPlaybackQueue.REVERSE;
        }
        String state=playbackSwitchInFlight?"正在切换":!isPrepared?"准备中":playbackCompleted?"播放结束"
                :isPlaying&&(videoTrackBuffering||audioTrackBuffering)?"缓冲中":isPlaying?"正在播放":"已暂停";
        String timer=audioSleepDeadline>0?" · "+Math.max(1,(audioSleepDeadline-SystemClock.elapsedRealtime()+59999)/60000)+"分钟后停止":"";
        audioPanel.update(videoTitle==null||videoTitle.isEmpty()?getAudioOnlyModeName():videoTitle,
                state+" · "+currentPlaybackSpeed+"×"+queue+timer,video_now,video_all,isPlaying,isPrepared&&!playbackSwitchInFlight,
                screenLocked,previous&&!playbackSwitchInFlight&&!favoritePaging,next&&!playbackSwitchInFlight&&!favoritePaging);
        audioPanel.setCover(getIntent().getStringExtra("cover"));
        View root=findViewById(R.id.root_layout);
        audioPanel.fit(root.getWidth(),root.getHeight(),layout_top.getVisibility()==View.VISIBLE?layout_top.getBottom():0,
                playerSafeLeft,playerSafeTop,playerSafeRight,playerSafeBottom,roundPlayerDisplay());
    }

    private void showAudioOptions(){
        if(screenLocked||destroyed)return;
        if(playbackOptionsMenu!=null)playbackOptionsMenu.dismiss();
        playbackOptionsMenu=new com.RobinNotBad.BiliClient.player.PlayerOptionsMenu(this,audioPanel);
        playbackOptionsMenu.getMenu().add(0,1,0,"倍速："+currentPlaybackSpeed+"×");
        playbackOptionsMenu.getMenu().add(0,2,1,"播放列表").setEnabled(hasMultiplePages()||collectionPlaybackActive());
        playbackOptionsMenu.getMenu().add(0,3,2,"定时停止");
        if(!isLocalAudioFile)playbackOptionsMenu.getMenu().add(0,4,3,"返回视频画面");
        playbackOptionsMenu.getMenu().add(0,5,4,"锁定播放器");
        playbackOptionsMenu.getMenu().add(0,6,5,"音量−");
        playbackOptionsMenu.getMenu().add(0,7,6,"音量+");
        playbackOptionsMenu.getMenu().add(0,8,7,"更多播放功能");
        playbackOptionsMenu.setOnMenuItemClickListener(item->{
            if(screenLocked)return true;
            if(item.getItemId()==1)showAudioSpeeds();
            else if(item.getItemId()==2)showAudioQueue();
            else if(item.getItemId()==3)showAudioSleepOptions();
            else if(item.getItemId()==4)toggleAudioOnlyMode();
            else if(item.getItemId()==5)setScreenLocked(true);
            else if(item.getItemId()==8)showCompactPlayerTools();
            else changeVolume(item.getItemId()==7);
            return true;
        });playbackOptionsMenu.show();
    }
    private void showAudioSpeeds(){
        playbackOptionsMenu=new com.RobinNotBad.BiliClient.player.PlayerOptionsMenu(this,audioPanel);
        for(int i=0;i<speed_values.length;i++)playbackOptionsMenu.getMenu().add(0,i+1,i,speed_values[i]+"×")
                .setCheckable(true).setChecked(Math.abs(currentPlaybackSpeed-speed_values[i])<.01f);
        playbackOptionsMenu.setOnMenuItemClickListener(item->{
            if(!screenLocked){int i=item.getItemId()-1;seekbar_speed.setProgress(i);
                text_speed.setText(speed_strs[i]);text_newspeed.setText(speed_strs[i]);setPlaybackSpeed(speed_values[i]);updateAudioDashboard();}
            return true;
        });playbackOptionsMenu.show();
    }
    private void showAudioQueue(){
        int index=hasMultiplePages()?currentPageIndex:collectionPlaybackActive()?collectionQueue.indexOf(currentQueueKey()):0;
        showAudioQueue(Math.max(0,index/30*30));
    }
    private void showAudioQueue(int start){
        final boolean parts=hasMultiplePages();
        final long[] ids=!parts&&collectionPlaybackActive()?collectionQueue.toArray():new long[0];
        final long sourceAid=aid,sourceCid=cid;
        playbackOptionsMenu=new com.RobinNotBad.BiliClient.player.PlayerOptionsMenu(this,audioPanel);
        int count=parts?pagenames.size():ids.length;
        for(int i=start;i<Math.min(count,start+30);i++){
            String name=parts?pagenames.get(i):cachedPlaybackSnapshot!=null&&ids[i]>0&&ids[i]<=cachedPlaybackSnapshot.items.size()
                    ?cachedPlaybackSnapshot.items.get((int)ids[i]-1).title:"合集第"+(i+1)+"个视频";
            playbackOptionsMenu.getMenu().add(0,i+1,i,name).setCheckable(true)
                    .setChecked(parts?i==currentPageIndex:ids[i]==currentQueueKey());
        }
        if(start>0)playbackOptionsMenu.getMenu().add(0,-1,count+1,"上一页");
        if(start+30<count)playbackOptionsMenu.getMenu().add(0,-2,count+2,"下一页");
        playbackOptionsMenu.setOnMenuItemClickListener(item->{
            int i=item.getItemId()-1;
            if(screenLocked||!isPrepared||playbackSwitchInFlight||favoritePaging||sourceAid!=aid||sourceCid!=cid)return true;
            if(item.getItemId()<0){showAudioQueue(item.getItemId()==-1?start-30:start+30);return true;}
            if(parts&&i>=0&&i<cids.size())switchToPage(i);
            else if(i>=0&&i<ids.length&&ids[i]!=currentQueueKey())switchPlaybackItem(ids[i],0,true,false);
            return true;
        });playbackOptionsMenu.show();
    }
    private void showAudioSleepOptions(){
        final int[] minutes={0,15,30,60};
        playbackOptionsMenu=new com.RobinNotBad.BiliClient.player.PlayerOptionsMenu(this,audioPanel);
        for(int i=0;i<minutes.length;i++)playbackOptionsMenu.getMenu().add(0,i+1,i,i==0?"关闭定时停止":minutes[i]+"分钟后停止");
        playbackOptionsMenu.setOnMenuItemClickListener(item->{
            if(!screenLocked)setAudioSleepDelay(minutes[item.getItemId()-1]*60000L);return true;
        });playbackOptionsMenu.show();
    }
    private void setAudioSleepDelay(long delay){
        mainHandler.removeCallbacks(audioSleepCheck);
        audioSleepDeadline=delay>0?SystemClock.elapsedRealtime()+delay:0;
        if(delay>0)mainHandler.postDelayed(audioSleepCheck,delay);
        updateAudioDashboard();
        MsgUtil.showMsg(delay>0?"已设置定时停止":"已关闭定时停止");
    }

    private void updateAudioOnlyUI() {
        runOnUiThread(() -> {
            updatePlayerInfoOverlay();
            if (!isLiveMode) {
                if (isAudioOnlyMode) {
                    stopDanmakuLoading();
                    isDanmakuVisible = false;
                } else downdanmu();
            }
            if (isAudioOnlyMode) {
                // 进入听视频模式
                text_speed.setVisibility(View.GONE);
                btn_debug.setVisibility(View.GONE);
                btn_danmaku.setVisibility(View.GONE);
                layout_video.setVisibility(View.GONE);
                layout_audio_only.setVisibility(View.VISIBLE);
                right_control.setVisibility(View.GONE);bottom_buttons.setVisibility(View.GONE);
                seekbar_progress.setVisibility(View.GONE);text_progress.setVisibility(View.GONE);text_online.setVisibility(View.GONE);
                if (mDanmakuView != null) {
                    mDanmakuView.setVisibility(View.GONE);
                }
                if (text_audio_title != null) {
                    String title = text_title.getText().toString();
                    text_audio_title.setText(title.isEmpty() ? getAudioOnlyModeName() : title);
                }
            } else {
                // 退出听视频模式
                text_speed.setVisibility(View.VISIBLE);
                updateDebugButtonVisibility();
                btn_danmaku.setVisibility(View.VISIBLE);
                layout_video.setVisibility(View.VISIBLE);
                layout_audio_only.setVisibility(View.GONE);
                text_progress.setVisibility(View.VISIBLE);
                if(layout_top.getVisibility()==View.VISIBLE)showcon();
                if (mDanmakuView != null && isDanmakuVisible) {
                    mDanmakuView.setVisibility(View.VISIBLE);
                }
            }
            updateAudioDashboard();
        });
    }

    private boolean eventBusInit = false;

    @Override
    protected void onStart() {
        super.onStart();
        if (eventBusEnabled() && !eventBusInit) {
            EventBus.getDefault().register(this);
            Logu.v("event", "register");
            eventBusInit = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onEvent(SnackEvent snackEvent) {
        if (isFinishing())
            return;
        Logu.v("event", "onEvent");

        long currentTime = System.currentTimeMillis();

        int duration;
        if (snackEvent.getDuration() > 0)
            duration = snackEvent.getDuration();
        else if (snackEvent.getDuration() == Snackbar.LENGTH_SHORT)
            duration = 1950;
        else if (snackEvent.getDuration() == Snackbar.LENGTH_INDEFINITE)
            duration = Integer.MAX_VALUE;
        else
            duration = 2750;

        long endTime = snackEvent.getStartTime() + duration;
        if (currentTime >= endTime) {
            EventBus.getDefault().removeStickyEvent(snackEvent);
        } else {
            MsgUtil.toast(snackEvent.getMessage()); // 由于Theme.Black不支持，只能这样用了
        }
    }

    protected boolean eventBusEnabled() {
        return SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true);
    }

    /**
     * 加载高能进度条数据
     */
    private void loadHighEnergyData() {
        highEnergyEnabled = SharedPreferencesUtil.getBoolean("player_high_energy", false);
        if (!highEnergyEnabled) {
            Logu.d("高能进度条", "功能已禁用");
            return;
        }
        highEnergyDataBuilder.reset(video_all);
        highEnergySegments.clear();
        seekbar_progress.clearHighEnergyData();
        Logu.d("高能进度条", "改用已加载弹幕分段生成密度数据，视频时长=" + video_all);
    }

    private boolean hasMultiplePages() {
        return pagenames != null && cids != null && pagenames.size() > 1
                && pagenames.size() == cids.size();
    }

    private void showPageSelectorCard() {
        if (!hasMultiplePages())
            return;

        runOnUiThread(() -> {
            RecyclerView pageSelectorRecycler = findViewById(R.id.page_selector_list);
            PageSelectorAdapter adapter = new PageSelectorAdapter();
            adapter.setData(pagenames, currentPageIndex);
            adapter.setOnItemClickListener(index -> {
                layout_card_bg.setVisibility(View.GONE);
                card_page_selector.setVisibility(View.GONE);
                if (index != currentPageIndex) {
                    switchToPage(index);
                }
            });
            pageSelectorRecycler.setLayoutManager(new CustomLinearManager(this));
            pageSelectorRecycler.setAdapter(adapter);
            layout_card_bg.setVisibility(View.VISIBLE);
            card_page_selector.setVisibility(View.VISIBLE);
        });
    }

    private void switchToPage(int pageIndex) {
        if (!hasMultiplePages() || cids == null || pageIndex < 0 || pageIndex >= cids.size()
                || pageIndex == currentPageIndex) return;
        switchPlaybackItem(aid, pageIndex, false);
    }

    private void switchPlaybackItem(long targetAid, int pageIndex, boolean collectionAdvance) {
        switchPlaybackItem(targetAid, pageIndex, collectionAdvance, false);
    }

    private void switchPlaybackItem(long targetAid, int pageIndex, boolean collectionAdvance, boolean backwards) {
        if (destroyed || playbackSwitchInFlight) return;
        final boolean cached = !isOnlineVideo && cachedPlaybackSnapshot != null;
        if (!isOnlineVideo && !cached) return;
        if (cached && (targetAid < 1 || targetAid > cachedPlaybackSnapshot.items.size())) return;
        final CachedPlaybackItem cachedItem = cached ? cachedPlaybackSnapshot.items.get((int) targetAid - 1) : null;
        final int generation = ++playbackSwitchGeneration;
        final long sourceAid = aid;
        final boolean wasPlaying = isPlaying;
        final ArrayList<String> sourceNames = pagenames;
        final boolean sourceFinished = finishWatching;
        final ArrayList<Long> sourceCids = cids;
        final long sourceMid = mid;
        final String sourceTitle = videoTitle;
        playbackSwitchInFlight = true;
        collectionSwitchInFlight = collectionAdvance;
        pendingMediaPlaybackRequest = null;
        resumeAfterPrepare = wasPlaying;
        if (collectionAdvance) {
            playbackCompleted = true;
            isPlaying = false;
            pausePlaybackPlayers();
            if (isDanmakuRenderingEnabled()) mDanmakuView.pause();
            btn_control.setImageResource(R.drawable.btn_player_play);
        }
        CenterThreadPool.run(() -> {
            try {
                PlayerData current = new PlayerData();
                current.aid = sourceAid;
                current.mid = sourceMid;
                current.title = sourceTitle;
                current.pagenames = sourceNames;
                current.cids = sourceCids;
                current.danmakuCount = knownDanmakuCount;
                current.qn = com.RobinNotBad.BiliClient.util.NetworkPlaybackQuality.requestedQuality(this,manualPlaybackQuality);
                PlayerData playerData = cached ? cachedItem.load(this)
                        : collectionPlaybackSource.loadItem(targetAid, pageIndex, current);
                runOnUiThread(() -> {
                    if (destroyed || generation != playbackSwitchGeneration || sourceAid != aid) return;
                    if (collectionAdvance && !collectionPlaybackActive()) {
                        playbackSwitchInFlight = collectionSwitchInFlight = false;
                        if (favoritePlaybackToken != null) stopFavoritePlayback();
                        return;
                    }
                    if (collectionAdvance && !cached) reportFinishedCollectionItem(aid, cid,
                            Math.max(0L, ijkPlayer == null ? 0 : ijkPlayer.getCurrentPosition()) / 1000);
                    long sourcePosition = Math.max(0L, ijkPlayer == null ? progress_history : ijkPlayer.getCurrentPosition());
                    if (cached) saveLocalPlaybackProgress(sourcePosition);
                    else playlistResumePoints.remember(aid, cid, sourcePosition, video_all, sourceFinished);
                    final int restoreProgress = cached ? 0 : (int) playlistResumePoints.position(
                            playerData.aid, playerData.cid, playerData.cidHistory, playerData.progress);
                    saveInteractionProgress(true);
                    isPrepared = false;
                    stopDanmakuLoading();
                    releasePlaybackPlayers();
                    if (mDanmakuView != null) {
                        mDanmakuView.release();
                        mDanmakuView = null;
                    }

                    if (cached) {
                        currentCachedIndex = (int) targetAid - 1;
                        localProgressFolderLocator = cachedItem.folder;
                        localProgressFile = null;
                        localSubtitleRoot = cachedItem.subtitles;
                        if (!isActivityVisible && backgroundPlaybackActive && isAudioOnlyMode)
                            cachedUserAudioOnlyMode = true;
                        isLocalAudioFile = cachedItem.audioOnly;
                        isAudioOnlyMode = isLocalAudioFile || cachedUserAudioOnlyMode;
                        getIntent().putExtra(CachedPlaybackSnapshot.INDEX, currentCachedIndex)
                                .putExtra("local_progress_folder", localProgressFolderLocator)
                                .putExtra("local_subtitle_root", localSubtitleRoot)
                                .putExtra("audio_only", isLocalAudioFile);
                    }
                    danmakuFile = null;
                    localRuntimeRetryCount = 0;
                    aid = playerData.aid;
                    cid = playerData.cid;
                    mid = playerData.mid;
                    pagenames = playerData.pagenames;
                    cids = playerData.cids;
                    currentPageIndex = playerData.currentPageIndex;
                    playbackSwitchInFlight = collectionSwitchInFlight = false;
                    foregroundRestorePosition = -1L;
                    initialEdgeId = 0;
                    interactionRestoreSnapshot = InteractionProgressStore.load(aid);
                    playbackCompleted = false;
                    video_now = video_now_last = 0;
                    getIntent().putExtra("aid", aid).putExtra("cid", cid).putExtra("mid", mid)
                            .putExtra("url", playerData.videoUrl).putExtra("title", playerData.title)
                            .putExtra("cover",playerData.cover)
                            .putExtra("audio_url", playerData.audioUrl).putExtra("danmaku", playerData.danmakuUrl)
                            .putExtra("danmaku_count", playerData.danmakuCount).putExtra("progress", restoreProgress)
                            .putExtra("currentPageIndex", currentPageIndex);
                    getIntent().putStringArrayListExtra("backup_urls", playerData.videoBackupUrls);
                    getIntent().putStringArrayListExtra("audio_backup_urls", playerData.audioBackupUrls);
                    getIntent().putStringArrayListExtra("pagenames", pagenames);
                    long[] newCids = new long[cids == null ? 0 : cids.size()];
                    for (int i = 0; i < newCids.length; i++) newCids[i] = cids.get(i);
                    getIntent().putExtra("cids", newCids);
                    setPlaybackUrls(playerData.videoUrl, playerData.videoBackupUrls);
                    setDashAudioUrls(playerData.audioUrl, playerData.audioBackupUrls);
                    danmaku_url = playerData.danmakuUrl;
                    hasDanmaku = danmaku_url != null && !danmaku_url.isEmpty();
                    resetDanmakuSource(playerData.danmakuCount);
                    if (collectionQueue != null) collectionQueue.recordPlayed(currentQueueKey(), backwards);
                    text_title.setText(playerData.title);
                    videoTitle = playerData.title;

                    if (playerData.qnStrList != null && playerData.qnValueList != null) {
                        qnStrList = playerData.qnStrList;
                        qnValueList = playerData.qnValueList;
                        currentQuality = playerData.qn;
                    }

                    loading_info.setVisibility(View.VISIBLE);
                    anim_loading.start();
                    loading_text0.setText(collectionAdvance ? "加载合集视频" : "加载P" + (pageIndex + 1));
                    isPrepared = false;
                    isPlaying = false;
                    resumeAfterPrepare = pendingMediaPlaybackRequest == null ? wasPlaying : pendingMediaPlaybackRequest;
                    pendingMediaPlaybackRequest = null;
                    finishWatching = false;
                    progress_history = restoreProgress;
                    if (cached) loadLocalPlaybackProgress();
                    subtitles = null;
                    subtitleLinks = null;
                    subtitle_selected = -1;
                    viewPoints = null;
                    viewPointAdapter = null;
                    if (btn_viewpoint != null) {
                        btn_viewpoint.setVisibility(View.GONE);
                    }

                    interactionData = null;
                    if (infoOverlay != null) infoOverlay.setChapters(null);
                    updatePlayerInfoOverlay();
                    currentEdgeId = 0;
                    currentQuestion = null;
                    questionShown = false;
                    if (interactionChoiceLayout != null) {
                        interactionChoiceLayout.setVisibility(View.GONE);
                        interactionChoiceLayout.removeAllViews();
                    }

                    ijkPlayer = createMediaPlayer();
                    mDanmakuView = findViewById(R.id.sv_danmaku);

                    updateCollectionPlaybackControls();
                    setDisplay();

                    layout_control.postDelayed(() -> CenterThreadPool.run(() -> {
                        if (destroyed)
                            return;

                        runOnUiThread(() -> {
                            loading_text0.setText("装填弹幕中");
                            loading_text1.setText("(≧∇≦)");
                        });

                        if (isOnlineVideo) {
                            danmakuFile = new File(getCacheDir(), "danmaku.xml");
                            if (danmakuFile.exists()) {
                                danmakuFile.delete();
                            }
                            downdanmu();
                        }

                        if (!destroyed && SharedPreferencesUtil.getBoolean("player_subtitle_autoshow", true)) {
                            downSubtitle(false);
                        }

                        if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                            loadHighEnergyData();
                        }

                        if (!destroyed && isOnlineVideo && aid > 0 && cid > 0 && SharedPreferencesUtil.getBoolean("player_show_viewpoints", true)) {
                            loadViewPoints();
                        }

                        if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                            loadInteractionVideo();
                        }
                    }), 60);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed || generation != playbackSwitchGeneration) return;
                    playbackSwitchInFlight = collectionSwitchInFlight = false;
                    recordPlayerAction("collection_item_failed", e.getClass().getSimpleName(), currentQuality);
                    MsgUtil.showMsg(collectionAdvance ? getString(R.string.collection_playback_failed) : "切换分P失败");
                    if (collectionAdvance && favoriteSession != null) {
                        favoritePageFailed = true;
                        favoriteRetryAction = backwards ? 3 : 2;
                        updateCollectionPlaybackControls();
                    }
                    stopBackgroundPlaybackService();
                });
            }
        });
    }


    private void loadFavoriteQueue() {
        final int generation = ++favoriteGeneration;
        final String token = favoritePlaybackToken;
        final long account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        final long sourceAid = aid;
        collectionQueueLoading = true;
        collectionQueueFailed = false;
        CenterThreadPool.run(() -> {
            try {
                com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.Session session =
                        com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.read(this, token, account);
                if (explicitOnlineQueue != session.isUploads()) throw new IOException("播放列表类型不匹配");
                CollectionPlaybackQueue queue = session.queue();
                if (!queue.contains(sourceAid)) throw new IOException("当前视频不在播放列表中");
                runOnUiThread(() -> {
                    if (destroyed || generation != favoriteGeneration || aid != sourceAid) return;
                    collectionQueueLoading = false;
                    if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
                        collectionQueueFailed = true;
                        favoritePendingCompletion = false;
                        stopFavoritePlayback();
                        updateCollectionPlaybackControls();
                        return;
                    }
                    favoriteSession = session;
                    collectionQueue = queue;
                    collectionQueue.recordPlayed(aid, false);
                    collectionQueueLoadedForAid = aid;
                    updateCollectionPlaybackControls();
                    if (favoritePendingCompletion && collectionPlaybackActive()) {
                        favoritePendingCompletion = false;
                        requestFavoriteAdvance(1);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (destroyed || generation != favoriteGeneration) return;
                    collectionQueueLoading = false;
                    collectionQueueFailed = true;
                    collectionQueueLoadedForAid = sourceAid;
                    if (favoritePendingCompletion) stopFavoritePlayback();
                    favoritePendingCompletion = false;
                    updateCollectionPlaybackControls();
                    MsgUtil.showMsg(onlineQueueLabel() + "列表加载失败，请重试或从列表重新播放");
                });
            }
        });
    }

    private void cancelFavoriteLoading() {
        if (favoritePlaybackToken == null) return;
        boolean completedWaiting = favoritePendingCompletion || favoritePaging && favoriteRetryAction == 1;
        if (favoritePaging || collectionQueueLoading) favoriteGeneration++;
        favoritePaging = false;
        collectionQueueLoading = false;
        if (completedWaiting) stopFavoritePlayback();
        favoritePendingCompletion = false;
    }

    /** 1 = completion, 2/3 = explicit next/previous, 4 = prepare a full shuffle. */
    private void requestFavoriteAdvance(int action) {
        if (!collectionPlaybackActive()) return;
        if (favoritePaging) {
            if (action == 1) favoritePendingCompletion = true;
            return;
        }
        if (action == 1 && hasMultiplePages() && currentPageIndex + 1 < cids.size()) {
            switchPlaybackItem(aid, currentPageIndex + 1, true);
            return;
        }
        long next = action == 3 ? collectionQueue.previousForUser(aid, collectionPlaybackOrder)
                : action == 2 ? collectionQueue.nextForUser(aid, collectionPlaybackOrder, new java.util.Random())
                : collectionQueue.next(aid, collectionPlaybackOrder, new java.util.Random());
        boolean needPages = !favoriteSession.bottom && (action == 4
                || action != 3 && collectionPlaybackOrder == CollectionPlaybackQueue.SHUFFLE
                || next < 0 && (action == 3 ? collectionPlaybackOrder == CollectionPlaybackQueue.REVERSE
                : collectionPlaybackOrder != CollectionPlaybackQueue.REVERSE));
        if (!needPages) {
            finishFavoriteAdvance(action);
            return;
        }
        final int generation = ++favoriteGeneration;
        final long sourceAid = aid;
        final com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.Session accepted = favoriteSession;
        favoritePaging = true;
        favoritePageFailed = false;
        favoriteRetryAction = action;
        CenterThreadPool.run(() -> {
            com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.Session progress = accepted;
            Exception failure = null;
            try {
                int pages = 0;
                do {
                    if (generation != favoriteGeneration) return;
                    if (accepted.account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                        throw new IOException("账号已切换");
                    progress = favoritePlaybackSource.nextPage(progress, () -> {
                        if (generation != favoriteGeneration) throw new java.util.concurrent.CancellationException();
                        if (accepted.account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                            throw new IOException("账号已切换");
                    });
                    if (generation != favoriteGeneration) return;
                    if (accepted.account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                        throw new IOException("账号已切换");
                    com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.save(this, favoritePlaybackToken, progress);
                    pages++;
                    // Empty/duplicate pages may be legitimate after edits. Keep progress but bound one retry.
                    if (action != 4 && pages >= 5 && !progress.bottom
                            && progress.queue().next(sourceAid, CollectionPlaybackQueue.SEQUENTIAL,
                            new java.util.Random()) < 0) throw new IOException(accepted.label() + "分页暂无新视频，请重试继续加载");
                } while (!progress.bottom && (action == 4 || collectionPlaybackOrder == CollectionPlaybackQueue.SHUFFLE
                        || progress.queue().next(sourceAid, CollectionPlaybackQueue.SEQUENTIAL, new java.util.Random()) < 0));
            } catch (Exception error) { failure = error; }
            final com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.Session result = progress;
            final Exception error = failure;
            runOnUiThread(() -> {
                if (destroyed || generation != favoriteGeneration || sourceAid != aid) return;
                favoritePaging = false;
                if (accepted.account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) {
                    stopFavoritePlayback();
                    updateCollectionPlaybackControls();
                    return;
                }
                favoriteSession = result;
                collectionQueue.append(result.queue().toArray());
                if (!collectionPlaybackActive()) return;
                if (error != null) {
                    favoritePageFailed = true;
                    if (action == 1 || favoritePendingCompletion) stopFavoritePlayback();
                    favoritePendingCompletion = false;
                    MsgUtil.showMsg(onlineQueueLabel() + "列表加载失败，可在左下角菜单重试；账号与网络错误不会自动跳过");
                    updateCollectionPlaybackControls();
                    return;
                }
                finishFavoriteAdvance(action);
                if (playbackSwitchInFlight) favoritePendingCompletion = false;
                if (favoritePendingCompletion && !playbackSwitchInFlight) {
                    favoritePendingCompletion = false;
                    requestFavoriteAdvance(1);
                }
            });
        });
    }

    private void finishFavoriteAdvance(int action) {
        if (!collectionPlaybackActive()) return;
        favoritePageFailed = false;
        if (action == 4) {
            collectionPlaybackOrder = CollectionPlaybackQueue.SHUFFLE;
            collectionQueue.clearForwardHistory();
            MsgUtil.showMsg(getString(R.string.collection_playback_shuffle));
            updateCollectionPlaybackControls();
            return;
        }
        long target = action == 3 ? collectionQueue.previousForUser(aid, collectionPlaybackOrder)
                : action == 2 ? collectionQueue.nextForUser(aid, collectionPlaybackOrder, new java.util.Random())
                : collectionQueue.next(aid, collectionPlaybackOrder, new java.util.Random());
        if (target == aid && action == 1 && currentPageIndex == 0 && !hasMultiplePages()) {
            playbackCompleted = false;
            resetPlaybackTimelineToStart("favorite_repeat");
            isPlaying = true;
            startPlaybackPlayers();
        } else if (target > 0 && (target != aid || action == 1)) {
            switchPlaybackItem(target, 0, true, action == 3);
        } else if (action == 1 || action == 2) {
            stopFavoritePlayback();
            MsgUtil.showMsg("当前" + onlineQueueLabel() + "列表已播放完毕");
        }
        updateCollectionPlaybackControls();
    }

    private void stopFavoritePlayback() {
        playbackCompleted = true;
        isPlaying = false;
        pausePlaybackPlayers();
        stopBackgroundPlaybackService();
        if (isDanmakuRenderingEnabled()) mDanmakuView.pause();
        btn_control.setImageResource(R.drawable.btn_player_play);
        updateMediaSessionPlaybackState();
    }

    private long currentQueueKey() {
        return cachedPlaybackSnapshot == null ? aid : currentCachedIndex + 1L;
    }

    private String onlineQueueLabel() {
        return favoriteSession != null ? favoriteSession.label() : explicitOnlineQueue ? "投稿" : "收藏";
    }

    private boolean collectionPlaybackAllowed() {
        return !isLiveMode && (cachedPlaybackSnapshot != null && currentCachedIndex >= 0
                || isOnlineVideo && aid > 0 && getIntent().getBooleanExtra("collection_playback_allowed", true));
    }

    private boolean collectionPlaybackActive() {
        return showCollectionPlayback && collectionPlaybackEnabled && collectionPlaybackAllowed()
                && (favoritePlaybackToken == null || favoriteSession != null
                && favoriteSession.account == SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                && collectionQueue != null && collectionQueue.contains(currentQueueKey());
    }

    private void refreshCollectionPlaybackSettings() {
        if (btn_quality == null || getIntent() == null) return;
        showCollectionPlayback = explicitOnlineQueue || SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK, false);
        collectionPlaybackEnabled = explicitOnlineQueue ? explicitOnlineEnabled : SharedPreferencesUtil.getBoolean(
                SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED, true);
        if (!showCollectionPlayback || !collectionPlaybackEnabled) cancelFavoriteLoading();
        if (!showCollectionPlayback && collectionSwitchInFlight) {
            playbackSwitchGeneration++;
            playbackSwitchInFlight = collectionSwitchInFlight = false;
        }
        updateCollectionPlaybackControls();
        if (showCollectionPlayback && !isOnlineVideo && cachedPlaybackToken != null
                && cachedPlaybackSnapshot == null && !collectionQueueLoading && !collectionQueueFailed) {
            loadCachedQueue();
            return;
        }
        if (showCollectionPlayback && isOnlineVideo && collectionPlaybackAllowed()
                && (collectionQueue == null || !collectionQueue.contains(aid))
                && !collectionQueueLoading && collectionQueueLoadedForAid != aid) loadCollectionQueue();
    }

    private void loadCachedQueue() {
        final String token = cachedPlaybackToken;
        final String source = video_url;
        collectionQueueLoading = true;
        CenterThreadPool.run(() -> {
            try {
                CachedPlaybackSnapshot snapshot = CachedPlaybackSnapshot.read(this, token);
                int index = snapshot.indexOf(source);
                if (index < 0) throw new IOException("当前缓存不在歌单中");
                runOnUiThread(() -> {
                    if (destroyed || isOnlineVideo || !token.equals(cachedPlaybackToken) || !source.equals(video_url)) return;
                    cachedPlaybackSnapshot = snapshot;
                    currentCachedIndex = index;
                    collectionQueue = snapshot.queue();
                    collectionQueue.recordPlayed(currentQueueKey(), false);
                    collectionQueueLoading = false;
                    collectionQueueFailed = false;
                    updateCollectionPlaybackControls();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    collectionQueueLoading = false;
                    collectionQueueFailed = true;
                    recordPlayerAction("cached_queue_failed", error.getClass().getSimpleName(), currentQuality);
                    updateCollectionPlaybackControls();
                });
            }
        });
    }

    private void loadCollectionQueue() {
        if (collectionQueueLoading || !collectionPlaybackAllowed()) return;
        if (favoritePlaybackToken != null) {
            loadFavoriteQueue();
            return;
        }
        final long sourceAid = aid;
        collectionQueueLoading = true;
        collectionQueueFailed = false;
        CenterThreadPool.run(() -> {
            try {
                final CollectionPlaybackQueue queue = collectionPlaybackSource.loadQueue(sourceAid);
                runOnUiThread(() -> {
                    if (destroyed || aid != sourceAid) return;
                    collectionQueueLoading = false;
                    collectionQueueLoadedForAid = sourceAid;
                    collectionQueue = queue != null && queue.contains(sourceAid) ? queue : null;
                    if (collectionQueue != null) collectionQueue.recordPlayed(sourceAid, false);
                    updateCollectionPlaybackControls();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (destroyed || aid != sourceAid) return;
                    collectionQueueLoading = false;
                    collectionQueueFailed = true;
                    collectionQueueLoadedForAid = sourceAid;
                    recordPlayerAction("collection_queue_failed", e.getClass().getSimpleName(), currentQuality);
                    updateCollectionPlaybackControls();
                });
            }
        });
    }

    private void updateCollectionPlaybackControls() {
        if (btn_collection_order == null) return;
        boolean active = collectionPlaybackActive();
        int visibility = active && !screenLocked ? View.VISIBLE : View.GONE;
        if (btn_collection_order.getVisibility() != visibility) {
            btn_collection_order.setVisibility(visibility);
            updateRightToolbarBounds();
        }
        int[] icons = {R.drawable.icon_collection_sequence, R.drawable.icon_collection_shuffle,
                R.drawable.icon_collection_repeat, R.drawable.icon_collection_reverse};
        int[] names = {R.string.collection_playback_sequential, R.string.collection_playback_shuffle,
                R.string.collection_playback_repeat, R.string.collection_playback_reverse};
        if (favoriteSession != null && favoriteSession.isUploads()) {
            boolean oldest = favoriteSession.uploadOldest;
            names[0] = oldest ? R.string.uploader_playback_order_oldest : R.string.uploader_playback_order_newest;
            names[3] = oldest ? R.string.uploader_playback_order_newest : R.string.uploader_playback_order_oldest;
            if (!oldest) {
                icons[0] = R.drawable.icon_collection_reverse;
                icons[3] = R.drawable.icon_collection_sequence;
            }
        }
        btn_collection_order.setImageResource(icons[collectionPlaybackOrder]);
        btn_collection_order.setContentDescription(getString(R.string.collection_playback_order,
                getString(names[collectionPlaybackOrder])));
        btn_collection_order.setOnClickListener(v -> {
            if (screenLocked || playbackSwitchInFlight || favoritePaging) return;
            if (favoriteSession != null && collectionPlaybackOrder == CollectionPlaybackQueue.SEQUENTIAL
                    && !favoriteSession.bottom) {
                MsgUtil.showMsg("正在准备完整" + onlineQueueLabel() + "列表，完成后开启随机播放；可关闭连播取消");
                requestFavoriteAdvance(4);
                return;
            }
            collectionPlaybackOrder = CollectionPlaybackQueue.nextMode(collectionPlaybackOrder);
            collectionQueue.clearForwardHistory();
            updateCollectionPlaybackControls();
            autohideReset();
            MsgUtil.showMsg(getString(names[collectionPlaybackOrder]));
        });
        btn_loop.setVisibility(!isLiveMode && !active ? View.VISIBLE : View.GONE);
        btn_auto_next.setVisibility(!isLiveMode && !active && hasMultiplePages() ? View.VISIBLE : View.GONE);
        boolean quality = !isLiveMode && isOnlineVideo
                && SharedPreferencesUtil.getBoolean("player_ui_showQualityBtn", true);
        boolean collectionMenu = showCollectionPlayback && collectionPlaybackAllowed();
        btn_quality.setVisibility(quality || collectionMenu ? View.VISIBLE : View.GONE);
        btn_quality.setOnClickListener(v -> {
            if (screenLocked) return;
            if (showCollectionPlayback && collectionPlaybackAllowed()) showPlaybackOptions();
            else showQualitySelectorCard();
        });
        refreshMediaControls();
    }

    private int expandedMenuAvailableWidth() {
        View toolbar = findViewById(R.id.right_toolbar_scroll);
        int toolbarWidth = toolbar == null ? playerDp(36) : Math.max(playerDp(28), toolbar.getMeasuredWidth());
        int reservedLeft = Math.max(playerDp(4), playerSafeLeft) + playerDp(48 + 4);
        return Math.max(0, layout_control.getWidth() - reservedLeft - toolbarWidth
                - Math.max(playerDp(4), playerSafeRight));
    }

    private boolean compactPlayerToolsNeeded(){
        return roundPlayerDisplay()||right_control.getHeight()<playerDp(68);
    }
    private void updateRightToolbarBounds(){
        if(layout_control==null||destroyed)return;
        layout_control.removeCallbacks(toolbarBoundsUpdate);
        layout_control.post(toolbarBoundsUpdate);
    }
    private void applyRightToolbarBounds(){
        if(destroyed||right_control==null||layout_control.getHeight()<=0)return;
        View more=findViewById(R.id.round_menu_btn);
        more.setVisibility(View.GONE);
        if(!roundPlayerDisplay()){
            if(roundSideControlsPositioned)restoreStandardSideControls();
            boolean visible=!screenLocked&&!isAudioOnlyMode&&layout_top.getVisibility()==View.VISIBLE;
            right_control.setVisibility(visible?View.VISIBLE:View.GONE);return;
        }
        boolean visible=!screenLocked&&!isAudioOnlyMode&&layout_top.getVisibility()==View.VISIBLE;
        right_control.setVisibility(visible?View.VISIBLE:View.GONE);
        if(!visible)return;
        int width=layout_control.getWidth(),height=layout_control.getHeight();
        int top=Math.max(playerSafeTop,layout_top.getBottom());
        View bottom=findViewById(R.id.bottom_control);
        int end=bottom.getVisibility()==View.VISIBLE?bottom.getTop()-playerDp(4):height-playerSafeBottom;
        int railHeight=Math.min(Math.max(playerDp(28),end-top),playerDp(144));
        fitRoundToolbarButtons(railHeight);
        int y=Math.max(top,Math.min((height-railHeight)/2,end-railHeight));
        double radius=Math.min(width,height)/2d;
        double dy=Math.min(radius,Math.max(Math.abs(y-height/2d),Math.abs(y+railHeight-height/2d)));
        int edgeGapDp=Math.max(0,Math.min(16,SharedPreferencesUtil.getInt(
                SharedPreferencesUtil.PLAYER_ROUND_SIDE_INSET_DP,4)));
        int inset=Math.max(playerSafeRight,
                (int)Math.ceil(width/2d-Math.sqrt(Math.max(0,radius*radius-dy*dy)))+playerDp(edgeGapDp));
        RelativeLayout.LayoutParams p=(RelativeLayout.LayoutParams)right_control.getLayoutParams();
        // Both rails use parent coordinates. Keeping BELOW/ABOVE here adds the
        // header height again and shifts the right rail away from the left one.
        int rightWidth=Math.max(playerDp(28),findViewById(R.id.right_toolbar_scroll).getMeasuredWidth());
        int columnWidth=Math.max(playerDp(48),Math.max(rightWidth,text_speed.getMeasuredWidth()));
        int axis=inset+columnWidth/2;
        int rightMargin=axis-rightWidth/2;
        if(right_control.getPaddingRight()!=0)right_control.setPadding(0,0,0,0);
        if(p.height!=railHeight||p.topMargin!=y||p.rightMargin!=rightMargin
                ||p.getRules()[RelativeLayout.BELOW]!=0||p.getRules()[RelativeLayout.ABOVE]!=0){
            p.addRule(RelativeLayout.BELOW,0);p.addRule(RelativeLayout.ABOVE,0);
            p.addRule(RelativeLayout.CENTER_VERTICAL,0);p.addRule(RelativeLayout.ALIGN_PARENT_TOP,RelativeLayout.TRUE);
            p.width=ViewGroup.LayoutParams.WRAP_CONTENT;p.height=railHeight;p.topMargin=y;p.rightMargin=rightMargin;
            if(Build.VERSION.SDK_INT>=17)p.setMarginEnd(rightMargin);
            right_control.setLayoutParams(p);
        }
        int leftHeight=0,lastBottomMargin=0;
        View[] leftControls={btn_debug,text_speed,btn_lock};
        for(View control:leftControls)if(control.getVisibility()==View.VISIBLE){
            RelativeLayout.LayoutParams cp=(RelativeLayout.LayoutParams)control.getLayoutParams();
            leftHeight+=control.getMeasuredHeight()+cp.bottomMargin;
            lastBottomMargin=cp.bottomMargin;
        }
        leftHeight-=lastBottomMargin;
        int leftTop=Math.max(top,Math.min(y+(railHeight-leftHeight)/2,end-leftHeight));
        for(View control:leftControls)if(control.getVisibility()==View.VISIBLE){
            RelativeLayout.LayoutParams cp=(RelativeLayout.LayoutParams)control.getLayoutParams();
            int left=axis-control.getMeasuredWidth()/2;
            if(cp.topMargin!=leftTop||cp.leftMargin!=left||cp.getRules()[RelativeLayout.ABOVE]!=0
                    ||cp.getRules()[RelativeLayout.CENTER_VERTICAL]!=0){
                cp.addRule(RelativeLayout.ABOVE,0);cp.addRule(RelativeLayout.CENTER_VERTICAL,0);
                cp.addRule(RelativeLayout.ALIGN_PARENT_TOP,RelativeLayout.TRUE);
                cp.topMargin=leftTop;cp.leftMargin=left;
                if(Build.VERSION.SDK_INT>=17)cp.setMarginStart(left);
                control.setLayoutParams(cp);
            }
            leftTop+=control.getMeasuredHeight()+cp.bottomMargin;
        }
        roundSideControlsPositioned=true;
    }

    private void restoreStandardSideControls(){
        fitRoundToolbarButtons(0);
        RelativeLayout.LayoutParams rail=(RelativeLayout.LayoutParams)right_control.getLayoutParams();
        rail.addRule(RelativeLayout.ALIGN_PARENT_TOP,0);
        rail.addRule(RelativeLayout.BELOW,R.id.top);rail.addRule(RelativeLayout.ABOVE,R.id.bottom_control);
        rail.height=ViewGroup.LayoutParams.MATCH_PARENT;rail.topMargin=0;rail.rightMargin=playerSafeRight;
        if(Build.VERSION.SDK_INT>=17)rail.setMarginEnd(playerSafeRight);
        right_control.setLayoutParams(rail);
        for(View control:new View[]{btn_debug,text_speed,btn_lock}){
            RelativeLayout.LayoutParams cp=(RelativeLayout.LayoutParams)control.getLayoutParams();
            cp.addRule(RelativeLayout.ALIGN_PARENT_TOP,0);cp.topMargin=0;
            int left=control==btn_lock?playerSafeLeft:playerDp(4);
            cp.leftMargin=left;if(Build.VERSION.SDK_INT>=17)cp.setMarginStart(left);
            if(control==btn_lock)cp.addRule(RelativeLayout.CENTER_VERTICAL,RelativeLayout.TRUE);
            else if(control==btn_debug)cp.addRule(RelativeLayout.ABOVE,R.id.text_speed);
            control.setLayoutParams(cp);
        }
        positionPlaybackSpeedLabel();
        roundSideControlsPositioned=false;
    }

    private void fitRoundToolbarButtons(int height) {
        ImageButton[] buttons = {btn_danmaku, btn_rotate, btn_menu, btn_collection_order};
        int visible = 0;
        for (ImageButton button : buttons) if (button.getVisibility() == View.VISIBLE) visible++;
        // Condense the rail when chapters take part of the screen. Keep it
        // scrollable if the available space would make the controls too small.
        int maxButtonHeight = height > 0 && visible > 0
                ? Math.max(playerDp(22), height / visible) : Integer.MAX_VALUE;
        for (ImageButton button : buttons) {
            ViewGroup.LayoutParams params = button.getLayoutParams();
            int normalHeight = params.width;
            int fittedHeight = Math.min(normalHeight, maxButtonHeight);
            if (params.height != fittedHeight) {
                params.height = fittedHeight;
                button.setLayoutParams(params);
            }
            if (button != btn_collection_order) {
                button.setScaleType(fittedHeight < normalHeight ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.FIT_XY);
            }
        }
    }

    private void updateExpandedMenuBounds() {
        if(layout_control==null||destroyed||!menu_opened)return;
        layout_control.removeCallbacks(expandedMenuBoundsUpdate);
        layout_control.post(expandedMenuBoundsUpdate);
    }
    private void applyExpandedMenuBounds() {
        if (right_second == null || !menu_opened || screenLocked || layout_control.getWidth() <= 0) return;
        int available = expandedMenuAvailableWidth();
        if (available < playerDp(32)||compactPlayerToolsNeeded()) {
            right_second.setVisibility(View.GONE);
            menu_opened = false;
            btn_menu.setImageResource(R.mipmap.morehide);
            layout_control.post(() -> { if (!destroyed && !screenLocked) showCompactPlayerTools(); });
            return;
        }
        View content = findViewById(R.id.right_second_container);
        content.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        android.widget.LinearLayout.LayoutParams params = (android.widget.LinearLayout.LayoutParams)right_second.getLayoutParams();
        int width = Math.min(available, content.getMeasuredWidth());
        int[] menuPosition=new int[2],parentPosition=new int[2];
        btn_menu.getLocationInWindow(menuPosition);right_control.getLocationInWindow(parentPosition);
        float center=menuPosition[1]-parentPosition[1]+btn_menu.getHeight()/2f;
        float parentCenter=right_control.getPaddingTop()
                +(right_control.getHeight()-right_control.getPaddingTop()-right_control.getPaddingBottom())/2f;
        int topMargin=Math.round(2*(center-parentCenter));
        if (params.width != width||params.topMargin!=topMargin||params.bottomMargin!=0) {
            params.width=width;params.topMargin=topMargin;params.bottomMargin=0;right_second.setLayoutParams(params);
        }
    }

    private void showCompactPlayerTools() {
        if (screenLocked || destroyed) return;
        if (playbackOptionsMenu != null) playbackOptionsMenu.dismiss();
        layout_control.removeCallbacks(hidecon);
        playbackOptionsMenu = new com.RobinNotBad.BiliClient.player.PlayerOptionsMenu(this, btn_menu);
        playbackOptionsMenu.setOnDismissListener(this::autohideReset);
        int[] ids = {R.id.danmaku_send_btn, R.id.subtitle_btn, R.id.loop_btn, R.id.audio_only_btn,
                R.id.auto_next_btn, R.id.viewpoint_btn, R.id.seek_back_btn, R.id.seek_forward_btn,R.id.danmaku_btn,R.id.rotate_btn,
                R.id.collection_order_btn,R.id.button_quality};
        int seconds = com.RobinNotBad.BiliClient.player.PlaybackVolume.seekSeconds(SharedPreferencesUtil.getInt(
                com.RobinNotBad.BiliClient.player.PlaybackVolume.SEEK_SECONDS, 10));
        String[] labels = {"发送弹幕", "字幕", "循环播放", "听视频模式", "分P自动连播", "视频看点", "快退"+seconds+"秒", "快进"+seconds+"秒","弹幕开关","旋转画面",
                String.valueOf(btn_collection_order.getContentDescription()),"画质与连播设置"};
        for (int i = 0; i < ids.length; i++) if (findViewById(ids[i]).getVisibility() == View.VISIBLE)
            playbackOptionsMenu.getMenu().add(0, 100 + i, i, labels[i]);
        playbackOptionsMenu.setOnMenuItemClickListener(item -> {
            int index = item.getItemId() - 100;
            if (!screenLocked && index >= 0 && index < ids.length) findViewById(ids[index]).performClick();
            return true;
        });
        playbackOptionsMenu.show();
    }

    private void showPlaybackOptions() {
        if (playbackOptionsMenu != null && playbackOptionsMenu.isShowing()) return;
        layout_control.removeCallbacks(hidecon);
        playbackOptionsMenu = new com.RobinNotBad.BiliClient.player.PlayerOptionsMenu(this, btn_quality);
        playbackOptionsMenu.setOnDismissListener(this::autohideReset);
        if (isOnlineVideo && SharedPreferencesUtil.getBoolean("player_ui_showQualityBtn", true)) {
            playbackOptionsMenu.getMenu().add(0, 1, 0, R.string.collection_playback_quality)
                    .setEnabled(!playbackSwitchInFlight);
        }
        int label = collectionQueueLoading ? R.string.collection_playback_loading
                : collectionQueue != null ? explicitOnlineQueue ? R.string.uploader_playback_menu : R.string.collection_playback
                : collectionQueueFailed ? R.string.collection_playback_retry : R.string.collection_playback_unavailable;
        playbackOptionsMenu.getMenu().add(0, 2, 1, label)
                .setCheckable(collectionQueue != null).setChecked(collectionPlaybackActive())
                .setEnabled(!collectionQueueLoading && (collectionQueue != null || collectionQueueFailed));
        if (favoritePageFailed && collectionPlaybackActive())
            playbackOptionsMenu.getMenu().add(0, 3, 2, "重试加载" + onlineQueueLabel() + "列表").setEnabled(!favoritePaging);
        playbackOptionsMenu.setOnMenuItemClickListener(item -> {
            if (screenLocked) return true;
            if (item.getItemId() == 1) showQualitySelectorCard();
            else if (item.getItemId() == 3) requestFavoriteAdvance(favoriteRetryAction);
            else if (collectionQueue == null) loadCollectionQueue();
            else {
                collectionPlaybackEnabled = !collectionPlaybackEnabled;
                if (explicitOnlineQueue) explicitOnlineEnabled = collectionPlaybackEnabled;
                else SharedPreferencesUtil.putBoolean(SharedPreferencesUtil.PLAYER_COLLECTION_PLAYBACK_ENABLED,
                            collectionPlaybackEnabled);
                if (!collectionPlaybackEnabled) cancelFavoriteLoading();
                if (!collectionPlaybackEnabled && collectionSwitchInFlight) {
                    playbackSwitchGeneration++;
                    playbackSwitchInFlight = collectionSwitchInFlight = false;
                }
                updateCollectionPlaybackControls();
                MsgUtil.showMsg(getString(collectionPlaybackEnabled
                        ? explicitOnlineQueue ? R.string.uploader_playback_on : R.string.collection_playback_on
                        : explicitOnlineQueue ? R.string.uploader_playback_off : R.string.collection_playback_off));
            }
            return true;
        });
        playbackOptionsMenu.show();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if (isOnlineVideo && !isLiveMode && !bangumiPlayback && isPrepared && ijkPlayer != null)
            playlistResumePoints.remember(aid, cid, Math.max(0L, ijkPlayer.getCurrentPosition()), video_all, finishWatching);
        state.putLong("playlist_resume_account", SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0));
        state.putStringArray("playlist_resume_keys", playlistResumePoints.keys());
        state.putLongArray("playlist_resume_values", playlistResumePoints.values());
        state.putInt("collection_order", collectionPlaybackOrder);
        state.putInt("manual_playback_quality",manualPlaybackQuality);
        state.putBoolean("explicit_online_enabled", explicitOnlineEnabled);
        state.putBoolean("cached_user_audio_only", cachedUserAudioOnlyMode);
        state.putLong("audio_sleep_deadline",audioSleepDeadline);
        if (collectionQueue != null && favoritePlaybackToken == null) {
            state.putLong("collection_id", collectionQueue.collectionId);
            state.putLongArray("collection_aids", collectionQueue.toArray());
        }
    }

    private void reportFinishedCollectionItem(long oldAid, long oldCid, long positionSeconds) {
        if (oldAid <= 0 || oldCid <= 0) return;
        CenterThreadPool.run(() -> {
            try { collectionPlaybackSource.reportHistory(oldAid, oldCid, positionSeconds); }
            catch (Exception error) { Logu.w("collection-history", "进度上报失败：" + error.getClass().getSimpleName()); }
        });
    }

    private void toggleAutoNext() {
        auto_next_enabled = !auto_next_enabled;
        updateAutoNextButton();
        MsgUtil.showMsg(auto_next_enabled ? "已开启自动连播" : "已关闭自动连播");
    }

    private void updateAutoNextButton() {
        if (btn_auto_next != null) {
            btn_auto_next
                    .setImageResource(auto_next_enabled ? R.drawable.icon_auto_next_on : R.drawable.icon_auto_next_off);
        }
    }

    private void showQualitySelectorCard() {
        if (qnStrList == null || qnValueList == null || qnStrList.length == 0) {
            recordPlayerAction("quality_list_empty", null, currentQuality);
            MsgUtil.showMsg("清晰度列表未加载");
            return;
        }

        recordPlayerAction("quality_list_open", null, currentQuality);

        runOnUiThread(() -> {
            RecyclerView qualitySelectorRecycler = findViewById(R.id.quality_selector_list);
            QualitySelectorAdapter adapter = new QualitySelectorAdapter();
            adapter.setData(qnStrList, qnValueList, currentQuality);
            adapter.setOnItemClickListener(index -> {
                layout_card_bg.setVisibility(View.GONE);
                card_quality_selector.setVisibility(View.GONE);
                if (index >= 0 && index < qnValueList.length) {
                    if(qnValueList[index]==currentQuality)manualPlaybackQuality=currentQuality;
                    else switchQuality(qnValueList[index]);
                }
            });
            qualitySelectorRecycler
                    .setLayoutManager(new CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false));
            qualitySelectorRecycler.setAdapter(adapter);
            layout_card_bg.setVisibility(View.VISIBLE);
            card_quality_selector.setVisibility(View.VISIBLE);
        });
    }

    private void switchQuality(int newQuality) {
        if (playbackSwitchInFlight) return;
        if (!isOnlineVideo || newQuality == currentQuality) {
            return;
        }

        recordPlayerAction("quality_switch_requested", null, newQuality);
        MsgUtil.showMsg("正在切换清晰度...");

        CenterThreadPool.run(() -> {
            try {
                PlayerData playerData = new PlayerData();
                playerData.aid = aid;
                playerData.cid = cid;
                playerData.title = text_title.getText().toString();
                playerData.mid = mid;
                playerData.qn = newQuality;
                playerData.pagenames = pagenames;
                playerData.cids = cids;
                playerData.currentPageIndex = currentPageIndex;
                playerData.type = bangumiPlayback ? PlayerData.TYPE_BANGUMI : PlayerData.TYPE_VIDEO;
                playerData.seasonId = pgcSeason; playerData.episodeId = pgcEpisode; playerData.seasonType = pgcType;
                if (bangumiPlayback) PlayerApi.getBangumi(playerData);
                else PlayerApi.getVideoForPlayback(playerData);

                runOnUiThread(() -> {
                    if (destroyed)
                        return;

                    final long currentPosition = ijkPlayer != null ? ijkPlayer.getCurrentPosition() : 0;
                    final boolean wasPlaying = isPlaying;

                    releasePlaybackPlayers();

                    setPlaybackUrls(playerData.videoUrl, playerData.videoBackupUrls);
                    setDashAudioUrls(playerData.audioUrl, playerData.audioBackupUrls);
                    currentQuality = playerData.qn;
                    manualPlaybackQuality=newQuality;
                    if (bangumiPlayback && segmentPolicy != null) {
                        segmentPolicy.update(playerData.clipSegments);
                        getIntent().putExtra("clip_segments", playerData.clipSegments);
                    }
                    recordPlayerAction("quality_source_ready", null, currentQuality);

                    if (playerData.qnStrList != null && playerData.qnValueList != null) {
                        qnStrList = playerData.qnStrList;
                        qnValueList = playerData.qnValueList;
                    }

                    loading_info.setVisibility(View.VISIBLE);
                    anim_loading.start();
                    loading_text0.setText("切换清晰度中");
                    isPrepared = false;
                    isPlaying = false;
                    resumeAfterPrepare = wasPlaying;

                    ijkPlayer = createMediaPlayer();
                    progress_history = currentPosition;

                    setDisplay();
                });
            } catch (Exception e) {
                recordPlayerAction("quality_switch_failed", e.getClass().getSimpleName(), newQuality);
                runOnUiThread(() -> {
                    MsgUtil.err(e);
                    MsgUtil.showMsg("清晰度切换失败");
                });
            }
        });
    }

    private List<ViewPoint> viewPoints;
    private ViewPointAdapter viewPointAdapter;

    private void loadViewPoints() {
        final long requestedAid = aid, requestedCid = cid;
        CenterThreadPool.run(() -> {
            try {
                Logu.d("视频分段", "开始加载分段数据 aid=" + aid + " cid=" + cid);
                List<ViewPoint> points = PlayerApi.getViewPoints(requestedAid, requestedCid);
                runOnUiThread(() -> {
                    if (destroyed || requestedAid != aid || requestedCid != cid) return;
                    viewPoints = points;
                    if (infoOverlay != null) infoOverlay.setChapters(points);
                    if (viewPointAdapter != null) viewPointAdapter.setData(points);
                    btn_viewpoint.setVisibility(points != null && !points.isEmpty() ? View.VISIBLE : View.GONE);
                    btn_viewpoint.setOnClickListener(view -> showViewPointSelectorCard());
                    updatePlayerInfoOverlay();
                });
            } catch (Exception e) {
                Logu.e("视频分段", "加载失败: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void showViewPointSelectorCard() {
        if (viewPoints == null || viewPoints.isEmpty())
            return;

        runOnUiThread(() -> {
            RecyclerView viewPointRecycler = findViewById(R.id.viewpoint_selector_list);
            if (viewPointAdapter == null) {
                viewPointAdapter = new ViewPointAdapter();
                viewPointAdapter.setData(viewPoints);
                viewPointAdapter.setOnItemClickListener(index -> {
                    layout_card_bg.setVisibility(View.GONE);
                    card_viewpoint_selector.setVisibility(View.GONE);
                    if (index >= 0 && index < viewPoints.size()) {
                        ViewPoint vp = viewPoints.get(index);
                        seekToPosition(vp.from * 1000L);
                        MsgUtil.showMsg("跳转到: " + vp.content);
                    }
                });
                viewPointRecycler.setLayoutManager(new CustomLinearManager(this, LinearLayoutManager.HORIZONTAL, false));
                viewPointRecycler.setAdapter(viewPointAdapter);
            }
            if (ijkPlayer != null && isPrepared) {
                int currentPos = (int) (ijkPlayer.getCurrentPosition() / 1000);
                viewPointAdapter.updateCurrentPosition(currentPos);
            }
            layout_card_bg.setVisibility(View.VISIBLE);
            card_viewpoint_selector.setVisibility(View.VISIBLE);
        });
    }





    // 再往下就是互动视频的天下了

    private void loadInteractionVideo() {
        final long requestedAid = aid, requestedCid = cid;
        CenterThreadPool.run(() -> {
            try {
                long graphVersion = PlayerApi.getInteractionGraphVersion(requestedAid, requestedCid);
                if (destroyed || aid != requestedAid || cid != requestedCid) return;
                if (graphVersion > 0) {
                    interactionGraphVersion = graphVersion;
                    Logu.d("互动视频", "检测到互动视频，graph_version: " + interactionGraphVersion + ", cid: " + cid);
                    
                    runOnUiThread(() -> {
                        if (destroyed || aid != requestedAid || cid != requestedCid) return;
                        questionShown = false;
                        currentQuestion = null;
                        if (interactionChoiceLayout != null) {
                            interactionChoiceLayout.setVisibility(View.GONE);
                            interactionChoiceLayout.removeAllViews();
                        }
                    });
                    
                    long edgeId = 0;
                    if (initialEdgeId > 0) {
                        edgeId = initialEdgeId;
                        initialEdgeId = 0;
                    } else if (interactionRestoreSnapshot != null
                            && interactionRestoreSnapshot.graphVersion == interactionGraphVersion
                            && interactionRestoreSnapshot.edgeId > 0L) {
                        edgeId = interactionRestoreSnapshot.edgeId;
                    } else if (interactionData != null && currentEdgeId > 0) {
                        edgeId = currentEdgeId;
                    }
                    
                    InteractionVideoData loadedData = InteractionVideoApi.getEdgeInfo(requestedAid, null, graphVersion, edgeId);
                    if (destroyed || aid != requestedAid || cid != requestedCid) return;
                    interactionData = loadedData;
                    if (interactionData != null) {
                        currentEdgeId = interactionData.edgeId;
                        if (interactionRestoreSnapshot != null
                                && interactionRestoreSnapshot.graphVersion == interactionGraphVersion) {
                            InteractionProgressStore.apply(interactionRestoreSnapshot, interactionData);
                            if (interactionRestoreSnapshot.cid == cid
                                    && interactionRestoreSnapshot.positionMs > 0L) {
                                progress_history = interactionRestoreSnapshot.positionMs;
                            }
                            long restoreCid = interactionRestoreSnapshot.cid;
                            if (restoreCid > 0L && restoreCid != cid) {
                                jumpToInteractionPage(restoreCid, interactionData);
                            }
                        }
                        Logu.d("互动视频", "成功加载互动视频数据，edge_id: " + currentEdgeId);
                        runOnUiThread(() -> updateDebugButtonVisibility());
                    }
                } else {
                    interactionData = null;
                    currentEdgeId = 0;
                    runOnUiThread(() -> {
                        questionShown = false;
                        currentQuestion = null;
                        updateDebugButtonVisibility();
                    });
                }
            } catch (Exception e) {
                if (destroyed || aid != requestedAid || cid != requestedCid) return;
                Logu.e("互动视频", "加载失败: " + e.getMessage());
                e.printStackTrace();
                interactionData = null;
                currentEdgeId = 0;
                runOnUiThread(() -> {
                    questionShown = false;
                    currentQuestion = null;
                    updateDebugButtonVisibility();
                });
            }
        });
    }

    private void updateDebugButtonVisibility() {
        updatePlayerInfoOverlay();
        if (btn_debug == null) return;
        boolean debugEnabled = SharedPreferencesUtil.getBoolean("player_interaction_debug", false);
        if (debugEnabled && interactionData != null && interactionData.hiddenVars != null && !interactionData.hiddenVars.isEmpty() && !isLiveMode && !isAudioOnlyMode) {
            btn_debug.setVisibility(layout_top.getVisibility());
        } else {
            btn_debug.setVisibility(View.GONE);
        }
    }

    private void updatePlayerInfoOverlay() {
        if (infoOverlay != null && layout_top != null) infoOverlay.show(
                !screenLocked && layout_top.getVisibility() == View.VISIBLE,
                !isLiveMode && !isAudioOnlyMode, interactionData,
                SharedPreferencesUtil.getBoolean("player_show_viewpoints", true));
        updateAudioDashboard();
        updateRightToolbarBounds();
    }

    private void checkEndInteractionQuestions() {
        if (interactionData == null || interactionData.edges == null || 
            interactionData.edges.questions == null || questionShown) {
            return;
        }

        for (InteractionVideoData.InteractionQuestion question : interactionData.edges.questions) {
            if (question.type == 0) {
                if (question.choices != null && !question.choices.isEmpty()) {
                    for (InteractionVideoData.InteractionChoice choice : question.choices) {
                        if (choice.isHidden == 1) continue;
                        
                        if (choice.condition != null && !choice.condition.isEmpty()) {
                            if (!evaluateCondition(choice.condition)) {
                                continue;
                            }
                        }
                        
                        handleChoiceSelection(choice);
                        break;
                    }
                }
                continue;
            }
            showInteractionQuestion(question);
        }
    }

    private void showInteractionQuestion(InteractionVideoData.InteractionQuestion question) {
        if (questionShown || question.choices == null || question.choices.isEmpty()) {
            return;
        }

        runOnUiThread(() -> {
            questionShown = true;
            currentQuestion = question;

            if (question.pauseVideo == 1 && isPlaying) {
                playerPause();
            }

            if (interactionChoiceLayout == null) {
                createInteractionChoiceLayout();
            }

            interactionChoiceLayout.removeAllViews();
            
            for (InteractionVideoData.InteractionChoice choice : question.choices) {
                if (choice.isHidden == 1) continue;
                
                if (choice.condition != null && !choice.condition.isEmpty()) {
                    if (!evaluateCondition(choice.condition)) {
                        continue;
                    }
                }

                TextView choiceView = createChoiceView(choice);
                interactionChoiceLayout.addView(choiceView);
            }

            if (interactionChoiceLayout.getChildCount() > 0) {
                interactionChoiceLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private TextView createChoiceView(InteractionVideoData.InteractionChoice choice) {
        TextView choiceView = (TextView) LayoutInflater.from(this).inflate(R.layout.cell_interaction_choice, null);
        float scale = PlayerSettingsUtil.getInteractionChoiceScale();
        choiceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * scale);
        choiceView.setPadding(
                dp((int) (28f * scale)),
                dp((int) (18f * scale)),
                dp((int) (28f * scale)),
                dp((int) (18f * scale)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = dp((int) (6f * scale));
        params.setMargins(dp((int) (20f * scale)), margin, dp((int) (20f * scale)), margin);
        choiceView.setLayoutParams(params);
        choiceView.setText(choice.option);
        choiceView.setOnClickListener(v -> handleChoiceSelection(choice));
        return choiceView;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void createInteractionChoiceLayout() {
        RelativeLayout rootLayout = findViewById(R.id.root_layout);
        interactionChoiceLayout = new LinearLayout(this);
        interactionChoiceLayout.setOrientation(LinearLayout.VERTICAL);
        interactionChoiceLayout.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.setMargins(0, 0, 0, 100);
        
        interactionChoiceLayout.setLayoutParams(params);
        interactionChoiceLayout.setVisibility(View.GONE);
        rootLayout.addView(interactionChoiceLayout);
    }

    private boolean evaluateCondition(String condition) {
        if (interactionData == null || interactionData.hiddenVars == null) {
            return true;
        }
        
        try {
            for (InteractionVideoData.InteractionHiddenVar var : interactionData.hiddenVars) {
                condition = condition.replace(var.idV2, String.valueOf(var.value));
            }
            
            return evaluateExpression(condition);
        } catch (Exception e) {
            Logu.e("互动视频", "条件判断失败: " + e.getMessage());
            return true;
        }
    }

    private boolean evaluateExpression(String expr) {
        try {
            expr = expr.trim();
            if (expr.contains(">=")) {
                String[] parts = expr.split(">=");
                long left = Long.parseLong(parts[0].trim());
                long right = Long.parseLong(parts[1].trim());
                return left >= right;
            } else if (expr.contains("<=")) {
                String[] parts = expr.split("<=");
                long left = Long.parseLong(parts[0].trim());
                long right = Long.parseLong(parts[1].trim());
                return left <= right;
            } else if (expr.contains(">")) {
                String[] parts = expr.split(">");
                long left = Long.parseLong(parts[0].trim());
                long right = Long.parseLong(parts[1].trim());
                return left > right;
            } else if (expr.contains("<")) {
                String[] parts = expr.split("<");
                long left = Long.parseLong(parts[0].trim());
                long right = Long.parseLong(parts[1].trim());
                return left < right;
            } else if (expr.contains("==")) {
                String[] parts = expr.split("==");
                long left = Long.parseLong(parts[0].trim());
                long right = Long.parseLong(parts[1].trim());
                return left == right;
            } else if (expr.contains("!=")) {
                String[] parts = expr.split("!=");
                long left = Long.parseLong(parts[0].trim());
                long right = Long.parseLong(parts[1].trim());
                return left != right;
            }
        } catch (Exception e) {
            Logu.e("互动视频", "表达式计算失败: " + expr);
        }
        return true;
    }

    private void handleChoiceSelection(InteractionVideoData.InteractionChoice choice) {
        hideInteractionChoices();

        if (choice.nativeAction != null && !choice.nativeAction.isEmpty()) {
            executeNativeAction(choice.nativeAction);
        }
        saveInteractionProgress(true);

        CenterThreadPool.run(() -> {
            try {
                long targetEdgeId = choice.id;
                InteractionVideoData newData = InteractionVideoApi.getEdgeInfo(aid, null, interactionGraphVersion, targetEdgeId);
                
                if (newData == null) {
                    runOnUiThread(() -> MsgUtil.showMsg("获取互动视频数据失败"));
                    return;
                }

                interactionData = newData;
                currentEdgeId = newData.edgeId;
                InteractionProgressStore.apply(interactionRestoreSnapshot, newData);
                saveInteractionProgress(true);
                runOnUiThread(this::updatePlayerInfoOverlay);
                
                long targetCid = choice.cid;
                if (targetCid > 0 && targetCid != cid) {
                    jumpToInteractionPage(targetCid, newData);
                } else {
                    resumePlaybackIfPaused();
                }
            } catch (Exception e) {
                Logu.e("互动视频", "处理选择失败: " + e.getMessage());
                runOnUiThread(() -> MsgUtil.showMsg("处理选择失败: " + e.getMessage()));
            }
        });
    }

    private void hideInteractionChoices() {
        runOnUiThread(() -> {
            if (interactionChoiceLayout != null) {
                interactionChoiceLayout.setVisibility(View.GONE);
            }
            questionShown = false;
            currentQuestion = null;
        });
    }

    private void jumpToInteractionPage(long targetCid, InteractionVideoData newData) {
        CenterThreadPool.run(() -> {
            try {
                PlayerData playerData = new PlayerData();
                playerData.aid = aid;
                playerData.cid = targetCid;
                playerData.title = newData.title;
                playerData.mid = mid;
                playerData.qn = getTargetQuality();
                
                if (pagenames != null && cids != null) {
                    playerData.pagenames = pagenames;
                    playerData.cids = cids;
                    int newPageIndex = cids.indexOf(targetCid);
                    if (newPageIndex >= 0) {
                        playerData.currentPageIndex = newPageIndex;
                        currentPageIndex = newPageIndex;
                    }
                }
                
                PlayerApi.getVideoForPlayback(playerData);
                
                runOnUiThread(() -> {
                    if (destroyed)
                        return;
                    
                    releasePlaybackPlayers();
                    if (mDanmakuView != null) {
                        mDanmakuView.release();
                        mDanmakuView = null;
                    }
                    
                    cid = targetCid;
                    setPlaybackUrls(playerData.videoUrl, playerData.videoBackupUrls);
                    setDashAudioUrls(playerData.audioUrl, playerData.audioBackupUrls);
                    danmaku_url = playerData.danmakuUrl;
                    resetDanmakuSource(knownDanmakuCount);
                    text_title.setText(newData.title);
                    videoTitle = newData.title;
                    currentEdgeId = newData.edgeId;
                    
                    if (playerData.qnStrList != null && playerData.qnValueList != null) {
                        qnStrList = playerData.qnStrList;
                        qnValueList = playerData.qnValueList;
                        currentQuality = playerData.qn;
                    }
                    
                    loading_info.setVisibility(View.VISIBLE);
                    anim_loading.start();
                    loading_text0.setText("加载互动分P");
                    isPrepared = false;
                    isPlaying = false;
                    resumeAfterPrepare = true;
                    finishWatching = false;
                    progress_history = 0;
                    subtitles = null;
                    subtitleLinks = null;
                    subtitle_selected = -1;
                    viewPoints = null;
                    viewPointAdapter = null;
                    if (btn_viewpoint != null) {
                        btn_viewpoint.setVisibility(View.GONE);
                    }
                    
                    interactionData = newData;
                    if (infoOverlay != null) infoOverlay.setChapters(null);
                    updatePlayerInfoOverlay();
                    if (interactionRestoreSnapshot != null
                            && interactionRestoreSnapshot.cid == targetCid
                            && interactionRestoreSnapshot.graphVersion == interactionGraphVersion) {
                        progress_history = interactionRestoreSnapshot.positionMs;
                    }
                    InteractionProgressStore.apply(interactionRestoreSnapshot, interactionData);
                    saveInteractionProgress(true);
                    currentQuestion = null;
                    questionShown = false;
                    if (interactionChoiceLayout != null) {
                        interactionChoiceLayout.setVisibility(View.GONE);
                        interactionChoiceLayout.removeAllViews();
                    }
                    
                    ijkPlayer = createMediaPlayer();
                    mDanmakuView = findViewById(R.id.sv_danmaku);
                    
                    setDisplay();
                    
                    layout_control.postDelayed(() -> CenterThreadPool.run(() -> {
                        if (destroyed)
                            return;
                        
                        runOnUiThread(() -> {
                            loading_text0.setText("装填弹幕中");
                            loading_text1.setText("(≧∇≦)");
                        });
                        
                        if (isOnlineVideo) {
                            danmakuFile = new File(getCacheDir(), "danmaku.xml");
                            if (danmakuFile.exists()) {
                                danmakuFile.delete();
                            }
                            downdanmu();
                        }
                        
                        if (!destroyed && SharedPreferencesUtil.getBoolean("player_subtitle_autoshow", true)) {
                            downSubtitle(false);
                        }
                        
                        if (!destroyed && isOnlineVideo && aid > 0 && cid > 0) {
                            loadHighEnergyData();
                        }
                        
                        if (!destroyed && isOnlineVideo && aid > 0 && cid > 0 && SharedPreferencesUtil.getBoolean("player_show_viewpoints", true)) {
                            loadViewPoints();
                        }
                    }), 60);
                });
            } catch (Exception e) {
                Logu.e("互动视频", "跳转失败: " + e.getMessage());
                runOnUiThread(() -> MsgUtil.showMsg("跳转失败: " + e.getMessage()));
            }
        });
    }

    private void resumePlaybackIfPaused() {
        runOnUiThread(() -> {
            if (currentQuestion != null && currentQuestion.pauseVideo == 1 && !isPlaying) {
                playerResume();
            }
        });
    }

    private void saveInteractionProgress(boolean force) {
        if (interactionData == null || interactionGraphVersion <= 0L || aid <= 0L || isLiveMode) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastInteractionSaveAt < 1000L) return;
        long position = progress_history;
        if (ijkPlayer != null && isPrepared) {
            try {
                position = Math.max(0L, ijkPlayer.getCurrentPosition());
            } catch (RuntimeException ignored) {
            }
        }
        InteractionProgressStore.save(aid, interactionGraphVersion, currentEdgeId,
                cid, position, interactionData.hiddenVars);
        interactionRestoreSnapshot = InteractionProgressStore.load(aid);
        lastInteractionSaveAt = now;
    }

    private int getTargetQuality() {
        int defaultQn = com.RobinNotBad.BiliClient.util.NetworkPlaybackQuality.requestedQuality(this,manualPlaybackQuality);
        if (qnValueList == null || qnValueList.length == 0) {
            return currentQuality > 0 ? currentQuality : defaultQn;
        }
        
        for (int qn : qnValueList) {
            if (qn == currentQuality) {
                return currentQuality;
            }
        }
        
        return currentQuality > 0 ? currentQuality : defaultQn;
    }

    private void executeNativeAction(String nativeAction) {
        if (interactionData == null || interactionData.hiddenVars == null || nativeAction == null || nativeAction.isEmpty()) {
            return;
        }

        String[] actions = nativeAction.split(";");
        for (String action : actions) {
            action = action.trim();
            if (action.isEmpty()) continue;

            try {
                if (action.contains("=")) {
                    String[] parts = action.split("=");
                    if (parts.length == 2) {
                        String varId = parts[0].trim();
                        String valueExpr = parts[1].trim();
                        
                        long value = evaluateValueExpression(valueExpr);
                        
                        for (InteractionVideoData.InteractionHiddenVar var : interactionData.hiddenVars) {
                            if (var.idV2.equals(varId)) {
                                var.value = value;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Logu.e("互动视频", "执行动作失败: " + action);
            }
        }
        runOnUiThread(this::updatePlayerInfoOverlay);
    }

    private long evaluateValueExpression(String expr) {
        try {
            expr = expr.trim();
            if (expr.contains("+")) {
                String[] parts = expr.split("\\+");
                long sum = 0;
                for (String part : parts) {
                    sum += evaluateValueExpression(part.trim());
                }
                return sum;
            } else if (expr.contains("-")) {
                String[] parts = expr.split("-");
                long result = evaluateValueExpression(parts[0].trim());
                for (int i = 1; i < parts.length; i++) {
                    result -= evaluateValueExpression(parts[i].trim());
                }
                return result;
            } else {
                if (interactionData != null && interactionData.hiddenVars != null) {
                    for (InteractionVideoData.InteractionHiddenVar var : interactionData.hiddenVars) {
                        if (expr.equals(var.idV2)) {
                            return var.value;
                        }
                    }
                }
                if (expr.contains(".")) {
                    return (long) Double.parseDouble(expr);
                } else {
                    return Long.parseLong(expr);
                }
            }
        } catch (Exception e) {
            Logu.e("互动视频", "值表达式计算失败: " + expr + ", 错误: " + e.getMessage());
            return 0;
        }
    }

    private void showInteractionDebugDialog() {
        if (interactionData == null || interactionData.hiddenVars == null || interactionData.hiddenVars.isEmpty()) {
            MsgUtil.showMsg("当前没有互动视频变量");
            return;
        }

        InteractionDebugActivity.setInteractionData(interactionData);
        Intent intent = new Intent(this, InteractionDebugActivity.class);
        backgroundTransitionSuppressed = true;
        startActivity(intent);
    }

    @Override
    public void finish() {
        if (isPlaying)
            playerPause();
        saveInteractionProgress(true);
        if (ijkPlayer != null) {
            saveLocalPlaybackProgress(Math.max(0L, ijkPlayer.getCurrentPosition()));
            Intent result = new Intent();
            result.putExtra("progress", (int) ijkPlayer.getCurrentPosition());
            result.putExtra("aid", aid);
            result.putExtra("cid", cid);
            result.putExtra(BiliTerminal.RETURNING_HOME, getIntent().getBooleanExtra(BiliTerminal.RETURNING_HOME, false));
            result.putExtra("online_video", isOnlineVideo && !isLiveMode && !bangumiPlayback);
            Logu.d("进度回传", String.valueOf(ijkPlayer.getCurrentPosition()));
            setResult(RESULT_OK, result);
        } else
            setResult(RESULT_CANCELED);
        super.finish();
    }
}
