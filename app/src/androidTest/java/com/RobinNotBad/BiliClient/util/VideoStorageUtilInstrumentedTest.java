package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.helper.sql.DownloadSqlHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class VideoStorageUtilInstrumentedTest {

    @Test
    @SdkSuppress(minSdkVersion = 21)
    public void testSafWriteAppendReadAndDelete() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("The test requires a selected SAF directory", VideoStorageUtil.isSafMode());

        VideoStorageUtil.Node root = VideoStorageUtil.currentRoot(context);
        VideoStorageUtil.Node directory = root.getOrCreateDirectory(".saf_instrumentation_test");
        try {
            VideoStorageUtil.Node file = directory.getOrCreateFile(
                    "append.bin", "application/octet-stream");
            try (OutputStream output = file.openOutput(false)) {
                output.write("first".getBytes(StandardCharsets.UTF_8));
            }
            try (OutputStream output = file.openOutput(true)) {
                output.write("-second".getBytes(StandardCharsets.UTF_8));
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = file.openInput()) {
                byte[] buffer = new byte[64];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                }
            }
            assertEquals("first-second", bytes.toString("UTF-8"));
        } finally {
            assertTrue("SAF test directory could not be removed", directory.deleteRecursive());
        }
    }

    @Test
    public void testLegacyDownloadQueueMigrationKeepsFileStorage() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("download.db");
        try {
            SQLiteDatabase legacy = context.openOrCreateDatabase("download.db", 0, null);
            legacy.execSQL("create table download(id INTEGER primary key autoincrement," +
                    "type TEXT,state TEXT,aid BIGINT,cid BIGINT,qn INTEGER,title TEXT," +
                    "child TEXT,cover TEXT,download_type TEXT DEFAULT 'video',audio_url TEXT)");
            legacy.execSQL("insert into download(type,state,aid,cid,qn,title,child,cover) " +
                    "values('video_single','none',1,2,32,'legacy','','')");
            legacy.setVersion(4);
            legacy.close();

            DownloadSqlHelper helper = new DownloadSqlHelper(context);
            SQLiteDatabase migrated = helper.getWritableDatabase();
            try (Cursor cursor = migrated.rawQuery(
                    "select storage_mode,storage_ref from download where title='legacy'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(VideoStorageUtil.MODE_FILE, cursor.getString(0));
                assertEquals(FileUtil.getVideoDownloadPath().getAbsolutePath(), cursor.getString(1));
            }
            helper.close();
        } finally {
            context.deleteDatabase("download.db");
        }
    }
}
