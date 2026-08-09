package com.RobinNotBad.BiliClient.helper.sql;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;

public class DownloadSqlHelper extends SQLiteOpenHelper {
    public DownloadSqlHelper(@Nullable Context context) {
        super(context, "download.db", null, 5);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table download(id INTEGER primary key autoincrement," +
                "type TEXT," +
                "state TEXT," +
                "aid BIGINT," +
                "cid BIGINT," +
                "qn INTEGER," +
                "title TEXT," +
                "child TEXT," +
                "cover TEXT," +
                "download_type TEXT DEFAULT 'video'," +
                "audio_url TEXT," +
                "storage_mode TEXT," +
                "storage_ref TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE download ADD COLUMN download_type TEXT DEFAULT 'video'");
                db.execSQL("ALTER TABLE download ADD COLUMN audio_url TEXT");
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE download ADD COLUMN storage_mode TEXT");
                db.execSQL("ALTER TABLE download ADD COLUMN storage_ref TEXT");
                db.execSQL("UPDATE download SET storage_mode=?, storage_ref=? " +
                                "WHERE storage_mode IS NULL OR storage_mode='' " +
                                "OR storage_ref IS NULL OR storage_ref=''",
                        new Object[]{VideoStorageUtil.MODE_FILE,
                                FileUtil.getVideoDownloadPath().getAbsolutePath()});
            }
        } catch (Throwable e) {
            MsgUtil.err(e);
        }
    }
}
