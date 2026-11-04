package com.RobinNotBad.BiliClient.ui;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.button.MaterialButton;

/** Compound icons keep the existing title layout and the complete menu touch target. */
public final class MessageBadgeStyle {
    private MessageBadgeStyle() {}
    public static void title(TextView title,int arrow,int unread) {
        if(title==null)return;
        Drawable icon=AppCompatResources.getDrawable(title.getContext(),arrow);
        if(unread>0&&com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.getBoolean(
                com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.MESSAGE_MENU_ARROW_DOT_ENABLE,true))icon=new UnreadArrow(icon);
        Drawable[] old=TextViewCompat.getCompoundDrawablesRelative(title);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(title,icon,old[1],old[2],old[3]);
        title.setContentDescription(title.getText()+(unread>0?"，有 "+unread+" 条未读消息，展开菜单":"，展开菜单"));
    }
    public static void button(MaterialButton button,String label,int unread) {
        if(button==null)return;
        button.setText(unread>0?label+" ("+unread+")":label);
        button.setContentDescription(unread>0?label+"，"+unread+" 条未读":label);
        button.setTextColor(unread>0?0xffff8eaa:0xffebe0e2);
        float density=button.getResources().getDisplayMetrics().density;
        button.setStrokeWidth(unread>0?Math.max(1,Math.round(density)):0);
        button.setStrokeColor(ColorStateList.valueOf(0xfff45c7b));
        GradientDrawable dot=new GradientDrawable();dot.setShape(GradientDrawable.OVAL);dot.setColor(0xffff4d6d);
        button.setIcon(unread>0?dot:null);button.setIconTint(null);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconSize(Math.max(6,Math.round(8*density)));
    }
    public static final class UnreadArrow extends Drawable {
        private final Drawable arrow;
        private final Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);
        UnreadArrow(Drawable arrow){this.arrow=arrow;dot.setColor(0xffff4d6d);}
        @Override public int getIntrinsicWidth(){return arrow.getIntrinsicWidth();}
        @Override public int getIntrinsicHeight(){return arrow.getIntrinsicHeight();}
        @Override protected void onBoundsChange(Rect bounds){arrow.setBounds(bounds);}
        @Override public void draw(Canvas canvas){
            arrow.draw(canvas);Rect b=getBounds();float radius=Math.min(b.width(),b.height())*.22f;
            canvas.drawCircle(b.right-radius,b.top+radius,radius,dot);
        }
        @Override public void setAlpha(int alpha){arrow.setAlpha(alpha);dot.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){arrow.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
