package com.RobinNotBad.BiliClient.adapter.viewpager;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

public final class ImageViewerPagerAdapter extends PagerAdapter {
    private final List<String> imageUrls;

    public ImageViewerPagerAdapter(List<String> imageUrls) {
        this.imageUrls = imageUrls;
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
        PhotoView photoView = new PhotoView(container.getContext());
        photoView.setMaximumScale(6.25f);
        container.addView(photoView);
        Glide.with(photoView)
                .asDrawable()
                .load(GlideUtil.url_hq(imageUrls.get(position)))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(photoView);
        return photoView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        PhotoView photoView = (PhotoView) object;
        Glide.with(photoView).clear(photoView);
        container.removeView(photoView);
    }
}
