package com.RobinNotBad.BiliClient.player;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.InteractionVideoData;
import com.RobinNotBad.BiliClient.model.ViewPoint;
import com.RobinNotBad.BiliClient.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/** Compact metadata that belongs to the player's visible controls, not the video. */
public final class PlayerInfoOverlay {
    public interface SeekListener { void seek(long positionMs); }

    private final View valuesScroll;
    private final TextView values;
    private final RecyclerView chapters;
    private final ChapterAdapter adapter;

    public PlayerInfoOverlay(View valuesScroll, TextView values, RecyclerView chapters, SeekListener listener) {
        this.valuesScroll = valuesScroll;
        this.values = values;
        this.chapters = chapters;
        adapter = new ChapterAdapter(listener);
        chapters.setLayoutManager(new LinearLayoutManager(chapters.getContext(), LinearLayoutManager.HORIZONTAL, false));
        chapters.setNestedScrollingEnabled(false);
        chapters.setItemAnimator(null);
        chapters.setAdapter(adapter);
    }

    public void setChapters(List<ViewPoint> points) {
        adapter.points.clear();
        if (points != null) for (ViewPoint point : points) {
            if (point != null && point.from >= 0) adapter.points.add(point);
        }
        adapter.notifyDataSetChanged();
    }

    public void show(boolean controlsVisible, boolean videoMode, InteractionVideoData data, boolean showChapters) {
        String text = visibleValues(data);
        if (!text.contentEquals(values.getText())) values.setText(text);
        valuesScroll.setVisibility(controlsVisible && videoMode && !text.isEmpty() ? View.VISIBLE : View.GONE);
        chapters.setVisibility(controlsVisible && videoMode && showChapters && !adapter.points.isEmpty()
                ? View.VISIBLE : View.GONE);
    }

    public static String visibleValues(InteractionVideoData data) {
        StringBuilder text = new StringBuilder();
        if (data != null && data.hiddenVars != null) for (InteractionVideoData.InteractionHiddenVar var : data.hiddenVars) {
            if (var == null || var.isShow != 1 || var.name == null || var.name.trim().isEmpty()) continue;
            if (text.length() > 0) text.append("    ");
            text.append(var.name.replace('\n', ' ').replace('\r', ' ')).append(": ").append(var.value);
        }
        return text.toString();
    }

    private static final class ChapterAdapter extends RecyclerView.Adapter<ChapterHolder> {
        final List<ViewPoint> points = new ArrayList<>();
        final SeekListener listener;
        ChapterAdapter(SeekListener listener) { this.listener = listener; }
        @NonNull @Override public ChapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            TextView text = new TextView(parent.getContext());
            float density = parent.getResources().getDisplayMetrics().density;
            text.setLayoutParams(new RecyclerView.LayoutParams(Math.round(128 * density), ViewGroup.LayoutParams.MATCH_PARENT));
            int padding = Math.round(6 * density);
            text.setPadding(padding, 0, padding, 0);
            text.setGravity(Gravity.CENTER_VERTICAL);
            text.setTextSize(10);
            text.setTextColor(0xffebe0e2);
            text.setMaxLines(2);
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.setBackgroundDrawable(AppCompatResources.getDrawable(parent.getContext(), R.drawable.background_button_compat));
            text.setFocusable(true);
            return new ChapterHolder(text);
        }
        @Override public void onBindViewHolder(@NonNull ChapterHolder holder, int position) {
            ViewPoint point = points.get(position);
            String time = StringUtil.toTime(point.from);
            String title = point.content == null ? "" : point.content;
            holder.text.setText(time + "\n" + title);
            holder.text.setContentDescription(time + " " + title);
            holder.text.setOnClickListener(view -> listener.seek(point.from * 1000L));
        }
        @Override public int getItemCount() { return points.size(); }
    }

    private static final class ChapterHolder extends RecyclerView.ViewHolder {
        final TextView text;
        ChapterHolder(TextView text) { super(text); this.text = text; }
    }
}
