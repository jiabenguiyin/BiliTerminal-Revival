package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import com.bumptech.glide.Glide;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Storage information and bounded cleanup; user media and account files are outside its roots. */
public final class AppStorageManager {
    private static final AtomicBoolean CLEARING=new AtomicBoolean();
    private AppStorageManager() {}
    public static final class Snapshot {
        public long images,temporary,apk,available,total;
        public String apkPath="";
    }
    public static final class Cleanup {
        public long bytes;public int failures;public boolean busy,changed;
    }
    private static List<File> caches(Context context) {
        ArrayList<File> roots=new ArrayList<>();roots.add(context.getCacheDir());
        try{File external=context.getExternalCacheDir();if(external!=null)roots.add(external);}catch(Exception ignored){}
        return roots;
    }
    private static List<File> savedMedia() {
        ArrayList<File> result=new ArrayList<>();
        for(String key:new String[]{"save_path_video","save_path_pictures"}) {
            String path=SharedPreferencesUtil.getString(key,"");if(!path.isEmpty())result.add(new File(path));
        }
        return result;
    }
    private static boolean imageDirectoryAvailable(Context context,List<File> media) {
        try{File images=new File(context.getCacheDir(),AppCacheFiles.IMAGE_DIR);
            for(File path:media)if(AppCacheFiles.overlaps(images,path))return false;
            return AppCacheFiles.imageTreeSafe(context.getCacheDir());
        }catch(Exception ignored){return false;}
    }
    private static File apk(Context context,String path) {
        File updates=null;
        try{updates=context.getExternalFilesDir("updates");}catch(Exception ignored){}
        return AppCacheFiles.registeredApk(path,Arrays.asList(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),updates,
                new File(context.getFilesDir(),"updates")));
    }
    public static Snapshot inspect(Context context) {
        Snapshot result=new Snapshot();long now=System.currentTimeMillis();
        List<File> media=savedMedia();
        if(imageDirectoryAvailable(context,media))result.images=AppCacheFiles.imageBytes(context.getCacheDir());
        for(File root:caches(context))result.temporary+=AppCacheFiles.oldTemporary(root,now,false,media).bytes;
        result.apkPath=SharedPreferencesUtil.getString("terminal_update_pkg","");
        File apk=apk(context,result.apkPath);if(apk!=null)result.apk=apk.length();
        try {
            StatFs storage=new StatFs(context.getFilesDir().getAbsolutePath());
            if(Build.VERSION.SDK_INT>=18){result.available=storage.getAvailableBytes();result.total=storage.getTotalBytes();}
            else{result.available=(long)storage.getAvailableBlocks()*storage.getBlockSize();result.total=(long)storage.getBlockCount()*storage.getBlockSize();}
        }catch(Exception ignored){}
        return result;
    }
    /** Run on a worker thread. Image memory is cleared separately on the UI thread. */
    public static Cleanup clearCaches(Context context) {
        Cleanup result=new Cleanup();
        if(!CLEARING.compareAndSet(false,true)){result.busy=true;return result;}
        try {
            File internal=context.getCacheDir().getCanonicalFile();
            List<File> media=savedMedia();
            if(imageDirectoryAvailable(context,media)) {
                long before=AppCacheFiles.imageBytes(internal);
                try{Glide.get(context).clearDiskCache();result.bytes+=Math.max(0,before-AppCacheFiles.imageBytes(internal));}
                catch(Exception ignored){result.failures++;}
            }else result.failures++;
            long now=System.currentTimeMillis();
            for(File root:caches(context)){AppCacheFiles.Result cleared=AppCacheFiles.oldTemporary(root,now,true,media);result.bytes+=cleared.bytes;result.failures+=cleared.failures;}
        }catch(Exception ignored){result.failures++;}
        finally{CLEARING.set(false);}
        return result;
    }
    public static Cleanup clearUpdatePackage(Context context,String expectedPath) {
        Cleanup result=new Cleanup();
        if(!CLEARING.compareAndSet(false,true)){result.busy=true;return result;}
        try {
            String registered=SharedPreferencesUtil.getString("terminal_update_pkg","");
            if(!registered.equals(expectedPath)){result.changed=true;return result;}
            File file=apk(context,registered);
            if(file==null){if(!registered.isEmpty())result.failures++;return result;}
            long bytes=file.length();
            if(file.delete()) {
                result.bytes=bytes;
                if(registered.equals(SharedPreferencesUtil.getString("terminal_update_pkg","")))SharedPreferencesUtil.removeValue("terminal_update_pkg");
            }else if(file.exists())result.failures++;
        }catch(Exception ignored){result.failures++;}
        finally{CLEARING.set(false);}
        return result;
    }
}
