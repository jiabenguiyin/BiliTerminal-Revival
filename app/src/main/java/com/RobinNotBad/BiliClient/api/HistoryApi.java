package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.ApiResult;
import com.RobinNotBad.BiliClient.model.VideoCard;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.RobinNotBad.BiliClient.util.SearchQueryUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.*;
import okhttp3.FormBody;
import okhttp3.Response;
import okio.Buffer;

public class HistoryApi {

    /** Bound to the account and credentials the user saw when opening the confirmation. */
    public static final class DeleteSession {
        private final long account;
        private final String cookie,auth,csrf;
        private DeleteSession(long account,String cookie){
            this.account=account;this.cookie=cookie;
            auth=NetWorkUtil.getInfoFromCookie("SESSDATA",cookie);csrf=NetWorkUtil.getInfoFromCookie("bili_jct",cookie);
        }
        public static DeleteSession capture(long expectedAccount)throws IOException{
            String cookie=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");
            DeleteSession session=new DeleteSession(expectedAccount,cookie);
            if(expectedAccount<=0||!session.isCurrent()||session.auth.isEmpty()||session.csrf.isEmpty())
                throw new IOException("登录信息不完整或账号已变化，请重新登录后操作");
            String cookieMid=NetWorkUtil.getInfoFromCookie("DedeUserID",cookie);
            if(!cookieMid.isEmpty()&&!cookieMid.equals(String.valueOf(expectedAccount)))throw new IOException("登录账号信息不一致，请重新登录");
            return session;
        }
        public boolean isCurrent(){
            String current=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");
            return account==SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)
                    &&auth.equals(NetWorkUtil.getInfoFromCookie("SESSDATA",current))
                    &&csrf.equals(NetWorkUtil.getInfoFromCookie("bili_jct",current));
        }
        public ApiResult delete(List<Long> aids)throws IOException,JSONException{
            if(!isCurrent())throw new IOException("账号已变化，未提交删除");
            FormBody form=new FormBody.Builder().add("kid",historyDeleteKids(aids)).add("csrf",csrf).build();
            Buffer body=new Buffer();form.writeTo(body);
            List<String> headers=Arrays.asList("Cookie",cookie,"User-Agent",NetWorkUtil.USER_AGENT_WEB,
                    "Origin","https://www.bilibili.com","Referer","https://www.bilibili.com/account/history");
            try(Response response=NetWorkUtil.postOnce("https://api.bilibili.com/x/v2/history/delete",body.readUtf8(),headers)){
                if(!response.isSuccessful()||response.body()==null)throw new IOException("删除结果未确认，请刷新历史记录核对");
                JSONObject result=new JSONObject(response.body().string());
                if(result.optInt("code",Integer.MIN_VALUE)==Integer.MIN_VALUE)throw new JSONException("Missing history deletion result");
                return new ApiResult(result);
            }
        }
    }

    static String historyDeleteKids(Collection<Long> aids){
        if(aids==null||aids.isEmpty())throw new IllegalArgumentException("请选择历史记录");
        LinkedHashSet<Long> unique=new LinkedHashSet<>(aids);
        if(unique.size()>100)throw new IllegalArgumentException("每次最多删除100条记录");
        StringBuilder kids=new StringBuilder();
        for(Long aid:unique){if(aid==null||aid<=0)throw new IllegalArgumentException("历史记录标识无效");
            if(kids.length()>0)kids.append(',');kids.append("archive_").append(aid);}
        return kids.toString();
    }

    /**
     * 上传历史记录
     *
     * @param aid      视频aid
     * @param cid      分集cid
     * @param progress 观看进度，单位为s
     * @throws IOException
     */
    public static void reportHistory(long aid, long cid, long progress) throws IOException {
        String url = "https://api.bilibili.com/x/v2/history/report";
        String per = "aid=" + aid + "&cid=" + cid
                + "&progress=" + (progress >= 0 ? progress : "")
                + "&platform=pc"
                + "&csrf=" + SharedPreferencesUtil.getString(SharedPreferencesUtil.csrf, "");
        NetWorkUtil.post(url, per, NetWorkUtil.webHeaders);
    }

    /**
     * 获取视频历史记录
     *
     * @param lastResult 上一次获取返回的ApiResult，如果是第一次就传入新对象
     * @param videoList  已有的视频列表
     * @return 新的ApiResult，包含了返回码、文本信息以及翻页所需的offset
     * @throws IOException
     * @throws JSONException
     */
    public static ApiResult getHistory(ApiResult lastResult, List<VideoCard> videoList) throws IOException, JSONException {
        String url = "https://api.bilibili.com/x/web-interface/history/cursor?type=archive&view_at=" + lastResult.timestamp + "&business=" + lastResult.business + "&max=" + lastResult.offset;
        JSONObject result = NetWorkUtil.getJson(url);
        return parseHistoryPage(result, videoList);
    }

    static String buildHistorySearchUrl(String keyword, int page) {
        return "https://api.bilibili.com/x/web-interface/history/search" + new NetWorkUtil.FormData()
                .setUrlParam(true).put("keyword", keyword == null ? "" : keyword.trim())
                .put("pn", page).put("business", "all");
    }

    public static ApiResult searchHistory(String keyword, int page, List<VideoCard> output)
            throws IOException, JSONException {
        return parseHistoryPage(NetWorkUtil.getJson(buildHistorySearchUrl(keyword, page)), output);
    }

    static ApiResult parseHistoryPage(JSONObject result, List<VideoCard> videoList) throws JSONException {
        ApiResult apiResult = new ApiResult(result);
        if (apiResult.code != 0) return apiResult;
        JSONObject data = result.optJSONObject("data");
        if (data != null) {
            JSONArray list = data.optJSONArray("list");
            if (list == null) return apiResult.setBottom(true);
            for (int i = 0; i < list.length(); i++) {
                JSONObject videoCard = list.optJSONObject(i);
                if (videoCard == null) continue;
                String title = SearchQueryUtil.plainResultTitle(videoCard.optString("title"));
                String cover = videoCard.optString("cover");
                String upName = videoCard.optString("author_name");
                int progress = videoCard.optInt("progress");

                JSONObject history = videoCard.optJSONObject("history");
                if (history == null || !"archive".equals(history.optString("business", "archive"))) continue;
                long aid = history.optLong("oid");
                if (aid <= 0) continue;
                String bvid = history.optString("bvid");

                String viewStr;
                if (progress < 0) viewStr = "已看完";
                else if (progress == 0) viewStr = "还没看过";
                else viewStr = "看到" + StringUtil.toTime(progress);

                videoList.add(new VideoCard(title, upName, viewStr, cover, aid, bvid));
            }
            if (list.length() == 0) apiResult.isBottom = true;

            JSONObject cursor = data.optJSONObject("cursor");
            if (cursor != null) {
                apiResult.business = cursor.optString("business");
                apiResult.offset = cursor.optLong("max");
                apiResult.timestamp = cursor.optLong("view_at");
                if (cursor.optBoolean("is_end", false)) apiResult.isBottom = true;
            }
            if (data.has("has_more") && !data.optBoolean("has_more")) apiResult.isBottom = true;
        } else apiResult.isBottom = true;
        return apiResult;
    }

}
