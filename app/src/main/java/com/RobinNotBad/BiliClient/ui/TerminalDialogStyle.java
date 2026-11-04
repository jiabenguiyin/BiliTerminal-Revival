package com.RobinNotBad.BiliClient.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;

/** One appearance for ordinary alerts; AlertController still owns actions, lists and inputs. */
public final class TerminalDialogStyle {
    private TerminalDialogStyle() {}

    public static void prepare(AlertDialog dialog) {
        if (dialog != null) dialog.setOnShowListener(ignored -> apply(dialog, true));
    }

    public static void prepareSeparatedActions(AlertDialog dialog) {
        if (dialog != null) dialog.setOnShowListener(ignored -> apply(dialog, true));
    }

    public static void prepare(android.app.AlertDialog dialog) {
        if (dialog != null) dialog.setOnShowListener(ignored -> apply(dialog, true));
    }

    public static android.app.DatePickerDialog createDatePicker(Context context, int year, int month, int day,
            android.app.DatePickerDialog.OnDateSetListener accepted) {
        // Older framework pickers notify their constructor callback from onStop,
        // including cancellation. Commit only the explicit positive action.
        android.app.DatePickerDialog picker = new android.app.DatePickerDialog(context,
                android.R.style.Theme_Holo_Dialog_NoActionBar, null, year, month, day);
        picker.getDatePicker().setCalendarViewShown(false);
        picker.getDatePicker().setSpinnersShown(true);
        picker.setButton(android.app.AlertDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                (android.content.DialogInterface.OnClickListener) null);
        picker.setButton(android.app.AlertDialog.BUTTON_POSITIVE, context.getString(android.R.string.ok), (d, which) -> {
            android.widget.DatePicker date = picker.getDatePicker();
            date.clearFocus();
            if (accepted != null) accepted.onDateSet(date, date.getYear(), date.getMonth(), date.getDayOfMonth());
        });
        prepare(picker);
        return picker;
    }

    private static Button action(android.app.Dialog dialog, int which) {
        return dialog instanceof AlertDialog ? ((AlertDialog)dialog).getButton(which)
                : dialog instanceof android.app.AlertDialog ? ((android.app.AlertDialog)dialog).getButton(which) : null;
    }

    private static View panel(android.app.Dialog dialog, int compatId, String platformName) {
        View view = dialog.findViewById(compatId);
        if (view != null) return view;
        int id = dialog.getContext().getResources().getIdentifier(platformName,"id","android");
        return id == 0 ? null : dialog.findViewById(id);
    }

