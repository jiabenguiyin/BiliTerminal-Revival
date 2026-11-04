package com.RobinNotBad.BiliClient.activity;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.ArrayList;
import static org.junit.Assert.*;

/** Uses no product internals, so it also validates the obfuscated, signed release APK. */
@RunWith(AndroidJUnit4.class)
public class ImageViewerReleaseSmokeTest {
    final Instrumentation ui=InstrumentationRegistry.getInstrumentation();
    @Test public void signedPackageCanLoadLargeImageThroughItsRealDecoderFactories()throws Exception {
        Context context=ui.getTargetContext();File file=new File(context.getCacheDir(),"image-viewer-release-smoke.png");
        try(InputStream in=ui.getContext().getAssets().open("image-viewer/report-206-size.png");OutputStream out=new FileOutputStream(file)){
            byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }
        String name="com.RobinNotBad.BiliClient.activity.ImageViewerActivity";
        Instrumentation.ActivityMonitor monitor=ui.addMonitor(name,null,false);Activity[]owner={null};
        try{
            ArrayList<String> urls=new ArrayList<>();urls.add(file.toURI().toString());
            ui.runOnMainSync(()->context.startActivity(new Intent().setClassName(context,name).putStringArrayListExtra("imageList",urls)
                    .putExtra("loadOriginal",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            owner[0]=monitor.waitForActivityWithTimeout(7000);assertNotNull(owner[0]);
            long end=SystemClock.uptimeMillis()+12000;boolean[]loaded={false};
            do{
                ui.runOnMainSync(()->{
                    TextView status=find(owner[0].findViewById(android.R.id.content));
                    loaded[0]=status!=null&&status.getVisibility()==View.GONE;
                });
                if(loaded[0])break;SystemClock.sleep(30);
            }while(SystemClock.uptimeMillis()<end);
            assertTrue("Signed/R8 image decoder did not load its base tiles",loaded[0]);
        }finally{
            ui.removeMonitor(monitor);
            if(owner[0]!=null){ui.runOnMainSync(owner[0]::finish);long end=SystemClock.uptimeMillis()+5000;boolean[]done={false};
                do{ui.runOnMainSync(()->done[0]=ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(owner[0])==Stage.DESTROYED);
                    if(done[0])break;SystemClock.sleep(25);}while(SystemClock.uptimeMillis()<end);}
            file.delete();
        }
    }
    TextView find(View view){if(view instanceof TextView&&((TextView)view).getText().toString().startsWith("正在加载图片"))return (TextView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i));if(found!=null)return found;}}return null;}
}
