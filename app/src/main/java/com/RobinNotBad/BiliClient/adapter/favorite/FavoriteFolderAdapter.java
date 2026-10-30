package com.RobinNotBad.BiliClient.adapter.favorite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.user.favorite.FavoriteVideoListActivity;
import com.RobinNotBad.BiliClient.activity.user.favorite.FavouriteOpusListActivity;
import com.RobinNotBad.BiliClient.model.FavoriteFolder;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;

//收藏夹Adapter

public class FavoriteFolderAdapter extends RecyclerView.Adapter<FavoriteFolderAdapter.FavoriteHolder> {

    final Context context;
    final ArrayList<FavoriteFolder> folderList;
    final long mid;
    public interface OnFolderLongClickListener { boolean onLongClick(FavoriteFolder folder); }
    private OnFolderLongClickListener longClickListener;

    public void setOnFolderLongClickListener(OnFolderLongClickListener listener) { longClickListener = listener; }

    public FavoriteFolderAdapter(Context context, ArrayList<FavoriteFolder> folderList, long mid) {
        this.context = context;
        this.folderList = folderList;
        this.mid = mid;
    }

    @NonNull
    @Override
    public FavoriteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(this.context).inflate(R.layout.cell_favorite_folder_list, parent, false);
        return new FavoriteHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull FavoriteHolder holder, int position) {
        holder.itemView.setOnLongClickListener(null);
        holder.itemView.setLongClickable(false);
        if (folderList == null)
            return;
        if (position == 0) {
            holder.name.setText("全部");
            holder.count.setText("");
            String cover = folderList.isEmpty() ? "" : folderList.get(0).cover;
            Glide.with(BiliTerminal.context).asDrawable().load(GlideUtil.url(cover))
                    .placeholder(R.mipmap.placeholder)
                    .transition(GlideUtil.getTransitionOptions())
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(ToolsUtil.dp2px(5))))
                    .into(holder.cover);
            holder.itemView.setOnClickListener(view -> context.startActivity(new Intent(context,
                    FavoriteVideoListActivity.class).putExtra("mid", mid)
                    .putExtra("fid", folderList.isEmpty() ? 0L : folderList.get(0).id)
                    .putExtra("all_folders", true).putExtra("name", "全部收藏")));
        } else if (position == folderList.size() + 1) {
            holder.name.setText("图文收藏夹");
            holder.count.setText("");
            Glide.with(BiliTerminal.context).asDrawable()
                    .load(StringUtil.getDrawable(context, R.drawable.article_fav_cover))
                    .transition(GlideUtil.getTransitionOptions())
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(ToolsUtil.dp2px(5))))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(holder.cover);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, FavouriteOpusListActivity.class);
                context.startActivity(intent);
            });
        } else if (position > 0 && position <= folderList.size()) {
            FavoriteFolder folder = folderList.get(position - 1);
            if (folder == null)
                return;
            holder.itemView.setOnLongClickListener(view -> longClickListener != null && longClickListener.onLongClick(folder));

            holder.name.setText(StringUtil.htmlToString(folder.name));
            // 当 maxCount 为 0 或无效时，只显示视频数量
            if (folder.maxCount > 0) {
                holder.count.setText(folder.videoCount + "/" + folder.maxCount);
            } else {
                holder.count.setText(String.valueOf(folder.videoCount));
            }
            Glide.with(BiliTerminal.context).asDrawable().load(GlideUtil.url(folder.cover))
                    .transition(GlideUtil.getTransitionOptions())
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(ToolsUtil.dp2px(5))))
                    .format(DecodeFormat.PREFER_RGB_565)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(holder.cover);
            holder.itemView.setOnClickListener(view -> {
                Intent intent = new Intent();
                intent.setClass(context, FavoriteVideoListActivity.class);
                intent.putExtra("fid", folder.id);
                intent.putExtra("mid", mid);
                intent.putExtra("name", folder.name);
                context.startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return folderList != null ? folderList.size() + 2 : 2;
    }

    public static class FavoriteHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView count;
        final ImageView cover;

        public FavoriteHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_title);
            count = itemView.findViewById(R.id.text_itemcount);
            cover = itemView.findViewById(R.id.img_cover);
        }
    }
}
