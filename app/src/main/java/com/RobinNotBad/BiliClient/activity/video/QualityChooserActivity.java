package com.RobinNotBad.BiliClient.activity.video;

import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.model.DashData;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import org.json.JSONException;
import java.io.IOException;
import java.util.ArrayList;

public class QualityChooserActivity extends BaseActivity {
    int[] qns = new int[0];
    private boolean downloadRequestPending;
    private int qualityGeneration;
    private DownloadSource downloadSource = new DownloadSource();

    /** Keeps fetching stream data separate from committing a download task. */
    public static class DownloadSource {
        public void loadVideo(PlayerData data) throws Exception { PlayerApi.getVideo(data, true); }
        public void loadAudio(PlayerData data) throws Exception { PlayerApi.getAudioForDownload(data); }
        public void enqueueVideo(VideoInfo info, int page, int qn) throws Exception { PlayerApi.startDownloading(info, page, qn); }
        public void enqueueAudio(VideoInfo info, int page, String url) throws Exception { PlayerApi.startDownloadingAudioOnly(info, page, 0, url); }
    }

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_simple_list);
        RecyclerView list = findViewById(R.id.recyclerView);
        findViewById(R.id.top).setOnClickListener(view -> { setResult(RESULT_CANCELED); finish(); });
        ((TextView) findViewById(R.id.pageName)).setText("请选择清晰度");
        long aid = getIntent().getLongExtra("aid", 0);
        String bvid = getIntent().getStringExtra("bvid");
        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(this, result -> result.onSuccess(info -> {
            if (!canUpdateUi()) return;
            int page = getIntent().getIntExtra("page", 0);
            if (info == null || info.cids == null || info.pagenames == null || page < 0
                    || page >= info.cids.size() || page >= info.pagenames.size()) {
                MsgUtil.showMsg("视频分P信息不完整，请返回后重试");
                return;
            }
            DownloadSource source = downloadSource;
            QualityChooseAdapter adapter = new QualityChooseAdapter(this);
            list.setLayoutManager(new CustomLinearManager(this));
            list.setAdapter(adapter);
            adapter.setOnItemClickListener(position -> choose(info, page, source, position));
            int generation = ++qualityGeneration;
            CenterThreadPool.run(() -> {
                ArrayList<Integer> available = new ArrayList<>();
                boolean failed = false;
                try {
                    PlayerData data = info.toPlayerData(page);
                    data.qn=com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.getInt("play_qn",0);
                    source.loadVideo(data);
                    if (data.qnValueList != null) for (int qn : data.qnValueList) {
                        if (DashData.isSupportedQuality(qn) && !available.contains(qn)) available.add(qn);
                    }
                } catch (Exception error) {
                    failed = true;
                    Logu.w("download-quality", "视频画质获取失败：" + error.getClass().getSimpleName());
                }
                ArrayList<String> names = new ArrayList<>();
                int[] values = new int[available.size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = available.get(i);
                    names.add(DashData.getQualityLabel(values[i]));
                }
                // Audio has its own request; missing video qualities must not disable it.
                names.add("仅音频");
                boolean videoFailed = failed;
                runOnUiThread(() -> {
                    if (!canUpdateUi() || generation != qualityGeneration) return;
                    qns = values;
                    adapter.setNameList(names);
                    if (videoFailed) MsgUtil.showMsg("视频清晰度暂不可用，可尝试仅音频缓存");
                });
            });
        }).onFailure(error -> { if (canUpdateUi()) MsgUtil.showMsg("视频信息获取失败，请返回后重试"); }));
    }

    private boolean canUpdateUi() {
        return !isFinishing() && getLifecycle().getCurrentState() != Lifecycle.State.DESTROYED;
    }

    private void choose(VideoInfo info, int page, DownloadSource source, int position) {
        if (!canUpdateUi() || downloadRequestPending || position < 0 || position > qns.length) return;
        downloadRequestPending = true;
        if (position < qns.length) {
            try { source.enqueueVideo(info, page, qns[position]); finish(); }
            catch (Exception error) { downloadRequestPending = false; MsgUtil.showMsg("添加下载失败，请稍后重试"); }
            return;
        }
        CenterThreadPool.run(() -> {
            try {
                PlayerData audio = info.toPlayerData(page);
                audio.qn = 0;
                source.loadAudio(audio);
                if (audio.audioUrl == null || audio.audioUrl.isEmpty()) throw new JSONException("该视频没有可单独缓存的音频流");
                runOnUiThread(() -> {
                    if (!canUpdateUi()) return;
                    try { source.enqueueAudio(info, page, audio.audioUrl); finish(); }
                    catch (Exception error) { downloadRequestPending = false; MsgUtil.showMsg("添加音频下载失败，请稍后重试"); }
                });
            } catch (Exception error) {
                Logu.w("download-audio", "音轨获取失败：" + error.getClass().getSimpleName());
                runOnUiThread(() -> {
                    if (!canUpdateUi()) return;
                    downloadRequestPending = false;
                    String reason = error instanceof IOException ? "网络请求失败，请稍后重试"
                            : error instanceof JSONException ? error.getMessage() : "请返回视频详情后重试";
                    MsgUtil.showMsg("获取音频信息失败：" + reason);
                });
            }
        });
    }
}
