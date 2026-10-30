package com.RobinNotBad.BiliClient.activity.video;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.DownloadActivity;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.api.HistoryApi;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import org.json.JSONException;

import java.io.IOException;

public class JumpToPlayerActivity extends BaseActivity {
    String title;
    TextView textView;

    PlayerData playerData;

    int download;

    final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<>() {
        @Override
        public void onActivityResult(ActivityResult o) {
            int code = o.getResultCode();
            Intent result = o.getData();
            Logu.d("进度回调", "onActivityResult");
            if (code == RESULT_OK && result != null) {
                int progress = result.getIntExtra("progress", 0);
                Logu.d("进度回调", String.valueOf(progress));

                // History reporting is auxiliary. Do not keep the transition
                // page open or show an error when a weak network drops it.
                finish();
                CenterThreadPool.run(() -> {
                    reportHistoryBestEffort(progress / 1000);
                });
            }
        }
    });

    private void reportHistoryBestEffort(long progressSeconds) {
        if (playerData == null || playerData.mid == 0 || playerData.aid == 0) return;

        Exception lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HistoryApi.reportHistory(playerData.aid, playerData.cid, progressSeconds);
                Logu.d("进度上报", "成功");
                return;
            } catch (Exception e) {
                lastError = e;
                if (attempt == 0) SystemClock.sleep(500L);
            }
        }

        // Reporting must never turn a successful playback into a visible error.
        Logu.e("进度上报", "后台上报失败，已忽略: "
                + (lastError == null ? "unknown" : lastError.toString()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_jump);

        textView = findViewById(R.id.text_title);

        Intent intent = getIntent();
        Log.e("debug-哔哩终端-跳转页", "已接收数据");

        playerData = (PlayerData) intent.getParcelableExtra("data");

        title = playerData.title;

        download = intent.getIntExtra("download", 0);

        playerData.qn = playerData.qn != -1 ? playerData.qn : SharedPreferencesUtil.getInt("play_qn", 0);

        requestVideo();
    }

    @SuppressLint("SetTextI18n")
    private void requestVideo() {
        CenterThreadPool.run(() -> {

            try {
                if (playerData.isBangumi()) PlayerApi.getBangumi(playerData);
                else if (download == 0) PlayerApi.getVideoForPlayback(playerData);
                else PlayerApi.getVideo(playerData, true);

                Logu.d("history", String.valueOf(playerData.progress));
                jump();
            } catch (IOException e) {
                setClickExit("网络错误！\n请检查你的网络连接是否正常");
            } catch (JSONException e) {
                setClickExit("视频获取失败！\n可能的原因：\n1.本视频仅大会员可播放\n2.视频获取接口失效\n\n清除应用数据也许可以解决" + e.getMessage());
                e.printStackTrace();
            } catch (ActivityNotFoundException e) {
                setClickExit("跳转失败！\n请安装对应的播放器\n或在设置中选择正确的播放器\n或将哔哩终端和播放器同时更新到最新版本");
                e.printStackTrace();
            }
        });
    }

    private void jump() {
        if (isDestroyed()) return;
        if (download == 0) {
            Intent intent = PlayerApi.jumpToPlayer(playerData);
            launcher.launch(intent);
            setClickExit("等待退出播放后上报进度\n（点击跳过）");
        } else {
            Intent intent = new Intent();
            intent.setClass(this, DownloadActivity.class);
            intent.putExtra("type", download);
            intent.putExtra("link", playerData.videoUrl);
            intent.putExtra("danmaku", playerData.danmakuUrl);
            intent.putExtra("title", title);
            intent.putExtra("cover", getIntent().getStringExtra("cover"));
            if (download == 2)
                intent.putExtra("parent_title", getIntent().getStringExtra("parent_title"));
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void setClickExit(String reason) {
        runOnUiThread(() -> {
            textView.setText(reason);
            textView.setOnClickListener((view) -> finish());
        });
    }
}
