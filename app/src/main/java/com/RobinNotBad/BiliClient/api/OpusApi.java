package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.Opus;
import com.RobinNotBad.BiliClient.model.OpusParagraph;
import com.RobinNotBad.BiliClient.model.Stats;
import com.RobinNotBad.BiliClient.model.UserInfo;
import com.RobinNotBad.BiliClient.util.JsonUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.MsgUtil;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Response;
import okhttp3.ResponseBody;

public class OpusApi {

    public static Opus getOpus(long id) throws IOException, JSONException {
        Opus opus = new Opus();
        opus.type = Opus.TYPE_DYNAMIC;
        opus.id = id;

        String url;
        if (id > 100000000)
            url = "https://www.bilibili.com/opus/" + id; // 别问，问就是动态和专栏都被统一了，判断不了类型，只能判断id长度了。能用。
        else url = "https://www.bilibili.com/read/cv" + id;
        try {
            Response response = NetWorkUtil.get(url);
            if (id <= 100000000) {
                String location = response.headers().get("location");
                response.close();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("文章页面没有返回重定向地址");
                }
                response = NetWorkUtil.get(location); // /read/cv[id] 会重定向到 /opus/，这里手动跟随。
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                if (id > 100000000) {
                    opus.type = Opus.TYPE_DYNAMIC_OLD_STYLE;
                    return opus;
                }
                throw new IOException("文章详情响应为空");
            }

            String html = responseBody.string();

            String detailJson = JsonUtil.search(html, "detail", "");
            if (detailJson == null || detailJson.trim().isEmpty()) {
                // 页面可能被网络拦截、返回空壳 HTML，动态详情交给官方动态接口处理。
                if (id > 100000000) {
                    opus.type = Opus.TYPE_DYNAMIC_OLD_STYLE;
                    return opus;
                }
                throw new IOException("文章页面未找到详情数据");
            }

            JSONObject detail;
            try {
                detail = new JSONObject(detailJson);
            } catch (JSONException parseError) {
                if (id > 100000000) {
                    opus.type = Opus.TYPE_DYNAMIC_OLD_STYLE;
                    return opus;
                }
                throw parseError;
            }

            JSONObject basic = detail.getJSONObject("basic");
            String detailType = detail.optString("type");
            opus.commentId = parseCommentId(basic);
            opus.commentType = parseCommentType(basic, detailType);

            if (isDynamicDetail(basic, detailType, opus.commentType)) {
                opus.type = Opus.TYPE_DYNAMIC_OLD_STYLE;
                return opus;
            }

            if (detail.isNull("modules")) return opus;    //isNull其实涵盖了!has的情况，之前都是咋想的判断两次，我简直是sb
            JSONArray modules = detail.getJSONArray("modules");

            for (int i = 0; i < modules.length(); i++) {
                JSONObject module = modules.getJSONObject(i);
                switch (module.optString("module_type")) {
                    case "MODULE_TYPE_TITLE":
                        opus.title = module.getJSONObject("module_title").getString("text");
                        break;
                    case "MODULE_TYPE_TOP":
                        ArrayList<String> topImages = new ArrayList<>();
                        JSONObject module_top = module.getJSONObject("module_top");
                        JSONObject display = module_top.getJSONObject("display");
                        int displayType = display.optInt("type");
                        if (displayType == 1) {
                            JSONObject album = display.getJSONObject("album");
                            JSONArray pics = album.getJSONArray("pics");
                            for (int j = 0; j < pics.length(); j++) {
                                topImages.add(pics.getJSONObject(j).getString("url"));
                            }
                        }
                        opus.topImages = topImages;
                        Logu.d("yes");
                        break;
                    case "MODULE_TYPE_AUTHOR":
                        JSONObject module_author = module.getJSONObject("module_author");    //我感觉b站也是一个巨大的草台班子，用户信息格式都好几种，头像有avatar有face有head的，他们自己的程序员不累吗……
                        UserInfo author = new UserInfo();
                        author.mid = module_author.getLong("mid");
                        author.name = module_author.getString("name");
                        author.followed = module_author.optBoolean("following", false);
                        author.avatar = module_author.getString("face");
                        if (!module_author.isNull("vip"))
                            author.vip_nickname_color = module_author.getJSONObject("vip").optString("nickname_color", "");

                        opus.pubTime = module_author.getString("pub_time");
                        opus.upInfo = author;
                        break;
                    case "MODULE_TYPE_CONTENT":
                        JSONArray paragraphs = module.getJSONObject("module_content").getJSONArray("paragraphs");
                        opus.paragraphs = analyzeParagraphs(paragraphs);
                        break;
                    case "MODULE_TYPE_STAT":
                        opus.stats = Stats.fromOpus(module.optJSONObject("module_stat"));
                        break;
                }
            }

            if (opus.upInfo == null) opus.upInfo = new UserInfo();
            if (opus.stats == null) opus.stats = new Stats();
        } catch (IllegalArgumentException e) { // 取不出来的时候，会重定向，但重定向的域名是//开头的，会报错
            //这里给opus设置一个参数，让OpusInfoActivity跳转到旧版的DynamicInfoActivity，从而无需重写解析
            //判断方式很简单粗暴，看报错信息里有没有URL这个关键字，有就是跳转错误
            String errMsg = e.getMessage();
            if (errMsg != null && errMsg.contains("URL")) opus.type = Opus.TYPE_DYNAMIC_OLD_STYLE;
            else MsgUtil.err(e);
            return opus;

            /*
            url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?";
            url += "timezone_offset=-480&platform=web&gaia_source=main_web&id=" + id + "&features=itemOpusStyle,opusBigCover,onlyfansVote,endFooterHidden,decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,editable,opusPrivateVisible,avatarAutoTheme&web_location=333.1368&x-bili-device-req-json=%7B%22platform%22:%22web%22,%22device%22:%22pc%22%7D&x-bili-web-req-json=%7B%22spm_id%22:%22333.1368%22%7D";
            Response response = NetWorkUtil.get(ConfInfoApi.signWBI(url));
            ResponseBody responseBody = response.body();
            if(responseBody == null) return opus;

            JSONObject json = new JSONObject(responseBody.string());
            JSONObject item = json.getJSONObject("data").getJSONObject("item");

            analyzeOldStyleDynamic(opus, item);
             */
        }
        // B站是会做图文的

