package com.RobinNotBad.BiliClient.adapter.message;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.message.PrivateMsgActivity;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.api.PrivateMsgApi;
import com.RobinNotBad.BiliClient.model.PrivateMessage;
import com.RobinNotBad.BiliClient.model.PrivateMsgSession;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.ui.widget.RadiusBackgroundSpan;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.PrivateMsgSessionList;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;

public class PrivateMsgSessionsAdapter
        extends RecyclerView.Adapter<PrivateMsgSessionsAdapter.PrivateMsgSessionsHolder> {

    final Context context;
    final ArrayList<PrivateMsgSession> sessionsList;
    final HashMap<Long, UserInfo> userMap;
    private final int cardRoundRadius;
    private static final int BADGE_TEXT_COLOR = Color.WHITE;
    private static final int BADGE_BG_COLOR = Color.rgb(207, 75, 95);
    private static final String BADGE_TEXT = "  未读 ";

    public PrivateMsgSessionsAdapter(Context context, ArrayList<PrivateMsgSession> sessionsList,
                                     HashMap<Long, UserInfo> userMap) {
        this.context = context;
        this.sessionsList = sessionsList == null ? new ArrayList<>() : new ArrayList<>(sessionsList);
        this.userMap = userMap == null ? new HashMap<>() : new HashMap<>(userMap);
        this.cardRoundRadius = (int) context.getResources().getDimension(R.dimen.card_round);
    }

    @NonNull
    @Override
    public PrivateMsgSessionsHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(this.context).inflate(R.layout.cell_user_list, parent, false);
        return new PrivateMsgSessionsHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PrivateMsgSessionsHolder holder, int position) {
        if (position < 0 || position >= sessionsList.size())
            return;
        PrivateMsgSession msgContent = sessionsList.get(position);
        if (msgContent == null)
            return;

        try {
            if (msgContent.content != null)
                switch (msgContent.contentType) {
                    case PrivateMessage.TYPE_TEXT:
                        holder.contentText.setText(msgContent.content.getString("content"));
                        break;
                    case PrivateMessage.TYPE_PIC:
                        holder.contentText.setText("[图片消息]");
                        break;

                    case PrivateMessage.TYPE_VIDEO:
                    case PrivateMessage.TYPE_PIC_CARD:
                    case PrivateMessage.TYPE_NOMAL_CARD:
                        holder.contentText.setText(msgContent.content.getString("title"));
                        break;

                    case PrivateMessage.TYPE_TEXT_WITH_VIDEO:
                        holder.contentText.setText(msgContent.content.getString("reply_content"));
                        break;
                    case PrivateMessage.TYPE_RETRACT:
                        holder.contentText.setText("[撤回消息]");
                        break;

                    default:
                        holder.contentText.setText("");
                }
            else
                holder.contentText.setText("");

            holder.contentText.setEllipsize(TextUtils.TruncateAt.END);

            UserInfo user = userMap != null ? userMap.get(msgContent.talkerUid) : null;
            String displayName = msgContent.resolveName(
                    user != null ? user.name : "用户 " + msgContent.talkerUid);
            String displayAvatar = msgContent.resolveAvatar(user != null ? user.avatar : "");

            if (!TextUtils.isEmpty(displayName)) {
                if (msgContent.unread > 0 && SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVATE_MSG_UNREAD_BADGE_ENABLE, false)) {
                    SpannableStringBuilder nameStr = new SpannableStringBuilder(displayName);
                    int nameLength = displayName.length();
                    nameStr.append(BADGE_TEXT);
                    nameStr.setSpan(
                            new RadiusBackgroundSpan(1, cardRoundRadius, BADGE_TEXT_COLOR, BADGE_BG_COLOR),
                            nameLength + 1, nameStr.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                    holder.nameText.setText(nameStr);
                } else {
                    holder.nameText.setText(displayName);
                }
            }
            Glide.with(holder.avatarView).clear(holder.avatarView);
            Glide.with(BiliTerminal.context).asDrawable().load(GlideUtil.url(displayAvatar))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .placeholder(R.mipmap.akari)
                    .error(R.mipmap.akari)
                    .apply(RequestOptions.circleCropTransform())
                    .into(holder.avatarView);

            holder.itemView.setOnClickListener(view -> {
                Intent intent = new Intent(context, PrivateMsgActivity.class);
                intent.putExtra("uid", msgContent.talkerUid);
                context.startActivity(intent);
            });
            holder.itemView.setOnLongClickListener(view -> {
                AlertDialog menu = new AlertDialog.Builder(context)
                        .setItems(new String[]{"进入空间", "删除私信"}, (dialog, which) -> {
                            if (which == 0) {
                                Intent intent = new Intent(context, UserInfoActivity.class);
                                intent.putExtra("mid", msgContent.talkerUid);
                                context.startActivity(intent);
                            } else {
                                confirmRemoveSession(msgContent.talkerUid);
                            }
                        })
                        .create();
                MsgUtil.prepareAlertDialog(menu);
                menu.setOnShowListener(dialog -> fitMenuToScreen(menu));
                menu.show();
                return true;
            });
        } catch (JSONException err) {
            Log.e("PrivateMsgUserAdapter", err.toString());
        }
    }

    private void fitMenuToScreen(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int margin = Math.max(8, Math.round(8 * metrics.density));
        int width = Math.max(1, metrics.widthPixels - margin * 2);
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void confirmRemoveSession(long talkerUid) {
        AlertDialog confirmDialog = new AlertDialog.Builder(context)
                .setTitle("删除私信")
                .setMessage("只会从私信列表移除，不会删除聊天记录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> CenterThreadPool.run(() -> {
                    try {
                        PrivateMsgApi.removeSession(talkerUid);
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            int removedPosition = PrivateMsgSessionList.removeByTalkerUid(
                                    sessionsList, talkerUid);
                            if (removedPosition >= 0) {
                                userMap.remove(talkerUid);
                                // Android 4.x can mis-measure this RecyclerView inside a
                                // NestedScrollView after removing position 0 with animations.
                                // Rebind the remaining rows as one consistent data set.
                                notifyDataSetChanged();
                            }
                            MsgUtil.showMsg("已删除私信");
                        });
                    } catch (Exception e) {
                        MsgUtil.err(e);
                    }
                }))
                .create();
        MsgUtil.prepareAlertDialog(confirmDialog);
        confirmDialog.show();
    }

    @Override
    public int getItemCount() {
        return sessionsList != null ? sessionsList.size() : 0;
    }

    public static class PrivateMsgSessionsHolder extends RecyclerView.ViewHolder {
        final ImageView avatarView;
        final TextView nameText;
        final TextView contentText;

        public PrivateMsgSessionsHolder(View itemView) {
            super(itemView);
            avatarView = itemView.findViewById(R.id.userAvatar);
            nameText = itemView.findViewById(R.id.userName);
            contentText = itemView.findViewById(R.id.userDesc);
        }

    }
}
