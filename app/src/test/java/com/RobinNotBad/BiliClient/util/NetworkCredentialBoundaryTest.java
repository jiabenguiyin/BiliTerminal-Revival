package com.RobinNotBad.BiliClient.util;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NetworkCredentialBoundaryTest {
    @Test
    public void onlyBilibiliAndConfiguredRelayReceiveCredentials() {
        String relayBase = "https://jp.031030.xyz/bili-relay";
        java.util.List<String> hosts = Arrays.asList(
                "bilibili.com", "*.bilibili.com", "*.hdslb.com", "upos-*.akamaized.net");
        assertTrue(NetworkCredentialPolicy.shouldAttach("https://api.bilibili.com/x/web-interface/nav", true, relayBase, hosts));
        assertTrue(NetworkCredentialPolicy.shouldAttach("https://i0.hdslb.com/image.jpg", true, relayBase, hosts));
        assertTrue(NetworkCredentialPolicy.shouldAttach(relayBase + "/api.bilibili.com/x/test", true, relayBase, hosts));
        assertTrue(NetworkCredentialPolicy.shouldAttach(
                "https://upos-hz-mirrorakam.akamaized.net/video.mp4", true, relayBase, hosts));

        assertFalse(NetworkCredentialPolicy.shouldAttach("https://example.com/image.jpg", true, relayBase, hosts));
        assertFalse(NetworkCredentialPolicy.shouldAttach("https://api.bilibili.com.example.com/steal", true, relayBase, hosts));
        assertFalse(NetworkCredentialPolicy.shouldAttach("https://example.akamaized.net/video.mp4", true, relayBase, hosts));
        assertFalse(NetworkCredentialPolicy.shouldAttach("file:///sdcard/test.mp4", true, relayBase, hosts));
        assertFalse(NetworkCredentialPolicy.shouldAttach("not a url", true, relayBase, hosts));
    }
}
