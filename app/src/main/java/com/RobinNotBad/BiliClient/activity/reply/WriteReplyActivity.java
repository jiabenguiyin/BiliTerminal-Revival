package com.RobinNotBad.BiliClient.activity.reply;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;
import android.view.Gravity;
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
import com.RobinNotBad.BiliClient.api.EmoteApi;
import com.RobinNotBad.BiliClient.api.ImageUploadApi;
import com.RobinNotBad.BiliClient.api.ReplyApi;
import com.RobinNotBad.BiliClient.event.ReplyEvent;
import com.RobinNotBad.BiliClient.model.Reply;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;
import com.google.android.material.card.MaterialCardView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

import org.json.JSONArray;

public class WriteReplyActivity extends BaseActivity {

    private static final int MAX_IMAGES = 9;
    private static final int THUMBNAIL_DP = 54;

    private static final Map<Integer, String> msgMap = new HashMap<>() {{
        put(-101, "没有登录or登录信息有误？");
        put(-102, "账号被封禁！");
        put(-509, "请求过于频繁！");
        put(12015, "需要评论验证码...？");
        put(12016, "包含敏感内容！");
        put(12025, "字数过多啦QAQ");
        put(12035, "被拉黑了...");
        put(12051, "重复评论，请勿刷屏！");
    }};

    EditText editText;
    private final ArrayList<Uri> selectedImages = new ArrayList<>();
    private TextView selectedImageText;
    private LinearLayout selectedImagePreview;
    private View selectedImageScroll;
    private MaterialCardView sendButton;
    private TextView sendLabel;
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

    boolean sent = false;
    boolean dontKyPlease = true;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_reply);

        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) {
            MsgUtil.showMsg("还没有登录喵~");
            finish();
            return;
        }

        Intent intent = getIntent();
        long oid = intent.getLongExtra("oid", 0);
        long rpid = intent.getLongExtra("rpid", 0);
        long parent = intent.getLongExtra("parent", 0);
        int replyType = intent.getIntExtra("replyType", ReplyApi.REPLY_TYPE_VIDEO);
        String parentSender = intent.getStringExtra("parentSender");
        int pos = intent.getIntExtra("pos", -1);

        editText = findViewById(R.id.editText);
        selectedImageText = findViewById(R.id.selected_images);
        selectedImagePreview = findViewById(R.id.selected_image_preview);
        selectedImageScroll = findViewById(R.id.selected_image_scroll);
        sendButton = findViewById(R.id.send);
        sendLabel = findViewById(R.id.send_reply_label);

        if (parentSender != null && !parentSender.isEmpty()) {
            editText.setText("回复 @" + parentSender + " :");
            editText.setSelection(editText.getText().length());
        }

        sendButton.setOnClickListener(view -> {
            if (sent) {
                MsgUtil.showMsg("正在发送中");
                return;
            }
            // Read views and freeze attachments on the main thread before entering the IO queue.
            final String text = editText.getText().toString();
            if (text.trim().isEmpty()) {
                MsgUtil.showMsg("请先输入评论文字，评论需要文字内容才能带图片~");
                return;
            }
            if (checkKy(text) && dontKyPlease) {
                dontKyPlease = false;
                MsgUtil.showDialog("保护措施……", getString(R.string.reply_dont_ky), 15);
                return;
            }
            final ArrayList<Uri> images = new ArrayList<>(selectedImages);
            final long account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
            setSending(true);
            CenterThreadPool.run(() -> {
                try {
                    JSONArray pictures = new JSONArray();
                    for (Uri uri : images) {
                        if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                            throw new IllegalStateException(getString(R.string.reply_send_account_changed));
                        pictures.put(ImageUploadApi.toReplyPicture(
                                ImageUploadApi.upload(getApplicationContext(), uri)));
                    }
                    if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                        throw new IllegalStateException(getString(R.string.reply_send_account_changed));
                    Pair<Integer, Reply> result = ReplyApi.sendReply(oid, rpid, parent, text, replyType,
                            pictures.length() == 0 ? null : pictures.toString());
                    int resultCode = result.first;
                    Reply resultReply = result.second;

                    if (resultCode == 0 && resultReply != null && resultReply.rpid > 0) {
                        runOnUiThread(() -> {
                            if (isDestroyed()) return;
                            MsgUtil.showMsg(getString(R.string.reply_send_submitted));
                            finish();
                        });
                        // Public visibility is a separate acknowledgement. Do not hold the
                        // composer open through its requests/backoff, or invite a duplicate POST.
                        CenterThreadPool.run(() -> confirmSubmittedReply(account, oid, replyType, resultReply, pos));
                    } else {
                        String toastMsg = resultCode == 0 ? getString(R.string.reply_send_uncertain) : "评论发送失败：\n"
                                + (msgMap.containsKey(resultCode) ? msgMap.get(resultCode) : resultCode);
                        runOnUiThread(() -> {
                            if (isDestroyed()) return;
                            setSending(false);
                            MsgUtil.showMsg(toastMsg);
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        setSending(false);
                        if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0))
                            MsgUtil.showMsg(getString(R.string.reply_send_account_changed));
                        else MsgUtil.err(e);
                    });
                }
            });
        });

        findViewById(R.id.pick_image).setOnClickListener(view -> {
            Intent imageIntent = new Intent(Intent.ACTION_GET_CONTENT)
                    .setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                imageIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            imageLauncher.launch(imageIntent);
        });

        findViewById(R.id.emote).setOnClickListener(view ->
                emoteLauncher.launch(new Intent(this, EmoteActivity.class).putExtra("from", EmoteApi.BUSINESS_REPLY)));
    }

    private void setSending(boolean sending) {
        sent = sending;
        sendButton.setEnabled(!sending);
        sendLabel.setText(sending ? R.string.reply_send_in_progress : R.string.btn_send);
        editText.setEnabled(!sending);
        findViewById(R.id.pick_image).setEnabled(!sending);
        findViewById(R.id.emote).setEnabled(!sending);
    }

    private static void confirmSubmittedReply(long account, long oid, int type, Reply reply, int pos) {
        if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
        boolean confirmed;
        try { confirmed = ReplyApi.isReplyVisible(oid, reply.rpid, type); }
        catch (Exception error) { confirmed = false; }
        final boolean visible = confirmed;
        CenterThreadPool.runOnUiThread(() -> {
            if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)) return;
            if (visible) EventBus.getDefault().post(new ReplyEvent(1, reply, pos, oid));
            MsgUtil.showMsg(com.RobinNotBad.BiliClient.BiliTerminal.context.getString(visible
                    ? R.string.reply_send_verified : R.string.reply_send_pending));
        });
    }

    /**
     * P用没有的保护措施
     *
     * @param str 评论文本
     */
    private boolean checkKy(String str) {
        if (str.contains("哔哩终端")) return true;
        if (str.contains("终端")) {
            return str.contains("表") || str.contains("b站") || str.contains("B站") || str.contains("bili") || str.contains("哔");
        }
        return false;
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
                if (sent) return;
                selectedImages.remove(index);
                updateImagePreview();
            });
            selectedImagePreview.addView(item);
        }
    }
}
