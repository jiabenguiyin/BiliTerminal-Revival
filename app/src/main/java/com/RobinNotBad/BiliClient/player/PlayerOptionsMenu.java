package com.RobinNotBad.BiliClient.player;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.view.ContextThemeWrapper;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.UiConfigurationUtil;
import com.RobinNotBad.BiliClient.R;

/** Standard Menu commands, presented in a bounded, scaled dark dialog on watches. */
public final class PlayerOptionsMenu {
    private final Activity owner;
    private final PopupMenu commands;
    private final Context context;
    private final float density, fontScale;
    private Dialog dialog;
    private Runnable onDismiss;

    public PlayerOptionsMenu(Activity owner, View anchor) {
        this.owner = owner;
        // Keep the Activity's window identity. Scaling only this menu also avoids changing
        // the legacy player's video transforms and physical button hit coordinates.
        context = new ContextThemeWrapper(owner, R.style.PlayerOptionsTheme);
        Context raw = owner instanceof com.RobinNotBad.BiliClient.activity.base.BaseActivity
                ? ((com.RobinNotBad.BiliClient.activity.base.BaseActivity) owner).old_context : owner;
        if (raw == null) raw = owner;
        android.util.DisplayMetrics metrics = raw.getResources().getDisplayMetrics();
        int shortest = Math.min(metrics.widthPixels, metrics.heightPixels);
        int stored = SharedPreferencesUtil.getInt("density", -1);
        int base = stored < UiConfigurationUtil.MIN_DENSITY_DPI ? metrics.densityDpi
                : UiConfigurationUtil.normalizeDensity(stored, metrics.densityDpi, shortest);
        density = UiConfigurationUtil.densityForScale(SharedPreferencesUtil.getFloat("dpi", 1f), base, shortest) / 160f;
        fontScale = owner.getResources().getConfiguration().fontScale;
        commands = new PopupMenu(context, anchor);
    }

    public Menu getMenu() { return commands.getMenu(); }
    public Dialog getDialog() { return dialog; }
    public boolean isShowing() { return dialog != null && dialog.isShowing(); }
    public void setOnDismissListener(Runnable listener) { onDismiss = listener; }
    public void setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener listener) {
        commands.setOnMenuItemClickListener(item -> {
            if (!item.isEnabled()) return false;
            dismiss();
            return listener.onMenuItemClick(item);
        });
    }

    private int dp(int value) { return Math.round(value * density); }

    public void show() {
        if (owner.isFinishing() || isShowing()) return;
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), dp(4));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        scroll.setContentDescription(owner.getString(R.string.player_options_title));
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            if (!item.isVisible()) continue;
            LinearLayout row = new LinearLayout(context);
            row.setTag(item.getItemId());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(44));
            row.setPadding(dp(6), dp(6), dp(6), dp(6));
            row.setBackgroundResource(R.drawable.background_button_compat);
            TextView label = text(item.getTitle(), 15);
            label.setTextColor(item.isEnabled() ? Color.WHITE : 0xff999999);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            if (item.isCheckable()) {
                androidx.appcompat.widget.AppCompatImageView check = new androidx.appcompat.widget.AppCompatImageView(context);
                check.setImageResource(item.isChecked() ? R.drawable.menu_radio_on : R.drawable.menu_radio_off);
                check.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                row.addView(check, new LinearLayout.LayoutParams(dp(22), dp(22)));
                row.setContentDescription(owner.getString(item.isChecked()
                        ? R.string.player_option_on : R.string.player_option_off, item.getTitle()));
            } else row.setContentDescription(item.getTitle());
            row.setEnabled(item.isEnabled());
            row.setFocusable(item.isEnabled());
            row.setOnClickListener(view -> getMenu().performIdentifierAction(item.getItemId(), 0));
            content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff262626);
        background.setCornerRadius(dp(8));
        scroll.setBackgroundDrawable(background);

        Dialog current = new Dialog(context);
        current.setOwnerActivity(owner);
        current.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog = current;
        current.setContentView(scroll);
        current.setCanceledOnTouchOutside(true);
        current.setOnDismissListener(ignored -> {
            if (dialog == current) dialog = null;
            if (onDismiss != null) onDismiss.run();
        });
        View bounds = owner.findViewById(android.R.id.content);
        int width = bounds == null ? 0 : bounds.getWidth();
        int height = bounds == null ? 0 : bounds.getHeight();
        if (width <= 0) width = owner.getResources().getDisplayMetrics().widthPixels;
        if (height <= 0) height = owner.getResources().getDisplayMetrics().heightPixels;
        int gap = Math.max(2, Math.min(dp(12), Math.min(width, height) / 20));
        width = Math.max(1, Math.min(width - 2 * gap, dp(320)));
        height = Math.max(1, height - 2 * gap);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        Window window = current.getWindow();
        if (window != null) {
            window.setFlags(owner.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(owner.getWindow().getDecorView().getSystemUiVisibility());
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.CENTER);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.45f;
            window.setAttributes(params);
            window.setLayout(width, Math.min(height, scroll.getMeasuredHeight()));
        }
        current.show();
        if (window != null) window.setLayout(width, Math.min(height, scroll.getMeasuredHeight()));
    }

    private TextView text(CharSequence value, int sp) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * density * fontScale);
        text.setTextColor(Color.WHITE);
        return text;
    }

    public void dismiss() { if (dialog != null) dialog.dismiss(); }
}
