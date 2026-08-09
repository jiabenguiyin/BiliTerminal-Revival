package com.RobinNotBad.BiliClient.model;

import android.content.Context;
import android.database.Cursor;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;

import java.io.File;
import java.io.IOException;

public class DownloadSection {
    public long id;
    public String type;
    public long aid;
    public long cid;
    public int qn;
    public String url_cover;
    public String title;
    public String child;
    public String name_short;
    public String state;
    public String downloadType;
    public String audioUrl;
    public String storageMode;
    public String storageRef;

    public DownloadSection() {
    }

    public DownloadSection(Cursor cursor) {
        id = cursor.getInt(0);
        type = cursor.getString(1);
        state = cursor.getString(2);
        aid = cursor.getLong(3);
        cid = cursor.getLong(4);
        qn = cursor.getInt(5);
        title = cursor.getString(6);
        child = cursor.getString(7);
        url_cover = cursor.getString(8);

        int downloadTypeIndex = cursor.getColumnIndex("download_type");
        if (downloadTypeIndex != -1) downloadType = cursor.getString(downloadTypeIndex);
        if (downloadType == null || downloadType.isEmpty()) downloadType = "video";

        int audioUrlIndex = cursor.getColumnIndex("audio_url");
        if (audioUrlIndex != -1) audioUrl = cursor.getString(audioUrlIndex);
        if (audioUrl == null) audioUrl = "";

        int storageModeIndex = cursor.getColumnIndex("storage_mode");
        if (storageModeIndex != -1) storageMode = cursor.getString(storageModeIndex);
        int storageRefIndex = cursor.getColumnIndex("storage_ref");
        if (storageRefIndex != -1) storageRef = cursor.getString(storageRefIndex);
        if (storageMode == null || storageMode.isEmpty()) storageMode = VideoStorageUtil.getCurrentMode();
        if (storageRef == null || storageRef.isEmpty()) storageRef = VideoStorageUtil.getCurrentReference();

        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(title.substring(0, Math.min(8, title.length())));
        sBuilder.append(title.length() > 7 ? "..." : "");
        if (type.equals("video_multi")) {
            sBuilder.append("-");
            sBuilder.append(child.substring(0, Math.min(8, child.length())));
            sBuilder.append(child.length() > 7 ? "..." : "");
        }
        if ("audio_only".equals(downloadType)) sBuilder.append("[音频]");
        name_short = sBuilder.toString();
    }

    public VideoStorageUtil.Node getStoragePath(Context context, boolean create) throws IOException {
        if (type.contains("video")) {
            return VideoStorageUtil.taskDirectory(context, storageMode, storageRef, title, child, create);
        }
        return VideoStorageUtil.root(context, VideoStorageUtil.MODE_FILE, FileUtil.getPicturePath().getAbsolutePath());
    }

    public VideoStorageUtil.Node getStoragePath(boolean create) throws IOException {
        return getStoragePath(BiliTerminal.context, create);
    }

    /** Legacy caller compatibility; returns null for SAF-backed tasks. */
    public File getPath() {
        if (VideoStorageUtil.MODE_SAF.equals(storageMode)) return null;
        if (type.contains("video")) {
            File root = new File(storageRef);
            File parent = new File(root, FileUtil.stringToFile(title));
            return child == null || child.isEmpty() ? parent : new File(parent, FileUtil.stringToFile(child));
        }
        return FileUtil.getPicturePath();
    }

    public boolean isAudioOnly() {
        return "audio_only".equals(downloadType);
    }

    public PlayerData toPlayerData() {
        PlayerData data = new PlayerData();
        data.aid = aid;
        data.cid = cid;
        data.title = title;
        data.qn = qn;
        data.mid = SharedPreferencesUtil.getLong("mid", 0);
        return data;
    }
}
