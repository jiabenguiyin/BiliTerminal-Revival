package com.RobinNotBad.BiliClient.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** File operations restricted to cache roots; linked directories are never traversed. */
public final class AppCacheFiles {
    public static final long TEMP_MIN_AGE_MS=24*60*60*1000L;
    public static final String IMAGE_DIR="image_manager_disk_cache";
    private AppCacheFiles() {}
    public static final class Result {
        public long bytes;public int failures;
        void add(Result other){bytes+=other.bytes;failures+=other.failures;}
    }
    public static long imageBytes(File cache) {
        try {
            File root=cache.getCanonicalFile(),images=new File(root,IMAGE_DIR);
            return safeChild(root,images)?size(images,0):0;
        }catch(Exception ignored){return 0;}
    }
    public static boolean safeChild(File root,File child)throws IOException {
        String base=root.getCanonicalPath(),absolute=child.getAbsolutePath();
        return absolute.startsWith(base+File.separator)&&absolute.equals(child.getCanonicalPath());
    }
    public static boolean imageTreeSafe(File cache) {
        try {
            File root=cache.getCanonicalFile(),images=new File(root,IMAGE_DIR);
            return safeChild(root,images)&&(!images.exists()||images.isDirectory()&&treeSafe(images,0));
        }catch(Exception ignored){return false;}
    }
    private static boolean treeSafe(File directory,int depth)throws IOException {
        if(depth>64)return false;
        File[] entries=directory.listFiles();if(entries==null)return false;
        for(File child:entries)if(!safeChild(directory,child)||child.isDirectory()&&!treeSafe(child,depth+1))return false;
        return true;
    }
    private static long size(File file,int depth)throws IOException {
        if(depth>64)return 0;
        if(file.isFile())return Math.max(0,file.length());
        long total=0;File[] children=file.listFiles();if(children==null)return 0;
        for(File child:children)if(safeChild(file,child))total+=size(child,depth+1);
        return total;
    }
    public static Result oldTemporary(File cache,long now,boolean delete) {
        return oldTemporary(cache,now,delete,java.util.Collections.emptyList());
    }
    public static Result oldTemporary(File cache,long now,boolean delete,List<File> protectedRoots) {
        Result result=new Result();
        try {
            java.util.ArrayList<File> canonical=new java.util.ArrayList<>();
            for(File file:protectedRoots)canonical.add(file.getCanonicalFile());
            walk(cache.getCanonicalFile(),cache.getCanonicalFile(),now-TEMP_MIN_AGE_MS,delete,result,canonical,0);
        }
        catch(Exception ignored){result.failures++;}
        return result;
    }
    public static boolean overlaps(File a,File b)throws IOException {
        String x=a.getCanonicalPath(),y=b.getCanonicalPath();
        return x.equals(y)||x.startsWith(y+File.separator)||y.startsWith(x+File.separator);
    }
    private static boolean protectedPath(File file,List<File> roots) {
        String path=file.getAbsolutePath();
        for(File root:roots)if(path.equals(root.getPath())||path.startsWith(root.getPath()+File.separator))return true;
        return false;
    }
    private static void walk(File root,File directory,long cutoff,boolean delete,Result result,List<File> protectedRoots,int depth)throws IOException {
        if(depth>64||protectedPath(directory,protectedRoots))return;
        File[] entries=directory.listFiles();if(entries==null){if(directory.exists())result.failures++;return;}
        for(File file:entries) {
            if(!safeChild(root,file)||protectedName(file.getName())||protectedPath(file,protectedRoots))continue;
            if(file.isDirectory()){walk(root,file,cutoff,delete,result,protectedRoots,depth+1);continue;}
            long modified=file.lastModified();
            if(!file.isFile()||modified<=0||modified>cutoff)continue;
            long bytes=file.length();
            if(!delete||file.delete())result.bytes+=bytes;
            else if(file.exists())result.failures++;
        }
    }
    private static boolean protectedName(String name) {
        String n=name.toLowerCase(Locale.US);
        if(n.equals(IMAGE_DIR)||n.startsWith("playback-")||n.startsWith("saf-")||n.contains("danmaku")
                ||n.contains("draft")||n.contains("upload")||n.endsWith(".part")||n.endsWith(".downloading"))return true;
        for(String suffix:new String[]{".mp4",".m4s",".m4a",".aac",".mp3",".flv",".webm",".mkv",".wav",".jpg",".jpeg",".png",".webp",".gif",".apk"})
            if(n.endsWith(suffix))return true;
        return false;
    }
    public static File registeredApk(String path,List<File> allowedDirectories) {
        if(path==null||path.isEmpty())return null;
        File file=new File(path);
        if(!file.getName().matches("BiliTerminal_[0-9]+(?:\\([0-9]+\\))?\\.apk"))return null;
        try {
            File parent=file.getParentFile().getCanonicalFile(),expected=new File(parent,file.getName());
            if(!expected.getAbsolutePath().equals(expected.getCanonicalPath())||!expected.isFile())return null;
            for(File directory:allowedDirectories)if(directory!=null&&parent.equals(directory.getCanonicalFile()))return expected;
        }catch(Exception ignored){}
        return null;
    }
}
