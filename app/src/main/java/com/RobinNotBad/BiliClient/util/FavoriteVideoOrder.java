package com.RobinNotBad.BiliClient.util;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

/** The active view order for each account and folder. */
public final class FavoriteVideoOrder {
    public static final String FOLDER_ORDER = "manual";
    private static final String[] CODES={"mtime","view","pubtime",FOLDER_ORDER};
    private static final String[] LABELS={"最近收藏","最多播放","最新投稿","收藏夹顺序"};
    private FavoriteVideoOrder() { }
    public interface Selection { void onSelected(String order); }
    public static String normalize(String order){for(String code:CODES)if(code.equals(order))return code;return CODES[0];}
    private static int index(String order){for(int i=0;i<CODES.length;i++)if(CODES[i].equals(order))return i;return 0;}
    public static String label(String order){return LABELS[index(order)];}
    private static String key(long account,long folder){return "favorite_video_order_"+account+"_"+folder;}
    public static String read(long account,long folder){return normalize(SharedPreferencesUtil.getString(key(account,folder),"mtime"));}
    public static void save(long account,long folder,String order){SharedPreferencesUtil.putString(key(account,folder),normalize(order));}
    public static AlertDialog dialog(Context context,String order,Selection selected,Runnable editOrder){
        AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle("收藏视频排序")
                .setSingleChoiceItems(LABELS,index(order),(dialog,which)->{dialog.dismiss();selected.onSelected(CODES[which]);})
                .setNegativeButton("取消",null);
        if (editOrder != null) builder.setPositiveButton("手动排序", (dialog, which) -> editOrder.run());
        return builder.create();
    }
}
