package com.RobinNotBad.BiliClient.adapter.dynamic;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

public class DynamicUpAdapter extends RecyclerView.Adapter<DynamicUpAdapter.Holder> {
    private final List<UserInfo> users = new ArrayList<>();

    public void setUsers(List<UserInfo> items) {
        users.clear();
        users.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dynamic_up, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        UserInfo user = users.get(position);
        holder.name.setText(user.name);
        holder.itemView.setContentDescription(user.name);
        Glide.with(holder.itemView).load(GlideUtil.url(user.avatar))
                .placeholder(R.mipmap.akari).apply(RequestOptions.circleCropTransform())
                .into(holder.avatar);
        holder.itemView.setOnClickListener(view -> view.getContext().startActivity(
                new Intent(view.getContext(), UserInfoActivity.class).putExtra("mid", user.mid)));
    }

    @Override public int getItemCount() { return users.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView name;
        Holder(View item) {
            super(item);
            avatar = item.findViewById(R.id.avatar);
            name = item.findViewById(R.id.name);
        }
    }
}
