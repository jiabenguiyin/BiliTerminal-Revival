package com.RobinNotBad.BiliClient.activity.user;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.api.CreatorArchiveApi;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.StringUtil;
import org.json.JSONObject;

public class CreativeCenterActivity extends BaseActivity {
    private CreatorArchiveApi.Session session;
    private boolean ready;
    private int generation;
    private TextView status;
    private static final int[] VALUES = {R.id.totalFans_number, R.id.totalClick_number,
            R.id.totalLike_number, R.id.totalCoin_number, R.id.totalFavourite_number,
            R.id.totalShare_number, R.id.totalReply_number, R.id.totalDm_number};
    private static final String[] TOTALS = {"total_fans", "total_click", "total_like", "total_coin",
            "total_fav", "total_share", "total_reply", "total_dm"};
    private static final String[] INCREMENTS = {"incr_fans", "incr_click", "inc_like", "inc_coin",
            "inc_fav", "inc_share", "incr_reply", "incr_dm"};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        asyncInflate(R.layout.activity_creative_center, (view, id) -> {
            if (isDestroyed()) return;
            setTopbarExit();
            status = findViewById(R.id.creator_statistics_status);
            findViewById(R.id.creator_manage).setOnClickListener(v ->
                    startActivity(new Intent(this, CreatorArchiveActivity.class)));
            status.setOnClickListener(v -> refresh());
            ready = true;
            refresh();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (ready && session != null && !session.isCurrent()) refresh();
    }

    private void refresh() {
        final int request = ++generation;
        for (int id : VALUES) ((TextView) findViewById(id)).setText("—");
        ((TextView) findViewById(R.id.beUpTime)).setText("—");
        status.setText("正在加载投稿数据…");
        try { session = CreatorArchiveApi.capture(); }
        catch (CreatorArchiveApi.Failure error) { status.setText(error.getMessage() + " · 点击重试"); return; }
        final CreatorArchiveApi.Session account = session;
        // Keep archive management available if statistics are unavailable.
        CenterThreadPool.run(() -> {
            try {
                JSONObject stats = CreatorArchiveApi.statistics(account);
                runOnUiThread(() -> {
                    if (!accepts(request, account)) return;
                    for (int i = 0; i < VALUES.length; i++) setStatsText(VALUES[i], stats, TOTALS[i], INCREMENTS[i]);
                    status.setText("当前账号投稿数据 · 点击刷新");
                });
                try {
                    String age = CreatorArchiveApi.creatorAge(account);
                    runOnUiThread(() -> { if (accepts(request, account)) ((TextView) findViewById(R.id.beUpTime)).setText(age); });
                } catch (Exception ignored) { /* Age is optional. Keep successful statistics. */ }
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (accepts(request, account)) status.setText(CreatorArchiveApi.message(error) + " · 点击重试");
                });
            }
        });
    }

    private boolean accepts(int request, CreatorArchiveApi.Session account) {
        if (isDestroyed() || request != generation || session != account) return false;
        if (!account.isCurrent()) { refresh(); return false; }
        return true;
    }

    private void setStatsText(int viewId, JSONObject object, String totalKey, String incrementKey) {
        String value = object.has(totalKey) && !object.isNull(totalKey) ? StringUtil.toWan(object.optLong(totalKey)) : "—";
        if (object.has(incrementKey) && !object.isNull(incrementKey)) {
            long increment = object.optLong(incrementKey);
            value += " (" + (increment < 0 ? "" : "+") + StringUtil.toWan(increment) + ")";
        }
        ((TextView) findViewById(viewId)).setText(value);
    }

    @Override protected void onDestroy() { generation++; super.onDestroy(); }
}
