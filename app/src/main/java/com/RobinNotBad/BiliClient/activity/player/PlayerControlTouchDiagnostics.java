package com.RobinNotBad.BiliClient.activity.player;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;

import org.json.JSONObject;

/** Bounded geometry samples for device-specific player drawing/input mismatches. */
final class PlayerControlTouchDiagnostics {
    private static final int MAX_SAMPLES = 8;
    private int samples;
    private long lastSampleAt = -1000;
    private String pressedOnDown = "none";
    private float localX, localY, rawX, rawY;
    private static final int[] IDS = {R.id.button_video, R.id.button_sound_cut, R.id.button_sound_add,
            R.id.videoprogress, R.id.control_layout};
    private static final String[] NAMES = {"pause", "volume_minus", "volume_plus", "progress", "background"};

    void beforeDispatch(Activity activity, MotionEvent event) {
        if (samples >= MAX_SAMPLES) return;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) pressedOnDown = "none";
        localX = event.getX();
        localY = event.getY();
        rawX = event.getRawX();
        rawY = event.getRawY();
    }

    void afterDispatch(Activity activity, MotionEvent event, boolean handled) {
        if (samples >= MAX_SAMPLES) return;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            for (int i = 0; i < IDS.length; i++) {
                View view = activity.findViewById(IDS[i]);
                if (view != null && view.isPressed()) {
                    pressedOnDown = NAMES[i];
                    break;
                }
            }
            return;
        }
        if (event.getActionMasked() != MotionEvent.ACTION_UP) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastSampleAt < 1000) return;
        View bottom = activity.findViewById(R.id.bottom_buttons);
        if (bottom == null || !bottom.isShown()) return;
        samples++;
        lastSampleAt = now;
        try {
            JSONObject details = new JSONObject();
            details.put("raw_x", Math.round(rawX));
            details.put("raw_y", Math.round(rawY));
            details.put("local_x", Math.round(localX));
            details.put("local_y", Math.round(localY));
            details.put("handled", handled);
            details.put("pressed_on_down", pressedOnDown);
            details.put("legacy_insets", Build.VERSION.SDK_INT < Build.VERSION_CODES.P);
            details.put("density", activity.getResources().getDisplayMetrics().density);
            DisplayMetrics display = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(display);
            details.put("display", display.widthPixels + "x" + display.heightPixels + "@" + display.densityDpi);
            details.put("display_rotation", activity.getWindowManager().getDefaultDisplay().getRotation());
            View decor = activity.getWindow().getDecorView();
            details.put("window", geometry(decor));
            details.put("ui_flags", decor.getSystemUiVisibility());
            details.put("root", geometry(activity.findViewById(R.id.root_layout)));
            details.put("bottom", geometry(bottom));
            details.put("row", geometry((View) activity.findViewById(R.id.button_video).getParent()));
            for (int i = 0; i < IDS.length - 1; i++) {
                details.put(NAMES[i], geometry(activity.findViewById(IDS[i])));
            }
            DiagnosticLogManager.record("player_control_touch", details);
        } catch (Exception ignored) {
            // Diagnostics must never change touch dispatch or show a UI error.
        }
    }

    private static String geometry(View view) {
        if (view == null) return "missing";
        Rect rect = new Rect();
        boolean visible = view.getGlobalVisibleRect(rect);
        return "xy=" + Math.round(view.getX()) + "," + Math.round(view.getY())
                + ";size=" + view.getWidth() + "," + view.getHeight()
                + ";screen=" + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom
                + ";rot=" + Math.round(view.getRotation()) + ";visible=" + visible;
    }
}
