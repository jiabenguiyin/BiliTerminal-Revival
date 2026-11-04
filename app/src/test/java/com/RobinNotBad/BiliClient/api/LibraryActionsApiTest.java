package com.RobinNotBad.BiliClient.api;
import com.RobinNotBad.BiliClient.model.*;
import com.RobinNotBad.BiliClient.util.FavoriteVideoOrder;
import org.json.JSONObject;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LibraryActionsApiTest {
    @Test public void eachFavoriteOrderIsSentWithPageAndSearchScope(){
        for(String order:new String[]{"mtime","view","pubtime"}){
            String url=FavoriteApi.buildFavoriteVideosUrl(10042,3,"a & b",false,order);
            assertTrue(url.contains("order="+order));assertTrue(url.contains("pn=3"));assertTrue(url.contains("media_id=10042"));assertTrue(url.contains("type=0"));assertFalse(url.contains("a & b"));
        }
    }
    @Test public void oldAndInvalidStoredFavoriteOrdersRemainCompatible(){
        assertEquals("mtime",FavoriteVideoOrder.normalize(null));assertEquals("mtime",FavoriteVideoOrder.normalize("unknown"));
        assertEquals("view",FavoriteVideoOrder.normalize("view"));assertEquals("最新投稿",FavoriteVideoOrder.label("pubtime"));
    }
    @Test public void playlistSnapshotKeepsItsOrderAndOldTokensDefaultToFavoriteTime()throws Exception{
        FavoritePlaybackSource.Session session=FavoritePlaybackSource.Session.capture(42,42,10042,false,"",1,new ApiResult(),false,
                Collections.singletonList(new VideoCard("Video","UP","1","",99,"BVfixture")));
        session.favoriteOrder="view";JSONObject json=session.toJson();assertEquals("view",FavoritePlaybackSource.Session.fromJson(json).favoriteOrder);
        json.remove("favorite_order");assertEquals("mtime",FavoritePlaybackSource.Session.fromJson(json).favoriteOrder);
    }
    @Test public void historyDeleteTargetsOnlyRequestedArchiveIdsAndDeduplicates(){
        assertEquals("archive_11,archive_9876543210",HistoryApi.historyDeleteKids(Arrays.asList(11L,9876543210L,11L)));
    }
    @Test public void historyDeleteRejectsEmptyInvalidAndOversizedSelections(){
        List<Long> tooMany=new ArrayList<>();for(long i=1;i<=101;i++)tooMany.add(i);
        for(java.util.Collection<Long> ids:Arrays.asList(Collections.<Long>emptyList(),Arrays.asList(0L),Arrays.asList(-1L),Arrays.asList((Long)null),tooMany)){
            try{HistoryApi.historyDeleteKids(ids);fail();}catch(IllegalArgumentException expected){}
        }
    }
}