        opus.cover = "";
        return opus;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long parseCommentId(JSONObject basic) {
        String[] keys = {"comment_id_str", "rid_str", "comment_id", "rid"};
        for (String key : keys) {
            long value = parseLong(basic.optString(key, "0"));
            if (value > 0) return value;
        }
        return 0;
    }

    private static int parseCommentType(JSONObject basic, String dynamicType) {
        int type = basic.optInt("comment_type", 0);
        return type > 0
                ? type
                : ReplyApi.resolveDynamicCommentType(dynamicType, basic.optInt("rtype", 0));
    }

    private static boolean isDynamicDetail(JSONObject basic, String dynamicType, int commentType) {
        if (dynamicType != null && dynamicType.startsWith("DYNAMIC_TYPE")) return true;
        if (commentType == ReplyApi.REPLY_TYPE_DYNAMIC) return true;

        String pageTitle = basic.optString("title", "");
        return pageTitle.endsWith("的动态 - 哔哩哔哩")
                || pageTitle.endsWith("的动态-哔哩哔哩");
    }

    public static OpusParagraph[] analyzeParagraphs(JSONArray jsonArray) throws JSONException {
        ArrayList<OpusParagraph> paragraphs = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject paragraphJson = jsonArray.optJSONObject(i);
            if (paragraphJson == null) continue;
            try {
                paragraphs.add(new OpusParagraph(paragraphJson));
            } catch (JSONException | RuntimeException error) {
                Logu.e("opus", "skip malformed paragraph " + i + ": " + error);
            }
        }
        return paragraphs.toArray(new OpusParagraph[0]);
    }

    public static void analyzeOldStyleDynamic(Opus opus, JSONObject item) throws JSONException {
        JSONObject basic = item.getJSONObject("basic");
        String dynamicType = item.getString("type");
        opus.commentId = parseCommentId(basic);
        opus.commentType = parseCommentType(basic, dynamicType);

        if (item.isNull("modules")) return;
        JSONObject modules = item.getJSONObject("modules");

        //up主信息
        UserInfo author = new UserInfo();
        if (!modules.isNull("module_author")) {
            JSONObject module_author = modules.getJSONObject("module_author");
            author.mid = module_author.getLong("mid");
            author.name = module_author.getString("name");
            author.followed = module_author.optBoolean("following", false);
            author.avatar = module_author.getString("face");
            if (!module_author.isNull("vip"))
                author.vip_nickname_color = module_author.getJSONObject("vip").optString("nickname_color", "");
            opus.pubTime = module_author.getString("pub_time");
        }
        opus.upInfo = author;

        if (dynamicType.equals("DYNAMIC_TYPE_NONE")) {
            opus.content = "[动态不存在]";
            return;
        }

        //动态主体内容
        JSONObject module_dynamic = modules.getJSONObject("module_dynamic");

        ArrayList<OpusParagraph> paragraphList = new ArrayList<>();

        if (!module_dynamic.isNull("desc")) {
            JSONObject object = new JSONObject();
            object.put("para_type", OpusParagraph.TYPE_TEXT_OPUS);
            object.put("data", module_dynamic.getJSONObject("desc").getJSONArray("rich_text_nodes"));
            paragraphList.add(new OpusParagraph(object));
        }

        if (!module_dynamic.isNull("major")) {
            JSONObject major = module_dynamic.getJSONObject("major");

            if (!major.isNull("opus")) {
                JSONObject dynamic_opus = major.getJSONObject("opus");
                JSONArray opus_pics = dynamic_opus.getJSONArray("pics");

                // 为了排版正常，这里必须把列表完整传递给OpusParagraph，让OpusParagraph那边解析
                // 这么干主要是为了适配这神秘的代码结构，我研究OpusParagraph的使用方法就研究了半天
                // by Moye

                JSONObject object = new JSONObject();
                object.put("para_type", OpusParagraph.TYPE_TEXT_OPUS);
                object.put("data", dynamic_opus.getJSONObject("summary").getJSONArray("rich_text_nodes"));
                paragraphList.add(new OpusParagraph(object));

                object = new JSONObject();
                object.put("para_type", OpusParagraph.TYPE_PIC);
                object.put("pic", new JSONObject().put("pics", opus_pics));
                paragraphList.add(new OpusParagraph(object));
            }

            if (!major.isNull("archive")) {
                // 这里是视频卡片
            }

        }

        opus.paragraphs = paragraphList.toArray(new OpusParagraph[0]);

        JSONObject module_stat = modules.getJSONObject("module_stat");
        Stats stats = new Stats();
        stats.reply = module_stat.getJSONObject("comment").getInt("count");
        stats.like = module_stat.getJSONObject("like").getInt("count");

        opus.stats = stats;
    }
}
