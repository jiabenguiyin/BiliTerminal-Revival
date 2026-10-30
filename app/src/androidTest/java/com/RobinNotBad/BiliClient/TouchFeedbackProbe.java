package com.RobinNotBad.BiliClient;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Real resource selection and pressed-pixel checks on the minified target, without AndroidX runner linkage. */
public class TouchFeedbackProbe extends Instrumentation {
    private Bundle args;
    private Activity host;
    private FrameLayout root;
    private final ArrayList<View> controls = new ArrayList<>();
    private final AtomicInteger clicks = new AtomicInteger();
    private int[] changedPixels;
    private String drawableClass;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args=arguments; start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Context target=getTargetContext();
            PackageInfo info=target.getPackageManager().getPackageInfo(target.getPackageName(),PackageManager.GET_SIGNATURES);
            check(info.versionCode==2026103003 && "1.2.3".equals(info.versionName),"Wrong package identity");
            String apkSha=hashFile(new File(info.applicationInfo.sourceDir));
            check(args.getString("expectedApkSha256").equals(apkSha),"Unexpected installed APK");
            check("94b1569cdc216f7b19acb1228e56a3dd8782f0adbc4e87e0d945c536e839f561".equals(hash(info.signatures[0].toByteArray())),"Signing changed");
            boolean baseline="baseline".equals(args.getString("mode"));
            if(baseline) target.getSharedPreferences("feedback_probe",Context.MODE_PRIVATE).edit().putString("marker","same-version-3003").commit();
            else check("same-version-3003".equals(target.getSharedPreferences("feedback_probe",Context.MODE_PRIVATE).getString("marker",null)),"Same-version installation lost data");
            Drawable drawable=target.getResources().getDrawable(id("background_button_compat","drawable"));
            drawableClass=drawable.getClass().getName();
            if(!baseline) check((Build.VERSION.SDK_INT>=21)=="android.graphics.drawable.RippleDrawable".equals(drawableClass),"Wrong feedback resource for this Android version: "+drawableClass);
            host=startActivitySync(new Intent().setClassName(target.getPackageName(),"com.RobinNotBad.BiliClient.activity.settings.TestActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(this::createControls); waitForIdleSync();
            changedPixels=new int[controls.size()];
            for(int index=0;index<controls.size();index++) exercise(index,baseline);
            if(!baseline) target.getSharedPreferences("feedback_probe",Context.MODE_PRIVATE).edit().remove("marker").commit();
            result.putBoolean("passed",true); result.putInt("api",Build.VERSION.SDK_INT);
            result.putString("mode",baseline?"baseline":"verify"); result.putString("drawableClass",drawableClass);
            result.putString("changedPixels",java.util.Arrays.toString(changedPixels)); result.putInt("successfulClicks",clicks.get());
            result.putString("apkSha256",apkSha);
        } catch(Throwable error) {
            result.putBoolean("passed",false); result.putString("error",error.getClass().getSimpleName()+": "+error.getMessage());
        } finally {
            if(host!=null) {runOnMainSync(host::finish);waitForIdleSync();}
        }
        finish(result.getBoolean("passed")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }

    private void createControls() {
        root=new FrameLayout(host); root.setBackgroundColor(0xff262626);
        root.setFocusableInTouchMode(true); host.setContentView(root); root.requestFocus();
        View card=LayoutInflater.from(host).inflate(id("cell_goto","layout"),root,false);
        ((TextView)card.findViewById(id("text","id"))).setText("Touch feedback fixture");
        place(card,12,20,280,64); controls.add(card);
        ImageButton button=new ImageButton(host);
        button.setImageResource(id("btn_player_play","drawable")); button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setBackgroundDrawable(host.getResources().getDrawable(id("background_button_compat","drawable")));
        place(button,12,108,48,48); controls.add(button);
        if(Build.VERSION.SDK_INT>=23) {
            View preview=LayoutInflater.from(host).inflate(id("cell_dynamic_image","layout"),root,false);
            ImageView image=preview.findViewById(id("imageView","id"));
            image.setImageDrawable(new ColorDrawable(0xff334d66)); image.getLayoutParams().height=dp(90);
            place(preview,12,180,280,90); controls.add(image);
        }
        for(View view:controls) view.setOnClickListener(v->clicks.incrementAndGet());
    }

    private void exercise(int index,boolean baseline) throws Exception {
        View view=controls.get(index); Bitmap[] before=new Bitmap[1],pressed=new Bitmap[1];
        runOnMainSync(()->{check(view.getWidth()>0 && view.getHeight()>0,"Control was not laid out");before[0]=capture(view);send(view,MotionEvent.ACTION_DOWN);});
        SystemClock.sleep(180);
        runOnMainSync(()->{check(view.isPressed(),"Control did not enter pressed state");pressed[0]=capture(view);});
        int changes=0;
        for(int y=0;y<before[0].getHeight();y++) for(int x=0;x<before[0].getWidth();x++)
            if(before[0].getPixel(x,y)!=pressed[0].getPixel(x,y)) changes++;
        changedPixels[index]=changes;
        // Pre-23 ImageView foregrounds are intentionally not part of this fixture.
        check(changes>20,"Pressed feedback is not visible, control="+index);
        save(pressed[0],(baseline?"before":"after")+"-feedback-"+index+".png");
        before[0].recycle(); pressed[0].recycle();
        runOnMainSync(()->send(view,MotionEvent.ACTION_CANCEL));
        SystemClock.sleep(450);
        runOnMainSync(()->{view.jumpDrawablesToCurrentState();check(!view.isPressed(),"Pressed feedback remained stuck");send(view,MotionEvent.ACTION_DOWN);send(view,MotionEvent.ACTION_UP);});
        waitForIdleSync();
        check(clicks.get()==index+1,"Click behavior changed, control="+index);
    }
    private void place(View view,int x,int y,int width,int height) {
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(width),dp(height));p.leftMargin=dp(x);p.topMargin=dp(y);root.addView(view,p);
    }
    private int dp(int value) {return Math.round(value*host.getResources().getDisplayMetrics().density);}
    private int id(String name,String type) {return getTargetContext().getResources().getIdentifier(name,type,getTargetContext().getPackageName());}
    private long down;
    private void send(View view,int action) {
        if(action==MotionEvent.ACTION_DOWN)down=SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,view.getWidth()/2f,view.getHeight()/2f,0);view.dispatchTouchEvent(event);event.recycle();
    }
    private Bitmap capture(View view) {Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));return b;}
    private void save(Bitmap bitmap,String name) throws Exception {
        try(FileOutputStream f=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,f);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.US,"%02x",b&255));return s.toString();}
    private static String hash(byte[] bytes)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String hashFile(File file)throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");try(FileInputStream f=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=f.read(b))!=-1)d.update(b,0,n);}return hex(d.digest());
    }
}
