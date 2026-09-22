package com.RobinNotBad.BiliClient.activity.dynamic.send;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.EmoteActivity;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.adapter.dynamic.DynamicHolder;
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder;
import com.RobinNotBad.BiliClient.api.EmoteApi;
import com.RobinNotBad.BiliClient.api.ImageUploadApi;
import com.RobinNotBad.BiliClient.model.Dynamic;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import com.RobinNotBad.BiliClient.util.ToolsUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;

import java.util.ArrayList;

/**
 * 发送动态输入Activity，直接copy的WriteReplyActivity
 * 换成了ActivityResult
 * （我并不怎么会写）
 */
public class SendDynamicActivity extends BaseActivity {

    private static final int MAX_IMAGES = 9;
    private static final int THUMBNAIL_DP = 54;

    EditText editText;
    private final ArrayList<Uri> selectedImages = new ArrayList<>();
    private TextView selectedImageText;
    private LinearLayout selectedImagePreview;
    private View selectedImageScroll;
    private boolean sending;

    private final ActivityResultLauncher<Intent> imageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Intent data = result.getData();
                int before = selectedImages.size();
                addImage(data.getData());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        addImage(data.getClipData().getItemAt(i).getUri());
                    }
                }
                if (selectedImages.size() == before && before >= MAX_IMAGES) {
                    MsgUtil.showMsg("最多只能添加 9 张图片");
                } else {
                    updateImagePreview();
                }
            });

    private final ActivityResultLauncher<Intent> emoteLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (result) -> {
        int code = result.getResultCode();
        Intent data = result.getData();
        if (code == RESULT_OK && data != null && data.hasExtra("text")) {
            editText.append(data.getStringExtra("text"));
        }
    });

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        asyncInflate(R.layout.activity_send_dynamic, (layoutView, resId) -> {

            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) {
                setResult(RESULT_CANCELED);
                finish();
                MsgUtil.showMsg("还没有登录喵~");
            }

            editText = findViewById(R.id.editText);
            selectedImageText = findViewById(R.id.selected_images);
            selectedImagePreview = findViewById(R.id.selected_image_preview);
            selectedImageScroll = findViewById(R.id.selected_image_scroll);
            MaterialCardView send = findViewById(R.id.send);

            FrameLayout extraCard = findViewById(R.id.forwardCard);
            VideoInfo video = null;
            Dynamic forward = null;
            if (TerminalContext.getInstance().getForwardContent() instanceof VideoInfo) {
                video = (VideoInfo) TerminalContext.getInstance().getForwardContent();
            } else {
                forward = (Dynamic) TerminalContext.getInstance().getForwardContent();
            }
            if (forward != null) {
                View childCard = View.inflate(this, R.layout.cell_dynamic, extraCard);
                DynamicHolder holder = new DynamicHolder(childCard, this, false);
                holder.showDynamic(this, forward, false);
            } else if (video != null) {
                VideoCardHolder holder = new VideoCardHolder(LayoutInflater.from(this).inflate(R.layout.cell_video_list, extraCard));
                holder.showVideoCard(video.toCard(), this);
            }

            findViewById(R.id.pick_image).setOnClickListener(view -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                        .setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                imageLauncher.launch(intent);
            });

            send.setOnClickListener(view -> {
                if (sending) {
                    MsgUtil.showMsg("图片还在上传中");
                    return;
                }
                String text = editText.getText().toString();
                if (text.trim().isEmpty() && selectedImages.isEmpty()) {
                    MsgUtil.showMsg("还没输入内容或选择图片呢~");
                    return;
                }
                sending = true;
                send.setEnabled(false);
                CenterThreadPool.run(() -> {
                    try {
                        JSONArray pics = new JSONArray();
                        for (Uri uri : selectedImages) {
                            pics.put(ImageUploadApi.toDynamicPicture(
                                    ImageUploadApi.upload(getApplicationContext(), uri)));
                        }
                        Intent result = new Intent();
                        Bundle bundle = SendDynamicActivity.this.getIntent().getExtras();
                        if (bundle != null) result.putExtras(bundle);
                        result.putExtra("text", text);
                        result.putExtra("pics_json", pics.toString());
                        runOnUiThread(() -> {
                            setResult(RESULT_OK, result);
                            finish();
                        });
                    } catch (Exception e) {
                        sending = false;
                        runOnUiThread(() -> {
                            send.setEnabled(true);
                            MsgUtil.showMsg("图片上传失败：" + e.getMessage());
                        });
                    }
                });
            });

            findViewById(R.id.emote).setOnClickListener(view ->
                    emoteLauncher.launch(new Intent(this, EmoteActivity.class).putExtra("from", EmoteApi.BUSINESS_DYNAMIC)));
        });
    }

    private void addImage(Uri uri) {
        if (uri != null && selectedImages.size() < MAX_IMAGES && !selectedImages.contains(uri)) {
            selectedImages.add(uri);
        }
    }

    private void updateImagePreview() {
        if (selectedImageText == null) return;
        selectedImageText.setText(selectedImages.isEmpty()
                ? "未选择图片"
                : "已选择 " + selectedImages.size() + "/9 张图片");
        selectedImagePreview.removeAllViews();
        selectedImageScroll.setVisibility(selectedImages.isEmpty() ? View.GONE : View.VISIBLE);
        int size = ToolsUtil.dp2px(THUMBNAIL_DP);
        for (int i = 0; i < selectedImages.size(); i++) {
            final int index = i;
            FrameLayout item = new FrameLayout(this);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(size, size);
            itemParams.setMargins(0, 0, ToolsUtil.dp2px(5), 0);
            item.setLayoutParams(itemParams);

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(42, 42, 42));
            item.addView(image, new FrameLayout.LayoutParams(size, size));
            Glide.with(image).asBitmap().load(selectedImages.get(i)).override(size, size).centerCrop()
                    .apply(new RequestOptions()
                            .format(DecodeFormat.PREFER_RGB_565)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .dontAnimate())
                    .placeholder(R.mipmap.akari).into(image);

            TextView delete = new TextView(this);
            delete.setText("X");
            delete.setTextColor(Color.WHITE);
            delete.setTextSize(10);
            delete.setGravity(Gravity.CENTER);
            GradientDrawable deleteBackground = new GradientDrawable();
            deleteBackground.setColor(Color.rgb(190, 45, 45));
            deleteBackground.setShape(GradientDrawable.OVAL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                delete.setBackground(deleteBackground);
            } else {
                delete.setBackgroundDrawable(deleteBackground);
            }
            FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(
                    ToolsUtil.dp2px(18), ToolsUtil.dp2px(18), Gravity.TOP | Gravity.RIGHT);
            deleteParams.setMargins(0, 0, ToolsUtil.dp2px(1), 0);
            item.addView(delete, deleteParams);
            delete.setOnClickListener(view -> {
                selectedImages.remove(index);
                updateImagePreview();
            });
            selectedImagePreview.addView(item);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        TerminalContext.getInstance().setForwardContent(null);
    }
}
