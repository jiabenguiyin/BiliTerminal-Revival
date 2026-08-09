package com.RobinNotBad.BiliClient.activity.update;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.DownloadActivity;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.api.AppInfoApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.BinaryPatchUtil;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.HotConfigManager;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UpdateInfoActivity extends BaseActivity {

    TextView versionNameTv;
    TextView versionCodeTv;
    TextView isReleaseTv;
    TextView pubTimeTv;
    TextView updateLogTv;
    MaterialButton downloadBtn;

    File apkFile;
    File downloadFile;
    HotConfigManager.UpdatePlan updatePlan;
    volatile boolean verifyingApk;

    final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<>() {
        @Override
        public void onActivityResult(ActivityResult o) {
            Logu.d("下载回调", "onActivityResult");
            if (downloadFile != null && downloadFile.exists()) {
                downloadComplete();
            }
        }
    });

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String versionName = intent.getStringExtra("versionName");
        int versionCode = intent.getIntExtra("versionCode", -1);
        int isRelease = intent.getIntExtra("isRelease", 0);
        long ctime = intent.getLongExtra("ctime", -1);
        String updateLog = intent.getStringExtra("updateLog");
        int canDownload = intent.getIntExtra("canDownload", 0);

        asyncInflate(R.layout.activity_update_info, (layoutView, resId) -> {

            versionNameTv = findViewById(R.id.versionName);
            versionCodeTv = findViewById(R.id.versionCode);
            isReleaseTv = findViewById(R.id.isRelease);
            pubTimeTv = findViewById(R.id.pubTime);
            updateLogTv = findViewById(R.id.updateLog);
            downloadBtn = findViewById(R.id.download);

            if (versionName == null || versionCode == -1) {
                finish();
                return;
            }

            versionNameTv.setText(String.format("版本名: %s", versionName));
            versionCodeTv.setText(String.format(Locale.getDefault(), "版本号: %d", versionCode));
            isReleaseTv.setText(String.format("是否为正式版: %s", isRelease == 1 ? "是" : "否"));
            pubTimeTv.setText(String.format("发布时间: %s", ctime == -1 ? "未知" : new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(String.valueOf(ctime).length() == 13 ? ctime : ctime * 1000))));
            updateLogTv.setText(TextUtils.isEmpty(updateLog) ? "暂无更新日志" : updateLog);

            if (canDownload != 1) downloadBtn.setVisibility(View.GONE);
            downloadBtn.setOnClickListener((view1) -> {
                if (!FileUtil.checkStoragePermission()) {
                    MsgUtil.showMsg("请授权存储空间权限");
                    FileUtil.requestStoragePermission(this);
                    return;
                }

                CenterThreadPool.run(() -> {
                    try {
                        runOnUiThread(() -> MsgUtil.showMsg("获取下载地址……"));
                        updatePlan = AppInfoApi.getUpdatePlan(versionCode);
                        downloadFile = new File(
                                FileUtil.getDownloadPath(),
                                FileUtil.getFileNameFromLink(updatePlan.downloadUrl)
                        );
                        apkFile = updatePlan.differential
                                ? new File(FileUtil.getDownloadPath(), FileUtil.getFileNameFromLink(updatePlan.fullUrl))
                                : downloadFile;
                        if (downloadFile.exists()) {
                            MsgUtil.showMsg(updatePlan.differential ? "已有本地增量包，正在优化……" : "已有本地安装包！");
                            downloadComplete();
                            return;
                        }

                        runOnUiThread(() -> {
                            if (updatePlan.differential) MsgUtil.showMsg("使用增量更新，失败会自动下载完整包");
                            launchDownload(updatePlan.downloadUrl);
                        });

                    } catch (Throwable e) {
                        MsgUtil.err("下载更新", e);
                    }
                });



                /*
                CenterThreadPool.run(() -> {
                    try {
                        runOnUiThread(() -> MsgUtil.showMsg("开始获取下载地址"));
                        String url = AppInfoApi.getDownloadUrl(versionCode);
                        if (TextUtils.isEmpty(url)) {
                            runOnUiThread(() -> MsgUtil.showMsg("没有该版本的下载地址"));
                        } else {
                            Request request = new Request.Builder()
                                    .get()
                                    .url(url)
                                    .build();

                            NetWorkUtil.getOkHttpInstance().newCall(request).enqueue(new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    runOnUiThread(() -> {
                                        MsgUtil.showMsg("下载失败: " + e.getMessage());
                                        downloadBtn.setText("下载");
                                    });
                                }

                                @Override
                                public void onResponse(@NonNull Call call, @NonNull Response response) {
                                    if (!response.isSuccessful()) {
                                        runOnUiThread(() -> MsgUtil.showMsg("下载失败！"));
                                    } else {
                                        try {
                                            downloading = true;
                                            stopDownload = false;
                                            ResponseBody responseBody = response.body();
                                            if (responseBody != null) {
                                                long fileSize = responseBody.contentLength();
                                                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BiliTerminal_" + versionCode + ".apk");
                                                if (file.exists() && !file.delete()) {
                                                    int num = 1;
                                                    while (file.exists()) {
                                                        if (file.delete()) break;
                                                        file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BiliTerminal_" + versionCode + "(" + num++ + ").apk");
                                                    }
                                                }
                                                FileOutputStream fos = new FileOutputStream(file);
                                                int bytesRead;
                                                byte[] data = new byte[1024];
                                                long totalBytesRead = 0;
                                                while ((bytesRead = responseBody.byteStream().read(data)) != -1) {
                                                    if (stopDownload) break;
                                                    fos.write(data, 0, bytesRead);
                                                    totalBytesRead += bytesRead;
                                                    int progress = (int) ((totalBytesRead * 100) / fileSize);
                                                    runOnUiThread(()->downloadBtn.setText(String.format(Locale.getDefault(), "下载(%d%%)", progress)));
                                                }
                                                fos.flush();
                                                fos.close();
                                                if (!stopDownload) {
                                                    startActivity(new Intent(UpdateInfoActivity.this, UpdateDownloadResultActivity.class)
                                                            .putExtra("path", file.getAbsolutePath()));
                                                } else {
                                                    if (file.delete()) {
                                                        runOnUiThread(() -> MsgUtil.showMsg("已中止下载并删除文件"));
                                                    } else {
                                                        runOnUiThread(() -> MsgUtil.showMsg("已中止下载，删除文件失败"));
                                                    }
                                                }
                                                stopDownload = false;
                                                downloading = false;
                                            }
                                        } catch (Throwable th) {
                                            MsgUtil.err("下载出错：",th);
                                            downloading = false;
                                        }
                                    }
                                    downloadBtn.setText("下载");
                                }
                            });
                        }
                    } catch (IOException e) {
                        runOnUiThread(() -> MsgUtil.showMsg("网络异常"));
                    } catch (Throwable th) {
                        runOnUiThread(() -> MsgUtil.showMsg("错误: " + th.getMessage()));
                    }
                });
                 */
            });
        });
    }

    private void downloadComplete() {
        if (verifyingApk) return;
        verifyingApk = true;
        CenterThreadPool.run(() -> {
            HotConfigManager.UpdatePlan plan = updatePlan;
            boolean verified;
            if (plan != null) {
                verified = HotConfigManager.verifyFile(
                        downloadFile,
                        plan.downloadSha256,
                        plan.downloadSize
                );
            } else {
                verified = HotConfigManager.verifyDownloadedUpdate(apkFile);
            }
            if (!verified) {
                if (downloadFile != null && downloadFile.exists()) downloadFile.delete();
                verifyingApk = false;
                runOnUiThread(() -> MsgUtil.showMsg("更新包校验失败，已删除，请重新下载"));
                return;
            }

            if (plan != null && plan.differential) {
                try {
                    File installedApk = new File(getApplicationInfo().sourceDir);
                    runOnUiThread(() -> MsgUtil.showMsg("正在合成并校验完整安装包……"));
                    BinaryPatchUtil.applyBsdiff(installedApk, downloadFile, apkFile);
                    if (!HotConfigManager.verifyFile(apkFile, plan.fullSha256, plan.fullSize)) {
                        throw new IllegalStateException("增量更新输出校验失败");
                    }
                    downloadFile.delete();
                } catch (Throwable error) {
                    Logu.e("terminal-update", "binary patch failed: " + error);
                    if (apkFile != null && apkFile.exists()) apkFile.delete();
                    if (downloadFile != null && downloadFile.exists()) downloadFile.delete();
                    verifyingApk = false;
                    fallbackToFullPackage(plan);
                    return;
                }
            }

            if (!HotConfigManager.verifyApkIdentity(apkFile)) {
                if (apkFile != null && apkFile.exists()) apkFile.delete();
                verifyingApk = false;
                runOnUiThread(() -> MsgUtil.showMsg("安装包身份校验失败，已删除，请重新下载"));
                return;
            }

            runOnUiThread(() -> {
                SharedPreferencesUtil.putString("terminal_update_pkg", apkFile.getAbsolutePath());
                downloadBtn.setVisibility(View.GONE);
                MaterialButton installBtn = findViewById(R.id.install);
                MaterialButton deleteBtn = findViewById(R.id.delete);
                installBtn.setVisibility(View.VISIBLE);
                deleteBtn.setVisibility(View.VISIBLE);

                installBtn.setOnClickListener(v -> startActivity(new Intent(this, UpdateInstallActivity.class)
                        .putExtra("path", apkFile.getAbsolutePath())));

                deleteBtn.setOnClickListener(v -> CenterThreadPool.run(() -> {
                    if (apkFile.delete()) MsgUtil.showMsg("删除成功");
                    else MsgUtil.showMsg("删除失败");
                    SharedPreferencesUtil.putString("terminal_update_pkg", "");
                    finish();
                    startActivity(getIntent());
                }));
                verifyingApk = false;
            });
        });
    }

    private void fallbackToFullPackage(HotConfigManager.UpdatePlan failedPlan) {
        updatePlan = failedPlan.asFull();
        downloadFile = new File(
                FileUtil.getDownloadPath(),
                FileUtil.getFileNameFromLink(updatePlan.fullUrl)
        );
        apkFile = downloadFile;
        runOnUiThread(() -> {
            MsgUtil.showMsg("增量更新失败，已切换完整安装包");
            if (downloadFile.exists()) downloadComplete();
            else launchDownload(updatePlan.fullUrl);
        });
    }

    private void launchDownload(String url) {
        Intent downloadIntent = new Intent(this, DownloadActivity.class)
                .putExtra("link", url)
                .putExtra("path", FileUtil.getDownloadPath().getAbsolutePath())
                .putExtra("terminal", true)
                .putExtra("type", 0);
        launcher.launch(downloadIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
