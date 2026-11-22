package com.RobinNotBad.BiliClient.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.RobinNotBad.BiliClient.R;
import com.google.android.material.button.MaterialButton;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ButtonFeedbackInstrumentedTest {
    @Test public void reportedActionsUseTheSamePlatformFeedback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ContextThemeWrapper context = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.Theme_BiliClient);
            FrameLayout parent = new FrameLayout(context);
            LayoutInflater inflater = LayoutInflater.from(context);
            View dynamic = inflater.inflate(R.layout.cell_dynamic, parent, false);
            View reply = inflater.inflate(R.layout.cell_reply_list, parent, false);
            View video = inflater.inflate(R.layout.fragment_video_info, parent, false);
            View profile = inflater.inflate(R.layout.activity_simple_viewpager, parent, false);

            assertFeedback(dynamic, R.id.item_dynamic_delete, R.id.item_dynamic_report,
                    R.id.likes, R.id.item_dynamic_comment, R.id.item_dynamic_share);
            assertFeedback(reply, R.id.reportbutton, R.id.item_reply_top, R.id.likes,
                    R.id.dislikeBtn, R.id.item_reply_delete, R.id.replyBtn);
            assertFeedback(video, R.id.layout_like, R.id.layout_coin, R.id.layout_fav);
            assertFeedback(profile, R.id.user_video_search);
            MaterialButton play = video.findViewById(R.id.play);
            assertEquals(context.getResources().getColor(R.color.button_touch_highlight),
                    play.getRippleColor().getDefaultColor());
        });
    }

    @Test public void feedbackIsVisibleWhilePressedAndClearsAfterRelease() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Drawable feedback = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getResources().getDrawable(R.drawable.background_button_compat);
            feedback.setBounds(0, 0, 48, 48);
            assertEquals(Color.TRANSPARENT, centerPixel(feedback));
            feedback.setState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed});
            feedback.jumpToCurrentState();
            assertTrue(Color.alpha(centerPixel(feedback)) > 0);
            feedback.setState(new int[]{android.R.attr.state_enabled});
            feedback.jumpToCurrentState();
            assertEquals(Color.TRANSPARENT, centerPixel(feedback));
        });
    }

    private static void assertFeedback(View layout, int... ids) {
        String expected = Build.VERSION.SDK_INT >= 21 ? "RippleDrawable" : "StateListDrawable";
        for (int id : ids) {
            View button = layout.findViewById(id);
            assertEquals(button.getResources().getResourceEntryName(id), expected,
                    button.getBackground().getClass().getSimpleName());
        }
    }

    private static int centerPixel(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(bitmap));
        int pixel = bitmap.getPixel(24, 24);
        bitmap.recycle();
        return pixel;
    }
}
