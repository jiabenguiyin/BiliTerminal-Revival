package com.RobinNotBad.BiliClient.activity.user;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.ui.TerminalDialogStyle;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.MySpaceOrder;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

import java.util.List;

public class MySpaceSortActivity extends BaseActivity {
    private List<MySpaceOrder.Item> items;
    private OrderAdapter adapter;
    private AlertDialog dialog;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_myspace_sort);
        items = MySpaceOrder.parse(SharedPreferencesUtil.getString(MySpaceOrder.PREFERENCE, ""));
        RecyclerView list = findViewById(R.id.recyclerView);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrderAdapter();
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView list, @NonNull RecyclerView.ViewHolder from,
                                            @NonNull RecyclerView.ViewHolder to) {
                int source = from.getAdapterPosition(), target = to.getAdapterPosition();
                if (source < 0 || target < 0) return false;
                items.add(target, items.remove(source));
                adapter.notifyItemMoved(source, target);
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {}
            @Override public void clearView(@NonNull RecyclerView list, @NonNull RecyclerView.ViewHolder holder) {
                super.clearView(list, holder);
                save();
            }
        }).attachToRecyclerView(list);
        findViewById(R.id.myspace_order_reset).setOnClickListener(v -> showDialog(
                new AlertDialog.Builder(this).setTitle("恢复默认顺序？")
                        .setMessage("仅重置“我的”页面的按钮位置。")
                        .setNegativeButton("取消", null).setPositiveButton("恢复", (d, which) -> {
                            items = MySpaceOrder.parse("");
                            if (save()) MsgUtil.showMsg("已恢复默认顺序");
                            adapter.notifyDataSetChanged();
                        }).create()));
    }

    private boolean save() {
        boolean saved = SharedPreferencesUtil.getSharedPreferences().edit()
                .putString(MySpaceOrder.PREFERENCE, MySpaceOrder.encode(items)).commit();
        if (!saved) {
            MsgUtil.showMsg("顺序保存失败，请重试");
            return false;
        }
        return true;
    }

    @Override protected void onPause() {
        if (items != null) save();
        super.onPause();
    }

    private void chooseMove(MySpaceOrder.Item selected) {
        int from = items.indexOf(selected);
        if (from < 0) return;
        showDialog(new AlertDialog.Builder(this).setTitle(selected.label)
                .setItems(new String[]{"上移", "下移", "移到最前", "移到最后"}, (d, which) -> {
                    int current = items.indexOf(selected);
                    int target = which == 0 ? current - 1 : which == 1 ? current + 1
                            : which == 2 ? 0 : items.size() - 1;
                    if (current < 0 || target < 0 || target >= items.size() || current == target) return;
                    items.add(target, items.remove(current));
                    adapter.notifyItemMoved(current, target);
                    save();
                    ((RecyclerView)findViewById(R.id.recyclerView)).scrollToPosition(target);
                }).setNegativeButton("取消", null).create());
    }

    private void showDialog(AlertDialog next) {
        if (dialog != null && dialog.isShowing()) return;
        dialog = next;
        next.setOnDismissListener(d -> { if (dialog == next) dialog = null; });
        TerminalDialogStyle.prepare(next);
        next.show();
    }

    @Override protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }

    private class OrderAdapter extends RecyclerView.Adapter<Row> {
        @NonNull @Override public Row onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_myspace_order, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Row row, int position) {
            MySpaceOrder.Item item = items.get(position);
            ((TextView)row.itemView).setText(item.label);
            row.itemView.setOnClickListener(v -> chooseMove(item));
        }
        @Override public int getItemCount() { return items.size(); }
    }
    private static class Row extends RecyclerView.ViewHolder {
        Row(View view) { super(view); }
    }
}
