package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.*;
import okio.Buffer;

/** Private creator requests use a captured, verified account; deletes never accept an arbitrary aid. */
public final class CreatorArchiveApi {
    public static final String MANAGER_URL = "https://member.bilibili.com/platform/upload-manager/article";
    public static final String ALL = "is_pubing,pubed,not_pubed";
    public static final String PUBLISHED = "pubed";
    public static final String UNPUBLISHED = "is_pubing,not_pubed";
    private static final Set<String> PENDING = Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());
    private CreatorArchiveApi() { }

    public static final class Failure extends IOException {
        public final int code;
        public Failure(int code,String message) { super(message);this.code=code; }
    }
    public static final class Session {
        public final long mid;
        private final String cookie,auth,csrf;
        private volatile boolean verified;
        private Session(long mid,String cookie) {
            this.mid=mid;this.cookie=cookie;
            auth=NetWorkUtil.getInfoFromCookie("SESSDATA",cookie);csrf=NetWorkUtil.getInfoFromCookie("bili_jct",cookie);
        }
        public boolean isCurrent() {
            String current=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");
            return mid==SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0)
                    && auth.equals(NetWorkUtil.getInfoFromCookie("SESSDATA",current))
                    && csrf.equals(NetWorkUtil.getInfoFromCookie("bili_jct",current));
        }
        private void requireCurrent()throws Failure {
            if(!isCurrent())throw new Failure(-10001,"账号已变化，请刷新后重新操作");
        }
        private ArrayList<String> headers() { return new ArrayList<>(Arrays.asList("Cookie",cookie,
                "User-Agent",NetWorkUtil.USER_AGENT_WEB,"Origin","https://member.bilibili.com","Referer",MANAGER_URL)); }
    }
    public static Session capture()throws Failure {
        long mid=SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid,0);
        String cookie=SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies,"");
        if(mid<=0||NetWorkUtil.getInfoFromCookie("SESSDATA",cookie).isEmpty())throw new Failure(-101,"请先登录后使用创作中心");
        String cookieMid=NetWorkUtil.getInfoFromCookie("DedeUserID",cookie);
        if(!cookieMid.isEmpty()&&!cookieMid.equals(String.valueOf(mid)))throw new Failure(-101,"登录账号信息不一致，请重新登录");
        return new Session(mid,cookie);
    }
    private static JSONObject read(Session session,String url)throws IOException,JSONException {
        session.requireCurrent();
        JSONObject result=NetWorkUtil.getJson(url,session.headers());
        session.requireCurrent();
        return result;
    }
    public static void verifyAccount(Session session)throws IOException,JSONException {
        session.requireCurrent();
        if(session.verified)return;
        JSONObject data=data(read(session,"https://api.bilibili.com/x/web-interface/nav"));
        if(data.optLong("mid",0)!=session.mid||!data.optBoolean("isLogin",true))
            throw new Failure(-101,"创作中心账号未验证，请重新登录");
        session.verified=true;
    }
    private static JSONObject data(JSONObject result)throws IOException {
        checkCode(result);
        JSONObject data=result.optJSONObject("data");
        if(data==null)throw new Failure(-10002,"创作中心返回数据不完整，请稍后重试");
        return data;
    }
    private static void checkCode(JSONObject result)throws Failure {
        if(!result.has("code"))throw new Failure(-10002,"创作中心返回数据不完整，请稍后重试");
        int code=result.optInt("code",Integer.MIN_VALUE);
        if(code==Integer.MIN_VALUE)throw new Failure(-10002,"创作中心返回数据不完整，请稍后重试");
        if(code==0)return;
        if(code==340022||code==-352||code==-412||code==22015)throw new Failure(code,"B 站要求安全验证，请前往官方稿件管理完成操作");
        if(code==-101||code==-111)throw new Failure(code,"登录信息失效，请重新登录后操作");
        if(code==-403)throw new Failure(code,"没有管理此稿件的权限");
        throw new Failure(code,"创作中心操作失败（"+code+"），请刷新后重试");
    }
    public static JSONObject statistics(Session session)throws IOException,JSONException {
        verifyAccount(session);
        return data(read(session,"https://member.bilibili.com/x/web/index/stat"));
    }
    public static String creatorAge(Session session)throws IOException,JSONException {
        verifyAccount(session);
        JSONArray rows=data(read(session,"https://member.bilibili.com/x/web/index/scrolls")).optJSONArray("scrolls");
        JSONObject row=rows==null?null:rows.optJSONObject(0);
        if(row==null)return "—";
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("\\d+").matcher(row.optString("name",""));
        return matcher.find()?matcher.group():"—";
    }

    public static final class Archive {
        public final long aid,accountMid,ownerMid,views,likes,replies,favorites;
        public final String bvid,title,cover,stateText;
        public final int state;
        private Archive(long accountMid,JSONObject a,JSONObject stat)throws JSONException {
            aid=a.getLong("aid");this.accountMid=accountMid;ownerMid=a.optLong("mid",0);
            bvid=a.optString("bvid","");title=a.optString("title","");
            String url=a.optString("cover","");
            if(url.startsWith("//"))url="https:"+url;
            else if(url.startsWith("/bfs/"))url="https://i0.hdslb.com"+url;
            cover=url.startsWith("https://")||url.startsWith("http://")?url:"";
            state=a.optInt("state",Integer.MIN_VALUE);
            String label=a.optString("state_desc","");
            stateText=!label.isEmpty()?label:state==0?"已发布":state==Integer.MIN_VALUE?"状态待确认":"未发布（"+state+"）";
            views=stat==null?-1:stat.optLong("view",stat.optLong("vv",-1));
            likes=stat==null?-1:stat.optLong("like",-1);replies=stat==null?-1:stat.optLong("reply",-1);
            favorites=stat==null?-1:stat.optLong("favorite",-1);
        }
    }
    public static final class Page {
        public final ArrayList<Archive> items;
        public final boolean hasMore;
        public final long total;
        private Page(ArrayList<Archive> items,boolean hasMore,long total){this.items=items;this.hasMore=hasMore;this.total=total;}
    }
    public static Page archives(Session session,int page,String status)throws IOException,JSONException {
        verifyAccount(session);
        if(!ALL.equals(status)&&!PUBLISHED.equals(status)&&!UNPUBLISHED.equals(status))throw new IllegalArgumentException("Unknown archive filter");
        HttpUrl url=HttpUrl.parse("https://member.bilibili.com/x/web/archives").newBuilder()
                .addQueryParameter("pn",String.valueOf(Math.max(1,page))).addQueryParameter("ps","20")
                .addQueryParameter("status",status).addQueryParameter("coop","1").addQueryParameter("interactive","1").build();
        return parsePage(read(session,url.toString()),session.mid,Math.max(1,page));
    }
    public static Page parsePage(JSONObject response,long mid,int requested)throws IOException,JSONException {
        JSONObject data=data(response),pagination=data.optJSONObject("page");
        if(pagination==null||!pagination.has("count"))throw new Failure(-10002,"稿件分页数据不完整，请重试");
        long total=pagination.optLong("count",-1);int size=pagination.optInt("ps",20),page=pagination.optInt("pn",requested);
        if(total<0||size<1||page!=requested)throw new Failure(-10002,"稿件分页数据异常，请刷新重试");
        JSONArray rows=data.optJSONArray("arc_audits");
        if(rows==null&&total!=0)throw new Failure(-10002,"稿件列表数据不完整，请重试");
        ArrayList<Archive> items=new ArrayList<>();Set<Long> seen=new HashSet<>();
        if(rows!=null)for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i),archive=row==null?null:row.optJSONObject("Archive");
            if(archive==null||archive.optLong("aid",0)<=0||archive.optString("title","").trim().isEmpty())
                throw new Failure(-10002,"稿件信息不完整，请刷新重试");
            Archive item=new Archive(mid,archive,row.optJSONObject("stat"));if(seen.add(item.aid))items.add(item);
        }
        return new Page(items,!items.isEmpty()&&(long)page*size<total,total);
    }
    public static final class OwnedArchive {
        public final long aid;
        public final String title,bvid;
        private final Session session;
        private final AtomicBoolean submitted=new AtomicBoolean();
        private OwnedArchive(Session session,Archive item,JSONObject info) {
            this.session=session;aid=item.aid;title=info.optString("title",item.title);bvid=info.optString("bvid",item.bvid);
        }
    }
    public static OwnedArchive prepareDelete(Session session,Archive item)throws IOException,JSONException {
        verifyAccount(session);
        if(item==null||item.aid<=0||item.accountMid!=session.mid||item.ownerMid>0&&item.ownerMid!=session.mid)
            throw new Failure(-403,"仅能删除当前账号本人发布的稿件");
        HttpUrl.Builder url=HttpUrl.parse("https://member.bilibili.com/x/vupre/web/archive/view").newBuilder();
        if(item.bvid.isEmpty())url.addQueryParameter("aid",String.valueOf(item.aid));else url.addQueryParameter("bvid",item.bvid);
        JSONObject info=data(read(session,url.build().toString())).optJSONObject("archive");
        if(info==null||info.optLong("aid",0)!=item.aid)throw new Failure(-10002,"稿件信息已变化，请刷新后重新选择");
        long owner=info.optLong("mid",item.ownerMid);
        if(owner<=0&&item.state==0) {
            JSONObject view=data(read(session,"https://api.bilibili.com/x/web-interface/view?aid="+item.aid));
            JSONObject uploader=view.optJSONObject("owner");
            if(view.optLong("aid",0)==item.aid&&uploader!=null)owner=uploader.optLong("mid",0);
        }
        if(owner!=session.mid)throw new Failure(-403,"无法确认本人稿件归属，请前往官方管理核对");
        return new OwnedArchive(session,item,info);
    }
    public static void delete(Session session,OwnedArchive item)throws IOException,JSONException {
        session.requireCurrent();
        if(item==null||item.session!=session||!session.verified||session.csrf.isEmpty())throw new Failure(-101,"删除授权已失效，请重新核对稿件");
        String key=session.mid+":"+item.aid;
        if(!PENDING.add(key))throw new Failure(-10003,"该稿件正在处理，请勿重复提交");
        try {
            if(!item.submitted.compareAndSet(false,true))throw new Failure(-10003,"这次删除已提交，请刷新列表核对结果");
            session.requireCurrent();
            FormBody form=new FormBody.Builder().add("aid",String.valueOf(item.aid)).add("csrf",session.csrf).build();
            Buffer body=new Buffer();form.writeTo(body);
            try(Response response=NetWorkUtil.postOnce("https://member.bilibili.com/x/web/archive/delete",body.readUtf8(),session.headers())) {
                if(!response.isSuccessful()||response.body()==null)throw new IOException("删除请求结果未确认");
                JSONObject result=new JSONObject(response.body().string());
                checkCode(result);
            }
        } finally {PENDING.remove(key);}
    }
    public static String message(Throwable error) {
        if(error instanceof Failure)return error.getMessage();
        if(error instanceof JSONException)return "创作中心返回数据异常，请稍后重试";
        return "网络连接失败，请稍后重试";
    }
}
