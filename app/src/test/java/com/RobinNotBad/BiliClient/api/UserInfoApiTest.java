package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.assertTrue;

public class UserInfoApiTest {
    @Test public void coinAmountKeepsItsFractionAndAlwaysShowsOneDecimal()throws Exception{
        assertEquals(14.6,UserInfoApi.readCoinAmount(new JSONObject("{\"code\":0,\"data\":{\"money\":14.6}}")),0.00001);
        assertEquals("14.6",UserInfoApi.formatCoinAmount(14.6));assertEquals("10.0",UserInfoApi.formatCoinAmount(10));
        assertEquals("0.0",UserInfoApi.formatCoinAmount(0));assertEquals("1.3",UserInfoApi.formatCoinAmount(1.25));
    }
    @Test public void invalidCoinDataDoesNotPretendTheBalanceIsZero()throws Exception{
        assertTrue(Double.isNaN(UserInfoApi.readCoinAmount(new JSONObject("{\"code\":-101,\"data\":{\"money\":99}}"))));
        assertTrue(Double.isNaN(UserInfoApi.readCoinAmount(new JSONObject("{\"code\":0,\"data\":{}}"))));
        assertEquals("--",UserInfoApi.formatCoinAmount(Double.NaN));
    }
    @Test
    public void fullBirthdayIsKeptExactly() {
        assertEquals("2003-10-30", UserInfoApi.normalizeBirthday("2003-10-30"));
    }

    @Test
    public void plainSchoolNameIsKeptExactly() {
        assertEquals("Example University", UserInfoApi.readSchoolName("Example University"));
    }

    @Test
    public void chinaMidnightTimestampDoesNotMoveToPreviousDay() {
        assertEquals("2003-10-30", UserInfoApi.normalizeBirthday("1067443200"));
    }
}
