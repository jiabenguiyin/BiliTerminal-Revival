package com.RobinNotBad.BiliClient.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.helper.TutorialHelper;
import com.RobinNotBad.BiliClient.model.Tutorial;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class TutorialActivity extends BaseActivity {
    private int wait_time = 3;

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        asyncInflate(R.layout.activity_tutorial, (layoutView, resId) -> {

            Intent intent = getIntent();

            Tutorial tutorial = Objects.requireNonNull(TutorialHelper.loadTutorial(getResources().getXml(intent.getIntExtra("xml_id", R.xml.tutorial_recommend))));

            ((TextView) findViewById(R.id.text_title)).setText(tutorial.name);
            ((TextView) findViewById(R.id.content)).setText(TutorialHelper.loadText(tutorial.content));

            ImageView imageView = findViewById(R.id.image_view);
            int imageId = resolveTutorialImage(tutorial.imgid);
            if (imageId > 0) {
                imageView.setImageResource(imageId);
                imageView.setVisibility(View.VISIBLE);
            } else {
                imageView.setVisibility(View.GONE);
            }

            MaterialButton close_btn = findViewById(R.id.close_btn);
            close_btn.setEnabled(false);
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(() -> {
                        if (wait_time > 0) {
                            close_btn.setText(String.format(Locale.getDefault(), "已阅(%ds)", wait_time));
                            close_btn.setEnabled(false);
                            wait_time--;
                        } else {
                            close_btn.setText("已阅");
                            close_btn.setEnabled(true);
                            timer.cancel();
                        }
                    });
                }
            }, 0, 1000);
            close_btn.setOnClickListener(view -> {
                SharedPreferencesUtil.putInt("tutorial_ver_" + intent.getStringExtra("tag"), intent.getIntExtra("version", -1));
                finish();
            });

            View scrollView = findViewById(R.id.scrollView);
            scrollView.setFocusable(true);
            scrollView.setFocusableInTouchMode(true);
            scrollView.requestFocus();
        });
    }

    @Override
    public void onBackPressed() {
    }

    private int resolveTutorialImage(String resourceName) {
        if (resourceName == null) return 0;
        String value = resourceName.trim();
        int separator = value.indexOf('/');
        if (separator <= 0 || separator >= value.length() - 1) return 0;

        String type = value.substring(0, separator).trim();
        String name = value.substring(separator + 1).trim();
        int imageId = getResources().getIdentifier(name, type, getPackageName());
        if (imageId > 0) return imageId;

        // Keep tutorial images available on old resource implementations where
        // getIdentifier() can fail for mipmap-nodpi entries.
        switch (name) {
            case "tutorial_article":
                return R.mipmap.tutorial_article;
            case "tutorial_dynamic":
                return R.mipmap.tutorial_dynamic;
            case "tutorial_recommend":
                return R.mipmap.tutorial_recommend;
            case "tutorial_search":
                return R.mipmap.tutorial_search;
            case "tutorial_space":
                return R.mipmap.tutorial_space;
            case "tutorial_video":
                return R.mipmap.tutorial_video;
            default:
                return 0;
        }
    }
}
