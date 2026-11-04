package com.RobinNotBad.BiliClient.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

/** A bounded confirmation: only the explanation scrolls; both actions stay reachable. */
public final class ReplyTopDialog extends AlertDialog {
    private final Context source;
    private final Activity owner;
    private final boolean pin;
    private final Runnable confirmed;
    private LinearLayout panel, body, actions;
    private ScrollView scroll;
    private Button positive, negative;
    private float density, fontScale;

    public ReplyTopDialog(Context context, boolean pin, Runnable confirmed) {
        super(context, R.style.PlayerOptionsTheme);
        this.source = context;
        this.pin = pin;
        this.confirmed = confirmed;
        Activity activity = null;
        Context candidate = context;
        while (candidate instanceof ContextWrapper) {
            if (candidate instanceof Activity) { activity = (Activity) candidate; break; }
            Context next = ((ContextWrapper) candidate).getBaseContext();
            if (next == candidate) break;
            candidate = next;
        }
        owner = activity;
        if (owner != null) setOwnerActivity(owner);
        setCanceledOnTouchOutside(true);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        DisplayMetrics metrics = source.getResources().getDisplayMetrics();
        int[] bounds = availableBounds();
        // Ordinary pages already carry the user's UI scale. Do not apply it twice.
        // On dense watches keep a minimum amount of usable text space in the dialog.
        density = Math.min(metrics.density, Math.min(bounds[0], bounds[1]) / 200f);
        fontScale = source.getResources().getConfiguration().fontScale;
        panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff262626);
        background.setCornerRadius(dp(8));
        panel.setBackgroundDrawable(background);
        body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(pin ? R.string.reply_pin : R.string.reply_unpin, 17);
        title.setId(android.R.id.title);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView message = text(pin ? R.string.reply_pin_confirm : R.string.reply_unpin_confirm, 14);
        message.setId(android.R.id.message);
        message.setPadding(0, dp(6), 0, dp(8));
        body.addView(message, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        negative = button(android.R.id.button2, android.R.string.cancel);
        positive = button(android.R.id.button1, android.R.string.ok);
        TerminalDialogStyle.stackButtons(actions,density,negative,positive);
        positive.setTextColor(0xffffa0aa);
        panel.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        negative.setOnClickListener(view -> dismiss());
        positive.setOnClickListener(view -> { dismiss(); confirmed.run(); });
        setContentView(panel);
    }

    @Override protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window == null) return;
        int[] bounds = availableBounds();
        boolean round = SharedPreferencesUtil.getBoolean("player_ui_round", false)
                || Build.VERSION.SDK_INT >= 23 && source.getResources().getConfiguration().isScreenRound();
        // A centred 70%-by-70% rectangle fits inside a circular display as well.
        float fraction = round ? .70f : .90f;
        int width = Math.max(1, Math.min(dp(320), Math.round(bounds[0] * fraction)));
        int maxHeight = Math.max(1, Math.round(bounds[1] * fraction));
        int innerWidth = Math.max(1, width - panel.getPaddingLeft() - panel.getPaddingRight());
        // Keep two-character action labels on one line even at a large system font size.
        float actionText = Math.min(14 * density * fontScale, (innerWidth - dp(32)) / 3f);
        for (Button action : new Button[]{positive, negative})
            action.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(1, actionText));
        TerminalDialogStyle.fitButtons(actions,density,innerWidth);
        actions.measure(View.MeasureSpec.makeMeasureSpec(innerWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int room = Math.max(1, maxHeight - panel.getPaddingTop() - panel.getPaddingBottom() - actions.getMeasuredHeight());
        body.measure(View.MeasureSpec.makeMeasureSpec(innerWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        scroll.getLayoutParams().height = Math.min(room, body.getMeasuredHeight());
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
        if (owner != null) {
            window.setFlags(owner.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(owner.getWindow().getDecorView().getSystemUiVisibility());
        }
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setGravity(Gravity.CENTER);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = .45f;
        window.setAttributes(params);
        window.setLayout(width, Math.min(maxHeight, panel.getMeasuredHeight()));
    }

    @Override public Button getButton(int which) {
        if (which == BUTTON_POSITIVE) return positive;
        if (which == BUTTON_NEGATIVE) return negative;
        return null;
    }

    private int[] availableBounds() {
        DisplayMetrics metrics = source.getResources().getDisplayMetrics();
        int width = metrics.widthPixels, height = metrics.heightPixels;
        if (owner != null) {
            View content = owner.findViewById(android.R.id.content);
            if (content != null && content.getWidth() > 0 && content.getHeight() > 0) {
                // Use the laid-out window, not cached resource/visible-frame sizes.
                // KitKat can retain a 90px app area during immersive dialog transitions.
                width = content.getWidth(); height = content.getHeight();
            }
        }
        return new int[]{Math.max(1, width), Math.max(1, height)};
    }

    private int dp(int value) { return Math.max(0, Math.round(value * density)); }
    private TextView text(int resource, int sp) {
        TextView view = new TextView(getContext());
        view.setText(resource);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * density * fontScale);
        return view;
    }
    private Button button(int id, int label) {
        Button button = new Button(getContext());
        button.setId(id); button.setText(label); button.setTextColor(Color.WHITE);
        button.setAllCaps(false); button.setSingleLine(true);
        button.setMinimumWidth(0); button.setMinWidth(0);
        button.setMinimumHeight(dp(36)); button.setMinHeight(dp(36));
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
        button.setBackgroundResource(R.drawable.background_button_compat);
        return button;
    }
}
