package com.RobinNotBad.BiliClient.player;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.util.GlideUtil;
import com.RobinNotBad.BiliClient.util.StringUtil;
import com.bumptech.glide.Glide;

/** A quiet, bounded audio dashboard. Playback and seeking still belong to the player. */
public final class AudioPlaybackPanel extends ScrollView {
    public interface Actions {
        void play(); void seek(long position); void skip(boolean next); void more(); void controls();
        boolean hold(float x,float y); void holdTouch(MotionEvent event); void releaseHold();
    }
    private final TextView title, status, time;
    private final SeekBar progress;
    private final ImageView cover;
    private final ImageButton play;
    private Actions actions;
    private boolean dragging, locked, playing;
    private String coverSource="";
    private long position, duration;
    private float downX,downY;
    private boolean scrollTap;
    private boolean holding;
    private boolean interactivePoint(float x,float y){
        int[] ids={R.id.audio_cover,R.id.audio_title,R.id.audio_progress,R.id.audio_previous,R.id.audio_play,
                R.id.audio_next,R.id.audio_rewind,R.id.audio_forward,R.id.audio_more};
        for(int id:ids){View child=findViewById(id);if(child==null||child.getVisibility()!=VISIBLE)continue;
            android.graphics.Rect rect=new android.graphics.Rect();child.getDrawingRect(rect);offsetDescendantRectToMyCoords(child,rect);
            if(rect.contains((int)x,(int)y))return true;}
        return false;
    }
    private final Runnable beginHold=()->{
        if(!locked&&actions!=null&&actions.hold(downX,downY)){
            holding=true;scrollTap=false;
            MotionEvent cancel=MotionEvent.obtain(android.os.SystemClock.uptimeMillis(),android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,downX,downY,0);
            super.dispatchTouchEvent(cancel);cancel.recycle();
        }
    };

