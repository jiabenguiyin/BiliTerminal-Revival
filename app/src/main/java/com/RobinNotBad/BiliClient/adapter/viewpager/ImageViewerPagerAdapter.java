package com.RobinNotBad.BiliClient.adapter.viewpager;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import com.RobinNotBad.BiliClient.ui.widget.ImageViewerPage;

import java.util.List;

public final class ImageViewerPagerAdapter extends PagerAdapter {
    private final List<String> imageUrls;
    private final boolean loadOriginal;

    public ImageViewerPagerAdapter(List<String> imageUrls) {
        this(imageUrls, false);
    }

    public ImageViewerPagerAdapter(List<String> imageUrls, boolean loadOriginal) {
        this.imageUrls = imageUrls;
        this.loadOriginal = loadOriginal;
    }

    @Override
    public int getCount() {
        return imageUrls.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        ImageViewerPage page = new ImageViewerPage(container.getContext(), imageUrls.get(position), loadOriginal);
        container.addView(page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return page;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        ImageViewerPage page = (ImageViewerPage) object;
        page.release();
        container.removeView(page);
    }
}
