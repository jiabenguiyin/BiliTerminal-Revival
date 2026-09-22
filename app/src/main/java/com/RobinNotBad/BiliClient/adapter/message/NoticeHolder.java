package com.RobinNotBad.BiliClient.adapter.message;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.reply.ReplyInfoActivity;
import com.RobinNotBad.BiliClient.activity.user.info.UserInfoActivity;
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder;
import com.RobinNotBad.BiliClient.api.ReplyApi;
import com.RobinNotBad.BiliClient.model.MessageCard;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import com.RobinNotBad.BiliClient.util.ToolsUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class NoticeHolder extends RecyclerView.ViewHolder {
    public final LinearLayout avaterList;
    public final LinearLayout senderRow;
    public final TextView action;
    public final TextView pubdate;
    public final TextView senderNames;
    public final ConstraintLayout extraCard;
    public final View itemView;

    public NoticeHolder(@NonNull View itemView) {
        super(itemView);
        this.itemView = itemView;
        avaterList = itemView.findViewById(R.id.avatar_list);
        senderRow = itemView.findViewById(R.id.sender_row);
        action = itemView.findViewById(R.id.action);
        pubdate = itemView.findViewById(R.id.pubdate);
        senderNames = itemView.findViewById(R.id.sender_names);
        extraCard = itemView.findViewById(R.id.extraCard);
    }

    @SuppressLint("SetTextI18n")
    public void showMessage(MessageCard message, Context context) {
        avaterList.removeAllViews();
        extraCard.removeAllViews();

        boolean moveNamesBelowAvatars = message.getType == MessageCard.GET_TYPE_LIKE;
        senderRow.setOrientation(moveNamesBelowAvatars
                ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        senderRow.setGravity(moveNamesBelowAvatars ? Gravity.START : Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams senderRowParams =
                (LinearLayout.LayoutParams) senderRow.getLayoutParams();
        senderRowParams.height = moveNamesBelowAvatars
                ? ViewGroup.LayoutParams.WRAP_CONTENT : ToolsUtil.dp2px(32);
        senderRow.setLayoutParams(senderRowParams);
        LinearLayout.LayoutParams senderNamesParams =
                (LinearLayout.LayoutParams) senderNames.getLayoutParams();
        senderNamesParams.width = moveNamesBelowAvatars
                ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
        senderNamesParams.weight = moveNamesBelowAvatars ? 0 : 1;
        senderNamesParams.leftMargin = moveNamesBelowAvatars ? 0 : ToolsUtil.dp2px(6);
        senderNamesParams.rightMargin = 0;
        senderNamesParams.topMargin = moveNamesBelowAvatars ? ToolsUtil.dp2px(2) : 0;
        senderNamesParams.gravity = moveNamesBelowAvatars ? Gravity.START : Gravity.CENTER_VERTICAL;
        senderNames.setLayoutParams(senderNamesParams);
        List<com.RobinNotBad.BiliClient.model.UserInfo> users = message.user;
        if (users == null || users.isEmpty()) {
            senderRow.setVisibility(View.GONE);
            avaterList.setVisibility(View.GONE);
        } else {
            senderRow.setVisibility(View.VISIBLE);
            avaterList.setVisibility(View.VISIBLE);
        }
        if (users == null || users.isEmpty()) {
            senderNames.setVisibility(View.GONE);
        } else {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < users.size(); i++) {
                if (i > 0) names.append("、");
                String name = users.get(i).name;
                names.append(name == null || name.isEmpty() ? "未知用户" : name);
            }
            senderNames.setText(names.toString());
            senderNames.setVisibility(View.VISIBLE);
        }
        for (int i = 0; users != null && i < users.size(); i++) {
            ImageView imageView = new ImageView(context);
            Glide.with(BiliTerminal.context)
                    .asDrawable()
                    .load(GlideUtil.url(users.get(i).avatar))
                    .transition(GlideUtil.getTransitionOptions())
                    .placeholder(R.mipmap.akari)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .apply(RequestOptions.circleCropTransform())
                    .into(imageView);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(ToolsUtil.dp2px(32), ToolsUtil.dp2px(32)));
            imageView.setLeft(ToolsUtil.dp2px(3));
            int finalI = i;
            imageView.setOnClickListener(view1 -> {
                Intent intent = new Intent();
                intent.setClass(context, UserInfoActivity.class);
                intent.putExtra("mid", users.get(finalI).mid);
                context.startActivity(intent);
            });
            avaterList.addView(imageView);

            //这个View什么都没有，用来当间隔的
            View view = new View(context);
            view.setLayoutParams(new ViewGroup.LayoutParams(ToolsUtil.dp2px(3), ToolsUtil.dp2px(32)));
            avaterList.addView(view);
        }

        if (message.timeStamp != 0) {
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.SIMPLIFIED_CHINESE);
            pubdate.setText(sdf.format(message.timeStamp * 1000));
        } else pubdate.setText(message.timeDesc);

        action.setText(message.content == null ? "" : message.content);
        StringUtil.setCopy(action);

        if (message.videoCard != null) {
            VideoCard childVideoCard = message.videoCard;
            VideoCardHolder holder = new VideoCardHolder(View.inflate(context, R.layout.cell_dynamic_video, extraCard));
            holder.showVideoCard(childVideoCard, context);
            holder.itemView.findViewById(R.id.videoCardView).setOnClickListener(view -> {
                if (message.getType == MessageCard.GET_TYPE_AT) {
                    openVideoTarget(context, message, childVideoCard.bvid);
                } else {
                    TerminalContext.getInstance().enterVideoDetailPage(context, 0, childVideoCard.bvid);
                }
            });
        }
        if (message.replyInfo != null || message.dynamicInfo != null) {
            Reply childReply = message.replyInfo != null ? message.replyInfo : message.dynamicInfo;
            ReplyCardHolder holder = new ReplyCardHolder(View.inflate(context, R.layout.cell_message_reply, extraCard));
            holder.showReplyCard(childReply);
            holder.itemView.findViewById(R.id.cardView).setOnClickListener(view -> {
                try {
                    if ("reply".equals(message.itemType) || message.getType == MessageCard.GET_TYPE_REPLY) {
                        long rootReply = getRootReplyId(message);
                        long seekReply = getReplyTargetId(message);
                        switch (message.businessId) {
                            case ReplyApi.REPLY_TYPE_VIDEO_CHILD:
                                openVideoReply(context, message, rootReply, seekReply, childReply);
                                break;
                            case ReplyApi.REPLY_TYPE_VIDEO:
                                if (message.subjectId > 0 && rootReply > 0 && seekReply > 0) {
                                    openVideoReply(context, message, rootReply, seekReply, childReply);
                                } else if (childReply.ofBvid != null && !childReply.ofBvid.isEmpty()) {
                                    TerminalContext.getInstance().enterVideoDetailPage(
                                            context, 0, childReply.ofBvid, null, rootReply, seekReply);
                                } else {
                                    MsgUtil.showMsg("这条消息缺少视频定位信息");
                                }
                                break;
                            case ReplyApi.REPLY_TYPE_DYNAMIC_CHILD:
                                if (message.subjectId > 0 && rootReply > 0) {
                                    Intent intent = new Intent(context, ReplyInfoActivity.class);
                                    intent.putExtra("rpid", rootReply);
                                    intent.putExtra("seekReply", seekReply);
                                    intent.putExtra("oid", message.subjectId);
                                    intent.putExtra("type", ReplyApi.REPLY_TYPE_PHOTO);
                                    context.startActivity(intent);
                                } else if (message.subjectId > 0) {
                                    TerminalContext.getInstance().enterDynamicDetailPage(
                                            context, message.subjectId, 0, seekReply);
                                } else {
                                    MsgUtil.showMsg("这条消息缺少评论定位信息");
                                }
                                break;
                            case ReplyApi.REPLY_TYPE_DYNAMIC:
                                TerminalContext.getInstance().enterDynamicDetailPage(context, message.subjectId, 0, seekReply);
                                break;
                            case ReplyApi.REPLY_TYPE_ARTICLE:
                                TerminalContext.getInstance().enterArticleDetailPage(context, message.subjectId, seekReply);
                                break;
                            default:
                                MsgUtil.showMsg("不支持这个类型喵：" + message.businessId);
                        }
                    } else switch (message.getType) {
                        case MessageCard.GET_TYPE_LIKE:
                        case MessageCard.GET_TYPE_AT:
                            switch (message.itemType) {
                                case "video":
                                    openVideoTarget(context, message, childReply.ofBvid);
                                    break;
                                case "reply":
                                    if (message.businessId == ReplyApi.REPLY_TYPE_VIDEO
                                            || message.businessId == ReplyApi.REPLY_TYPE_VIDEO_CHILD) {
                                        openVideoTarget(context, message, childReply.ofBvid);
                                    } else if (message.businessId == ReplyApi.REPLY_TYPE_PHOTO
                                            || message.businessId == ReplyApi.REPLY_TYPE_DYNAMIC) {
                                        openDynamicTarget(context, message);
                                    } else if (message.businessId == ReplyApi.REPLY_TYPE_ARTICLE) {
                                        TerminalContext.getInstance().enterArticleDetailPage(
                                                context, message.subjectId, getReplyTargetId(message));
                                    } else {
                                        MsgUtil.showMsg("不支持这个评论类型喵：" + message.businessId);
                                    }
                                    break;
                                case "dynamic":
                                case "album":
                                case "opus":
                                    openDynamicTarget(context, message);
                                    break;
                                case "article":
                                    TerminalContext.getInstance().enterArticleDetailPage(
                                            context, message.subjectId, getReplyTargetId(message));
                                    break;
                                default:
                                    MsgUtil.showMsg("不支持这个类型喵：" + message.itemType);
                            }
                            break;
                    }

                } catch (Exception e) {
                    MsgUtil.err("跳转出错？", e);
                }
            });
        }
    }

    private static long getRootReplyId(MessageCard message) {
        if (message.rootId > 0) return message.rootId;
        if (message.sourceId > 0) return message.sourceId;
        if (message.targetId > 0) return message.targetId;
        return -1;
    }

    private static long getReplyTargetId(MessageCard message) {
        if (message.sourceId > 0) return message.sourceId;
        if (message.targetId > 0) return message.targetId;
        return message.rootId > 0 ? message.rootId : -1;
    }

    private static void openVideoReply(Context context, MessageCard message,
                                       long rootReply, long seekReply, Reply childReply) {
        openVideoTarget(context, message, childReply.ofBvid, rootReply, seekReply);
    }

    private static void openVideoTarget(Context context, MessageCard message, String bvid) {
        openVideoTarget(context, message, bvid,
                getRootReplyId(message), getReplyTargetId(message));
    }

    private static void openVideoTarget(Context context, MessageCard message, String bvid,
                                        long rootReply, long seekReply) {
        if (message.subjectId > 0 && rootReply > 0 && seekReply > 0) {
            Intent intent = new Intent(context, ReplyInfoActivity.class);
            intent.putExtra("rpid", rootReply);
            intent.putExtra("seekReply", seekReply);
            intent.putExtra("oid", message.subjectId);
            intent.putExtra("type", ReplyApi.REPLY_TYPE_VIDEO);
            context.startActivity(intent);
        } else if (bvid != null && !bvid.isEmpty()) {
            TerminalContext.getInstance().enterVideoDetailPage(
                    context, 0, bvid, null, rootReply, seekReply);
        } else {
            MsgUtil.showMsg("这条消息缺少视频定位信息");
        }
    }

    private static void openDynamicTarget(Context context, MessageCard message) {
        long rootReply = getRootReplyId(message);
        long seekReply = getReplyTargetId(message);
        long contentId = message.contentId > 0 ? message.contentId : message.subjectId;
        if (contentId <= 0) {
            MsgUtil.showMsg("这条消息缺少动态定位信息");
            return;
        }
        if (rootReply > 0 && seekReply > 0 && rootReply != seekReply
                && (message.businessId == ReplyApi.REPLY_TYPE_PHOTO
                || message.businessId == ReplyApi.REPLY_TYPE_DYNAMIC)) {
            Intent intent = new Intent(context, ReplyInfoActivity.class);
            intent.putExtra("rpid", rootReply);
            intent.putExtra("seekReply", seekReply);
            intent.putExtra("oid", message.subjectId);
            intent.putExtra("type", message.businessId);
            context.startActivity(intent);
        } else {
            TerminalContext.getInstance().enterDynamicDetailPage(
                    context, contentId, 0, seekReply);
        }
    }
}
