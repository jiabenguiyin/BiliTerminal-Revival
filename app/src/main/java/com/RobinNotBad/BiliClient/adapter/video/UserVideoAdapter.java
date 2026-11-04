package com.RobinNotBad.BiliClient.adapter.video;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.video.series.UserSeriesActivity;
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.TerminalContext;

import java.util.List;

//用户视频列表专用Adapter

public class UserVideoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    final Context context;
    final long mid;
    final List<VideoCard> videoCardList;
    private Runnable playUploads;
    private Runnable chooseSort;
    private int sort;

    public void setOnPlayUploads(Runnable action) { playUploads = action; }
    public void setOnChooseSort(Runnable action) { chooseSort = action; }
    public void setSort(int value) { sort = com.RobinNotBad.BiliClient.api.UserVideoListSource.normalize(value); notifyItemChanged(0); }
    public static int sortLabel(int value) {
        switch (value) {
            case 1: return R.string.user_video_sort_played;
            case 2: return R.string.user_video_sort_favorited;
            case 3: return R.string.user_video_sort_oldest;
            default: return R.string.user_video_sort_newest;
        }
    }

    public UserVideoAdapter(Context context, long mid, List<VideoCard> videoCardList) {
        this.context = context;
        this.mid = mid;
        this.videoCardList = videoCardList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 0 || viewType == 2) {
            View view = LayoutInflater.from(context).inflate(viewType == 2 ? R.layout.cell_user_video_header : R.layout.cell_goto, parent, false);
            return new RecyclerView.ViewHolder(view) {
            };
        } else {
            View view = LayoutInflater.from(this.context).inflate(R.layout.cell_video_list, parent, false);
            return new VideoCardHolder(view);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position < 2) {
            TextView textView = holder.itemView.findViewById(R.id.text);
            if (position == 1) {
                textView.setText(R.string.uploader_playback_entry);
                holder.itemView.setContentDescription(context.getString(R.string.uploader_playback_entry_desc));
                holder.itemView.setOnClickListener(view -> { if (playUploads != null) playUploads.run(); });
                return;
            }
            textView.setText(R.string.user_video_series);
            textView.setOnClickListener(view -> {
                Intent intent = new Intent(context, UserSeriesActivity.class);
                intent.putExtra("mid", mid);
                context.startActivity(intent);
            });
            TextView sortView = holder.itemView.findViewById(R.id.user_video_sort);
            sortView.setText(sortLabel(sort));
            sortView.setContentDescription(context.getString(R.string.user_video_sort_description, context.getString(sortLabel(sort))));
            sortView.setOnClickListener(view -> { if (chooseSort != null) chooseSort.run(); });
        } else {
            int realPosition = position - 2;
            if (realPosition < 0 || realPosition >= videoCardList.size())
                return;
            VideoCardHolder videoCardHolder = (VideoCardHolder) holder;
            VideoCard videoCard = videoCardList.get(realPosition);
            if (videoCard == null)
                return;

            videoCardHolder.showVideoCard(videoCard, context);

            holder.itemView.setOnClickListener(
                    view -> TerminalContext.getInstance().enterVideoDetailPage(context, videoCard.aid, videoCard.bvid));
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof DynamicHolder)
            ((DynamicHolder) holder).extraCard.removeAllViews();
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return videoCardList != null ? videoCardList.size() + 2 : 2;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? 2 : position == 1 ? 0 : 1;
    }
}
