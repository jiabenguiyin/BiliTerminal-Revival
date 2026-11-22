package com.RobinNotBad.BiliClient.adapter.message;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.model.MessageCard;

import java.util.List;

public class NoticeAdapter extends RecyclerView.Adapter<NoticeHolder> {
    private static final String THANKS_PAYLOAD="thanks_state";
    Context context;
    List<MessageCard> messageList;

    public NoticeAdapter(Context context, List<MessageCard> messageList) {
        this.context = context;
        this.messageList = messageList;
    }

    @NonNull
    @Override
    public NoticeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(this.context).inflate(R.layout.cell_message, parent, false);
        return new NoticeHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull NoticeHolder holder, int position) {
        if (position < 0 || position >= messageList.size())
            return;
        MessageCard message = messageList.get(position);
        if (message == null)
            return;
        holder.showMessage(message, context);
        android.widget.TextView section=holder.itemView.findViewById(R.id.notice_section);
        MessageCard previous=position>0?messageList.get(position-1):null;
        boolean boundary=message.readStateKnown&&(previous==null||!previous.readStateKnown||previous.isNew!=message.isNew);
        section.setVisibility(boundary?View.VISIBLE:View.GONE);
        section.setText(message.isNew?R.string.message_latest_section:R.string.message_history_section);
        section.setTextColor(message.isNew?0xffff8eaa:0xffaaaaaa);
    }

    @Override
    public void onViewRecycled(@NonNull NoticeHolder holder) {
        holder.closeThanksDialog();
        holder.likeThanks.setOnClickListener(null);
        holder.likeThanks.setVisibility(View.GONE);
        holder.noticeCard.setStrokeWidth(0);
        holder.itemView.findViewById(R.id.notice_section).setVisibility(View.GONE);
        if (holder.extraCard != null) {
            holder.extraCard.removeAllViews();
        }
        super.onViewRecycled(holder);
    }

    @Override public void onBindViewHolder(@NonNull NoticeHolder holder,int position,@NonNull List<Object> payloads){
        if(payloads.contains(THANKS_PAYLOAD)&&position>=0&&position<messageList.size())
            holder.bindThanks(messageList.get(position),context);
        else super.onBindViewHolder(holder,position,payloads);
    }
    public void refreshThanks(){
        for(int i=0;i<messageList.size();i++){
            MessageCard card=messageList.get(i);
            if(card!=null&&card.getType==MessageCard.GET_TYPE_LIKE&&"video".equals(card.itemType))notifyItemChanged(i,THANKS_PAYLOAD);
        }
    }

    @Override
    public int getItemCount() {
        return messageList != null ? messageList.size() : 0;
    }
}
