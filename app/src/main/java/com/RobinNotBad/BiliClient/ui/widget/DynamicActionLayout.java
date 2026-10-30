package com.RobinNotBad.BiliClient.ui.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Sizes the three actions from the card's actual width, including in split screen. */
public class DynamicActionLayout extends LinearLayout {
    public DynamicActionLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        if (width > 0) {
            float density = getResources().getDisplayMetrics().density;
            float widthDp = width / density;
            float textSp = Math.max(12f, Math.min(16f, widthDp / 20f));
            int iconSize = Math.round(Math.max(18f, Math.min(26f, widthDp / 14f)) * density);
            int gap = Math.round(4f * density);
            int padding = Math.round(2f * density);
            int visible = 0;
            for (int i = 0; i < getChildCount(); i++) {
                if (getChildAt(i).getVisibility() != GONE) visible++;
            }
            int columnWidth = width / Math.max(1, visible);
            // Nested forward previews have extra padding. Decide from the actual
            // icon/label width so they match the list whenever the content fits.
            boolean inline = true;
            for (int i = 0; i < getChildCount(); i++) {
                TextView button = (TextView) getChildAt(i);
                button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
                // Large accessibility fonts can require stacked actions even on a wide card.
                if (button.getVisibility() != GONE && button.getPaint().measureText(
                        button.getText().toString()) + iconSize + gap + padding * 2 > columnWidth) {
                    inline = false;
                }
            }
            for (int i = 0; i < getChildCount(); i++) {
                TextView button = (TextView) getChildAt(i);
                int horizontalPadding = inline ? Math.max(padding, (columnWidth - iconSize - gap
                        - (int) Math.ceil(button.getPaint().measureText(button.getText().toString()))) / 2)
                        : padding;
                if (button.getPaddingLeft() != horizontalPadding || button.getPaddingRight() != horizontalPadding
                        || button.getPaddingTop() != padding || button.getPaddingBottom() != padding) {
                    button.setPadding(horizontalPadding, padding, horizontalPadding, padding);
                }
                if (button.getCompoundDrawablePadding() != gap) button.setCompoundDrawablePadding(gap);
                Drawable[] icons = button.getCompoundDrawables();
                Drawable icon = icons[0] != null ? icons[0] : icons[1];
                if (icon != null && (icon.getBounds().width() != iconSize
                        || icon.getBounds().height() != iconSize
                        || (inline ? icons[0] : icons[1]) != icon)) {
                    icon.setBounds(0, 0, iconSize, iconSize);
                    button.setCompoundDrawables(inline ? icon : null, inline ? null : icon, null, null);
                }
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
