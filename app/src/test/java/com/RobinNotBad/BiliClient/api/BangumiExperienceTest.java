package com.RobinNotBad.BiliClient.api;
import com.RobinNotBad.BiliClient.model.Bangumi;
import com.RobinNotBad.BiliClient.util.BangumiProgressStore;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class BangumiExperienceTest {
    @Test public void seasonListUsesSeasonIdsEvenWhenMediaIdsAreMissing()throws Exception{
        JSONArray a=new JSONArray().put(new JSONObject().put("season_id",7).put("title","第一季"))
                .put(new JSONObject().put("season_id",8).put("season_title","剧场版"))
                .put(new JSONObject().put("season_id",7)).put(new JSONObject().put("media_id",9));
        ArrayList<Bangumi.Season> seasons=BangumiApi.parseRelatedSeasons(a);assertEquals(2,seasons.size());
        assertEquals(7,seasons.get(0).season_id);assertEquals("第一季",seasons.get(0).season_title);assertEquals(0,seasons.get(0).media_id);
    }
    @Test public void minimalSeasonMetadataStillHasUsableFallbacks()throws Exception{
        Bangumi.Info i=BangumiApi.parseSeasonInfo(new JSONObject().put("season_id",7).put("cover","cover"));
        assertEquals(7,i.season_id);assertEquals("cover",i.cover_horizontal);assertNotNull(i.styles);assertNotNull(i.seasons);assertNotNull(i.publish);
    }
    @Test public void lastEpisodeSelectionSearchesAllSectionsAndFallsBackForRemovedEpisodes(){
        Bangumi b=new Bangumi();b.sectionList=new ArrayList<>();
        for(int s=0;s<2;s++){Bangumi.Section section=new Bangumi.Section();section.episodeList=new ArrayList<>();
            Bangumi.Episode episode=new Bangumi.Episode();episode.id=11+s;section.episodeList.add(episode);b.sectionList.add(section);}
        assertArrayEquals(new int[]{1,0},BangumiProgressStore.selection(b,12));
        assertArrayEquals(new int[]{0,0},BangumiProgressStore.selection(b,99));
    }
}
