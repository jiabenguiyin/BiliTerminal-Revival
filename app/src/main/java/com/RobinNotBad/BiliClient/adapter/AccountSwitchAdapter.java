package com.RobinNotBad.BiliClient.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.GlideUtil;

import java.util.List;

public class AccountSwitchAdapter extends RecyclerView.Adapter<AccountSwitchAdapter.ViewHolder> {
    public interface Listener {
        void onAccountClick(AccountManager.SavedAccount account);

        void onDeleteClick(AccountManager.SavedAccount account);
    }

    private final List<AccountManager.SavedAccount> accounts;
    private final long currentMid;
    private final Listener listener;

    public AccountSwitchAdapter(List<AccountManager.SavedAccount> accounts, long currentMid,
                                Listener listener) {
        this.accounts = accounts;
        this.currentMid = currentMid;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cell_saved_account, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AccountManager.SavedAccount account = accounts.get(position);
        boolean current = account.mid == currentMid;
        holder.name.setText(account.getDisplayName());
        holder.uid.setText("UID " + account.mid);
        holder.current.setVisibility(current ? View.VISIBLE : View.GONE);
        holder.delete.setVisibility(current ? View.INVISIBLE : View.VISIBLE);
        GlideUtil.requestRound(holder.avatar, account.avatar, R.mipmap.akari);
        holder.itemView.setOnClickListener(view -> listener.onAccountClick(account));
        holder.delete.setOnClickListener(view -> listener.onDeleteClick(account));
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final ImageView delete;
        final TextView name;
        final TextView uid;
        final TextView current;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.account_avatar);
            delete = itemView.findViewById(R.id.account_delete);
            name = itemView.findViewById(R.id.account_name);
            uid = itemView.findViewById(R.id.account_uid);
            current = itemView.findViewById(R.id.account_current);
        }
    }
}
