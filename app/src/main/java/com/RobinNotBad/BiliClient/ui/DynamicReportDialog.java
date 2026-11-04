package com.RobinNotBad.BiliClient.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.api.DynamicReportApi;
import java.util.List;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

/** Two explicit steps; choosing a reason or dismissing this dialog never sends a report. */
public final class DynamicReportDialog extends AlertDialog {
    public interface OnConfirmed { void report(DynamicReportApi.Reason reason, String note); }

    private final Context source;
    private final List<DynamicReportApi.Reason> reportReasons;
    private final long targetDynamicId;
    private final Activity owner;
    private final OnConfirmed confirmed;
    private LinearLayout panel, body, actions;
    private ScrollView scroll;
    private Button positive, negative;
    private EditText note;
    private String[] reasons;
    private int reason = -1;
    private boolean confirmation, submitted;
    private float density, fontScale;

    public DynamicReportDialog(Context context, long dynamicId, List<DynamicReportApi.Reason> reportReasons, OnConfirmed confirmed) {
        super(context, R.style.PlayerOptionsTheme);
        source = context;
        targetDynamicId=dynamicId;this.reportReasons=reportReasons;
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
        MsgUtil.prepareAlertDialog(this);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int[] bounds = availableBounds();
        density = Math.min(source.getResources().getDisplayMetrics().density,
                Math.min(bounds[0], bounds[1]) / 200f);
        fontScale = source.getResources().getConfiguration().fontScale;
        reasons=new String[reportReasons.size()];for(int i=0;i<reasons.length;i++)reasons[i]=reportReasons.get(i).name;
        panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(8));
        body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        negative = button(android.R.id.button2, R.string.reply_report_cancel);
        positive = button(android.R.id.button1, R.string.reply_report_next);
        positive.setEnabled(false);
        TerminalDialogStyle.stackButtons(actions,density,negative,positive);
        positive.setTextColor(0xffffa0aa);
        panel.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        negative.setOnClickListener(view -> dismiss());
        positive.setOnClickListener(view -> {
            if (submitted || reason < 0) return;
            if (!confirmation) { showConfirmation(); return; }
            String content = note == null ? "" : note.getText().toString();
            if (!reportReasons.get(reason).officialRequired && !DynamicReportApi.validDescription(content)) {
                note.setError(source.getString(R.string.dynamic_report_note_invalid));
                note.requestFocus();
                return;
            }
            submitted = true;
            positive.setEnabled(false);
            dismiss();
            confirmed.report(reportReasons.get(reason), content.trim());
        });
        title(R.string.dynamic_report_choose_reason);
        RadioGroup group = new RadioGroup(getContext());
        group.setId(R.id.dynamic_report_reasons);
        for (int i = 0; i < reasons.length; i++) {
            RadioButton choice = new RadioButton(getContext());
            choice.setId(i + 1);
            choice.setText(reasons[i]+(reportReasons.get(i).officialRequired?"（官方填写）":""));
            choice.setTextColor(Color.WHITE);
            choice.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * density * fontScale);
            choice.setMinHeight(dp(38));
            choice.setPadding(dp(4), dp(5), dp(4), dp(5));
            group.addView(choice, new RadioGroup.LayoutParams(-1, -2));
        }
        group.setOnCheckedChangeListener((ignored, checked) -> {
            reason = checked - 1;
            positive.setEnabled(reason >= 0 && reason < reasons.length);
        });
        body.addView(group, new LinearLayout.LayoutParams(-1, -2));
        setContentView(panel);
    }

    private void showConfirmation() {
        confirmation = true;
        body.removeAllViews();
        title(R.string.dynamic_report_confirm_title);
        TextView message = text(14);
        message.setId(android.R.id.message);
        message.setText(source.getString(R.string.dynamic_report_confirm_message, reasons[reason])+"\n动态："+targetDynamicId);
        message.setPadding(0, dp(6), 0, dp(8));
        body.addView(message, new LinearLayout.LayoutParams(-1, -2));
        if (!reportReasons.get(reason).officialRequired) {
            TextView label = text(13);
            label.setText(R.string.dynamic_report_note_hint);
            body.addView(label, new LinearLayout.LayoutParams(-1, -2));
            note = new EditText(getContext());
            note.setId(R.id.dynamic_report_note);
            if (Build.VERSION.SDK_INT >= 17) label.setLabelFor(R.id.dynamic_report_note);
            note.setContentDescription(source.getString(R.string.dynamic_report_note_hint));
            note.setTextColor(Color.WHITE);
            note.setHintTextColor(0xffbbbbbb);
            note.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * density * fontScale);
            note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
            note.addTextChangedListener(new android.text.TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                @Override public void onTextChanged(CharSequence s,int start,int before,int count){positive.setEnabled(DynamicReportApi.validDescription(s.toString()));}
                @Override public void afterTextChanged(android.text.Editable s){}
            });
            note.setMinLines(2);
            note.setMaxLines(3);
            note.setPadding(dp(6), dp(6), dp(6), dp(6));
            note.setBackgroundResource(R.drawable.background_reply_report);
            note.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) requestNoteKeyboard();
            });
            note.setOnClickListener(view -> requestNoteKeyboard());
            body.addView(note, new LinearLayout.LayoutParams(-1, -2));
            // Do not open the keyboard until the user elects to enter the optional note.
            body.setFocusableInTouchMode(true);
            body.requestFocus();
        }
        positive.setText(reportReasons.get(reason).officialRequired?"前往官方":source.getString(R.string.dynamic_report_submit));
        positive.setEnabled(reportReasons.get(reason).officialRequired);
        scroll.scrollTo(0, 0);
        fitContent();
    }

    private void requestNoteKeyboard() {
        Window window = getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        note.post(() -> {
            if (note == null || !note.hasFocus() || !isShowing()) return;
            InputMethodManager input = (InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null) input.showSoftInput(note, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @Override public void show() {
        super.show(); // TerminalDialogStyle supplies the shared dark rounded window.
        fitContent();
    }

    private void fitContent() {
        Window window = getWindow();
        if (window == null || panel == null) return;
        int[] bounds = availableBounds();
        boolean round = SharedPreferencesUtil.getBoolean("player_ui_round", false)
                || Build.VERSION.SDK_INT >= 23 && source.getResources().getConfiguration().isScreenRound();
        float fraction = round ? .70f : .90f;
        int width = Math.max(1, Math.min(dp(320), Math.round(bounds[0] * fraction)));
        int maxHeight = Math.max(1, Math.round(bounds[1] * fraction));
        int inner = Math.max(1, width - panel.getPaddingLeft() - panel.getPaddingRight());
        float actionText = Math.min(14 * density * fontScale, (inner - dp(32)) / 3f);
        for (Button action : new Button[]{positive, negative})
            action.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(1, actionText));
        TerminalDialogStyle.fitButtons(actions,density,inner);
        actions.measure(View.MeasureSpec.makeMeasureSpec(inner, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int room = Math.max(1, maxHeight - panel.getPaddingTop() - panel.getPaddingBottom()
                - actions.getMeasuredHeight());
        body.measure(View.MeasureSpec.makeMeasureSpec(inner, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        scroll.getLayoutParams().height = Math.min(room, body.getMeasuredHeight());
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        window.setLayout(width, Math.min(maxHeight, panel.getMeasuredHeight()));
        panel.requestLayout();
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
                width = content.getWidth(); height = content.getHeight();
            }
        }
        return new int[]{Math.max(1, width), Math.max(1, height)};
    }

    private void title(int resource) {
        TextView title = text(17);
        title.setId(android.R.id.title);
        title.setText(resource);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(int value) { return Math.max(0, Math.round(value * density)); }

    private TextView text(int sp) {
        TextView text = new TextView(getContext());
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp * density * fontScale);
        return text;
    }

    private Button button(int id, int label) {
        Button button = new Button(getContext());
        button.setId(id);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(dp(38)); button.setMinimumHeight(dp(38));
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
        button.setBackgroundResource(R.drawable.background_reply_report);
        return button;
    }
}