    private static void apply(android.app.Dialog dialog, boolean separatedActions) {
        Window window = dialog.getWindow();
        if (window == null) return;
        Context context = dialog.getContext();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        Activity owner = owner(context);
        int width = metrics.widthPixels, height = metrics.heightPixels;
        if (owner != null) {
            View content = owner.findViewById(android.R.id.content);
            if (content != null && content.getWidth() > 0 && content.getHeight() > 0) {
                width = content.getWidth(); height = content.getHeight();
            }
            dialog.setOwnerActivity(owner);
            window.setFlags(owner.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(owner.getWindow().getDecorView().getSystemUiVisibility());
        }
        // Ordinary pages already apply the UI scale. Cap it only where the window is too
        // small for readable content and reachable actions; never use Application bounds.
        float density = Math.min(metrics.density, Math.min(width, height) / 200f);
        float font = context.getResources().getConfiguration().fontScale;
        boolean round = SharedPreferencesUtil.getBoolean("player_ui_round", false)
                || Build.VERSION.SDK_INT >= 23 && context.getResources().getConfiguration().isScreenRound();
        int dialogWidth = Math.max(1, Math.min(Math.round(320 * density), Math.round(width * (round ? .70f : .90f))));
        int maxHeight = Math.max(1, Math.round(height * (round ? .70f : .90f)));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff262626);
        background.setCornerRadius(8 * density);
        window.setBackgroundDrawable(background);
        window.setGravity(Gravity.CENTER);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = .45f;
        window.setAttributes(params);
        window.getDecorView().setMinimumWidth(0);

        TextView title = (TextView) panel(dialog,androidx.appcompat.R.id.alertTitle,"alertTitle");
        if (title != null) {
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17 * density * font);
            title.setSingleLine(false);
            title.setMaxLines(3);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(0xffdddddd);
            message.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * density * font);
            // Its existing NestedScrollView scrolls the whole explanation, without truncation.
            message.setMaxLines(Integer.MAX_VALUE);
            message.setPadding(Math.round(12 * density), 0, Math.round(12 * density), Math.round(8 * density));
        }
        for (int which : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = action(dialog,which);
            if (button == null) continue;
            button.setTextColor(Color.WHITE);
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, 13 * density * font);
            button.setMinWidth(Math.round(48 * density)); button.setMinimumWidth(Math.round(48 * density));
            button.setMinHeight(Math.round(40 * density)); button.setMinimumHeight(Math.round(40 * density));
            button.setPadding(Math.round(6 * density), 0, Math.round(6 * density), 0);
        }
        if (separatedActions) separateActions(dialog, density, dialogWidth);
        View panel = panel(dialog,androidx.appcompat.R.id.parentPanel,"parentPanel");
        if (panel != null) {
            View actions = panel(dialog,androidx.appcompat.R.id.buttonPanel,"buttonPanel");
            if (actions != null && actions.getVisibility() == View.VISIBLE) {
                // AlertDialogLayout normally reserves only ButtonBarLayout's first-row
                // minimum. A stacked bar must reserve its complete measured height.
                actions.measure(View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                actions.setMinimumHeight(actions.getMeasuredHeight());
            }
            // Replace the stock 24dp title margins, especially costly at a watch UI scale of 2.
            View titleTemplate = panel(dialog,androidx.appcompat.R.id.title_template,"title_template");
            if (titleTemplate != null) {
                titleTemplate.setPadding(Math.round(12 * density), Math.round(12 * density),
                        Math.round(12 * density), Math.round(8 * density));
                if (titleTemplate.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams margin = (ViewGroup.MarginLayoutParams) titleTemplate.getLayoutParams();
                    margin.leftMargin = margin.topMargin = margin.rightMargin = margin.bottomMargin = 0;
                }
            }
            if (separatedActions && actions != null) {
                View titlePanel = panel(dialog,androidx.appcompat.R.id.topPanel,"topPanel");
                View contentPanel = panel(dialog,androidx.appcompat.R.id.contentPanel,"contentPanel");
                View customPanel = panel(dialog,androidx.appcompat.R.id.customPanel,"customPanel");
                View custom = panel(dialog,androidx.appcompat.R.id.custom,"custom");
                if (customPanel != null && customPanel.getVisibility() == View.VISIBLE && custom instanceof ViewGroup
                        && ((ViewGroup)custom).getChildCount() == 1) {
                    ViewGroup frame = (ViewGroup) custom;
                    View child = frame.getChildAt(0);
                    if (!(child instanceof android.widget.ScrollView) && !(child instanceof androidx.core.widget.NestedScrollView)) {
                        frame.removeView(child);
                        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
                        if (dialog instanceof android.app.DatePickerDialog) {
                            android.widget.HorizontalScrollView horizontal = new android.widget.HorizontalScrollView(context);
                            horizontal.setFillViewport(true);horizontal.addView(child,new android.widget.HorizontalScrollView.LayoutParams(-2,-2));
                            scroll.addView(horizontal,new android.widget.ScrollView.LayoutParams(-1,-2));
                        } else scroll.addView(child,new android.widget.ScrollView.LayoutParams(-1,-2));
                        frame.addView(scroll,new ViewGroup.LayoutParams(-1,-1));
                    }
                }
                int actionHeight = actions.getMeasuredHeight();
                int titleHeight = 0;
                if (titlePanel != null && titlePanel.getVisibility() != View.GONE) {
                    titlePanel.measure(View.MeasureSpec.makeMeasureSpec(dialogWidth,View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                    titleHeight = titlePanel.getMeasuredHeight();
                }
                int room = maxHeight - actionHeight - titleHeight - panel.getPaddingTop() - panel.getPaddingBottom();
                if (titlePanel != null && (room < Math.max(24,Math.round(32*density))
                        || dialog instanceof android.app.DatePickerDialog && maxHeight < 300*density)) {
                    // The question and explicit action label already name the operation.
                    // On very short displays prefer a readable question to a duplicate title.
                    titlePanel.setVisibility(View.GONE);room += titleHeight;
                }
                int count = (contentPanel != null && contentPanel.getVisibility() == View.VISIBLE ? 1 : 0)
                        + (customPanel != null && customPanel.getVisibility() == View.VISIBLE ? 1 : 0);
                for (View middle : new View[]{contentPanel,customPanel}) {
                    if (middle == null || middle.getVisibility() != View.VISIBLE) continue;
                    middle.setMinimumHeight(0);
                    ViewGroup.LayoutParams contentParams = middle.getLayoutParams();
                    contentParams.height = Math.max(1,room/Math.max(1,count));middle.setLayoutParams(contentParams);
                }
            }
            panel.measure(View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
            window.setLayout(dialogWidth, Math.min(maxHeight, panel.getMeasuredHeight()));
        } else window.setLayout(dialogWidth, maxHeight);
    }

    private static void separateActions(android.app.Dialog dialog, float density, int width) {
        Button cancel = action(dialog,AlertDialog.BUTTON_NEGATIVE), confirm = action(dialog,AlertDialog.BUTTON_POSITIVE);
        Button neutral = action(dialog,AlertDialog.BUTTON_NEUTRAL);
        Button anchor = confirm != null && confirm.getVisibility() == View.VISIBLE ? confirm
                : cancel != null && cancel.getVisibility() == View.VISIBLE ? cancel : neutral;
        if (anchor == null || !(anchor.getParent() instanceof LinearLayout)) return;
        if(dialog instanceof android.app.AlertDialog) {
            View outer=panel(dialog,androidx.appcompat.R.id.parentPanel,"parentPanel");
            if(outer!=null)width-=outer.getPaddingLeft()+outer.getPaddingRight();
        }
        // Legacy date pickers keep additional framework insets. Preserve full-width
        // controls on small pickers rather than squeezing their confirm/cancel areas.
        if(dialog instanceof android.app.DatePickerDialog&&width<Math.round(260*density))width=0;
        layoutButtons((LinearLayout)anchor.getParent(),density,width,cancel,neutral,confirm);
        if (confirm != null) confirm.setTextColor(0xffffa0aa);
    }

    public static LinearLayout stackButtons(LinearLayout bar, float density, Button... buttons) {
        return layoutButtons(bar,density,0,buttons);
    }

    public static void fitButtons(LinearLayout bar,float density,int width) {
        java.util.ArrayList<Button> buttons=new java.util.ArrayList<>();
        for(int i=0;i<bar.getChildCount();i++)if(bar.getChildAt(i) instanceof Button)buttons.add((Button)bar.getChildAt(i));
        layoutButtons(bar,density,width,buttons.toArray(new Button[0]));
    }

    private static LinearLayout layoutButtons(LinearLayout bar,float density,int width,Button... buttons) {
        if (bar instanceof androidx.appcompat.widget.ButtonBarLayout)
            ((androidx.appcompat.widget.ButtonBarLayout) bar).setAllowStacking(false);
        else if (bar.getClass() != LinearLayout.class && bar.getParent() instanceof ViewGroup) {
            // Framework DatePickerDialog uses a platform ButtonBarLayout which can
            // unstack itself. Replace the bar while retaining its ID and buttons.
            ViewGroup parent = (ViewGroup)bar.getParent();int index = parent.indexOfChild(bar);
            LinearLayout replacement = new LinearLayout(bar.getContext());replacement.setId(bar.getId());
            ViewGroup.LayoutParams params = bar.getLayoutParams();parent.removeView(bar);parent.addView(replacement,index,params);bar = replacement;
        }
        java.util.ArrayList<Button> visible = new java.util.ArrayList<>();
        for (Button button : buttons) if (button != null && button.getVisibility() == View.VISIBLE) {
            if (button.getParent() instanceof ViewGroup) ((ViewGroup)button.getParent()).removeView(button);
            visible.add(button);
        }
        int padding=Math.round(6*density),gap=Math.max(12,Math.round(16*density));
        boolean horizontal=visible.size()==2&&width-padding*2-gap>=2*Math.max(60,Math.round(48*density));
        bar.removeAllViews();bar.setOrientation(horizontal?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        if(Build.VERSION.SDK_INT>=17)bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bar.setPadding(padding,Math.round(6*density),padding,Math.round(6*density));
        for (int i=0;i<visible.size();i++) {
            Button button = visible.get(i);
            int minHeight = visible.size()>2 ? 32 : Math.max(32,Math.round(40*density));
            button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(minHeight);button.setMinimumHeight(minHeight);
            button.setSingleLine(false);button.setMaxLines(2);
            StateListDrawable feedback = new StateListDrawable();
            for (int state : new int[]{android.R.attr.state_pressed,android.R.attr.state_focused,0}) {
                GradientDrawable shape = new GradientDrawable();shape.setCornerRadius(6*density);
                shape.setColor(state == 0 ? 0xff383838 : 0xff565656);
                shape.setStroke(Math.max(1,Math.round(density)),state==0?0xffaaaaaa:0xffffa0aa);
                feedback.addState(state == 0 ? new int[]{} : new int[]{state},shape);
            }
            button.setBackgroundDrawable(feedback);
            // Material/AppCompat buttons can retain the theme's background tint,
            // which would recolor the entire drawable, including its border.
            androidx.core.view.ViewCompat.setBackgroundTintList(button,null);
            if(button instanceof androidx.core.view.TintableBackgroundView)
                ((androidx.core.view.TintableBackgroundView)button).setSupportBackgroundTintList(null);
            feedback.clearColorFilter();
            LinearLayout.LayoutParams params = horizontal?new LinearLayout.LayoutParams(0,-1,1f):new LinearLayout.LayoutParams(-1,-2);
            if(i>0){if(horizontal)params.leftMargin=gap;else params.topMargin=Math.max(10,Math.round(12*density));}
            bar.addView(button,params);
        }
        return bar;
    }

    private static Activity owner(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }
}
