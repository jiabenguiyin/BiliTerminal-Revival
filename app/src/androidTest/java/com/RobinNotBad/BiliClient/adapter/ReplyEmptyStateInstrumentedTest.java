package com.RobinNotBad.BiliClient.adapter;

import android.app.Instrumentation;
import android.content.Intent;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.reply.ReplyFragment;
import com.RobinNotBad.BiliClient.activity.settings.TestActivity;
import com.RobinNotBad.BiliClient.model.Reply;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReplyEmptyStateInstrumentedTest {
    @Test public void unknownBangumiTotalDoesNotClaimALoadedThreadIsEmpty()throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        TestActivity host=(TestActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),TestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            inst.runOnMainSync(()->{
                FrameLayout frame=new FrameLayout(host);frame.setId(0x00123457);host.setContentView(frame);
                // Same overload as the bangumi page: no total is available yet.
                ReplyFragment fragment=ReplyFragment.newInstance(123,1,true);
                host.getSupportFragmentManager().beginTransaction().add(frame.getId(),fragment).commitNow();fragment.setRefreshing(false);
                try {
                    Field field=ReplyFragment.class.getDeclaredField("replyAdapter");field.setAccessible(true);ReplyAdapter adapter=(ReplyAdapter)field.get(fragment);
                    field=ReplyFragment.class.getDeclaredField("replyList");field.setAccessible(true);ArrayList<Reply> replies=(ArrayList<Reply>)field.get(fragment);
                    assertEquals(-1,adapter.count);
                    Reply reply=new Reply();reply.rpid=1234;reply.oid=123;replies.add(reply);
                    for(long total:new long[]{-1,0}) {
                        adapter.count=total;ReplyAdapter.WriteReply row=(ReplyAdapter.WriteReply)adapter.onCreateViewHolder(frame,0);adapter.onBindViewHolder(row,0);
                        String label=((TextView)row.itemView.findViewById(R.id.count_label)).getText().toString();
                        assertEquals("评论",label);assertFalse(label.contains("暂无"));assertFalse(label.contains("-1"));
                    }
                    adapter.count=5;ReplyAdapter.WriteReply row=(ReplyAdapter.WriteReply)adapter.onCreateViewHolder(frame,0);adapter.onBindViewHolder(row,0);
                    assertEquals("共5条评论",row.count_label.getText().toString());
                    replies.clear();adapter.count=0;adapter.onBindViewHolder(row,0);assertTrue(row.count_label.getText().toString().contains("暂无评论"));
                }catch(Exception e){throw new AssertionError(e);}
            });
        }finally{inst.runOnMainSync(host::finish);inst.waitForIdleSync();}
    }
}
