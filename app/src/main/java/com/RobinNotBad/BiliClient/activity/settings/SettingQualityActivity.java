package com.RobinNotBad.BiliClient.activity.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.adapter.QualityChooseAdapter;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.api.VipApi;
import com.RobinNotBad.BiliClient.model.VipInfo;
import com.RobinNotBad.BiliClient.model.DashData;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.VipQualityPolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

public class SettingQualityActivity extends BaseActivity {
    private static final String VIP_60FPS = "vip_60fps_enabled";
    private static final String VIP_60FPS_MID = "vip_60fps_mid";
    private static final String VIP_60FPS_CHECKED = "vip_60fps_checked";
    QualityChooseAdapter adapter;

    static LinkedHashMap<String, Integer> getQnMap() {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        map.put("自动（视频最高画质）", 0);
        if (supportsVipHighFrameRate()) {
            map.put(DashData.getQualityLabel(DashData.QN_4K) + "（大会员）", DashData.QN_4K);
            map.put(DashData.getQualityLabel(DashData.QN_1080P_60) + "（大会员）", DashData.QN_1080P_60);
            map.put(DashData.getQualityLabel(DashData.QN_1080P_PLUS) + "（大会员）", DashData.QN_1080P_PLUS);
        }
        map.put(DashData.getQualityLabel(DashData.QN_1080P), DashData.QN_1080P);
        if (supportsVipHighFrameRate()) {
            map.put(DashData.getQualityLabel(DashData.QN_720P_60) + "（大会员）", DashData.QN_720P_60);
        }
        map.put(DashData.getQualityLabel(DashData.QN_720P), DashData.QN_720P);
        map.put(DashData.getQualityLabel(DashData.QN_480P), DashData.QN_480P);
        map.put(DashData.getQualityLabel(DashData.QN_360P), DashData.QN_360P);
        return map;
    }

    private static boolean supportsVipHighFrameRate() {
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        return VipQualityPolicy.shouldShowVipQualities(
                mid,
                SharedPreferencesUtil.getLong(VIP_60FPS_MID, 0),
                SharedPreferencesUtil.getBoolean(VIP_60FPS, false),
                SharedPreferencesUtil.getLong(VIP_60FPS_CHECKED, 0),
                System.currentTimeMillis());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_simple_list);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        findViewById(R.id.top).setOnClickListener(view -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        setPageName("请选择清晰度");

        adapter = new QualityChooseAdapter(this);
        adapter.setNameList(new ArrayList<>(getQnMap().keySet()));
        adapter.setOnItemClickListener((this::save));

        recyclerView.setLayoutManager(new CustomLinearManager(this));
        recyclerView.setAdapter(adapter);
        refreshVipState();
    }

    private void refreshVipState() {
        final long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (mid == 0) {
            SharedPreferencesUtil.putBoolean(VIP_60FPS, false);
            SharedPreferencesUtil.putLong(VIP_60FPS_MID, 0);
            adapter.setNameList(new ArrayList<>(getQnMap().keySet()));
            normalizeSavedQuality();
            return;
        }

        long checked = SharedPreferencesUtil.getLong(VIP_60FPS_CHECKED, 0);
        if (VipQualityPolicy.hasFreshStatus(mid,
                SharedPreferencesUtil.getLong(VIP_60FPS_MID, 0), checked,
                System.currentTimeMillis())) {
            normalizeSavedQuality();
            return;
        }

        CenterThreadPool.run(() -> {
            Boolean vip = null;
            try {
                VipInfo info = VipApi.getVipInfo();
                vip = info.isVip && !info.isOverdueVip && info.vipStatus == 1;
            } catch (Exception ignored) {
                // A temporary network failure must not turn a confirmed member into a non-member.
            }
            if (vip != null) {
                SharedPreferencesUtil.putBoolean(VIP_60FPS, vip);
                SharedPreferencesUtil.putLong(VIP_60FPS_MID, mid);
                SharedPreferencesUtil.putLong(VIP_60FPS_CHECKED, System.currentTimeMillis());
            }
            runOnUiThread(() -> {
                adapter.setNameList(new ArrayList<>(getQnMap().keySet()));
                normalizeSavedQuality();
            });
        });
    }

    private void normalizeSavedQuality() {
        int saved = SharedPreferencesUtil.getInt("play_qn", 0);
        long mid = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        if (VipQualityPolicy.shouldResetRestrictedQuality(
                saved,
                mid,
                SharedPreferencesUtil.getLong(VIP_60FPS_MID, 0),
                SharedPreferencesUtil.getBoolean(VIP_60FPS, false),
                SharedPreferencesUtil.getLong(VIP_60FPS_CHECKED, 0),
                System.currentTimeMillis())) {
            // 会员失效后按当前视频实际可用的最高画质播放，避免卡在受限的高帧率档位。
            SharedPreferencesUtil.putInt("play_qn", 0);
        }
    }

    private void save(int position) {
        String str = adapter.getName(position);
        LinkedHashMap<String, Integer> qnMap = getQnMap();
        if (qnMap.containsKey(str))
            SharedPreferencesUtil.putInt("play_qn", Objects.requireNonNull(qnMap.get(str)));
        finish();
    }
}
