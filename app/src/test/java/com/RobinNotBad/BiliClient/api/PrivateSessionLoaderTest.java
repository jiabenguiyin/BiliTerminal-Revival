package com.RobinNotBad.BiliClient.api;

import com.RobinNotBad.BiliClient.model.PrivateMsgSession;
import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class PrivateSessionLoaderTest {
    private final AtomicLong now = new AtomicLong(1000);
    private final AtomicInteger calls = new AtomicInteger();
    private final PrivateSessionLoader.Key key = new PrivateSessionLoader.Key(42, "fixture-42");
    private ArrayList<PrivateMsgSession> rows(long uid) {
        ArrayList<PrivateMsgSession> rows = new ArrayList<>();
        PrivateMsgSession row = new PrivateMsgSession(); row.talkerUid = uid; row.unread = 2;
        rows.add(row); return rows;
    }
    @Test public void rapidRefreshesReuseIndependentSnapshotsButUnreadChangesForceARead() throws Exception {
        PrivateSessionLoader loader = new PrivateSessionLoader(now::get, (k,s,u)->{calls.incrementAndGet();return rows(7);});
        PrivateSessionLoader.Result first=loader.load(key,20,2,false);
        first.sessions.get(0).unread=0; first.sessions.clear();
        PrivateSessionLoader.Result second=loader.load(key,20,2,false);
        assertTrue(second.cached);assertEquals(2,second.sessions.get(0).unread);assertEquals(1,calls.get());
        loader.load(key,20,0,false);assertEquals(2,calls.get());
        loader.load(key,20,0,true);assertEquals(3,calls.get());
    }
    @Test public void limitedResponseCoolsDownWithoutTurningFailureIntoEmptySuccess() throws Exception {
        PrivateSessionLoader loader=new PrivateSessionLoader(now::get,(k,s,u)->{
            if(calls.incrementAndGet()==1)throw new PrivateMsgApi.SessionApiException(-509);return rows(7);
        });
        PrivateSessionLoader.Result failed=loader.load(key,20,0,false);
        assertNull(failed.sessions);assertNotNull(failed.failure);assertEquals(15000,failed.retryAfterMs);
        for(int i=0;i<10;i++)loader.load(key,20,0,true);
        assertEquals(1,calls.get());
        now.addAndGet(15000);PrivateSessionLoader.Result recovered=loader.load(key,20,0,false);
        assertNull(recovered.failure);assertEquals(1,recovered.sessions.size());assertEquals(2,calls.get());
    }
    @Test public void failureRetainsRecentSnapshotWithoutChangingItsUnreadCount() throws Exception {
        PrivateSessionLoader loader=new PrivateSessionLoader(now::get,(k,s,u)->{
            if(calls.incrementAndGet()>1)throw new PrivateMsgApi.SessionApiException(-509);return rows(7);
        });
        loader.load(key,20,2,false);now.addAndGet(6000);
        PrivateSessionLoader.Result stale=loader.load(key,20,0,false);
        assertEquals(2,stale.sessions.get(0).unread);assertNotNull(stale.failure);
        now.addAndGet(10*60*1000);assertNull(loader.load(key,20,0,false).sessions);
    }
    @Test public void credentialsAndAccountChangesDropSnapshotsAndCooldown() throws Exception {
        PrivateSessionLoader loader=new PrivateSessionLoader(now::get,(k,s,u)->{calls.incrementAndGet();return rows(k.mid);});
        loader.load(key,20,0,false);
        PrivateSessionLoader.Result renewed=loader.load(new PrivateSessionLoader.Key(42,"renewed-42"),20,0,false);
        assertFalse(renewed.cached);
        PrivateSessionLoader.Result switched=loader.load(new PrivateSessionLoader.Key(77,"fixture-77"),20,0,false);
        assertEquals(77,switched.sessions.get(0).talkerUid);assertEquals(3,calls.get());
    }
    @Test public void concurrentLoadsShareOneRequest() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        PrivateSessionLoader loader=new PrivateSessionLoader(now::get,(k,s,u)->{
            calls.incrementAndGet();entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return rows(7);
        });
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Future<PrivateSessionLoader.Result> first=pool.submit(()->loader.load(key,20,0,false));
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            Future<PrivateSessionLoader.Result> second=pool.submit(()->loader.load(key,20,0,false));
            release.countDown();assertEquals(1,first.get(3,TimeUnit.SECONDS).sessions.size());
            assertEquals(1,second.get(3,TimeUnit.SECONDS).sessions.size());assertEquals(1,calls.get());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test public void lateOldAccountResultDoesNotReplaceTheNewAccountSnapshot() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        PrivateSessionLoader loader=new PrivateSessionLoader(now::get,(k,s,u)->{
            if(k.mid==42){entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));}return rows(k.mid);
        });
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try{
            Future<PrivateSessionLoader.Result> old=pool.submit(()->loader.load(key,20,0,false));
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            PrivateSessionLoader.Key other=new PrivateSessionLoader.Key(77,"fixture-77");
            assertEquals(77,loader.load(other,20,0,false).sessions.get(0).talkerUid);
            release.countDown();
            try{old.get(3,TimeUnit.SECONDS);fail();}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof java.io.IOException);}
            assertEquals(77,loader.load(other,20,0,false).sessions.get(0).talkerUid);
        }finally{release.countDown();pool.shutdownNow();}
    }
}
