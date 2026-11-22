package com.RobinNotBad.BiliClient.activity.player;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayerRoundInsetsInstrumentedTest {
    @Rule public org.junit.rules.TestName testName=new org.junit.rules.TestName();
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Map<String,Object> old=new HashMap<>();private PlayerActivity player;private File media;
    @Before public void before()throws Exception{
        for(String key:new String[]{"player_from_last","player_loop","player_autolandscape","ui_landscape","player_ui_round",
                "player_subtitle_autoshow","player_audio_only","player_fine_volume",SharedPreferencesUtil.PLAYER_SHOW_COLLECTION_PLAYBACK})
            pref(key,key.equals("player_ui_round")&&testName.getMethodName().startsWith("roundSide"));
        prefInt(SharedPreferencesUtil.PLAYER_ROUND_SIDE_INSET_DP,4);
        pref(SharedPreferencesUtil.PLAYER_SCREEN_LOCK,true);
        media=new File(inst.getTargetContext().getCacheDir(),"round-insets-fixture.mp4");
        try(InputStream in=inst.getContext().getAssets().open("player-gesture.mp4");OutputStream out=new FileOutputStream(media)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);
        }
        player=(PlayerActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),PlayerActivity.class)
                .putExtra("url",media.getAbsolutePath()).putExtra("title","圆屏布局验证").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(()->player.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY));
        await(()->Boolean.TRUE.equals(get("isPrepared"))&&get("progress_str")!=null);
        main(()->{call("playerPause");call("showcon");});
        stable();
    }
    @After public void after(){
        if(player!=null){main(player::finish);inst.waitForIdleSync();}
        for(Map.Entry<String,Object> e:old.entrySet()){
            if(e.getValue()==null)SharedPreferencesUtil.removeValue(e.getKey());
            else if(e.getValue() instanceof Boolean)SharedPreferencesUtil.putBoolean(e.getKey(),(Boolean)e.getValue());
            else if(e.getValue() instanceof Integer)SharedPreferencesUtil.putInt(e.getKey(),(Integer)e.getValue());
        }
        if(media!=null)media.delete();
    }
    /** Baseline proof: this is exactly the 09 mapping from a half-screen radius to margins. */
    @Test public void reproduceCircularRadiusUsedAsMarginsCollapsesControls()throws Exception{
        int width=player.findViewById(R.id.control_layout).getWidth();int radius=width/2;
        main(()->call("applyPlayerSafeArea",new Class[]{int.class,int.class,int.class,int.class},new Object[]{radius,0,radius,0}));
        stable();
        assertTrue(player.findViewById(R.id.bottom_control).getWidth()<=2);
        assertTrue(player.findViewById(R.id.top).getWidth()<=2);
        save("round-insets-before.png");
    }
    @Test public void circularRadiusKeepsHeaderToolbarAndButtonsInTheirOwnPlaces()throws Exception{
        int width=player.findViewById(R.id.control_layout).getWidth();apply(0,0,0,0,width/2,width/2,true);stable();
        assertTrue(player.findViewById(R.id.bottom_control).getWidth()>width*3/4);
        assertTrue(player.findViewById(R.id.top).getWidth()>width*3/4);
        View toolbar=player.findViewById(R.id.right_control);assertTrue(toolbar.getX()>width*2/3);
        View pause=player.findViewById(R.id.button_video);Rect r=new Rect();assertTrue(pause.getGlobalVisibleRect(r));
        assertEquals(pause.getWidth(),r.width());assertEquals(pause.getHeight(),r.height());
        save("round-insets-after.png");
        tap(pause);assertTrue((Boolean)get("isPlaying"));tap(pause);assertFalse((Boolean)get("isPlaying"));
        android.media.AudioManager audio=(android.media.AudioManager)player.getSystemService(android.content.Context.AUDIO_SERVICE);
        int oldVolume=audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        int middle=audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)/2;
        try{audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,middle,0);
            tap(player.findViewById(R.id.button_sound_add));assertTrue(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)>middle);
        }finally{audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,oldVolume,0);}
    }
    @Test public void missingRoundFlagWithHalfScreenRadiusIsAlsoSafe()throws Exception{
        int width=player.findViewById(R.id.control_layout).getWidth();apply(0,0,0,0,width/2,width/2,false);stable();
        assertTrue((Integer)get("playerSafeLeft")<width/10);assertTrue((Integer)get("playerSafeRight")<width/10);
        assertTrue(player.findViewById(R.id.bottom_control).getWidth()>width*3/4);
    }
    @Test public void realSystemInsetsAreKeptOnceWithoutPaddingTheRowAgain()throws Exception{
        View row=player.findViewById(R.id.bottom_buttons);int left=row.getPaddingLeft(),right=row.getPaddingRight(),bottom=row.getPaddingBottom();
        int width=player.findViewById(R.id.control_layout).getWidth();apply(40,0,32,30,width/2,width/2,true);stable();
        ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)player.findViewById(R.id.bottom_control).getLayoutParams();
        assertEquals(40,p.leftMargin);assertEquals(32,p.rightMargin);assertEquals(30,p.bottomMargin);
        assertEquals(left,row.getPaddingLeft());assertEquals(right,row.getPaddingRight());assertEquals(bottom,row.getPaddingBottom());
    }
    @Test public void normalPhoneCornerDoesNotOverrideALargerGenuineCutout()throws Exception{
        apply(40,0,0,0,20,20,false);stable();assertEquals(40,get("playerSafeLeft"));
        assertTrue((Integer)get("playerSafeRight")<=24);assertTrue((Integer)get("playerSafeRight")>=1);
    }
    @Test public void geometryEventIncludesRawRadiusAndEffectiveViewportWithoutVideoContent()throws Exception{
        int width=player.findViewById(R.id.control_layout).getWidth();apply(0,0,0,0,width/2,width/2,true);stable();
        main(()->call("recordPlayerGeometry"));
        await(()->{
            File file=new File(player.getFilesDir(),"diagnostics.ndjson");if(!file.exists())return false;
            try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){String line;
                while((line=in.readLine())!=null){org.json.JSONObject x=new org.json.JSONObject(line);
                    if(!"player_layout".equals(x.optString("name")))continue;org.json.JSONObject d=x.getJSONObject("details");
                    if(d.optInt("corner_left")!=width/2)continue;
                    assertFalse(d.has("title"));assertFalse(d.has("url"));assertTrue(d.optString("viewport").startsWith(width+"x"));return true;}
            }catch(Exception e){return false;}return false;
        });
    }
    @Test public void chaptersKeepMoreButtonAndItsMenuInsideTheRoundViewport()throws Exception{
        pref("player_ui_round",true);
        main(()->{
            try{Field f=PlayerActivity.class.getDeclaredField("screen_round");f.setAccessible(true);f.set(player,true);
                Field overlay=PlayerActivity.class.getDeclaredField("infoOverlay");overlay.setAccessible(true);
                java.util.List<com.RobinNotBad.BiliClient.model.ViewPoint> points=new ArrayList<>();
                for(int i=0;i<12;i++)points.add(new com.RobinNotBad.BiliClient.model.ViewPoint("章节 "+i,i,i+1,0,"",""));
                ((com.RobinNotBad.BiliClient.player.PlayerInfoOverlay)overlay.get(player)).setChapters(points);
            }catch(Exception e){throw new AssertionError(e);}
            call("showcon");call("updatePlayerInfoOverlay");
        });stable();
        main(()->call("updateRightToolbarBounds"));stable();
        View more=player.findViewById(R.id.menu_btn);
        Rect visible=new Rect();assertTrue(more.getGlobalVisibleRect(visible));
        assertEquals(more.getWidth(),visible.width());assertEquals(more.getHeight(),visible.height());
        assertTrue(more.getHeight()>0);save("round-chapters-toolbar.png");
        assertTrue("More overlaps the chapter/playback bar",more.getBottom()<=player.findViewById(R.id.bottom_control).getTop());
        View root=player.findViewById(R.id.control_layout);
        int[] origin=new int[2];root.getLocationOnScreen(origin);
        double radius=Math.min(root.getWidth(),root.getHeight())/2d;
        double cx=origin[0]+root.getWidth()/2d,cy=origin[1]+root.getHeight()/2d;
        System.out.println("round-more bounds="+visible+" root="+root.getWidth()+"x"+root.getHeight()
                +" rail="+player.findViewById(R.id.right_control).getX()+","+player.findViewById(R.id.right_control).getY());
        for(int x:new int[]{visible.left,visible.right})for(int y:new int[]{visible.top,visible.bottom})
            assertTrue("More corner outside circular display",Math.pow(x-cx,2)+Math.pow(y-cy,2)<=radius*radius);
        tap(more);
        com.RobinNotBad.BiliClient.player.PlayerOptionsMenu menu=(com.RobinNotBad.BiliClient.player.PlayerOptionsMenu)get("playbackOptionsMenu");
        assertNotNull(menu);assertTrue(menu.isShowing());
        await(()->menu.getDialog().getWindow().getDecorView().getWidth()>0);
        main(()->{
            View decor=menu.getDialog().getWindow().getDecorView();View controls=player.findViewById(R.id.control_layout);
            int diameter=Math.min(controls.getWidth(),controls.getHeight());
            assertTrue((long)decor.getWidth()*decor.getWidth()+(long)decor.getHeight()*decor.getHeight()<=(long)diameter*diameter);
            android.widget.ScrollView scroll=(android.widget.ScrollView)((ViewGroup)decor.findViewById(android.R.id.content)).getChildAt(0);
            scroll.fullScroll(View.FOCUS_DOWN);
            assertNotNull(menu.getMenu().findItem(109));menu.dismiss();
        });
    }
    @Test public void roundSideGroupsShareCenterWithoutHeaderOffset()throws Exception{
        pref("player_ui_round",true);
        main(()->{
            try{Field f=PlayerActivity.class.getDeclaredField("screen_round");f.setAccessible(true);f.set(player,true);}
            catch(Exception e){throw new AssertionError(e);}
            call("showcon");call("updateRightToolbarBounds");
        });stable();
        View rail=player.findViewById(R.id.right_control);
        android.widget.RelativeLayout.LayoutParams p=(android.widget.RelativeLayout.LayoutParams)rail.getLayoutParams();
        assertEquals(0,p.getRules()[android.widget.RelativeLayout.BELOW]);
        assertEquals(0,p.getRules()[android.widget.RelativeLayout.ABOVE]);
        assertEquals(p.topMargin,rail.getTop());
        Rect left=bounds(R.id.text_speed);left.union(bounds(R.id.screen_lock_btn));
        Rect right=bounds(R.id.danmaku_btn);right.union(bounds(R.id.menu_btn));
        assertTrue("Side groups do not share a center: "+left+" / "+right,Math.abs(left.exactCenterY()-right.exactCenterY())<=1);
        View root=player.findViewById(R.id.control_layout);int[] origin=new int[2];root.getLocationOnScreen(origin);
        assertTrue(Math.abs(left.exactCenterX()+right.exactCenterX()-(2*origin[0]+root.getWidth()))<=1);
        save("round-side-alignment.png");
        tap(player.findViewById(R.id.screen_lock_btn));assertTrue((Boolean)get("screenLocked"));
        tap(player.findViewById(R.id.screen_lock_btn));assertFalse((Boolean)get("screenLocked"));
        main(()->{call("showcon");call("updateRightToolbarBounds");});stable();
        Rect after=bounds(R.id.text_speed);after.union(bounds(R.id.screen_lock_btn));
        assertEquals(left,after);
        pref("player_ui_round",false);
        main(()->{
            try{Field f=PlayerActivity.class.getDeclaredField("screen_round");f.setAccessible(true);f.set(player,false);}
            catch(Exception e){throw new AssertionError(e);}
            call("updateRightToolbarBounds");
        });stable();
        p=(android.widget.RelativeLayout.LayoutParams)rail.getLayoutParams();
        assertEquals(R.id.top,p.getRules()[android.widget.RelativeLayout.BELOW]);
        assertEquals(R.id.bottom_control,p.getRules()[android.widget.RelativeLayout.ABOVE]);
        assertEquals(android.widget.RelativeLayout.TRUE,((android.widget.RelativeLayout.LayoutParams)
                player.findViewById(R.id.screen_lock_btn).getLayoutParams()).getRules()[android.widget.RelativeLayout.CENTER_VERTICAL]);
    }

    @Test public void roundSideGroupsStayCenteredWithLockAndRotateHidden()throws Exception{
        pref(SharedPreferencesUtil.PLAYER_SCREEN_LOCK,false);
        main(()->{player.findViewById(R.id.rotate_btn).setVisibility(View.GONE);call("showcon");call("updateRightToolbarBounds");});stable();
        assertEquals(View.GONE,player.findViewById(R.id.screen_lock_btn).getVisibility());
        Rect left=bounds(R.id.text_speed),right=bounds(R.id.danmaku_btn);right.union(bounds(R.id.menu_btn));
        assertTrue("Hidden controls changed the common center",Math.abs(left.exactCenterY()-right.exactCenterY())<=1);
        tap(player.findViewById(R.id.menu_btn));
        com.RobinNotBad.BiliClient.player.PlayerOptionsMenu menu=(com.RobinNotBad.BiliClient.player.PlayerOptionsMenu)get("playbackOptionsMenu");
        assertNotNull(menu);assertTrue(menu.isShowing());main(menu::dismiss);
    }
    @Test public void roundSideInsetSettingMovesBothRailsOutward()throws Exception{
        prefInt(SharedPreferencesUtil.PLAYER_ROUND_SIDE_INSET_DP,16);
        main(()->call("updateRightToolbarBounds"));stable();
        Rect leftBefore=bounds(R.id.text_speed);leftBefore.union(bounds(R.id.screen_lock_btn));
        Rect rightBefore=bounds(R.id.danmaku_btn);rightBefore.union(bounds(R.id.menu_btn));
        prefInt(SharedPreferencesUtil.PLAYER_ROUND_SIDE_INSET_DP,0);
        main(()->call("updateRightToolbarBounds"));stable();
        Rect leftAfter=bounds(R.id.text_speed);leftAfter.union(bounds(R.id.screen_lock_btn));
        Rect rightAfter=bounds(R.id.danmaku_btn);rightAfter.union(bounds(R.id.menu_btn));
        assertTrue("Left controls did not move toward the edge",leftAfter.left<leftBefore.left);
        assertTrue("Right controls did not move toward the edge",rightAfter.right>rightBefore.right);
    }
    private Rect bounds(int id){View v=player.findViewById(id);Rect r=new Rect();assertTrue(v.getGlobalVisibleRect(r));return r;}

    private void apply(int l,int t,int r,int b,int cl,int cr,boolean round){main(()->call("applyPlayerSafeArea",
            new Class[]{int.class,int.class,int.class,int.class,int.class,int.class,boolean.class},new Object[]{l,t,r,b,cl,cr,round}));}
    private void tap(View view){main(()->{int[] xy=new int[2];view.getLocationInWindow(xy);long time=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(time,time+(action==MotionEvent.ACTION_UP?40:0),action,xy[0]+view.getWidth()/2f,xy[1]+view.getHeight()/2f,0);player.dispatchTouchEvent(e);e.recycle();}
    });inst.waitForIdleSync();}
    private void save(String filename){main(()->{
        View root=player.findViewById(R.id.control_layout);Bitmap image=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(image));try(FileOutputStream out=new FileOutputStream(new File(player.getFilesDir(),filename))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
        catch(IOException e){throw new AssertionError(e);}finally{image.recycle();}
    });}
    private void stable()throws Exception{await(()->!player.findViewById(R.id.control_layout).isLayoutRequested()&&!player.findViewById(R.id.bottom_control).isLayoutRequested());SystemClock.sleep(100);inst.waitForIdleSync();}
    private interface Check{boolean ok();}
    private void await(Check c)throws Exception{long end=SystemClock.uptimeMillis()+20000;boolean[] ok={false};do{main(()->ok[0]=c.ok());if(ok[0])return;SystemClock.sleep(50);}while(SystemClock.uptimeMillis()<end);throw new AssertionError("Timed out");}
    private void pref(String k,boolean value){old.put(k,SharedPreferencesUtil.getSharedPreferences().getAll().get(k));SharedPreferencesUtil.putBoolean(k,value);}
    private void prefInt(String k,int value){if(!old.containsKey(k))old.put(k,SharedPreferencesUtil.getSharedPreferences().getAll().get(k));SharedPreferencesUtil.putInt(k,value);}
    private Object get(String n){try{Field f=PlayerActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(player);}catch(Exception e){throw new AssertionError(e);}}
    private void call(String n){call(n,new Class[0],new Object[0]);}
    private void call(String n,Class[] types,Object[] args){try{Method m=PlayerActivity.class.getDeclaredMethod(n,types);m.setAccessible(true);m.invoke(player,args);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable r){inst.runOnMainSync(r);}
}
