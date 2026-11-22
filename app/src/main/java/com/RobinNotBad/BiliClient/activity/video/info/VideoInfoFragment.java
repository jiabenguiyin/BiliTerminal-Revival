package com.RobinNotBad.BiliClient.activity.video.info;

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity;
import com.RobinNotBad.BiliClient.activity.base.BaseFragment;
import com.RobinNotBad.BiliClient.activity.dynamic.send.SendDynamicActivity;
import com.RobinNotBad.BiliClient.activity.search.SearchActivity;
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity;
import com.RobinNotBad.BiliClient.activity.user.WatchLaterActivity;
import com.RobinNotBad.BiliClient.activity.video.MultiPageActivity;
import com.RobinNotBad.BiliClient.activity.video.QualityChooserActivity;
import com.RobinNotBad.BiliClient.activity.video.collection.CollectionInfoActivity;
import com.RobinNotBad.BiliClient.adapter.user.UpListAdapter;
import com.RobinNotBad.BiliClient.api.BangumiApi;
import com.RobinNotBad.BiliClient.api.DynamicApi;
import com.RobinNotBad.BiliClient.api.ExperienceRecordApi;
import com.RobinNotBad.BiliClient.api.LikeCoinFavApi;
import com.RobinNotBad.BiliClient.api.PlayerApi;
import com.RobinNotBad.BiliClient.api.ShareApi;
import com.RobinNotBad.BiliClient.api.VideoInfoApi;
import com.RobinNotBad.BiliClient.api.WatchLaterApi;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.model.VideoInfo;
import com.RobinNotBad.BiliClient.ui.widget.RadiusBackgroundSpan;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.AccountManager;
import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.VideoStorageUtil;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.TerminalContext;
import com.RobinNotBad.BiliClient.util.ToolsUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//真正的视频详情页
//2023-07-17

public class VideoInfoFragment extends BaseFragment {

