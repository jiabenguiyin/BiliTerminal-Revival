package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BilibiliImageUrlUtilTest {
    @Test
    public void removesCdnResizeSuffixFromCommentImage() {
        assertEquals("https://i0.hdslb.com/bfs/new_dyn/abc.jpg",
                BilibiliImageUrlUtil.stripResizeSuffix(
                        "https://i0.hdslb.com/bfs/new_dyn/abc.jpg@672w_378h_1c_!web-dynamic.avif"));
    }

    @Test
    public void preservesQueryAndFragmentWhenRemovingSuffix() {
        assertEquals("https://i0.hdslb.com/bfs/new_dyn/abc.jpg?token=one#preview",
                BilibiliImageUrlUtil.stripResizeSuffix(
                        "https://i0.hdslb.com/bfs/new_dyn/abc.jpg@80q.webp?token=one#preview"));
    }

    @Test
    public void leavesOtherHostsAndUnresizedImagesAlone() {
        String external = "https://images.example.com/user@photo.jpg";
        String original = "https://i0.hdslb.com/bfs/new_dyn/abc.jpg";
        assertEquals(external, BilibiliImageUrlUtil.stripResizeSuffix(external));
        assertEquals(original, BilibiliImageUrlUtil.stripResizeSuffix(original));
    }
}
