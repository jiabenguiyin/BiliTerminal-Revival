package com.RobinNotBad.BiliClient.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.api.AppInfoApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Inflater;

import okhttp3.Response;

public class DownloadActivity extends BaseActivity {

    View progressView;
    TextView progressText;
    File downFile;
    VideoStorageUtil.Node rootNode, downNode;
    String link;
    int scrHeight;
    String dldText = "";
    float dldPercent = 0;
    int type;
    boolean finish = false;
    boolean no_bili_headers = false;

    final Timer timer = new Timer();
    final TimerTask showText = new TimerTask() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            int viewHeight = (int) (dldPercent * scrHeight);
            runOnUiThread(() -> {
                progressText.setText(dldText + "\n" + (dldPercent * 100) + "%");
                ViewGroup.LayoutParams params = progressView.getLayoutParams();
                params.height = viewHeight;
                progressView.setLayoutParams(params);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);

        Intent intent = getIntent();
        type = intent.getIntExtra("type", 0);
        link = intent.getStringExtra("link");
        no_bili_headers = intent.getBooleanExtra("terminal", false);
        progressText = findViewById(R.id.progressText);
        progressView = findViewById(R.id.progressView);
        scrHeight = window_height;

        if (!FileUtil.checkStoragePermission()) FileUtil.requestStoragePermission(this);

        timer.schedule(showText, 100, 100);
        CenterThreadPool.run(() -> {
            try {
                if (type == 0) {
                    File rootPath = new File(intent.getStringExtra("path"));
                    if (!rootPath.exists()) rootPath.mkdirs();
                    downFile = new File(rootPath, FileUtil.getFileNameFromLink(link));
                    downloadFile(link, downFile, "下载文件中", true);
                    return;
                }

                String title = FileUtil.stringToFile(intent.getStringExtra("title"));
                VideoStorageUtil.Node storageRoot = VideoStorageUtil.currentRoot(this);
                VideoStorageUtil.Node coverRoot;
                if (type == 1) {
                    downNode = storageRoot.getOrCreateDirectory(title);
                    rootNode = downNode;
                    coverRoot = downNode;
                } else {
                    rootNode = storageRoot.getOrCreateDirectory(
                            FileUtil.stringToFile(intent.getStringExtra("parent_title")));
                    downNode = rootNode.getOrCreateDirectory(title);
                    coverRoot = rootNode;
                }

                String danmaku = intent.getStringExtra("danmaku");
                String cover = intent.getStringExtra("cover");
                downloadDanmaku(danmaku, downNode.getOrCreateFile("danmaku.xml", "text/xml"));
                VideoStorageUtil.Node coverNode = coverRoot.getOrCreateFile("cover.png", "image/png");
                if (coverNode.length() == 0L) downloadNode(cover, coverNode, "下载封面", false);
                downloadNode(link, downNode.getOrCreateFile("video.mp4", "video/mp4"),
                        "下载视频", true);
            } catch (Exception e) {
                runOnUiThread(() -> MsgUtil.showMsg("下载失败：目录不可写"));
                e.printStackTrace();
                finish();
            }
        });
    }

    private void downloadFile(String url, File file, String desc, boolean exitOnFinish) {
        try {
            if (!file.exists()) file.createNewFile();
            download(url, new FileOutputStream(file, false), desc, exitOnFinish);
        } catch (IOException e) {
            fail(e);
        }
    }

    private void downloadNode(String url, VideoStorageUtil.Node file, String desc, boolean exitOnFinish) {
        try {
            download(url, file.openOutput(false), desc, exitOnFinish);
        } catch (IOException e) {
            fail(e);
        }
    }

    private void download(String url, OutputStream output,
                          String desc, boolean exitOnFinish) {
        dldText = desc;
        try (Response response = NetWorkUtil.get(url,
                no_bili_headers ? AppInfoApi.customHeaders : NetWorkUtil.webHeaders);
             InputStream input = Objects.requireNonNull(response.body()).byteStream();
             OutputStream target = output) {
            byte[] bytes = new byte[1024 * 10];
            long total = response.body().contentLength();
            long complete = 0L;
            int len;
            while ((len = input.read(bytes)) != -1) {
                target.write(bytes, 0, len);
                complete += len;
                if (total > 0) dldPercent = 1.0f * complete / total;
            }
            target.flush();
            if (exitOnFinish) finishSuccessfully();
        } catch (IOException e) {
            fail(e);
        }
    }

    private void downloadDanmaku(String url, VideoStorageUtil.Node target) {
        try (Response response = NetWorkUtil.get(url,
                no_bili_headers ? AppInfoApi.customHeaders : NetWorkUtil.webHeaders);
             OutputStream output = target.openOutput(false)) {
            byte[] bytes = decompress(Objects.requireNonNull(response.body()).bytes());
            output.write(bytes);
            output.flush();
        } catch (IOException e) {
            runOnUiThread(() -> MsgUtil.showMsg("弹幕下载失败！"));
            e.printStackTrace();
        }
    }

    private void finishSuccessfully() {
        runOnUiThread(() -> MsgUtil.showMsg("下载完成"));
        Timer finishTimer = new Timer();
        finishTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                finish = true;
                finish();
            }
        }, 200);
    }

    private void fail(Exception e) {
        runOnUiThread(() -> MsgUtil.showMsg("下载失败"));
        e.printStackTrace();
        finish();
    }

    public static byte[] decompress(byte[] data) {
        byte[] output;
        Inflater decompresser = new Inflater(true);
        decompresser.reset();
        decompresser.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        try {
            byte[] buf = new byte[2048];
            while (!decompresser.finished()) {
                int count = decompresser.inflate(buf);
                out.write(buf, 0, count);
            }
            output = out.toByteArray();
        } catch (Exception e) {
            output = data;
            e.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {
            }
            decompresser.end();
        }
        return output;
    }

    @Override
    protected void onDestroy() {
        timer.cancel();
        if (!finish) {
            if (type != 0 && downNode != null) downNode.deleteRecursive();
            else if (downFile != null) downFile.delete();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
