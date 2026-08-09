package com.RobinNotBad.BiliClient.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.documentfile.provider.DocumentFile;

import com.RobinNotBad.BiliClient.BiliTerminal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Video cache storage abstraction.
 *
 * File mode uses a normal filesystem path. SAF mode stores the selected tree URI and accesses
 * every child through ContentResolver/DocumentFile. Never convert a SAF URI back to /storage/...
 * because doing so loses the granted write capability on removable SD cards.
 */
public final class VideoStorageUtil {
    public static final String MODE_FILE = "file";
    public static final String MODE_SAF = "saf";
    public static final String PREF_MODE = "save_storage_mode_video";
    public static final String PREF_TREE_URI = "save_tree_uri_video";

    private static final String LOCATOR_PREFIX = "saf-tree|";

    private VideoStorageUtil() {
    }

    public static String getCurrentMode() {
        return SharedPreferencesUtil.getString(PREF_MODE, MODE_FILE);
    }

    public static String getCurrentReference() {
        if (MODE_SAF.equals(getCurrentMode())) {
            return SharedPreferencesUtil.getString(PREF_TREE_URI, "");
        }
        return FileUtil.getVideoDownloadPath().getAbsolutePath();
    }

    public static boolean isSafMode() {
        return MODE_SAF.equals(getCurrentMode())
                && !SharedPreferencesUtil.getString(PREF_TREE_URI, "").isEmpty();
    }

    public static void selectFileRoot(File root) {
        SharedPreferencesUtil.putString(PREF_MODE, MODE_FILE);
        SharedPreferencesUtil.putString("save_path_video", root.getAbsolutePath());
    }

