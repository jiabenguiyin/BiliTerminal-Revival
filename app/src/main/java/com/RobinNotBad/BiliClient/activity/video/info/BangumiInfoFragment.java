package com.RobinNotBad.BiliClient.activity.video.info;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity;
import com.RobinNotBad.BiliClient.activity.settings.SettingPlayerChooseActivity;
import com.RobinNotBad.BiliClient.activity.video.JumpToPlayerActivity;
import com.RobinNotBad.BiliClient.adapter.video.MediaEpisodeAdapter;
import com.RobinNotBad.BiliClient.api.BangumiApi;
import com.RobinNotBad.BiliClient.model.Bangumi;
import com.RobinNotBad.BiliClient.ui.widget.recycler.CustomLinearManager;
import com.RobinNotBad.BiliClient.util.CenterThreadPool;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.BangumiProgressStore;
import com.RobinNotBad.BiliClient.model.PlayerData;
import com.RobinNotBad.BiliClient.player.PlayerOptionsMenu;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BangumiInfoFragment extends Fragment {
    private long mediaId;
    private int selectedSection = 0, selectedEpisode = 0;
    private Dialog dialog;
    private View rootView;
    private RecyclerView episodeRecyclerView;
    private Button section_choose;
    private TextView episode_choose;
    private Bangumi bangumi;
    private long restoredSeason, restoredEpisode;
    private volatile int seasonGeneration;
    private PlayerOptionsMenu seasonsMenu;
    private PlayerOptionsMenu downloadMenu;
    private boolean preparingDownload;

    public static BangumiInfoFragment newInstance(long mediaId) {
        Bundle args = new Bundle();
        args.putLong("media_id", mediaId);
        BangumiInfoFragment fragment = new BangumiInfoFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if (arguments != null) {
            mediaId = arguments.getLong("media_id");
        }
        rootView = inflater.inflate(R.layout.fragment_media_info, container, false);
        if(savedInstanceState!=null){restoredSeason=savedInstanceState.getLong("pgc_season");restoredEpisode=savedInstanceState.getLong("pgc_episode");}
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setVisibility(View.GONE);
        episodeRecyclerView = rootView.findViewById(R.id.rv_episode_list);
        //拉数据
        CenterThreadPool
                .supplyAsyncWithLiveData(() -> restoredSeason>0 ? BangumiApi.getSeason(restoredSeason) : BangumiApi.getBangumi(mediaId))
                .observe(getViewLifecycleOwner(), (result) -> result.onSuccess((bangumi) -> {
                    this.bangumi = bangumi;
                    initView();
                }).onFailure((error) -> MsgUtil.err("番剧详情：", error)));
    }

    @SuppressLint("SetTextI18n")
    private void initView() {
        //init data.
        ImageView imageMediaCover = rootView.findViewById(R.id.image_media_cover);
        Button playButton = rootView.findViewById(R.id.btn_play);
        TextView title = rootView.findViewById(R.id.text_title);
        TextView subtitle = rootView.findViewById(R.id.text_subtitle);
        TextView areaType = rootView.findViewById(R.id.text_area_type);
        TextView rating = rootView.findViewById(R.id.text_rating);
        TextView pubTime = rootView.findViewById(R.id.text_pub_time);
        TextView stats = rootView.findViewById(R.id.text_stats);
        TextView styles = rootView.findViewById(R.id.text_styles);
        View evaluateHeader = rootView.findViewById(R.id.layout_evaluate_header);
        ImageView evaluateArrow = rootView.findViewById(R.id.icon_evaluate_arrow);
        TextView evaluate = rootView.findViewById(R.id.text_evaluate);
        View staffHeader = rootView.findViewById(R.id.layout_staff_header);
        ImageView staffArrow = rootView.findViewById(R.id.icon_staff_arrow);
        TextView staff = rootView.findViewById(R.id.text_staff);
        TextView record = rootView.findViewById(R.id.text_record);
        section_choose = rootView.findViewById(R.id.section_choose);
        episode_choose = rootView.findViewById(R.id.episode_choose);
        int[] saved=BangumiProgressStore.selection(bangumi,restoredEpisode>0?restoredEpisode:BangumiProgressStore.episode(bangumi.info.season_id));
        selectedSection=saved[0];selectedEpisode=saved[1];restoredEpisode=0;
        Button seasonButton=rootView.findViewById(R.id.season_choose);
        seasonButton.setVisibility(bangumi.info.seasons!=null&&bangumi.info.seasons.size()>1?View.VISIBLE:View.GONE);
        seasonButton.setEnabled(true);
        seasonButton.setOnClickListener(v->showSeasonChoices());

        rootView.setVisibility(View.GONE);

        Glide.with(requireContext())
                .load(GlideUtil.url(bangumi.info.cover_horizontal))
                .transition(GlideUtil.getTransitionOptions())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(R.mipmap.placeholder)
                .into(imageMediaCover);
        imageMediaCover.setOnClickListener((view) -> startActivity(new Intent(view.getContext(), ImageViewerActivity.class).putExtra("imageList", new ArrayList<>(Collections.singletonList(bangumi.info.cover_horizontal)))));
        title.setText(bangumi.info.title);

        // 副标题
        if (bangumi.info.subtitle != null && !bangumi.info.subtitle.isEmpty()) {
            subtitle.setText(bangumi.info.subtitle);
            subtitle.setVisibility(View.VISIBLE);
        } else {
            subtitle.setVisibility(View.GONE);
        }

        // 地区和类型
        String areaTypeText = (bangumi.info.area_name != null ? bangumi.info.area_name : "") +
                             (bangumi.info.type_name != null ? " | " + bangumi.info.type_name : "");
        if (!areaTypeText.trim().isEmpty()) {
            areaType.setText(areaTypeText.trim());
            areaType.setVisibility(View.VISIBLE);
        } else {
            areaType.setVisibility(View.GONE);
        }

        // 评分
        if (bangumi.info.score > 0) {
            rating.setText(String.format("评分：%.1f (%d人)", bangumi.info.score, bangumi.info.count));
            rating.setVisibility(View.VISIBLE);
        } else {
            rating.setVisibility(View.GONE);
        }

        // 发布时间
        if (bangumi.info.publish != null && bangumi.info.publish.pub_time_show != null && !bangumi.info.publish.pub_time_show.isEmpty()) {
            String status = bangumi.info.publish.is_finish == 1 ? "已完结" : "连载中";
            pubTime.setText(bangumi.info.publish.pub_time_show + " " + status);
            pubTime.setVisibility(View.VISIBLE);
        } else {
            pubTime.setVisibility(View.GONE);
        }

        // 状态数
        if (bangumi.info.stat != null) {
            StringBuilder statBuilder = new StringBuilder();
            if (bangumi.info.stat.views > 0) {
                statBuilder.append("播放：").append(formatNumber(bangumi.info.stat.views));
            }
            if (bangumi.info.stat.favorites > 0) {
                if (statBuilder.length() > 0) statBuilder.append(" ");
                statBuilder.append("收藏：").append(formatNumber(bangumi.info.stat.favorites));
            }
            if (bangumi.info.stat.series_follow > 0) {
                if (statBuilder.length() > 0) statBuilder.append(" ");
                statBuilder.append("追番：").append(formatNumber(bangumi.info.stat.series_follow));
            }
            if (statBuilder.length() > 0) {
                stats.setText(statBuilder.toString());
                stats.setVisibility(View.VISIBLE);
            } else {
                stats.setVisibility(View.GONE);
            }
        } else {
            stats.setVisibility(View.GONE);
        }

        // 标签
        if (bangumi.info.styles != null && !bangumi.info.styles.isEmpty()) {
            String styleText = "标签：" + TextUtils.join(" ", bangumi.info.styles);
            styles.setText(styleText);
            styles.setVisibility(View.VISIBLE);
        } else {
            styles.setVisibility(View.GONE);
        }

        // 简介
        if (bangumi.info.evaluate != null && !bangumi.info.evaluate.trim().isEmpty()) {
            evaluate.setText(bangumi.info.evaluate.trim());
            evaluateHeader.setVisibility(View.VISIBLE);
            evaluate.setVisibility(View.GONE); // 默认折叠
            evaluateHeader.setOnClickListener(v -> {
                boolean isExpanded = evaluate.getVisibility() == View.VISIBLE;
                evaluate.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                evaluateArrow.animate().rotation(isExpanded ? 0 : 180).setDuration(200).start();
            });
        } else {
            evaluateHeader.setVisibility(View.GONE);
            evaluate.setVisibility(View.GONE);
        }

        // 制作人员
        if (bangumi.info.staff != null && !bangumi.info.staff.trim().isEmpty()) {
            staff.setText(bangumi.info.staff.trim());
            staffHeader.setVisibility(View.VISIBLE);
            staff.setVisibility(View.GONE); // 默认折叠
            staffHeader.setOnClickListener(v -> {
                boolean isExpanded = staff.getVisibility() == View.VISIBLE;
                staff.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                staffArrow.animate().rotation(isExpanded ? 0 : 180).setDuration(200).start();
            });
        } else {
            staffHeader.setVisibility(View.GONE);
            staff.setVisibility(View.GONE);
        }

        // 备案号
        if (bangumi.info.record != null && !bangumi.info.record.trim().isEmpty()) {
            record.setText("备案号：" + bangumi.info.record.trim());
            record.setVisibility(View.VISIBLE);
        } else {
            record.setVisibility(View.GONE);
        }
        //section selector setting.
        MediaEpisodeAdapter adapter = new MediaEpisodeAdapter();

        adapter.setOnItemClickListener(index -> {
            selectedEpisode = index;
            refreshReplies();
        });

        TextView indexShow = rootView.findViewById(R.id.indexShow);
        indexShow.setText(bangumi.info.indexShow);

        if (bangumi.sectionList.isEmpty()) {
            rootView.findViewById(R.id.bangumi_download).setVisibility(View.GONE);
            section_choose.setText("敬请期待");
            playButton.setVisibility(View.GONE);
            rootView.findViewById(R.id.episodes).setVisibility(View.GONE);    //未上线的番剧Activity activity = getActivity();
            Activity activity = requireActivity();
            if (activity instanceof VideoInfoActivity) {
                ((VideoInfoActivity) activity).replyFragment.setRefreshing(false);
            }
            onFinishLoad();
            return;
        }

        section_choose.setText(bangumi.sectionList.get(selectedSection).title + " 点击切换");
        section_choose.setOnClickListener(v -> getSectionChooseDialog().show());
        episode_choose.setOnClickListener(v -> getEposideChooseDialog().show());

        adapter.setData(bangumi.sectionList.get(selectedSection).episodeList);
        adapter.setSelectedItemIndex(selectedEpisode);
        episodeRecyclerView.setLayoutManager(new CustomLinearManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        episodeRecyclerView.setAdapter(adapter);
        episodeRecyclerView.scrollToPosition(selectedEpisode);

        //play button setting
        playButton.setOnClickListener(v -> {
            Bangumi.Episode episode = bangumi.sectionList.get(selectedSection).episodeList.get(selectedEpisode);
            Glide.get(requireContext()).clearMemory();
            Intent intent = new Intent(v.getContext(), JumpToPlayerActivity.class);
            PlayerData data=episode.toPlayerData();data.episodeId=episode.id;
            data.seasonId=bangumi.info.season_id;data.seasonType=bangumi.info.type;
            data.title=bangumi.info.title+" · "+episode.title+" "+episode.title_long;
            intent.putExtra("data", data);
            startActivity(intent);
        });
        playButton.setOnLongClickListener(v -> {
            Intent intent = new Intent(v.getContext(), SettingPlayerChooseActivity.class);
            startActivity(intent);
            return true;
        });
        View download=rootView.findViewById(R.id.bangumi_download);
        download.setVisibility(View.VISIBLE);
        download.setOnClickListener(v->prepareEpisodeDownload());
        onFinishLoad();

        refreshReplies();
    }

    private void prepareEpisodeDownload(){
        if(preparingDownload||!isAdded()||bangumi==null||bangumi.sectionList==null
                ||selectedSection<0||selectedSection>=bangumi.sectionList.size()
                ||selectedEpisode<0||selectedEpisode>=bangumi.sectionList.get(selectedSection).episodeList.size())return;
        final Bangumi.Episode episode=bangumi.sectionList.get(selectedSection).episodeList.get(selectedEpisode);
        final PlayerData data=episode.toPlayerData();data.episodeId=episode.id;
        data.seasonId=bangumi.info.season_id;data.seasonType=bangumi.info.type;data.qn=32;
        final String parent=bangumi.info.title;
        final String child=episode.title+" "+(episode.title_long==null?"":episode.title_long);
        final String cover=bangumi.info.cover;
        final int generation=seasonGeneration;
        final Activity owner=requireActivity();preparingDownload=true;
        MsgUtil.showMsg("正在检查当前剧集可缓存的画质…");
        CenterThreadPool.run(()->{
            try{
                com.RobinNotBad.BiliClient.api.BangumiDownloadApi.resolve(data);
                owner.runOnUiThread(()->{
                    preparingDownload=false;
                    if(!isAdded()||generation!=seasonGeneration||data.mid!=com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.getLong("mid",0))return;
                    if(downloadMenu!=null)downloadMenu.dismiss();
                    downloadMenu=new PlayerOptionsMenu(requireActivity(),rootView.findViewById(R.id.bangumi_download));
                    downloadMenu.getMenu().add(0,0,0,"缓存当前集").setEnabled(false);
                    for(int i=0;i<data.qnValueList.length;i++)
                        downloadMenu.getMenu().add(0,i+1,i+1,"缓存 "+data.qnStrList[i])
                                .setCheckable(true).setChecked(data.qnValueList[i]==data.qn);
                    downloadMenu.getMenu().add(0,data.qnValueList.length+1,data.qnValueList.length+1,android.R.string.cancel);
                    downloadMenu.setOnMenuItemClickListener(item->{
                                int index=item.getItemId()-1;
                                if(index<0||index>=data.qnValueList.length||!isAdded()||generation!=seasonGeneration)return true;
                                data.qn=data.qnValueList[index];
                                CenterThreadPool.run(()->{
                                    try{
                                        boolean added=com.RobinNotBad.BiliClient.service.DownloadService.queueBangumi(data,parent,child,cover);
                                        MsgUtil.showMsg(added?"已加入番剧缓存队列":"该集已在缓存队列中");
                                        com.RobinNotBad.BiliClient.service.DownloadService.start(-1);
                                    }catch(Exception error){MsgUtil.showMsg("加入缓存失败，请检查账号和存储设置");}
                                });
                                return true;
                            });
                    downloadMenu.show();
                });
            }catch(Exception error){owner.runOnUiThread(()->{
                preparingDownload=false;if(isAdded()&&generation==seasonGeneration)
                    MsgUtil.showMsgLong(error instanceof org.json.JSONException?error.getMessage():"获取番剧缓存信息失败，请检查网络后重试");
            });}
        });
    }

    @SuppressLint("SetTextI18n")
    private Dialog getSectionChooseDialog() {
        String[] choices = new String[bangumi.sectionList.size()];
        for (int i = 0; i < bangumi.sectionList.size(); i++) {
            choices[i] = bangumi.sectionList.get(i).title;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setSingleChoiceItems(choices, selectedSection, (dialog, which) -> {
            selectedSection = which;
            selectedEpisode = 0;

            refreshReplies();
            Bangumi.Section section = bangumi.sectionList.get(which);
            section_choose.setText(section.title + " 点击切换");
            MediaEpisodeAdapter adapter = (MediaEpisodeAdapter) episodeRecyclerView.getAdapter();
            if (adapter != null) {
                adapter.setData(bangumi.sectionList.get(which).episodeList);
                episodeRecyclerView.scrollToPosition(0);
            }
            episode_choose.setOnClickListener(v -> getEposideChooseDialog().show());
            dialog.dismiss();
        });
        dialog = builder.create();
        MsgUtil.prepareAlertDialog((AlertDialog) dialog);

        return dialog;
    }

    private Dialog getEposideChooseDialog() {
        ArrayList<Bangumi.Episode> episodeList = bangumi.sectionList.get(selectedSection).episodeList;

        String[] choices = new String[episodeList.size()];
        for (int i = 0; i < episodeList.size(); i++) {
            Bangumi.Episode episode = episodeList.get(i);
            choices[i] = episode.title + "." + episode.title_long;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setSingleChoiceItems(choices, selectedEpisode, (dialog, which) -> {
            selectedEpisode = which;
            refreshReplies();

            MediaEpisodeAdapter adapter = (MediaEpisodeAdapter) episodeRecyclerView.getAdapter();
            if (adapter != null) {
                adapter.setSelectedItemIndex(which);
                episodeRecyclerView.scrollToPosition(which);
            }
            dialog.dismiss();
        });
        dialog = builder.create();
        MsgUtil.prepareAlertDialog((AlertDialog) dialog);

        return dialog;
    }

    private void refreshReplies() {
        if(bangumi==null||bangumi.sectionList.isEmpty())return;
        Bangumi.Episode selected=bangumi.sectionList.get(selectedSection).episodeList.get(selectedEpisode);
        ((Button)rootView.findViewById(R.id.btn_play)).setText(
                BangumiProgressStore.position(bangumi.info.season_id,selected.id,selected.cid)>0
                        ?getString(R.string.pgc_continue,selected.title):"播放："+selected.title);
        episode_choose.setText("快速选择 · "+selected.title);
        Activity activity = getActivity();
        if (activity instanceof VideoInfoActivity) {
            ((VideoInfoActivity) activity).setCurrentAid(bangumi.sectionList.get(selectedSection).episodeList.get(selectedEpisode).aid);
        }
    }

    private void showSeasonChoices() {
        if(bangumi.info.seasons==null||getActivity()==null)return;
        if(seasonsMenu!=null)seasonsMenu.dismiss();
        final ArrayList<Bangumi.Season> seasons=new ArrayList<>(bangumi.info.seasons);
        seasonsMenu=new PlayerOptionsMenu(requireActivity(),rootView.findViewById(R.id.season_choose));
        for(int i=0;i<seasons.size();i++)seasonsMenu.getMenu().add(0,i+1,i,seasons.get(i).season_title)
                .setCheckable(true).setChecked(seasons.get(i).season_id==bangumi.info.season_id);
        seasonsMenu.setOnMenuItemClickListener(item->{loadSeason(seasons.get(item.getItemId()-1).season_id);return true;});
        seasonsMenu.show();
    }

    private void loadSeason(long seasonId) {
        if(bangumi.info.season_id==seasonId)return;
        final int generation=++seasonGeneration;
        rootView.findViewById(R.id.season_choose).setEnabled(false);MsgUtil.showMsg(getString(R.string.pgc_season_loading));
        CenterThreadPool.run(()->{
            try {
                Bangumi loaded=BangumiApi.getSeason(seasonId);
                CenterThreadPool.runOnUiThread(()->{
                    if(generation!=seasonGeneration||!isAdded()||getView()==null)return;
                    bangumi=loaded;restoredSeason=seasonId;initView();
                });
            }catch(Exception error){CenterThreadPool.runOnUiThread(()->{
                if(generation!=seasonGeneration||!isAdded()||getView()==null)return;
                rootView.findViewById(R.id.season_choose).setEnabled(true);MsgUtil.showMsg("切季失败，请重试");
            });}
        });
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        if(bangumi!=null){state.putLong("pgc_season",bangumi.info.season_id);
            if(!bangumi.sectionList.isEmpty())state.putLong("pgc_episode",bangumi.sectionList.get(selectedSection).episodeList.get(selectedEpisode).id);}
    }
    @Override public void onDestroyView() {
        seasonGeneration++;if(dialog!=null)dialog.dismiss();if(seasonsMenu!=null)seasonsMenu.dismiss();
        if(downloadMenu!=null)downloadMenu.dismiss();
        super.onDestroyView();rootView=null;
    }
    @Override public void onResume() {
        super.onResume();if(bangumi!=null&&rootView!=null)refreshReplies();
    }

    public void onFinishLoad() {
        if (rootView != null) rootView.setVisibility(View.VISIBLE);
        try {
            Activity activity = requireActivity();
            if (activity instanceof VideoInfoActivity) {
                ((VideoInfoActivity) activity).crossFade(getView());
            }
        } catch (Exception ignored) {
        }
    }

    private String formatNumber(int num) {
        if (num >= 100000000) { // 亿
            return String.format("%.1f亿", num / 100000000.0);
        } else if (num >= 10000) { // 万
            return String.format("%.1f万", num / 10000.0);
        } else {
            return String.valueOf(num);
        }
    }
}
