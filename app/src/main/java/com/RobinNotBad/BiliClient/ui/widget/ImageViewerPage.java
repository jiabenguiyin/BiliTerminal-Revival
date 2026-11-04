package com.RobinNotBad.BiliClient.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.DiagnosticLogManager;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.ImageDisplayPolicy;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.signature.ObjectKey;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.decoder.CompatDecoderFactory;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder;
import com.github.chrisbanes.photoview.PhotoView;

import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads encoded bytes to disk; static originals are never decoded as one huge bitmap. */
public final class ImageViewerPage extends FrameLayout {
    private static final ExecutorService TILE_EXECUTOR = Executors.newFixedThreadPool(2);
    private final TextView status;
    private final String source;
    private final com.bumptech.glide.RequestManager requests;
    private final long heapBytes = Runtime.getRuntime().maxMemory();
    private final CustomViewTarget<ImageViewerPage, File> fileTarget;
    private SubsamplingScaleImageView tiled;
    private PhotoView photo;
    private boolean released;
    private int generation;

    public ImageViewerPage(Context context, String url, boolean original) {
        super(context);
        requests = Glide.with(this);
        source = original ? GlideUtil.url_original(url) : GlideUtil.url_hq(url);
        status = new TextView(context);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        status.setPadding(padding, padding, padding, padding);
        addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        fileTarget = new CustomViewTarget<ImageViewerPage, File>(this) {
            @Override public void onLoadFailed(@Nullable Drawable errorDrawable) { failed("图片加载失败，点击重试"); }
            @Override protected void onResourceCleared(@Nullable Drawable placeholder) { clearContent(); }
            @Override public void onResourceReady(@NonNull File file, @Nullable Transition<? super File> transition) {
                final int request = generation;
                CenterThreadPool.run(() -> {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try { BitmapFactory.decodeFile(file.getAbsolutePath(), bounds); }
                    catch (RuntimeException error) {
                        post(() -> { if (!released && request == generation) failed("图片解码失败，点击重试"); });
                        return;
                    }
                    post(() -> {
                        if (released || request != generation) return;
                        if (!ImageDisplayPolicy.validDimensions(bounds.outWidth, bounds.outHeight)) {
                            failed("图片格式或尺寸不受支持，点击重试");
                        } else if ("image/jpeg".equals(bounds.outMimeType) || "image/png".equals(bounds.outMimeType)
                                || "image/webp".equals(bounds.outMimeType)) {
                            showTiled(file, bounds.outWidth, bounds.outHeight);
                        } else {
                            showPreview(file, "image/gif".equals(bounds.outMimeType), bounds.outWidth, bounds.outHeight);
                        }
                    });
                });
            }
        };
        load();
    }

    private void load() {
        if (released) return;
        generation++;
        clearContent();
        status.setText("正在加载图片…");
        status.setVisibility(View.VISIBLE);
        status.setGravity(Gravity.CENTER);
        status.setOnClickListener(null);
        // File targets cache compressed data, not a decoded full-size drawable.
        com.bumptech.glide.RequestBuilder<File> request = requests.asFile().load(source).diskCacheStrategy(DiskCacheStrategy.DATA);
        // A successfully downloaded but corrupt file must not poison every manual retry.
        if (generation > 1) request.signature(new ObjectKey("image-viewer-retry-" + System.nanoTime()));
        request.into(fileTarget);
    }

