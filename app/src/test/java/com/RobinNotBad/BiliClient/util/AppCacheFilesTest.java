package com.RobinNotBad.BiliClient.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import static org.junit.Assert.*;

public class AppCacheFilesTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private File write(File root,String name,long modified)throws Exception {
        File file=new File(root,name);assertTrue(file.getParentFile().exists()||file.getParentFile().mkdirs());
        try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[]{1,2,3,4});}
        assertTrue(file.setLastModified(modified));return file;
    }
    @Test public void onlyOldRegenerableFilesAreCleanedWhileCurrentAndPlaybackFilesSurvive()throws Exception {
        File cache=temporary.newFolder("cache");long now=System.currentTimeMillis(),old=now-3*AppCacheFiles.TEMP_MIN_AGE_MS;
        File stale=write(cache,"nested/old.tmp",old),recent=write(cache,"nested/recent.tmp",now);
        File video=write(cache,"media.mp4",old),queue=write(cache,"playback-queues/token.json",old),danmaku=write(cache,"local-danmaku-1.xml",old);
        File image=write(cache,"draft/photo.jpg",old);
        assertEquals(4,AppCacheFiles.oldTemporary(cache,now,false).bytes);assertTrue(stale.exists());
        assertEquals(4,AppCacheFiles.oldTemporary(cache,now,true).bytes);assertFalse(stale.exists());
        for(File file:Arrays.asList(recent,video,queue,danmaku,image))assertTrue(file.toString(),file.exists());
        assertTrue(cache.isDirectory());
    }
    @Test public void imageCacheIsMeasuredSeparatelyAndIsNotDeletedByTemporaryCleanup()throws Exception {
        File cache=temporary.newFolder("cache");long now=System.currentTimeMillis();
        File image=write(cache,AppCacheFiles.IMAGE_DIR+"/entry.0",now-3*AppCacheFiles.TEMP_MIN_AGE_MS);
        assertEquals(4,AppCacheFiles.imageBytes(cache));assertTrue(AppCacheFiles.imageTreeSafe(cache));
        assertEquals(0,AppCacheFiles.oldTemporary(cache,now,true).bytes);assertTrue(image.exists());
    }
    @Test public void appFilesOutsideCacheCannotBeReachedByTheWalker()throws Exception {
        File cache=temporary.newFolder("cache"),files=temporary.newFolder("files");long now=System.currentTimeMillis();
        File account=write(files,"accounts.json",now-3*AppCacheFiles.TEMP_MIN_AGE_MS);
        assertFalse(AppCacheFiles.safeChild(cache,account));AppCacheFiles.oldTemporary(cache,now,true);assertTrue(account.exists());
    }
    @Test public void onlyRegisteredApksInExplicitUpdateDirectoriesAreEligible()throws Exception {
        File updates=temporary.newFolder("updates"),files=temporary.newFolder("files");
        File valid=write(updates,"BiliTerminal_208(1).apk",System.currentTimeMillis());
        File unrelated=write(updates,"other-app.apk",System.currentTimeMillis());
        File outside=write(files,"BiliTerminal_208.apk",System.currentTimeMillis());
        assertEquals(valid.getCanonicalFile(),AppCacheFiles.registeredApk(valid.getAbsolutePath(),Arrays.asList(updates)));
        assertNull(AppCacheFiles.registeredApk(unrelated.getAbsolutePath(),Arrays.asList(updates)));
        assertNull(AppCacheFiles.registeredApk(outside.getAbsolutePath(),Arrays.asList(updates)));assertTrue(outside.exists());
    }
    @Test public void activeDownloadsAndOldUploadsAreAlwaysPreserved()throws Exception {
        File cache=temporary.newFolder("cache");long now=System.currentTimeMillis(),old=now-3*AppCacheFiles.TEMP_MIN_AGE_MS;
        for(String name:new String[]{"video.part","video.DOWNLOADING","pending-upload/data.bin","installer.apk","saf-source/data.bin"})write(cache,name,old);
        assertEquals(0,AppCacheFiles.oldTemporary(cache,now,true).bytes);
        assertTrue(new File(cache,"video.part").exists());assertTrue(new File(cache,"pending-upload/data.bin").exists());
    }
    @Test public void configuredVideoDirectoryKeepsOldMetadataInsideACacheRoot()throws Exception {
        File cache=temporary.newFolder("cache"),saved=new File(cache,"saved-videos");
        long now=System.currentTimeMillis(),old=now-3*AppCacheFiles.TEMP_MIN_AGE_MS;
        File metadata=write(saved,"info.json",old),video=write(saved,"video.mp4",old),temp=write(cache,"old.tmp",old);
        assertEquals(4,AppCacheFiles.oldTemporary(cache,now,true,Arrays.asList(saved)).bytes);
        assertTrue(metadata.exists());assertTrue(video.exists());assertFalse(temp.exists());
        assertTrue(AppCacheFiles.overlaps(cache,saved));assertFalse(AppCacheFiles.overlaps(new File(cache,AppCacheFiles.IMAGE_DIR),saved));
    }
}
