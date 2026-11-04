package com.RobinNotBad.BiliClient.activity;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.ui.widget.ImageViewerPage;
import com.RobinNotBad.BiliClient.ui.widget.PhotoViewpager;
import com.RobinNotBad.BiliClient.util.*;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.chrisbanes.photoview.PhotoView;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

/** Compressed local fixtures include a 119,504,896-byte image and a 246MB long image. */
@RunWith(AndroidJUnit4.class)
public class ImageViewerInstrumentedTest {
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();final Context context=ui.getTargetContext();
    final Map<String,Object> saved=new HashMap<>();final ArrayList<ImageViewerActivity> owners=new ArrayList<>();
    File fixtures;ImageViewerActivity owner;
    @Before public void setup()throws Exception {
        SharedPreferences prefs=SharedPreferencesUtil.getSharedPreferences();
        for(String key:new String[]{"dpi","density","paddingH_percent","paddingV_percent","player_ui_round","ui_landscape"})saved.put(key,prefs.getAll().get(key));
        prefs.edit().putFloat("dpi",1f).putInt("density",-1).putInt("paddingH_percent",0).putInt("paddingV_percent",0)
                .putBoolean("player_ui_round",false).putBoolean("ui_landscape",false).commit();
        fixtures=new File(context.getCacheDir(),"image-viewer-native-"+System.nanoTime());assertTrue(fixtures.mkdir());
        for(String name:new String[]{"report-206-size.png","long.png","wide.png","small.png","animated.gif","oversized.gif","corrupt.png"})
            try(InputStream in=ui.getContext().getAssets().open("image-viewer/"+name);OutputStream out=new FileOutputStream(new File(fixtures,name))){byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    }
    @After public void cleanup()throws Exception {
        ui.runOnMainSync(()->{for(ImageViewerActivity page:owners)if(!page.isFinishing())page.finish();});ui.waitForIdleSync();
        long end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){boolean[]all={true};ui.runOnMainSync(()->{for(ImageViewerActivity a:owners)if(!destroyed(a))all[0]=false;});if(all[0])break;SystemClock.sleep(30);}
        File[] files=fixtures.listFiles();if(files!=null)for(File f:files)f.delete();fixtures.delete();
        SharedPreferences.Editor edit=SharedPreferencesUtil.getSharedPreferences().edit();
        for(Map.Entry<String,Object>e:saved.entrySet()){Object v=e.getValue();if(v==null)edit.remove(e.getKey());else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);
            else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);else edit.putBoolean(e.getKey(),(Boolean)v);}edit.commit();
    }
    void open(String... names)throws Exception {
        ArrayList<String> urls=new ArrayList<>();for(String name:names)urls.add(new File(fixtures,name).toURI().toString());
        Instrumentation.ActivityMonitor monitor=ui.addMonitor(ImageViewerActivity.class.getName(),null,false);
        try{ui.runOnMainSync(()->context.startActivity(new Intent(context,ImageViewerActivity.class).putStringArrayListExtra("imageList",urls)
                .putExtra("loadOriginal",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            owner=(ImageViewerActivity)monitor.waitForActivityWithTimeout(7000);assertNotNull(owner);owners.add(owner);
        }finally{ui.removeMonitor(monitor);}
    }
    @Test public void report206SizedImageUsesSmallTilesAndKeepsFullResolutionZoom()throws Exception {
        open("report-206-size.png");ready();
        SubsamplingScaleImageView image=tiled();
        ui.runOnMainSync(()->{assertEquals(4096,image.getSWidth());assertEquals(7294,image.getSHeight());image.setScaleAndCenter(1f,new PointF(2000,6800));});
        loadedDetail(image);assertTileBudget(image);capture("image-viewer-large-zoom.png");
        ui.runOnMainSync(()->assertTrue(image.getScale()>0.9f));
    }
    @Test public void tallAndWideImagesPanToTheirLastSectionWithoutHugeBitmaps()throws Exception {
        for(String name:new String[]{"long.png","wide.png"}) {
            open(name);ready();SubsamplingScaleImageView image=tiled();
            ui.runOnMainSync(()->image.setScaleAndCenter(1f,name.equals("long.png")?new PointF(1024,29000):new PointF(29000,1024)));
            loadedDetail(image);assertTileBudget(image);
            ui.runOnMainSync(()->{
                Bitmap b=Bitmap.createBitmap(image.getWidth(),image.getHeight(),Bitmap.Config.ARGB_8888);image.draw(new Canvas(b));
                int c=b.getPixel(b.getWidth()/2,b.getHeight()/2);b.recycle();assertTrue("Last source section should be blue",Color.blue(c)>200&&Color.red(c)<40);
            });
            capture("image-viewer-"+name+".png");
            ImageViewerActivity old=owner;ui.runOnMainSync(old::finish);await(()->destroyed(old));
        }
    }
    @Test public void ordinaryGifAnimatesGalleryCountAndDownloadKeepTheSelectedSource()throws Exception {
        open("small.png","animated.gif");ready();PhotoViewpager pager=owner.findViewById(R.id.viewPager);
        ui.runOnMainSync(()->pager.setCurrentItem(1,false));
        await(()->photo()!=null&&photo().getDrawable() instanceof GifDrawable);
        ui.runOnMainSync(()->{assertTrue(((GifDrawable)photo().getDrawable()).isRunning());assertEquals("第 2/2张",((TextView)owner.findViewById(R.id.text_page)).getText().toString());});
        Instrumentation.ActivityMonitor download=ui.addMonitor(DownloadActivity.class.getName(),new Instrumentation.ActivityResult(0,null),true);
        try{
            ui.runOnMainSync(()->{owner.findViewById(R.id.btn_download).performClick();owner.findViewById(R.id.btn_download).performClick();});
            assertEquals(1,download.getHits());
        }finally{ui.removeMonitor(download);}
        for(int i=0;i<6;i++){final int index=i%2;ui.runOnMainSync(()->pager.setCurrentItem(index,false));}
        ui.runOnMainSync(()->pager.setCurrentItem(0,false));ready();assertTileBudget(tiled());
    }
    @Test public void corruptImageHasRetryAndOversizedGifUsesBoundedStaticPreview()throws Exception {
        open("corrupt.png");await(()->status()!=null&&status().getText().toString().contains("重试"));
        try(InputStream in=new FileInputStream(new File(fixtures,"small.png"));OutputStream out=new FileOutputStream(new File(fixtures,"corrupt.png"))){byte[]b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        ui.runOnMainSync(()->status().performClick());ready();
        ui.runOnMainSync(owner::finish);await(()->destroyed(owner));open("oversized.gif");
        await(()->photo()!=null&&photo().getDrawable() instanceof BitmapDrawable);
        ui.runOnMainSync(()->{Bitmap b=((BitmapDrawable)photo().getDrawable()).getBitmap();assertTrue(b.getWidth()<=2048&&b.getHeight()<=2048);
            assertTrue(status().getText().toString().contains("静态预览"));});
    }
    @Test public void closingWhileDecodeIsPendingReleasesPagesAndLateResultsCannotRestoreThem()throws Exception {
        open("long.png","wide.png","report-206-size.png");PhotoViewpager pager=owner.findViewById(R.id.viewPager);
        await(()->pager.getChildCount()>0);ArrayList<ImageViewerPage> pages=new ArrayList<>();
        ui.runOnMainSync(()->{for(int i=0;i<pager.getChildCount();i++)if(pager.getChildAt(i) instanceof ImageViewerPage)pages.add((ImageViewerPage)pager.getChildAt(i));owner.finish();});
        await(()->destroyed(owner));SystemClock.sleep(300);
        ui.runOnMainSync(()->{for(ImageViewerPage page:pages){assertEquals(Boolean.TRUE,field(page,"released"));assertNull(field(page,"tiled"));assertNull(field(page,"photo"));}});
    }
    ImageViewerPage current(){PhotoViewpager pager=owner.findViewById(R.id.viewPager);for(int i=0;i<pager.getChildCount();i++){View page=pager.getChildAt(i);if(page instanceof ImageViewerPage&&page.getLeft()==pager.getCurrentItem()*pager.getWidth())return (ImageViewerPage)page;}return null;}
    boolean destroyed(ImageViewerActivity page){return ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(page)==Stage.DESTROYED;}
    SubsamplingScaleImageView tiled(){ImageViewerPage page=current();return page==null?null:(SubsamplingScaleImageView)field(page,"tiled");}
    PhotoView photo(){ImageViewerPage page=current();return page==null?null:(PhotoView)field(page,"photo");}
    TextView status(){ImageViewerPage page=current();return page==null?null:(TextView)field(page,"status");}
    void ready()throws Exception{await(()->tiled()!=null&&tiled().isImageLoaded());}
    void loadedDetail(SubsamplingScaleImageView image)throws Exception{await(()->{Map<Integer,List<?>>map=(Map<Integer,List<?>>)field(image,"tileMap");if(map==null||map.get(1)==null)return false;boolean visible=false;for(Object t:map.get(1))if(Boolean.TRUE.equals(field(t,"visible"))){visible=true;if(field(t,"bitmap")==null)return false;}return visible;});}
    void assertTileBudget(SubsamplingScaleImageView image){ui.runOnMainSync(()->{Map<Integer,List<?>>map=(Map<Integer,List<?>>)field(image,"tileMap");long total=0;if(map==null){Bitmap b=(Bitmap)field(image,"bitmap");assertNotNull(b);assertTrue(b.getWidth()<=1024&&b.getHeight()<=1024);total=b.getByteCount();}else for(List<?>tiles:map.values())for(Object t:tiles){Bitmap b=(Bitmap)field(t,"bitmap");if(b!=null){assertTrue(b.getWidth()<=1024);assertTrue(b.getHeight()<=1024);total+=b.getByteCount();}}assertTrue("Decoded residency must stay far below the 114MB crashing bitmap",total<32L*1024*1024);});}
    static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new AssertionError(error);}}
    interface Check{boolean get();}
    void await(Check c)throws Exception{long end=SystemClock.uptimeMillis()+12000;boolean[]ok={false};do{ui.runOnMainSync(()->ok[0]=c.get());if(ok[0])return;SystemClock.sleep(30);}while(SystemClock.uptimeMillis()<end);fail("Image viewer did not settle");}
    void capture(String name)throws Exception{ui.waitForIdleSync();ui.runOnMainSync(()->{View v=owner.findViewById(android.R.id.content);Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));try(FileOutputStream out=owner.openFileOutput(name,0)){b.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{b.recycle();}});}
}
