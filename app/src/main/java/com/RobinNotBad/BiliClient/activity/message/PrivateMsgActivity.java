package com.RobinNotBad.BiliClient.activity.message;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import java.io.IOException;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.base.BaseActivity;
import com.RobinNotBad.BiliClient.adapter.message.PrivateMsgAdapter;
import com.RobinNotBad.BiliClient.api.ImageUploadApi;
import com.RobinNotBad.BiliClient.api.PrivateMsgApi;
import com.RobinNotBad.BiliClient.model.PrivateMessage;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class PrivateMsgActivity extends BaseActivity {
    private static final int MAX_IMAGES = 9;

    JSONObject allMsg = new JSONObject();
    List<PrivateMessage> list = Collections.synchronizedList(new ArrayList<>());
    JSONArray emoteArray = new JSONArray();
    RecyclerView msgView;
    EditText contentEt;
    ImageButton sendBtn;
    ImageButton pickImageBtn;
    View layout_input;
    View imagePreviewContainer;
    LinearLayout imagePreviewRow;
    PrivateMsgAdapter adapter;
    long uid;
    boolean isLoadingMore = false;
    Timer refreshTimer;

    private final ArrayList<Uri> selectedImages = new ArrayList<>();
    private boolean sending;
    private boolean previewExpanded;

    private final ActivityResultLauncher<Intent> imageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Intent data = result.getData();
                addImage(data.getData());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN
                        && data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        addImage(data.getClipData().getItemAt(i).getUri());
                    }
                }
                if (selectedImages.size() > 0) {
                    previewExpanded = true;
                    updateImagePreview();
                    MsgUtil.showMsg("已选择 " + selectedImages.size() + " 张图片");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_msg);

        msgView = findViewById(R.id.msg_view);
        contentEt = findViewById(R.id.msg_input_et);
        sendBtn = findViewById(R.id.send_btn);
        pickImageBtn = findViewById(R.id.pick_image_btn);
        layout_input = findViewById(R.id.layout_input);
        imagePreviewContainer = findViewById(R.id.image_preview_container);
        imagePreviewRow = findViewById(R.id.image_preview_row);
        layout_input.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom) -> updateMessagePadding());
        View topbar = findViewById(R.id.top);
        topbar.addOnLayoutChangeListener((view, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) -> updateMessagePadding());

        Intent intent = getIntent();
        uid = intent.getLongExtra("uid", 114514);
        String draft = intent.getStringExtra("draft");
        if (savedInstanceState == null && draft != null) {
            contentEt.setText(draft);
            contentEt.setSelection(contentEt.length());
        }
        Log.e("", String.valueOf(uid));

        MsgUtil.showMsg("私信有可能被拦截\n尽量不要用终端发私信喵");

        final long requestedAccount=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        CenterThreadPool.run(() -> {
            try {
                allMsg = PrivateMsgApi.getPrivateMsg(uid, 50, 0, 0);
                list = PrivateMsgApi.getPrivateMsgList(allMsg);
                Collections.reverse(list);
                emoteArray = PrivateMsgApi.getEmoteJsonArray(allMsg);
                adapter = new PrivateMsgAdapter(list, emoteArray, this);
                
                if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.PRIVATE_MSG_AUTO_READ_ENABLE, true)) {
                    try {
                        if(requestedAccount==SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)) {
                            JSONObject ack=PrivateMsgApi.updateAck(uid, 1, 0);
                            if(ack.optInt("code",-1)==0)com.RobinNotBad.BiliClient.util.MessageUnreadCounter.refresh(true);
                        }
                    } catch (Exception e) {
                        Log.e("PrivateMsgActivity", "自动已读失败", e);
                    }
                }
                
                runOnUiThread(() -> {
                    CustomLinearManager manager = new CustomLinearManager(this);
                    manager.setStackFromEnd(true);
                    msgView.setLayoutManager(manager);
                    msgView.setAdapter(adapter);
                    msgView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                            super.onScrollStateChanged(recyclerView, newState);
                            switch (newState) {
                                case RecyclerView.SCROLL_STATE_DRAGGING:
                                    previewExpanded = false;
                                    updateImagePreview();
                                    if (!recyclerView.canScrollVertically(-1) && !isLoadingMore) {
                                        loadMore();
                                        Log.e("", "滑动到顶部，开始刷新");
                                    }
                                    break;
                            }
                        }
                    });

                    refreshTimer = new Timer();
                    refreshTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            refresh();
                        }
                    }, 15000, 15000);
                });
            } catch (Exception e) {
                runOnUiThread(() -> MsgUtil.err(e));
            }
        });

        pickImageBtn.setOnClickListener(view -> {
            if (selectedImages.isEmpty()) {
                openImagePicker();
            } else {
                previewExpanded = !previewExpanded;
                updateImagePreview();
            }
        });
        sendBtn.setOnClickListener(view -> sendMessage());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        imageLauncher.launch(intent);
    }

    private void addImage(Uri uri) {
        if (uri != null && selectedImages.size() < MAX_IMAGES && !selectedImages.contains(uri)) {
            selectedImages.add(uri);
        }
    }

    private void updateImagePreview() {
        if (imagePreviewContainer == null || imagePreviewRow == null) return;
        imagePreviewRow.removeAllViews();
        if (selectedImages.isEmpty() || !previewExpanded) {
            imagePreviewContainer.setVisibility(View.GONE);
            return;
        }
        imagePreviewContainer.setVisibility(View.VISIBLE);

        int size = dp(48);
        for (Uri uri : new ArrayList<>(selectedImages)) {
            FrameLayout item = new FrameLayout(this);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(size, size);
            itemParams.setMargins(0, 0, dp(5), 0);
            imagePreviewRow.addView(item, itemParams);

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(48, 48, 48));
            item.addView(image, new FrameLayout.LayoutParams(size, size));
            Glide.with(image).asBitmap().load(uri).override(size, size).centerCrop()
                    .apply(new RequestOptions().format(DecodeFormat.PREFER_RGB_565)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true).dontAnimate())
                    .into(image);

                FrameLayout removeHit = new FrameLayout(this);
                removeHit.setClickable(true);
                removeHit.setFocusable(true);
                removeHit.setContentDescription("删除已选择图片");
                FrameLayout.LayoutParams removeHitParams = new FrameLayout.LayoutParams(
                        dp(44), dp(44), Gravity.TOP | Gravity.RIGHT);
                item.addView(removeHit, removeHitParams);

                TextView remove = new TextView(this);
                remove.setText("x");
                remove.setTextColor(Color.WHITE);
                remove.setTextSize(10);
                remove.setGravity(Gravity.CENTER);
                GradientDrawable removeBackground = new GradientDrawable();
                removeBackground.setColor(Color.rgb(190, 45, 45));
                removeBackground.setShape(GradientDrawable.OVAL);
                remove.setBackground(removeBackground);
                FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(
                        dp(18), dp(18), Gravity.TOP | Gravity.RIGHT);
                removeHit.addView(remove, removeParams);
                removeHit.setOnClickListener(view -> {
                selectedImages.remove(uri);
                if (selectedImages.isEmpty()) previewExpanded = false;
                updateImagePreview();
            });
        }

        if (selectedImages.size() < MAX_IMAGES) {
            TextView add = new TextView(this);
            add.setText("+");
            add.setTextColor(Color.WHITE);
            add.setTextSize(22);
            add.setGravity(Gravity.CENTER);
            GradientDrawable addBackground = new GradientDrawable();
            addBackground.setColor(Color.rgb(58, 58, 58));
            addBackground.setCornerRadius(dp(3));
            add.setBackground(addBackground);
            imagePreviewRow.addView(add, new LinearLayout.LayoutParams(size, size));
            add.setContentDescription("继续添加图片");
            add.setOnClickListener(view -> openImagePicker());
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateMessagePadding() {
        int top = findViewById(R.id.top).getHeight();
        int bottom = layout_input.getHeight() + dp(4);
        if (msgView.getPaddingTop() != top || msgView.getPaddingBottom() != bottom) {
            msgView.setPadding(msgView.getPaddingLeft(), top, msgView.getPaddingRight(), bottom);
        }
    }

    private void sendMessage() {
        if (sending) return;
        final String content = contentEt.getText().toString();
        final ArrayList<Uri> images = new ArrayList<>(selectedImages);
        if (content.trim().isEmpty() && images.isEmpty()) {
            MsgUtil.showMsg("你还木有输入喵~");
            return;
        }
        sending = true;
        final long sendingAccount=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        if(sendingAccount<=0){sending=false;MsgUtil.showMsg("请先登录后发送私信");return;}
        final long thanksAccount=getIntent().getLongExtra("thanks_account",0);
        if(thanksAccount>0&&thanksAccount!=sendingAccount){sending=false;MsgUtil.showMsg("账号已切换，请重新打开感谢入口");return;}
        sendBtn.setEnabled(false);
        pickImageBtn.setEnabled(false);
        CenterThreadPool.run(() -> {
            boolean success = true;
            String failureMessage = "发送失败";
            try {
                long selfUid = sendingAccount;
                if(selfUid!=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0))throw new IOException("账号已切换，请重试");
                if (!content.trim().isEmpty()) {
                    JSONObject result = PrivateMsgApi.sendMsg(selfUid, uid, PrivateMessage.TYPE_TEXT,
                            System.currentTimeMillis() / 1000,
                            new JSONObject().put("content", content).toString());
                    if (result.optInt("code", -1) != 0) {
                        success = false;
                        failureMessage = result.optString("message", "文字消息发送失败");
                    } else if(thanksAccount==sendingAccount) {
                        com.RobinNotBad.BiliClient.util.MessageThanksState.confirmSent(sendingAccount,
                                getIntent().getLongExtra("thanks_notice_id",0),uid);
                    }
                }
                for (Uri image : images) {
                    if (!success) break;
                    JSONObject upload = ImageUploadApi.upload(getApplicationContext(), image);
                    JSONObject result = PrivateMsgApi.sendMsg(selfUid, uid, PrivateMessage.TYPE_PIC,
                            System.currentTimeMillis() / 1000,
                            PrivateMsgApi.toPictureContent(upload));
                    if (result.optInt("code", -1) != 0) {
                        success = false;
                        failureMessage = result.optString("message", "图片消息发送失败");
                    }
                }
            } catch (Exception error) {
                success = false;
                failureMessage = error.getMessage() == null ? "发送失败" : error.getMessage();
            }
            boolean finalSuccess = success;
            String finalFailureMessage = failureMessage;
            runOnUiThread(() -> {
                sending = false;
                sendBtn.setEnabled(true);
                pickImageBtn.setEnabled(true);
                if (finalSuccess) {
                    contentEt.setText("");
                    selectedImages.clear();
                    previewExpanded = false;
                    updateImagePreview();
                    MsgUtil.showMsg("发送成功");
                    refresh();
                } else {
                    MsgUtil.showMsg(finalFailureMessage);
                }
            });
        });
    }
    @Override
    protected void onDestroy() {
        if (refreshTimer != null) refreshTimer.cancel();
        refreshTimer = null;
        super.onDestroy();
    }

    private void refresh() {
        CenterThreadPool.run(() -> {
            try {
                int oldListSize = list.size();
                long beginSeqno = PrivateMsgApi.getRefreshBeginSeqno(list);
                JSONObject msgResult = PrivateMsgApi.getPrivateMsg(uid, 50, beginSeqno, 0);
                ArrayList<PrivateMessage> newList = PrivateMsgApi.getPrivateMsgList(msgResult);
                if (newList.size() > 0) {
                    for (int i = 0; i < PrivateMsgApi.getEmoteJsonArray(msgResult).length(); ++i) {
                        JSONObject emote = PrivateMsgApi.getEmoteJsonArray(msgResult).getJSONObject(i);
                        emoteArray.put(emote);
                    }
                    Collections.reverse(newList);
                    runOnUiThread(() -> {
                        list.addAll(newList);
                        adapter.notifyItemRangeInserted(oldListSize, newList.size());
                        msgView.smoothScrollToPosition(list.size() - 1);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> MsgUtil.err(e));
            }
        });
    }

    @SuppressLint("SuspiciousIndentation")
    private void loadMore() {
        isLoadingMore = true;
        MsgUtil.showMsg("加载更多中...");
        CenterThreadPool.run(() -> {
            try {
                if (allMsg.getInt("has_more") == 1) {
                    allMsg = PrivateMsgApi.getPrivateMsg(uid, 15, 0, list.get(0).msgSeqno);
                    ArrayList<PrivateMessage> newList = PrivateMsgApi.getPrivateMsgList(allMsg);
                    Collections.reverse(newList);

                    for (int i = 0; i < PrivateMsgApi.getEmoteJsonArray(allMsg).length(); ++i) {
                        JSONObject emote = PrivateMsgApi.getEmoteJsonArray(allMsg).getJSONObject(i);
                        emoteArray.put(emote);
                    }
                    runOnUiThread(() -> {
                        adapter.addItem(newList);
                        MsgUtil.showMsg("已加载更多消息！");
                    });
                    isLoadingMore = false;
                } else runOnUiThread(() -> MsgUtil.showMsg("没有更多消息了"));
            } catch (Exception e) {
                runOnUiThread(() -> MsgUtil.err(e));
            }
        });
    }
}
