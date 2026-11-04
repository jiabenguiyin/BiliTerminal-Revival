package com.RobinNotBad.BiliClient.player;

import com.RobinNotBad.BiliClient.model.LocalVideo;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CachedPlaybackSnapshotTest {
    private CachedPlaybackItem item(String path,long collection,int video,int part) {
        CachedPlaybackItem x=new CachedPlaybackItem();x.media=path;x.collectionId=collection;
        x.collectionIndex=video;x.partIndex=part;return x;
    }
    private LocalVideo group(CachedPlaybackItem... items) {
        LocalVideo v=new LocalVideo();v.playbackItems.addAll(Arrays.asList(items));return v;
    }
    @Test public void currentCollectionUsesOnlyDownloadedMembersInOriginalVideoAndPartOrder() {
        CachedPlaybackItem a=item("/cache/a.mp4",10,0,0),b=item("/cache/b.m4a",10,2,1),
                c=item("content://storage/c",10,2,0),foreign=item("/cache/other",20,1,0);
        LocalVideo selected=group(b),others=group(c,a,foreign);
        List<CachedPlaybackItem> q=CachedPlaybackSnapshot.select(Arrays.asList(selected,others),selected,b,false);
        assertEquals(Arrays.asList(a,c,b),q);
    }
    @Test public void oldCacheWithoutCollectionMetadataStaysInItsOwnPartGroup() {
        CachedPlaybackItem a=item("/cache/a",0,-1,-1),b=item("/cache/b",0,-1,-1);
        LocalVideo selected=group(a);assertEquals(Collections.singletonList(a),
                CachedPlaybackSnapshot.select(Arrays.asList(selected,group(b)),selected,a,false));
    }
    @Test public void allCacheScopePreservesVisibleListOrderAndDeduplicatesFiles() {
        CachedPlaybackItem a=item("/cache/a",10,0,0),b=item("/cache/b",20,0,0);
        LocalVideo selected=group(a);
        assertEquals(Arrays.asList(b,a),CachedPlaybackSnapshot.select(Arrays.asList(group(b),selected,group(b)),selected,a,true));
    }
    @Test public void localReferencesAndLongIdentityRoundTripWithoutNetworkUrls() throws Exception {
        CachedPlaybackItem a=item("content://provider/tree/file",10,3,4);a.aid=8000000000L;a.cid=9000000000L;
        a.folder="saf-tree:example";a.subtitles="/cache/subtitles";a.audioOnly=true;a.danmaku="/cache/danmaku.xml";
        CachedPlaybackItem copy=CachedPlaybackItem.fromJson(a.toJson());
        assertEquals(a.aid,copy.aid);assertEquals(a.cid,copy.cid);assertTrue(copy.audioOnly);
        assertEquals(a.subtitles,copy.subtitles);assertEquals(a.folder,copy.folder);
    }
    @Test public void remoteMediaAndRemoteDanmakuAreRejected() throws Exception {
        for(String url:new String[]{"https://example.test/a.mp4","http://example.test/a.mp4","ftp://example.test/a.mp4"}) {
            try {CachedPlaybackItem.fromJson(new JSONObject().put("media",url));fail(url);}
            catch(JSONException expected) { }
        }
        try {CachedPlaybackItem.fromJson(new JSONObject().put("media","/cache/a").put("danmaku","https://example.test/dm"));fail();}
        catch(JSONException expected) { }
    }
}