    public static void selectSafRoot(Context context, Uri treeUri, int intentFlags) throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            throw new IOException("当前系统不支持 SAF 目录授权");
        }
        int flags = intentFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        context.getContentResolver().takePersistableUriPermission(treeUri, flags);

        Node root = root(context, MODE_SAF, treeUri.toString());
        if (!root.exists() || !root.isDirectory() || !root.canWrite()) {
            throw new IOException("所选目录不可写");
        }
        Node probe = root.getOrCreateFile(".biliterminal_write_test", "application/octet-stream");
        try (OutputStream output = probe.openOutput(false)) {
            output.write(1);
        }
        if (!probe.deleteRecursive()) {
            throw new IOException("目录写入测试文件无法删除");
        }

        SharedPreferencesUtil.putString(PREF_TREE_URI, treeUri.toString());
        SharedPreferencesUtil.putString(PREF_MODE, MODE_SAF);
        ensureNoMedia(context, root);
    }

    public static String describeCurrent(Context context) {
        if (isSafMode()) {
            try {
                Node root = currentRoot(context);
                String name = root.getName();
                return "SAF：" + (name == null ? getCurrentReference() : name);
            } catch (Exception ignored) {
                return "SAF（授权失效）";
            }
        }
        return FileUtil.getVideoDownloadPath().getAbsolutePath();
    }

    public static Node currentRoot(Context context) throws IOException {
        return root(context, getCurrentMode(), getCurrentReference());
    }

    public static Node root(Context context, String mode, String reference) throws IOException {
        if (MODE_SAF.equals(mode)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                throw new IOException("系统版本不支持 SAF");
            }
            if (reference == null || reference.isEmpty()) {
                throw new IOException("未保存 SAF 目录");
            }
            Uri treeUri = Uri.parse(reference);
            DocumentFile document = DocumentFile.fromTreeUri(context, treeUri);
            if (document == null) throw new IOException("无法打开 SAF 目录");
            return new Node(context.getApplicationContext(), MODE_SAF, reference, "", null, document);
        }
        File file = new File(reference == null || reference.isEmpty()
                ? FileUtil.getVideoDownloadPath().getAbsolutePath() : reference);
        return new Node(context.getApplicationContext(), MODE_FILE, file.getAbsolutePath(), "", file, null);
    }

    public static Node taskDirectory(Context context, String mode, String reference,
                                     String title, String child, boolean create) throws IOException {
        Node root = root(context, mode, reference);
        Node parent = create
                ? root.getOrCreateDirectory(FileUtil.stringToFile(title))
                : root.find(FileUtil.stringToFile(title));
        if (parent == null) return null;
        if (child == null || child.isEmpty()) return parent;
        return create
                ? parent.getOrCreateDirectory(FileUtil.stringToFile(child))
                : parent.find(FileUtil.stringToFile(child));
    }

    public static Node currentTaskDirectory(Context context, String title, String child,
                                            boolean create) throws IOException {
        return taskDirectory(context, getCurrentMode(), getCurrentReference(), title, child, create);
    }

    public static void ensureNoMedia(Context context, Node root) {
        try {
            Node noMedia = root.find(".nomedia");
            if (SharedPreferencesUtil.getBoolean("save_ban_gallery", true)) {
                if (noMedia == null) root.getOrCreateFile(".nomedia", "application/octet-stream");
            } else if (noMedia != null) {
                noMedia.deleteRecursive();
            }
        } catch (Exception ignored) {
        }
    }

    public static Node fromLocator(Context context, String locator) throws IOException {
        if (locator == null || locator.isEmpty()) return null;
        if (!locator.startsWith(LOCATOR_PREFIX)) {
            File file = new File(locator);
            return new Node(context.getApplicationContext(), MODE_FILE, file.getAbsolutePath(), "", file, null);
        }
        String body = locator.substring(LOCATOR_PREFIX.length());
        int split = body.indexOf('|');
        if (split < 0) throw new IOException("无效 SAF 定位符");
        String rootRef = Uri.decode(body.substring(0, split));
        String relative = Uri.decode(body.substring(split + 1));
        Node node = root(context, MODE_SAF, rootRef);
        if (relative.isEmpty()) return node;
        for (String part : relative.split("/")) {
            if (part.isEmpty()) continue;
            node = node.find(part);
            if (node == null) return null;
        }
        return node;
    }

    public static boolean deleteLocator(Context context, String locator) {
        try {
            Node node = fromLocator(context, locator);
            return node == null || !node.exists() || node.deleteRecursive();
        } catch (Exception e) {
            return false;
        }
    }

    public static File copyContentToCache(Context context, String uriString, String fileName) throws IOException {
        Uri uri = Uri.parse(uriString);
        File out = new File(context.getCacheDir(), fileName);
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(out, false)) {
            if (input == null) throw new IOException("无法读取 " + uriString);
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        }
        return out;
    }

    public static String readString(Context context, String reference) {
        try {
            if (reference == null || reference.isEmpty()) return null;
            if (!reference.startsWith("content://")) return FileUtil.readString(new File(reference));
            try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(reference));
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) return null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                return output.toString("UTF-8");
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static final class Node {
        private final Context context;
        private final String mode;
        private final String rootReference;
        private final String relativePath;
        private final File file;
        private final DocumentFile document;

        private Node(Context context, String mode, String rootReference, String relativePath,
                     File file, DocumentFile document) {
            this.context = context;
            this.mode = mode;
            this.rootReference = rootReference;
            this.relativePath = relativePath == null ? "" : relativePath;
            this.file = file;
            this.document = document;
        }

        public boolean isSaf() {
            return MODE_SAF.equals(mode);
        }

        public boolean exists() {
            return isSaf() ? document != null && document.exists() : file.exists();
        }

        public boolean isDirectory() {
            return isSaf() ? document != null && document.isDirectory() : file.isDirectory();
        }

        public boolean isFile() {
            return isSaf() ? document != null && document.isFile() : file.isFile();
        }

        public boolean canWrite() {
            return isSaf() ? document != null && document.canWrite() : file.canWrite();
        }

        public String getName() {
            return isSaf() ? (document == null ? null : document.getName()) : file.getName();
        }

        public long length() {
            return isSaf() ? (document == null ? 0L : document.length()) : file.length();
        }

        public String getMode() {
            return mode;
        }

        public String getRootReference() {
            return rootReference;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getLocator() {
            if (!isSaf()) return file.getAbsolutePath();
            return LOCATOR_PREFIX + Uri.encode(rootReference) + "|" + Uri.encode(relativePath);
        }

        public String getUriString() {
            return isSaf() && document != null ? document.getUri().toString() : file.getAbsolutePath();
        }

        public Node find(String name) {
            if (name == null || name.isEmpty()) return this;
            if (isSaf()) {
                if (document == null || !document.isDirectory()) return null;
                DocumentFile child = document.findFile(name);
                if (child == null) return null;
                return new Node(context, mode, rootReference, join(relativePath, name), null, child);
            }
            File child = new File(file, name);
            if (!child.exists()) return null;
            return new Node(context, mode, rootReference, join(relativePath, name), child, null);
        }

        public Node getOrCreateDirectory(String name) throws IOException {
            Node found = find(name);
            if (found != null) {
                if (!found.isDirectory()) throw new IOException(name + " 不是目录");
                return found;
            }
            if (isSaf()) {
                DocumentFile child = document.createDirectory(name);
                if (child == null) throw new IOException("无法创建目录：" + name);
                return new Node(context, mode, rootReference, join(relativePath, name), null, child);
            }
            File child = new File(file, name);
            if (!child.mkdirs() && !child.isDirectory()) throw new IOException("无法创建目录：" + child);
            return new Node(context, mode, rootReference, join(relativePath, name), child, null);
        }

        public Node getOrCreateFile(String name, String mimeType) throws IOException {
            Node found = find(name);
            if (found != null) {
                if (!found.isFile()) throw new IOException(name + " 不是文件");
                return found;
            }
            if (isSaf()) {
                DocumentFile child = document.createFile(mimeType == null ? "application/octet-stream" : mimeType, name);
                if (child == null) throw new IOException("无法创建文件：" + name);
                return new Node(context, mode, rootReference, join(relativePath, name), null, child);
            }
            if (!file.exists() && !file.mkdirs()) throw new IOException("无法创建目录：" + file);
            File child = new File(file, name);
            if (!child.createNewFile() && !child.isFile()) throw new IOException("无法创建文件：" + child);
            return new Node(context, mode, rootReference, join(relativePath, name), child, null);
        }

        public List<Node> listChildren() {
            List<Node> result = new ArrayList<>();
            if (!isDirectory()) return result;
            if (isSaf()) {
                for (DocumentFile child : document.listFiles()) {
                    String name = child.getName();
                    if (name != null) result.add(new Node(context, mode, rootReference,
                            join(relativePath, name), null, child));
                }
            } else {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) result.add(new Node(context, mode, rootReference,
                            join(relativePath, child.getName()), child, null));
                }
            }
            return result;
        }

        public InputStream openInput() throws IOException {
            if (!isSaf()) return new FileInputStream(file);
            InputStream input = context.getContentResolver().openInputStream(document.getUri());
            if (input == null) throw new IOException("无法打开输入流：" + document.getUri());
            return input;
        }

        public OutputStream openOutput(boolean append) throws IOException {
            if (!isSaf()) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("无法创建目录：" + parent);
                }
                return new FileOutputStream(file, append);
            }

            ContentResolver resolver = context.getContentResolver();
            if (!append) {
                OutputStream output;
                try {
                    output = resolver.openOutputStream(document.getUri(), "wt");
                } catch (Exception ignored) {
                    output = resolver.openOutputStream(document.getUri(), "w");
                }
                if (output == null) throw new IOException("无法打开输出流：" + document.getUri());
                return output;
            }

            ParcelFileDescriptor descriptor = resolver.openFileDescriptor(document.getUri(), "rw");
            if (descriptor == null) throw new IOException("无法打开输出流：" + document.getUri());
            ParcelFileDescriptor.AutoCloseOutputStream output =
                    new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
            try {
                long position = descriptor.getStatSize();
                if (position < 0L) position = length();
                output.getChannel().position(position);
            } catch (IOException e) {
                output.close();
                throw new IOException("该文档提供程序不支持断点定位", e);
            }
            return output;
        }

        public boolean deleteRecursive() {
            try {
                if (!exists()) return true;
                if (isDirectory()) {
                    for (Node child : listChildren()) child.deleteRecursive();
                }
                return isSaf() ? document.delete() : file.delete();
            } catch (Exception e) {
                return false;
            }
        }

        private static String join(String parent, String child) {
            return parent == null || parent.isEmpty() ? child : parent + "/" + child;
        }
    }
}
