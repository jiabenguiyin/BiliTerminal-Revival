package com.RobinNotBad.BiliClient.activity.player;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.model.DanmakuElem;
import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.*;
import master.flame.danmaku.danmaku.model.android.*;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.android.*;
import master.flame.danmaku.danmaku.util.SelfDanmakuStyle;
import master.flame.danmaku.ui.widget.DanmakuView;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SelfDanmakuInstrumentedTest {
    private final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    @Test public void protobufAndLegacyXmlHighlightOnlyTheCurrentAccountsHash()throws Exception {
        DanmakuContext context=DanmakuContext.create();context.getDisplayer().setSize(240,240);context.getDisplayer().setDensities(2,320,2);
        DmSegMobileReply segment=new DmSegMobileReply();segment.elems=new ArrayList<>();
        for(String hash:new String[]{SelfDanmakuStyle.senderHash(86137069),"ffffffff",null}) {
            DanmakuElem e=new DanmakuElem();e.progress=1000;e.mode=1;e.fontsize=25;e.color=0xffffff;e.content="same text";e.midHash=hash;segment.elems.add(e);
        }
        BiliProtobufDanmakuParser proto=new BiliProtobufDanmakuParser();proto.setDanmakuSegments(Collections.singletonList(segment));
        proto.setOwnUserHash(SelfDanmakuStyle.senderHash(86137069));prepare(proto,context);
        assertIdentities(proto.parse());
        ILoader loader=DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);
        loader.load(new ByteArrayInputStream("<i><d p=\"1,1,25,16777215,1,0,4e902d62,1\">same text</d><d p=\"1,1,25,16777215,1,0,ffffffff,2\">same text</d><d p=\"1,1,25,16777215,1,0\">same text</d></i>".getBytes("UTF-8")));
        BiliDanmukuParser xml=new BiliDanmukuParser();xml.setOwnUserHash(SelfDanmakuStyle.senderHash(86137069));xml.load(loader.getDataSource());prepare(xml,context);
        assertIdentities(xml.getDanmakus());xml.release();
        proto.setOwnUserHash("");assertEquals(0,proto.parse().first().borderColor);
    }

    @Test public void ownBorderIsVisibleWithTheRealRendererAndDrawingCacheOnOrOff()throws Exception {
        TestActivity host=(TestActivity)ui.startActivitySync(new Intent(ui.getTargetContext(),TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<DanmakuView> view=new AtomicReference<>();
        try {
            for(boolean cache:new boolean[]{false,true}) {
                CountDownLatch ready=new CountDownLatch(1),shown=new CountDownLatch(2);
                AtomicReference<BaseDanmaku> own=new AtomicReference<>(),other=new AtomicReference<>();
                ui.runOnMainSync(()->{
                    if(view.get()!=null)view.get().release();DanmakuView v=new DanmakuView(host);v.setBackgroundColor(Color.BLACK);host.setContentView(v);view.set(v);
                    DanmakuContext context=DanmakuContext.create().setDanmakuTransparency(1).setScaleTextSize(1);
                    BaseDanmakuParser parser=new BaseDanmakuParser(){@Override protected Danmakus parse(){
                        Danmakus items=new Danmakus();
                        for(int i=0;i<2;i++) {
                            BaseDanmaku d=mContext.mDanmakuFactory.createDanmaku(i==0?BaseDanmaku.TYPE_FIX_TOP:BaseDanmaku.TYPE_FIX_BOTTOM,mContext);
                            d.time=100;d.text="Same text";d.textSize=18;d.textColor=Color.WHITE;d.textShadowColor=Color.BLACK;d.setTimer(mTimer);
                            if(i==0){SelfDanmakuStyle.apply(d);own.set(d);}else other.set(d);items.addItem(d);
                        }return items;}};
                    v.setCallback(new DrawHandler.Callback(){public void prepared(){ready.countDown();}public void updateTimer(DanmakuTimer t){}public void drawingFinished(){}public void danmakuShown(BaseDanmaku d){shown.countDown();}});
                    v.enableDanmakuDrawingCache(cache);v.prepare(parser,context);
                });
                assertTrue(ready.await(12,TimeUnit.SECONDS));ui.runOnMainSync(()->view.get().start(0));assertTrue(shown.await(8,TimeUnit.SECONDS));SystemClock.sleep(120);
                ui.runOnMainSync(()->{
                    DanmakuView v=view.get();Bitmap image=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));
                    try {
                        assertEquals(SelfDanmakuStyle.BORDER_COLOR,own.get().borderColor);assertEquals(0,other.get().borderColor);
                        assertTrue("No visible pink frame",pinkPixels(image,own.get())>15);
                        assertEquals("Normal danmaku must not reuse the own-border cache",0,pinkPixels(image,other.get()));
                        try(FileOutputStream out=new FileOutputStream(new File(host.getFilesDir(),"self-danmaku-"+cache+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
                        catch(IOException e){throw new AssertionError(e);}
                    }finally{image.recycle();}
                });
            }
        }finally{ui.runOnMainSync(()->{if(view.get()!=null)view.get().release();host.finish();});ui.waitForIdleSync();}
    }

    @Test public void liveFramesUseUidEvenWhenNicknamesMatchAndSenderNamesAreHidden()throws Exception {
        Object oldMid=SharedPreferencesUtil.getSharedPreferences().getAll().get("mid"),oldNames=SharedPreferencesUtil.getSharedPreferences().getAll().get("player_danmaku_showsender");
        TestActivity host=(TestActivity)ui.startActivitySync(new Intent(ui.getTargetContext(),TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicReference<PlayerActivity> controller=new AtomicReference<>();ui.runOnMainSync(()->controller.set(new PlayerActivity()));
        CapturingView view=new CapturingView(host);PlayerActivity player=controller.get();
        try {
            SharedPreferencesUtil.putLong("mid",42);SharedPreferencesUtil.putBoolean("player_danmaku_showsender",false);
            ui.runOnMainSync(()->{
                DanmakuContext context=DanmakuContext.create();context.getDisplayer().setSize(240,240);context.getDisplayer().setDensities(2,320,2);
                context.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL,context);
                set(player,"mContext",context);set(player,"mDanmakuView",view);set(player,"isLiveMode",true);set(player,"isDanmakuVisible",true);
                set(player,"ijkPlayer",Proxy.newProxyInstance(IMediaPlayer.class.getClassLoader(),new Class[]{IMediaPlayer.class},(p,m,a)->m.getReturnType()==boolean.class?false:m.getReturnType()==long.class?0L:m.getReturnType()==int.class?0:null));
                PlayerDanmuClientListener listener=new PlayerDanmuClientListener();listener.playerActivity=player;
                try {
                    Method handle=PlayerDanmuClientListener.class.getDeclaredMethod("handleJsonObject",String.class);handle.setAccessible(true);
                    for(long mid:new long[]{42,77,0}) {
                        JSONArray info=new JSONArray().put(new JSONArray()).put("same content").put(new JSONArray().put(mid).put("Same nickname"));
                        handle.invoke(listener,new JSONObject().put("cmd","DANMU_MSG:4:0:2:2:2:0").put("info",info).toString());
                    }
                    assertEquals(3,view.items.size());assertEquals(SelfDanmakuStyle.BORDER_COLOR,view.items.get(0).borderColor);
                    assertEquals("same content",view.items.get(0).text);assertEquals(0,view.items.get(1).borderColor);assertEquals(0,view.items.get(2).borderColor);
                }catch(Exception e){throw new AssertionError(e);}
            });
        }finally{
            ui.runOnMainSync(()->{view.release();host.finish();});restore("mid",oldMid);restore("player_danmaku_showsender",oldNames);
        }
    }

    static class CapturingView extends DanmakuView {
        final List<BaseDanmaku> items=new ArrayList<>();CapturingView(android.content.Context c){super(c);}
        @Override public void addDanmaku(BaseDanmaku item){items.add(item);}
    }

    @Test public void videoSendHighlightsOnlyConfirmedSuccessOnTheSameVideo()throws Exception {
        Field clientField=NetWorkUtil.class.getDeclaredField("INSTANCE");clientField.setAccessible(true);
        AtomicReference<okhttp3.OkHttpClient> clients=(AtomicReference<okhttp3.OkHttpClient>)clientField.get(null);okhttp3.OkHttpClient original=clients.get();
        AtomicInteger code=new AtomicInteger(),posts=new AtomicInteger();AtomicReference<CountDownLatch> gate=new AtomicReference<>();
        clients.set(new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{
            if(chain.request().url().encodedPath().endsWith("/dm/post")) {
                posts.incrementAndGet();CountDownLatch held=gate.get();if(held!=null)try{held.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
            }
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),"{\"code\":"+code.get()+",\"data\":{}}" )).build();
        }).build());
        File media=new File(ui.getTargetContext().getCacheDir(),"self-danmaku-send.mp4");
        try(InputStream in=ui.getContext().getAssets().open("player-gesture.mp4");OutputStream out=new FileOutputStream(media)) {
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }
        PlayerActivity player=null;Object originalView=null,originalContext=null;CapturingView capture=null;
        try {
            player=(PlayerActivity)ui.startActivitySync(new Intent(ui.getTargetContext(),PlayerActivity.class).putExtra("url",media.getAbsolutePath())
                    .putExtra("title","Self danmaku test").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            PlayerActivity p=player;await(()->Boolean.TRUE.equals(get(p,"isPrepared")));
            originalView=get(p,"mDanmakuView");originalContext=get(p,"mContext");capture=new CapturingView(p);CapturingView v=capture;
            ui.runOnMainSync(()->{
                DanmakuContext context=DanmakuContext.create();context.getDisplayer().setDensities(2,320,2);
                context.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL,context);
                set(p,"mContext",context);set(p,"mDanmakuView",v);set(p,"isDanmakuVisible",true);set(p,"aid",111L);set(p,"cid",222L);
            });
            android.widget.EditText edit=p.findViewById(R.id.danmaku_send_edit);android.view.View send=p.findViewById(R.id.danmaku_send);
            ui.runOnMainSync(()->{edit.setText("sent by me");send.performClick();});await(()->send.isEnabled()&&posts.get()==1);
            ui.runOnMainSync(()->{assertEquals(1,v.items.size());assertEquals(SelfDanmakuStyle.BORDER_COLOR,v.items.get(0).borderColor);assertEquals("",edit.getText().toString());v.items.clear();});
            code.set(-111);ui.runOnMainSync(()->{edit.setText("failed draft");send.performClick();});await(()->send.isEnabled()&&posts.get()==2);
            ui.runOnMainSync(()->{assertTrue(v.items.isEmpty());assertEquals("failed draft",edit.getText().toString());});
            code.set(0);gate.set(new CountDownLatch(1));ui.runOnMainSync(()->{edit.setText("old video draft");send.performClick();});
            await(()->posts.get()==3);ui.runOnMainSync(()->set(p,"cid",777L));gate.get().countDown();await(send::isEnabled);
            ui.runOnMainSync(()->{assertTrue(v.items.isEmpty());assertEquals("old video draft",edit.getText().toString());});
        }finally {
            if(gate.get()!=null)gate.get().countDown();
            if(player!=null){PlayerActivity p=player;Object oldView=originalView,oldContext=originalContext;CapturingView v=capture;ui.runOnMainSync(()->{if(v!=null)v.release();if(oldView!=null)set(p,"mDanmakuView",oldView);set(p,"mContext",oldContext);p.finish();});ui.waitForIdleSync();}
            clients.set(original);media.delete();
        }
    }
    private static void prepare(BaseDanmakuParser p,DanmakuContext c){p.setConfig(c);p.setDisplayer(c.getDisplayer());p.setTimer(new DanmakuTimer());}
    private static void assertIdentities(IDanmakus items){assertEquals(3,items.size());IDanmakuIterator it=items.iterator();assertEquals(SelfDanmakuStyle.BORDER_COLOR,it.next().borderColor);while(it.hasNext())assertEquals(0,it.next().borderColor);}
    private static int pinkPixels(Bitmap image,BaseDanmaku d){
        int count=0;for(int y=Math.max(0,(int)d.getTop());y<Math.min(image.getHeight(),(int)Math.ceil(d.getBottom()));y++)
            for(int x=Math.max(0,(int)d.getLeft());x<Math.min(image.getWidth(),(int)Math.ceil(d.getRight()));x++) {
                int c=image.getPixel(x,y);if(Color.red(c)>180&&Color.green(c)<160&&Color.blue(c)>85&&Color.blue(c)<200)count++;
            }return count;
    }
    static void set(Object target,String name,Object value){try{Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new AssertionError(e);}}
    static Object get(Object target,String name){try{Field f=PlayerActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private interface Check{boolean get();}
    private void await(Check check){long end=SystemClock.uptimeMillis()+15000;boolean[] result={false};do{ui.runOnMainSync(()->result[0]=check.get());if(result[0])return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<end);fail("Player test did not settle");}
    static void restore(String key,Object value){android.content.SharedPreferences.Editor e=SharedPreferencesUtil.getSharedPreferences().edit();if(value==null)e.remove(key);else if(value instanceof Long)e.putLong(key,(Long)value);else if(value instanceof Boolean)e.putBoolean(key,(Boolean)value);e.commit();}
}
