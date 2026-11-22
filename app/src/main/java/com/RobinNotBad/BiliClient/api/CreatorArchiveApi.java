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
    private static final int ID_SEARCH_PAGES_PER_REQUEST = 4;
    private static final java.util.regex.Pattern ARCHIVE_ID =
            java.util.regex.Pattern.compile("(?i:BV)[A-Za-z0-9]{10}|(?i:av)[0-9]+");
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
        public final int number;

        private Page(ArrayList<Archive> items, boolean hasMore, long total, int number) {
            this.items = items;
            this.hasMore = hasMore;
            this.total = total;
            this.number = number;
        }
    }

    public static boolean isArchiveId(String keyword) {
        return ARCHIVE_ID.matcher(keyword).matches();
    }

    public static Page archives(Session session, int page, String status, String keyword)
            throws IOException, JSONException {
        verifyAccount(session);
        if (!ALL.equals(status) && !PUBLISHED.equals(status) && !UNPUBLISHED.equals(status))
            throw new IllegalArgumentException("Unknown archive filter");
        int firstPage = Math.max(1, page);
        if (!isArchiveId(keyword)) return archivePage(session, firstPage, status, keyword);

        // ID lookups stay within the same account and filter, with bounded work per tap.
        boolean byBvid = keyword.regionMatches(true, 0, "BV", 0, 2);
        String id = keyword.substring(2);
        if (!byBvid) id = id.replaceFirst("^0+(?!$)", "");
        ArrayList<Archive> matches = new ArrayList<>(1);
        Page source;
        int currentPage = firstPage;
        do {
            source = archivePage(session, currentPage, status, "");
            for (Archive archive : source.items) {
                boolean matchesId = byBvid
                        ? archive.bvid.equals("BV" + id) : Long.toString(archive.aid).equals(id);
                if (matchesId) {
                    matches.add(archive);
                    return new Page(matches, false, 1, currentPage);
                }
            }
            currentPage++;
        } while (source.hasMore && currentPage - firstPage < ID_SEARCH_PAGES_PER_REQUEST);
        return new Page(matches, source.hasMore, 0, source.number);
    }

    private static Page archivePage(Session session, int page, String status, String keyword)
            throws IOException, JSONException {
        HttpUrl.Builder url = HttpUrl.parse("https://member.bilibili.com/x/web/archives").newBuilder()
                .addQueryParameter("pn", String.valueOf(page)).addQueryParameter("ps", "20")
                .addQueryParameter("status", status).addQueryParameter("coop", "1").addQueryParameter("interactive", "1");
        if (!keyword.isEmpty()) url.addQueryParameter("keyword", keyword);
        return parsePage(read(session, url.build().toString()), session.mid, page);
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
        return new Page(items,!items.isEmpty()&&(long)page*size<total,total,page);
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
        JSONObject info=ownedData(session,item).getJSONObject("archive");
        return new OwnedArchive(session,item,info);
    }
    private static JSONObject ownedData(Session session,Archive item)throws IOException,JSONException {
        verifyAccount(session);
        if(item==null||item.aid<=0||item.accountMid!=session.mid||item.ownerMid>0&&item.ownerMid!=session.mid)
            throw new Failure(-403,"仅能管理当前账号本人发布的稿件");
        HttpUrl.Builder url=HttpUrl.parse("https://member.bilibili.com/x/vupre/web/archive/view").newBuilder();
        if(item.bvid.isEmpty())url.addQueryParameter("aid",String.valueOf(item.aid));else url.addQueryParameter("bvid",item.bvid);
        JSONObject result=data(read(session,url.build().toString()));
        JSONObject info=result.optJSONObject("archive");
        if(info==null||info.optLong("aid",0)!=item.aid)throw new Failure(-10002,"稿件信息已变化，请刷新后重新选择");
        long owner=info.optLong("mid",item.ownerMid);
        if(owner<=0&&item.state==0) {
            JSONObject view=data(read(session,"https://api.bilibili.com/x/web-interface/view?aid="+item.aid));
            JSONObject uploader=view.optJSONObject("owner");
            if(view.optLong("aid",0)==item.aid&&uploader!=null)owner=uploader.optLong("mid",0);
        }
        if(owner!=session.mid)throw new Failure(-403,"无法确认本人稿件归属，请前往官方管理核对");
        return result;
    }

    public static final class TitleEdit {
        public final long aid;
        public final String title,bvid;
        private final Session session;
        private final Archive archive;
        private final String payload;
        private final AtomicBoolean submitted=new AtomicBoolean();
        private TitleEdit(Session session,Archive archive,JSONObject payload) {
            this.session=session;this.archive=archive;aid=archive.aid;
            title=payload.optString("title");bvid=archive.bvid;this.payload=payload.toString();
        }
    }

    public static TitleEdit prepareTitleEdit(Session session,Archive item)throws IOException,JSONException {
        if(item==null||item.state!=0)throw new Failure(-10004,"暂仅支持修改已发布稿件的标题");
        return new TitleEdit(session,item,titlePayload(ownedData(session,item)));
    }

    static JSONObject titlePayload(JSONObject data)throws JSONException,Failure {
        JSONObject archive=data.optJSONObject("archive");
        JSONArray videos=data.optJSONArray("videos");
        if(archive==null||archive.optLong("aid",0)<=0||archive.optString("title","").trim().isEmpty()
                ||archive.optInt("state",Integer.MIN_VALUE)!=0||videos==null||videos.length()==0
                ||!archive.has("tid")||archive.optInt("tid",0)<=0
                ||(archive.optInt("copyright",0)!=1&&archive.optInt("copyright",0)!=2)
                ||!(archive.opt("tag") instanceof String)||archive.optString("tag").trim().isEmpty()
                ||!(archive.opt("cover") instanceof String)||!(archive.opt("desc") instanceof String))
            throw new Failure(-10004,"稿件编辑数据不完整，请使用官方管理修改标题");
        if(archive.optInt("interactive",0)!=0||archive.optInt("charging_pay",0)!=0)
            throw new Failure(-10004,"此类稿件请前往官方管理修改标题");
        if(archive.optInt("copyright")==2&&archive.optString("source","").trim().isEmpty())
            throw new Failure(-10004,"转载来源信息不完整，请使用官方管理");
        for(int i=0;i<videos.length();i++) {
            JSONObject part=videos.optJSONObject(i);
            if(part==null||part.optString("filename","").trim().isEmpty())
                throw new Failure(-10004,"分P信息不完整，请使用官方管理修改标题");
        }
        // Preserve all returned archive metadata and every part, including unknown fields.
        JSONObject payload=new JSONObject(archive.toString());
        payload.put("videos",new JSONArray(videos.toString()));
        return payload;
    }

    public static String validateTitle(String input)throws Failure {
        String title=input==null?"":input.trim();
        if(title.isEmpty()||title.codePointCount(0,title.length())>80)
            throw new Failure(-10005,"标题需为 1～80 个字符");
        for(int i=0;i<title.length();i++)if(Character.isISOControl(title.charAt(i)))
            throw new Failure(-10005,"标题不能包含换行或控制字符");
        return title;
    }

    /** True means read-back matched. False means accepted but still awaiting audit/propagation. */
    public static boolean editTitle(Session session,TitleEdit edit,String input)throws IOException,JSONException {
        session.requireCurrent();
        if(edit==null||edit.session!=session||!session.verified||session.csrf.isEmpty())
            throw new Failure(-101,"标题修改授权已失效，请重新选择稿件");
        String title=validateTitle(input);
        if(title.equals(edit.title))throw new Failure(-10005,"标题没有变化");
        String key=session.mid+":"+edit.aid;
        if(!PENDING.add(key))throw new Failure(-10003,"该稿件正在处理，请勿重复提交");
        try {
            JSONObject previous=new JSONObject(edit.payload);
            JSONObject fresh=titlePayload(ownedData(session,edit.archive));
            if(!sameJson(previous,fresh))throw new Failure(-10006,"稿件信息已变化，请重新打开编辑，避免覆盖其他修改");
            fresh.put("title",title);
            if(!edit.submitted.compareAndSet(false,true))throw new Failure(-10003,"此修改已提交，请刷新核对结果");
            session.requireCurrent();
            String url=HttpUrl.parse("https://member.bilibili.com/x/vu/web/edit").newBuilder()
                    .addQueryParameter("csrf",session.csrf).build().toString();
            try(Response response=NetWorkUtil.postJsonOnce(url,fresh.toString(),session.headers())) {
                if(!response.isSuccessful()||response.body()==null)throw new IOException("标题修改结果未确认");
                checkCode(new JSONObject(response.body().string()));
            }
            try {
                JSONObject confirmed=ownedData(session,edit.archive).getJSONObject("archive");
                return title.equals(confirmed.optString("title"));
            }catch(IOException|JSONException readBackFailure){return false;}
        }finally{PENDING.remove(key);}
    }

    static boolean sameJson(Object first,Object second)throws JSONException {
        if(first instanceof JSONObject&&second instanceof JSONObject) {
            JSONObject a=(JSONObject)first,b=(JSONObject)second;
            if(a.length()!=b.length())return false;
            Iterator<String> keys=a.keys();
            while(keys.hasNext()){String key=keys.next();if(!b.has(key)||!sameJson(a.get(key),b.get(key)))return false;}
            return true;
        }
        if(first instanceof JSONArray&&second instanceof JSONArray) {
            JSONArray a=(JSONArray)first,b=(JSONArray)second;
            if(a.length()!=b.length())return false;
            for(int i=0;i<a.length();i++)if(!sameJson(a.get(i),b.get(i)))return false;
            return true;
        }
        return first==second||first!=null&&first.equals(second);
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