    private void showTiled(File file, int width, int height) {
        tiled = new SubsamplingScaleImageView(getContext());
        tiled.setMaxTileSize(ImageDisplayPolicy.tileSide(heapBytes));
        tiled.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
        tiled.setExecutor(TILE_EXECUTOR);
        tiled.setEagerLoadingEnabled(false);
        // Use source pixels at 1:1 zoom even when the watch overrides display DPI.
        // Memory is bounded by tiles rather than by lowering detail resolution.
        tiled.setMinimumTileDpi(0);
        tiled.setRegionDecoderFactory(new CompatDecoderFactory<>(SkiaImageRegionDecoder.class, Bitmap.Config.ARGB_8888));
        tiled.setBitmapDecoderFactory(new CompatDecoderFactory<>(SkiaImageDecoder.class, Bitmap.Config.ARGB_8888));
        final int request = generation;
        tiled.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
            @Override public void onReady() {
                if (released || request != generation || tiled == null) return;
                float minimum = tiled.getMinScale();
                float maximum = Math.max(1f, minimum * 6.25f);
                tiled.setMaxScale(maximum);
                tiled.setDoubleTapZoomScale(Math.min(maximum, Math.max(1f, minimum * 2.5f)));
            }
            @Override public void onImageLoaded() { if (!released && request == generation) status.setVisibility(View.GONE); }
            @Override public void onImageLoadError(Exception error) { if (request == generation) failed("图片解码失败，点击重试"); }
            @Override public void onTileLoadError(Exception error) { if (request == generation) failed("图片分块加载失败，点击重试"); }
        });
        addView(tiled, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tiled.setImage(ImageSource.uri(file.getAbsolutePath()).tilingEnabled());
        record("tiled", width, height);
    }

    private void showPreview(File file, boolean gif, int width, int height) {
        final boolean animate = gif && ImageDisplayPolicy.canAnimate(width, height, heapBytes);
        final int requestGeneration = generation;
        photo = new PhotoView(getContext());
        photo.setMaximumScale(6.25f);
        addView(photo, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        int side = ImageDisplayPolicy.previewSide(heapBytes);
        RequestListener<Drawable> listener = new RequestListener<Drawable>() {
            @Override public boolean onLoadFailed(@Nullable GlideException error, Object model, Target<Drawable> target, boolean first) {
                if (requestGeneration == generation) failed("图片解码失败，点击重试"); return false;
            }
            @Override public boolean onResourceReady(Drawable drawable, Object model, Target<Drawable> target, DataSource from, boolean first) {
                if (released || requestGeneration != generation) return true;
                Bitmap bitmap = drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap()
                        : drawable instanceof GifDrawable ? ((GifDrawable) drawable).getFirstFrame() : null;
                if (bitmap == null || !ImageDisplayPolicy.safePreview(bitmap.getWidth(), bitmap.getHeight(), bitmap.getByteCount(), heapBytes)) {
                    failed("图片尺寸过大，无法安全预览"); return true;
                }
                if (gif && !animate) {
                    status.setText("大尺寸动图以静态预览显示，可下载原图");
                    status.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                    status.setClickable(false);
                } else status.setVisibility(View.GONE);
                return false;
            }
        };
        com.bumptech.glide.RequestBuilder<Drawable> request = requests.asDrawable().load(file)
                .override(side, side).downsample(DownsampleStrategy.AT_MOST).disallowHardwareConfig();
        if (!animate) request = request.dontAnimate().decode(BitmapDrawable.class);
        request.listener(listener).into(photo);
        record(animate ? "gif" : "bounded_preview", width, height);
    }

    private void failed(String message) {
        if (released) return;
        status.setGravity(Gravity.CENTER);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        status.setOnClickListener(view -> load());
    }

    private void clearContent() {
        if (tiled != null) {
            tiled.setOnImageEventListener(null);
            tiled.recycle(); removeView(tiled); tiled = null;
        }
        if (photo != null) { requests.clear(photo); removeView(photo); photo = null; }
    }

    public void release() {
        if (released) return;
        released = true;
        generation++;
        clearContent();
        requests.clear(fileTarget);
        status.setOnClickListener(null);
    }

    private void record(String mode, int width, int height) {
        try {
            JSONObject details = new JSONObject().put("mode", mode).put("width", width).put("height", height)
                    .put("tile_side", ImageDisplayPolicy.tileSide(heapBytes));
            DiagnosticLogManager.record("image_viewer_decode", details);
        } catch (org.json.JSONException ignored) { }
    }
}
