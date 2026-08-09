package com.RobinNotBad.BiliClient.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.viewpager.widget.ViewPager;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.adapter.viewpager.ImageViewerPagerAdapter;
import com.RobinNotBad.BiliClient.ui.widget.PhotoViewpager;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;

import java.util.ArrayList;

public class ImageViewerActivity extends BaseActivity {
    private long longClickTimestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_BiliClient);
        setContentView(R.layout.activity_image_viewer);

        ArrayList<String> imageList = getIntent().getStringArrayListExtra("imageList");
        if (imageList == null || imageList.isEmpty()) {
            MsgUtil.showMsg("没有可查看的图片");
            finish();
            return;
        }

        PhotoViewpager viewPager = findViewById(R.id.viewPager);
        TextView pageText = findViewById(R.id.text_page);
        pageText.setText("第 1/" + imageList.size() + "张");

        ImageButton download = findViewById(R.id.btn_download);
        download.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - longClickTimestamp < 3000) {
                Intent downloadIntent = new Intent(this, DownloadActivity.class)
                        .putExtra("link", imageList.get(viewPager.getCurrentItem()))
                        .putExtra("path", FileUtil.getPicturePath().getAbsolutePath())
                        .putExtra("type", 0);
                startActivity(downloadIntent);
            } else {
                MsgUtil.showMsg("再次点击下载");
            }
            longClickTimestamp = now;
        });

        viewPager.setOffscreenPageLimit(1);
        viewPager.setAdapter(new ImageViewerPagerAdapter(imageList));
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onPageSelected(int position) {
                pageText.setText("第 " + (position + 1) + "/" + imageList.size() + "张");
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }
}
