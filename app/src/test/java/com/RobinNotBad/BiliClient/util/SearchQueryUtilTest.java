package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SearchQueryUtilTest {

    @Test
    public void parsesPositiveUidAndSurroundingWhitespace() {
        assertEquals(Long.valueOf(313733893L), SearchQueryUtil.parseUserUid(" 313733893 "));
    }

    @Test
    public void leavesNamesAndInvalidIdsAsKeywordQueries() {
        assertNull(SearchQueryUtil.parseUserUid("张的梦想"));
        assertNull(SearchQueryUtil.parseUserUid("123abc"));
        assertNull(SearchQueryUtil.parseUserUid("0"));
        assertNull(SearchQueryUtil.parseUserUid("-123"));
        assertNull(SearchQueryUtil.parseUserUid("999999999999999999999999"));
        assertNull(SearchQueryUtil.parseUserUid(null));
        assertNull(SearchQueryUtil.parseUserUid("   "));
    }
}