    final int RESULT_ADDED = 1;
    final int RESULT_DELETED = -1;
    final ActivityResultLauncher<Intent> favLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<>() {
        @Override
        public void onActivityResult(ActivityResult o) {
            if (fav == null || getView() == null) return;
            int code = o.getResultCode();
            if (code == RESULT_ADDED) {
                fav.setImageResource(R.drawable.icon_fav_1);
            } else if (code == RESULT_DELETED) {
                fav.setImageResource(R.drawable.icon_fav_0);
            }
        }
    });
    private VideoInfo videoInfo;
    // 其实我不会用，也是抄的上面的😡
    final ActivityResultLauncher<Intent> writeDynamicLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            int code = result.getResultCode();
            Intent data = result.getData();
            if (code == RESULT_OK && data != null) {
                String text = data.getStringExtra("text");
                CenterThreadPool.run(() -> {
                    try {
                        long dynId;
                        Map<String, Long> atUids = new HashMap<>();
                        Pattern pattern = Pattern.compile("@(\\S+)\\s");
                        Matcher matcher = pattern.matcher(text);
                        while (matcher.find()) {
                            String matchedString = matcher.group(1);
                            long uid;
                            if ((uid = DynamicApi.mentionAtFindUser(matchedString)) != -1) {
                                atUids.put(matchedString, uid);
                            }
                        }
                        dynId = DynamicApi.relayVideo(text, (atUids.isEmpty() ? null : atUids), videoInfo.aid);

                        if (dynId != -1) MsgUtil.showMsg("转发成功~");
                        else MsgUtil.showMsg("转发失败");

                    } catch (Exception e) {
                        MsgUtil.err(e);
                    }
                });
            }
        }
    });
    private PlayerData playerData;
    private long aid;
    private String bvid;
    private TextView description;
    private TextView tagsText;
    private ImageView like, coin, fav;
    private int coinAdd = 0;
    private boolean shareRequestInFlight;
    private long pendingShareAccount;
    private String pendingShareCookies = "";
    private long pendingShareAid;
    private ArrayList<ExperienceRecordApi.Record> pendingShareExperienceBefore;
    private final ActivityResultLauncher<Intent> shareLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onShareReturned);
    private boolean desc_expand = false, tags_expand = false;
    private Animation shakeAnimation;
    private Runnable tripleActionRunnable;
    private boolean isTripleInProgress = false;
    private boolean tripleRequestInFlight;
    private final Handler tripleResultHandler = new Handler(Looper.getMainLooper());
    private com.RobinNotBad.BiliClient.ui.VideoReportController reportController;

    public VideoInfoFragment() {
    }


    public static VideoInfoFragment newInstance(long aid, String bvid) {
        Bundle args = new Bundle();
        args.putLong("aid", aid);
        args.putString("bvid", bvid);
        VideoInfoFragment fragment = new VideoInfoFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        if (bundle == null) {
            MsgUtil.showMsg("视频详情页：数据为空");
            return;
        }
        aid = bundle.getLong("aid");
        bvid = bundle.getString("bvid");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootview, Bundle savedInstanceState) {
        super.onViewCreated(rootview, savedInstanceState);

        TerminalContext.getInstance().getVideoInfoByAidOrBvId(aid, bvid).observe(getViewLifecycleOwner(), (videoInfoResult) -> videoInfoResult.onSuccess((videoInfo) -> {
            if (getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;
            this.videoInfo = videoInfo;

            if (videoInfo == null) {
                Activity activity = getActivity();
                if (activity == null) return;
                activity.finish();
                return;
            }

            initView(rootview);
        }).onFailure((error) -> {
        }));
    }

    @SuppressLint("SetTextI18n")
    private void initView(View rootview) {
        if (SharedPreferencesUtil.getBoolean("ui_landscape", false)) {
            WindowManager windowManager = (WindowManager) rootview.getContext().getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            if (Build.VERSION.SDK_INT >= 17) display.getRealMetrics(metrics);
            else display.getMetrics(metrics);
            int paddings = metrics.widthPixels / 6;
            rootview.setPadding(paddings, 0, paddings, 0);
        }

        ImageView cover = rootview.findViewById(R.id.img_cover);
        if(reportController!=null)reportController.close();
        reportController=new com.RobinNotBad.BiliClient.ui.VideoReportController(requireActivity(),
                rootview.findViewById(R.id.video_report_button),videoInfo.aid);
        TextView title = rootview.findViewById(R.id.text_title);
        description = rootview.findViewById(R.id.description);
        tagsText = rootview.findViewById(R.id.tags);
        MaterialCardView exclusiveTip = rootview.findViewById(R.id.exclusiveTip);
        RecyclerView up_recyclerView = rootview.findViewById(R.id.up_recyclerView);
        TextView exclusiveTipLabel = rootview.findViewById(R.id.exclusiveTipLabel);
        TextView viewCount = rootview.findViewById(R.id.viewCount);
        TextView timeText = rootview.findViewById(R.id.timeText);
        TextView durationText = rootview.findViewById(R.id.durationText);
        MaterialButton play = rootview.findViewById(R.id.play);
        MaterialButton addWatchlater = rootview.findViewById(R.id.addWatchlater);
        MaterialButton download = rootview.findViewById(R.id.download);
        MaterialButton relay = rootview.findViewById(R.id.relay);
        MaterialButton share = rootview.findViewById(R.id.share_video);
        TextView bvidText = rootview.findViewById(R.id.bvidText);
        TextView danmakuCount = rootview.findViewById(R.id.danmakuCount);
        like = rootview.findViewById(R.id.btn_like);
        coin = rootview.findViewById(R.id.btn_coin);
        fav = rootview.findViewById(R.id.btn_fav);
        TextView likeLabel = rootview.findViewById(R.id.like_label);
        TextView coinLabel = rootview.findViewById(R.id.coin_label);
        TextView favLabel = rootview.findViewById(R.id.fav_label);
        MaterialCardView collectionCard = rootview.findViewById(R.id.collection);

        rootview.setVisibility(View.GONE);


        if (videoInfo.epid != -1) { //不是空的的话就应该跳转到番剧页面了
            Context context = rootview.getContext();
            if (context == null) return;
            CenterThreadPool.run(() -> {
                TerminalContext.getInstance()
                        .enterVideoDetailPage(context, BangumiApi.getMdidFromEpid(videoInfo.epid), null, "media");
                Activity activity = getActivity();
                if (activity == null) return;
                activity.finish();
            });
            return;
        }

        //显示封面
        Glide.with(getAppContext()).asDrawable().load(GlideUtil.url(videoInfo.cover)).placeholder(R.mipmap.placeholder)
                .transition(GlideUtil.getTransitionOptions())
                .apply(RequestOptions.bitmapTransform(new RoundedCorners(ToolsUtil.dp2px(4))).sizeMultiplier(0.85f).skipMemoryCache(true).dontAnimate())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(cover);

        if (SharedPreferencesUtil.getBoolean("tags_enable", true)) {
            CenterThreadPool.run(() -> {
                try {
                    SpannableStringBuilder tags_spannable
                            = getDescSpan(VideoInfoApi.getTags(videoInfo.aid));

                    runOnUiThread(() -> {
                        tagsText.setMovementMethod(LinkMovementMethod.getInstance());
                        tagsText.setText(tags_spannable.toString());
                        tagsText.setOnClickListener(view1 -> {
                            tags_expand = !tags_expand;
                            if (tags_expand) {
                                tagsText.setMaxLines(233);
                                tagsText.setText(tags_spannable);
                            } else {
                                tagsText.setMaxLines(1);
                                tagsText.setText(tags_spannable.toString());
                            }
                        });
                    });
                } catch (Exception e) {
                    MsgUtil.err(e);
                }
            });
        } else tagsText.setVisibility(View.GONE);

        //点赞投币收藏
        if (videoInfo.stats.coined != 0)
            coin.setImageResource(R.drawable.icon_coin_1);
        if (videoInfo.stats.liked)
            like.setImageResource(R.drawable.icon_like_1);
        if (videoInfo.stats.favoured)
            fav.setImageResource(R.drawable.icon_fav_1);

        playerData = videoInfo.toPlayerData(0);
        playerData.favoritePlaybackToken = requireActivity().getIntent().getStringExtra(
                com.RobinNotBad.BiliClient.api.FavoritePlaybackSource.TOKEN);

        // 1.1.6: 详情页只负责展示内容；播放地址在用户真正点击播放时再请求，
        // 避免 Android 4.x 打开详情页时同时拉播放流和评论/相关内容。

        //封面
        cover.requestFocus();
        cover.setOnClickListener(view1 -> {
            if (SharedPreferencesUtil.getBoolean("cover_play_enable", true)) playClick();
            else showCover();
        });
        cover.setOnLongClickListener(v -> {
            showCover();
            return true;
        });

        //标题
        title.setText(getTitleSpan());
        StringUtil.setCopy(title, videoInfo.title);

        //争议信息
        if (!TextUtils.isEmpty(videoInfo.argueMsg)) {
            exclusiveTipLabel.setText(videoInfo.argueMsg);
            exclusiveTip.setVisibility(View.VISIBLE);
        }

        //UP主列表
        UpListAdapter adapter = new UpListAdapter(requireContext(), videoInfo.staff);
        up_recyclerView.setHasFixedSize(true);
        up_recyclerView.setLayoutManager(new CustomLinearManager(getContext()));
        up_recyclerView.setAdapter(adapter);

        viewCount.setText(StringUtil.toWan(videoInfo.stats.view));
        likeLabel.setText(StringUtil.toWan(videoInfo.stats.like));
        coinLabel.setText(StringUtil.toWan(videoInfo.stats.coin));
        favLabel.setText(StringUtil.toWan(videoInfo.stats.favorite));

        danmakuCount.setText(String.valueOf(videoInfo.stats.danmaku));
        bvidText.setText(videoInfo.bvid);
        timeText.setText(videoInfo.timeDesc);
        durationText.setText(videoInfo.duration);

        description.setText(videoInfo.description);
        description.setOnClickListener(view1 -> {
            if (desc_expand) description.setMaxLines(3);
            else description.setMaxLines(512);
            desc_expand = !desc_expand;
        });
        StringUtil.setLink(description);
        StringUtil.setAtLink(videoInfo.descAts, description);
        StringUtil.setCopy(description);

        bvidText.setOnLongClickListener(v -> {
            Context context = getContext();
            if (context == null) {
                return true;
            }
            StringUtil.copyText(context, videoInfo.bvid);
            MsgUtil.showMsg("BV号已复制");
            return true;
        });

        //播放
        play.setOnClickListener(view1 -> playClick());
        play.setOnLongClickListener(view1 -> {
            Context context = getContext();
            if (context != null)
                startActivity(new Intent(context, SettingPlayerChooseActivity.class));
            return true;
        });

        //点赞
        rootview.findViewById(R.id.layout_like).setOnClickListener(view1 -> CenterThreadPool.run(() -> {
            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) {
                MsgUtil.showMsg("还没有登录喵~");
                return;
            }
            try {
                int result = LikeCoinFavApi.like(videoInfo.aid, (videoInfo.stats.liked ? 2 : 1));
                if (result == 0) {
                    videoInfo.stats.liked = !videoInfo.stats.liked;
                    runOnUiThread(() -> {
                        MsgUtil.showMsg(videoInfo.stats.liked ? "点赞成功" : "取消成功");

                        if (videoInfo.stats.liked)
                            likeLabel.setText(StringUtil.toWan(++videoInfo.stats.like));
                        else likeLabel.setText(StringUtil.toWan(--videoInfo.stats.like));
                        like.setImageResource(videoInfo.stats.liked ? R.drawable.icon_like_1 : R.drawable.icon_like_0);
                    });
                } else if (isAdded()) {
                    String msg = "操作失败：" + result;
                    switch (result) {
                        case -403:
                            msg = "当前请求触发B站风控";
                            break;
                        case 65006:
                            msg = "已经点赞过了喵~";
                            videoInfo.stats.liked = true;
                            runOnUiThread(() -> like.setImageResource(R.drawable.icon_like_1));
                            break;
                    }
                    String finalMsg = msg;
                    MsgUtil.showMsg(finalMsg);
                }
            } catch (Exception e) {
                MsgUtil.err(e);
            }
        }));

        //投币
        rootview.findViewById(R.id.layout_coin).setOnClickListener(view1 -> CenterThreadPool.run(() -> {
            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) {
                MsgUtil.showMsg("还没有登录喵~");
                return;
            }
            if (videoInfo.stats.coined < videoInfo.stats.coin_limit) {
                try {
                    int result = LikeCoinFavApi.coin(videoInfo.aid, 1);
                    if (result == 0) {
                        if (++coinAdd <= 2) videoInfo.stats.coined++;
                        runOnUiThread(() -> {
                            MsgUtil.showMsg("投币成功");
                            coinLabel.setText(StringUtil.toWan(++videoInfo.stats.coin));
                            coin.setImageResource(R.drawable.icon_coin_1);
                        });
                    } else if (isAdded()) {
                        String msg = "投币失败：" + result;
                        if (result == -403) {
                            msg = "当前请求触发B站风控";
                        } else if (result == 34002) {
                            msg = "不能给自己投币哦";
                        }
                        String finalMsg = msg;
                        MsgUtil.showMsg(finalMsg);
                    }
                } catch (Exception e) {
                    MsgUtil.err(e);
                }
            } else {
                MsgUtil.showMsg("投币数量到达上限");
            }
        }));

        //收藏
        rootview.findViewById(R.id.layout_fav).setOnClickListener(view1 -> {
            if (!isAdded() || getView() != rootview
                    || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
            Intent intent = new Intent();
            intent.setClass(requireContext(), AddFavoriteActivity.class);
            intent.putExtra("aid", videoInfo.aid);
            intent.putExtra("bvid", videoInfo.bvid);
            favLauncher.launch(intent);
        });

        //稍后再看
        addWatchlater.setOnClickListener(view1 -> CenterThreadPool.run(() -> {
            try {
                int result = WatchLaterApi.add(videoInfo.aid);
                if (result == 0) MsgUtil.showMsg("添加成功");
                else MsgUtil.showMsg("添加失败，错误码：" + result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
        addWatchlater.setOnLongClickListener(view1 -> {
            Intent intent = new Intent();
            intent.setClass(requireContext(), WatchLaterActivity.class);
            startActivity(intent);
            return true;
        });

        //下载
        download.setOnClickListener(view1 -> downloadClick());
        download.setOnLongClickListener(v -> {
            CenterThreadPool.run(() -> {
                try {
                    VideoStorageUtil.Node downPath = VideoStorageUtil.currentTaskDirectory(
                            requireContext(), videoInfo.title, null, false);
                    if (downPath != null) downPath.deleteRecursive();
                    MsgUtil.showMsg("已清除此视频的缓存文件夹");
                } catch (Exception e) {
                    MsgUtil.showMsg("缓存目录不可访问");
                }
            });
            return true;
        });

        //转发
        relay.setOnClickListener((view1) -> {
            if (!isAdded() || getView() != rootview
                    || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
            Intent intent = new Intent();
            intent.setClass(requireContext(), SendDynamicActivity.class);
            writeDynamicLauncher.launch(intent);
        });
        relay.setOnLongClickListener(v -> {
            StringUtil.copyText(requireContext(), "https://www.bilibili.com/" + videoInfo.bvid);
            MsgUtil.showMsg("视频完整链接已复制");
            return true;
        });

        share.setOnClickListener(v -> shareVideo(rootview));

        //未登录隐藏按钮
        if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) {
            addWatchlater.setVisibility(View.GONE);
            relay.setVisibility(View.GONE);
            share.setVisibility(View.GONE);
        }

        //合集按钮
        if (videoInfo.collection != null) {
            TextView collectionTitle = rootview.findViewById(R.id.collectionText);
            collectionTitle.setText(String.format("合集 · %s", videoInfo.collection.title));
            collectionCard.setOnClickListener((view1) ->
                    startActivity(new Intent(requireContext(), CollectionInfoActivity.class)
                            .putExtra("fromVideo", videoInfo.aid)));
        } else {
            collectionCard.setVisibility(View.GONE);
        }

        onFinishLoad();

        // 一键三连
        rootview.findViewById(R.id.layout_like).setOnLongClickListener(v -> {
            if (SharedPreferencesUtil.getBoolean("like_one_triple", true) &&
                    SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) != 0) {
                if (isTripleInProgress || tripleRequestInFlight) return true;

                if (shakeAnimation == null) {
                    shakeAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.shake);
                    shakeAnimation.setRepeatCount(Animation.INFINITE);
                }

                like.startAnimation(shakeAnimation);
                coin.startAnimation(shakeAnimation);
                fav.startAnimation(shakeAnimation);

                Runnable tripleAction = () -> {
                    isTripleInProgress = false;
                    tripleActionRunnable = null;
                    like.clearAnimation();
                    coin.clearAnimation();
                    fav.clearAnimation();
                    if (!isAdded() || getView() != rootview) return;
                    tripleRequestInFlight = true;
                    final long targetAid = aid;
                    CenterThreadPool.run(() -> {
                        int result;
                        Exception failure;
                        try {
                            result = LikeCoinFavApi.triple(targetAid);
                            failure = null;
                        } catch (Exception e) {
                            result = -1;
                            failure = e;
                        }
                        final int code = result;
                        final Exception error = failure;
                        tripleResultHandler.post(() -> {
                            tripleRequestInFlight = false;
                            if (!isAdded() || getView() != rootview) return;
                            if (error != null) MsgUtil.err("三连失败", error);
                            else if (code == 0) {
                                coin.setImageResource(R.drawable.icon_coin_1);
                                like.setImageResource(R.drawable.icon_like_1);
                                fav.setImageResource(R.drawable.icon_fav_1);
                                MsgUtil.showMsg("三连成功");
                            } else MsgUtil.showMsg("三连失败，错误码：" + code);
                        });
                    });
                };
                scheduleTripleAction(tripleAction);

                return true;
            }
            return false;
        });

        rootview.findViewById(R.id.layout_like).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                cancelTripleAction();
            }
            return false;
        });
    }

    private void shareVideo(View rootview) {
        if (shareRequestInFlight) return;
        long account = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0);
        String cookies = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        if (account <= 0 || com.RobinNotBad.BiliClient.util.NetWorkUtil.getInfoFromCookie("SESSDATA", cookies).isEmpty()) {
            MsgUtil.showMsg("请先登录后分享");
            return;
        }
        shareRequestInFlight = true;
        View shareButton = rootview.findViewById(R.id.share_video);
        shareButton.setEnabled(false);
        String shareText = videoInfo.title + "\nhttps://www.bilibili.com/video/" + videoInfo.bvid;
        long shareAid = videoInfo.aid;
        CenterThreadPool.run(() -> {
            ArrayList<ExperienceRecordApi.Record> experienceBefore = null;
            try {
                if (account != SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)
                        || !AccountManager.sameSession(cookies,
                        SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""))) {
                    throw new IllegalStateException("账号已切换");
                }
                try {
                    experienceBefore = ExperienceRecordApi.getRecords(account, cookies);
                } catch (Exception ignored) {
                    // Still allow sharing when the experience snapshot is unavailable.
                }
            } catch (Exception error) {
                if (error instanceof IllegalStateException && "账号已切换".equals(error.getMessage())) {
                    Activity activity = getActivity();
                    if (activity != null) activity.runOnUiThread(() -> {
                        shareRequestInFlight = false;
                        if (shareButton != null) shareButton.setEnabled(true);
                        if (isAdded() && getView() == rootview) MsgUtil.showMsg("账号已切换，请重新操作");
                    });
                    return;
                }
            }
            Activity activity = getActivity();
            if (activity == null) return;
            ArrayList<ExperienceRecordApi.Record> finalExperienceBefore = experienceBefore;
            activity.runOnUiThread(() -> {
                if (!isAdded() || getView() != rootview
                        || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
                pendingShareAccount = account;
                pendingShareCookies = cookies;
                pendingShareAid = shareAid;
                pendingShareExperienceBefore = finalExperienceBefore;
                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, shareText);
                try {
                    shareLauncher.launch(Intent.createChooser(send, "分享视频"));
                } catch (ActivityNotFoundException ignored) {
                    shareRequestInFlight = false;
                    if (shareButton != null) shareButton.setEnabled(true);
                    MsgUtil.showMsg("设备没有可用的分享应用");
                    return;
                }
                MsgUtil.showMsg("分享后返回确认结果");
            });
        });
    }

    private void onShareReturned(ActivityResult result) {
        if (!isAdded() || getActivity() == null || getView() == null
                || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            clearPendingShare();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("确认分享结果")
                .setMessage("你是否已在所选应用中完成发送？")
                .setNegativeButton("未完成", (d, which) -> {
                    clearPendingShare();
                    shareRequestInFlight = false;
                    setShareButtonEnabled(true);
                    MsgUtil.showMsg("分享未完成，未登记经验");
                })
                .setPositiveButton("已完成", (d, which) -> registerCompletedShare())
                .create();
        dialog.setCancelable(false);
        MsgUtil.prepareAlertDialog(dialog);
        dialog.show();
    }

    private void registerCompletedShare() {
        final long account = pendingShareAccount;
        final String cookies = pendingShareCookies;
        final long aid = pendingShareAid;
        final ArrayList<ExperienceRecordApi.Record> before = pendingShareExperienceBefore;
        clearPendingShare();
        if (aid <= 0 || !ExperienceRecordApi.isCurrent(account, cookies)) {
            shareRequestInFlight = false;
            setShareButtonEnabled(true);
            MsgUtil.showMsg("账号已切换，无法登记分享经验");
            return;
        }
        MsgUtil.showMsg("正在登记分享并核对经验…");
        CenterThreadPool.run(() -> {
            int shareCode = Integer.MIN_VALUE;
            try {
                shareCode = ShareApi.shareVideo(aid, cookies);
            } catch (Exception ignored) {
                // Sharing was confirmed; report registration independently.
            }
            boolean reward = false;
            if (shareCode == 0 && before != null && ExperienceRecordApi.isCurrent(account, cookies)) {
                try {
                    ArrayList<ExperienceRecordApi.Record> after = ExperienceRecordApi.getRecords(account, cookies);
                    reward = ExperienceRecordApi.hasNewShareReward(before, after);
                    if (!reward) {
                        Thread.sleep(800);
                        if (ExperienceRecordApi.isCurrent(account, cookies)) {
                            after = ExperienceRecordApi.getRecords(account, cookies);
                            reward = ExperienceRecordApi.hasNewShareReward(before, after);
                        }
                    }
                } catch (Exception ignored) {
                    // External sharing was confirmed; experience verification can be temporarily unavailable.
                }
            }
            final boolean hasReward = reward;
            final int finalShareCode = shareCode;
            Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                shareRequestInFlight = false;
                setShareButtonEnabled(true);
                if (finalShareCode == 0) {
                    MsgUtil.showMsg(hasReward ? "分享成功，经验 +5" : "分享成功，今日经验未确认或已领取");
                } else if (finalShareCode == 71000) {
                    MsgUtil.showMsg("分享成功，B站提示今日已登记");
                } else if (finalShareCode == -101 || finalShareCode == -111) {
                    MsgUtil.showMsg("分享已完成，但登录失效，经验登记失败");
                } else if (finalShareCode == Integer.MIN_VALUE) {
                    MsgUtil.showMsg("分享已完成，但B站登记失败，经验未确认");
                } else {
                    MsgUtil.showMsg("分享已完成，但B站登记失败（" + finalShareCode + "）");
                }
            });
        });
    }

    private void clearPendingShare() {
        pendingShareAccount = 0;
        pendingShareCookies = "";
        pendingShareAid = 0;
        pendingShareExperienceBefore = null;
    }

    private void setShareButtonEnabled(boolean enabled) {
        View currentView = getView();
        if (currentView == null) return;
        View shareButton = currentView.findViewById(R.id.share_video);
        if (shareButton != null) shareButton.setEnabled(enabled);
    }

    private void scheduleTripleAction(Runnable action) {
        isTripleInProgress = true;
        tripleActionRunnable = action;
        // A detached View can no longer remove tasks queued on its old Handler.
        // Keep scheduling and cancellation on this stable main-thread Handler.
        tripleResultHandler.postDelayed(action, 2000);
    }

    private void cancelTripleAction() {
        isTripleInProgress = false;
        if (tripleActionRunnable != null) tripleResultHandler.removeCallbacks(tripleActionRunnable);
        tripleActionRunnable = null;
        if (like != null) like.clearAnimation();
        if (coin != null) coin.clearAnimation();
        if (fav != null) fav.clearAnimation();
    }


    private SpannableString getTitleSpan() {
        String string = null;

        //优先级要对
        if (videoInfo.upowerExclusive) string = "充电专属";
        else if (videoInfo.isSteinGate) string = "互动视频";
        else if (videoInfo.is360) string = "全景视频";
        else if (videoInfo.isCooperation) string = "联合投稿";

        if (string == null) return new SpannableString(videoInfo.title);

        SpannableString titleStr = new SpannableString(" " + string + " " + videoInfo.title);
        RadiusBackgroundSpan badgeBG = new RadiusBackgroundSpan(0, (int) getResources().getDimension(R.dimen.card_round), Color.WHITE, Color.rgb(207, 75, 95));
        titleStr.setSpan(badgeBG, 0, string.length() + 2, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return titleStr;
    }

    private SpannableStringBuilder getDescSpan(String tags) {
        SpannableStringBuilder tag_str = new SpannableStringBuilder("标签：");
        for (String str : tags.split("/")) {
            int old_len = tag_str.length();
            tag_str.append(str).append("/");
            tag_str.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View arg0) {
                    Intent intent = new Intent(requireContext(), SearchActivity.class);
                    intent.putExtra("keyword", str);
                    requireContext().startActivity(intent);
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                    ds.setColor(Color.parseColor("#03a9f4"));
                }
            }, old_len, tag_str.length() - 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return tag_str;
    }

    private void playClick() {
        if (SharedPreferencesUtil.getBoolean("first_play", true)) {
            SharedPreferencesUtil.putBoolean("first_play", false);

            if (SharedPreferencesUtil.getBoolean("cover_play_enable", true))
                MsgUtil.showDialog("播放视频", getString(R.string.tutorial_cover_play_enabled));
            else
                MsgUtil.showDialog("播放视频", getString(R.string.tutorial_cover_play_disabled));

            return;
        }

        Glide.get(getAppContext()).clearMemory();
        //在播放前清除内存缓存，因为手表内存太小了，播放完回来经常把Activity全释放掉
        //...经过测试，还是会释放，但会好很多
        if (videoInfo.pagenames.size() == 1) PlayerApi.startGettingUrl(playerData);
        else
            startActivity(new Intent(requireContext(), MultiPageActivity.class).putExtra("data", playerData));

        playerData.timeStamp = 0;
    }

    private void downloadClick() {
        if (!FileUtil.checkStoragePermission()) {
            FileUtil.requestStoragePermission(requireActivity());
        } else {
            VideoStorageUtil.Node downPath = null;
            try {
                downPath = VideoStorageUtil.currentTaskDirectory(requireContext(), videoInfo.title, null, false);
            } catch (Exception ignored) {
            }

            if (downPath != null && downPath.exists() && videoInfo.pagenames.size() == 1) {
                VideoStorageUtil.Node fileSign = downPath.find(".DOWNLOADING");
                MsgUtil.showMsg(fileSign != null ? "已在下载队列\n如有异常，长按可清空文件" : "已下载完成");
            } else {
        if (videoInfo.pagenames.size() > 1) {
                    Intent intent = new Intent();
                    intent.setClass(requireContext(), MultiPageActivity.class)
                            .putExtra("download", 1)
                            .putExtra("data", playerData);
                    startActivity(intent);
                } else {
                    startActivity(new Intent(requireContext(), QualityChooserActivity.class)
                            .putExtra("page", 0)
                            .putExtra("aid", videoInfo.aid)
                            .putExtra("bvid", videoInfo.bvid)
                    );
                }
            }
        }

    }

    private void showCover() {
        try {
            Intent intent = new Intent();
            intent.setClass(requireContext(), ImageViewerActivity.class);
            ArrayList<String> imageList = new ArrayList<>();
            imageList.add(videoInfo.cover);
            intent.putExtra("imageList", imageList);
            requireContext().startActivity(intent);
        } catch (Exception ignored) {
        }
    }


    public void onFinishLoad() {
        try {
            Activity activity = getActivity();
            if (activity instanceof VideoInfoActivity) {
                ((VideoInfoActivity) activity).crossFade(getView());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        if(reportController!=null){reportController.close();reportController=null;}
        cancelTripleAction();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Logu.d("onDestroy");
        cancelTripleAction();
        super.onDestroy();

    }
}
