package com.RobinNotBad.BiliClient.ui;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.os.Build;
import android.os.SystemClock;

import androidx.appcompat.app.AlertDialog;

import com.RobinNotBad.BiliClient.util.BilibiliVideoLinkParser;
import com.RobinNotBad.BiliClient.util.LinkUrlUtil;

public final class ClipboardVideoLinkPrompt {
    private static final long REPEAT_PROMPT_DELAY_MS = 30_000L;
    private static String lastPromptedUrl;
    private static long lastPromptedAt;

    private ClipboardVideoLinkPrompt() {}

    public static void showIfPresent(Activity activity) {
        if (isUnavailable(activity)) return;
        String url = readVideoUrl(activity);
        if (url == null) return;

        long now = SystemClock.elapsedRealtime();
        if (url.equals(lastPromptedUrl) && now - lastPromptedAt < REPEAT_PROMPT_DELAY_MS) return;
        lastPromptedUrl = url;
        lastPromptedAt = now;

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("发现分享链接")
                .setMessage("剪贴板中有一个 B 站视频，是否立即查看？")
                .setNegativeButton("取消", null)
                .setPositiveButton("立即查看", (ignored, which) -> {
                    if (!isUnavailable(activity))
                        LinkUrlUtil.handleWebURL(activity, url);
                })
                .create();
        TerminalDialogStyle.prepare(dialog);
        dialog.show();
    }

    private static boolean isUnavailable(Activity activity) {
        return activity == null || activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed());
    }

    private static String readVideoUrl(Activity activity) {
        try {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).getText();
            if (text == null) return null;
            return BilibiliVideoLinkParser.extractVideoUrl(text.toString());
        } catch (SecurityException | IllegalStateException ignored) {
            return null;
        }
    }
}
