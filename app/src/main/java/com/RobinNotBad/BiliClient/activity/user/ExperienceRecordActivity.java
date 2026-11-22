package com.RobinNotBad.BiliClient.activity.user;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity;
import com.RobinNotBad.BiliClient.api.ExperienceRecordApi;
import com.RobinNotBad.BiliClient.api.ExperienceRecordApi.Record;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.ArrayList;

public class ExperienceRecordActivity extends RefreshListActivity {
    private final ArrayList<Record> records = new ArrayList<>();
    private final RecordAdapter adapter = new RecordAdapter();
    private long account;
    private String cookie = "";
    private int generation;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setPageName(getString(R.string.experience_records));
        setAdapter(adapter);
        setOnRefreshListener(this::loadRecords);
        emptyView.setOnClickListener(view -> loadRecords());
        loadRecords();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!ExperienceRecordApi.isCurrent(account, cookie)) loadRecords();
    }

    private void loadRecords() {
        final int request = ++generation;
        account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        final long requestedAccount = account;
        final String requestedCookie = cookie;
        records.clear();
        adapter.notifyDataSetChanged();
        hideEmptyView();
        setRefreshing(true);
        CenterThreadPool.run(() -> {
            ArrayList<Record> result = null;
            Exception failure = null;
            try {
                result = ExperienceRecordApi.getRecords(requestedAccount, requestedCookie);
            } catch (Exception error) {
                failure = error;
            }
            final ArrayList<Record> loaded = result;
            final Exception error = failure;
            runOnUiThread(() -> {
                if (isDestroyed() || request != generation) return;
                setRefreshing(false);
                if (!ExperienceRecordApi.isCurrent(requestedAccount, requestedCookie)) {
                    emptyView.setText(R.string.experience_records_account_changed);
                    showEmptyView();
                    return;
                }
                if (error != null) {
                    Logu.w("ExperienceRecord", "经验记录加载失败: " + error.getClass().getSimpleName());
                    String message = error instanceof ExperienceRecordApi.Failure
                            ? error.getMessage() : getString(R.string.experience_records_load_failed);
                    emptyView.setText(message + "\n" + getString(R.string.experience_records_retry));
                    showEmptyView();
                    return;
                }
                records.addAll(loaded);
                adapter.notifyDataSetChanged();
                if (records.isEmpty()) {
                    emptyView.setText(R.string.experience_records_empty);
                    showEmptyView();
                }
            });
        });
    }

    @Override protected void onDestroy() {
        generation++;
        super.onDestroy();
    }

    private final class RecordAdapter extends RecyclerView.Adapter<RecordHolder> {
        @NonNull @Override public RecordHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_experience_record, parent, false);
            return new RecordHolder(view);
        }

        @Override public void onBindViewHolder(@NonNull RecordHolder holder, int position) {
            Record record = records.get(position);
            holder.reason.setText(record.reason);
            holder.time.setText(record.time);
            holder.delta.setText(record.delta > 0 ? "+" + record.delta : Long.toString(record.delta));
            holder.delta.setTextColor(ContextCompat.getColor(ExperienceRecordActivity.this,
                    record.delta > 0 ? R.color.pink : R.color.textwhite));
        }

        @Override public int getItemCount() { return records.size(); }
    }

    private static final class RecordHolder extends RecyclerView.ViewHolder {
        final TextView reason;
        final TextView time;
        final TextView delta;

        RecordHolder(View view) {
            super(view);
            reason = view.findViewById(R.id.experience_reason);
            time = view.findViewById(R.id.experience_time);
            delta = view.findViewById(R.id.experience_delta);
        }
    }
}