    public AudioPlaybackPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFillViewport(false);setVerticalScrollBarEnabled(false);
        LayoutInflater.from(context).inflate(R.layout.view_audio_player,this,true);
        title=findViewById(R.id.audio_title);status=findViewById(R.id.audio_subtitle);time=findViewById(R.id.audio_time);
        progress=findViewById(R.id.audio_progress);cover=findViewById(R.id.audio_cover);play=findViewById(R.id.audio_play);
        click(R.id.audio_play,()->actions.play());
        click(R.id.audio_previous,()->actions.skip(false));click(R.id.audio_next,()->actions.skip(true));
        click(R.id.audio_more,()->actions.more());
        click(R.id.audio_rewind,()->seekRelative(false));click(R.id.audio_forward,()->seekRelative(true));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){dragging=true;}
            public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)showTime(value);}
            public void onStopTrackingTouch(SeekBar bar){
                dragging=false;
                if(!locked&&actions!=null&&bar.isEnabled())actions.seek(bar.getProgress());
            }
        });
    }
    public void setActions(Actions value){actions=value;}
    private void click(int id,Runnable action){findViewById(id).setOnClickListener(v->{if(!locked&&actions!=null)action.run();});}
    private int seekSeconds(){return PlaybackVolume.seekSeconds(com.RobinNotBad.BiliClient.util.SharedPreferencesUtil.getInt(PlaybackVolume.SEEK_SECONDS,10));}
    private void seekRelative(boolean forward){actions.seek(Math.max(0,Math.min(duration,position+(forward?1:-1)*seekSeconds()*1000L)));}
    private void showTime(long value){String valueText=StringUtil.toTime((int)(value/1000))+" / "+StringUtil.toTime((int)(duration/1000));if(!valueText.contentEquals(time.getText()))time.setText(valueText);}
    private void enabled(int id,boolean value){View view=findViewById(id);view.setEnabled(value);view.setAlpha(value?1f:.35f);}
    public void update(String name,String description,long value,long total,boolean isPlaying,boolean ready,boolean isLocked,boolean previous,boolean next){
        locked=isLocked;if(locked){removeCallbacks(beginHold);holding=false;}
        position=Math.max(0,value);duration=Math.max(0,total);
        if(!name.contentEquals(title.getText()))title.setText(name);
        if(!description.contentEquals(status.getText()))status.setText(description);
        if(playing!=isPlaying||play.getTag()==null){playing=isPlaying;play.setTag(isPlaying);play.setImageResource(playing?R.drawable.btn_player_pause:R.drawable.btn_player_play);}
        play.setContentDescription(playing?"暂停":"播放");
        progress.setMax((int)Math.min(Integer.MAX_VALUE,duration));progress.setEnabled(ready&&!locked&&duration>0);
        if(!dragging){progress.setProgress((int)Math.min(Integer.MAX_VALUE,position));showTime(position);}
        enabled(R.id.audio_play,ready&&!locked);enabled(R.id.audio_previous,ready&&!locked&&previous);
        enabled(R.id.audio_next,ready&&!locked&&next);enabled(R.id.audio_more,!locked);
        enabled(R.id.audio_rewind,ready&&!locked&&duration>0);enabled(R.id.audio_forward,ready&&!locked&&duration>0);
        setText(R.id.audio_rewind,"−"+seekSeconds()+"s");setText(R.id.audio_forward,"+"+seekSeconds()+"s");
    }
    private void setText(int id,String value){TextView view=findViewById(id);if(!value.contentEquals(view.getText()))view.setText(value);}
    public void setCover(String source){
        source=source==null?"":source;
        if(source.equals(coverSource))return;coverSource=source;
        Glide.with(this).load(source.isEmpty()?null:GlideUtil.url(source)).override(160,90).centerCrop()
                .placeholder(R.drawable.icon_audio_only_on).error(R.drawable.icon_audio_only_on).into(cover);
    }
    public void fit(int width,int height,int header,int left,int top,int right,int bottom,boolean round){
        int gap=Math.max(4,Math.round(8*getResources().getDisplayMetrics().density));
        int x=round?Math.round((width-Math.min(width,height)*.70f)/2):gap;
        int y=round?Math.round((height-Math.min(width,height)*.70f)/2):gap;
        int l=Math.max(x,left),r=Math.max(x,right),t=Math.max(header+gap,Math.max(y,top)),b=Math.max(y,bottom+gap);
        if(l!=getPaddingLeft()||r!=getPaddingRight()||t!=getPaddingTop()||b!=getPaddingBottom())setPadding(l,t,r,b);
        cover.setVisibility(height-t-b>Math.round(280*getResources().getDisplayMetrics().density)?VISIBLE:GONE);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();scrollTap=true;}
        return !locked&&super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(locked)return false;
        int slop=android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        if(event.getPointerCount()!=1||Math.abs(event.getX()-downX)>slop||Math.abs(event.getY()-downY)>slop)scrollTap=false;
        boolean handled=super.onTouchEvent(event);
        return handled;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(locked)return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            holding=false;downX=event.getX();downY=event.getY();scrollTap=!interactivePoint(downX,downY);
            android.graphics.Rect bounds=new android.graphics.Rect();progress.getDrawingRect(bounds);offsetDescendantRectToMyCoords(progress,bounds);
            removeCallbacks(beginHold);
            if(scrollTap&&!bounds.contains((int)downX,(int)downY))postDelayed(beginHold,android.view.ViewConfiguration.getLongPressTimeout());
        }
        int slop=android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        if(!holding&&(event.getPointerCount()!=1||Math.abs(event.getX()-downX)>slop||Math.abs(event.getY()-downY)>slop))removeCallbacks(beginHold);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)removeCallbacks(beginHold);
        if(holding){
            if(actions!=null)actions.holdTouch(event);
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){holding=false;if(actions!=null)actions.releaseHold();}
            return true;
        }
        boolean handled=super.dispatchTouchEvent(event);
        if(action==MotionEvent.ACTION_UP&&!holding&&scrollTap&&actions!=null)actions.controls();
        return handled;
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(beginHold);super.onDetachedFromWindow();}
}
