package com.RobinNotBad.BiliClient.adapter.viewpager;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.github.chrisbanes.photoview.PhotoView;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

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
        PhotoView photoView = new PhotoView(container.getContext());
        photoView.setMaximumScale(6.25f);
        container.addView(photoView);
        RequestOptions requestOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
        if (loadOriginal) {
            requestOptions = requestOptions.override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
        }
        Glide.with(photoView)
                .asDrawable()
                .load(loadOriginal
                        ? GlideUtil.url_original(imageUrls.get(position))
                        : GlideUtil.url_hq(imageUrls.get(position)))
                .apply(requestOptions)
                .into(photoView);
        return photoView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        PhotoView photoView = (PhotoView) object;
        Glide.with(container.getContext().getApplicationContext()).clear(photoView);
        container.removeView(photoView);
    }
}
