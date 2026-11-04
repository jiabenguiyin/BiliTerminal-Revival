package com.RobinNotBad.BiliClient.util;
import master.flame.danmaku.danmaku.util.SelfDanmakuStyle;
import org.junit.Test;
import static org.junit.Assert.*;

public class SelfDanmakuStyleTest {
    @Test public void signedInUidUsesTheKnownForwardCrc32Format() {
        assertEquals("4e902d62",SelfDanmakuStyle.senderHash(86137069L));
        assertTrue(SelfDanmakuStyle.sameHash(SelfDanmakuStyle.senderHash(86137069L),"4E902D62"));
    }
    @Test public void anonymousAndInvalidAccountsNeverClaimAnySender() {
        for(long mid:new long[]{0,-1,Long.MIN_VALUE}) {
            assertEquals("",SelfDanmakuStyle.senderHash(mid));
            assertFalse(SelfDanmakuStyle.sameHash(SelfDanmakuStyle.senderHash(mid),"0"));
        }
    }
    @Test public void hexPaddingAndCaseAreEquivalentWithoutAcceptingArbitraryStrings() {
        assertTrue(SelfDanmakuStyle.sameHash("abc","00000ABC"));
        assertFalse(SelfDanmakuStyle.sameHash("abc","000000abc"));
        assertFalse(SelfDanmakuStyle.sameHash("abc","0xabc"));
        assertFalse(SelfDanmakuStyle.sameHash("abc","same nickname"));
    }
    @Test public void missingOrDifferentHashDoesNotMarkSomeoneElsesMessage() {
        for(String value:new String[]{null,""," ","4e902d63","4e902d6g"})
            assertFalse(SelfDanmakuStyle.sameHash("4e902d62",value));
    }
}
