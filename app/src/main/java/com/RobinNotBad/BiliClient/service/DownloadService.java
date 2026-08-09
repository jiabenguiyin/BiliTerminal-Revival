package com.RobinNotBad.BiliClient.service;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.InstanceActivity;
import com.RobinNotBad.BiliClient.activity.video.local.DownloadListActivity;
import com.RobinNotBad.BiliClient.activity.video.local.LocalListActivity;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.helper.sql.DownloadSqlHelper;
import com.RobinNotBad.BiliClient.model.DownloadSection;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.SubtitleLink;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Inflater;

import okhttp3.Response;

public class DownloadService extends Service {
    public static volatile boolean started;
    public static volatile int exitCode;
    public static volatile float percent = -1;
    public static volatile String state;
    public static volatile DownloadSection section;
    private static long firstDown = -1;
    private static volatile boolean pauseRequested;
    private static volatile long deleteRequestedId = -1;
    private volatile boolean workerRunning;

    final String NOTIFICATION_CHANNEL_ID = "biliterminal_download";
    final int FOREGROUND_ID = 1027;
    NotificationCompat.Builder statusBuilder, completionBuilder;
    NotificationManager notifyManager;
    PowerManager.WakeLock wakeLock;

    private String exitMessage = null;

    private Timer toastTimer, notifyTimer;

    private static final int NORMAL = 0;
    private static final int ERR_NETWORK = -1;
    private static final int ERR_JSON = -2;
    private static final int ERR_FILE = -3;
    private static final int ERR_DATABASE = -4;
    private static final int ERR_UNKNOWN = -7;
    private static final int EXIT_PAUSED = 1;
    private static final int EXIT_DELETED = 2;

    public DownloadService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Logu.d("onCreate");

        notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BiliClient:DownloadService");
            wakeLock.setReferenceCounted(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "哔哩终端下载服务",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("哔哩终端下载服务");
            channel.setSound(null, null);
            channel.enableVibration(false);

            notifyManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, DownloadListActivity.class);
        @SuppressLint("UnspecifiedImmutableFlag")
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        statusBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.icon)
                .setContentTitle("下载视频中")
                .setProgress(100, 0, false)
                .setContentIntent(pendingIntent)
                .setSound(null)
                .setVibrate(null)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        completionBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.icon)
                .setContentTitle("下载完成")
                .setContentIntent(pendingIntent)
                .setOngoing(false)
                .setSound(null)
                .setVibrate(null)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("MutatingSharedPrefs")
    @Override
    public int onStartCommand(Intent serviceIntent, int flags, int startId) {
        Logu.d("onStartCommand");
        startForeground(FOREGROUND_ID, statusBuilder.build());
        acquireWakeLock();

        if (workerRunning) return Service.START_STICKY;

        started = true;
        workerRunning = true;
        pauseRequested = false;
        deleteRequestedId = -1;
        exitCode = ERR_UNKNOWN;
        startNotifyProgress();

        CenterThreadPool.run(() -> {
            resetInterruptedDownloads();
            boolean failed = false;
            while (!failed && started) {
                DownloadSection section_tmp = getFirst();
                if (section_tmp == null)
                    break;

                section = section_tmp;

                // 获取视频链接
                String url_video, url_danmaku, url_audio;
                try {
                    PlayerData data = section.toPlayerData();

                    // 如果是仅音频下载，使用DASH格式获取音频流
                    if (section.isAudioOnly()) {
                        PlayerApi.getVideoDash(data);
                        url_audio = section.audioUrl != null && !section.audioUrl.isEmpty()
                                ? section.audioUrl
                                : data.audioUrl;
                        url_video = null; // 仅音频模式不需要视频
                    } else {
                        PlayerApi.getVideo(data, true);
                        url_video = data.videoUrl;
                        url_audio = null;
                    }
                    url_danmaku = data.danmakuUrl;
                } catch (JSONException e) {
                    setState(section.id, "error");
                    notifyCompletion("下载链接获取失败：\n" + section.name_short, (int) section.id);
                    section = null;
                    refreshDownloadList();
                    continue;
                } catch (IOException e) {
                    failed = true;
                    exitCode = ERR_NETWORK;
                    setState(section.id, "none");
                    continue;
                }

                try {
                    setState(section.id, "downloading");
                    percent = 0;
                    refreshDownloadList();

                    VideoStorageUtil.Node fileSign = null;

                    int result;
                    VideoStorageUtil.Node path = section.getStoragePath(this, true);
                    if (path == null) {
                        failed = true;
                        exitCode = ERR_FILE;
                        continue;
                    }

                    switch (section.type) {
                        case "video_single":
                            fileSign = path.getOrCreateFile(".DOWNLOADING", "application/octet-stream");

                            toastState("下载封面");
                            result = downFile(section.url_cover, path.getOrCreateFile("cover.png", "image/png"));
                            if (result != NORMAL) {
                                failed = true;
                                exitCode = result;
                                continue;
                            }

                            if (!section.isAudioOnly()) {
                                toastState("下载字幕");
                                result = downSubtitles(section.aid, section.cid, path);
                                if (result != NORMAL) {
                                    failed = true;
                                    exitCode = result;
                                    continue;
                                }

                                toastState("下载弹幕");
                                result = downDanmaku(url_danmaku,
                                        path.getOrCreateFile("danmaku.xml", "text/xml"));
                                if (result != NORMAL) {
                                    failed = true;
                                    exitCode = result;
                                    continue;
                                }
                            }

                            if (section.isAudioOnly()) {
                                toastState("下载音频");
                                result = downFile(url_audio,
                                        path.getOrCreateFile("audio.m4a", "audio/mp4"));
                            } else {
                                toastState("下载视频");
                                result = downFile(url_video,
                                        path.getOrCreateFile("video.mp4", "video/mp4"));
                            }
                            if (result != NORMAL) {
                                failed = true;
                                exitCode = result;
                                continue;
                            }
                            break;

                        case "video_multi":
                            VideoStorageUtil.Node parent = VideoStorageUtil.taskDirectory(
                                    this, section.storageMode, section.storageRef, section.title, null, true);
                            fileSign = path.getOrCreateFile(".DOWNLOADING", "application/octet-stream");

                            toastState("下载封面");
                            VideoStorageUtil.Node cover = parent.getOrCreateFile("cover.png", "image/png");
                            if (cover.length() == 0L) {
                                result = downFile(section.url_cover, cover);
                                if (result != NORMAL) {
                                    failed = true;
                                    exitCode = result;
                                    continue;
                                }
                            }

                            if (!section.isAudioOnly()) {
                                toastState("下载字幕");
                                result = downSubtitles(section.aid, section.cid, path);
                                if (result != NORMAL) {
                                    failed = true;
                                    exitCode = result;
                                    continue;
                                }

                                toastState("下载弹幕");
                                result = downDanmaku(url_danmaku,
                                        path.getOrCreateFile("danmaku.xml", "text/xml"));
                                if (result != NORMAL) {
                                    failed = true;
                                    exitCode = result;
                                    continue;
                                }
                            }

                            if (section.isAudioOnly()) {
                                toastState("下载音频");
                                result = downFile(url_audio,
                                        path.getOrCreateFile("audio.m4a", "audio/mp4"));
                            } else {
                                toastState("下载视频");
                                result = downFile(url_video,
                                        path.getOrCreateFile("video.mp4", "video/mp4"));
                            }
                            if (result != NORMAL) {
                                failed = true;
                                exitCode = result;
                                continue;
                            }
                            break;
                    }

                    notifyCompletion("下载成功：\n" + section.name_short, (int) section.id);

                    if (fileSign != null) fileSign.deleteRecursive();
                    deleteSection(section.id);
                    refreshLocalList();
                    section = null;
                } catch (IOException e) {
                    failed = true;
                    exitCode = ERR_FILE;
                    setState(section.id, "error");
                } finally {
                    if (failed && section != null) {
                        if (exitCode == EXIT_DELETED) {
                            long deletedId = section.id;
                            VideoStorageUtil.Node deletedFolder = null;
                            try {
                                deletedFolder = section.getStoragePath(this, false);
                            } catch (IOException ignored) {
                            }
                            section = null;
                            deleteSection(deletedId);
                            if (deletedFolder != null) deletedFolder.deleteRecursive();
                        } else if (exitCode == ERR_NETWORK || exitCode == ERR_UNKNOWN
                                || exitCode == EXIT_PAUSED)
                            setState(section.id, "none");
                        else
                            setState(section.id, "error");
                        refreshDownloadList();
                    }
                }
            }

            section = null;
            refreshDownloadList();

            if (!failed) {
                exitCode = NORMAL;
                exitMessage = "全部下载完成";
            } else
                switch (exitCode) {
                    case ERR_NETWORK:
                        exitMessage = "下载失败，网络错误";
                        break;
                    case ERR_JSON:
                        exitMessage = "下载失败，视频链接获取错误";
                        break;
                    case ERR_FILE:
                        exitMessage = "下载失败，文件错误";
                        break;
                    case ERR_DATABASE:
                        exitMessage = "下载失败，数据库错误";
                        break;
                    case ERR_UNKNOWN:
                        exitMessage = "下载被系统中断，请重新点击继续";
                        break;
                    case EXIT_PAUSED:
                        exitMessage = "下载已暂停";
                        break;
                    case EXIT_DELETED:
                        exitMessage = "下载任务已删除";
                        break;
                    default:
                        exitMessage = "下载失败，未知错误";
                }

            workerRunning = false;
            stopSelf();
        });

        return Service.START_STICKY;
    }

    private void fakeDownload() {
        setState(section.id, "downloading");
        percent = 0;
        refreshDownloadList();
        try {
            Thread.sleep(3000);
        } catch (Exception ignored) {
        }

        deleteSection(section.id);
        refreshLocalList();
    }

    private void toastState(String newState) {
        state = newState;
        percent = 0;
        if (toastTimer != null)
            toastTimer.cancel();
    }

    private void startNotifyProgress() {
        notifyTimer = new Timer();
        notifyTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (section == null || notifyTimer == null)
                    return;

                statusBuilder.setContentText(state + "：" + section.name_short);
                statusBuilder.setProgress(100, (int) (percent * 100), false);
                notifyManager.notify(FOREGROUND_ID, statusBuilder.build());
            }
        }, 500, 500);
    }

    private void notifyExit(String content) {
        MsgUtil.showMsg(content);
        notifyManager.cancel(FOREGROUND_ID);
        completionBuilder.setContentTitle("下载结束");
        completionBuilder.setContentText(content);
        completionBuilder.setProgress(0, 0, false);
        notifyManager.notify(2, completionBuilder.build());
    }

    private void notifyCompletion(String content, int id) {
        MsgUtil.showMsg(content);
        completionBuilder.setContentText(content);
        notifyManager.notify(id % 100 + 100, completionBuilder.build());
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock != null && !wakeLock.isHeld())
                wakeLock.acquire(60 * 60 * 1000L);
        } catch (Exception e) {
            Logu.e("download", "wake lock acquire failed: " + e);
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld())
                wakeLock.release();
        } catch (Exception e) {
            Logu.e("download", "wake lock release failed: " + e);
        }
    }

    private void refreshDownloadList() {
        if (DownloadListActivity.weakRef != null && DownloadListActivity.weakRef.get() != null) {
            DownloadListActivity.weakRef.get().refreshList(true);
        }
    }

    private void refreshLocalList() {
        InstanceActivity instance = BiliTerminal.getInstanceActivityOnTop();
        if (instance instanceof LocalListActivity && !instance.isDestroyed())
            ((LocalListActivity) (instance)).refresh();
    }

    private int downSubtitles(long aid, long cid, VideoStorageUtil.Node folder) {
        try {
            SubtitleLink[] subtitleLinks = PlayerApi.getSubtitleLinks(aid, cid);
            if (subtitleLinks.length == 0) return NORMAL;

            VideoStorageUtil.Node subtitleFolder = folder.getOrCreateDirectory("subtitles");
            for (SubtitleLink subtitleLink : subtitleLinks) {
                if (subtitleLink.id != -1) {
                    VideoStorageUtil.Node subtitleFile = subtitleFolder.getOrCreateFile(
                            FileUtil.stringToFile(subtitleLink.lang) + ".json", "application/json");
                    int result = downFile(subtitleLink.url, subtitleFile);
                    if (result != NORMAL) return result;
                }
            }
        } catch (IOException e) {
            return ERR_NETWORK;
        } catch (JSONException e) {
            return ERR_JSON;
        }
        return NORMAL;
    }

    private int downFile(String url, VideoStorageUtil.Node file) throws IOException {
        boolean allowResume = true;
        for (int attempt = 0; attempt < 2; attempt++) {
            long existingSize = allowResume && file.isFile() ? file.length() : 0L;
            if (!allowResume) {
                try (OutputStream ignored = file.openOutput(false)) {
                    // Truncate before the fresh request.
                } catch (IOException e) {
                    return ERR_FILE;
                }
            }

            ArrayList<String> headers = new ArrayList<>(NetWorkUtil.webHeaders);
            if (existingSize > 0L) {
                headers.add("Range");
                headers.add("bytes=" + existingSize + "-");
            }

            Response response;
            try {
                response = NetWorkUtil.getDownload(url, headers);
            } catch (IOException e) {
                return ERR_NETWORK;
            }

            try (Response closeableResponse = response) {
                if (response.code() == 416 && existingSize > 0L) {
                    String contentRange = response.header("Content-Range", "");
                    int slash = contentRange.lastIndexOf('/');
                    if (slash >= 0) {
                        try {
                            if (Long.parseLong(contentRange.substring(slash + 1)) == existingSize) return NORMAL;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    return ERR_NETWORK;
                }
                if (!response.isSuccessful() || response.body() == null) return ERR_NETWORK;
                String contentType = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
                if (contentType.contains("text/html")) return ERR_NETWORK;

                boolean append = existingSize > 0L && response.code() == 206;
                if (append) {
                    String contentRange = response.header("Content-Range", "");
                    if (!contentRange.startsWith("bytes " + existingSize + "-")) return ERR_NETWORK;
                }
                if (!append) existingSize = 0L;
                long responseSize = response.body().contentLength();
                long expectedSize = responseSize >= 0L ? existingSize + responseSize : -1L;
                long completeSize = existingSize;

                try (InputStream inputStream = response.body().byteStream();
                     OutputStream outputStream = file.openOutput(append)) {
                    int len;
                    byte[] bytes = new byte[1024 * 10];
                    while ((len = inputStream.read(bytes)) != -1 && started) {
                        outputStream.write(bytes, 0, len);
                        completeSize += len;
                        if (expectedSize > 0L) percent = 1.0f * completeSize / expectedSize;
                    }
                    outputStream.flush();
                } catch (IOException e) {
                    if (append && allowResume) {
                        allowResume = false;
                        continue;
                    }
                    return ERR_FILE;
                }
                if (!started) return getStoppedExitCode();
                if (expectedSize >= 0L && completeSize != expectedSize) return ERR_NETWORK;
                return NORMAL;
            }
        }
        return ERR_FILE;
    }

    private int getStoppedExitCode() {
        if (section != null && deleteRequestedId == section.id) return EXIT_DELETED;
        if (pauseRequested) return EXIT_PAUSED;
        return ERR_UNKNOWN;
    }

    private int downDanmaku(String danmaku, VideoStorageUtil.Node danmakuFile) throws IOException {
        Response response;
        try {
            response = NetWorkUtil.get(danmaku);
        } catch (IOException e) {
            return ERR_NETWORK;
        }
        try (Response closeable = response) {
            if (!response.isSuccessful() || response.body() == null) return ERR_NETWORK;
            byte[] bytes = decompress(response.body().bytes());
            try (OutputStream output = danmakuFile.openOutput(false)) {
                output.write(bytes);
                output.flush();
            } catch (IOException e) {
                return ERR_FILE;
            }
        }
        return NORMAL;
    }

    @Override
    public void onDestroy() {
        Logu.d("结束");

        started = false;
        percent = -1;
        state = null;

        if (toastTimer != null)
            toastTimer.cancel();
        toastTimer = null;

        if (notifyTimer != null)
            notifyTimer.cancel();
        notifyTimer = null;
        releaseWakeLock();

        if (exitMessage == null)
            exitMessage = "下载服务已退出";

        Logu.d("退出下载服务");
        // The active worker owns this reference until its cleanup block completes.
        if (section != null && !workerRunning) {
            final long id = section.id;
            VideoStorageUtil.Node resolvedFolder = null;
            try {
                resolvedFolder = section.getStoragePath(this, false);
            } catch (IOException ignored) {
            }
            final VideoStorageUtil.Node folder = resolvedFolder;
            section = null;

            CenterThreadPool.run(() -> {
                notifyExit(exitMessage);
                if (exitCode != NORMAL) {
                    setState(id, "none");
                    if (exitCode == EXIT_DELETED || exitCode == ERR_FILE
                            || exitCode == ERR_JSON || exitCode == ERR_DATABASE) {
                        if (folder != null) folder.deleteRecursive();
                    }
                }
                refreshDownloadList();
            });
        }

        super.onDestroy();
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
                o.write(buf, 0, i);
            }
            output = o.toByteArray();
        } catch (Exception e) {
            output = data;
            e.printStackTrace();
        } finally {
            try {
                o.close();
                decompresser.end();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return output;
    }

    public static DownloadSection getFirst() {
        Cursor cursor = null;
        SQLiteDatabase database = null;
        try {
            DownloadSqlHelper helper = new DownloadSqlHelper(BiliTerminal.context);
            database = helper.getReadableDatabase();

            if (firstDown >= 0) {
                cursor = database.rawQuery("select * from download where id=? limit 1",
                        new String[]{String.valueOf(firstDown)});
                if (cursor != null && cursor.getCount() == 0) {
                    cursor.close();
                    cursor = null;
                }
            }
            if (cursor == null)
                cursor = database.rawQuery("select * from download where state!=? order by id asc limit 1", new String[]{"error"});

            firstDown = -1;

            if (cursor == null || cursor.getCount() == 0)
                return null;

            cursor.moveToFirst();
            return new DownloadSection(cursor);
        } catch (Exception e) {
            MsgUtil.err(e);
            return null;
        } finally {
            if (cursor != null)
                cursor.close();
            if (database != null)
                database.close();
        }
    }

    public static ArrayList<DownloadSection> getAll() {
        Cursor cursor = null;
        SQLiteDatabase database = null;
        try {
            DownloadSqlHelper helper = new DownloadSqlHelper(BiliTerminal.context);
            database = helper.getReadableDatabase();
            cursor = database.rawQuery("select * from download", null);
            if (cursor == null || cursor.getCount() == 0)
                return null;

            ArrayList<DownloadSection> list = new ArrayList<>();
            while (cursor.moveToNext()) {
                list.add(new DownloadSection(cursor));
            }
            return list;
        } catch (Exception e) {
            MsgUtil.err(e);
            return new ArrayList<>();
        } finally {
            if (cursor != null)
                cursor.close();
            if (database != null)
                database.close();
        }
    }

    public static void deleteSection(long id) {
        SQLiteDatabase database = null;
        try {
            DownloadSqlHelper helper = new DownloadSqlHelper(BiliTerminal.context);
            database = helper.getWritableDatabase();
            database.execSQL("delete from download where id=?", new Object[]{id});
            database.close();
        } catch (Exception e) {
            MsgUtil.err(e);
        } finally {
            if (database != null)
                database.close();
        }
    }

    public static void clear() {
        SQLiteDatabase database = null;
        try {
            DownloadSqlHelper helper = new DownloadSqlHelper(BiliTerminal.context);
            database = helper.getWritableDatabase();
            database.execSQL("delete from download", new Object[]{});
            database.close();
        } catch (Exception e) {
            MsgUtil.err(e);
        } finally {
            if (database != null)
                database.close();
        }
    }

    public static void setState(long id, String state) {
        SQLiteDatabase database = null;
        try {
            DownloadSqlHelper helper = new DownloadSqlHelper(BiliTerminal.context);
            database = helper.getWritableDatabase();
            database.execSQL("update download set state=? where id=?", new Object[]{state, id});
            database.close();
        } catch (Exception e) {
            MsgUtil.err(e);
        } finally {
            if (database != null)
                database.close();
        }
    }

    public static void resetInterruptedDownloads() {
        SQLiteDatabase database = null;
        try {
            DownloadSqlHelper helper = new DownloadSqlHelper(BiliTerminal.context);
            database = helper.getWritableDatabase();
            database.execSQL("update download set state=? where state=?", new Object[]{"none", "downloading"});
            database.close();
        } catch (Exception e) {
            MsgUtil.err(e);
        } finally {
            if (database != null)
                database.close();
        }
    }

    public static void startDownload(String title, long aid, long cid, String cover, int qn, String downloadType,
                                     String audioUrl) {
        CenterThreadPool.run(() -> {
            SQLiteDatabase database = null;
            Cursor cursor = null;
            try {
                DownloadSqlHelper helper = new DownloadSqlHelper(BiliTerminal.context);
                database = helper.getWritableDatabase();

                cursor = database.rawQuery("select * from download where aid=? and cid=?",
                        new String[]{String.valueOf(aid), String.valueOf(cid)});
                if (cursor != null && cursor.getCount() > 0) {
                    MsgUtil.showMsg("该视频已在下载队列中");
                    return;
                }
                if (cursor != null)
                    cursor.close();

                String storageMode = VideoStorageUtil.getCurrentMode();
                String storageRef = VideoStorageUtil.getCurrentReference();
                database.execSQL(
                        "insert into download(type,state,aid,cid,qn,title,child,cover,download_type,audio_url,storage_mode,storage_ref) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{"video_single", "none", aid, cid, qn, title, "", GlideUtil.url(cover),
                                downloadType, audioUrl, storageMode, storageRef});

                VideoStorageUtil.Node pathSingle = VideoStorageUtil.taskDirectory(
                        BiliTerminal.context, storageMode, storageRef, title, null, true);
                pathSingle.getOrCreateFile(".DOWNLOADING", "application/octet-stream");

                String msg = "audio_only".equals(downloadType) ? "已添加音频下载" : "已添加下载";
                MsgUtil.showMsg(msg);

                start(-1);
            } catch (Exception e) {
                MsgUtil.err(e);
            } finally {
                if (cursor != null)
                    cursor.close();
                if (database != null)
                    database.close();
            }
        });
    }

    public static void startDownload(String parent, String child, long aid, long cid, String cover, int qn,
                                     String downloadType, String audioUrl) {
        CenterThreadPool.run(() -> {
            SQLiteDatabase database = null;
            Cursor cursor = null;
            try {
                DownloadSqlHelper helper = new DownloadSqlHelper(BiliTerminal.context);
                database = helper.getWritableDatabase();

                cursor = database.rawQuery("select * from download where aid=? and cid=?",
                        new String[]{String.valueOf(aid), String.valueOf(cid)});
                if (cursor != null && cursor.getCount() > 0) {
                    MsgUtil.showMsg("该视频已在下载队列中");
                    return;
                }
                if (cursor != null)
                    cursor.close();

                String storageMode = VideoStorageUtil.getCurrentMode();
                String storageRef = VideoStorageUtil.getCurrentReference();
                database.execSQL(
                        "insert into download(type,state,aid,cid,qn,title,child,cover,download_type,audio_url,storage_mode,storage_ref) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{"video_multi", "none", aid, cid, qn, parent, child, GlideUtil.url(cover),
                                downloadType, audioUrl, storageMode, storageRef});

                VideoStorageUtil.Node pathPage = VideoStorageUtil.taskDirectory(
                        BiliTerminal.context, storageMode, storageRef, parent, child, true);
                pathPage.getOrCreateFile(".DOWNLOADING", "application/octet-stream");

                String msg = "audio_only".equals(downloadType) ? "已添加音频下载" : "已添加下载";
                MsgUtil.showMsg(msg);

                start(-1);
            } catch (Exception e) {
                MsgUtil.err(e);
            } finally {
                if (cursor != null)
                    cursor.close();
                if (database != null)
                    database.close();
            }
        });
    }

    public static void start(long first) {
        if (started)
            return;
        started = true;
        Logu.d("start");
        firstDown = first;

        Context context = BiliTerminal.context;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(new Intent(context, DownloadService.class));
        else
            context.startService(new Intent(context, DownloadService.class));
    }

    public static boolean isCurrent(long id) {
        return started && section != null && section.id == id;
    }

    public static void pause() {
        if (!started)
            return;
        pauseRequested = true;
        exitCode = EXIT_PAUSED;
        started = false;
    }

    public static void stopForDelete(long id) {
        if (!isCurrent(id))
            return;
        deleteRequestedId = id;
        pauseRequested = false;
        exitCode = EXIT_DELETED;
        started = false;
    }

}
